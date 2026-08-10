package com.eeseka.lynk.payment.infra.bank_logo

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class BankLogoConfig(
    @param:Value("\${bank-logo.nigerian-banks-url}") private val nigerianBanksUrl: String,
    @param:Value("\${bank-logo.supermx-url}") private val supermxUrl: String
) {
    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2L)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5L)
    }

    @Bean
    fun nigerianBanksRestClient(): RestClient = buildClient(nigerianBanksUrl)

    @Bean
    fun supermxRestClient(): RestClient = buildClient(supermxUrl)

    private fun buildClient(baseUrl: String): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(CONNECT_TIMEOUT)
            setReadTimeout(READ_TIMEOUT)
        }

        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}