package com.eeseka.lynk.spot.domain.model

data class SpotPayment(
    val acceptsCreditCards: Boolean?,
    val acceptsDebitCards: Boolean?,
    val acceptsCashOnly: Boolean?,
    val acceptsNfc: Boolean?
)
