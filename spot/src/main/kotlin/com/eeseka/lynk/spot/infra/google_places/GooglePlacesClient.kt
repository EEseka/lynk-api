package com.eeseka.lynk.spot.infra.google_places

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
        private const val TOP_SPOTS_COUNT = 10
    }

    private val placeFields = listOf(
        "id", "displayName", "primaryTypeDisplayName", "editorialSummary", "generativeSummary", "reviewSummary",
        "photos", "primaryType", "types", "priceLevel", "priceRange", "rating", "userRatingCount",
        "businessStatus", "currentOpeningHours",
        "goodForGroups", "reservable", "liveMusic", "outdoorSeating", "servesCocktails", "goodForWatchingSports",
        "parkingOptions", "paymentOptions",
        "formattedAddress", "shortFormattedAddress", "location",
        "internationalPhoneNumber", "websiteUri", "googleMapsUri", "googleMapsLinks"
    )

    private val listFieldMask = placeFields.joinToString(",") { "places.$it" }

    private val searchFieldMask = "$listFieldMask,nextPageToken"

    private val detailsFieldMask = placeFields.joinToString(",")

    @Cacheable(
        value = ["trending_spots"],
        key = "T(Math).round(#latitude * 100.0) / 100.0 + '_' + T(Math).round(#longitude * 100.0) / 100.0 + '_' + #limit"
    )
    fun getTrendingSpots(latitude: Double, longitude: Double, limit: Int): List<Spot> {
        return searchNearby(latitude, longitude, limit, radiusInMeters = 5000.0, rankByDistance = false)
            .ifEmpty { searchNearby(latitude, longitude, limit, radiusInMeters = 50000.0, rankByDistance = true) }
    }

    @Cacheable(
        value = ["top_spots"],
        key = "#city.trim().toLowerCase()"
    )
    fun getTopSpots(city: String): List<Spot> {
        val body = mapOf(
            "textQuery" to "popular places to hang out in ${city.trim()}",
            "pageSize" to 20 // Twice what's shown, so closed places can drop out and still leave ten
        )

        val response = googlePlacesRestClient.post()
            .uri("/places:searchText")
            .header("X-Goog-FieldMask", listFieldMask)
            .body(body)
            .retrieve()
            .body<GooglePlacesSearchResponse>()

        return response?.places
            ?.mapNotNull { it.toSpot() }
            ?.filter { it.isOpenForBusiness() }
            ?.take(TOP_SPOTS_COUNT)
            ?: emptyList()
    }

    fun searchSpots(
        latitude: Double,
        longitude: Double,
        query: String?,
        category: SpotCategory?,
        priceLevel: PriceLevel?,
        radiusInMeters: Int,
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

        val body = mutableMapOf(
            "textQuery" to actualQuery,
            "pageSize" to 20,
            "locationBias" to mapOf(
                "circle" to mapOf(
                    "center" to mapOf("latitude" to latitude, "longitude" to longitude),
                    "radius" to radiusInMeters.toDouble()
                )
            )
        )

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
            "includedTypes" to listOf(
                "restaurant",
                "cafe",
                "bar",
                "night_club",
                "tourist_attraction",
                "park",
                "movie_theater",
                "bowling_alley",
                "amusement_center",
                "art_gallery",
                "shopping_mall"
            ),
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

        return response?.places?.mapNotNull { it.toSpot() }?.filter { it.isOpenForBusiness() } ?: emptyList()
    }

    // Lists never suggest a closed venue; details still return one so a saved or chosen spot can say it closed
    private fun Spot.isOpenForBusiness(): Boolean {
        return businessStatus == null || businessStatus == BusinessStatus.OPERATIONAL
    }
}