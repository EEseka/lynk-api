package com.eeseka.lynk.spot.api.controllers

import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.spot.api.dto.PaginatedSpotsDto
import com.eeseka.lynk.spot.api.dto.SpotDto
import com.eeseka.lynk.spot.api.mappers.toPaginatedSpotsDto
import com.eeseka.lynk.spot.api.mappers.toSpotDto
import com.eeseka.lynk.spot.domain.type.PriceLevel
import com.eeseka.lynk.spot.domain.type.SpotCategory
import com.eeseka.lynk.spot.service.SpotService
import jakarta.validation.constraints.*
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Validated
@RestController
@RequestMapping("/api/spots")
class SpotController(
    private val spotService: SpotService
) {

    companion object {
        private const val DEFAULT_TRENDING_LIMIT = 20
        private const val DEFAULT_PAGE_SIZE = 20
        private const val DEFAULT_SEARCH_RADIUS_IN_METERS = 5000

        private const val MAX_TRENDING_LIMIT = 20
        private const val MAX_PAGE_SIZE = 50
        private const val MAX_SEARCH_RADIUS_METERS = 50_000
    }

    @GetMapping("/trending")
    fun getTrendingSpots(
        @RequestParam("latitude")
        @DecimalMin("-90.0") @DecimalMax("90.0")
        latitude: Double,

        @RequestParam("longitude")
        @DecimalMin("-180.0") @DecimalMax("180.0")
        longitude: Double,

        @RequestParam("limit", required = false)
        @Min(1) @Max(MAX_TRENDING_LIMIT.toLong())
        limit: Int = DEFAULT_TRENDING_LIMIT
    ): List<SpotDto> {
        return spotService.getTrendingSpots(
            latitude = latitude,
            longitude = longitude,
            limit = limit,
            userId = requestUserId
        ).map { it.toSpotDto() }
    }

    @GetMapping("/search")
    fun searchSpots(
        @RequestParam("latitude")
        @DecimalMin("-90.0") @DecimalMax("90.0")
        latitude: Double,

        @RequestParam("longitude")
        @DecimalMin("-180.0") @DecimalMax("180.0")
        longitude: Double,

        @RequestParam("query", required = false)
        query: String? = null,

        @RequestParam("category", required = false)
        category: SpotCategory? = null,

        @RequestParam("priceLevel", required = false)
        priceLevel: PriceLevel? = null,

        @RequestParam("radiusInMeters", required = false)
        @Min(1) @Max(MAX_SEARCH_RADIUS_METERS.toLong())
        radiusInMeters: Int = DEFAULT_SEARCH_RADIUS_IN_METERS,

        @RequestParam("nextPageToken", required = false)
        nextPageToken: String? = null
    ): PaginatedSpotsDto {
        return spotService.searchSpots(
            latitude = latitude,
            longitude = longitude,
            query = query,
            category = category,
            priceLevel = priceLevel,
            radiusInMeters = radiusInMeters,
            nextPageToken = nextPageToken,
            userId = requestUserId
        ).toPaginatedSpotsDto()
    }

    @GetMapping("/{spotId}")
    fun getSpotById(
        @PathVariable @NotBlank spotId: String
    ): SpotDto {
        return spotService.getSpotById(
            spotId = spotId,
            userId = requestUserId
        ).toSpotDto()
    }

    @PostMapping("/{spotId}/save")
    @ResponseStatus(HttpStatus.CREATED)
    fun saveSpot(
        @PathVariable @NotBlank spotId: String
    ) {
        spotService.saveSpot(
            spotId = spotId,
            userId = requestUserId
        )
    }

    @DeleteMapping("/{spotId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsaveSpot(
        @PathVariable @NotBlank spotId: String
    ) {
        spotService.unsaveSpot(
            spotId = spotId,
            userId = requestUserId
        )
    }

    @GetMapping("/saved")
    fun getSavedSpots(
        @RequestParam("before", required = false)
        before: Instant? = null,

        @RequestParam("pageSize", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<SpotDto> {
        return spotService.getSavedSpots(
            before = before,
            pageSize = pageSize,
            userId = requestUserId,
        ).map { it.toSpotDto() }
    }
}