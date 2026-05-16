# Matchmaking Service Design

## Summary

Build a new `matchmaking-service` that owns driver discovery and assignment decisions for ride requests. It consumes cab-service ride request events, asks location-service for nearby online drivers, verifies driver eligibility through driver-service, selects the best available driver, records assignment audit data, and publishes assignment or failure events.

The service does not own rides, driver profiles, or location storage. Cab-service remains the ride lifecycle owner, driver-service remains the driver profile and availability owner, and location-service remains the Redis GEO source for online nearby driver IDs.

## Current Contracts

The implementation will use the repo's current Kafka topic naming:

- Consumes `ride-requested`
- Produces `driver-assigned`
- Produces `matchmaking-failed`

The implementation will use the repo's current ID model:

- `rideId`: `UUID`
- `riderId`: `UUID`
- `driverId`: `Long`, matching driver-service `userId`

Cab-service ride requests and `RideRequestedEvent` must include:

- `pickupLocation`
- `dropLocation`
- `pickupLatitude`
- `pickupLongitude`
- `dropLatitude`
- `dropLongitude`

Location-service exposes:

- `POST /location/nearby`
- `POST /location/driver/online`
- `POST /location/driver/offline`
- `POST /location/driver/update`

Driver-service exposes:

- `GET /drivers/{userId}`
- `PATCH /drivers/{userId}/availability?available=false`

## Matching Flow

1. Kafka consumer receives `ride-requested`.
2. If `eventId` already exists in `processed_events`, ignore the event.
3. Call location-service `POST /location/nearby` with pickup latitude, pickup longitude, radius, and limit.
4. Location-service returns online nearby driver IDs sorted by proximity.
5. For each candidate driver ID, call driver-service `GET /drivers/{userId}`.
6. Filter out drivers where `available=false`.
7. Score remaining candidates by `rating`.
8. If ratings tie, keep location-service order, so nearer driver wins.
9. Persist assignment attempts for considered candidates.
10. If a driver is selected, call driver-service to mark availability false.
11. Publish `driver-assigned`.
12. Save `processed_events` row after the assignment/failure outcome is recorded.

If no eligible driver is found, matchmaking records a failed attempt and publishes `matchmaking-failed` with reason `NO_DRIVER_AVAILABLE`.

## Storage

`assignment_attempts` stores audit data for candidate selection:

```sql
CREATE TABLE assignment_attempts (
    id BIGSERIAL PRIMARY KEY,
    ride_id UUID NOT NULL,
    driver_id BIGINT,
    score DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(100),
    created_at TIMESTAMP NOT NULL
);
```

`processed_events` stores consumed Kafka event IDs:

```sql
CREATE TABLE processed_events (
    event_id VARCHAR PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
```

## Matchmaking Service Structure

Package layout:

```text
matchmaking-service/
├── client/
├── config/
├── dto/
├── entity/
├── event/
├── exception/
├── kafka/
├── repository/
├── scoring/
├── service/
└── service/impl/
```

Core components:

- `RideRequestedConsumer`: consumes `ride-requested`.
- `MatchmakingService`: orchestrates idempotency, candidate lookup, scoring, assignment, and failure handling.
- `LocationServiceClient`: calls location-service nearby endpoint.
- `DriverServiceClient`: calls driver-service profile and availability endpoints.
- `DriverScoringStrategy`: pluggable strategy interface.
- `RatingDriverScoringStrategy`: v1 scoring by rating only.
- `MatchmakingEventProducer`: publishes `driver-assigned` and `matchmaking-failed`.

## Error Handling

Matchmaking should fail closed:

- Duplicate `eventId`: ignore without producing another event.
- Location-service error: save failed attempt with `LOCATION_SERVICE_UNAVAILABLE`, publish `matchmaking-failed`.
- Driver-service profile error for one driver: skip that candidate and continue.
- Driver-service availability update failure for chosen driver: mark attempt failed with `DRIVER_RESERVATION_FAILED`, try the next eligible candidate if one exists.
- No candidate after retries: publish `matchmaking-failed`.

Kafka consumer retries and DLQ behavior should follow the existing cab/driver service style using `DefaultErrorHandler` and a `.DLQ` topic.

## Ports And Config

Recommended local ports:

- cab-service: `8083`
- driver-service: `8084`
- location-service: `8086`
- matchmaking-service: `8087`

Matchmaking config:

- PostgreSQL DB: `matchmaking_db`
- Kafka bootstrap server: `localhost:9092`
- Location service URL: `http://localhost:8086`
- Driver service URL: `http://localhost:8084`
- Nearby radius default: `5km`
- Nearby candidate limit default: `10`

## Tests

Matchmaking tests:

- Ignores duplicate `ride-requested` events.
- Selects highest-rated available driver from nearby candidates.
- Preserves proximity order as tie-breaker when ratings are equal.
- Skips unavailable drivers.
- Tries next candidate when marking selected driver unavailable fails.
- Publishes `matchmaking-failed` when no eligible driver exists.
- Saves assignment attempts and processed events.

Location-service tests:

- Online flow stores location and marks driver online.
- Nearby lookup validates radius and limit.
- Repository failures map to location-service exceptions.

Cab-service tests:

- Ride creation stores pickup/drop coordinates.
- Ride creation emits `ride-requested` with coordinates.
- `driver-assigned` with `Long driverId` moves ride from `MATCHING` to `DRIVER_ASSIGNED`.

## Explicit Non-Goals For V1

- No driver acceptance timeout engine.
- No Redis distributed locks.
- No ML ranking.
- No ETA calculation.
- No address geocoding.
- No payment or pricing logic.
- No ownership of cab-service ride lifecycle.
