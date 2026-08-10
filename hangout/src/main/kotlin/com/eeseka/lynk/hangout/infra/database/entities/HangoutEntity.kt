package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutStatus
import com.eeseka.lynk.hangout.domain.model.HangoutVibe
import com.eeseka.lynk.common.domain.type.UserId
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangouts",
    schema = "hangout_service",
    indexes = [
        Index(name = "idx_hangouts_host_id", columnList = "host_id"),
        Index(name = "idx_hangouts_created_at", columnList = "created_at")
    ]
)
class HangoutEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: HangoutId? = null,

    @Column(nullable = false)
    var hostId: UserId,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = true, length = 500)
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var vibe: HangoutVibe,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: HangoutStatus,

    @Column(nullable = false)
    var scheduledAt: Instant,

    @Column(nullable = true)
    var maxAttendees: Int?,

    @Column(nullable = false)
    var participantCount: Int = 0,

    @Column(nullable = true)
    var chosenSpotId: String?,

    @OneToMany(
        mappedBy = "hangout",
        fetch = FetchType.LAZY,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var participants: MutableList<HangoutParticipantEntity> = mutableListOf(),

    @Embedded
    var payment: HangoutPaymentEntity? = null,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    var updatedAt: Instant = Instant.now()
)