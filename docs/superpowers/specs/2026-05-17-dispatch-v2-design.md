# Matchmaking / Dispatch Service v2 - Design Document

## 1. Executive Summary

This document describes the design for a production-grade Matchmaking/Dispatch System (v2) for a ride-hailing platform. The system is responsible for discovering nearby drivers, evaluating eligibility, reserving drivers, coordinating driver acceptance/rejection, and publishing assignment outcomes.

The v2 design **enhances** the existing matchmaking service by adding:
- Redis-based driver reservation (15-second hold)
- Driver accept/reject flow with timeout handling
- Sequential retry on reject/timeout
- Dispatch session state machine
- REST API for driver responses
- Production-grade observability

**Important:** The existing `MatchmakingServiceImpl` code is preserved and reused. The new `DispatchService` builds upon existing components.

## 1.1 Existing Code Reuse

The following existing components will be reused in v2:

| Component | Location | Usage in v2 |
|-----------|----------|-------------|
| `LocationServiceClient` | `client/LocationServiceClient.java` | Find nearby drivers via REST |
| `DriverServiceClient` | `client/DriverServiceClient.java` | Check driver availability |
| `MatchmakingEventProducer` | `kafka/MatchmakingEventProducer.java` | Publish driver-assigned, matchmaking-failed |
| `DriverScoringStrategy` | `scoring/DriverScoringStrategy.java` | Rank drivers |
| `RideRequestedConsumer` | `kafka/RideRequestedConsumer.java` | Consume ride-requested events |
| `AssignmentAttemptRepository` | `repository/AssignmentAttemptRepository.java` | Audit trail for attempts |
| `ProcessedEventRepository` | `repository/ProcessedEventRepository.java` | Idempotency |

## 2. Integration with Existing Code

### 2.1 Flow Comparison

**Existing v1 Flow (one-shot):**
1. Consume `RideRequested`
2. Find nearby drivers (via LocationServiceClient)
3. Filter available drivers (via DriverServiceClient)
4. Score and pick best driver
5. Mark driver unavailable (via DriverServiceClient)
6. Publish `DriverAssigned` immediately

**New v2 Flow (with reservation):**
1. Consume `RideRequested`
2. Find nearby drivers (via existing LocationServiceClient)
3. Filter available + not-reserved (via existing DriverServiceClient + new ReservationService)
4. Score and rank drivers
5. **NEW:** Reserve driver in Redis (15s hold)
6. **NEW:** Send assignment request to driver
7. **NEW:** Wait for driver response (REST API or Kafka)
8. **NEW:** On accept → publish `DriverAssigned`, release reservation
9. **NEW:** On reject/timeout → release reservation, retry next driver

### 2.2 Code Organization

The v2 implementation adds new code without modifying existing components:
- Existing `MatchmakingServiceImpl` remains unchanged (can be used as fallback)
- New `DispatchServiceImpl` uses existing clients internally
- Decision point: which service to use can be configurable

### 2.3 Existing Services

The platform already contains:
- **Auth Service** - Authentication and authorization
- **User Service** - Rider profile management
- **Driver Service** - Driver profile, availability status, ratings
- **Location Service** - Driver location tracking (Redis GEO source)
- **Ride/Cab Service** - Ride lifecycle management, emits RideRequested events

### 2.4 Event Dependencies

| Event | Source | Description |
|-------|--------|-------------|
| RideRequested | cab-service | Trigger for dispatch flow |
| AssignmentAccepted | driver-service (via REST) | Driver accepts assignment |
| AssignmentRejected | driver-service (via REST) | Driver rejects assignment |

### 2.5 Output Events

| Event | Destination | Description |
|-------|-------------|-------------|
| AssignmentRequested | driver notification | Notification to driver |
| DriverAssigned | cab-service | Successful assignment |
| MatchmakingFailed | cab-service | All candidates exhausted | |

