package com.eeseka.lynk.payment.api.controllers

import com.eeseka.lynk.payment.infra.paystack.PaystackSignatureVerifier
import com.eeseka.lynk.payment.infra.paystack.dto.PaystackWebhook
import com.eeseka.lynk.payment.infra.paystack.mappers.toPaymentProviderEvent
import com.eeseka.lynk.payment.service.PaymentWebhookService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@RestController
@RequestMapping("/api/payments/webhook")
class PaymentWebhookController(
    private val paystackSignatureVerifier: PaystackSignatureVerifier,
    private val paymentWebhookService: PaymentWebhookService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun onPaystackEvent(
        @RequestBody rawBody: String,
        @RequestHeader(value = "x-paystack-signature", required = false) signature: String?
    ): ResponseEntity<Unit> {
        if (!paystackSignatureVerifier.isValid(rawBody, signature)) {
            logger.warn("Rejected a webhook with an invalid signature")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val webhook = try {
            objectMapper.readValue(rawBody, PaystackWebhook::class.java)
        } catch (e: Exception) {
            logger.error("Could not read a signed Paystack webhook: {}", e.message)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

        paymentWebhookService.handle(webhook.toPaymentProviderEvent())
        return ResponseEntity.ok().build()
    }
}