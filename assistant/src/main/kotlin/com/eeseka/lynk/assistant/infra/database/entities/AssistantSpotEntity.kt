package com.eeseka.lynk.assistant.infra.database.entities

import com.eeseka.lynk.spot.domain.model.PriceLevel
import com.eeseka.lynk.spot.domain.model.SpotCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "assistant_spots",
    schema = "assistant_service",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_assistant_spot_position",
            columnNames = ["message_id", "position"]
        )
    ]
)
class AssistantSpotEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    var message: AssistantMessageEntity,

    @Column(nullable = false)
    var position: Int,

    @Column(nullable = false, length = 300)
    var reason: String,

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
    var longitude: Double
)