## 3. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MATCHMAKING SERVICE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐ │
│  │   Kafka     │    │   REST      │    │   Redis     │    │ PostgreSQL  │ │
│  │   Consumer  │    │   APIs      │    │   Cluster   │    │   Cluster   │ │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └──────┬──────┘ │
│         │                  │                  │                  │        │
│         └──────────────────┴──────────────────┴──────────────────┘        │
│                                    │                                        │
│                           ┌────────▼────────┐                              │
│                           │ DispatchSession │                              │
│                           │    Manager      │                              │
│                           └────────┬────────┘                              │
│                                    │                                        │
│         ┌──────────────────────────┼──────────────────────────┐            │
│         │                          │                          │            │
│  ┌──────▼──────┐          ┌────────▼────────┐      ┌────────▼────────┐   │
│  │  Candidate  │          │   Reservation   │      │    Assignment   │   │
│  │   Finder    │          │    Manager      │      │    Manager      │   │
│  │ (GEO search)│          │ (Redis lock)   │      │   (timeout)     │   │
│  └──────┬──────┘          └────────┬────────┘      └────────┬────────┘   │
│         │                           │                          │            │
│         │              ┌────────────┼────────────┐            │            │
│         │              │            │            │            │            │
│  ┌──────▼──────┐ ┌─────▼────┐ ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐     │
│  │  Driver    │ │ Driver   │ │   Retry   │ │ Timeout │ │   Event   │     │
│  │  Filter    │ │  Ranker  │ │Orchestrator│ │Scheduler│ │  Publisher │     │
│  └─────────────┘ └──────────┘ └───────────┘ └──────────┘ └────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 4. Core Components

### 4.1 DispatchSessionManager

The central orchestrator maintaining dispatch state.

**Responsibilities:**
- Create and manage dispatch sessions
- Track current candidate, remaining candidates
- Handle state transitions
- Coordinate the dispatch flow

**State Machine:**

```
SEARCHING ──► ASSIGNMENT_SENT ──► RETRYING ──► ASSIGNED
                │                   │
                │                   ▼
                │                 FAILED
                │                   │
                ▼                   ▼
              CANCELLED ◄────────────┘
```

**States:**
- `SEARCHING` - Finding and evaluating candidates
- `ASSIGNMENT_SENT` - Driver reservation active, waiting for response
- `RETRYING` - Previous candidate failed, trying next
- `ASSIGNED` - Driver accepted, dispatch complete
- `FAILED` - All candidates exhausted
- `CANCELLED` - Dispatch cancelled by user

### 4.2 CandidateFinder (Reuses Existing LocationServiceClient)

Uses the existing `LocationServiceClient` to call location-service REST API.

**Algorithm:**
1. Call `LocationServiceClient.findNearbyDrivers()` with lat, lng, radius, limit
2. Location service returns driver IDs sorted by distance (nearest first)
3. Configuration: default radius 5km, default limit 10

**Note:** The existing `LocationServiceClient` wraps REST calls to `location-service`. The v2 implementation delegates to this client rather than directly using Redis GEO.

### 4.3 DriverFilter (Reuses Existing DriverServiceClient)

Filters candidates based on eligibility criteria using existing clients.

**Filters:**
- Status: ONLINE (from location service)
- Availability: AVAILABLE = true (via existing `DriverServiceClient.getDriver()`)
- Not Reserved: No active reservation in Redis (via new `ReservationService`)

**Implementation:** Calls existing `DriverServiceClient.getDriver(driverId)` to check availability before attempting reservation.

### 4.4 DriverRanker

Scores drivers using Strategy Pattern for extensibility.

**Current Strategy (MultiFactorRankingStrategy):**

```
score = (1 / (1 + distance_km)) * distance_weight
      + rating * rating_weight
      + idle_minutes * idle_weight
      + acceptance_rate * acceptance_weight
```

**Default Weights:**
- distance_weight: 0.4
- rating_weight: 0.3
- idle_weight: 0.15
- acceptance_weight: 0.15

**Architecture supports:**
- Pluggable scoring strategies
- Future ML-based ranking (PredictionStrategy)
- A/B testing via strategy selection

### 4.5 ReservationManager

Critical component preventing double-assignment.

**Mechanism:**
- Redis SETNX with 15-second TTL
- Key: `driver:{driverId}:reservation`
- Value: `{dispatchId}:{rideId}`
- Auto-release on timeout or explicit release

