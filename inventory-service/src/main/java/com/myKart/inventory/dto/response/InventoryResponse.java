package com.mykart.inventory.dto.response;

import com.mykart.inventory.entity.Inventory;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID id,
        String skuCode,
        UUID productId,
        int quantity,
        int reservedQuantity,
        int availableQuantity,
        Instant updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getSkuCode(),
                inventory.getProductId(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.availableQuantity(),
                inventory.getUpdatedAt()
        );
    }
}
