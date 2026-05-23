package com.mykart.product.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DescriptionGenerationServiceTest {

    @Mock
    private ChatClient chatClient;

    @Test
    void generateDescription_returnsContent_whenEnabled() {
        var service = new DescriptionGenerationService(chatClient, true);

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        var callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("A high-performance wireless mouse with 10m range.");

        var result = service.generateDescription(
                "Wireless Mouse", "Accessories", Map.of("Color", "Black", "Range", "10m"));

        assertEquals("A high-performance wireless mouse with 10m range.", result);
    }

    @Test
    void generateDescription_returnsNull_whenDisabled() {
        var service = new DescriptionGenerationService(null, false);

        var result = service.generateDescription("Mouse", "Accessories", Map.of());

        assertNull(result);
    }

    @Test
    void generateDescription_returnsNull_whenChatClientNull() {
        var service = new DescriptionGenerationService(null, true);

        var result = service.generateDescription("Mouse", "Accessories", Map.of());

        assertNull(result);
    }

    @Test
    void generateDescription_returnsNull_onApiException() {
        var service = new DescriptionGenerationService(chatClient, true);

        var promptSpec = mock(ChatClient.ChatClientRequestSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenThrow(new RuntimeException("API error"));

        var result = service.generateDescription("Mouse", "Accessories", Map.of());

        assertNull(result);
    }
}
