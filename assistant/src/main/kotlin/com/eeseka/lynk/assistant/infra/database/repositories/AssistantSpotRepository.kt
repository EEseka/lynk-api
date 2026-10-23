package com.eeseka.lynk.assistant.infra.database.repositories

import com.eeseka.lynk.assistant.infra.database.entities.AssistantSpotEntity
import com.eeseka.lynk.common.domain.type.AssistantMessageId
import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AssistantSpotRepository : JpaRepository<AssistantSpotEntity, Long> {
    fun findByMessageIdInOrderByPosition(messageIds: Collection<AssistantMessageId>): List<AssistantSpotEntity>

    @Modifying
    @Query(
        """
        UPDATE AssistantSpotEntity s
        SET s.name = :name,
            s.coverPhotoUrl = :coverPhotoUrl,
            s.category = :category,
            s.priceLevel = :priceLevel,
            s.shortAddress = :shortAddress,
            s.latitude = :latitude,
            s.longitude = :longitude
        WHERE s.googlePlaceId = :googlePlaceId
    """
    )
    fun updateSnapshotData(
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
