package com.eeseka.lynk.notification.infra.email

import com.eeseka.lynk.notification.domain.exception.EmailNotSentException
import com.eeseka.lynk.notification.infra.email.dto.BrevoAccount
import com.eeseka.lynk.notification.infra.email.dto.BrevoEmailRequest
import com.eeseka.lynk.notification.infra.email.dto.BrevoEmailResponse
import com.eeseka.lynk.notification.infra.email.dto.BrevoRecipient
import com.eeseka.lynk.notification.infra.email.dto.BrevoSender
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class BrevoEmailClient(
    private val brevoRestClient: RestClient,
    @param:Value("\${lynk.email.from}")
    private val emailFrom: String,
    @param:Value("\${lynk.email.from-name}")
    private val emailFromName: String,
    @param:Value("\${brevo.verify-on-startup}")
    private val verifyOnStartup: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun verifyConnection() {
        if (!verifyOnStartup) {
            logger.info("Skipping Brevo startup check")
            return
        }

        val account = try {
            brevoRestClient.get()
                .uri("/account")
                .retrieve()
                .body<BrevoAccount>()
        } catch (e: RestClientException) {
            throw EmailNotSentException("Could not reach Brevo to verify the API key: ${e.message}")
        }

        if (account == null) {
            throw EmailNotSentException("Brevo accepted the key but returned no account")
        }

        logger.info("Brevo reachable, sending as {}", emailFrom)
    }

    fun sendHtmlEmail(to: String, subject: String, html: String) {
        val request = BrevoEmailRequest(
            sender = BrevoSender(name = emailFromName, email = emailFrom),
            to = listOf(BrevoRecipient(email = to)),
            subject = subject,
            htmlContent = html
        )

        val response = try {
            brevoRestClient.post()
                .uri("/smtp/email")
                .body(request)
                .retrieve()
                .body<BrevoEmailResponse>()
        } catch (e: RestClientException) {
            throw EmailNotSentException("Brevo rejected \"$subject\": ${e.message}")
        }

        if (response?.messageId == null) {
            throw EmailNotSentException("Brevo accepted \"$subject\" but returned no message id")
        }
    }
}