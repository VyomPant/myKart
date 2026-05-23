package com.mykart.payment.channel

import com.mykart.payment.enums.Channel
import org.springframework.stereotype.Component

@Component
class NeftChannel : MockPaymentChannel() {

    override val channelType: Channel = Channel.NEFT
    override val transientFailureRate: Double = 0.03
    override val permanentFailureRate: Double = 0.01
    override val unavailableRate: Double = 0.01
}
