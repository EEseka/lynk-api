package com.eeseka.lynk.spot.domain.model

enum class PriceLevel(val tier: Int) {
    CHEAP(1),
    MODERATE(2),
    EXPENSIVE(3),
    LUXURY(4)
}