**Reservation States:**
- ACTIVE - Driver reserved, awaiting response
- RELEASED - Reservation cleared (accept/reject/timeout)
- EXPIRED - TTL naturally expired

### 4.6 AssignmentManager

Manages the assignment request/response lifecycle.

**Flow:**
1. Publish AssignmentRequested event to driver
2. Store pending assignment with timeout timer
3. Wait for response via REST API
4. On response: validate reservation, process outcome

**Timeout Handling:**
- Default: 15 seconds
- On timeout: emit AssignmentTimedOut, release reservation, trigger retry
- Distributed-safe: use Redis TTL, not in-memory timers

### 4.7 RetryOrchestrator

Sequential retry logic for failed candidates.

**Algorithm:**
```
for each candidate in ranked_list:
    try_reserve(candidate)
    if success:
        send_assignment()
        wait_for_response()
        if accept: return SUCCESS
        if reject: release_and_continue()
        if timeout: release_and_continue()
    if failure: continue to next
return FAILED
```

**Constraints:**
- Max retry count: configurable (default: 10)
- No parallel fanout in v2
- Exponential backoff between attempts (optional)

### 4.8 TimeoutScheduler

Handles distributed timeout tracking.

**Implementation:**
- Uses Redis TTL on reservation key as source of truth
- Scheduled task polls for expired reservations
- Triggers retry logic when expiration detected

**Alternative (Simpler):**
- Driver response timeout handled synchronously in REST handler
- No separate scheduler needed
- Scheduled task for cleanup only

### 4.9 EventPublisher (Reuses Existing MatchmakingEventProducer)

Kafka event publishing using existing producer component.

**Outbound Events:**
- DriverAssigned: via existing `MatchmakingEventProducer.publishDriverAssigned()`
- MatchmakingFailed: via existing `MatchmakingEventProducer.publishMatchmakingFailed()`
- AssignmentRequested: via new Kafka producer (to driver notification topic)

**Note:** The existing `MatchmakingEventProducer` is reused for publishing assignment outcomes.

## 5. Data Flow

### 5.1 Consumer Integration

The existing `RideRequestedConsumer` is updated to delegate to the new `DispatchService`:

```java
// Existing consumer - updated implementation
@KafkaListener(topics = "ride-requested", groupId = "matchmaking-service-group")
public void consume(String message) {
    RideRequestedEvent event = objectMapper.readValue(message, RideRequestedEvent.class);
    
    // Option 1: Use new DispatchService (v2 flow)
    dispatchService.startDispatch(event);
    
    // Option 2: Keep existing MatchmakingService (v1 flow)
    // matchmakingService.matchRide(event);
}
```

### 5.2 Happy Path Sequence

```
┌────────┐    ┌──────────────┐    ┌─────────┐    ┌───────────┐    ┌────────┐
│  Cab   │    │ Matchmaking  │    │  Redis  │    │   Driver  │    │  Cab   │
│ Service│    │    Service   │    │         │    │  Service  │    │ Service│
└───┬────┘    └──────┬───────┘    └────┬────┘    └─────┬─────┘    └───┬────┘
    │                │                  │               │              │
    │ RideRequested  │                  │               │              │
    │───────────────►│                  │               │              │
    │                │                  │               │              │
    │                │ GEOSEARCH        │               │              │
    │                │─────────────────►│               │              │
    │                │◄─────────────────│ (driver IDs)   │              │
    │                │                  │               │              │
    │                │ getDriver()      │               │              │
    │                │─────────────────────────────────►│              │
    │                │◄─────────────────────────────────│ (driver data)│
    │                │                  │               │              │
    │                │ SETNX reservation│               │              │
    │                │─────────────────►│               │              │
    │                │◄─────────────────│ (OK)           │              │
    │                │                  │               │              │
    │                │ AssignmentRequested              │              │
    │                │─────────────────────────────────►│              │
    │                │                  │               │              │
    │                │                  │       (15s)   │              │
    │                │                  │               │              │
    │                │                  │    Accept      │              │
    │                │◄─────────────────────────────────│─────────────│
    │                │                  │               │              │
    │                │ DELETE reservation│              │              │
    │                │─────────────────►│               │              │
    │                │ DriverAssigned    │              │              │
    │                │─────────────────────────────────►│              │
    │                │                  │               │              │
```

