package com.eeseka.lynk.spot.service

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.exception.SpotNotFoundException
import com.eeseka.lynk.spot.domain.model.PaginatedSpots
import com.eeseka.lynk.spot.domain.model.Spot
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import com.eeseka.lynk.spot.infra.database.entities.SavedSpotEntity
import com.eeseka.lynk.spot.infra.database.mappers.toSpot
import com.eeseka.lynk.spot.infra.database.repositories.SavedSpotRepository
import com.eeseka.lynk.spot.infra.google_places.GooglePlacesClient
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SpotService(
    private val googlePlacesClient: GooglePlacesClient,
    private val savedSpotRepository: SavedSpotRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getTrendingSpots(latitude: Double, longitude: Double, limit: Int, userId: UserId): List<Spot> {
        val rawSpots = googlePlacesClient.getTrendingSpots(latitude, longitude, limit)
        return enrichWithSavedState(rawSpots, userId)
    }

    fun searchSpots(
        latitude: Double,
        longitude: Double,
        query: String?,
        category: SpotCategory?,
        priceLevel: PriceLevel?,
        radiusInMeters: Int,
        nextPageToken: String?,
        userId: UserId
    ): PaginatedSpots {
        val (rawSpots, nextPageToken) = googlePlacesClient.searchSpots(
            latitude = latitude,
            longitude = longitude,
            query = query,
            category = category,
            priceLevel = priceLevel,
            radiusInMeters = radiusInMeters,
            nextPageToken = nextPageToken
        )
        val enrichedSpots = enrichWithSavedState(rawSpots, userId)
        return PaginatedSpots(spots = enrichedSpots, nextPageToken = nextPageToken)
    }

    fun getSpotById(spotId: String, userId: UserId): Spot {
        val spot = googlePlacesClient.getSpotById(spotId) ?: throw SpotNotFoundException(spotId)
        val isSaved = savedSpotRepository.existsByUserIdAndGooglePlaceId(userId, spotId)

        // OPPORTUNISTIC UPDATE:
        // If the spot is saved, the local DB snapshot might be stale (e.g., the venue moved).
        // Since we just fetched fresh data from Google, we silently update our DB snapshot.
        if (isSaved) {
            updateStaleSnapshot(userId, spot)
        }

        return spot.copy(isSaved = isSaved)
    }

    fun saveSpot(spotId: String, userId: UserId) {
        val spot = googlePlacesClient.getSpotById(spotId) ?: throw SpotNotFoundException(spotId)

        try {
            savedSpotRepository.save(
                SavedSpotEntity(
                    userId = userId,
                    googlePlaceId = spot.id,
                    name = spot.name,
                    coverPhotoUrl = spot.photoUrls.firstOrNull(),
                    category = spot.category,
                    priceLevel = spot.priceLevel,
                    shortAddress = spot.shortAddress,
                    latitude = spot.latitude,
                    longitude = spot.longitude
                )
            )
        } catch (_: DataIntegrityViolationException) {
            /* * WHY THIS TRY-CATCH EXISTS:
             * We do not use "if(!exists) save()" because of Race Conditions. If a user taps the
             * save button twice rapidly, both threads check "exists" simultaneously, get false,
             * and try to save, crashing the app.
             * Instead, we rely on the DB's UniqueConstraint (uk_user_spot) as the bouncer.
             * If a duplicate save occurs, Postgres throws a DataIntegrityViolationException.
             * We catch it and do nothing, achieving an idempotent, thread-safe save.
             */
        }
    }

    @Transactional
    fun unsaveSpot(spotId: String, userId: UserId) {
        savedSpotRepository.deleteByUserIdAndGooglePlaceId(userId = userId, googlePlaceId = spotId)
    }

    fun getSavedSpots(
        before: Instant?,
        pageSize: Int,
        userId: UserId,
        query: String? = null
    ): List<Spot> {
        // Saved spots are used for a list screen that needs few data.
        // It saves API costs to pull locally as there is no way to pass a list of IDs
        // and make the Google API return only those items efficiently.
        val savedEntities = savedSpotRepository.findByUserIdAndCreatedAtBeforeAndNameContaining(
            userId = userId,
            before = before ?: Instant.now(),
            query = query,
            pageable = PageRequest.of(0, pageSize)
        )

        return savedEntities.content.map { it.toSpot() }
    }

    private fun enrichWithSavedState(spots: List<Spot>, userId: UserId): List<Spot> {
        if (spots.isEmpty()) return spots

        val spotIdsInView = spots.map { it.id }.toSet()
        val savedIdsInView = savedSpotRepository
            .findSavedGooglePlaceIdsByUserIdAndGooglePlaceIdIn(userId, spotIdsInView)

        return spots.map { it.copy(isSaved = it.id in savedIdsInView) }
    }

    private fun updateStaleSnapshot(userId: UserId, freshSpot: Spot) {
        try {
            savedSpotRepository.updateSnapshotData(
                userId = userId,
                googlePlaceId = freshSpot.id,
                name = freshSpot.name,
                coverPhotoUrl = freshSpot.photoUrls.firstOrNull(),
                category = freshSpot.category,
                priceLevel = freshSpot.priceLevel,
                shortAddress = freshSpot.shortAddress,
                latitude = freshSpot.latitude,
                longitude = freshSpot.longitude
            )
        } catch (e: Exception) {
            logger.warn("Failed to update stale snapshot for spot ${freshSpot.id}", e)
            // Fail silently. A failed snapshot background update shouldn't crash a user's read request.
        }
    }
}