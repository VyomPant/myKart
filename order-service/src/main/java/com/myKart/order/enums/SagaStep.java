package com.mykart.order.enums;

public enum SagaStep {
    INVENTORY_RESERVE,
    ORDER_PERSIST,
    OUTBOX_WRITE,
    COMPLETED,
    COMPENSATING,
    FAILED
}
