package com.eeseka.lynk.spot.api.dto

data class PaginatedSpotsDto(
    val spots: List<SpotDto>,
    val nextPageToken: String?
)