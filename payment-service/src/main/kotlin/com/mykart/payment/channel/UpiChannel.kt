package com.mykart.payment.channel

import com.mykart.payment.enums.Channel
import org.springframework.stereotype.Component

@Component
class UpiChannel : MockPaymentChannel() {

    override val channelType: Channel = Channel.UPI
    override val transientFailureRate: Double = 0.05
    override val permanentFailureRate: Double = 0.02
    override val unavailableRate: Double = 0.03
}
