package com.mykart.order.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_line_items")
public class OrderLineItem {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku_code", nullable = false)
    private String skuCode;

    @Column(name = "product_id", columnDefinition = "uuid")
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    public OrderLineItem() {}

    public OrderLineItem(UUID id, Order order, String skuCode, UUID productId, int quantity, BigDecimal unitPrice) {
        this.id = id;
        this.order = order;
        this.skuCode = skuCode;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public String getSkuCode() { return skuCode; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public void setOrder(Order order) { this.order = order; }
}