### 5.2 Retry Path Sequence

```
Driver Rejects Assignment
    │
    ▼
Release Redis Reservation
    │
    ▼
Check: More Candidates?
    │
    ├── YES → Reserve Next Driver → Send Assignment → Wait
    │
    └── NO → Publish NoDriverFound → End
```

## 6. Database Design

### 6.1 PostgreSQL Schema

```sql
-- Dispatch sessions - main dispatch state
CREATE TABLE dispatch_sessions (
    dispatch_id UUID PRIMARY KEY,
    ride_id UUID NOT NULL,
    rider_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_driver_id BIGINT,
    remaining_candidates JSONB,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_status CHECK (status IN (
        'SEARCHING', 'ASSIGNMENT_SENT', 'RETRYING',
        'ASSIGNED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX idx_dispatch_ride_id ON dispatch_sessions(ride_id);
CREATE INDEX idx_dispatch_status ON dispatch_sessions(status);
CREATE INDEX idx_dispatch_expires ON dispatch_sessions(expires_at) WHERE status = 'ASSIGNMENT_SENT';

-- Assignment attempts - audit trail
CREATE TABLE assignment_attempts (
    id BIGSERIAL PRIMARY KEY,
    dispatch_id UUID NOT NULL REFERENCES dispatch_sessions(dispatch_id),
    driver_id BIGINT NOT NULL,
    score DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(100),
    reservation_key VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_attempt_status CHECK (status IN (
        'RESERVED', 'ASSIGNMENT_SENT', 'ACCEPTED',
        'REJECTED', 'TIMEOUT', 'FAILED'
    ))
);

CREATE INDEX idx_attempt_dispatch ON assignment_attempts(dispatch_id);
CREATE INDEX idx_attempt_driver ON assignment_attempts(driver_id);

-- Processed events - idempotency
CREATE TABLE processed_events (
    event_id VARCHAR(200) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_type ON processed_events(event_type);

-- Driver reservations audit (optional)
CREATE TABLE driver_reservations (
    id BIGSERIAL PRIMARY KEY,
    dispatch_id UUID NOT NULL,
    driver_id BIGINT NOT NULL,
    ride_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    released_at TIMESTAMP,
    release_reason VARCHAR(50)
);

CREATE INDEX idx_reservation_dispatch ON driver_reservations(dispatch_id);
CREATE INDEX idx_reservation_driver ON driver_reservations(driver_id);
```

### 6.2 Entity Classes

```java
// DispatchSession entity
@Entity
@Table(name = "dispatch_sessions")
public class DispatchSession {
    @Id
    private UUID dispatchId;
    private UUID rideId;
    private UUID riderId;
    @Enumerated(EnumType.STRING)
    private DispatchStatus status;
    private Long currentDriverId;
    @Column(columnDefinition = "jsonb")
    private List<Long> remainingCandidates;
    private Integer retryCount;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant updatedAt;
}

// AssignmentAttempt entity
@Entity
@Table(name = "assignment_attempts")
public class AssignmentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID dispatchId;
    private Long driverId;
    private Double score;
    @Enumerated(EnumType.STRING)
    private AttemptStatus status;
    private String failureReason;
    private String reservationKey;
    private Instant createdAt;
}

// Enums
public enum DispatchStatus {
    SEARCHING, ASSIGNMENT_SENT, RETRYING, ASSIGNED, FAILED, CANCELLED
}

public enum AttemptStatus {
    RESERVED, ASSIGNMENT_SENT, ACCEPTED, REJECTED, TIMEOUT, FAILED
}
```

## 7. Redis Design

### 7.1 Key Structure

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `driver:{driverId}:location` | GEO | None | Driver positions |
| `driver:{driverId}:reservation` | String | 15s | Reservation lock |
| `dispatch:{dispatchId}` | Hash | 5min | Active dispatch state |
| `ride:{rideId}:attempts` | List | 1hr | Attempted driver IDs |

### 7.2 GEO Indexing

