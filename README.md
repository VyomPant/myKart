# myKart

[![CI](https://github.com/VyomPant/myKart/actions/workflows/ci.yml/badge.svg)](https://github.com/VyomPant/myKart/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-payment--service-purple)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

An event-driven marketplace backend built with Spring Boot microservices. Sellers list products, buyers place orders, and the platform automatically disburses seller payouts after each fulfilled order — with exactly-once delivery guarantees end to end.

```
                          ┌──────────────┐
                 JWT      │  api-gateway │  Redis rate limiting
        client ──────────▶│    :8080     │
                          └──────┬───────┘
                                 │  X-User-Id / X-User-Role
        ┌──────────┬─────────────┼──────────────┬─────────────┐
        ▼          ▼             ▼              ▼             ▼
  ┌──────────┐ ┌─────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐
  │   auth   │ │ product │ │   order   │ │ inventory │ │  payment  │
  │  :8082   │ │  :8083  │ │   :8081   │ │   :8084   │ │   :8086   │
  │ Postgres │ │ MongoDB │ │ Postgres  │ │ PG+Redis  │ │ Postgres  │
  └──────────┘ └─────────┘ └─────┬─────┘ └───────────┘ └─────┬─────┘
                                 │                           │
                                 │ order.confirmed           │ payout.completed
                                 │ order.cancelled           │
                                 ▼                           ▼
                          ┌─────────────────────────────────────┐
                          │                Kafka                │
                          └──────────────────┬──────────────────┘
                                             ▼
                                   ┌──────────────────┐
                                   │   notification   │
                                   │      :8085       │
                                   └──────────────────┘
```

## Services

| Service                | Port | Language | Storage            | Responsibility                                  |
|------------------------|------|----------|--------------------|-------------------------------------------------|
| `api-gateway`          | 8080 | Java 21  | Redis              | Entry point, stateless JWT validation, rate limiting |
| `auth-service`         | 8082 | Java 21  | PostgreSQL         | User identity, RS256 JWT issuance, refresh tokens |
| `product-service`      | 8083 | Java 21  | MongoDB            | Product catalog, semantic search (Spring AI)    |
| `order-service`        | 8081 | Java 21  | PostgreSQL         | Order lifecycle — Saga orchestration + Outbox   |
| `inventory-service`    | 8084 | Java 21  | PostgreSQL + Redis | Stock management, atomic reservations           |
| `payment-service`      | 8086 | Kotlin   | PostgreSQL         | Seller payouts via UPI/IMPS/NEFT with exactly-once semantics |
| `notification-service` | 8085 | Java 21  | —                  | Kafka consumer, email/SMS notifications         |
| `discovery-server`     | 8761 | Java 21  | —                  | Eureka service registry                         |

## Architecture

Full service map, pattern diagrams, and data flow: [docs/architecture.md](docs/architecture.md)

### Outbox pattern (order-service)

Kafka events are written to an `outbox_events` table in the **same transaction** as the order itself, eliminating the dual-write gap. A scheduled poller (500ms) publishes pending events synchronously and marks `published_at`. If Kafka is down when an order is placed, the event persists and is delivered automatically on recovery.

```
Order placed ──▶ INSERT order + outbox_event  (one TX)
                        │
OutboxPoller (500ms) ──▶ kafkaTemplate.send().get() ──▶ published_at = now()
```

### Saga orchestration (order-service)

Order placement runs a state machine persisted in a `saga_state` table:

```
INVENTORY_RESERVE ──▶ ORDER_PERSIST ──▶ OUTBOX_WRITE ──▶ COMPLETED
        │
        └── failure ──▶ compensating stock release ──▶ FAILED
```

Insufficient stock returns `409` with the order marked `CANCELLED` and an `ORDER_CANCELLED` outbox event; an open circuit breaker to inventory-service returns `503`.

### Exactly-once payouts (payment-service)

Four independent idempotency layers:

1. `orderId` idempotency key — application-level no-op on duplicates
2. `UNIQUE` DB constraint on `order_id` — catches races
3. `externalReferenceId` sent to the bank — channel-level deduplication
4. Status inquiry before retry — handles "transfer succeeded but response was lost"

Payout batches are processed with Kotlin coroutines (`supervisorScope` + `Dispatchers.IO`) so a single channel timeout doesn't cancel the rest of the batch.

### Stateless auth

`auth-service` issues RS256-signed JWTs. The gateway holds only the RSA public key and validates tokens in-process — zero network calls to auth-service per request. Downstream services trust the `X-User-Id` / `X-User-Role` headers injected at the gateway.

## Getting Started

### Prerequisites

- Docker + Docker Compose
- Java 21 (`JAVA_HOME` set)
- Maven 3.8+

### 1. Start infrastructure

```bash
cd infra
docker-compose up -d
# PostgreSQL, MongoDB, Redis, Kafka, Prometheus, Grafana, Tempo
```

### 2. Build

```bash
mvn clean install -DskipTests --no-transfer-progress
```

### 3. Run services

Start `discovery-server` first and `api-gateway` last:

```bash
cd discovery-server && mvn spring-boot:run     # 1st — service registry
cd auth-service && mvn spring-boot:run
cd product-service && mvn spring-boot:run
cd inventory-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd payment-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run          # last — routes to everything
```

### 4. Try the full flow

```bash
# Register a seller and log in
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"pass123","role":"SELLER"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"pass123"}' | jq -r '.accessToken')

# Create inventory and a product
curl -X POST http://localhost:8080/api/inventory \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"skuCode":"MOUSE-001","productId":"00000000-0000-0000-0000-000000000001","quantity":50}'

curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","category":"Electronics","description":"High precision","price":"29.99","skuCode":"MOUSE-001","specs":{"Color":"Black"}}'

# Register a buyer and place an order
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"pass123","role":"BUYER"}'

BUYER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"pass123"}' | jq -r '.accessToken')

# Triggers the saga: reserve stock → confirm order → Kafka event → payout created
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $BUYER_TOKEN" -H "Content-Type: application/json" \
  -d '{"sellerId":"seller@test.com","items":[{"skuCode":"MOUSE-001","productId":"00000000-0000-0000-0000-000000000001","quantity":2,"unitPrice":"29.99"}]}'

# Check the seller payout (PayoutWorker runs every 60s)
curl "http://localhost:8080/api/payments?orderId=<orderId-from-above>" \
  -H "Authorization: Bearer $TOKEN"
```

### 5. Dashboards

| URL | Description |
|-----|-------------|
| http://localhost:3000 | Grafana (admin/admin) — JVM, Kafka lag, payment worker, order flow |
| http://localhost:9090 | Prometheus metrics |
| http://localhost:3200 | Tempo distributed traces |
| http://localhost:8761 | Eureka service registry |
| http://localhost:8080/swagger-ui.html | OpenAPI docs |

## AI Features (optional)

`product-service` integrates Spring AI when `OPENAI_API_KEY` is set and `AI_ENABLED=true`, and degrades gracefully when they aren't:

- **Semantic search** — `GET /api/products/search?q=wireless+input+device` matches by meaning via vector embeddings; falls back to text search on name + description when disabled
- **Description generation** — `POST /api/products/generate-description` writes product copy from name, category, and specs; returns `501` when disabled

## Testing

| Layer | Command | What runs |
|-------|---------|-----------|
| Unit | `mvn test -pl auth-service,product-service,order-service,inventory-service,payment-service` | Service/controller tests with mocks |
| Integration | `mvn verify -pl order-service,inventory-service,payment-service -P integration-test` | Testcontainers: real PostgreSQL + Redis + embedded Kafka |
| Contract | `mvn verify -pl inventory-service,order-service -P contract-verify` | Spring Cloud Contract — HTTP stub for inventory reserve, Kafka message contract for `order.confirmed` |

Integration scenarios include: order happy path (CONFIRMED + outbox event written), out-of-stock (409 + CANCELLED + compensating event), role guard, atomic stock reservation under contention, platform fee calculation, payout idempotency, and permanent-failure rejection.

CI (GitHub Actions) runs unit → integration → contract → full build on every PR: [.github/workflows/ci.yml](.github/workflows/ci.yml)

## Observability

- **Tracing** — Micrometer → OpenTelemetry → Tempo; trace context propagated across HTTP and Kafka hops
- **Metrics** — Micrometer → Prometheus, with four Grafana dashboards committed in `infra/grafana/dashboards/` and auto-provisioned on startup (JVM overview, Kafka consumer lag, payment worker, order flow)
- **Logging** — structured JSON via logstash-logback-encoder; `traceId`, `spanId`, `orderId`, `sellerId`, `payoutId` in MDC on every line

```json
{
  "timestamp": "2026-05-23T10:30:00Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "abc123",
  "orderId": "uuid",
  "message": "Order placed and confirmed"
}
```

## Configuration

All services are configurable via environment variables:

```bash
# Infrastructure
DB_HOST=localhost
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
REDIS_HOST=localhost
OTEL_HOST=localhost
EUREKA_HOST=localhost

# AI features (optional)
OPENAI_API_KEY=sk-...
AI_ENABLED=true

# Payment tuning
PAYMENT_PLATFORM_FEE_RATE=0.02
PAYMENT_PAYOUT_MAX_RETRIES=5
PAYMENT_PAYOUT_WORKER_INTERVAL_MS=60000
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Out of stock on first order | Create inventory first: `POST /api/inventory` with `quantity > 0` |
| Payout stuck in `PENDING` | PayoutWorker runs every 60s; trigger manually with `POST /api/payments/{payoutId}/retry` |
| Semantic search returns keyword results | Set `AI_ENABLED=true` and `OPENAI_API_KEY`, then re-create products |
| Kafka events not publishing | Check OutboxPoller logs; verify the broker with `docker-compose logs kafka` |
| Service not registering | Start `discovery-server` first; other services retry registration on startup |

## License

MIT
