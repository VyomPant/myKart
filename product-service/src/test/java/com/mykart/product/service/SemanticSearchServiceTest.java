package com.mykart.product.service;

import com.mykart.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MongoTemplate mongoTemplate;

    private SemanticSearchService service;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(embeddingService, embeddingModel, mongoTemplate);
    }

    @Test
    void search_returnsScoredResults_whenAiEnabled() {
        float[] queryVec = {1.0f, 0.0f, 0.0f};
        var product = productWithEmbedding("Laptop", List.of(0.9, 0.1, 0.0));

        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(queryVec);
        when(mongoTemplate.findAll(Product.class)).thenReturn(List.of(product));

        var results = service.search("fast laptop", 10);

        assertEquals(1, results.size());
        assertEquals("Laptop", results.get(0).getName());
    }

    @Test
    void search_fallsBackToTextSearch_whenAiDisabled() {
        when(embeddingService.isEnabled()).thenReturn(false);
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(Collections.emptyList());

        assertTrue(service.search("laptop", 10).isEmpty());
    }

    @Test
    void search_fallsBackToTextSearch_whenEmbeddingThrows() {
        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API down"));
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(Collections.emptyList());

        assertTrue(service.search("laptop", 10).isEmpty());
    }

    @Test
    void search_fallsBackToTextSearch_whenNoProductsHaveEmbeddings() {
        float[] queryVec = {1.0f, 0.0f, 0.0f};
        var productNoVec = product("No-vector product");

        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(queryVec);
        when(mongoTemplate.findAll(Product.class)).thenReturn(List.of(productNoVec));
        when(mongoTemplate.find(any(Query.class), eq(Product.class))).thenReturn(Collections.emptyList());

        assertTrue(service.search("laptop", 10).isEmpty());
    }

    private Product productWithEmbedding(String name, List<Double> embedding) {
        var p = product(name);
        p.setEmbedding(embedding);
        return p;
    }

    private Product product(String name) {
        var p = new Product();
        p.setId("id-" + name);
        p.setName(name);
        p.setCategory("Electronics");
        p.setDescription("A product");
        p.setPrice(BigDecimal.TEN);
        p.setSkuCode("SKU-001");
        p.setSpecs(Map.of());
        return p;
    }
}
