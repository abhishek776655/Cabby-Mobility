# 📡 REALTIME GATEWAY SERVICE — HLD + LLD (Smart Mobility)

## Service Configuration

* **Port:** 8095
* **Data Store:** None (stateless, event fanout)
* **Clients:** Rider App (WebSocket), Driver App (WebSocket)


# 🏗️ High Level Design (HLD)

## 🎯 Purpose

Realtime Gateway Service bridges **Kafka events to WebSocket/STOMP clients** for real-time communication:

* Rider trip tracking (driver location updates)
* Driver assignment notifications
* Event fanout to subscribed clients


## 📦 Responsibilities

### Core

* Consume Kafka events (`driver-location-events`, `assignment-requested`)
* Validate routing fields in events
* Broadcast events to active WebSocket/STOMP subscribers
* Log connection, subscription, disconnection, and broadcast events

### Boundaries

* ❌ No driver GPS ingestion (Location Service)
* ❌ No ride state ownership (Cab Service)
* ❌ No driver state ownership (Driver Service)
* ❌ No persistence (stateless by design)
* ❌ No Redis pub/sub — **single-instance only** (see Scaling Limitation below)


---

## 🔗 Inter-Service Communication

### Sync (WebSocket/STOMP)

* Rider App → Realtime Service (subscribe to `/topic/trip/{rideId}`)
* Driver App → Realtime Service (subscribe to `/topic/driver/{driverId}`)
* Realtime Service → Cab Service (`GET /rides/{rideId}` with `X-User-Id`) — verifies trip-topic ownership,
  see Security below

### Security — WebSocket/STOMP Auth

Previously this endpoint had **zero authN/authZ**: `setAllowedOriginPatterns("*")` and no token check
anywhere, so anyone could connect and subscribe to any `/topic/trip/{rideId}` or `/topic/driver/{driverId}`
— both IDs are sequential/guessable. Fixed with a `StompAuthChannelInterceptor`:

* **CONNECT** requires a native STOMP header `Authorization: Bearer {jwt}` (same shared HMAC secret as
  auth-service/gateway-service). Missing/invalid token → `StompAuthorizationException`, connection rejected.
* **SUBSCRIBE** is destination-scoped to the authenticated principal:
  * `/topic/driver/{id}` — `id` must equal the principal's own `userId`, or role `ADMIN`
  * `/topic/trip/{rideId}` — calls `RideOwnershipClient.isRideParticipant(rideId, userId)`, which hits
    cab-service's existing `GET /rides/{rideId}` with `X-User-Id` — reuses cab-service's own
    rider-or-driver ownership check rather than duplicating it here
  * anything else — denied by default

> ⚠️ **Non-obvious gotcha:** `accessor.setUser(principal)` set on the CONNECT message inside a
> `ChannelInterceptor` does **not** persist to later frames on the same session. That association is made
> by `StompSubProtocolHandler` at the WebSocket-handshake level using an *HTTP* principal — which doesn't
> exist here, since auth happens in-band over STOMP, not at the HTTP handshake. `StompAuthChannelInterceptor`
> tracks `sessionId → principal` itself in a `ConcurrentHashMap`, populated on CONNECT, read on SUBSCRIBE,
> and cleaned up on DISCONNECT (STOMP frame) and on `SessionDisconnectEvent` (abrupt closes that skip it).

**CORS**: `allowedOriginPatterns` is now `realtime.websocket.allowed-origins` (default
`http://localhost:3000,http://localhost:5173`), not `*`.

**Client-side change required:** any STOMP client connecting here must send the JWT as a native CONNECT
header — e.g. `connectHeaders: { Authorization: 'Bearer ' + token }` — or every connection is rejected.
`scripts/simulate-random-ride-load.mjs` and `scripts/simulate-ride-scenario.mjs` were updated accordingly,
and now open **one WebSocket connection per driver candidate** instead of one connection subscribing to
every candidate's topic (a single token can't own multiple driver identities).


### Async (Kafka)

**Consumes:**

```
driver-location-events       → Location Service
assignment-requested         → Matchmaking Service
```


---

## 🧠 Event Flow

### Rider Trip Tracking

```
Location Service → Kafka (driver-location-events)
        ↓
Realtime Gateway → Broadcast to /topic/trip/{rideId}
        ↓
Rider App receives location update
```

### Driver Assignment Notification

```
Matchmaking Service → Kafka (assignment-requested)
        ↓
Realtime Gateway → Broadcast to /topic/driver/{driverId}
        ↓
Driver App receives assignment request
```


---

## 🗄️ Storage Strategy

### None (Stateless)

Realtime Gateway is a **stateless event fanout service**:

* No database
* No Redis
* No persistent state
* Events broadcasted in-memory via Spring SimpleBroker

