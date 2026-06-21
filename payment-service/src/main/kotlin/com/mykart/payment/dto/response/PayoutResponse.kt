package com.mykart.payment.dto.response

import com.mykart.payment.entity.Payout
import com.mykart.payment.enums.Channel
import com.mykart.payment.enums.FailureType
import com.mykart.payment.enums.PayoutStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PayoutResponse(
    val id: UUID,
    val orderId: String,
    val sellerId: String,
    val amount: BigDecimal,
    val status: PayoutStatus,
    val channel: Channel?,
    val externalReferenceId: String?,
    val failureType: FailureType?,
    val failureMessage: String?,
    val retryCount: Int,
    val maxRetries: Int,
    val lastAttemptAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(payout: Payout) = PayoutResponse(
            id = payout.id,
            orderId = payout.orderId,
            sellerId = payout.sellerId,
            amount = payout.amount,
            status = payout.status,
            channel = payout.channel,
            externalReferenceId = payout.externalReferenceId,
            failureType = payout.failureType,
            failureMessage = payout.failureMessage,
            retryCount = payout.retryCount,
            maxRetries = payout.maxRetries,
            lastAttemptAt = payout.lastAttemptAt,
            createdAt = payout.createdAt,
            updatedAt = payout.updatedAt
        )
    }
}
