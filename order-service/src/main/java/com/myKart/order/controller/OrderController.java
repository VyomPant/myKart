package com.mykart.order.controller;

import com.mykart.common.dto.PagedResponse;
import com.mykart.order.dto.request.PlaceOrderRequest;
import com.mykart.order.dto.response.OrderResponse;
import com.mykart.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order placement and management")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order", security = @SecurityRequirement(name = "bearerAuth"))
    public OrderResponse placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader("X-User-Id") String buyerId,
            @RequestHeader("X-User-Role") String role) {
        if (!"BUYER".equals(role)) {
            throw new IllegalArgumentException("Only BUYER role can place orders");
        }
        return orderService.placeOrder(request, buyerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public OrderResponse getById(@PathVariable UUID id) {
        return orderService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List buyer orders (paginated)", security = @SecurityRequirement(name = "bearerAuth"))
    public PagedResponse<OrderResponse> listMyOrders(
            @RequestHeader("X-User-Id") String buyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.listByBuyer(buyerId, page, size);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending order", security = @SecurityRequirement(name = "bearerAuth"))
    public OrderResponse cancel(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String buyerId) {
        return orderService.cancel(id, buyerId);
    }
}
