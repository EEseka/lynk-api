package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutPhotoId
import com.eeseka.lynk.hangout.domain.model.HangoutPhotoStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangout_photos",
    schema = "hangout_service",
    indexes = [
        // The partial index on PENDING rows lives only in V5; JPA cannot express it
        Index(name = "idx_hangout_photos_hangout_id_created_at", columnList = "hangout_id, created_at DESC"),
        Index(name = "idx_hangout_photos_uploader_id", columnList = "uploader_id")
    ]
)
class HangoutPhotoEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: HangoutPhotoId? = null,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    var uploader: HangoutUserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: HangoutPhotoStatus = HangoutPhotoStatus.PENDING,

    @Column(nullable = true)
    var confirmedAt: Instant? = null,

    @Column(nullable = true, length = 200)
    var caption: String? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)
