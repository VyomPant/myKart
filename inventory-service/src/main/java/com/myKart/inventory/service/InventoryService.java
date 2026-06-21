package com.mykart.inventory.service;

import com.mykart.common.exception.InsufficientStockException;
import com.mykart.common.exception.ResourceNotFoundException;
import com.mykart.inventory.dto.request.*;
import com.mykart.inventory.dto.response.InventoryResponse;
import com.mykart.inventory.dto.response.ReserveResponse;
import com.mykart.inventory.entity.Inventory;
import com.mykart.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final String STOCK_CACHE = "inventory-stock";

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryResponse create(CreateInventoryRequest request) {
        if (inventoryRepository.findBySkuCode(request.skuCode()).isPresent()) {
            throw new IllegalArgumentException("SKU already exists: " + request.skuCode());
        }
        var inventory = new Inventory(UUID.randomUUID(), request.skuCode(), request.productId(), request.quantity());
        var saved = inventoryRepository.save(inventory);
        log.info("Inventory created: skuCode={} quantity={}", saved.getSkuCode(), saved.getQuantity());
        return InventoryResponse.from(saved);
    }

    @Cacheable(value = STOCK_CACHE, key = "#skuCode")
    public int getAvailableStock(String skuCode) {
        return inventoryRepository.findBySkuCode(skuCode)
                .map(Inventory::availableQuantity)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "skuCode", skuCode));
    }

    public List<InventoryResponse> getBySkuCodes(List<String> skuCodes) {
        return inventoryRepository.findAllBySkuCodeIn(skuCodes).stream()
                .map(InventoryResponse::from)
                .toList();
    }

    @Transactional
    @CacheEvict(value = STOCK_CACHE, allEntries = true)
    public ReserveResponse reserve(ReserveRequest request) {
        List<String> skuCodes = request.items().stream().map(ReserveRequest.Item::skuCode).toList();
        Map<String, Inventory> inventoryMap = inventoryRepository.findAllBySkuCodeInForUpdate(skuCodes)
                .stream()
                .collect(Collectors.toMap(Inventory::getSkuCode, Function.identity()));

        for (ReserveRequest.Item item : request.items()) {
            Inventory inventory = inventoryMap.get(item.skuCode());
            if (inventory == null) {
                throw new ResourceNotFoundException("Inventory", "skuCode", item.skuCode());
            }
            int available = inventory.availableQuantity();
            if (available < item.quantity()) {
                throw new InsufficientStockException(item.skuCode(), item.quantity(), available);
            }
        }

        List<ReserveResponse.Item> result = request.items().stream().map(item -> {
            Inventory inventory = inventoryMap.get(item.skuCode());
            inventory.setReservedQuantity(inventory.getReservedQuantity() + item.quantity());
            inventory.setUpdatedAt(Instant.now());
            inventoryRepository.save(inventory);
            MDC.put("skuCode", item.skuCode());
            log.info("Reserved {} units of {}", item.quantity(), item.skuCode());
            MDC.remove("skuCode");
            return new ReserveResponse.Item(item.skuCode(), item.quantity());
        }).toList();

        return new ReserveResponse(true, result);
    }

    @Transactional
    @CacheEvict(value = STOCK_CACHE, allEntries = true)
    public void release(ReleaseRequest request) {
        List<String> skuCodes = request.items().stream().map(ReleaseRequest.Item::skuCode).toList();
        Map<String, Inventory> inventoryMap = inventoryRepository.findAllBySkuCodeIn(skuCodes)
                .stream()
                .collect(Collectors.toMap(Inventory::getSkuCode, Function.identity()));

        for (ReleaseRequest.Item item : request.items()) {
            Inventory inventory = inventoryMap.get(item.skuCode());
            if (inventory == null) {
                log.warn("Release skipped — SKU not found: {}", item.skuCode());
                continue;
            }
            int newReserved = Math.max(0, inventory.getReservedQuantity() - item.quantity());
            inventory.setReservedQuantity(newReserved);
            inventory.setUpdatedAt(Instant.now());
            inventoryRepository.save(inventory);
            log.info("Released {} units of {}", item.quantity(), item.skuCode());
        }
    }

    @Transactional
    @CacheEvict(value = STOCK_CACHE, allEntries = true)
    public void confirm(ConfirmRequest request) {
        List<String> skuCodes = request.items().stream().map(ConfirmRequest.Item::skuCode).toList();
        Map<String, Inventory> inventoryMap = inventoryRepository.findAllBySkuCodeIn(skuCodes)
                .stream()
                .collect(Collectors.toMap(Inventory::getSkuCode, Function.identity()));

        for (ConfirmRequest.Item item : request.items()) {
            Inventory inventory = inventoryMap.get(item.skuCode());
            if (inventory == null) {
                log.warn("Confirm skipped — SKU not found: {}", item.skuCode());
                continue;
            }
            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - item.quantity()));
            inventory.setQuantity(inventory.getQuantity() - item.quantity());
            inventory.setUpdatedAt(Instant.now());
            inventoryRepository.save(inventory);
            log.info("Confirmed sale of {} units of {}", item.quantity(), item.skuCode());
        }
    }

    @Transactional
    @CacheEvict(value = STOCK_CACHE, key = "#skuCode")
    public InventoryResponse restock(String skuCode, RestockRequest request) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "skuCode", skuCode));
        inventory.setQuantity(inventory.getQuantity() + request.quantity());
        inventory.setUpdatedAt(Instant.now());
        var saved = inventoryRepository.save(inventory);
        log.info("Restocked {} units of {}", request.quantity(), skuCode);
        return InventoryResponse.from(saved);
    }
}
