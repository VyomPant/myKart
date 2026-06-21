# myKart v2 — Production-Ready Marketplace Backend

> A Spring Boot microservices project

---

## What This Demonstrates

### Distributed Systems Patterns
- **Outbox Pattern** — Kafka events written atomically with the business entity; OutboxPoller publishes with exactly-once guarantee
- **Saga Orchestration** — Explicit state machine (`SagaState` table) with compensating transactions on inventory failure
- **Atomic Stock Reservation** — Optimistic locking + Redis cache; concurrent orders for the same SKU handled correctly
- **Exactly-Once Payouts** — 4 idempotency layers: app key, DB unique constraint, channel reference, status inquiry before retry

### Production Observability
- Distributed tracing via OpenTelemetry → Tempo (Grafana)
- Metrics via Micrometer → Prometheus → 4 committed Grafana dashboards
- Structured JSON logging with MDC: `traceId`, `orderId`, `payoutId` on every log line
- Health + actuator endpoints on all 8 services

### AI-Native Development
- Spring AI integration: semantic product search (vector embeddings) + description generation
- Config-driven graceful degradation when `OPENAI_API_KEY` absent
- Claude Code tooling visible in `.claude/`: skills, hooks, commands, agents
- Agentic development workflow — not prompt-and-paste

### Kotlin in Production
- `payment-service` in idiomatic Kotlin: data classes, sealed classes, coroutines with `supervisorScope`
- Real async batch processing, not just Kotlin syntax over Java patterns

---

## Services

| Service              | Port | Lang    | DB               | Role                                       |
|----------------------|------|---------|------------------|--------------------------------------------|
| `api-gateway`        | 8080 | Java 21 | Redis            | Entry point, JWT validation, rate limiting |
| `auth-service`       | 8082 | Java 21 | PostgreSQL       | JWT issuance (RS256), user identity        |
| `product-service`    | 8083 | Java 21 | MongoDB          | Product catalog, semantic search (Spring AI)|
| `order-service`      | 8081 | Java 21 | PostgreSQL       | Order orchestration, Outbox + Saga         |
| `inventory-service`  | 8084 | Java 21 | PostgreSQL+Redis | Stock management, atomic reservations      |
| `payment-service`    | 8086 | Kotlin  | PostgreSQL       | Seller payouts (UPI/IMPS/NEFT channels)    |
| `notification-service`| 8085| Java 21 | —               | Kafka consumer, email/SMS stubs            |
| `discovery-server`   | 8761 | Java 21 | —               | Eureka service registry                    |

---

## Quick Start

### Prerequisites
- Docker + Docker Compose
- Java 21 (set `JAVA_HOME`)
- Maven 3.8+

### 1. Start Infrastructure

```bash
cd infra
docker-compose up -d
# PostgreSQL, MongoDB, Redis, Kafka, Prometheus, Grafana, Tempo
```

### 2. Build

```bash
# From repo root
mvn clean install -DskipTests --no-transfer-progress
```

### 3. Run Services (in order)

```bash
# Terminal 1
cd discovery-server && mvn spring-boot:run

# Terminal 2
cd auth-service && mvn spring-boot:run

# Terminal 3
cd product-service && mvn spring-boot:run

# Terminal 4
cd inventory-service && mvn spring-boot:run

# Terminal 5
cd order-service && mvn spring-boot:run

# Terminal 6
cd payment-service && mvn spring-boot:run

# Terminal 7
cd notification-service && mvn spring-boot:run

# Terminal 8 (last — routes to all services)
cd api-gateway && mvn spring-boot:run
```

### 4. Test the Full Flow

