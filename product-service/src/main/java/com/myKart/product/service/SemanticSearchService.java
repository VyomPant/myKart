package com.mykart.product.service;

import com.mykart.product.entity.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final MongoTemplate mongoTemplate;

    public SemanticSearchService(
            EmbeddingService embeddingService,
            @Autowired(required = false) EmbeddingModel embeddingModel,
            MongoTemplate mongoTemplate) {
        this.embeddingService = embeddingService;
        this.embeddingModel = embeddingModel;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Product> search(String query, int limit) {
        if (!embeddingService.isEnabled() || embeddingModel == null) {
            log.info("AI search disabled, using text search query={}", query);
            return textSearch(query, limit);
        }

        try {
            float[] floats = embeddingModel.embed(query);
            List<Double> queryEmbedding = toDoubleList(floats);

            var allProducts = mongoTemplate.findAll(Product.class);
            var results = allProducts.stream()
                    .filter(p -> p.getEmbedding() != null)
                    .map(p -> new ScoredProduct(p, cosineSimilarity(queryEmbedding, p.getEmbedding())))
                    .sorted(Comparator.comparingDouble(ScoredProduct::score).reversed())
                    .limit(limit)
                    .map(ScoredProduct::product)
                    .toList();

            log.info("semantic search query={} returned={}", query, results.size());
            return results.isEmpty() ? textSearch(query, limit) : results;
        } catch (Exception ex) {
            log.error("semantic search failed, falling back to text search", ex);
            return textSearch(query, limit);
        }
    }

    private List<Product> textSearch(String query, int limit) {
        TextCriteria criteria = TextCriteria.forDefaultLanguage().matching(query);
        Query mongoQuery = new Query(criteria).limit(limit);
        return mongoTemplate.find(mongoQuery, Product.class);
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) return 0.0;

        double dotProduct = 0.0, magA = 0.0, magB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            magA += a.get(i) * a.get(i);
            magB += b.get(i) * b.get(i);
        }
        double denominator = Math.sqrt(magA) * Math.sqrt(magB);
        return denominator > 0 ? dotProduct / denominator : 0.0;
    }

    private record ScoredProduct(Product product, double score) {}

    private static List<Double> toDoubleList(float[] floats) {
        List<Double> result = new ArrayList<>(floats.length);
        for (float f : floats) result.add((double) f);
        return result;
    }
}
