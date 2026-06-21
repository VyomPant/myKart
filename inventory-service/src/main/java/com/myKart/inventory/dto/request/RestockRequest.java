package com.mykart.inventory.dto.request;

import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(1) int quantity) {}
