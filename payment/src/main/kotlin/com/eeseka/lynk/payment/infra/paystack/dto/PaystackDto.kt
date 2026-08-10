package com.eeseka.lynk.payment.infra.paystack.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackResponse<T>(
    val status: Boolean,
    val message: String?,
    val data: T?
)

// For calls where only the verdict matters.
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackOutcome(
    val status: Boolean,
    val message: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackBank(
    val name: String,
    val code: String,
    val active: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackAccount(
    @param:JsonProperty("account_number") val accountNumber: String,
    @param:JsonProperty("account_name") val accountName: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackTransferRecipient(
    @param:JsonProperty("recipient_code") val recipientCode: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackCheckout(
    @param:JsonProperty("authorization_url") val authorizationUrl: String
)

// A webhook as it arrives. "event" says what happened; "data" describes the transaction it happened to.
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackWebhook(
    val event: String,
    val data: PaystackWebhookData?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackWebhookData(
    val reference: String?,
    @param:JsonProperty("transaction_reference") val transactionReference: String? = null,
    val amount: Long?,
    val status: String?,
    @param:JsonProperty("gateway_response") val gatewayResponse: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaystackTransactionStatus(
    val status: String?,
    val amount: Long?
)