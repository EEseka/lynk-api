package com.eeseka.lynk.notification.api.dto

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.NotificationId
import com.eeseka.lynk.notification.domain.model.NotificationType
import java.time.Instant

data class NotificationDto(
    val id: NotificationId,
    val type: NotificationType,
    val hangoutId: HangoutId,
    val hangoutName: String,
    val actorDisplayName: String?,
    val amountKobo: Long?,
    val isRead: Boolean,
    val createdAt: Instant
)

data class UnreadCountDto(
    val count: Long
)