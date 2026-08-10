package com.eeseka.lynk.payment.infra.bank_logo.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BankLogo(
    val code: String?,
    val logo: String?
)