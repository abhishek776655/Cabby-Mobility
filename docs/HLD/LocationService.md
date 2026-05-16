# 📍 LOCATION SERVICE — HLD + LLD (Smart Mobility)



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

---

### Key Structure

```
drivers:geo        → GEO (driverId → lat/lng)
drivers:available  → SET (online drivers)
```



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



### Get Nearby Drivers

Service → GEOSEARCH → filter via SET → return

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



## 🌐 APIs

### Driver Online

POST /location/driver/online



### Driver Offline

POST /location/driver/offline



### Update Location

POST /location/driver/update


### Get Nearby Drivers

POST /location/nearby



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



### Get Nearby Drivers

```java
GEOSEARCH drivers within radius;
filter using SISMEMBER (availability);
return driverIds;
```



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
