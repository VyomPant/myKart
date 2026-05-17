# /test-all

Run the full test suite across all implemented services.

## Usage

```
/test-all
/test-all <service-name>    # run for a single service only
```

## What this command does

### Step 1 — Unit tests (all services)

```bash
mvn test -pl mkart-common,auth-service,api-gateway,notification-service --no-transfer-progress
```

Reports: pass/fail per service, total test count, any failures with stack traces.

### Step 2 — Integration tests (Phase 2+ services with Testcontainers)

```bash
mvn verify -P integration-test -pl order-service,inventory-service,payment-service --no-transfer-progress
```

Requires Docker to be running — Testcontainers will spin up PostgreSQL and Kafka containers.

### Step 3 — Contract tests (Phase 5)

```bash
mvn verify -P contract-test -pl inventory-service --no-transfer-progress
mvn verify -P contract-consumer-test -pl order-service --no-transfer-progress
```

### Summary output

```
Unit tests:      mkart-common ✓ | auth-service ✓ | api-gateway ✓ | notification-service ✓
Integration:     order-service ✓ | inventory-service ✓ | payment-service ✓
Contracts:       inventory-service (producer) ✓ | order-service (consumer) ✓
```

## Notes

- Integration tests require Docker running locally
- The Testcontainers extension manages container lifecycle — no manual setup needed
- If a test fails, check the test output for the container logs (captured on failure)
