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
* Reserve drivers (offer-window hold, extended to an on-trip hold once accepted) to prevent double-assignment
* Coordinate driver acceptance/rejection
* Publish assignment requests and assignment outcomes
* Retry on reject/timeout
* Release a driver's on-trip reservation when the ride actually completes/cancels (`ride-completed`/`ride-cancelled`)


## 📦 Responsibilities

### Core (v2 Dispatch)

* Consume `ride-requested` events from Kafka
* Find nearby drivers via Location Service — **over-fetches** (`matchmaking.default-limit=40`) because eligibility filtering happens *after* the fetch (see Driver Discovery below)
* Filter eligible drivers (available, not reserved)
* Rank drivers (distance, rating, acceptance rate)
* Reserve driver in Redis (offer-window TTL, `dispatch.assignment.timeout-seconds=20`, capped at `dispatch.assignment.max-retries=10` total attempts)
* Publish `assignment-requested` for realtime fanout
* **Consume driver response via Kafka** (assignment-accepted/rejected)
* Sequential retry on reject/timeout
* On acceptance: **extend** (not release) the reservation for `dispatch.on-trip-reservation-seconds` (safety-net TTL) — the driver is on-trip, not free
* Consume `ride-completed`/`ride-cancelled` from cab-service to release the driver's reservation exactly when the ride ends
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

* Matchmaking → Location Service (nearby drivers) — requires `X-Internal-Secret` header (shared secret, see Security below)
* Matchmaking → Driver Service (driver availability)
* Matchmaking ← Cab Service (GET /internal/dispatch/{id})

### Async (Kafka)

**Consumes:**
```
ride-requested         → from cab-service (trigger matching)
assignment-accepted   → from cab-service (driver accepted via /dispatch/driver-response)
assignment-rejected   → from cab-service (driver rejected - triggers retry, deduped via Redis processed:{eventId})
ride-completed         → from cab-service (release the driver's on-trip reservation)
ride-cancelled         → from cab-service (release the driver's on-trip reservation, if one was assigned)
```

**Produces:**
```
driver-assigned        → to cab-service
matchmaking-failed     → to cab-service
```

### Security — Internal API Auth

`/internal/**` endpoints (this service exposes `/internal/dispatch/{rideId}`) require an `X-Internal-Secret`
header matching `internal.api.secret` — enforced by `InternalApiSecurityFilter`, checked on every request
regardless of network path. Previously the API-Gateway's path-block was the *only* protection; any pod
reachable on the docker network could call it directly. Same pattern now applies to its own outbound
call to Location Service's `/internal/nearby`.


---

## 🧠 Dispatch Flow (v2)

```
RideRequested (Kafka)
        ↓
Find Nearby Drivers (Location Service REST, over-fetch top 40 by distance)
        ↓
Filter Eligible (availability + not reserved)  ← this is where the 40 shrinks;
        ↓                                         fetching only 10 here made the
Rank Drivers (distance + rating)                  post-filter pool collapse to 1-2
        ↓                                         once busy-driver tracking became accurate
Reserve Driver (Redis SETNX, offer-window TTL)
        ↓
Assignment Requested (to driver)
        ↓
Wait for Response (REST API / Kafka)
        ↓
Accept → Extend reservation to on-trip TTL, publish DriverAssigned
Reject → Release, Retry next
Timeout → Release, Retry next
Exhausted → Publish NoDriverFound
        ↓
(later) RideCompleted/RideCancelled (Kafka, from cab-service)
        ↓
Release driver's on-trip reservation
```


---

## 🗄️ Storage Strategy

### PostgreSQL (Persistent State)

* dispatch_sessions - active dispatch state
* assignment_attempts - audit trail
* processed_events - idempotency

### Redis (Ephemeral State)

* driver:{driverId}:reservation - reservation lock. Offer-window TTL (`dispatch.assignment.timeout-seconds`,
  default 30s) while a candidate is being asked; **extended** (not released) to an on-trip safety-net TTL
  (`dispatch.on-trip-reservation-seconds`, default 7200s) once accepted, then explicitly released the moment
  `ride-completed`/`ride-cancelled` arrives. The TTL is only a fallback in case that event is lost.
* dispatch:{dispatchId} - active dispatch cache
* drivers:available:geo - available drivers geo index (shared with location-service)
* processed:{eventId} - Kafka consumer dedup keys (24h TTL) for assignment-accepted/rejected, ride-completed, ride-cancelled


---

### Key Structure

```
PostgreSQL:
dispatch_sessions        → dispatch state & candidates
assignment_attempts      → per-driver attempt audit
processed_events         → idempotency

Redis:
driver:{driverId}:reservation  → SETNX (dispatchId:rideId, offer-window TTL; EXPIRE-extended to on-trip TTL on accept)
dispatch:{dispatchId}          → Hash (status, driverId, expiresAt)
drivers:available:geo         → GEO (online drivers only)
processed:{eventId}            → dedup marker (24h TTL)
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
// Acquire (offer window)
SET driver:{driverId}:reservation {dispatchId}:{rideId} NX EX 30

// Extend on acceptance (on-trip safety net — NOT released here)
EXPIRE driver:{driverId}:reservation 7200   // only if value still starts with dispatchId

// Release (on rejection/timeout, or on ride-completed/ride-cancelled)
DEL driver:{driverId}:reservation           // only if value still starts with dispatchId

// Check
GET driver:{driverId}:reservation
```

