package com.mykart.order.entity;

import com.mykart.order.enums.SagaStep;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_state")
public class SagaState {

    @Id
    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "saga_step")
    private SagaStep step;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SagaState() {}

    public SagaState(UUID orderId, SagaStep step) {
        this.orderId = orderId;
        this.step = step;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() { return orderId; }
    public SagaStep getStep() { return step; }
    public String getFailureReason() { return failureReason; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStep(SagaStep step) {
        this.step = step;
        this.updatedAt = Instant.now();
    }

    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
