package com.mykart.order.client;

import com.mykart.common.exception.InsufficientStockException;
import com.mykart.order.client.dto.InventoryReleaseRequest;
import com.mykart.order.client.dto.InventoryReserveRequest;
import com.mykart.order.client.dto.InventoryReserveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class InventoryClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final WebClient webClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public InventoryClient(WebClient webClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.webClient = webClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public InventoryReserveResponse reserve(InventoryReserveRequest request) {
        var cb = circuitBreakerFactory.create("inventory");
        return cb.run(
                () -> webClient.post()
                        .uri("/api/inventory/reserve")
                        .bodyValue(request)
                        .retrieve()
                        .onStatus(HttpStatusCode::is4xxClientError, response ->
                                response.bodyToMono(String.class)
                                        .flatMap(body -> Mono.error(new InsufficientStockException(body))))
                        .bodyToMono(InventoryReserveResponse.class)
                        .block(),
                throwable -> {
                    log.error("Inventory service circuit breaker open or call failed", throwable);
                    throw new InventoryServiceUnavailableException("Inventory service is unavailable");
                }
        );
    }

    public void release(InventoryReleaseRequest request) {
        var cb = circuitBreakerFactory.create("inventory");
        cb.run(
                () -> {
                    webClient.post()
                            .uri("/api/inventory/release")
                            .bodyValue(request)
                            .retrieve()
                            .toBodilessEntity()
                            .block();
                    return null;
                },
                throwable -> {
                    log.error("Failed to release inventory — will require manual intervention", throwable);
                    return null;
                }
        );
    }

    public static class InventoryServiceUnavailableException extends RuntimeException {
        public InventoryServiceUnavailableException(String message) {
            super(message);
        }
    }
}
