package com.mykart.product.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "products")
public class Product {

    @Id
    private String id;

    private String sellerId;

    @TextIndexed(weight = 3)
    private String name;

    private String category;

    @TextIndexed
    private String description;

    private BigDecimal price;

    private String skuCode;

    private Map<String, String> specs;

    // 1536-dim OpenAI embedding; null when AI is disabled or not yet computed
    private List<Double> embedding;

    private Instant createdAt;

    private Instant updatedAt;

    public Product() {}

    public Product(String id, String sellerId, String name, String category, String description,
                   BigDecimal price, String skuCode, Map<String, String> specs,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.sellerId = sellerId;
        this.name = name;
        this.category = category;
        this.description = description;
        this.price = price;
        this.skuCode = skuCode;
        this.specs = specs;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getSkuCode() { return skuCode; }
    public Map<String, String> getSpecs() { return specs; }
    public List<Double> getEmbedding() { return embedding; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public void setSpecs(Map<String, String> specs) { this.specs = specs; }
    public void setEmbedding(List<Double> embedding) { this.embedding = embedding; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
