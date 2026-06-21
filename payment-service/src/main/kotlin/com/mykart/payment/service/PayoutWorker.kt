package com.mykart.payment.service

import com.mykart.payment.metric.PayoutMetrics
import com.mykart.payment.repository.PayoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class PayoutWorker(
    private val payoutRepository: PayoutRepository,
    private val payoutService: PayoutService,
    private val payoutMetrics: PayoutMetrics,
    @Value("\${payment.payout.batch-size:50}") private val batchSize: Int,
    @Value("\${payment.payout.backoff-base-seconds:60}") private val backoffBaseSeconds: Long
) {

    private val log = LoggerFactory.getLogger(PayoutWorker::class.java)

    @Scheduled(fixedDelayString = "\${payment.payout.worker-interval-ms:60000}")
    fun process() {
        val threshold = Instant.now().minus(backoffBaseSeconds, ChronoUnit.SECONDS)
        val payouts = payoutRepository.findPendingOrRetryable(threshold, PageRequest.of(0, batchSize))

        if (payouts.isEmpty()) return

        log.info("PayoutWorker processing batch size={}", payouts.size)

        runBlocking {
            supervisorScope {
                payouts.forEach { payout ->
                    launch(Dispatchers.IO) {
                        payoutMetrics.incrementRetry()
                        try {
                            payoutService.processOne(payout)
                        } catch (ex: Exception) {
                            log.error("payoutId={} unhandled error during processing", payout.id, ex)
                        }
                    }
                }
            }
        }

        log.info("PayoutWorker batch complete size={}", payouts.size)
    }
}
