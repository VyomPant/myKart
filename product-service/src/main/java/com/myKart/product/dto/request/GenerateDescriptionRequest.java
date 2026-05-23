package com.mykart.product.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record GenerateDescriptionRequest(
        @NotBlank String name,
        @NotBlank String category,
        Map<String, String> specs
) {}
