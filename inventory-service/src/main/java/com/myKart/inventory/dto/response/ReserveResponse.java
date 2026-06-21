package com.mykart.inventory.dto.response;

import java.util.List;

public record ReserveResponse(boolean reserved, List<Item> items) {
    public record Item(String skuCode, int reservedQuantity) {}
}
