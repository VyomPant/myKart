package com.mykart.inventory.controller;

import com.mykart.inventory.dto.request.*;
import com.mykart.inventory.dto.response.InventoryResponse;
import com.mykart.inventory.dto.response.ReserveResponse;
import com.mykart.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Stock management")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create stock entry", security = @SecurityRequirement(name = "bearerAuth"))
    public InventoryResponse create(@Valid @RequestBody CreateInventoryRequest request) {
        return inventoryService.create(request);
    }

    @GetMapping
    @Operation(summary = "Check stock by SKU codes")
    public List<InventoryResponse> getBySkuCodes(@RequestParam List<String> skuCode) {
        return inventoryService.getBySkuCodes(skuCode);
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve stock for an order")
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Release reserved stock (order cancelled)")
    public void release(@Valid @RequestBody ReleaseRequest request) {
        inventoryService.release(request);
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm stock deduction (order confirmed)")
    public void confirm(@Valid @RequestBody ConfirmRequest request) {
        inventoryService.confirm(request);
    }

    @PutMapping("/{skuCode}")
    @Operation(summary = "Restock inventory", security = @SecurityRequirement(name = "bearerAuth"))
    public InventoryResponse restock(
            @PathVariable String skuCode,
            @Valid @RequestBody RestockRequest request) {
        return inventoryService.restock(skuCode, request);
    }
}
