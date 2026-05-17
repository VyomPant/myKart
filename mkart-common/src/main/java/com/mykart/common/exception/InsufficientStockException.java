package com.mykart.common.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String skuCode, int requested, int available) {
        super(String.format("Insufficient stock for SKU %s: requested %d, available %d",
                skuCode, requested, available));
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}
