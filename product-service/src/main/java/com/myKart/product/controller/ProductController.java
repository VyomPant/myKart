package com.mykart.product.controller;

import com.mykart.common.dto.PagedResponse;
import com.mykart.product.dto.request.CreateProductRequest;
import com.mykart.product.dto.request.GenerateDescriptionRequest;
import com.mykart.product.dto.request.UpdateProductRequest;
import com.mykart.product.dto.response.ProductResponse;
import com.mykart.product.service.DescriptionGenerationService;
import com.mykart.product.service.ProductService;
import com.mykart.product.service.SemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product catalog with semantic search and AI descriptions")
public class ProductController {

    private final ProductService productService;
    private final SemanticSearchService semanticSearchService;
    private final DescriptionGenerationService descriptionGenerationService;

    public ProductController(ProductService productService,
                             SemanticSearchService semanticSearchService,
                             DescriptionGenerationService descriptionGenerationService) {
        this.productService = productService;
        this.semanticSearchService = semanticSearchService;
        this.descriptionGenerationService = descriptionGenerationService;
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
    @Operation(summary = "Search products — semantic (vector cosine) when AI enabled, MongoDB text search fallback")
    public List<ProductResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return semanticSearchService.search(q, limit).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @PostMapping("/generate-description")
    @Operation(summary = "Generate product description via AI (returns 501 if AI disabled)")
    public ResponseEntity<Map<String, String>> generateDescription(
            @Valid @RequestBody GenerateDescriptionRequest request) {
        String description = descriptionGenerationService.generateDescription(
                request.name(), request.category(), request.specs());

        if (description != null) {
            return ResponseEntity.ok(Map.of("description", description));
        }
        return ResponseEntity.status(501)
                .body(Map.of("message", "AI features disabled — set OPENAI_API_KEY and ai.enabled=true"));
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
