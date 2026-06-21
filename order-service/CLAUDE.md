# order-service — Claude Code Context

**Port**: 8081 | **DB**: PostgreSQL (`order_db`) | **Status**: Phase 2

## Responsibility

Core order lifecycle. Orchestrates the placement saga and guarantees reliable Kafka publishing via the Outbox pattern.

## Implemented Endpoints

```
POST /api/orders              → place order (BUYER)
GET  /api/orders/{id}         → get order status
GET  /api/orders              → list buyer's orders (paginated)
POST /api/orders/{id}/cancel  → cancel PENDING order (BUYER)
```

## Saga Orchestration (OrderService)

`placeOrder()` runs a state machine persisted in `saga_state` table:

```
INVENTORY_RESERVE → [WebClient to inventory-service]
  → Success: ORDER_PERSIST
    → [DB: Order=CONFIRMED + OutboxEvent written, SagaStep=OUTBOX_WRITE]
      → OutboxPoller publishes → SagaStep=COMPLETED
  → Failure (InsufficientStock): order CANCELLED + OutboxEvent(ORDER_CANCELLED)
  → Failure (CircuitBreaker open): 503 returned
```

`createOrderAndSagaState()` and `confirmOrderAndWriteOutbox()` each use `REQUIRES_NEW` transactions to keep step-1 commit separate from step-3 commit. This ensures the order exists in DB even if confirmation fails.

## Outbox Pattern (OutboxPoller)

`OutboxPoller.poll()` runs every 500ms via `@Scheduled(fixedDelay=500)`.
Reads `outbox_events WHERE published_at IS NULL LIMIT 50`, sends synchronously to Kafka (`.get()` for blocking), marks `published_at = now()`.

**Why this matters**: If Kafka is down when the order is placed, the outbox event stays undelivered. Once Kafka recovers, the next poll sends it. Without Outbox, that confirmation event is permanently lost.

## Database Schema (3 Flyway migrations)

- `V1__create_orders.sql` — orders + order_line_items, `order_status` ENUM
- `V2__create_outbox.sql` — outbox_events, partial index on `published_at IS NULL`
- `V3__create_saga_state.sql` — saga_state, `saga_step` ENUM

## Circuit Breaker (inventory-service calls)

Uses `spring-cloud-starter-circuitbreaker-resilience4j` via `CircuitBreakerFactory`.
Config: 5-call sliding window, 50% failure threshold, 5s open wait, 3s timeout.
Fallback throws `InventoryServiceUnavailableException` → 503 to buyer.

## Key Files

```
entity/Order.java                  — JPA with @OneToMany to OrderLineItem
entity/OutboxEvent.java            — Outbox record (published_at = null until sent)
entity/SagaState.java              — Saga step tracker keyed by orderId
service/OrderService.java          — Saga orchestration logic
service/OutboxPoller.java          — @Scheduled Kafka publisher
client/InventoryClient.java        — WebClient wrapper with circuit breaker
config/KafkaConfig.java            — Producer with acks=all, idempotent enabled
```

## Debugging Stuck Orders

Query `saga_state` to see where an order is stuck:
```sql
SELECT order_id, step, failure_reason FROM saga_state WHERE step NOT IN ('COMPLETED', 'FAILED');
```

Query unpublished outbox events:
```sql
SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at;
```

## Gotchas

- `order_status` and `saga_step` are PostgreSQL ENUM types — must match Flyway migrations exactly.
- `@Transactional(propagation = REQUIRES_NEW)` on `createOrderAndSagaState` and `confirmOrderAndWriteOutbox` means Spring requires the calling class to NOT be the same bean (self-invocation breaks AOP). Both are called from `placeOrder()` on `this` — this works because they're on the same bean and Spring proxy is the entry point.
- Wait — actually REQUIRES_NEW from the same bean DOES work because Spring's proxy is only bypassed for private methods or direct `this.*` calls inside the same class. `placeOrder()` calls `createOrderAndSagaState()` and `confirmOrderAndWriteOutbox()` on `this` which bypasses AOP. This is a known Spring limitation. TODO Phase 3: extract to separate Spring beans.
