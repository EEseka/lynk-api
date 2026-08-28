package com.eeseka.lynk.notification.infra.database.entities

import com.eeseka.lynk.common.domain.type.UserId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "notification_users",
    schema = "notification_service"
)
class NotificationUserEntity(
    @Id
    var userId: UserId,

    @Column(nullable = false, unique = true)
    var email: String,

    @Column(nullable = false)
    var displayName: String,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)