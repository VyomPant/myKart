package com.mykart.payment.channel

import com.mykart.payment.enums.FailureType
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

abstract class MockPaymentChannel : PaymentChannel {

    private val log = LoggerFactory.getLogger(javaClass)

    private val successfulTransfers = ConcurrentHashMap.newKeySet<String>()

    protected abstract val transientFailureRate: Double
    protected abstract val permanentFailureRate: Double
    protected abstract val unavailableRate: Double

    override fun isAvailable(): Boolean {
        val available = Math.random() >= unavailableRate
        if (!available) log.warn("channel={} unavailable (simulated)", channelType)
        return available
    }

    override fun transfer(request: TransferRequest): TransferResult {
        val refId = request.externalReferenceId.toString()

        if (successfulTransfers.contains(refId)) {
            log.info("channel={} duplicate transfer detected externalRef={}", channelType, refId)
            return TransferResult.Success(refId)
        }

        val roll = Math.random()
        return when {
            roll < permanentFailureRate -> {
                log.warn("channel={} permanent failure externalRef={}", channelType, refId)
                TransferResult.Failure("Invalid account or IFSC", FailureType.PERMANENT)
            }
            roll < permanentFailureRate + transientFailureRate -> {
                log.warn("channel={} transient failure externalRef={}", channelType, refId)
                TransferResult.Failure("Timeout or rate limit", FailureType.TRANSIENT)
            }
            else -> {
                successfulTransfers.add(refId)
                log.info("channel={} transfer success externalRef={}", channelType, refId)
                TransferResult.Success(refId)
            }
        }
    }

    override fun inquireStatus(externalReferenceId: String): StatusResult {
        return if (successfulTransfers.contains(externalReferenceId)) {
            StatusResult.Success
        } else {
            StatusResult.Unknown
        }
    }
}
