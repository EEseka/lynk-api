package com.eeseka.lynk.notification.infra.database.entities

import com.eeseka.lynk.common.domain.type.UserId
import com.eeseka.lynk.notification.domain.model.Platform
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
    name = "device_tokens",
    schema = "notification_service",
    indexes = [
        Index(name = "idx_device_tokens_user_id", columnList = "user_id")
    ]
)
class DeviceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(nullable = false)
    var userId: UserId,

    @Column(nullable = false, unique = true, length = 512)
    var token: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var platform: Platform,

    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)