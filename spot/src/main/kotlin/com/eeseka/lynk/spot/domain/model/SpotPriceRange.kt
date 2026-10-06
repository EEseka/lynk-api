package com.eeseka.lynk.spot.domain.model

data class SpotPriceRange(
    val currencyCode: String,
    val startAmount: Long?,
    val endAmount: Long?
)
