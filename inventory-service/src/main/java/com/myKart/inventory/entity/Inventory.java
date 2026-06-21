package com.mykart.inventory.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "sku_code", unique = true, nullable = false)
    private String skuCode;

    @Column(name = "product_id", columnDefinition = "uuid")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Inventory() {}

    public Inventory(UUID id, String skuCode, UUID productId, int quantity) {
        this.id = id;
        this.skuCode = skuCode;
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public int availableQuantity() {
        return quantity - reservedQuantity;
    }

    public UUID getId() { return id; }
    public String getSkuCode() { return skuCode; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public void setProductId(UUID productId) { this.productId = productId; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
