package com.mykart.inventory.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykart.common.events.OrderCancelledEvent;
import com.mykart.common.events.OrderConfirmedEvent;
import com.mykart.inventory.dto.request.ConfirmRequest;
import com.mykart.inventory.dto.request.ReleaseRequest;
import com.mykart.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public OrderEventListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order.confirmed", groupId = "inventory-service")
    public void onOrderConfirmed(String payload) {
        try {
            var event = objectMapper.readValue(payload, OrderConfirmedEvent.class);
            log.info("Processing order.confirmed: orderId={}", event.orderId());
            var items = event.items().stream()
                    .map(i -> new ConfirmRequest.Item(i.skuCode(), i.quantity()))
                    .toList();
            inventoryService.confirm(new ConfirmRequest(items));
        } catch (Exception e) {
            log.error("Failed to process order.confirmed event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "order.cancelled", groupId = "inventory-service")
    public void onOrderCancelled(String payload) {
        try {
            var event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Processing order.cancelled: orderId={}", event.orderId());
            // OrderCancelledEvent doesn't carry item details — release is handled by order-service directly.
            // This listener is for eventual consistency if a downstream cancel is needed.
        } catch (Exception e) {
            log.error("Failed to process order.cancelled event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
