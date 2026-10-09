package com.eeseka.lynk.spot.domain.model

import java.time.Instant

data class Spot(
    val id: String, // The Google Place ID
    val name: String,
    val typeLabel: String?, // Google's own label, e.g. "Cocktail bar"
    val description: String?,
    val generativeSummary: SpotAiSummary?,
    val reviewSummary: SpotAiSummary?,
    val photoUrls: List<String>,

    val category: SpotCategory,
    val priceLevel: PriceLevel?,
    val priceRange: SpotPriceRange?,

    val rating: Double?,
    val reviewCount: Int?,

    val businessStatus: BusinessStatus?,
    val openingHours: SpotOpeningHours?,

    val amenities: SpotAmenities?,
    val parking: SpotParking?,
    val payment: SpotPayment?,

    val shortAddress: String?,
    val latitude: Double,
    val longitude: Double,

    val phoneNumber: String?,
    val websiteUrl: String?,
    val googleMapsUrl: String?,
    val directionsUrl: String?,

    val isSaved: Boolean,
    val savedAt: Instant? = null,
)
