# myKart v2 — Architecture

## High-Level Service Map

```
                    ┌─────────────────────────────────┐
                    │       Client / Mobile App        │
                    └────────────┬────────────────────┘
                                 │ HTTPS
                  ┌──────────────▼──────────────────┐
                  │      API Gateway (:8080)         │
                  │  JWT Validation (RS256 stateless)│
                  │  Rate Limiting (Redis token bucket)
                  │    Spring Cloud Gateway          │
                  └───┬─────┬──────┬──────┬──────────┘
                      │     │      │      │
         ┌────────────┘     │      │      └────────────┐
         │                  │      │                   │
         ▼                  ▼      ▼                   ▼
  ┌─────────────┐  ┌──────────────┐ ┌───────────┐  ┌──────────────┐
  │auth-service │  │product-svc   │ │order-svc  │  │inventory-svc │
  │(Java 21)    │  │(Java 21+AI)  │ │(Java 21)  │  │(Java 21)     │
  │PostgreSQL   │  │MongoDB+Redis │ │PostgreSQL │  │PostgreSQL    │
  │RS256 JWT    │  │Spring AI     │ │Outbox+Saga│  │+Redis Cache  │
  │:8082        │  │:8083         │ │:8081      │  │:8084         │
  └─────────────┘  └──────────────┘ └─────┬─────┘  └──────────────┘
                                           │
                                    ┌──────▼──────┐
                                    │    Kafka    │
                                    │  (Confluent)│
                                    │             │
                                    │ order.confirmed ──────┐
                                    │ order.cancelled ──┐   │
                                    │ payout.completed  │   │
                                    └──────────────┘   │   │
                                                        │   │
                            ┌───────────────────────────┘   │
                            │                               │
                            ▼                               ▼
                  ┌──────────────────┐          ┌─────────────────┐
                  │notification-svc  │          │payment-service  │
                  │(Java 21)         │          │(Kotlin)         │
                  │Kafka Consumer    │          │PostgreSQL       │
                  │Email/SMS stubs   │          │Coroutines       │
                  │:8085             │          │UPI/IMPS/NEFT    │
                  └──────────────────┘          │:8086            │
                                                └─────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ Infrastructure (Docker Compose)                              │
  │                                                              │
  │  PostgreSQL (5 schemas: auth, order, inventory, payment)     │
  │  MongoDB (product catalog + embeddings)                      │
  │  Redis (inventory cache, gateway rate limiting)              │
  │  Kafka + Zookeeper (event streaming)                         │
  │  Prometheus :9090 (metrics scraping)                         │
  │  Grafana :3000 (dashboards: JVM, Kafka, Payment, Order)      │
  │  Tempo :3200/:4317 (distributed tracing via OTel)            │
  └──────────────────────────────────────────────────────────────┘

  Service Registry:
  ┌───────────────────────────────────────────────────────────┐
  │  Eureka (discovery-server :8761)                          │
  │  All services register; Gateway uses lb://service-name    │
  └───────────────────────────────────────────────────────────┘
```

---

## Key Patterns

### Outbox Pattern (order-service)

Eliminates the dual-write gap where Kafka publish could fail after DB commit.

```
HTTP POST /api/orders
        │
        ▼
┌──────────────────────────────────────────────┐
│ REQUIRES_NEW Transaction #1                  │
│  INSERT orders (status=PENDING)              │
│  INSERT saga_states (step=INVENTORY_RESERVE) │
│  COMMIT                                      │
└──────────────────┬───────────────────────────┘
                   │
                   ▼ (outside any TX)
        inventoryClient.reserve(items)  ← WebClient to inventory-service
                   │
          ┌────────┴────────┐
        SUCCESS           FAIL (InsufficientStock)
          │                  │
          ▼                  ▼
┌──────────────────┐  ┌────────────────────────────┐
│ TX #2: CONFIRM   │  │ TX #3: CANCEL              │
│  UPDATE status   │  │  UPDATE status=CANCELLED   │
│    = CONFIRMED   │  │  INSERT outbox ORDER_CANCELLED│
│  INSERT outbox   │  │  UPDATE saga = FAILED      │
│   ORDER_CONFIRMED│  │  COMMIT                    │
│  COMMIT          │  └────────────────────────────┘
└────────┬─────────┘
         │
         ▼
  OutboxPoller (@Scheduled 500ms)
  SELECT unpublished events → kafkaTemplate.send().get() → mark published_at
```

### Saga State Machine (order-service)

```
INVENTORY_RESERVE → ORDER_PERSIST → OUTBOX_WRITE → COMPLETED
        │
        └── (on failure) → FAILED + compensating release
```

### Exactly-Once Payout Guarantee (payment-service)

```
Layer 1: orderId idempotency key (PayoutService.createIfAbsent)
         ↓ if passes
Layer 2: DB UNIQUE constraint on order_id column
         ↓ if passes
Layer 3: External reference UUID sent to bank channel
         ↓ before retry
Layer 4: Status inquiry — "did the bank receive it already?"
         Only re-send if STATUS_UNKNOWN or FAILED
```

---

## Service Responsibilities

| Service            | Owns                              | Calls               | Publishes                       | Consumes              |
|--------------------|-----------------------------------|---------------------|---------------------------------|-----------------------|
| `auth-service`     | Users, JWT issuance               | —                   | —                               | —                     |
| `api-gateway`      | Routing, JWT validation, rate limit | auth-service (pubkey)| —                              | —                     |
| `product-service`  | Product catalog, embeddings       | —                   | —                               | —                     |
| `order-service`    | Orders, Saga, Outbox              | inventory-service   | `order.confirmed`, `order.cancelled` | —              |
| `inventory-service`| Stock levels, reservations        | —                   | —                               | `order.cancelled`     |
| `payment-service`  | Seller payouts                    | —                   | `payout.completed`              | `order.confirmed`     |
| `notification-service` | Email/SMS dispatch            | —                   | —                               | `order.confirmed`, `payout.completed` |

---

## Auth Flow

```
Client → POST /auth/login → auth-service issues RS256 JWT
Client → GET /api/products → gateway validates JWT (RS256 public key, no network call)
                           → injects X-User-Id + X-User-Role headers
                           → routes to product-service (never sees raw JWT)
product-service reads X-User-Id/X-User-Role headers (trusted, set by gateway)
```

---

## Data Stores

| Store      | Used by                                | Purpose                              |
|------------|----------------------------------------|--------------------------------------|
| PostgreSQL | auth, order, inventory, payment        | Primary business data                |
| MongoDB    | product-service                        | Product catalog + vector embeddings  |
| Redis      | inventory-service, api-gateway         | Stock cache (TTL 60s), rate limiting |
| Kafka      | order, inventory, payment, notification| Async event streaming                |

---

## Observability Stack

```
Services → Micrometer → Prometheus (:9090) → Grafana (:3000)
Services → OTel SDK → Tempo (:4317 gRPC) → Grafana Tempo UI
Services → Logback JSON → stdout (structured logs with traceId, spanId, orderId)
```

**Grafana Dashboards** (committed to `infra/grafana/dashboards/`):
- `jvm-overview.json` — Heap, GC, threads, HTTP latency per service
- `kafka-consumer-lag.json` — Consumer lag by topic, message rates
- `payment-worker.json` — Payout queue depth, channel success rates, retry rate
- `order-flow.json` — Order status distribution, saga completion, outbox health

**MDC fields** propagated in all service logs:
- `traceId`, `spanId` — distributed trace correlation
- `orderId`, `buyerId` — business context
- `payoutId`, `sellerId` — payment context
