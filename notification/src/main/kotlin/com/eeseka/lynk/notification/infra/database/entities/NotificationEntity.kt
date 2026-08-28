package com.eeseka.lynk.notification.infra.database.entities

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.NotificationId
import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.model.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "notifications",
    schema = "notification_service",
    indexes = [
        Index(name = "idx_notifications_user_id_created_at", columnList = "user_id, created_at")
    ]
)
class NotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: NotificationId? = null,

    @Column(nullable = false)
    var userId: UserId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: NotificationType,

    @Column(nullable = false)
    var hangoutId: HangoutId,

    @Column(nullable = false)
    var hangoutName: String,

    @Column(nullable = true)
    var actorDisplayName: String? = null,

    @Column(nullable = true)
    var amountKobo: Long? = null,

    @Column(nullable = false)
    var isRead: Boolean = false,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)