package com.mykart.payment.integration

import com.mykart.payment.entity.Payout
import com.mykart.payment.enums.FailureType
import com.mykart.payment.enums.PayoutStatus
import com.mykart.payment.repository.PayoutRepository
import com.mykart.payment.service.PayoutService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.tracing.sampling.probability=0",
        "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}"
    ]
)
@EmbeddedKafka(partitions = 1, topics = ["order.confirmed", "payout.completed"])
class PayoutServiceIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var payoutService: PayoutService

    @Autowired
    lateinit var payoutRepository: PayoutRepository

    @Autowired
    lateinit var testRestTemplate: TestRestTemplate

    @LocalServerPort
    var port: Int = 0

    @BeforeEach
    fun setUp() {
        payoutRepository.deleteAll()
    }

    @Test
    fun `createIfAbsent deducts 2 percent platform fee and saves as PENDING`() {
        val payout = payoutService.createIfAbsent(
            orderId = "order-fee-test",
            sellerId = "seller-1",
            totalAmount = BigDecimal("1000.00"),
            accountNumber = "1234567890",
            ifscCode = "HDFC0001234",
            beneficiaryName = "Test Seller"
        )

        assertEquals(BigDecimal("980.00"), payout.amount)
        assertEquals(PayoutStatus.PENDING, payout.status)

        val saved = payoutRepository.findByOrderId("order-fee-test")
        assertTrue(saved.isPresent)
        assertEquals(BigDecimal("980.00"), saved.get().amount)
    }

    @Test
    fun `createIfAbsent is idempotent for same orderId`() {
        payoutService.createIfAbsent(
            orderId = "order-idem",
            sellerId = "seller-1",
            totalAmount = BigDecimal("500.00"),
            accountNumber = "111",
            ifscCode = "SBIN0001",
            beneficiaryName = "Seller A"
        )
        payoutService.createIfAbsent(
            orderId = "order-idem",
            sellerId = "seller-1",
            totalAmount = BigDecimal("500.00"),
            accountNumber = "111",
            ifscCode = "SBIN0001",
            beneficiaryName = "Seller A"
        )

        assertEquals(1, payoutRepository.count())
    }

    @Test
    fun `retry endpoint resets FAILED TRANSIENT payout to PENDING`() {
        val payout = Payout(
            orderId = "order-retry",
            sellerId = "seller-2",
            amount = BigDecimal("490.00"),
            accountNumber = "123",
            ifscCode = "SBIN0001",
            beneficiaryName = "Seller B",
            status = PayoutStatus.FAILED,
            failureType = FailureType.TRANSIENT,
            failureMessage = "Timeout",
            retryCount = 1,
            maxRetries = 5
        )
        payoutRepository.save(payout)

        val response = testRestTemplate.postForEntity(
            "http://localhost:$port/api/payments/${payout.id}/retry",
            null,
            Map::class.java
        )

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)

        val updated = payoutRepository.findById(payout.id)
        assertTrue(updated.isPresent)
        assertEquals(PayoutStatus.PENDING, updated.get().status)
    }

    @Test
    fun `retry endpoint rejects PERMANENT failure`() {
        val payout = Payout(
            orderId = "order-perm-fail",
            sellerId = "seller-3",
            amount = BigDecimal("200.00"),
            accountNumber = "999",
            ifscCode = "INVALID",
            beneficiaryName = "Seller C",
            status = PayoutStatus.FAILED,
            failureType = FailureType.PERMANENT,
            failureMessage = "Invalid IFSC",
            retryCount = 1
        )
        payoutRepository.save(payout)

        val response = testRestTemplate.postForEntity(
            "http://localhost:$port/api/payments/${payout.id}/retry",
            null,
            Map::class.java
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `getByOrderId returns payout after creation`() {
        payoutService.createIfAbsent(
            orderId = "order-get-test",
            sellerId = "seller-4",
            totalAmount = BigDecimal("300.00"),
            accountNumber = "456",
            ifscCode = "ICIC0001",
            beneficiaryName = "Seller D"
        )

        val response = testRestTemplate.getForEntity(
            "http://localhost:$port/api/payments?orderId=order-get-test",
            Map::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("order-get-test", response.body!!["orderId"])
    }
}
