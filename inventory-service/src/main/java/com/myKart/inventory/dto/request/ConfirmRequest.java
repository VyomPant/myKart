package com.mykart.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfirmRequest(@NotEmpty @Valid List<Item> items) {
    public record Item(@NotBlank String skuCode, @Min(1) int quantity) {}
}