```bash
# Register seller
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"pass123","role":"SELLER"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seller@test.com","password":"pass123"}' | jq -r '.accessToken')

# Create inventory
curl -X POST http://localhost:8080/api/inventory \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"skuCode":"MOUSE-001","productId":"00000000-0000-0000-0000-000000000001","quantity":50}'

# Create product
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Wireless Mouse","category":"Electronics","description":"High precision","price":"29.99","skuCode":"MOUSE-001","specs":{"Color":"Black"}}'

# Register buyer and get token
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"pass123","role":"BUYER"}'

BUYER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"buyer@test.com","password":"pass123"}' | jq -r '.accessToken')

# Place order: triggers Saga → reserves stock → publishes Kafka event → payout created
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $BUYER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sellerId":"seller@test.com","items":[{"skuCode":"MOUSE-001","productId":"00000000-0000-0000-0000-000000000001","quantity":2,"unitPrice":"29.99"}]}'

# Check payout (wait ~60s for PayoutWorker)
curl "http://localhost:8080/api/payments?orderId=<orderId-from-above>" \
  -H "Authorization: Bearer $TOKEN"
```

### 5. View Dashboards

| URL | Description |
|-----|-------------|
| http://localhost:3000 (admin/admin) | Grafana — JVM, Kafka Lag, Payment Worker, Order Flow |
| http://localhost:9090 | Prometheus metrics |
| http://localhost:3200 | Tempo distributed traces |
| http://localhost:8761 | Eureka service registry |
| http://localhost:8080/swagger-ui.html | OpenAPI docs |

---

## Architecture

See [docs/architecture.md](docs/architecture.md) for the full service map, pattern diagrams, and data flow.

### Outbox Pattern

```
Order placed → INSERT Order + OutboxEvent (same TX)
↓
OutboxPoller (500ms) → kafkaTemplate.send().get() (sync) → mark published_at
↓
Guaranteed delivery, no dual-write gap
```

### Saga Orchestration

```
INVENTORY_RESERVE → ORDER_PERSIST → OUTBOX_WRITE → COMPLETED
        │
        └── (failure) → compensating release → FAILED
```

### Exactly-Once Payouts

```
1. orderId idempotency key (app level)
2. UNIQUE constraint on order_id (DB level)
3. externalReferenceId sent to bank (channel level)
4. Status inquiry before retry (handles "sent but response lost")
```

---

## AI Features

### Semantic Product Search

When `OPENAI_API_KEY` is set and `AI_ENABLED=true`:

```bash
curl "http://localhost:8080/api/products/search?q=wireless+input+device" \
  -H "Authorization: Bearer $TOKEN"
# Returns semantically relevant results, not just keyword matches
```

Fallback to text search on name + description when AI disabled.

### AI Description Generation

```bash
curl -X POST http://localhost:8080/api/products/generate-description \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Gaming Laptop","category":"Electronics","specs":{"GPU":"RTX 4060","RAM":"16GB"}}'
# Returns 501 when AI_ENABLED=false
```

---

## Testing

### Unit Tests

```bash
mvn test -pl auth-service,product-service,order-service,inventory-service,payment-service
```

### Integration Tests (Testcontainers)

Spins up real PostgreSQL, Redis, and Kafka containers per service.

```bash
mvn verify -pl order-service,inventory-service,payment-service -P integration-test
```

Scenarios covered:
- **order-service**: happy path (CONFIRMED + outbox event), out-of-stock (409 + CANCELLED), role guard (400)
- **inventory-service**: reserve atomicity, insufficient stock (409), confirm (reserved → sold), SKU not found (404)
- **payment-service**: 2% platform fee deduction, orderId idempotency, retry endpoint, permanent failure rejection

### Contract Tests (Spring Cloud Contract)

```bash
mvn verify -pl inventory-service,order-service -P contract-verify
```

Contracts:
- `inventory-service/reserve-stock.groovy` — HTTP POST /api/inventory/reserve
- `order-service/order-confirmed-event.groovy` — Kafka output on order.confirmed

### CI/CD

GitHub Actions at `.github/workflows/ci.yml`:
1. Unit tests (all services)
2. Integration tests (order, inventory, payment — Testcontainers)
3. Contract verification (inventory, order)
4. Full build

---

## Observability

### Grafana Dashboards

Committed to `infra/grafana/dashboards/`, auto-provisioned on startup:

| Dashboard | Panels |
|-----------|--------|
| `jvm-overview.json` | Heap usage, GC rate, thread count, HTTP p99 latency |
| `kafka-consumer-lag.json` | Consumer lag by topic, message rates |
| `payment-worker.json` | Payout queue depth, channel success rates, retry rate |
| `order-flow.json` | Order status distribution, saga completion, outbox health |

