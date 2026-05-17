# 📡 REALTIME GATEWAY SERVICE — HLD + LLD (Smart Mobility)

## Service Configuration

* **Port:** 8085
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
* ❌ No authentication in Phase 1
* ❌ No Redis pub/sub


---

## 🔗 Inter-Service Communication

### Sync (WebSocket/STOMP)

* Rider App → Realtime Service (subscribe to `/topic/trip/{rideId}`)
* Driver App → Realtime Service (subscribe to `/topic/driver/{driverId}`)


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
├── config/
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
ws://localhost:8085/ws
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



## 🔒 Configuration (application.yml)

```yaml
server:
  port: 8085

spring:
  application:
    name: realtime-gateway-service
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: realtime-gateway-service-group
  websocket:
    allowed-origins: "*"
```


## ⚠️ Failure Handling

* Kafka failure → Logged, handled by Kafka error handler
* No WebSocket subscriber → Message dropped (no retry)
* Invalid event → Logged, passed to error handler


## 🔑 Key Insights

* Realtime Gateway = **stateless event fanout**
* Spring SimpleBroker = **in-memory message broker**
* No persistence = **horizontally scalable**
* Phase 1 = **no auth, no replay, no clustering**