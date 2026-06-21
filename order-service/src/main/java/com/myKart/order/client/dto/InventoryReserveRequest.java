package com.mykart.order.client.dto;

import java.util.List;

public record InventoryReserveRequest(List<Item> items) {
    public record Item(String skuCode, int quantity) {}
}
