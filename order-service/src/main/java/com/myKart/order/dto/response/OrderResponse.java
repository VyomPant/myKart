package com.mykart.order.dto.response;

import com.mykart.order.entity.Order;
import com.mykart.order.entity.OrderLineItem;
import com.mykart.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String buyerId,
        String sellerId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<LineItemResponse> lineItems,
        Instant createdAt,
        Instant updatedAt
) {
    public record LineItemResponse(UUID id, String skuCode, UUID productId, int quantity, BigDecimal unitPrice) {
        public static LineItemResponse from(OrderLineItem item) {
            return new LineItemResponse(item.getId(), item.getSkuCode(), item.getProductId(),
                    item.getQuantity(), item.getUnitPrice());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getLineItems().stream().map(LineItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
