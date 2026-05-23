package com.mykart.payment.channel

import java.math.BigDecimal
import java.util.UUID

data class TransferRequest(
    val externalReferenceId: UUID,
    val accountNumber: String,
    val ifscCode: String,
    val beneficiaryName: String,
    val amountInr: BigDecimal,
    val remarks: String = "myKart seller payout"
)
