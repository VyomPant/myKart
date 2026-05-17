package com.mykart.product.controller;

import com.mykart.common.dto.PagedResponse;
import com.mykart.product.dto.request.CreateProductRequest;
import com.mykart.product.dto.request.UpdateProductRequest;
import com.mykart.product.dto.response.ProductResponse;
import com.mykart.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create product", security = @SecurityRequirement(name = "bearerAuth"))
    public ProductResponse create(
            @Valid @RequestBody CreateProductRequest request,
            @RequestHeader("X-User-Id") String sellerId,
            @RequestHeader("X-User-Role") String role) {
        if (!"SELLER".equals(role)) {
            throw new IllegalArgumentException("Only SELLER role can create products");
        }
        return productService.create(request, sellerId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ProductResponse getById(@PathVariable String id) {
        return productService.getById(id);
    }

    @GetMapping
    @Operation(summary = "List products (paginated)")
    public PagedResponse<ProductResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(page, size);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by keyword")
    public List<ProductResponse> search(@RequestParam String q) {
        return productService.search(q);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", security = @SecurityRequirement(name = "bearerAuth"))
    public ProductResponse update(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request,
            @RequestHeader("X-User-Id") String sellerId,
            @RequestHeader("X-User-Role") String role) {
        if (!"SELLER".equals(role)) {
            throw new IllegalArgumentException("Only SELLER role can update products");
        }
        return productService.update(id, request, sellerId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete product", security = @SecurityRequirement(name = "bearerAuth"))
    public void delete(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String sellerId,
            @RequestHeader("X-User-Role") String role) {
        if (!"SELLER".equals(role)) {
            throw new IllegalArgumentException("Only SELLER role can delete products");
        }
        productService.delete(id, sellerId);
    }
}
