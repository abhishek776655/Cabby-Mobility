# 📍 LOCATION SERVICE — HLD + LLD (Smart Mobility)

## Service Configuration

* **Port:** 8086
* **Data Store:** Redis (GEO + SET)
* **Clients:** Matchmaking Service, Driver Service


# 🏗️ High Level Design (HLD)

## 🎯 Purpose

Location Service manages the **real-time driver location & availability lifecycle**:

* Driver location ingestion (lat, lng)
* Real-time availability state (online/offline)
* Geo-spatial indexing for fast lookup
* Serving nearby drivers for matchmaking



## 📦 Responsibilities

### Core

* Ingest driver location updates (WebSocket / REST)
* Maintain driver geo index (Redis GEO)
* Manage availability state (online/offline)
* Provide low-latency nearby driver lookup


### Boundaries

* ❌ No driver profile management (handled by driver-service)
* ❌ No ride lifecycle (handled by cab-service)
* ❌ No matchmaking logic (handled by matchmaking-service)
* ❌ No persistent storage (Redis is ephemeral state store)

---

## 🔗 Inter-Service Communication

### Sync (REST)

* Driver App → Location Service (location updates)
* Matchmaking Service → Location Service (nearby drivers)

---

### Async (Kafka — Future)

**Produces:**

```
driver.location.updated
driver.status.changed
```

**Consumes:**

```
(none currently)
```

---

## 🧠 Driver Location Flow

```
Driver → (WebSocket/REST) → Location Service
        ↓
Location Service → GEOADD (Redis)
        ↓
Location Service → SADD (availability)
```

---

## 🧠 Nearby Driver Query Flow

```
Matchmaking → Location Service
        ↓
GEOSEARCH (Redis)
        ↓
Filter using SET (availability)
        ↓
Return driverIds
```

---

## 🗄️ Storage Strategy

### Redis (Primary)

* GEO index → driver locations
* SET → available drivers
* Available GEO → online driver locations (optimized)

---

### Key Structure (v2 Optimized)

```
drivers:geo              → GEO (driverId → lat/lng) - ALL drivers
drivers:available        → SET (online drivers) - for quick check
drivers:available:geo    → GEO (driverId → lat/lng) - ONLINE drivers only
```

**v2 Optimization:** The `drivers:available:geo` key stores only online drivers, eliminating the N+1 Redis call problem. `getNearbyDrivers` now searches directly in this key - no filtering needed.



### (Optional Future)

* Redis Cluster (horizontal scaling)
* Kafka (event streaming)
* Cold storage (Postgres for audit)



## ⚙️ High-Level Flow

### Driver Online

Driver → Location Service → GEOADD → SADD



### Driver Offline

Driver → Location Service → SREM



### Location Update

Driver → Location Service → GEOADD (overwrite)



### Get Nearby Drivers (v2 Optimized)

Service → GEOSEARCH (drivers:available:geo) → return (no filtering needed)

---

# 🧱 Low Level Design (LLD)


## 📁 Package Structure

```
location-service/
├── controller/
├── service/
├── service/impl/
├── repository/
├── dto/
├── mapper/
├── config/
├── constants/
├── exception/
```



## 🗄️ Data Model (Redis)

### GEO Index

```text
Key: drivers:geo
Type: GEO (ZSET internally)
Value: driverId → lat/lng
```

---

### Availability

```text
Key: drivers:available
Type: SET
Value: driverId
```

---

### Available Drivers GEO (v2)

```text
Key: drivers:available:geo
Type: GEO
Value: driverId → lat/lng (only online drivers)
```



## 🌐 APIs

### Public (Driver App)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/location/driver/online` | POST | Driver goes online |
| `/location/driver/offline` | POST | Driver goes offline |
| `/location/driver/update` | POST | Update driver location |

### Internal (Matchmaking Service only)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/location/nearby` | POST | Find nearby drivers (not exposed to external clients) |



## ⚙️ Service Logic



### Go Online

```java
validate coordinates;
upsert location (GEOADD);
mark driver online (SADD);
```



### Update Location

```java
validate coordinates;
GEOADD (overwrite);
```



### Go Offline

```java
SREM driver from availability set;
```



### Get Nearby Drivers (v2 Optimized)

```java
// v1: N+1 problem
GEOSEARCH drivers:geo radius;
for each driver: SISMEMBER drivers:available (N calls!);
return;

// v2: Single call
GEOSEARCH drivers:available:geo radius;  // Only online drivers
return;
```

**Performance Improvement:** Redis calls reduced from N+1 to 1



## 📡 Kafka Events (Future)

### driver.location.updated

```json
{
  "driverId": "123",
  "lat": 28.61,
  "lng": 77.20,
  "timestamp": "..."
}
```



### driver.status.changed

```json
{
  "driverId": "123",
  "status": "ONLINE"
}
```



## ⚡ Kafka Flow (Future)

```
Location Service → driver.location.updated → Matchmaking
Location Service → driver.status.changed → Other services
```



## 🔒 Concurrency

### Idempotency

```
GEOADD → idempotent (overwrite)
SADD   → idempotent
SREM   → idempotent
```



## 🧠 Patterns Used

* In-memory data store (Redis)
* Repository Pattern
* Service Layer Pattern
* Real-time streaming (WebSocket)
* Event-driven architecture (future Kafka)



## ⚠️ Failure Handling

* Redis failure → request fails (client retry expected)
* No fallback DB (by design)
* Future: retry + circuit breaker



## 🔑 Key Insights

* Location Service = **real-time state engine (not source of truth)**
* Redis = **primary datastore (not cache)**
* GEO + SET separation = **critical for performance**
* Designed for **low latency + high throughput**
