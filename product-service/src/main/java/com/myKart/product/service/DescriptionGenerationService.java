package com.mykart.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DescriptionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DescriptionGenerationService.class);

    private final ChatClient chatClient;
    private final boolean aiEnabled;

    public DescriptionGenerationService(
            @Autowired(required = false) ChatClient chatClient,
            @Value("${ai.enabled:false}") boolean aiEnabled) {
        this.chatClient = chatClient;
        this.aiEnabled = aiEnabled;
    }

    public String generateDescription(String name, String category, Map<String, String> specs) {
        if (!aiEnabled || chatClient == null) {
            return null;
        }

        String specsText = specs.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));

        String prompt = """
                You are a marketplace product description writer. Create a concise, marketing-focused product description.

                Product Name: %s
                Category: %s
                Specifications: %s

                Write a 2-3 sentence description that highlights key features and benefits. Be concise and persuasive.
                """.formatted(name, category, specsText.isEmpty() ? "none provided" : specsText);

        try {
            String description = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.info("description generated name={} category={}", name, category);
            return description;
        } catch (Exception ex) {
            log.error("description generation failed name={}", name, ex);
            return null;
        }
    }
}
