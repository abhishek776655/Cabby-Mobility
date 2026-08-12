# Notification Service

## Responsibilities
The Notification Service acts as the centralized outbound messaging system for the Smart Mobility platform. It listens for domain events via Kafka and logs a delivery record for riders and drivers.

Currently implemented as a log-only stub, it persists notification intent to the database, acting as the foundation for future integration with real third-party SMS, Email, or Push Notification providers.

## Architecture
- **Event-Driven:** Purely consumes events from Kafka; exposes no REST APIs.
- **Idempotent:** Guards against duplicate event processing using a `UNIQUE(event_id)` database constraint.
- **Resilient:** Unparseable messages or transient errors are routed to Dead Letter Queues (DLQ) after 3 retries.

## Consumed Kafka Events

| Topic | Consumer | Purpose |
| :--- | :--- | :--- |
| `ride-requested` | `NotificationService` | Notifies the rider that matchmaking has started. |
| `driver-assigned` | `NotificationService` | Notifies the rider that a driver was found. |
| `matchmaking-failed` | `NotificationService` | Notifies the rider that no driver was found. |
| `ride-cancelled` | `NotificationService` | Notifies the rider that the ride was cancelled. |
| `ride-completed` | `NotificationService` | Notifies the rider that the ride is finished. |
| `assignment-requested` | `NotificationService` | Notifies the driver of a new ride offer. |

## Failure Handling
- **Idempotency:** Natural key (`UNIQUE(event_id)`) guarantees exactly-once processing even if Kafka delivers the same event multiple times.
- **Dead Letter Queue (DLQ):** Messages that fail deserialization or exceed maximum retries are routed to `<topic>.DLQ`.
- **Metrics:** Increments `business.notifications.sent` and `business.notifications.failed` for Prometheus tracking.

## Service Configuration
- **Port:** 8096
- **Database:** `notification_db`

---

# Low Level Design (LLD)

## Package Structure
```
notification-service/src/main/java/com/smartmobility/notification_service/
├── config/            # KafkaConsumerConfig, KafkaErrorHandlerConfig (DLQ + retry policy)
├── event/             # RideRequestedEvent, DriverAssignedEvent, MatchmakingFailedEvent,
│                      # RideCancelledEvent, RideCompletedEvent, AssignmentRequestedEvent
├── kafka/consumer/    # one @KafkaListener class per topic (1:1 with event/ classes)
├── entity/            # NotificationEntity (JPA)
├── repository/        # NotificationRepository
└── service/
    └── impl/          # NotificationDeliveryServiceImpl
```
One consumer class per Kafka topic — each deserializes its own event type, then delegates to the shared `NotificationDeliveryService.deliver(...)`. No fan-out/routing logic beyond that; each consumer hardcodes its own channel + message text (e.g. `DriverAssignedNotificationConsumer` always sends `PUSH` / "Your driver has been assigned").

## Data Model (Postgres — `notification_db`)
```sql
notifications
  id           BIGSERIAL PK
  event_id     VARCHAR UNIQUE NOT NULL   -- idempotency key
  user_id      BIGINT
  channel      VARCHAR                   -- e.g. PUSH (only channel currently used)
  event_type   VARCHAR                   -- e.g. DRIVER_ASSIGNED
  message      VARCHAR
  status       VARCHAR                   -- SENT | FAILED
  created_at   TIMESTAMP
```

## Service Logic — `NotificationDeliveryServiceImpl.deliver(...)`
```
1. if notifications.exists(event_id): log + return   // idempotency short-circuit
2. log "Would notify user {userId} via {channel}: {message}"   // stub for real provider
3. try: save NotificationEntity(status=SENT); increment business.notifications.sent
4. on DataIntegrityViolationException (race on unique event_id): treat as duplicate, no-op
5. on any other exception: increment business.notifications.failed; best-effort save
   a second NotificationEntity(status=FAILED) (swallow failure if this also fails)
```
Delivery is **log-only** — step 2 is where a real SMS/Email/Push provider integration would plug in; today it never actually reaches a user.

## Kafka Consumer Config
- `KafkaErrorHandlerConfig`: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`, `FixedBackOff(2000ms, 3 retries)`, non-retryable on `IllegalArgumentException` / `InvalidFormatException` (bad payloads go straight to `<topic>.DLQ` instead of retrying).
- Each consumer wraps deserialization in try/catch and rethrows `IllegalArgumentException` on parse failure, which the error handler above routes straight to DLQ (no retry, since malformed messages won't fix themselves).

## Known Gap
No real notification channel is wired — `NotificationDeliveryServiceImpl` only persists a row and logs; there's no SMS/Email/Push provider client (Twilio, FCM, SES, etc.) anywhere in the package. This is fine for the current stage but should be called out explicitly as a v2 item rather than left implicit.
