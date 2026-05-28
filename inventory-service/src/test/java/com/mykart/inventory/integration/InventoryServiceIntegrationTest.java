package com.mykart.inventory.integration;

import com.mykart.inventory.entity.Inventory;
import com.mykart.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
class InventoryServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    InventoryRepository inventoryRepository;

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        inventoryRepository.deleteAll();
    }

    @Test
    void reserve_decrementsReservedQuantityAtomically() {
        var inventory = new Inventory(UUID.randomUUID(), "SKU-RES-001", UUID.randomUUID(), 100);
        inventoryRepository.save(inventory);

        var response = postReserve(List.of(Map.of("skuCode", "SKU-RES-001", "quantity", 10)));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("reserved"));

        var updated = inventoryRepository.findBySkuCode("SKU-RES-001");
        assertTrue(updated.isPresent());
        assertEquals(10, updated.get().getReservedQuantity());
        assertEquals(100, updated.get().getQuantity());
    }

    @Test
    void reserve_failsIfInsufficientStock() {
        var inventory = new Inventory(UUID.randomUUID(), "SKU-RES-002", UUID.randomUUID(), 5);
        inventoryRepository.save(inventory);

        var response = postReserve(List.of(Map.of("skuCode", "SKU-RES-002", "quantity", 10)));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        var unchanged = inventoryRepository.findBySkuCode("SKU-RES-002");
        assertTrue(unchanged.isPresent());
        assertEquals(0, unchanged.get().getReservedQuantity());
    }

    @Test
    void confirm_movesReservedToSold() {
        var inventory = new Inventory(UUID.randomUUID(), "SKU-CONF-001", UUID.randomUUID(), 100);
        inventory.setReservedQuantity(20);
        inventoryRepository.save(inventory);

        var response = postConfirm(List.of(Map.of("skuCode", "SKU-CONF-001", "quantity", 20)));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        var updated = inventoryRepository.findBySkuCode("SKU-CONF-001");
        assertTrue(updated.isPresent());
        assertEquals(0, updated.get().getReservedQuantity());
        assertEquals(80, updated.get().getQuantity());
    }

    @Test
    void reserve_skuNotFound_returns404() {
        var response = postReserve(List.of(Map.of("skuCode", "SKU-NONEXISTENT", "quantity", 1)));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postReserve(List<Map<String, Object>> items) {
        return post("/api/inventory/reserve", Map.of("items", items), Map.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> postConfirm(List<Map<String, Object>> items) {
        return post("/api/inventory/confirm", Map.of("items", items), Map.class);
    }

    private <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(
                "http://localhost:" + port + path,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                responseType
        );
    }
}
