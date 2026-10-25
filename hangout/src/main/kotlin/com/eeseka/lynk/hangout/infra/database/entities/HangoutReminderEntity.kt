package com.eeseka.lynk.hangout.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.hangout.domain.model.HangoutReminderKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "hangout_reminders",
    schema = "hangout_service",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_hangout_reminders_hangout_kind",
            columnNames = ["hangout_id", "kind"]
        )
    ]
)
class HangoutReminderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: HangoutReminderKind,

    @CreationTimestamp
    var sentAt: Instant = Instant.now()
)
