package com.eeseka.lynk.spot.domain.model

import java.time.Instant

data class Spot(
    val id: String, // The Google Place ID
    val name: String,
    val description: String?,
    val photoUrls: List<String>,

    val category: SpotCategory,
    val tags: List<String>,
    val priceLevel: PriceLevel?,

    val rating: Double?,
    val reviewCount: Int?,

    val isOpenNow: Boolean,

    val shortAddress: String?,
    val latitude: Double,
    val longitude: Double,

    val websiteUrl: String?,
    val googleMapsUrl: String?,

    val isSaved: Boolean,
    val savedAt: Instant? = null,
)