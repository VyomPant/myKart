package com.mykart.order.contract;

import com.mykart.order.client.InventoryClient;
import com.mykart.order.client.dto.InventoryReserveResponse;
import com.mykart.order.dto.request.PlaceOrderRequest;
import com.mykart.order.repository.OrderRepository;
import com.mykart.order.repository.OutboxEventRepository;
import com.mykart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.tracing.sampling.probability=0",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
    }
)
@EmbeddedKafka(partitions = 1, topics = {"order.confirmed", "order.cancelled"})
public abstract class OrderContractBaseTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_contract_test")
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
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        doReturn(new InventoryReserveResponse(true, List.of(
                new InventoryReserveResponse.Item("SKU-001", 2)
        ))).when(inventoryClient).reserve(any());
    }

    public void placeOrderAndConfirm() {
        var request = new PlaceOrderRequest(
                "seller-contract",
                List.of(new PlaceOrderRequest.OrderItemRequest(
                        "SKU-001",
                        UUID.randomUUID(),
                        2,
                        BigDecimal.valueOf(100.00)
                ))
        );
        orderService.placeOrder(request, "buyer-contract");
    }
}
