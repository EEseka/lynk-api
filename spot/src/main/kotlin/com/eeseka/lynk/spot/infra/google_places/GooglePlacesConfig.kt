package com.eeseka.lynk.spot.infra.google_places

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class GooglePlacesConfig(
    @param:Value("\${places.url}") private val placesUrl: String,
    @param:Value("\${places.api-key}") private val apiKey: String
) {
    @Bean
    fun googlePlacesRestClient(): RestClient {
        return RestClient.builder()
            .baseUrl(placesUrl)
            .defaultHeader("X-Goog-Api-Key", apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build()
    }
}