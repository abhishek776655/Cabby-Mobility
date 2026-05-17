# 🎯 MATCHMAKING SERVICE — HLD + LLD (Smart Mobility)

> ⚠️ **IMPORTANT:** Matchmaking Service is **INTERNAL ONLY** - no user-facing REST APIs. All communication happens via Kafka events.

## Service Configuration

* **Port:** 8087
* **Data Store:** PostgreSQL + Redis
* **Clients:** Cab Service (Kafka + REST), Driver Service (REST), Location Service (REST)


# 🏗️ High Level Design (HLD)

## 🎯 Purpose

Matchmaking Service handles **driver discovery, assignment, and dispatch coordination**:

* Consume ride requests from cab-service
* Find nearby available drivers
* Reserve drivers (15s hold) to prevent double-assignment
* Coordinate driver acceptance/rejection
* Publish assignment outcomes
* Retry on reject/timeout


## 📦 Responsibilities

### Core (v2 Dispatch)

* Consume `ride-requested` events from Kafka
* Find nearby drivers via Location Service
* Filter eligible drivers (available, not reserved)
* Rank drivers (distance, rating, acceptance rate)
* Reserve driver in Redis (15s TTL)
* Send assignment to Driver Service
* **Consume driver response via Kafka** (assignment-accepted/rejected)
* Sequential retry on reject/timeout
* Publish `driver-assigned` or `matchmaking-failed`

### Boundaries

* ❌ Driver profiles (driver-service)
* ❌ Driver locations (location-service)
* ❌ Ride lifecycle (cab-service)
* ❌ User-facing APIs (moved to cab-service)
* ❌ Payment/pricing (future)


---

## 🔗 Inter-Service Communication

### Sync (REST) - INTERNAL

* Matchmaking → Location Service (nearby drivers)
* Matchmaking → Driver Service (driver availability)
* Matchmaking ← Cab Service (GET /internal/dispatch/{id})

### Async (Kafka)

**Consumes:**
```
ride-requested         → from cab-service (trigger matching)
assignment-accepted   → from cab-service (driver accepted via /dispatch/driver-response)
assignment-rejected   → from cab-service (driver rejected - triggers retry)
```

**Produces:**
```
driver-assigned        → to cab-service
matchmaking-failed     → to cab-service
```


---

## 🧠 Dispatch Flow (v2)

```
RideRequested (Kafka)
        ↓
Find Nearby Drivers (Location Service REST)
        ↓
Filter Eligible (availability + not reserved)
        ↓
Rank Drivers (distance + rating)
        ↓
Reserve Driver (Redis SETNX, 15s TTL)
        ↓
Assignment Requested (to driver)
        ↓
Wait for Response (REST API / Kafka)
        ↓
Accept → Publish DriverAssigned
Reject → Release, Retry next
Timeout → Release, Retry next
Exhausted → Publish NoDriverFound
```


---

## 🗄️ Storage Strategy

### PostgreSQL (Persistent State)

* dispatch_sessions - active dispatch state
* assignment_attempts - audit trail
* processed_events - idempotency

### Redis (Ephemeral State)

* driver:{driverId}:reservation - reservation lock (15s TTL)
* dispatch:{dispatchId} - active dispatch cache
* drivers:available:geo - available drivers geo index (shared with location-service)


---

### Key Structure

```
PostgreSQL:
dispatch_sessions        → dispatch state & candidates
assignment_attempts      → per-driver attempt audit
processed_events         → idempotency

Redis:
driver:{driverId}:reservation  → SETNX (dispatchId:rideId, 15s TTL)
dispatch:{dispatchId}          → Hash (status, driverId, expiresAt)
drivers:available:geo         → GEO (online drivers only)
```


---

# 🧱 Low Level Design (LLD)


## 📁 Package Structure

```
matchmaking-service/
├── config/
├── controller/          (REST API for driver response)
├── service/             (DispatchService, MatchmakingService)
├── service/impl/
├── repository/
├── dto/
├── entity/
├── redis/               (ReservationService, DispatchCacheService)
├── kafka/
│   ├── consumer/
│   └── producer/
├── event/
├── exception/
├── domain/              (DispatchStatus, AttemptStatus)
└── strategy/            (ranking strategies)
```


