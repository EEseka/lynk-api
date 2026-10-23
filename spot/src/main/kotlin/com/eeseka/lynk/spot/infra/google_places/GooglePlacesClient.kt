package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.domain.TopSpotsRanking
import com.eeseka.lynk.spot.domain.model.BusinessStatus
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlace
import com.eeseka.lynk.spot.infra.google_places.dto.GooglePlacesSearchResponse
import com.eeseka.lynk.spot.infra.google_places.mappers.toSpot
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GooglePlacesClient(
    private val googlePlacesRestClient: RestClient
) {
    companion object {
        private const val SEARCH_RADIUS_METERS = 5_000.0 // Only a preference: a place farther out that matches the words well still comes back
        private const val TRENDING_RADIUS_METERS = 5_000.0
        private const val TRENDING_FALLBACK_RADIUS_METERS = 50_000.0 // Nothing within reach (the sea, a forest), so look much wider and take the nearest
    }

    private val placeFields = listOf(
        "id", "displayName", "primaryTypeDisplayName", "editorialSummary", "generativeSummary", "reviewSummary",
        "photos", "primaryType", "types", "priceLevel", "priceRange", "rating", "userRatingCount",
        "businessStatus", "currentOpeningHours", "utcOffsetMinutes", "timeZone",
        "goodForGroups", "reservable", "liveMusic", "outdoorSeating", "servesCocktails", "goodForWatchingSports",
        "parkingOptions", "paymentOptions",
        "formattedAddress", "shortFormattedAddress", "location",
        "internationalPhoneNumber", "websiteUri", "googleMapsUri", "googleMapsLinks"
    )

    private val listFieldMask = placeFields.joinToString(",") { "places.$it" }

    private val searchFieldMask = "$listFieldMask,nextPageToken"

    // The address parts are only needed to keep a Top 10 inside its area
    private val topSpotsFieldMask = "$listFieldMask,places.addressComponents"

    private val detailsFieldMask = placeFields.joinToString(",")

    @Cacheable(
        value = ["trending_spots"],
        key = "T(Math).round(#latitude * 100.0) / 100.0 + '_' + T(Math).round(#longitude * 100.0) / 100.0 + '_' + #limit"
    )
    fun getTrendingSpots(latitude: Double, longitude: Double, limit: Int): List<Spot> {
        return searchNearby(latitude, longitude, limit, radiusInMeters = TRENDING_RADIUS_METERS, rankByDistance = false)
            .ifEmpty { searchNearby(latitude, longitude, limit, radiusInMeters = TRENDING_FALLBACK_RADIUS_METERS, rankByDistance = true) }
    }

    @Cacheable(
        value = ["top_spots"],
        key = "#city.trim().toLowerCase()"
    )
    fun getTopSpots(city: String): List<Spot> {
        val body = mapOf(
            "textQuery" to "popular places to hang out in ${city.trim()}",
            "pageSize" to 20 // Twice what's shown, so places from elsewhere, closed or not for hanging out, can drop out and still leave ten
        )

        val response = googlePlacesRestClient.post()
            .uri("/places:searchText")
            .header("X-Goog-FieldMask", topSpotsFieldMask)
            .body(body)
            .retrieve()
            .body<GooglePlacesSearchResponse>()

        val spots = response?.places.orEmpty()
            .inArea(city)
            .mapNotNull { it.toSpot() }
            .filter { it.isListable() }
        return TopSpotsRanking.pick(spots)
    }

    fun searchSpots(
        latitude: Double?,
        longitude: Double?,
        query: String?,
        category: SpotCategory?,
        priceLevel: PriceLevel?,
        nextPageToken: String?
    ): Pair<List<Spot>, String?> {
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedNextPageToken = nextPageToken?.trim()?.takeIf { it.isNotEmpty() }

        val actualQuery = normalizedQuery ?: when (category) {
            SpotCategory.ACTIVITY -> "fun things to do, entertainment, and attractions"
            SpotCategory.LOUNGE -> "lounges and bars"
            SpotCategory.CLUB -> "night clubs"
            SpotCategory.CAFE -> "cafes and coffee shops"
            SpotCategory.RESTAURANT -> "restaurants"
            else -> "places to visit"
        }

        val body = mutableMapOf<String, Any>(
            "textQuery" to actualQuery,
            "pageSize" to 20
        )

        if (latitude != null && longitude != null) {
            body["locationBias"] = mapOf(
                "circle" to mapOf(
                    "center" to mapOf("latitude" to latitude, "longitude" to longitude),
                    "radius" to SEARCH_RADIUS_METERS
                )
            )
        }

        normalizedNextPageToken?.let { body["pageToken"] = it }

        priceLevel?.let {
            val googlePriceLevel = when (it) {
                PriceLevel.CHEAP -> "PRICE_LEVEL_INEXPENSIVE"
                PriceLevel.MODERATE -> "PRICE_LEVEL_MODERATE"
                PriceLevel.EXPENSIVE -> "PRICE_LEVEL_EXPENSIVE"
                PriceLevel.LUXURY -> "PRICE_LEVEL_VERY_EXPENSIVE"
            }
            body["priceLevels"] = listOf(googlePriceLevel)
        }

        category?.let {
            val googleType = when (it) {
                SpotCategory.LOUNGE -> "bar"
                SpotCategory.CAFE -> "cafe"
                SpotCategory.CLUB -> "night_club"
                SpotCategory.RESTAURANT -> "restaurant"
                SpotCategory.ACTIVITY -> null
                SpotCategory.OTHER -> null
            }

            if (googleType != null) {
                body["includedType"] = googleType
            }
        }

        val response = googlePlacesRestClient.post()
            .uri("/places:searchText")
            .header("X-Goog-FieldMask", searchFieldMask)
            .body(body)
            .retrieve()
            .body<GooglePlacesSearchResponse>()

        val spots = response?.places?.mapNotNull { it.toSpot() }?.filter { it.isOpenForBusiness() } ?: emptyList()
        return Pair(spots, response?.nextPageToken)
    }

    @Cacheable(
        value = ["spot_details"],
        unless = "#result == null"
    )
    fun getSpotById(placeId: String): Spot? {
        return try {
            val response = googlePlacesRestClient.get()
                .uri("/places/{placeId}", placeId)
                .header("X-Goog-FieldMask", detailsFieldMask)
                .retrieve()
                .body<GooglePlace>()

            response?.toSpot()
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }
    }

    private fun searchNearby(
        latitude: Double,
        longitude: Double,
        limit: Int,
        radiusInMeters: Double,
        rankByDistance: Boolean
    ): List<Spot> {
        val body = mutableMapOf(
            "maxResultCount" to limit,
            "includedTypes" to GooglePlaceTypes.trendingTypes,
            "locationRestriction" to mapOf(
                "circle" to mapOf(
                    "center" to mapOf("latitude" to latitude, "longitude" to longitude),
                    "radius" to radiusInMeters
                )
            )
        )

        if (rankByDistance) {
            body["rankPreference"] = "DISTANCE"
        }

        val response = googlePlacesRestClient.post()
            .uri("/places:searchNearby")
            .header("X-Goog-FieldMask", listFieldMask)
            .body(body)
            .retrieve()
            .body<GooglePlacesSearchResponse>()

        return response?.places?.mapNotNull { it.toSpot() }?.filter { it.isListable() } ?: emptyList()
    }

    // What trending and a Top 10 suggest on their own. Search skips the category check, because whoever typed "Eko Hotel" asked for it
    private fun Spot.isListable(): Boolean {
        return isOpenForBusiness() && category != SpotCategory.OTHER
    }

    // Lists never suggest a closed venue; details still return one so a saved or chosen spot can say it closed
    private fun Spot.isOpenForBusiness(): Boolean {
        return businessStatus == null || businessStatus == BusinessStatus.OPERATIONAL
    }
}