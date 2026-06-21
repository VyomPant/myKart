package com.mykart.product.service;

import com.mykart.product.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final boolean aiEnabled;

    public EmbeddingService(
            @Autowired(required = false) EmbeddingModel embeddingModel,
            @Value("${ai.enabled:false}") boolean aiEnabled,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        // Only treat AI as enabled if the flag is set AND a real API key is present
        this.aiEnabled = aiEnabled && StringUtils.hasText(apiKey);
        this.embeddingModel = this.aiEnabled ? embeddingModel : null;
    }

    public List<Double> embedProduct(Product product) {
        if (!isEnabled()) return null;

        String text = String.join(" ",
                product.getName(),
                product.getCategory(),
                product.getDescription(),
                product.getSpecs() != null ? String.join(" ", product.getSpecs().values()) : ""
        ).trim();

        try {
            float[] floats = embeddingModel.embed(text);
            List<Double> embedding = toDoubleList(floats);
            log.debug("productId={} embedded dimensions={}", product.getId(), embedding.size());
            return embedding;
        } catch (Exception ex) {
            log.error("productId={} embedding failed, storing without vector", product.getId(), ex);
            return null;
        }
    }

    public boolean isEnabled() {
        return aiEnabled && embeddingModel != null;
    }

    private static List<Double> toDoubleList(float[] floats) {
        List<Double> result = new ArrayList<>(floats.length);
        for (float f : floats) result.add((double) f);
        return result;
    }
}
