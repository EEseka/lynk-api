package com.eeseka.lynk.spot.infra.google_places

import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.type.PriceLevel
import com.eeseka.lynk.spot.domain.type.SpotCategory
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
    private val searchFieldMask =
        "places.id,places.displayName,places.editorialSummary,places.photos,places.primaryType,places.types,places.priceLevel,places.rating,places.userRatingCount,places.regularOpeningHours,places.formattedAddress,places.shortFormattedAddress,places.location,places.websiteUri,places.googleMapsUri,nextPageToken"

    private val detailsFieldMask =
        "id,displayName,editorialSummary,photos,primaryType,types,priceLevel,rating,userRatingCount,regularOpeningHours,formattedAddress,shortFormattedAddress,location,websiteUri,googleMapsUri"

    @Cacheable(
        value = ["trending_spots"],
        key = "T(Math).round(#latitude * 100.0) / 100.0 + '_' + T(Math).round(#longitude * 100.0) / 100.0",
        sync = true
    )
    fun getTrendingSpots(latitude: Double, longitude: Double, limit: Int): List<Spot> {
        val body = mapOf(
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
                    "radius" to 5000.0
                )
            )
        )

        val response = googlePlacesRestClient.post()
            .uri("/places:searchNearby")
            .header("X-Goog-FieldMask", searchFieldMask.replace(",nextPageToken", ""))
            .body(body)
            .retrieve()
            .body<GooglePlacesSearchResponse>()

        return response?.places?.map { it.toSpot() } ?: emptyList()
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

        val spots = response?.places?.map { it.toSpot() } ?: emptyList()
        return Pair(spots, response?.nextPageToken)
    }

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
}