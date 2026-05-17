package com.mykart.common.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    public IdempotencyConflictException(String resourceName, String idempotencyKey) {
        super(String.format("%s already exists for idempotency key: %s", resourceName, idempotencyKey));
    }
}
