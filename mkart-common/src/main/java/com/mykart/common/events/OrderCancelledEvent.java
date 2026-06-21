package com.mykart.common.events;

public record OrderCancelledEvent(
        String orderId,
        String orderNumber,
        String buyerId,
        String reason
) {}
