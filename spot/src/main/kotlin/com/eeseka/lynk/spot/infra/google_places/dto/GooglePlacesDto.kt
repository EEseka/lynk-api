package com.eeseka.lynk.spot.infra.google_places.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePlacesSearchResponse(
    val places: List<GooglePlace>?,
    val nextPageToken: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePlace(
    val id: String,
    val displayName: GoogleText?,
    val primaryTypeDisplayName: GoogleText?,
    val editorialSummary: GoogleText?,
    val generativeSummary: GoogleGenerativeSummary?,
    val reviewSummary: GoogleReviewSummary?,
    val photos: List<GooglePhoto>?,
    val primaryType: String?,
    val types: List<String>?,
    val priceLevel: String?,
    val priceRange: GooglePriceRange?,
    val rating: Double?,
    val userRatingCount: Int?,
    val businessStatus: String?,
    val currentOpeningHours: GoogleOpeningHours?,
    val goodForGroups: Boolean?,
    val reservable: Boolean?,
    val liveMusic: Boolean?,
    val outdoorSeating: Boolean?,
    val servesCocktails: Boolean?,
    val goodForWatchingSports: Boolean?,
    val parkingOptions: GoogleParkingOptions?,
    val paymentOptions: GooglePaymentOptions?,
    val formattedAddress: String?,
    val shortFormattedAddress: String?,
    val location: GoogleLocation?,
    val internationalPhoneNumber: String?,
    val websiteUri: String?,
    val googleMapsUri: String?,
    val googleMapsLinks: GoogleMapsLinks?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleText(
    val text: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleGenerativeSummary(
    val overview: GoogleText?,
    val disclosureText: GoogleText?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleReviewSummary(
    val text: GoogleText?,
    val disclosureText: GoogleText?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePhoto(
    val name: String,
    val widthPx: Int,
    val heightPx: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePriceRange(
    val startPrice: GoogleMoney?,
    val endPrice: GoogleMoney?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleMoney(
    val currencyCode: String?,
    val units: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleOpeningHours(
    val openNow: Boolean?,
    val weekdayDescriptions: List<String>?,
    val nextOpenTime: Instant?,
    val nextCloseTime: Instant?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleParkingOptions(
    val freeParkingLot: Boolean?,
    val paidParkingLot: Boolean?,
    val freeStreetParking: Boolean?,
    val paidStreetParking: Boolean?,
    val valetParking: Boolean?,
    val freeGarageParking: Boolean?,
    val paidGarageParking: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GooglePaymentOptions(
    val acceptsCreditCards: Boolean?,
    val acceptsDebitCards: Boolean?,
    val acceptsCashOnly: Boolean?,
    val acceptsNfc: Boolean?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleLocation(
    val latitude: Double,
    val longitude: Double
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GoogleMapsLinks(
    val directionsUri: String?
)
