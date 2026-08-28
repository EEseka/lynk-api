package com.eeseka.lynk.notification.domain.model

import com.eeseka.lynk.common.domain.type.HangoutId
import com.eeseka.lynk.common.domain.type.NotificationId
import java.time.Instant

data class Notification(
    val id: NotificationId,
    val type: NotificationType,
    val hangoutId: HangoutId,
    val hangoutName: String,
    val actorDisplayName: String?,
    val amountKobo: Long?,
    val isRead: Boolean,
    val createdAt: Instant
)