### Structured Logs

```json
{
  "timestamp": "2025-05-23T10:30:00Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "abc123",
  "orderId": "uuid",
  "message": "Order placed and confirmed"
}
```

---

## AI-Native Development

### `.claude/` Structure

```
.claude/
├── settings.json              # PreToolUse safety hooks + PostToolUse lint
├── commands/
│   ├── bootstrap-service.md   # /bootstrap-service <name>
│   ├── add-migration.md       # /add-migration <service> <desc>
│   ├── test-all.md            # /test-all
│   ├── check-contract.md      # /check-contract
│   └── run-full-test-suite.md # /run-full-test-suite
├── skills/
│   ├── microservice-patterns/SKILL.md  # Outbox, Saga, idempotency
│   ├── kotlin-conventions/SKILL.md     # Kotlin idioms
│   └── spring-boot-conventions/SKILL.md
└── agents/
    ├── code-reviewer.yml
    └── test-writer.yml
```

### Hooks

- **PreToolUse (Bash)**: Blocks `rm -rf`, `git push --force`, `DROP TABLE`
- **PostToolUse (Write)**: `ktlint` on `.kt`, Flyway naming validation on `.sql`

Claude Code was orchestrated with full project context, automated safety guards, and reusable skills — demonstrating systematic AI-assisted development, not prompt-and-paste.

---

## Production Configuration

```bash
# Feature flags
OPENAI_API_KEY=sk-...          # Optional: enables AI features
AI_ENABLED=true

# Infrastructure
DB_HOST=postgres.prod
KAFKA_BOOTSTRAP_SERVERS=kafka.prod:9092
REDIS_HOST=redis.prod
OTEL_HOST=tempo.prod
EUREKA_HOST=discovery.prod

# Payment tuning
PAYMENT_PLATFORM_FEE_RATE=0.02
PAYMENT_PAYOUT_MAX_RETRIES=5
PAYMENT_PAYOUT_WORKER_INTERVAL_MS=60000
```

---

## Troubleshooting

**Out of stock on first order?** Create inventory first: `POST /api/inventory` with `quantity > 0`

**Payout stuck in PENDING?** PayoutWorker runs every 60s. Trigger manually: `POST /api/payments/{payoutId}/retry`

**Semantic search returning keyword results?** Set `AI_ENABLED=true` and `OPENAI_API_KEY`, then re-create products

**Kafka not publishing?** Check OutboxPoller logs. Verify Kafka: `docker-compose logs kafka`

**Service not registering?** Start `discovery-server` first; other services retry on startup

---

## Resume Talking Points

1. **Outbox Pattern** — "Eliminates the dual-write gap that causes silent payout failures. The Kafka event and order update are committed atomically; the OutboxPoller publishes synchronously and marks the event published."

2. **Saga Orchestration** — "SagaState table is the single source of truth. Order placement has four explicit steps with compensating transactions that automatically release stock on failure."

3. **Exactly-Once Payouts** — "Four idempotency layers: app-level orderId key, DB unique constraint, channel-level externalReferenceId, and status inquiry before retry. No duplicate disbursements even with network failures."

4. **Auth Architecture** — "auth-service issues RS256 JWTs. Gateway validates stateless using the RSA public key — zero network calls to auth-service per request."

5. **AI-Native Development** — "Claude Code was orchestrated with skills, hooks, commands, and agents visible in `.claude/`. Systematic orchestration, not prompt-and-paste."

6. **Kotlin Coroutines** — "payment-service uses supervisorScope for safe parallel payout batch processing. Real async design, not just Kotlin syntax."

7. **Observability** — "Distributed traces via OTel + Tempo, Prometheus metrics, structured JSON logs with MDC. Four Grafana dashboards committed and auto-provisioned."

8. **End-to-End** — "Order → inventory reservation → Kafka event → payout creation → PayoutWorker → bank channel → payout.completed → notification. Full flow covered by Testcontainers integration tests."

---

## License

MIT
