package com.mykart.payment.channel

import com.mykart.payment.enums.FailureType

sealed class TransferResult {
    data class Success(val externalReferenceId: String) : TransferResult()
    data class Failure(val message: String, val failureType: FailureType) : TransferResult()
}
