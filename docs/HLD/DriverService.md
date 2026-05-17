# 🚖 DRIVER SERVICE — HLD + LLD (Smart Mobility)

## Service Configuration

* **Port:** 8084
* **Database:** PostgreSQL
* **Cache:** Redis (availability & location)

## 🏗️ High Level Design (HLD)

### 🎯 Purpose

Driver Service manages real-time driver state:

* Availability (ONLINE/OFFLINE/BUSY)
* Location tracking
* Driver metadata

---

## 📦 Responsibilities

### Core

* Driver onboarding
* Availability toggle
* Location updates
* Driver state tracking

### Boundaries

* ❌ No ride lifecycle ownership
* ❌ No matchmaking logic

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Matchmaking → Driver Service (fetch drivers)

### Async (Kafka)

**Produces:**

* driver.status.updated
* driver.location.updated
* driver.accepted
* driver.rejected

**Consumes:**

* ride.matched (future notification)

---

## 🧠 Driver State Model

OFFLINE → ONLINE → BUSY → ONLINE → OFFLINE

---

## 🗄️ Storage Strategy

### PostgreSQL

* Driver profile
* Vehicle info

### Redis

* Availability
* Geo indexing

---

## ⚙️ High-Level Flow

### Driver Online

Driver → Service → Redis update

### Matchmaking Query

Matchmaking → Driver → Redis GEO query

### Ride Acceptance

Driver → Kafka → Ride Service

---

# 🧱 Low Level Design (LLD)

## 📁 Package Structure

```
driver-service/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── kafka/
├── config/
├── redis/
├── exception/
```

---

## 🗄️ Database Schema

### drivers

```sql
CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL,
    name VARCHAR(100),
    phone VARCHAR(20),
    rating DOUBLE PRECISION DEFAULT 5.0,
    status VARCHAR(20),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### driver_vehicle

```sql
CREATE TABLE driver_vehicle (
    id UUID PRIMARY KEY,
    driver_id UUID,
    vehicle_type VARCHAR(50),
    vehicle_number VARCHAR(20),
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
);
```

---

## ⚡ Redis Model

Keys:

```
driver:availability:{driverId}
driver:location:{driverId}
```

Geo:

```
GEOADD drivers:geo <lng> <lat> driverId
```

---

## 🌐 APIs

### Create Driver

```
POST /drivers
```

### Update Availability

```
PATCH /drivers/{id}/availability
```

### Update Location

```
POST /drivers/{id}/location
```

### Nearby Drivers (Internal)

```
GET /internal/drivers/nearby
```

---

## ⚙️ Service Logic

### Availability

```java
redis.set(key, status);
geoAdd/remove;
kafka.publish(event);
```

### Location

```java
redis.geoAdd(...);
kafka.publish(event);
```

---

## 📡 Kafka Events

### driver.accepted

```json
{ "driverId": "uuid", "rideId": "uuid" }
```

### driver.status.updated

```json
{ "driverId": "uuid", "status": "ONLINE" }
```

---

## 🔒 Concurrency

Prevent double assignment:

```
SETNX driver:lock:{driverId}
```

---

## 🧠 Patterns Used

* Repository Pattern
* Service Layer Pattern
* Event-Driven Architecture
* Cache-Aside (Redis)
* State Machine Pattern

---

## ⚠️ Failure Handling

* Retry (Kafka)
* DLQ
* Idempotency

---

## 🔑 Key Insights

* Redis = real-time truth
* Stateless service design
* Loose coupling from Ride Service

---