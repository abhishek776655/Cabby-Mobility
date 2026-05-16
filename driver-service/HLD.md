# 🚖 Driver Service — HLD & LLD

## Overview

Driver Service manages real-time driver state, availability, and location, and exposes fast lookup APIs for matchmaking. It is stateless, with real-time data in Redis and durable data in PostgreSQL.

---

# 🏗️ High Level Design (HLD)

## Purpose

* Manage driver availability (ONLINE/OFFLINE/BUSY)
* Track driver location (lat/lng)
* Maintain driver metadata (rating, status)

## Responsibilities

* Onboard drivers and manage profiles
* Toggle availability
* Update location in real time
* Serve nearby-driver queries for matchmaking

## Boundaries

* No ride lifecycle ownership (Ride Service)
* No matching logic (Matchmaking Service)

## Inter-Service Communication

### Synchronous (REST)

* Matchmaking → Driver: fetch nearby/eligible drivers

### Asynchronous (Kafka)

**Produces**

* driver.status.updated
* driver.location.updated
* driver.accepted
* driver.rejected

**Consumes**

* ride.matched (optional: notify driver)

## Driver State Model

OFFLINE → ONLINE → BUSY → ONLINE → OFFLINE

Constraint: BUSY drivers must not be assigned again.

## Storage Strategy

* PostgreSQL: profiles, vehicles, ratings (source of truth)
* Redis: availability + GEO index (real-time truth)

## High-Level Flows

1. Driver goes ONLINE → update Redis (availability + geo)
2. Matchmaking queries → Redis GEO radius query → return candidates
3. Driver accepts → publish driver.accepted → Ride updates state

---

# 🧱 Low Level Design (LLD)

## Package Structure

```
driver-service/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
├── mapper/
├── kafka/
├── redis/
├── config/
├── exception/
```

## Database Schema (PostgreSQL)

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

## Redis Model

### Keys

```
driver:availability:{driverId} -> ONLINE|OFFLINE|BUSY
driver:location:{driverId} -> lat,lng
```

### GEO Index

```
GEOADD drivers:geo <lng> <lat> driverId
GEORADIUS drivers:geo <lng> <lat> <radius>
```

## APIs

### Create Driver

```
POST /drivers
```

### Update Availability

```
PATCH /drivers/{id}/availability
Body: { "status": "ONLINE" }
```

### Update Location

```
POST /drivers/{id}/location
Body: { "lat": 28.61, "lng": 77.23 }
```

### Nearby Drivers (Internal)

```
GET /internal/drivers/nearby?lat=&lng=&radius=
```

## Service Logic

### Availability Update (pseudo)

```java
set availability in Redis;
if ONLINE -> add to GEO; else remove;
publish driver.status.updated;
```

### Location Update (pseudo)

```java
geoAdd(drivers:geo, lng, lat, driverId);
publish driver.location.updated;
```

## Kafka Event Contracts

### driver.accepted

```json
{ "driverId": "uuid", "rideId": "uuid", "timestamp": "..." }
```

### driver.status.updated

```json
{ "driverId": "uuid", "status": "ONLINE" }
```

## Concurrency & Consistency

Problem: double assignment under race conditions

Solution: Redis lock

```
SETNX driver:lock:{driverId}
EXPIRE driver:lock:{driverId} <ttl>
```

## Patterns Used

* Repository Pattern
* Service Layer Pattern
* Event-Driven Architecture (Kafka)
* Cache-Aside (Redis)
* State Machine (driver status)

## Failure Handling

* Retries with backoff for Kafka
* Idempotent consumers
* Dead Letter Queue (DLQ)

## Key Decisions

* Redis as real-time source for availability/geo
* Stateless service (horizontal scaling)
* Strict boundaries from Ride and Matchmaking

---

