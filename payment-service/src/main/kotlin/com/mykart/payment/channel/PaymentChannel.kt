package com.mykart.payment.channel

import com.mykart.payment.enums.Channel

interface PaymentChannel {

    val channelType: Channel

    fun isAvailable(): Boolean

    fun transfer(request: TransferRequest): TransferResult

    fun inquireStatus(externalReferenceId: String): StatusResult
}
