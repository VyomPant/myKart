# /run-full-test-suite

Run the complete test suite: unit tests → integration tests → contract verification.

## Command

```bash
# Step 1: Build shared library
mvn install -pl mkart-common -am --no-transfer-progress -DskipTests

# Step 2: Unit tests (all services)
mvn test \
  -pl auth-service,product-service,order-service,inventory-service,payment-service \
  --no-transfer-progress \
  -Deureka.client.enabled=false \
  -Dspring.cloud.discovery.enabled=false

# Step 3: Integration tests with Testcontainers (requires Docker)
mvn verify \
  -pl order-service,inventory-service,payment-service \
  -P integration-test \
  --no-transfer-progress

# Step 4: Contract verification
mvn verify \
  -pl inventory-service,order-service \
  -P contract-verify \
  --no-transfer-progress
```

## What each step tests

| Step | What runs | Requires |
|------|-----------|----------|
| Unit | Mocked service/repository tests | Nothing external |
| Integration | `*IntegrationTest` via Testcontainers | Docker |
| Contract | Spring Cloud Contract generated tests | Docker (Testcontainers in base class) |

## Scenarios covered

**order-service**:
- Happy path: order confirmed, outbox event written
- Out-of-stock: 409 CONFLICT, order cancelled
- Role guard: SELLER role rejected with 400

**inventory-service**:
- Reserve: atomic decrement of reserved_quantity
- Reserve failure: insufficient stock returns 409
- Confirm: moves reserved to sold
- Not found: 404 for unknown SKU

**payment-service**:
- Platform fee: 2% deducted from totalAmount
- Idempotency: duplicate orderId returns same payout
- Retry: FAILED+TRANSIENT payout resets to PENDING
- Permanent failure: retry rejected

## Notes

- Docker must be running for integration and contract tests
- First run pulls images (postgres:16-alpine, redis:7-alpine) — takes ~2 minutes
- Subsequent runs use cached images — takes ~30-60 seconds per service
- All tests are independent; they create and clean their own data
