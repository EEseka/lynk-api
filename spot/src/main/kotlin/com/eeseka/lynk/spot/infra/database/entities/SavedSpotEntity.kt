package com.eeseka.lynk.spot.infra.database.entities

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.spot.domain.type.PriceLevel
import com.eeseka.lynk.spot.domain.type.SpotCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "user_saved_spots",
    schema = "spot_service",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_spot",
            columnNames = ["user_id", "google_place_id"]
        )
    ],
    indexes = [
        Index(name = "idx_saved_spots_user_id", columnList = "user_id"),
        Index(name = "idx_saved_spots_user_id_created_at", columnList = "user_id, created_at DESC")
    ]
)
class SavedSpotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var userId: UserId,

    @Column(nullable = false)
    var googlePlaceId: String,

    @Column(nullable = false, length = 500)
    var name: String,

    @Column(nullable = true, length = 2048)
    var coverPhotoUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: SpotCategory,

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    var priceLevel: PriceLevel? = null,

    @Column(nullable = true, length = 1000)
    var shortAddress: String? = null,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)