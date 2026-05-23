package com.mykart.payment.channel

sealed class StatusResult {
    data object Success : StatusResult()
    data object Pending : StatusResult()
    data object Unknown : StatusResult()
    data class Failed(val message: String) : StatusResult()
}
