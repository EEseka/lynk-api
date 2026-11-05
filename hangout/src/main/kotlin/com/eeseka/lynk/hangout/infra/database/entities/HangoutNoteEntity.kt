package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.HangoutNoteId
import jakarta.persistence.Column
import jakarta.persistence.Entity
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
    name = "hangout_notes",
    schema = "hangout_service",
    indexes = [
        Index(name = "idx_hangout_notes_hangout_id_created_at", columnList = "hangout_id, created_at DESC"),
        Index(name = "idx_hangout_notes_author_id", columnList = "author_id")
    ]
)
class HangoutNoteEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: HangoutNoteId? = null,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: HangoutUserEntity,

    @Column(nullable = false, length = 140)
    var content: String,

    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    var updatedAt: Instant? = null
)
