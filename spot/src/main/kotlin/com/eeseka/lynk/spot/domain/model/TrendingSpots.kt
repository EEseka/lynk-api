package com.eeseka.lynk.spot.domain.model

data class TrendingSpots(
    val spots: List<Spot>,
    val areaName: String?
)
