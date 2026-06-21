package com.mykart.order.integration;

import com.mykart.common.exception.InsufficientStockException;
import com.mykart.order.client.InventoryClient;
import com.mykart.order.client.dto.InventoryReserveResponse;
import com.mykart.order.enums.OrderStatus;
import com.mykart.order.repository.OrderRepository;
import com.mykart.order.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.tracing.sampling.probability=0",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
    }
)
@EmbeddedKafka(partitions = 1, topics = {"order.confirmed", "order.cancelled"})
class OrderServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean
    InventoryClient inventoryClient;

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void happyPath_orderConfirmedAndOutboxEventWritten() {
        var mockResponse = new InventoryReserveResponse(true, List.of(
                new InventoryReserveResponse.Item("SKU-001", 2)
        ));
        doReturn(mockResponse).when(inventoryClient).reserve(any());

        var response = placeOrder("seller-1", "buyer-1", "SKU-001", 2, "100.00");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        var orderId = UUID.fromString((String) response.getBody().get("id"));

        var order = orderRepository.findById(orderId);
        assertTrue(order.isPresent());
        assertEquals(OrderStatus.CONFIRMED, order.get().getStatus());

        var confirmedEvent = outboxEventRepository.findAll().stream()
                .filter(e -> "ORDER_CONFIRMED".equals(e.getEventType()))
                .findFirst();
        assertTrue(confirmedEvent.isPresent());
        assertEquals(orderId, confirmedEvent.get().getAggregateId());
    }

    @Test
    void outOfStock_returns409() {
        doThrow(new InsufficientStockException("SKU-OOS", 10, 2))
                .when(inventoryClient).reserve(any());

        var response = placeOrder("seller-1", "buyer-2", "SKU-OOS", 10, "50.00");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void outOfStock_orderIsCancelledWithOutboxEvent() {
        doThrow(new InsufficientStockException("SKU-OOS", 10, 2))
                .when(inventoryClient).reserve(any());

        placeOrder("seller-1", "buyer-3", "SKU-OOS", 10, "50.00");

        var orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        assertEquals(OrderStatus.CANCELLED, orders.get(0).getStatus());

        var cancelledEvent = outboxEventRepository.findAll().stream()
                .filter(e -> "ORDER_CANCELLED".equals(e.getEventType()))
                .findFirst();
        assertTrue(cancelledEvent.isPresent());
    }

    @Test
    void nonBuyerRole_returns400() {
        var response = placeOrderWithRole("seller-1", "SELLER", "seller-1", "SKU-001", 1, "100.00");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> placeOrder(String sellerId, String buyerId,
                                           String skuCode, int quantity, String unitPrice) {
        return placeOrderWithRole(buyerId, "BUYER", sellerId, skuCode, quantity, unitPrice);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> placeOrderWithRole(String userId, String role,
                                                    String sellerId, String skuCode,
                                                    int quantity, String unitPrice) {
        var headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-User-Role", role);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var body = Map.of(
                "sellerId", sellerId,
                "items", List.of(Map.of(
                        "skuCode", skuCode,
                        "productId", UUID.randomUUID().toString(),
                        "quantity", quantity,
                        "unitPrice", unitPrice
                ))
        );

        return testRestTemplate.exchange(
                "http://localhost:" + port + "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
    }
}
