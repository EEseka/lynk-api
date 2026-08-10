package com.eeseka.lynk.spot.api.controllers

import com.eeseka.lynk.common.api.config.UserRateLimit
import com.eeseka.lynk.common.api.util.requestUserId
import com.eeseka.lynk.spot.api.dto.PaginatedSpotsDto
import com.eeseka.lynk.spot.api.dto.SpotDto
import com.eeseka.lynk.spot.api.mappers.toPaginatedSpotsDto
import com.eeseka.lynk.spot.api.mappers.toSpotDto
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.service.SpotService
import jakarta.validation.constraints.*
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.concurrent.TimeUnit

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
        @RequestParam
        @DecimalMin("-90.0") @DecimalMax("90.0")
        latitude: Double,

        @RequestParam
        @DecimalMin("-180.0") @DecimalMax("180.0")
        longitude: Double,

        @RequestParam(required = false)
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

    @UserRateLimit(
        requests = 300,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    @GetMapping("/search")
    fun searchSpots(
        @RequestParam
        @DecimalMin("-90.0") @DecimalMax("90.0")
        latitude: Double,

        @RequestParam
        @DecimalMin("-180.0") @DecimalMax("180.0")
        longitude: Double,

        @RequestParam(required = false)
        query: String? = null,

        @RequestParam(required = false)
        category: SpotCategory? = null,

        @RequestParam(required = false)
        priceLevel: PriceLevel? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_SEARCH_RADIUS_METERS.toLong())
        radiusInMeters: Int = DEFAULT_SEARCH_RADIUS_IN_METERS,

        @RequestParam(required = false)
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
        @PathVariable spotId: String
    ): SpotDto {
        return spotService.getSpotById(
            spotId = spotId,
            userId = requestUserId
        ).toSpotDto()
    }

    @PostMapping("/{spotId}/save")
    @ResponseStatus(HttpStatus.CREATED)
    fun saveSpot(
        @PathVariable spotId: String
    ) {
        spotService.saveSpot(
            spotId = spotId,
            userId = requestUserId
        )
    }

    @DeleteMapping("/{spotId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsaveSpot(
        @PathVariable spotId: String
    ) {
        spotService.unsaveSpot(
            spotId = spotId,
            userId = requestUserId
        )
    }

    @GetMapping("/saved")
    fun getSavedSpots(
        @RequestParam(required = false)
        before: Instant? = null,

        @RequestParam(required = false)
        @Min(1) @Max(MAX_PAGE_SIZE.toLong())
        pageSize: Int = DEFAULT_PAGE_SIZE,

        @RequestParam(required = false)
        query: String? = null
    ): List<SpotDto> {
        return spotService.getSavedSpots(
            before = before,
            pageSize = pageSize,
            query = query,
            userId = requestUserId
        ).map { it.toSpotDto() }
    }
}