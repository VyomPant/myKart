package com.mykart.product.service;

import com.mykart.product.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void embedProduct_returnsEmbedding_whenEnabled() {
        var service = new EmbeddingService(embeddingModel, true, "sk-real-key");
        float[] vector = {0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(anyString())).thenReturn(vector);

        var result = service.embedProduct(product("Laptop", "Electronics", "Fast laptop", Map.of("RAM", "16GB")));

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(0.1, result.get(0), 1e-5);
    }

    @Test
    void embedProduct_returnsNull_whenDisabled() {
        var service = new EmbeddingService(null, false, "");
        assertNull(service.embedProduct(product("Laptop", "Electronics", "Fast laptop", Map.of())));
    }

    @Test
    void embedProduct_returnsNull_whenApiKeyBlank() {
        var service = new EmbeddingService(embeddingModel, true, "");
        assertNull(service.embedProduct(product("Laptop", "Electronics", "Fast laptop", Map.of())));
        assertFalse(service.isEnabled());
    }

    @Test
    void embedProduct_returnsNull_onException() {
        var service = new EmbeddingService(embeddingModel, true, "sk-real-key");
        when(embeddingModel.embed(anyString())).thenThrow(new RuntimeException("API error"));
        assertNull(service.embedProduct(product("Mouse", "Accessories", "Wireless mouse", Map.of())));
    }

    private Product product(String name, String category, String description, Map<String, String> specs) {
        var p = new Product();
        p.setId("test-id");
        p.setName(name);
        p.setCategory(category);
        p.setDescription(description);
        p.setPrice(BigDecimal.TEN);
        p.setSkuCode("SKU-001");
        p.setSpecs(specs);
        return p;
    }
}
