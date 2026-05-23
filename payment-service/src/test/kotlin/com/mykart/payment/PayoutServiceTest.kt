package com.mykart.payment

import com.mykart.payment.channel.ChannelSelector
import com.mykart.payment.channel.TransferResult
import com.mykart.payment.channel.UpiChannel
import com.mykart.payment.entity.Payout
import com.mykart.payment.enums.Channel
import com.mykart.payment.enums.FailureType
import com.mykart.payment.enums.PayoutStatus
import com.mykart.payment.metric.PayoutMetrics
import com.mykart.payment.repository.PayoutRepository
import com.mykart.payment.service.PayoutService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class PayoutServiceTest {

    private val payoutRepository: PayoutRepository = mock()
    private val channelSelector: ChannelSelector = mock()
    private val kafkaTemplate: KafkaTemplate<String, Any> = mock()
    private val meterRegistry = SimpleMeterRegistry()
    private val payoutMetrics: PayoutMetrics = mock()

    private lateinit var payoutService: PayoutService

    @BeforeEach
    fun setUp() {
        payoutService = PayoutService(
            payoutRepository = payoutRepository,
            channelSelector = channelSelector,
            kafkaTemplate = kafkaTemplate,
            payoutMetrics = payoutMetrics,
            platformFeeRate = BigDecimal("0.02"),
            maxRetries = 5,
            payoutCompletedTopic = "payout.completed"
        )
        doAnswer { it.arguments[0] }.`when`(payoutRepository).save(any())
    }

    @Test
    fun `createIfAbsent deducts platform fee and saves`() {
        whenever(payoutRepository.findByOrderId("order-1")).thenReturn(Optional.empty())

        val payout = payoutService.createIfAbsent(
            orderId = "order-1",
            sellerId = "seller-1",
            totalAmount = BigDecimal("1000.00"),
            accountNumber = "1234567890",
            ifscCode = "HDFC0001234",
            beneficiaryName = "Test Seller"
        )

        assertEquals(BigDecimal("980.00"), payout.amount)
        assertEquals(PayoutStatus.PENDING, payout.status)
        verify(payoutMetrics).incrementCreated()
    }

    @Test
    fun `createIfAbsent returns existing payout when orderId already present`() {
        val existingPayout = Payout(
            orderId = "order-1", sellerId = "seller-1", amount = BigDecimal("980.00"),
            accountNumber = "123", ifscCode = "HDFC0001", beneficiaryName = "Seller"
        )
        whenever(payoutRepository.findByOrderId("order-1")).thenReturn(Optional.of(existingPayout))

        val result = payoutService.createIfAbsent(
            orderId = "order-1", sellerId = "seller-1", totalAmount = BigDecimal("1000.00"),
            accountNumber = "123", ifscCode = "HDFC0001", beneficiaryName = "Seller"
        )

        assertEquals(existingPayout, result)
        verify(payoutRepository, org.mockito.kotlin.never()).save(any())
    }

    @Test
    fun `processOne marks success and publishes event on transfer success`() {
        val payout = Payout(
            orderId = "order-2", sellerId = "seller-2", amount = BigDecimal("500.00"),
            accountNumber = "123", ifscCode = "SBIN0001", beneficiaryName = "Seller B"
        )
        val upiChannel: UpiChannel = mock()
        val stopwatch: PayoutMetrics.Stopwatch = mock()

        whenever(channelSelector.selectFor(payout.amount)).thenReturn(upiChannel)
        whenever(upiChannel.channelType).thenReturn(Channel.UPI)
        whenever(upiChannel.transfer(any())).thenReturn(TransferResult.Success("ext-ref-123"))
        whenever(payoutMetrics.startChannelTimer(Channel.UPI)).thenReturn(stopwatch)

        payoutService.processOne(payout)

        assertEquals(PayoutStatus.SUCCESS, payout.status)
        verify(payoutMetrics).incrementSuccess(Channel.UPI)
    }

    @Test
    fun `processOne marks failed with PERMANENT type on permanent failure`() {
        val payout = Payout(
            orderId = "order-3", sellerId = "seller-3", amount = BigDecimal("200.00"),
            accountNumber = "999", ifscCode = "INVALID", beneficiaryName = "Seller C"
        )
        val impsChannel: com.mykart.payment.channel.ImpsChannel = mock()
        val stopwatch: PayoutMetrics.Stopwatch = mock()

        whenever(channelSelector.selectFor(payout.amount)).thenReturn(impsChannel)
        whenever(impsChannel.channelType).thenReturn(Channel.IMPS)
        whenever(impsChannel.transfer(any())).thenReturn(TransferResult.Failure("Invalid IFSC", FailureType.PERMANENT))
        whenever(payoutMetrics.startChannelTimer(Channel.IMPS)).thenReturn(stopwatch)

        payoutService.processOne(payout)

        assertEquals(PayoutStatus.FAILED, payout.status)
        assertEquals(FailureType.PERMANENT, payout.failureType)
        assertEquals(1, payout.retryCount)
        verify(payoutMetrics).incrementFailed(Channel.IMPS)
    }
}
