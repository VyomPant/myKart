# inventory-service — Claude Code Context

**Port**: 8084 | **DB**: PostgreSQL (`inventory_db`) + Redis cache | **Status**: Phase 2

## Responsibility

Stock reservation. The ONLY service that increments/decrements inventory.
Redis caches available quantities for fast reads (30s TTL, evicted on mutations).

## Implemented Endpoints

```
POST /api/inventory              → create stock entry (SELLER/ADMIN)
GET  /api/inventory?skuCode=X,Y  → batch stock check (Redis-cached, 30s TTL)
POST /api/inventory/reserve      → atomically reserve items for an order
POST /api/inventory/release      → undo reservation (order cancelled)
POST /api/inventory/confirm      → move reserved → sold (decrement quantity)
PUT  /api/inventory/{skuCode}    → restock
```

## Reservation Logic (Critical)

`reserve()` is `@Transactional`. It:
1. Locks all requested rows with `OPTIMISTIC_FORCE_INCREMENT` (catches concurrent updates via @Version)
2. Checks ALL items for sufficient stock — fails ALL if ANY item is insufficient
3. Updates `reserved_quantity` for each, saves, and evicts the cache

If ANY item fails `InsufficientStockException` is thrown → entire transaction rolls back.

## Redis Cache

Key pattern: Spring Cache with name `inventory-stock`.
`@Cacheable` on `getAvailableStock(skuCode)` — used for single-item reads.
`@CacheEvict(allEntries=true)` on reserve/release/confirm — simpler than per-key eviction
when batch operations may affect many SKUs.

## Kafka Consumers

- `order.confirmed` → calls `confirm(items)` to finalize stock deduction
- `order.cancelled` → no-op (order-service releases directly before publishing)

## Database Schema

Flyway: `V1__create_inventory.sql`
- `inventory(id, sku_code UNIQUE, product_id, quantity, reserved_quantity, version, timestamps)`
- `version` column is Hibernate `@Version` for optimistic locking

## Gotchas

- `@Version` on `Inventory.version` means concurrent updates raise `OptimisticLockingFailureException` — handled by retrying at the caller (Phase 2) or surfaced as 409.
- Redis must be running or the service won't start (`spring.cache.type=redis`).
- `findAllBySkuCodeInForUpdate` uses `@Lock(OPTIMISTIC_FORCE_INCREMENT)` — forces version bump even on reads.
