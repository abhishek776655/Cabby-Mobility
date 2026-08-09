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
