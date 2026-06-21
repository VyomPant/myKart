package com.mykart.product.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * Creates the ChatClient bean only when ai.enabled=true and Spring AI has
     * auto-configured a ChatClient.Builder (requires a valid OPENAI_API_KEY).
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    @ConditionalOnBean(ChatClient.Builder.class)
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
