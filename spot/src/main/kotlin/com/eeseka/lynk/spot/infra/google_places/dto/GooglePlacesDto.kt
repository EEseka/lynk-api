package com.eeseka.lynk.spot.infra.google_places.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePlacesSearchResponse(
    val places: List<GooglePlace>?,
    val nextPageToken: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePlace(
    val id: String,
    val displayName: GoogleText?,
    val editorialSummary: GoogleText?,
    val photos: List<GooglePhoto>?,
    val primaryType: String?,
    val types: List<String>?,
    val priceLevel: String?,
    val rating: Double?,
    val userRatingCount: Int?,
    val regularOpeningHours: GoogleOpeningHours?,
    val formattedAddress: String?,
    val shortFormattedAddress: String?,
    val location: GoogleLocation?,
    val websiteUri: String?,
    val googleMapsUri: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleText(
    val text: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePhoto(
    val name: String,
    val widthPx: Int,
    val heightPx: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleOpeningHours(
    val openNow: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleLocation(
    val latitude: Double,
    val longitude: Double
)