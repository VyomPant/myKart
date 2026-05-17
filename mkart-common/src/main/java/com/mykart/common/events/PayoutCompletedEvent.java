package com.mykart.common.events;

import java.math.BigDecimal;

public record PayoutCompletedEvent(
        String payoutId,
        String orderId,
        String sellerId,
        BigDecimal amount,
        String channel
) {}
