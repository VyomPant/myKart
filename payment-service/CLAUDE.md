# payment-service — Claude Code Context

Seller payout disbursement in idiomatic Kotlin. Triggered by `order.confirmed` Kafka events,
disburses via UPI / IMPS / NEFT channels, and publishes `payout.completed` on success.

**Port**: 8086 | **DB**: `payment_db` (PostgreSQL) | **Language**: Kotlin 1.9 + coroutines

---

## Kotlin Pattern Choices

**`class` not `data class` for JPA entities**
`data class` generates `equals`/`hashCode` based on all properties, which breaks Hibernate's
identity-map semantics. It also conflicts with lazy loading proxies. `Payout` is a plain class
with explicit `val`/`var` fields and a `protected` no-arg constructor for Hibernate.

**`val` for immutable fields, `var` for mutable state**
Fields set at creation (`id`, `orderId`, `amount`, `accountNumber`, etc.) are `val`.
Fields that change during processing (`status`, `channel`, `retryCount`, etc.) are `var`.
This makes the mutability contract readable without a comment.

**Sealed classes for discriminated results**
`TransferResult` (Success/Failure) and `StatusResult` (Success/Pending/Failed/Unknown) are
sealed classes. Call sites use exhaustive `when` — the compiler enforces all branches are
handled. No stringly-typed result codes.

**No `!!` operator anywhere**
Every nullable access uses `?.let`, `?: run`, or a local `val` capture before use. The two
places where Kotlin's smart cast doesn't cover mutable `var` properties (status inquiry
before retry) use local val captures: `val existingExtRef = payout.externalReferenceId`.

**Coroutines in PayoutWorker**
`runBlocking + supervisorScope + launch(Dispatchers.IO)` processes a batch in parallel.
`supervisorScope` means one payout's failure doesn't cancel the rest of the batch.
`Dispatchers.IO` keeps the coroutines on the IO thread pool — JPA calls block threads,
so we don't use `Dispatchers.Default`.

---

## Channel Selection Logic

Priority order: **UPI → IMPS → NEFT**

```
UPI:  ≤ ₹1,00,000  (free)
IMPS: ≤ ₹5,00,000  (₹7 fee)
NEFT: no limit      (₹3 fee, batch settlement)
```

`ChannelSelector.selectFor(amount)` iterates `[upi, imps, neft]`, returns the first
where `channel.channelType.supportsAmount(amountLong) && channel.isAvailable()`.

`Channel` enum carries `maxAmountInr: Long?` (null = no limit) and a `supportsAmount()`
method — the routing rule lives on the enum, not scattered across service code.

**Failure classification**:
- `PERMANENT` — invalid account/IFSC, account closed. Never retry; no amount of retries
  will fix a bad beneficiary detail.
- `TRANSIENT` — timeout, rate limit, network blip. Retry with exponential backoff up to
  `maxRetries` (default 5). `lastAttemptAt < threshold` enforces the backoff window.

---

## 4-Layer Exactly-Once Semantics

Exactly-once payout is critical — double disbursement is a direct financial loss.

| Layer | Mechanism | What it catches |
|-------|-----------|-----------------|
| 1 | Application-level `findByOrderId` check in `createIfAbsent` | Duplicate Kafka delivery before DB write |
| 2 | `UNIQUE` constraint on `order_id` column | Race between two concurrent consumer threads |
| 3 | `externalReferenceId` (UUID) sent to bank channel | Bank-level dedup — same UUID = bank no-ops |
| 4 | Status inquiry before retry in `processOne` | "Sent but response lost" — transfer succeeded but we got no ACK |

Layer 4 is the subtle one. Before re-sending a FAILED+TRANSIENT payout that already has an
`externalReferenceId`, `processOne` calls `channel.inquireStatus(externalReferenceId)`. If
the bank reports SUCCESS, we mark it complete without re-sending. Without this, a timeout
on a successful transfer would trigger a double disbursement on the next worker cycle.

---

## Configuration Parameters

| Property | Default | Description |
|----------|---------|-------------|
| `payment.platform-fee-rate` | `0.02` | Platform fee deducted before payout (2%) |
| `payment.payout.max-retries` | `5` | Max TRANSIENT retries before permanent failure |
| `payment.payout.worker-interval-ms` | `60000` | Scheduler delay between worker runs (ms) |
| `payment.payout.batch-size` | `50` | Max payouts processed per worker cycle |
| `payment.payout.backoff-base-seconds` | `60` | Min age of last attempt before retry is eligible |

All overridable via env vars: `PAYMENT_PAYOUT_MAX_RETRIES`, etc.

---

## Reconciliation Strategy

`POST /api/payments/reconcile` accepts a list of `successfulExternalReferenceIds` from a
bank statement export.

1. Load all DB rows where `status = SUCCESS AND external_reference_id IS NOT NULL`
2. Diff: `bankSet - dbSet` = transfers that succeeded at bank but are FAILED in DB ("orphans")
3. Diff: `dbSet - bankSet` = transfers marked SUCCESS in DB but absent from bank statement
4. For each orphan, call `channel.inquireStatus()` — if confirmed SUCCESS, mark healed
5. Return counts: `matched`, `onlyInBank`, `onlyInDb`, `healed`

`onlyInDb` entries are flagged for manual investigation — they may indicate a channel bug
or a bank statement gap. The service does not auto-correct these.

---

## Key Files

| File | Purpose |
|------|---------|
| `entity/Payout.kt` | JPA entity with `markInProgress`, `markSuccess`, `markFailed` state machine methods |
| `channel/PaymentChannel.kt` | Interface: `transfer()` + `inquireStatus()` |
| `channel/MockPaymentChannel.kt` | Abstract base with ConcurrentHashSet dedup, configurable failure rates |
| `channel/ChannelSelector.kt` | Priority routing + `getChannel(type)` for re-inquiry |
| `service/PayoutService.kt` | Core logic: `createIfAbsent`, `processOne` (with status inquiry), `retryManually` |
| `service/PayoutWorker.kt` | `@Scheduled` + coroutines batch processor |
| `service/ReconciliationService.kt` | Bank statement diff + orphan healing |
| `metric/PayoutMetrics.kt` | Micrometer counters/timers/gauges; inner `Stopwatch` for clean timer API |
| `listener/OrderEventListener.kt` | `@KafkaListener` on `order.confirmed` → `createIfAbsent` |
| `repository/PayoutRepository.kt` | `findPendingOrRetryable` JPQL with threshold + pageable |
| `db/migration/V1__create_payouts.sql` | PostgreSQL enums, partial index on pending/retry queue |