> ⚠️ **Known scaling limitation (not yet fixed):** `enableSimpleBroker` is in-memory and per-instance. If
> this service runs as >1 replica, a session connected to pod A never receives a broadcast triggered by a
> Kafka message consumed by pod B — Kafka's consumer group already handles fan-in correctly (only one pod
> processes each event), but fan-out to sessions on *other* pods is unhandled. Fine at a single instance;
> would need `enableStompBrokerRelay` (external broker) or a Redis pub/sub relay before scaling out.


---

## ⚙️ High-Level Flow

### WebSocket Connection

Client → WebSocket Handshake → STOMP Subscribe → Session Tracked


### Event Broadcast

Kafka Event → Validate → Construct Destination → Broadcast via SimpMessagingTemplate


---

# 🧱 Low Level Design (LLD)


## 📁 Package Structure

```
realtime-gateway-service/
├── config/          (WebSocketConfig, KafkaConsumerConfig, RestClientConfig)
├── security/        (StompAuthChannelInterceptor, JwtUtils, RealtimePrincipal,
│                      RideOwnershipClient, StompAuthorizationException)
├── websocket/
├── kafka/
├── service/
├── dto/
├── controller/
├── handler/
├── exception/
├── util/
└── domain/
```



## 🗄️ Data Model (Kafka Events)

### DriverLocationUpdatedEvent

```json
{
  "driverId": "driver_1",
  "rideId": "ride_123",
  "latitude": 28.6139,
  "longitude": 77.2090,
  "speed": 42.0,
  "heading": 120.0,
  "timestamp": "2026-05-17T12:00:00Z"
}
```

**Broadcast to:** `/topic/trip/{rideId}`

---

### AssignmentRequestedEvent

```json
{
  "eventId": "...",
  "eventType": "ASSIGNMENT_REQUESTED",
  "dispatchId": "...",
  "rideId": "ride_123",
  "driverId": "driver_1",
  "pickupLatitude": 28.6139,
  "pickupLongitude": 77.2090,
  "pickupLocation": "Connaught Place",
  "expiresAt": "..."
}
```

**Broadcast to:** `/topic/driver/{driverId}`


## 🌐 APIs

### WebSocket Endpoint

```
ws://localhost:8095/ws
```

### STOMP Destinations

```
Rider: /topic/trip/{rideId}
Driver: /topic/driver/{driverId}
```


## ⚙️ Service Logic


### Kafka Consumption

```java
@KafkaListener(topics = "driver-location-events")
public void consumeDriverLocation(DriverLocationUpdatedEvent event) {
    validate(event.getRideId());
    broadcastService.broadcastDriverLocation(event);
}

@KafkaListener(topics = "assignment-requested")
public void consumeAssignment(AssignmentRequestedEvent event) {
    validate(event.getDriverId());
    broadcastService.broadcastAssignmentRequest(event);
}
```



### Broadcast

```java
public void broadcastDriverLocation(DriverLocationUpdatedEvent event) {
    String destination = "/topic/trip/" + event.getRideId();
    messagingTemplate.convertAndSend(destination, event);
}

public void broadcastAssignmentRequest(AssignmentRequestedEvent event) {
    String destination = "/topic/driver/" + event.getDriverId();
    messagingTemplate.convertAndSend(destination, event);
}
```



## 🔒 Configuration (application.properties)

```properties
server.port=8095

spring.application.name=realtime-gateway-service
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=realtime-gateway-service-group

jwt.secret=${JWT_SECRET:...}
services.cab.url=${CAB_SERVICE_URL:http://cab-service:8089}

realtime.websocket.endpoint=/ws
realtime.websocket.allowed-origins=${REALTIME_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```


## ⚠️ Failure Handling

* Kafka failure → Logged, handled by Kafka error handler
* No WebSocket subscriber → Message dropped (no retry)
* Invalid event → Logged, passed to error handler
* Missing/invalid JWT on CONNECT → `StompAuthorizationException`, connection rejected
* Unauthorized SUBSCRIBE (wrong driver topic, not a ride participant, unknown destination) → rejected,
  same exception type
* `RideOwnershipClient` call to cab-service fails/errors → treated as not-a-participant (deny, not allow)


## 🔑 Key Insights

* Realtime Gateway = **stateless event fanout**
* Spring SimpleBroker = **in-memory message broker** — single-instance only, see scaling limitation above
* No persistence = **horizontally scalable** *for the HTTP/Kafka-consumer side*; the broker itself is not
* WebSocket auth is now **mandatory** — CONNECT needs a JWT, SUBSCRIBE is scoped to the caller's own
  topics, verified where needed against cab-service's own ownership logic rather than duplicating it
* Session-to-principal association across STOMP frames needed a manual sessionId map — `ChannelInterceptor.
  setUser()` on CONNECT alone doesn't propagate to later frames when there's no HTTP-level principal
