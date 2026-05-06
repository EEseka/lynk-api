package com.eeseka.lynk.spot.domain.model

data class PaginatedSpots(
    val spots: List<Spot>,
    val nextPageToken: String?
)