**Driver Location Updates:**
- Driver app sends location update to location-service
- location-service updates Redis GEO key: `driver:locations`
- Key format: `GEOADD driver:locations lon lat driverId`

**Nearby Search:**
```
GEOSEARCH driver:locations FROMLONLAT {lng} {lat} BYRADIUS {radius} KM ASC
```

### 7.3 Reservation Lock

**Acquire Reservation:**
```
SET driver:{driverId}:reservation {dispatchId}:{rideId} NX EX 15
```

**Release Reservation:**
- On accept: DELETE key
- On reject: DELETE key
- On timeout: TTL expires naturally

**Check Reservation:**
```
GET driver:{driverId}:reservation
```

### 7.4 Active Dispatch Cache

```java
// Store current dispatch state in Redis for fast access
HSET dispatch:{dispatchId} status "ASSIGNMENT_SENT"
HSET dispatch:{dispatchId} driverId "12345"
HSET dispatch:{dispatchId} expiresAt "1700000000000"
```

## 8. Kafka Topic Design

### 8.1 Topics

| Topic | Partitions | Replication | Description |
|-------|------------|-------------|-------------|
| `ride-requested` | 6 | 1 | In - from cab-service |
| `driver-assigned` | 6 | 1 | Out - to cab-service |
| `no-driver-found` | 6 | 1 | Out - to cab-service |
| `assignment-requested` | 6 | 1 | Out - to driver-service |
| `assignment-accepted` | 6 | 1 | In - from driver-service |
| `assignment-rejected` | 6 | 1 | In - from driver-service |

### 8.2 Event Schemas

**RideRequested:**
```json
{
  "eventId": "uuid",
  "eventType": "RIDE_REQUESTED",
  "rideId": "uuid",
  "riderId": "uuid",
  "pickupLocation": "string",
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "dropLocation": "string",
  "dropLatitude": 40.7580,
  "dropLongitude": -73.9855,
  "requestedAt": "2026-05-17T10:00:00Z"
}
```

**AssignmentRequested:**
```json
{
  "eventId": "uuid",
  "eventType": "ASSIGNMENT_REQUESTED",
  "dispatchId": "uuid",
  "rideId": "uuid",
  "driverId": 12345,
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "pickupLocation": "123 Main St",
  "estimatedDistanceKm": 2.5,
  "expiresAt": "2026-05-17T10:00:15Z"
}
```

**AssignmentAccepted:**
```json
{
  "eventId": "uuid",
  "eventType": "ASSIGNMENT_ACCEPTED",
  "dispatchId": "uuid",
  "rideId": "uuid",
  "driverId": 12345,
  "acceptedAt": "2026-05-17T10:00:10Z"
}
```

**DriverAssigned:**
```json
{
  "eventId": "uuid",
  "eventType": "DRIVER_ASSIGNED",
  "rideId": "uuid",
  "driverId": 12345,
  "assignedAt": "2026-05-17T10:00:10Z"
}
```

**NoDriverFound:**
```json
{
  "eventId": "uuid",
  "eventType": "NO_DRIVER_FOUND",
  "rideId": "uuid",
  "reason": "NO_DRIVER_AVAILABLE",
  "attempts": 10,
  "failedAt": "2026-05-17T10:00:30Z"
}
```

## 9. REST API Design

### 9.1 Endpoints

**POST /dispatch/driver-response**
```json
Request:
{
  "dispatchId": "uuid",
  "driverId": 12345,
  "response": "ACCEPT" | "REJECT"
}

Response (200 OK):
{
  "success": true,
  "message": "Assignment accepted"
}

Response (409 Conflict):
{
  "success": false,
  "error": "RESERVATION_EXPIRED"
}
```

**POST /dispatch/cancel**
```json
Request:
{
  "rideId": "uuid",
  "reason": "USER_CANCELLED"
}

Response (200 OK):
{
  "success": true,
  "status": "CANCELLED"
}
```

**GET /dispatch/{rideId}**
```json
Response (200 OK):
{
  "dispatchId": "uuid",
  "rideId": "uuid",
  "status": "ASSIGNED",
  "driverId": 12345,
  "createdAt": "2026-05-17T10:00:00Z"
}
```

