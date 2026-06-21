# Skill: Microservice Patterns

Auto-invoked when creating services that need Outbox, Saga, or idempotency patterns.

## Outbox Pattern (order-service)

The Outbox pattern guarantees reliable Kafka event delivery by writing the event to the database
in the SAME transaction as the business entity, then publishing asynchronously.

### Why it exists

Direct `kafkaTemplate.send()` inside a `@Transactional` method creates a dual-write gap:
- DB commits successfully
- Kafka broker is down → event lost → payout never triggered

### Implementation checklist

- [ ] `OutboxEvent` table has `published_at` column (null = not yet published)
- [ ] The business entity save and `OutboxEvent` insert are in the SAME `@Transactional` method
- [ ] `OutboxPoller` is `@Scheduled(fixedDelay = 500)` — NOT `fixedRate` (avoids overlap)
- [ ] `kafkaTemplate.send(...).get()` — synchronous! Throws on broker failure
- [ ] `published_at` is set AFTER successful Kafka send in the same transaction
- [ ] `Limit.of(50)` on the poll query — never load all unpublished at once

### Template

```java
@Scheduled(fixedDelay = 500)
@Transactional
void poll() {
    var unpublished = outboxRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(50));
    unpublished.forEach(event -> {
        kafkaTemplate.send(topicFor(event), event.getPayload()).get(); // sync — throws on failure
        event.setPublishedAt(Instant.now());
        outboxRepository.save(event);
    });
}
```

---

## Saga Orchestration (order-service)

The Saga pattern manages distributed transactions across services. In myKart, `order-service`
is the orchestrator — it owns the `SagaState` table and drives all compensating transactions.

### State machine

```
PENDING → INVENTORY_RESERVE (WebClient call to inventory-service)
        ↓ success              ↓ failure
ORDER_PERSIST              COMPENSATING → FAILED
        ↓ (outbox write)
COMPLETED
```

### Implementation checklist

- [ ] `SagaState` has `order_id` as PK (one saga per order)
- [ ] Each transition updates `SagaState.step` in a DB transaction
- [ ] Compensating transactions are implemented for EVERY failure branch
- [ ] The saga's compensating call (inventory release) is idempotent — can be called multiple times safely
- [ ] `SagaState.failure_reason` captures the error for debugging

---

## Idempotency Key Pattern

Used in payment-service to prevent duplicate payouts.

### Layers (in order)

1. **Application layer**: `createIfAbsent(orderId, ...)` — no-ops if orderId already in DB
2. **Database layer**: `UNIQUE` constraint on `order_id` column — catches race conditions
3. **Channel layer**: `external_reference_id` UUID sent to UPI/IMPS/NEFT — deduplicates at bank
4. **Status inquiry**: Before retrying, check channel status → handle "sent but response lost"

### Template

```java
@Transactional
public Payout createIfAbsent(String orderId, ...) {
    return payoutRepository.findByOrderId(orderId).orElseGet(() -> {
        var payout = new Payout(/* ... */);
        try {
            return payoutRepository.save(payout);
        } catch (DataIntegrityViolationException e) {
            // Race condition: another thread saved first
            return payoutRepository.findByOrderId(orderId).orElseThrow();
        }
    });
}
```

---

## Circuit Breaker (order-service → inventory-service)

```java
@CircuitBreaker(name = "inventory", fallbackMethod = "inventoryFallback")
@TimeLimiter(name = "inventory")
public CompletableFuture<ReservationResponse> reserveStock(...) {
    return CompletableFuture.supplyAsync(() ->
        inventoryClient.post().uri("/api/inventory/reserve").bodyValue(request)
            .retrieve().bodyToMono(ReservationResponse.class).block()
    );
}
```

application.yml:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventory:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
  timelimiter:
    instances:
      inventory:
        timeout-duration: 3s
```
