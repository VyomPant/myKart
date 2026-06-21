package com.mykart.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInventoryRequest(
        @NotBlank String skuCode,
        @NotNull UUID productId,
        @Min(0) int quantity
) {}
