package com.mykart.payment.service

import com.mykart.payment.channel.ChannelSelector
import com.mykart.payment.channel.StatusResult
import com.mykart.payment.dto.request.ReconcileRequest
import com.mykart.payment.dto.response.ReconcileResponse
import com.mykart.payment.enums.FailureType
import com.mykart.payment.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReconciliationService(
    private val payoutRepository: PayoutRepository,
    private val channelSelector: ChannelSelector
) {

    private val log = LoggerFactory.getLogger(ReconciliationService::class.java)

    @Transactional
    fun reconcile(request: ReconcileRequest): ReconcileResponse {
        val bankSuccessRefs = request.successfulExternalReferenceIds.toSet()
        val dbSuccessPayouts = payoutRepository.findAllSuccessfulWithExternalRef()

        val dbSuccessRefs = dbSuccessPayouts.mapNotNull { it.externalReferenceId }.toSet()

        val onlyInBank = bankSuccessRefs - dbSuccessRefs
        val onlyInDb = dbSuccessRefs - bankSuccessRefs
        val matched = bankSuccessRefs.intersect(dbSuccessRefs).size

        if (onlyInBank.isNotEmpty()) {
            log.warn("reconciliation found {} transfers succeeded at bank but not in DB — investigate", onlyInBank.size)
        }

        if (onlyInDb.isNotEmpty()) {
            log.warn("reconciliation found {} transfers marked SUCCESS in DB but not in bank statement", onlyInDb.size)
        }

        val healedCount = healOrphans(onlyInBank)

        log.info("reconciliation complete matched={} onlyInBank={} onlyInDb={} healed={}", matched, onlyInBank.size, onlyInDb.size, healedCount)

        return ReconcileResponse(
            matched = matched,
            onlyInBank = onlyInBank.size,
            onlyInDb = onlyInDb.size,
            healed = healedCount
        )
    }

    private fun healOrphans(bankOnlyRefs: Set<String>): Int {
        if (bankOnlyRefs.isEmpty()) return 0

        val retryableFailed = payoutRepository.findAllSuccessfulWithExternalRef()
            .filter { it.externalReferenceId in bankOnlyRefs }

        var healed = 0
        for (payout in retryableFailed) {
            val channel = payout.channel?.let { channelSelector.getChannel(it) } ?: continue
            val status = channel.inquireStatus(payout.externalReferenceId!!)
            if (status is StatusResult.Success) {
                payout.markSuccess()
                payoutRepository.save(payout)
                healed++
                log.info("payoutId={} healed via reconciliation externalRef={}", payout.id, payout.externalReferenceId)
            }
        }
        return healed
    }
}
