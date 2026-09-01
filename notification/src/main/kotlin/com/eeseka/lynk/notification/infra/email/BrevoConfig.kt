package com.eeseka.lynk.notification.infra.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class BrevoConfig(
    @param:Value("\${brevo.url}") private val brevoUrl: String,
    @param:Value("\${brevo.api-key}") private val apiKey: String
) {
    @Bean
    fun brevoRestClient(): RestClient {
        return RestClient.builder()
            .baseUrl(brevoUrl)
            .defaultHeader("api-key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}
