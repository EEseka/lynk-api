package com.eeseka.lynk.spot.infra.database.repositories

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.type.PriceLevel
import com.eeseka.lynk.spot.domain.type.SpotCategory
import com.eeseka.lynk.spot.infra.database.entities.SavedSpotEntity
import jakarta.transaction.Transactional
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface SavedSpotRepository : JpaRepository<SavedSpotEntity, Long> {

    fun existsByUserIdAndGooglePlaceId(userId: UserId, googlePlaceId: String): Boolean

    @Transactional
    fun deleteByUserIdAndGooglePlaceId(userId: UserId, googlePlaceId: String)

    @Query(
        """
        SELECT s.googlePlaceId
        FROM SavedSpotEntity s
        WHERE s.userId = :userId
        AND s.googlePlaceId IN :googlePlaceIds
    """)
    fun findSavedGooglePlaceIdsByUserIdAndGooglePlaceIdIn(
        userId: UserId,
        googlePlaceIds: Collection<String>
    ): Set<String>

    @Query("""
        SELECT s 
        FROM SavedSpotEntity s 
        WHERE s.userId = :userId 
        AND s.createdAt < :before 
        ORDER BY s.createdAt DESC
    """)
    fun findByUserIdBefore(
        userId: UserId,
        before: Instant,
        pageable: Pageable
    ): Slice<SavedSpotEntity>

    @Modifying
    @Transactional
    @Query(
        """
        UPDATE SavedSpotEntity s 
        SET s.name = :name, 
            s.coverPhotoUrl = :coverPhotoUrl, 
            s.category = :category, 
            s.priceLevel = :priceLevel, 
            s.shortAddress = :shortAddress, 
            s.latitude = :latitude, 
            s.longitude = :longitude 
        WHERE s.userId = :userId AND s.googlePlaceId = :googlePlaceId
    """
    )
    fun updateSnapshotData(
        userId: UserId,
        googlePlaceId: String,
        name: String,
        coverPhotoUrl: String?,
        category: SpotCategory,
        priceLevel: PriceLevel?,
        shortAddress: String?,
        latitude: Double,
        longitude: Double
    )
}