package com.eeseka.lynk.notification.infra.email.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

data class BrevoSender(
    val name: String,
    val email: String
)

data class BrevoRecipient(
    val email: String
)

data class BrevoEmailRequest(
    val sender: BrevoSender,
    val replyTo: BrevoSender,
    val to: List<BrevoRecipient>,
    val subject: String,
    val htmlContent: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BrevoEmailResponse(
    val messageId: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BrevoAccount(
    val email: String?
)