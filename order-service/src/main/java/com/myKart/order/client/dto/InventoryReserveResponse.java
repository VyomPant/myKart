package com.mykart.order.client.dto;

import java.util.List;

public record InventoryReserveResponse(boolean reserved, List<Item> items) {
    public record Item(String skuCode, int reservedQuantity) {}
}
