package com.mykart.payment.entity

import com.mykart.payment.enums.Channel
import com.mykart.payment.enums.FailureType
import com.mykart.payment.enums.PayoutStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "payouts",
    uniqueConstraints = [UniqueConstraint(name = "uk_payout_order_id", columnNames = ["order_id"])],
    indexes = [Index(name = "idx_payout_status", columnList = "status")]
)
class Payout(
    @Id
    @Column(columnDefinition = "uuid")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    val orderId: String,

    @Column(name = "seller_id", nullable = false)
    val sellerId: String,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "account_number", nullable = false, length = 32)
    val accountNumber: String,

    @Column(name = "ifsc_code", nullable = false, length = 16)
    val ifscCode: String,

    @Column(name = "beneficiary_name", nullable = false, length = 128)
    val beneficiaryName: String,

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var channel: Channel? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PayoutStatus = PayoutStatus.PENDING,

    @Column(name = "external_reference_id", length = 128)
    var externalReferenceId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_type", length = 16)
    var failureType: FailureType? = null,

    @Column(name = "failure_message", length = 512)
    var failureMessage: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 5,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0
) {
    // Required by JPA (noarg plugin also generates this, but explicit is clearer)
    protected constructor() : this(
        orderId = "", sellerId = "", amount = BigDecimal.ZERO,
        accountNumber = "", ifscCode = "", beneficiaryName = ""
    )

    val isRetryable: Boolean
        get() = status == PayoutStatus.FAILED &&
                failureType == FailureType.TRANSIENT &&
                retryCount < maxRetries

    val isPendingOrRetryable: Boolean
        get() = status == PayoutStatus.PENDING || isRetryable

    fun markInProgress(ch: Channel, extRef: String) {
        channel = ch
        externalReferenceId = extRef
        status = PayoutStatus.IN_PROGRESS
        lastAttemptAt = Instant.now()
        updatedAt = Instant.now()
    }

    fun markSuccess() {
        status = PayoutStatus.SUCCESS
        failureType = null
        failureMessage = null
        updatedAt = Instant.now()
    }

    fun markFailed(ft: FailureType, message: String?) {
        status = PayoutStatus.FAILED
        failureType = ft
        failureMessage = message
        retryCount++
        updatedAt = Instant.now()
    }
}
