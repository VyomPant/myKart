package com.mykart.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank String name,
        @NotBlank String category,
        @NotBlank String description,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotBlank String skuCode,
        Map<String, String> specs
) {}
