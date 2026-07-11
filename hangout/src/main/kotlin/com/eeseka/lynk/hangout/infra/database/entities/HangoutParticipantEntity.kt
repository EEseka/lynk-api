package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.hangout.domain.model.RsvpStatus
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
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangout_participants",
    schema = "hangout_service",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_hangout_participant",
            columnNames = ["hangout_id", "user_id"]
        )
    ],
    indexes = [
        // hangout_id covered by leftmost column of uk_hangout_participant composite unique index
        Index(name = "idx_hangout_participants_user_id", columnList = "user_id")
    ]
)
class HangoutParticipantEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hangout_id", nullable = false)
    var hangout: HangoutEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var hangoutUser: HangoutUserEntity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var rsvpStatus: RsvpStatus = RsvpStatus.PENDING,

    @Column(nullable = false)
    var hasPaid: Boolean = false,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)