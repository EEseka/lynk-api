package com.eeseka.lynk.payment.infra.bank_logo

import com.eeseka.lynk.payment.infra.bank_logo.dto.BankLogo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

/**
 * Bank logos from two free community lists. Neither is complete - Paystack knows 262 institutions, and
 * between them these cover about 107, which is every bank anyone actually picks and none of the rural
 * microfinance banks that have no logo to begin with. Callers must handle a missing logo.
 *
 * Nothing here is allowed to fail the caller: a logo is decoration, and a host must still be able to
 * pick their bank when both of these hosts are down.
 */
@Component
class BankLogoClient(
    private val nigerianBanksRestClient: RestClient,
    private val supermxRestClient: RestClient,
    @param:Value("\${bank-logo.supermx-url}") private val supermxUrl: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Cacheable(
        value = ["bank_logos"],
        unless = "#result.isEmpty()"
    )
    fun getLogoUrlsByBankCode(): Map<String, String> {
        val nigerianBanksLogos = fetchLogos(
            sourceName = "nigerianbanks.xyz",
            restClient = nigerianBanksRestClient,
            path = "/"
        )
        val supermxLogos = fetchLogos(
            sourceName = "supermx1",
            restClient = supermxRestClient,
            path = "/data.json",
            baseUrl = supermxUrl
        )

        return nigerianBanksLogos + supermxLogos
    }

    private fun fetchLogos(
        sourceName: String,
        restClient: RestClient,
        path: String,
        baseUrl: String? = null
    ): Map<String, String> {
        return try {
            val logos = restClient.get()
                .uri(path)
                .retrieve()
                .body<List<BankLogo>>() ?: emptyList()

            logos.mapNotNull { logo ->
                val code = logo.code?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val url = logo.logo?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                code to if (baseUrl == null) url else "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
            }.toMap()
        } catch (e: RestClientException) {
            logger.warn("Could not fetch bank logos from {}, continuing without them: {}", sourceName, e.message)
            emptyMap()
        }
    }
}