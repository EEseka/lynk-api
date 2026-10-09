package com.eeseka.lynk.spot.api.dto

data class SpotPriceRangeDto(
    val currencyCode: String,
    val startAmount: Long?,
    val endAmount: Long?
)
