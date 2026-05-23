package com.mykart.payment.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Payment Service API")
            .description("Seller payout disbursement — UPI / IMPS / NEFT channels")
            .version("1.0.0")
    )
}