### 9.2 Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 404 | DISPATCH_NOT_FOUND | No active dispatch for ride |
| 409 | RESERVATION_EXPIRED | Driver reservation timed out |
| 409 | ALREADY_ASSIGNED | Ride already assigned to another driver |
| 400 | INVALID_STATE | Cannot process in current state |
| 409 | DRIVER_MISMATCH | Driver ID doesn't match dispatch |

## 10. Package Structure

**Legend:** (existing) = already exists, (new) = new in v2

```
com.smartmobility.matchmaking/
├── config/
│   ├── KafkaConsumerConfig.java (existing)
│   ├── KafkaProducerConfig.java (existing)
│   ├── RedisConfig.java (new)
│   ├── RestClientConfig.java (existing)
│   └── MatchmakingProperties.java (new)
├── controller/
│   └── DispatchController.java (new)
├── domain/
│   ├── DispatchStatus.java (new)
│   └── AttemptStatus.java (new)
├── dto/
│   ├── ApiResponse.java (existing)
│   ├── DriverResponseDTO.java (existing)
│   ├── NearbyDriversRequest.java (existing)
│   ├── DriverResponseRequest.java (new)
│   ├── CancelDispatchRequest.java (new)
│   └── DispatchStatusResponse.java (new)
├── entity/
│   ├── DispatchSessionEntity.java (new)
│   ├── AssignmentAttemptEntity.java (existing - new columns)
│   ├── AssignmentStatus.java (existing)
│   └── ProcessedEvent.java (existing)
├── repository/
│   ├── DispatchSessionRepository.java (new)
│   ├── AssignmentAttemptRepository.java (existing)
│   └── ProcessedEventRepository.java (existing)
├── service/
│   ├── MatchmakingService.java (existing)
│   ├── DispatchService.java (new interface)
│   └── impl/
│       ├── MatchmakingServiceImpl.java (existing)
│       └── DispatchServiceImpl.java (new)
├── strategy/
│   ├── DriverScoringStrategy.java (existing)
│   └── RatingDriverScoringStrategy.java (existing)
├── kafka/
│   ├── consumer/
│   │   ├── RideRequestedConsumer.java (existing)
│   │   ├── AssignmentAcceptedConsumer.java (new)
│   │   └── AssignmentRejectedConsumer.java (new)
│   └── producer/
│       ├── MatchmakingEventProducer.java (existing)
│       └── MatchmakingEventProducerImpl.java (existing)
├── redis/
│   ├── ReservationService.java (new)
│   └── DispatchCacheService.java (new)
├── scheduler/
│   └── DispatchTimeoutScheduler.java (new)
├── event/
│   ├── RideRequestedEvent.java (existing)
│   ├── DriverAssignedEvent.java (existing)
│   ├── MatchmakingFailedEvent.java (existing)
│   ├── AssignmentRequestedEvent.java (new)
│   ├── AssignmentAcceptedEvent.java (new)
│   └── AssignmentRejectedEvent.java (new)
├── exception/
│   ├── DispatchException.java (new)
│   ├── DispatchNotFoundException.java (new)
│   ├── ReservationExpiredException.java (new)
│   └── InvalidDispatchStateException.java (new)
└── MatchmakingServiceApplication.java (existing - add @EnableScheduling)
```

## 11. Observability

### 11.1 Logging

Structured JSON logging with correlation IDs:
```json
{
  "timestamp": "2026-05-17T10:00:00.000Z",
  "level": "INFO",
  "traceId": "abc-123",
  "service": "matchmaking-service",
  "event": "dispatch_started",
  "rideId": "uuid",
  "candidatesFound": 15
}
```

### 11.2 Metrics (Prometheus)

| Metric | Type | Description |
|--------|------|-------------|
| `dispatch_total` | Counter | Total dispatch attempts |
| `dispatch_success_total` | Counter | Successful assignments |
| `dispatch_failure_total` | Counter | Failed dispatches |
| `dispatch_duration_seconds` | Histogram | End-to-end dispatch time |
| `assignment_duration_seconds` | Histogram | Assignment request to response |
| `retry_count` | Summary | Retries per dispatch |
| `no_driver_rate` | Gauge | % of no-driver-found |
| `reservation_conflicts` | Counter | Double-assignment attempts |
| `kafka_consumer_lag` | Gauge | Consumer lag |

