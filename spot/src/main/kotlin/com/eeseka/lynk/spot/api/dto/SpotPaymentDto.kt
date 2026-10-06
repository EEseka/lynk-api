package com.eeseka.lynk.spot.api.dto

data class SpotPaymentDto(
    val acceptsCreditCards: Boolean?,
    val acceptsDebitCards: Boolean?,
    val acceptsCashOnly: Boolean?,
    val acceptsNfc: Boolean?
)
