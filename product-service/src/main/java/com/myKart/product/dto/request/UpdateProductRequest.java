package com.mykart.product.dto.request;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateProductRequest(
        String name,
        String category,
        String description,
        @DecimalMin("0.01") BigDecimal price,
        Map<String, String> specs
) {}
