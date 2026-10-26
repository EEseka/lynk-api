package com.eeseka.lynk.spot.api.dto

data class TrendingSpotsDto(
    val spots: List<SpotDto>,
    val areaName: String?
)
