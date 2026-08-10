package com.eeseka.lynk.payment.infra.paystack

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class PaystackConfig(
    @param:Value("\${paystack.url}") private val paystackUrl: String,
    @param:Value("\${paystack.secret-key}") private val secretKey: String
) {
    @Bean
    fun paystackRestClient(): RestClient {
        return RestClient.builder()
            .baseUrl(paystackUrl)
            .defaultHeader("Authorization", "Bearer $secretKey")
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}