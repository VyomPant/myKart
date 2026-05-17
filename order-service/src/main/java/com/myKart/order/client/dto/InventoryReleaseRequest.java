package com.mykart.order.client.dto;

import java.util.List;

public record InventoryReleaseRequest(List<Item> items) {
    public record Item(String skuCode, int quantity) {}
}
