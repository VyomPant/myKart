package com.mykart.payment.channel

import com.mykart.payment.enums.Channel
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ChannelSelector(
    private val upiChannel: UpiChannel,
    private val impsChannel: ImpsChannel,
    private val neftChannel: NeftChannel
) {

    private val orderedChannels: List<PaymentChannel> = listOf(upiChannel, impsChannel, neftChannel)

    fun selectFor(amountInr: BigDecimal): PaymentChannel? {
        val amountLong = amountInr.toLong()
        return orderedChannels.firstOrNull { channel ->
            channel.channelType.supportsAmount(amountLong) && channel.isAvailable()
        }
    }

    fun getChannel(channelType: Channel): PaymentChannel = when (channelType) {
        Channel.UPI -> upiChannel
        Channel.IMPS -> impsChannel
        Channel.NEFT -> neftChannel
    }
}
