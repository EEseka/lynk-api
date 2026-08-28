package com.eeseka.lynk.notification.infra.database.mappers

import com.eeseka.lynk.notification.domain.model.Notification
import com.eeseka.lynk.notification.infra.database.entities.NotificationEntity

fun NotificationEntity.toNotification(): Notification {
    return Notification(
        id = id!!,
        type = type,
        hangoutId = hangoutId,
        hangoutName = hangoutName,
        actorDisplayName = actorDisplayName,
        amountKobo = amountKobo,
        isRead = isRead,
        createdAt = createdAt
    )
}
