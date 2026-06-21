package com.mykart.common.events;

import java.math.BigDecimal;
import java.util.List;

public record OrderConfirmedEvent(
        String orderId,
        String orderNumber,
        String buyerId,
        String sellerId,
        BigDecimal totalAmount,
        String accountNumber,
        String ifscCode,
        String beneficiaryName,
        List<OrderItem> items
) {
    public record OrderItem(
            String skuCode,
            String productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