### 11.3 Tracing

OpenTelemetry integration:
- Trace dispatch flow across services
- Propagate trace context via Kafka headers
- Add span for each step: geo-search, ranking, reservation, assignment

## 12. Failure Handling

### 12.1 Failure Scenarios

| Scenario | Handling |
|----------|----------|
| Duplicate Kafka event | Check processed_events, skip if exists |
| Redis GEO down | Fail closed - publish NoDriverFound |
| Redis lock acquire fail | Skip driver, try next candidate |
| Driver goes offline | Release reservation, retry next |
| Driver accepts after timeout | Reject - reservation expired |
| Driver late reject | Release, move to next |
| Kafka publish failure | Retry with backoff, DLQ on exhaustion |
| DB write failure | Retry transaction, fail if persistent |

### 12.2 Recovery Strategies

**Redis Outage:**
- Fail closed: publish NoDriverFound
- Log for monitoring/alerting

**Kafka Consumer Failure:**
- Use Spring Kafka retry with backoff
- Dead Letter Queue for failed messages
- Manual replay from DLQ after fix

**Matchmaking Restart:**
- Read active dispatch_sessions from DB on startup
- Resume incomplete dispatches
- Use Redis TTL for timeout cleanup

## 13. Idempotency

### 13.1 Implementation

1. **Event-Level:** Check `processed_events` table before processing
2. **Dispatch-Level:** Use `dispatchId` as idempotency key
3. **Assignment-Level:** Prevent duplicate accept

### 13.2 Idempotency Keys

- RideRequested: `eventId` from event
- Driver response: `dispatchId` + `driverId` combo
- AssignmentAccepted: check if dispatch already ASSIGNED

## 14. Scalability Strategy

### 14.1 Kafka Partitioning

- Partition by `rideId` (or cityId when available)
- Ensures per-ride ordering
- 6 partitions for parallelism

### 14.2 Redis Clustering

- Redis Cluster mode for GEO data
- Hash slots for driver locations
- Read replicas for read-heavy workload

### 14.3 Geo Sharding

- Partition by city/zone
- Each city has dedicated dispatch queue
- Reduces cross-region driver matching

### 14.4 Horizontal Scaling

- Stateless service instances
- Auto-scale based on Kafka consumer lag
- Load balance via Kubernetes

## 15. Security

- API authentication via gateway
- Driver authorization: verify driver owns dispatch
- Input validation on all endpoints
- Rate limiting on driver response endpoint

## 16. Configuration

```yaml
matchmaking:
  discovery:
    default-radius-km: 5
    max-candidates: 10
  assignment:
    timeout-seconds: 15
    max-retries: 10
  redis:
    geo-key: "driver:locations"
    reservation-ttl-seconds: 15
  kafka:
    consumer-group: "matchmaking-service-group"
```

## 17. Future Extensions

The architecture supports future enhancements:

### 17.1 Surge Pricing
- Inject surge multiplier into ranking
- Filter by pricing tier preference

### 17.2 ML Ranking
- Replace heuristic strategy with ML model
- A/B test ranking strategies

### 17.3 Batch Fanout
- Send to N drivers simultaneously
- First accept wins

### 17.4 ETA Prediction
- Integrate ETA service
- Weight ETA into ranking

### 17.5 Multi-Stop Routing
- Support multiple pickup/dropoff
- Optimize route ordering

### 17.6 Real-time Updates
- WebSocket to rider app
- Push driver location updates

## 18. Non-Goals For V2

- No multi-driver fanout (sequential only)
- No surge pricing
- No ETA prediction
- No ML ranking (heuristic-based)
- No WebSocket updates

## 19. Testing Strategy

### 19.1 Unit Tests
- DriverRanker scoring logic
- ReservationManager lock/unlock
- RetryOrchestrator retry flow

### 19.2 Integration Tests
- Kafka consumer/producer
- Redis operations
- REST API endpoints

### 19.3 End-to-End Tests
- Full dispatch flow
- Timeout handling
- Retry behavior