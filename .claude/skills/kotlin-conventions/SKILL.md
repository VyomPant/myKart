# Skill: Kotlin Conventions (payment-service)

Auto-invoked when working in `payment-service/`. All Kotlin code here must follow these rules.

## Entity conventions

JPA data classes in Kotlin need mutable vars for Hibernate — use `var` not `val` on entity fields:

```kotlin
@Entity
@Table(name = "payouts")
class Payout(
    @Id val id: UUID = UUID.randomUUID(),  // val is fine for IDs
    var orderId: String,                    // var for mutable fields
    var status: PayoutStatus = PayoutStatus.PENDING,
    @Version var version: Long = 0
)
```

Use `data class` for value objects and DTOs (not JPA entities).

## No `!!` operator

Every `!!` is a potential NullPointerException. Always use safe alternatives:

```kotlin
// Bad
val channel = payout.channel!!

// Good
val channel = payout.channel ?: throw IllegalStateException("Payout ${payout.id} has no channel assigned")
```

## Sealed classes for failure types

```kotlin
sealed class FailureClassification {
    data object Permanent : FailureClassification()
    data class Transient(val backoffSeconds: Long) : FailureClassification()
}
```

## Coroutines for parallel batch work

Use `supervisorScope` so one payout failure doesn't cancel the whole batch:

```kotlin
@Scheduled(fixedDelayString = "\${payout.worker.interval-ms:60000}")
fun processPending() = runBlocking {
    val batch = payoutService.getPendingOrRetryable(batchSize)
    supervisorScope {
        batch.map { payout ->
            launch(Dispatchers.IO) { payoutService.processOne(payout) }
        }.joinAll()
    }
}
```

Do NOT use `runBlocking` at the top level in production web handlers — only in scheduled tasks.

## Extension functions over utility classes

```kotlin
// Bad
object PayoutUtils {
    fun formatAmount(amount: BigDecimal): String = "₹${amount.toPlainString()}"
}

// Good
fun BigDecimal.formatAsRupees(): String = "₹${toPlainString()}"
```

## Logging with SLF4J

```kotlin
private val log = LoggerFactory.getLogger(PayoutService::class.java)

// Use string templates only for constants — use lazy lambda for expensive operations
log.info("Processing payout id={} orderId={}", payout.id, payout.orderId)
log.debug { "Full payout state: $payout" }  // only evaluated if DEBUG enabled
```

## No raw CompletableFuture

Kotlin has coroutines. Do not use `CompletableFuture` in payment-service. Use:
- `async/await` for parallel operations
- `supervisorScope` for fault-tolerant parallel work
- `Dispatchers.IO` for blocking IO calls (database, HTTP)

## Idiomatic null handling

```kotlin
// Bad
if (payout.channel != null) {
    processChannel(payout.channel!!)
}

// Good
payout.channel?.let { channel ->
    processChannel(channel)
} ?: log.warn("Payout ${payout.id} has no channel — skipping")
```
