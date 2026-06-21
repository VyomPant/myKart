package com.mykart.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykart.common.events.OrderCancelledEvent;
import com.mykart.common.events.OrderConfirmedEvent;
import com.mykart.order.client.InventoryClient;
import com.mykart.order.client.dto.InventoryReleaseRequest;
import com.mykart.order.dto.request.PlaceOrderRequest;
import com.mykart.order.entity.Order;
import com.mykart.order.entity.OrderLineItem;
import com.mykart.order.entity.OutboxEvent;
import com.mykart.order.entity.SagaState;
import com.mykart.order.enums.OrderStatus;
import com.mykart.order.enums.SagaStep;
import com.mykart.order.repository.OrderRepository;
import com.mykart.order.repository.OutboxEventRepository;
import com.mykart.order.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Holds methods that must execute in their own independent database transaction.
 * Extracted from OrderService to allow Spring AOP to properly apply REQUIRES_NEW
 * (self-invocation on the same bean bypasses the proxy).
 */
@Service
public class OrderTransactionService {

    private static final Logger log = LoggerFactory.getLogger(OrderTransactionService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SagaStateRepository sagaStateRepository;
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    public OrderTransactionService(OrderRepository orderRepository,
                                   OutboxEventRepository outboxEventRepository,
                                   SagaStateRepository sagaStateRepository,
                                   InventoryClient inventoryClient,
                                   ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createOrderAndSagaState(PlaceOrderRequest request, String buyerId) {
        UUID orderId = UUID.randomUUID();
        BigDecimal total = request.items().stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var order = new Order(orderId,
                "ORD-" + orderId.toString().substring(0, 8).toUpperCase(),
                buyerId, request.sellerId(), total, List.of());

        List<OrderLineItem> lineItems = request.items().stream()
                .map(i -> new OrderLineItem(UUID.randomUUID(), order,
                        i.skuCode(), i.productId(), i.quantity(), i.unitPrice()))
                .toList();
        order.getLineItems().addAll(lineItems);

        orderRepository.save(order);
        sagaStateRepository.save(new SagaState(orderId, SagaStep.INVENTORY_RESERVE));
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advanceSagaStep(UUID orderId, SagaStep step) {
        sagaStateRepository.findById(orderId).ifPresent(saga -> {
            saga.setStep(step);
            sagaStateRepository.save(saga);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmOrderAndWriteOutbox(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        String payload = serialize(buildConfirmedEvent(order));
        outboxEventRepository.save(new OutboxEvent(orderId, "ORDER_CONFIRMED", payload));

        sagaStateRepository.findById(orderId).ifPresent(saga -> {
            saga.setStep(SagaStep.OUTBOX_WRITE);
            sagaStateRepository.save(saga);
        });
        log.info("Outbox event written: orderId={} eventType=ORDER_CONFIRMED", orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelOrderAndWriteOutbox(UUID orderId, String reason, List<OrderLineItem> itemsToRelease) {
        try {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            if (!itemsToRelease.isEmpty()) {
                var releaseRequest = new InventoryReleaseRequest(
                        itemsToRelease.stream()
                                .map(i -> new InventoryReleaseRequest.Item(i.getSkuCode(), i.getQuantity()))
                                .toList()
                );
                inventoryClient.release(releaseRequest);
            }

            String payload = serialize(new OrderCancelledEvent(
                    orderId.toString(), order.getOrderNumber(), order.getBuyerId(), reason));
            outboxEventRepository.save(new OutboxEvent(orderId, "ORDER_CANCELLED", payload));

            SagaState saga = sagaStateRepository.findById(orderId)
                    .orElse(new SagaState(orderId, SagaStep.FAILED));
            saga.setStep(SagaStep.FAILED);
            saga.setFailureReason(reason);
            sagaStateRepository.save(saga);
        } catch (Exception e) {
            log.error("Compensation transaction failed for orderId={}. Manual intervention required.", orderId, e);
        }
    }

    private OrderConfirmedEvent buildConfirmedEvent(Order order) {
        var items = order.getLineItems().stream()
                .map(i -> new OrderConfirmedEvent.OrderItem(
                        i.getSkuCode(), i.getProductId().toString(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderConfirmedEvent(
                order.getId().toString(), order.getOrderNumber(),
                order.getBuyerId(), order.getSellerId(),
                order.getTotalAmount(), "", "", "", items);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