---

## 🗄️ Data Model (PostgreSQL)

### dispatch_sessions

```sql
dispatch_id        UUID PK
ride_id             UUID
rider_id            UUID
status              VARCHAR (SEARCHING, ASSIGNMENT_SENT, RETRYING, ASSIGNED, FAILED, CANCELLED)
current_driver_id   BIGINT
remaining_candidates JSON
retry_count         INT
created_at          TIMESTAMP
expires_at          TIMESTAMP
updated_at          TIMESTAMP
```

### assignment_attempts

```sql
id                 BIGSERIAL PK
dispatch_id        UUID FK
driver_id           BIGINT
score              DOUBLE
status             VARCHAR (RESERVED, ASSIGNMENT_SENT, ACCEPTED, REJECTED, TIMEOUT, FAILED)
failure_reason     VARCHAR
created_at         TIMESTAMP
```


---

## 🌐 APIs - INTERNAL ONLY

### Get Dispatch Status

```
GET /internal/dispatch/{rideId}
```
> Called by Cab Service to get dispatch status for ride

### No User-Facing Endpoints

All driver interaction now goes through **Cab Service**:
- Driver calls `POST /dispatch/driver-response` (Cab Service)
- Cab Service publishes to Kafka: `assignment-accepted` or `assignment-rejected`
- Matchmaking consumes these events and handles retry logic


---

## ⚙️ Service Logic

### Dispatch State Machine

```
SEARCHING → ASSIGNMENT_SENT → RETRYING → ASSIGNED
                │                   │
                │                   ↓
                │                 FAILED
                │                   │
                ↓                   ↓
              CANCELLED ◄────────────┘
```


### Driver Reservation (Redis)

```java
// Acquire
SET driver:{driverId}:reservation {dispatchId}:{rideId} NX EX 15

// Release
DEL driver:{driverId}:reservation

// Check
GET driver:{driverId}:reservation
```


### Retry Logic

```java
for each candidate in ranked:
  if reserve(candidate):
    sendAssignment()
    waitForResponse()
    if accept: return SUCCESS
    if reject: release(), continue
    if timeout: release(), continue
return FAILED
```


---

## 📡 Kafka Events

### ride-requested (Consumed)

```json
{
  "eventId": "uuid",
  "rideId": "uuid",
  "riderId": "uuid",
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "pickupLocation": "123 Main St"
}
```


### driver-assigned (Produced)

```json
{
  "eventId": "uuid",
  "rideId": "uuid",
  "driverId": 12345,
  "assignedAt": "2026-05-17T10:00:00Z"
}
```


### matchmaking-failed (Produced)

```json
{
  "eventId": "uuid",
  "rideId": "uuid",
  "reason": "NO_DRIVER_AVAILABLE"
}
```


---

## 🔒 Concurrency & Idempotency

### Double Assignment Prevention

* Redis SETNX with 15s TTL ensures only one dispatch can reserve a driver
* Reservation key = `driver:{driverId}:reservation`
* Value = `{dispatchId}:{rideId}`

### Idempotency

* Check `processed_events` table before processing ride-requested
* Use dispatchId as idempotency key at dispatch level
* Check dispatch status before processing driver response


---

## ⚠️ Failure Handling

| Scenario | Handling |
|----------|----------|
| Redis down | Fail closed → publish NoDriverFound |
| Driver offline during reservation | Release, retry next |
| Driver accepts after timeout | Reject (reservation expired) |
| All candidates exhausted | Publish NoDriverFound |
| Kafka publish failure | Retry with backoff |


---

## 🧠 Patterns Used

* Event-driven architecture (Kafka)
* Repository Pattern (JPA)
* Service Layer Pattern
* Strategy Pattern (driver ranking)
* Redis distributed lock (SETNX)
* State machine (dispatch status)


---

## 🔑 Key Insights

* Matchmaking = **internal coordination engine (no user-facing APIs)**
* Redis reservation = **critical for preventing double-assignment**
* Sequential retry = **simpler than parallel fanout (v2)**
* State machine = **clear dispatch lifecycle**
* Reuse existing clients = **location-service, driver-service**
* **Driver response flow:** Driver App → Cab Service → Kafka → Matchmaking