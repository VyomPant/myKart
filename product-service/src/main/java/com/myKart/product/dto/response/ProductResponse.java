package com.mykart.product.dto.response;

import com.mykart.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
        String sellerId,
        String name,
        String category,
        String description,
        BigDecimal price,
        String skuCode,
        Map<String, String> specs,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getCategory(),
                product.getDescription(),
                product.getPrice(),
                product.getSkuCode(),
                product.getSpecs(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
