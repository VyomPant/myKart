# myKart v2 — Claude Code Context

myKart v2 is a production-ready marketplace backend. Sellers list products, buyers place orders,
and the platform automatically disburses seller payouts after each fulfilled order.

**Branch**: `mykart-v2`
**Stack**: Java 21 + Spring Boot 3.3 + Spring Cloud 2023 + Kotlin (payment-service)

---

## Service Registry

| Service              | Port | Language | DB              | Status   |
|----------------------|------|----------|-----------------|----------|
| `discovery-server`   | 8761 | Java 21  | —               | Phase 1  |
| `auth-service`       | 8082 | Java 21  | PostgreSQL      | Phase 1  |
| `api-gateway`        | 8080 | Java 21  | Redis           | Phase 1  |
| `product-service`    | 8083 | Java 21  | MongoDB         | Phase 2  |
| `order-service`      | 8081 | Java 21  | PostgreSQL      | Phase 2  |
| `inventory-service`  | 8084 | Java 21  | PostgreSQL+Redis | Phase 2 |
| `payment-service`    | 8086 | Kotlin   | PostgreSQL      | Phase 3  |
| `notification-service` | 8085 | Java 21 | —              | Phase 1  |

## Kafka Topics

| Topic             | Producer      | Consumers                              |
|-------------------|---------------|----------------------------------------|
| `order.confirmed` | order-service | payment-service, notification-service  |
| `order.cancelled` | order-service | inventory-service, notification-service |
| `payout.completed` | payment-service | notification-service                |

---

## Architecture Decisions

**Auth split**: `auth-service` issues RS256 JWT tokens. The gateway holds the RSA public key and
validates tokens stateless — zero network calls to auth-service per request. Downstream services
trust `X-User-Id` and `X-User-Role` headers injected by the gateway.

**Outbox pattern** (order-service): Kafka events written to `outbox_events` DB table in the SAME
transaction as the business entity. A `@Scheduled` poller publishes synchronously and marks
`published_at`. Eliminates dual-write gap that causes silent payout failures.

**Saga orchestration** (order-service): `SagaState` table is the single source of truth. Order
placement: PENDING → INVENTORY_RESERVE → ORDER_PERSIST → COMPLETED. Compensating transactions
automatically release stock on failure.

**Exactly-once payouts** (payment-service): Four idempotency layers:
1. `orderId` idempotency key — application-level no-op on duplicate
2. DB UNIQUE constraint on `order_id` — catches races
3. Channel-level `external_reference_id` — deduplicates at bank
4. Status inquiry before retry — handles "sent but response lost"

**Kotlin in payment-service only**: The most self-contained service with the most complex logic.
Demonstrates real Kotlin (data classes, sealed classes, coroutines with `supervisorScope`).

---

## Build Commands

```bash
# Start all infrastructure (from infra/)
docker-compose up -d

# Build shared library (required first)
mvn install -pl mkart-common -am

# Build and test all Phase 1 services
mvn test -pl auth-service,api-gateway,notification-service

# Run a specific service locally
cd auth-service && mvn spring-boot:run

# Build everything
mvn clean install --no-transfer-progress
```

## Run Order

1. `docker-compose up -d` (starts postgres, mongo, redis, kafka, tempo, prometheus, grafana)
2. `discovery-server` (must be up before other services register)
3. `auth-service`, `product-service`, `inventory-service` (no cross-service dependencies)
4. `order-service` (depends on inventory-service)
5. `payment-service` (depends on Kafka)
6. `notification-service` (Kafka consumer only)
7. `api-gateway` (routes to all services)

---

## Package Conventions

- All services: `com.mykart.<servicename>`
- Main class: `<PascalCase>Application`
- No Lombok in new code (use Java 21 records for DTOs, explicit getters/setters for JPA entities)
- No `System.out.println` — SLF4J logger everywhere
- No abbreviations: `inventoryService` not `invSvc`, `productRepository` not `prodRepo`

## Flyway Migrations

Location: `src/main/resources/db/migration/`
Naming: `V{n}__{lowercase_description}.sql` (two underscores, lowercase, no spaces)

Examples:
- `V1__create_users_table.sql`  ✓
- `V2__create_refresh_tokens_table.sql`  ✓
- `V1_CreateUsers.sql`  ✗ (single underscore, camelCase)

`ddl-auto: validate` is set in all services — Flyway manages schema exclusively.

## Observability

- **Tracing**: Micrometer → OTel → Tempo (`:4317` gRPC, `:3200` HTTP)
- **Metrics**: Micrometer → Prometheus (`:9090`) → Grafana (`:3000`)
- **Logging**: JSON via logstash-logback-encoder on all services
- **MDC fields**: `traceId`, `spanId`, `service`, `orderId`, `sellerId`, `payoutId`

Actuator endpoints exposed on all services: `health`, `info`, `prometheus`

---

## Claude Tooling in This Repo

```
.claude/
├── settings.json          — PreToolUse bash safety + PostToolUse lint hooks
├── commands/
│   ├── bootstrap-service.md  — /bootstrap-service <name>
│   ├── add-migration.md      — /add-migration <service> <description>
│   ├── test-all.md           — /test-all
│   └── check-contract.md     — /check-contract
├── skills/
│   ├── microservice-patterns/SKILL.md  — Outbox, Saga, idempotency templates
│   ├── kotlin-conventions/SKILL.md     — Kotlin idioms for payment-service
│   └── spring-boot-conventions/SKILL.md — Error handling, validation, logging
└── agents/
    ├── code-reviewer.yml   — Reviews for pattern correctness
    └── test-writer.yml     — Generates Testcontainers integration tests
```

scripts/
- `pre-bash-safety.sh`: blocks `rm -rf`, `git push --force`, `DROP TABLE`
- `post-write-check.sh`: ktlint on `.kt` files, Flyway naming validation on `.sql`