> ⚠️ **Fixed bug:** acceptance used to call release immediately, which made an
> accepted-but-still-on-trip driver look free to every other concurrent dispatch a
> moment later. It now extends the TTL and waits for the ride's actual completion/
> cancellation event to release it.


### Retry Logic

```java
for each candidate in ranked:
  if reserve(candidate):
    sendAssignment()
    waitForResponse()
    if accept: extendReservation(onTripTtl); return SUCCESS
    if reject: release(), continue
    if timeout: release(), continue
return FAILED

// separately, on ride-completed/ride-cancelled (rideId → dispatchId via DispatchSessionRepository.findByRideId):
release(driverId, dispatchId)
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

### ride-completed (Consumed, from cab-service)

```json
{
  "eventId": "uuid",
  "eventType": "RIDE_COMPLETED",
  "rideId": "uuid",
  "driverUserId": 1600,
  "riderUserId": 1571,
  "completedAt": "2026-08-08T11:59:11.410937251"
}
```
> Looked up by `rideId` via `DispatchSessionRepository.findByRideId` to recover the
> `dispatchId` needed to release the reservation. Deduped via `processed:{eventId}`.

### ride-cancelled (Consumed, from cab-service)

```json
{
  "eventId": "uuid",
  "eventType": "RIDE_CANCELLED",
  "rideId": "uuid",
  "driverUserId": 1600,
  "cancelledAt": "2026-08-08T11:59:11.410937251"
}
```
> Only published by cab-service if a driver had actually been assigned to the ride.


---

## 🔒 Concurrency & Idempotency

### Double Assignment Prevention

* Redis SETNX ensures only one dispatch can reserve a driver during the offer window
* Reservation key = `driver:{driverId}:reservation`
* Value = `{dispatchId}:{rideId}`
* On acceptance the same key's TTL is **extended** (ownership-checked via the `dispatchId:` prefix), covering
  the ride's real duration instead of the brief offer window
* Released explicitly on `ride-completed`/`ride-cancelled`, with the on-trip TTL as a lost-event safety net

### Idempotency

* Check `processed_events` table before processing ride-requested
* Use dispatchId as idempotency key at dispatch level
* Check dispatch status before processing driver response
* `assignment-accepted`, `assignment-rejected`, `ride-completed`, `ride-cancelled` consumers all dedupe via
  Redis `processed:{eventId}` (24h TTL) before acting, guarding against Kafka redelivery


---

## ⚠️ Failure Handling

| Scenario | Handling |
|----------|----------|
| Redis down | Fail closed → publish NoDriverFound |
| Driver offline during reservation | Release, retry next |
| Driver accepts after timeout | Reject (reservation expired) |
| All candidates exhausted | Publish NoDriverFound |
| Retry count hits `dispatch.assignment.max-retries` (10) | Fail fast → publish NoDriverFound, even if candidates remain — bounds worst-case dispatch tail latency to `max-retries × timeout-seconds` (200s) instead of `discoveryLimit × timeout-seconds` (up to 1200s when default-limit=40) |
| Kafka publish failure | Retry with backoff (`DefaultErrorHandler` + `DeadLetterPublishingRecoverer` → `{topic}.DLQ`, wired on the shared `kafkaListenerContainerFactory`, 3 retries) |
| `ride-completed`/`ride-cancelled` arrives for an unknown dispatch | Logged and skipped — no reservation to release |
| `ride-completed`/`ride-cancelled` never arrives (event lost) | On-trip reservation TTL (default 7200s) expires it anyway |


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
* Redis reservation = **critical for preventing double-assignment — for the whole ride, not just the offer window**
* Sequential retry = **simpler than parallel fanout (v2)**, but under real concurrency each ride only gets
  a bounded number of sequential tries; a candidate pool that's too small after eligibility filtering
  exhausts fast even when other genuinely-nearby drivers exist. Over-fetch before filtering.
* Retry count is explicitly capped (`max-retries=10`), independent of `discoveryLimit`/`default-limit` —
  without this, raising the over-fetch limit (10→40) to fix candidate-pool collapse would have let a
  fully-failed dispatch chew through all 40 candidates sequentially before giving up (up to 20 min tail
  latency). Capping retries and shortening `timeout-seconds` (30→20) bounds that to ~200s.
* State machine = **clear dispatch lifecycle**
* Reuse existing clients = **location-service, driver-service**
* **Driver response flow:** Driver App → Cab Service → Kafka → Matchmaking
* **Reservation lifecycle is now event-driven end to end:** reserve (offer) → extend (accept) → release
  (ride-completed/cancelled), with TTL as a safety net rather than the primary release mechanism
* **Internal endpoints trust a shared secret, not just network position** — closes the gap where any pod
  on the docker network could call `/internal/**` directly, bypassing the gateway entirely
