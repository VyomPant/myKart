package com.mykart.payment.service

import com.mykart.payment.channel.ChannelSelector
import com.mykart.payment.channel.StatusResult
import com.mykart.payment.channel.TransferRequest
import com.mykart.payment.channel.TransferResult
import com.mykart.payment.entity.Payout
import com.mykart.payment.enums.FailureType
import com.mykart.payment.enums.PayoutStatus
import com.mykart.payment.metric.PayoutMetrics
import com.mykart.payment.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PayoutService(
    private val payoutRepository: PayoutRepository,
    private val channelSelector: ChannelSelector,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val payoutMetrics: PayoutMetrics,
    @Value("\${payment.platform-fee-rate:0.02}") private val platformFeeRate: BigDecimal,
    @Value("\${payment.payout.max-retries:5}") private val maxRetries: Int,
    @Value("\${spring.kafka.producer.topic.payout-completed:payout.completed}") private val payoutCompletedTopic: String
) {

    private val log = LoggerFactory.getLogger(PayoutService::class.java)

    @Transactional
    fun createIfAbsent(
        orderId: String,
        sellerId: String,
        totalAmount: BigDecimal,
        accountNumber: String,
        ifscCode: String,
        beneficiaryName: String
    ): Payout {
        val existing = payoutRepository.findByOrderId(orderId).orElse(null)
        if (existing != null) {
            log.info("payoutId={} orderId={} already exists, skipping create", existing.id, orderId)
            return existing
        }

        val payoutAmount = totalAmount.multiply(BigDecimal.ONE.minus(platformFeeRate))
            .setScale(2, java.math.RoundingMode.HALF_UP)

        val payout = Payout(
            orderId = orderId,
            sellerId = sellerId,
            amount = payoutAmount,
            accountNumber = accountNumber,
            ifscCode = ifscCode,
            beneficiaryName = beneficiaryName,
            maxRetries = maxRetries
        )

        val saved = payoutRepository.save(payout)
        payoutMetrics.incrementCreated()
        log.info("payoutId={} orderId={} sellerId={} amount={} created", saved.id, orderId, sellerId, payoutAmount)
        return saved
    }

    @Transactional
    fun processOne(payout: Payout) {
        if (!payout.isPendingOrRetryable) {
            log.warn("payoutId={} not processable status={}", payout.id, payout.status)
            return
        }

        val existingExtRef = payout.externalReferenceId
        val externalReferenceId = if (existingExtRef != null) UUID.fromString(existingExtRef) else UUID.randomUUID()

        if (payout.status == PayoutStatus.FAILED && existingExtRef != null) {
            val priorChannel = payout.channel?.let { channelSelector.getChannel(it) }
            if (priorChannel != null) {
                val statusResult = priorChannel.inquireStatus(existingExtRef)
                if (statusResult is StatusResult.Success) {
                    payout.markSuccess()
                    payoutRepository.save(payout)
                    publishPayoutCompleted(payout)
                    payoutMetrics.incrementSuccess(priorChannel.channelType)
                    log.info("payoutId={} recovered via status inquiry channel={}", payout.id, priorChannel.channelType)
                    return
                }
            }
        }

        val channel = channelSelector.selectFor(payout.amount)
        if (channel == null) {
            log.warn("payoutId={} no available channel for amount={}", payout.id, payout.amount)
            payout.markFailed(FailureType.TRANSIENT, "No available channel")
            payoutRepository.save(payout)
            payoutMetrics.incrementFailed(null)
            return
        }

        payout.markInProgress(channel.channelType, externalReferenceId.toString())
        payoutRepository.save(payout)

        val request = TransferRequest(
            externalReferenceId = externalReferenceId,
            accountNumber = payout.accountNumber,
            ifscCode = payout.ifscCode,
            beneficiaryName = payout.beneficiaryName,
            amountInr = payout.amount
        )

        val timer = payoutMetrics.startChannelTimer(channel.channelType)
        val result = channel.transfer(request)
        timer.stop()

        when (result) {
            is TransferResult.Success -> {
                payout.markSuccess()
                payoutRepository.save(payout)
                publishPayoutCompleted(payout)
                payoutMetrics.incrementSuccess(channel.channelType)
                log.info("payoutId={} orderId={} success channel={} externalRef={}", payout.id, payout.orderId, channel.channelType, result.externalReferenceId)
            }
            is TransferResult.Failure -> {
                payout.markFailed(result.failureType, result.message)
                payoutRepository.save(payout)
                payoutMetrics.incrementFailed(channel.channelType)
                log.warn("payoutId={} orderId={} failed channel={} type={} message={}", payout.id, payout.orderId, channel.channelType, result.failureType, result.message)
            }
        }
    }

    @Transactional
    fun retryManually(payoutId: UUID): Payout {
        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { IllegalArgumentException("Payout $payoutId not found") }

        if (!payout.isRetryable) {
            throw IllegalStateException("Payout $payoutId is not retryable: status=${payout.status} retryCount=${payout.retryCount} maxRetries=${payout.maxRetries}")
        }

        payout.status = PayoutStatus.PENDING
        val saved = payoutRepository.save(payout)
        log.info("payoutId={} manually queued for retry", payoutId)
        return saved
    }

    private fun publishPayoutCompleted(payout: Payout) {
        try {
            val event = mapOf(
                "payoutId" to payout.id.toString(),
                "orderId" to payout.orderId,
                "sellerId" to payout.sellerId,
                "amount" to payout.amount,
                "channel" to payout.channel?.name
            )
            kafkaTemplate.send(payoutCompletedTopic, payout.orderId, event)
        } catch (ex: Exception) {
            log.error("payoutId={} failed to publish payout.completed event", payout.id, ex)
        }
    }
}
