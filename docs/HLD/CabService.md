# 🚕 CAB SERVICE — HLD + LLD (Smart Mobility)

## 🏗️ High Level Design (HLD)

### 🎯 Purpose

Cab Service manages the **ride lifecycle**:

* Ride creation
* State transitions
* Ride orchestration
* Event publishing & consumption

---

## 📦 Responsibilities

### Core

* Create ride request
* Maintain ride state machine
* Handle ride lifecycle transitions
* Publish ride events
* Consume driver assignment events

### Boundaries

* ❌ No driver selection logic
* ❌ No location tracking
* ❌ No notification handling

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Gateway → Cab Service (user APIs)

### Async (Kafka)

**Produces:**

* ride-requested
* ride-started (future)
* ride-completed (future)
* ride-cancelled (future)

**Consumes:**

* driver-assigned

---

## 🧠 Ride State Model

```
REQUESTED → MATCHING → DRIVER_ASSIGNED → ONGOING → COMPLETED
                           ↓
                        CANCELLED
```

---

## 🗄️ Storage Strategy

### PostgreSQL

* Ride data
* Ride state
* Metadata

### (Optional Future)

* Outbox table (event reliability)

---

## ⚙️ High-Level Flow

### Ride Creation

User → Cab Service → DB → Kafka (ride-requested)

---

### Driver Assignment

Kafka → Cab Service → State transition → DRIVER_ASSIGNED

---

### Ride Lifecycle

Driver → Start/Complete → Cab Service → State update

---

# 🧱 Low Level Design (LLD)

## 📁 Package Structure

```
cab-service/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── state/
├── event/
├── producer/
├── consumer/
├── config/
├── exception/
```

---

## 🗄️ Database Schema

### rides

```sql
CREATE TABLE rides (
    id UUID PRIMARY KEY,
    rider_id UUID NOT NULL,
    driver_id UUID,
    pickup_location VARCHAR(255),
    drop_location VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

---

### processed_events (Idempotency)

```sql
CREATE TABLE processed_events (
    event_id VARCHAR PRIMARY KEY,
    event_type VARCHAR(100),
    processed_at TIMESTAMP
);
```

---

## ⚙️ State Machine (Core Logic)

### RideState Interface

```java
interface RideState {
    void match(RideEntity ride);
    void assignDriver(RideEntity ride, UUID driverId);
    void start(RideEntity ride);
    void complete(RideEntity ride);
    void cancel(RideEntity ride);
}
```

---

### State Implementations

| State           | Allowed Actions      |
| --------------- | -------------------- |
| REQUESTED       | match, cancel        |
| MATCHING        | assignDriver, cancel |
| DRIVER_ASSIGNED | start, cancel        |
| ONGOING         | complete             |
| COMPLETED       | none                 |
| CANCELLED       | none                 |

---

## 🌐 APIs

### Create Ride

```
POST /rides
```

---

### Match Ride (internal)

```
POST /rides/{id}/match
```

---

### Assign Driver (internal)

```
POST /rides/{id}/assign?driverId=
```

---

### Start Ride

```
POST /rides/{id}/start
```

---

### Complete Ride

```
POST /rides/{id}/complete
```

---

### Cancel Ride

```
POST /rides/{id}/cancel
```

---

## ⚙️ Service Logic

### Create Ride

```java
save ride;
publish ride-requested;
```

---

### Assign Driver

```java
state.assignDriver();
save ride;
```

---

### Start Ride

```java
state.start();
save ride;
```

---

### Complete Ride

```java
state.complete();
save ride;
```

---

## 📡 Kafka Events

### ride-requested

```json
{
  "eventId": "...",
  "rideId": "...",
  "riderId": "...",
  "pickupLocation": "...",
  "dropLocation": "..."
}
```

---

### driver-assigned

```json
{
  "eventId": "...",
  "rideId": "...",
  "driverId": "..."
}
```

---

## ⚡ Kafka Flow

```
Cab Service → ride-requested → Matchmaking
Matchmaking → driver-assigned → Cab Service
```

---

## 🔒 Concurrency

### Idempotency

```text
processed_events table prevents duplicate event processing
```

---

### State Safety

```text
State machine enforces valid transitions
```

---

## 🧠 Patterns Used

* State Machine Pattern (core)
* Strategy Pattern (future fare logic)
* Factory Pattern (state factory)
* Observer Pattern (Kafka)
* Repository Pattern
* Service Layer Pattern

---

## ⚠️ Failure Handling

* Kafka retry
* DLQ (Dead Letter Queue)
* Idempotent consumers
* Transactional service methods

---

## 🔑 Key Insights

* Cab Service = **state owner**
* Event-driven lifecycle
* Strict separation from matchmaking
* State machine ensures correctness
* Designed for scalability & consistency

---
