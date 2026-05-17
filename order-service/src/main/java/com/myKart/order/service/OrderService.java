package com.mykart.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykart.common.dto.PagedResponse;
import com.mykart.common.events.OrderCancelledEvent;
import com.mykart.common.exception.InsufficientStockException;
import com.mykart.common.exception.ResourceNotFoundException;
import com.mykart.order.client.InventoryClient;
import com.mykart.order.client.dto.InventoryReserveRequest;
import com.mykart.order.dto.request.PlaceOrderRequest;
import com.mykart.order.dto.response.OrderResponse;
import com.mykart.order.entity.Order;
import com.mykart.order.entity.OutboxEvent;
import com.mykart.order.entity.SagaState;
import com.mykart.order.enums.OrderStatus;
import com.mykart.order.enums.SagaStep;
import com.mykart.order.repository.OrderRepository;
import com.mykart.order.repository.OutboxEventRepository;
import com.mykart.order.repository.SagaStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SagaStateRepository sagaStateRepository;
    private final InventoryClient inventoryClient;
    private final OrderTransactionService orderTransactionService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository,
                        SagaStateRepository sagaStateRepository,
                        InventoryClient inventoryClient,
                        OrderTransactionService orderTransactionService,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.sagaStateRepository = sagaStateRepository;
        this.inventoryClient = inventoryClient;
        this.orderTransactionService = orderTransactionService;
        this.objectMapper = objectMapper;
    }

    public OrderResponse placeOrder(PlaceOrderRequest request, String buyerId) {
        // Step 1: Create order + saga state in its own committed transaction
        Order order = orderTransactionService.createOrderAndSagaState(request, buyerId);
        MDC.put("orderId", order.getId().toString());
        MDC.put("buyerId", buyerId);

        try {
            // Step 2: Call inventory-service synchronously (outside any transaction)
            log.info("Saga INVENTORY_RESERVE: orderId={}", order.getId());
            var reserveRequest = new InventoryReserveRequest(
                    request.items().stream()
                            .map(i -> new InventoryReserveRequest.Item(i.skuCode(), i.quantity()))
                            .toList()
            );
            inventoryClient.reserve(reserveRequest);
            orderTransactionService.advanceSagaStep(order.getId(), SagaStep.ORDER_PERSIST);

            // Step 3: Confirm order + write outbox in its own committed transaction
            log.info("Saga ORDER_PERSIST → OUTBOX_WRITE: orderId={}", order.getId());
            orderTransactionService.confirmOrderAndWriteOutbox(order.getId());
            log.info("Saga OUTBOX_WRITE: orderId={} — event queued for Kafka", order.getId());

        } catch (InsufficientStockException e) {
            log.warn("Saga FAILED — insufficient stock: orderId={}", order.getId());
            orderTransactionService.cancelOrderAndWriteOutbox(
                    order.getId(), e.getMessage(), order.getLineItems());
            throw e;
        } catch (InventoryClient.InventoryServiceUnavailableException e) {
            log.error("Saga FAILED — inventory unavailable: orderId={}", order.getId());
            orderTransactionService.cancelOrderAndWriteOutbox(
                    order.getId(), "Inventory service unavailable", order.getLineItems());
            throw e;
        } catch (Exception e) {
            log.error("Saga FAILED — unexpected error: orderId={}", order.getId(), e);
            orderTransactionService.cancelOrderAndWriteOutbox(
                    order.getId(), e.getMessage(), order.getLineItems());
            throw new RuntimeException("Order placement failed", e);
        } finally {
            MDC.remove("orderId");
            MDC.remove("buyerId");
        }

        return OrderResponse.from(orderRepository.findById(order.getId()).orElseThrow());
    }

    public OrderResponse getById(UUID id) {
        return orderRepository.findById(id)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    public PagedResponse<OrderResponse> listByBuyer(String buyerId, int page, int size) {
        Page<Order> result = orderRepository.findByBuyerIdOrderByCreatedAtDesc(
                buyerId, PageRequest.of(page, size));
        return new PagedResponse<>(
                result.getContent().stream().map(OrderResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @Transactional
    public OrderResponse cancel(UUID orderId, String buyerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new IllegalArgumentException("You do not own this order");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        String payload = serialize(new OrderCancelledEvent(
                orderId.toString(), order.getOrderNumber(), buyerId, "Cancelled by buyer"));
        outboxEventRepository.save(new OutboxEvent(orderId, "ORDER_CANCELLED", payload));

        SagaState saga = sagaStateRepository.findById(orderId)
                .orElse(new SagaState(orderId, SagaStep.FAILED));
        saga.setStep(SagaStep.FAILED);
        saga.setFailureReason("Cancelled by buyer");
        sagaStateRepository.save(saga);

        log.info("Order cancelled: orderId={} buyerId={}", orderId, buyerId);
        return OrderResponse.from(order);
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
