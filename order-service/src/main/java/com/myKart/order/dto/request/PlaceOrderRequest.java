package com.mykart.order.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotBlank String sellerId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotBlank String skuCode,
            @NotNull UUID productId,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice
    ) {}
}
