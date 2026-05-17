package com.mykart.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

// Phase 3: Kotlin coroutines, PostgreSQL, Kafka consumer, UPI/IMPS/NEFT payout channels
@SpringBootApplication
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}
