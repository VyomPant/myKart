package com.mykart.payment.controller

import com.mykart.payment.dto.request.ReconcileRequest
import com.mykart.payment.dto.response.PayoutResponse
import com.mykart.payment.dto.response.ReconcileResponse
import com.mykart.payment.repository.PayoutRepository
import com.mykart.payment.service.PayoutService
import com.mykart.payment.service.ReconciliationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Seller payout management")
class PaymentController(
    private val payoutRepository: PayoutRepository,
    private val payoutService: PayoutService,
    private val reconciliationService: ReconciliationService
) {

    @GetMapping("/{payoutId}")
    @Operation(summary = "Get payout by ID")
    fun getById(@PathVariable payoutId: UUID): PayoutResponse {
        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { NoSuchElementException("Payout $payoutId not found") }
        return PayoutResponse.from(payout)
    }

    @GetMapping
    @Operation(summary = "Get payout by orderId")
    fun getByOrderId(@RequestParam orderId: String): PayoutResponse {
        val payout = payoutRepository.findByOrderId(orderId)
            .orElseThrow { NoSuchElementException("No payout found for orderId=$orderId") }
        return PayoutResponse.from(payout)
    }

    @PostMapping("/{payoutId}/retry")
    @Operation(summary = "Manually trigger retry for a failed payout")
    fun retry(@PathVariable payoutId: UUID): ResponseEntity<PayoutResponse> {
        val payout = payoutService.retryManually(payoutId)
        return ResponseEntity.accepted().body(PayoutResponse.from(payout))
    }

    @PostMapping("/reconcile")
    @Operation(summary = "Reconcile DB payouts against bank statement")
    fun reconcile(@Valid @RequestBody request: ReconcileRequest): ReconcileResponse {
        return reconciliationService.reconcile(request)
    }
}
