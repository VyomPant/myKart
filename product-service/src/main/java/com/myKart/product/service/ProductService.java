package com.mykart.product.service;

import com.mykart.common.dto.PagedResponse;
import com.mykart.common.exception.ResourceNotFoundException;
import com.mykart.product.dto.request.CreateProductRequest;
import com.mykart.product.dto.request.UpdateProductRequest;
import com.mykart.product.dto.response.ProductResponse;
import com.mykart.product.entity.Product;
import com.mykart.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final MongoTemplate mongoTemplate;

    public ProductService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public ProductResponse create(CreateProductRequest request, String sellerId) {
        var product = new Product(
                UUID.randomUUID().toString(),
                sellerId,
                request.name(),
                request.category(),
                request.description(),
                request.price(),
                request.skuCode(),
                request.specs(),
                Instant.now(),
                Instant.now()
        );
        var saved = productRepository.save(product);
        MDC.put("productId", saved.getId());
        MDC.put("sellerId", sellerId);
        log.info("Product created: name={} skuCode={}", saved.getName(), saved.getSkuCode());
        MDC.remove("productId");
        MDC.remove("sellerId");
        return ProductResponse.from(saved);
    }

    public ProductResponse getById(String id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    public PagedResponse<ProductResponse> list(int page, int size) {
        Page<Product> result = productRepository.findAll(PageRequest.of(page, size));
        List<ProductResponse> content = result.getContent().stream()
                .map(ProductResponse::from)
                .toList();
        return new PagedResponse<>(content, page, size, result.getTotalElements(),
                result.getTotalPages(), result.isLast());
    }

    public List<ProductResponse> search(String query) {
        TextCriteria criteria = TextCriteria.forDefaultLanguage().matching(query);
        Query mongoQuery = new Query(criteria);
        return mongoTemplate.find(mongoQuery, Product.class).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse update(String id, UpdateProductRequest request, String sellerId) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (!product.getSellerId().equals(sellerId)) {
            throw new IllegalArgumentException("You do not own this product");
        }

        if (request.name() != null) product.setName(request.name());
        if (request.category() != null) product.setCategory(request.category());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.specs() != null) product.setSpecs(request.specs());
        product.setUpdatedAt(Instant.now());

        var saved = productRepository.save(product);
        log.info("Product updated: id={} sellerId={}", id, sellerId);
        return ProductResponse.from(saved);
    }

    public void delete(String id, String sellerId) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));

        if (!product.getSellerId().equals(sellerId)) {
            throw new IllegalArgumentException("You do not own this product");
        }

        productRepository.delete(product);
        log.info("Product deleted: id={} sellerId={}", id, sellerId);
    }
}
