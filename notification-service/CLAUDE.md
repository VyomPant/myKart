# notification-service — Claude Code Context

**Port**: 8085 | **DB**: none | **Status**: Phase 1 — fully implemented

## Responsibility

Pure Kafka consumer. Reacts to order and payout events and sends user-facing
notifications. No database, no REST API, no state — intentionally the simplest
service in the system.

Currently all notification delivery is **stubbed** (log only). Real implementations
would call SendGrid (email) or Twilio (SMS) here.

## Topics Consumed

| Topic | Event type | Who receives notification |
|-------|-----------|--------------------------|
| `order.confirmed` | `OrderConfirmedEvent` | Buyer (order confirmation) + Seller (new sale) |
| `order.cancelled` | `OrderCancelledEvent` | Buyer (cancellation notice) |
| `payout.completed` | `PayoutCompletedEvent` | Seller (payout disbursed) |

All three listeners are in `OrderEventListener.java` (the class name is a historical
artefact — it handles payout events too and should be renamed `EventListener` if
ever refactored).

## Kafka Consumer Design

**Deserialization**: All three listeners consume `String` (raw JSON), then manually
call `ObjectMapper.readValue()` to bind to the event record. This is intentional:
using `StringDeserializer` means a malformed payload logs the raw string before the
parse fails, which is easier to debug than a Kafka deserializer stack trace.

**Error handling**: `KafkaConfig` wires a `DefaultErrorHandler` with
`FixedBackOff(1000ms, 2 retries)`. On three consecutive failures, Spring Kafka sends
the message to the default dead-letter topic (suffix `.DLT`) and moves on. Without
this, one bad message would stall the entire partition.

**At-least-once delivery**: Kafka consumer offsets are committed after the listener
returns. If the process crashes mid-send, the event will be redelivered. Notification
handlers must be idempotent (sending a duplicate email is acceptable; double-charging
is not — that's handled upstream in payment-service).

## Key Files

```
listener/OrderEventListener.java   — @KafkaListener for all three topics
config/KafkaConfig.java            — StringDeserializer + DefaultErrorHandler (2 retries)
resources/application.yml         — port 8085, Kafka bootstrap, Eureka, OTel
```

## Extending This Service

To wire a real email provider (e.g. SendGrid):
1. Add `spring-boot-starter-mail` or SendGrid SDK dependency to `pom.xml`
2. Add a `NotificationSender` interface with `sendEmail(to, subject, body)` + `sendSms(to, message)`
3. Inject into `OrderEventListener` — the listener already has all required fields
   from the event records (`buyerId`, `sellerId`, `orderNumber`, `amount`, `channel`)
4. Keep deserialization in the listener, delegate delivery to the sender — easier to test

## Gotchas

- The service has **no DB** — do not add `spring-boot-starter-data-jpa` or a datasource
  config. If notification state (e.g. delivery receipts) is ever needed, add a separate
  table and Flyway migration rather than reusing any existing service's DB.
- `OrderEventListener` re-throws as `RuntimeException` on parse failure. This is correct
  — it triggers the `DefaultErrorHandler` retry + DLT flow. Do not swallow the exception.
- `group-id: notification-service` means this service gets its own independent offset
  position on each topic. Adding a new consumer group (e.g. for analytics) requires a
  separate `@KafkaListener` or a new service — do not change the existing group ID.
