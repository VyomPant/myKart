package com.mykart.payment.listener

import com.mykart.common.events.OrderConfirmedEvent
import com.mykart.payment.service.PayoutService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventListener(private val payoutService: PayoutService) {

    private val log = LoggerFactory.getLogger(OrderEventListener::class.java)

    @KafkaListener(
        topics = ["\${spring.kafka.consumer.topic.order-confirmed:order.confirmed}"],
        groupId = "\${spring.kafka.consumer.group-id:payment-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun onOrderConfirmed(event: OrderConfirmedEvent) {
        log.info("received order.confirmed orderId={} sellerId={} amount={}", event.orderId(), event.sellerId(), event.totalAmount())
        try {
            payoutService.createIfAbsent(
                orderId = event.orderId(),
                sellerId = event.sellerId(),
                totalAmount = event.totalAmount(),
                accountNumber = event.accountNumber(),
                ifscCode = event.ifscCode(),
                beneficiaryName = event.beneficiaryName()
            )
        } catch (ex: Exception) {
            log.error("failed to create payout for orderId={}", event.orderId(), ex)
            throw ex
        }
    }
}
