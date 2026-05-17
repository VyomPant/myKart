package com.mykart.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykart.common.events.OrderCancelledEvent;
import com.mykart.common.events.OrderConfirmedEvent;
import com.mykart.common.events.PayoutCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;

    public OrderEventListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.confirmed", groupId = "notification-service")
    public void onOrderConfirmed(String payload) {
        try {
            var event = objectMapper.readValue(payload, OrderConfirmedEvent.class);
            log.info("Order confirmed: orderNumber={} buyerId={} sellerId={} totalAmount={}",
                    event.orderNumber(), event.buyerId(), event.sellerId(), event.totalAmount());
            // Stub: send confirmation email via SendGrid / SMS via Twilio
        } catch (Exception e) {
            log.error("Failed to process order.confirmed event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-service")
    public void onOrderCancelled(String payload) {
        try {
            var event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Order cancelled: orderNumber={} buyerId={} reason={}",
                    event.orderNumber(), event.buyerId(), event.reason());
            // Stub: send cancellation notification
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "payout.completed", groupId = "notification-service")
    public void onPayoutCompleted(String payload) {
        try {
            var event = objectMapper.readValue(payload, PayoutCompletedEvent.class);
            log.info("Payout completed: amount={} sellerId={} channel={} orderId={}",
                    event.amount(), event.sellerId(), event.channel(), event.orderId());
            // Stub: notify seller of successful payout
        } catch (Exception e) {
            log.error("Failed to process payout.completed event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
