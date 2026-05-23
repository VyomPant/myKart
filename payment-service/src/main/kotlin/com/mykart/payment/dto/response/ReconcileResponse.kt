package com.mykart.payment.dto.response

data class ReconcileResponse(
    val matched: Int,
    val onlyInBank: Int,
    val onlyInDb: Int,
    val healed: Int
)
