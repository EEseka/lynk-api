package com.eeseka.lynk.notification.api.mappers

import com.eeseka.lynk.notification.api.dto.DeviceTokenDto
import com.eeseka.lynk.notification.api.dto.NotificationDto
import com.eeseka.lynk.notification.domain.model.DeviceToken
import com.eeseka.lynk.notification.domain.model.Notification

fun DeviceToken.toDeviceTokenDto(): DeviceTokenDto {
    return DeviceTokenDto(
        userId = userId,
        token = token,
        createdAt = createdAt
    )
}

fun Notification.toNotificationDto(): NotificationDto {
    return NotificationDto(
        id = id,
        type = type,
        hangoutId = hangoutId,
        hangoutName = hangoutName,
        actorDisplayName = actorDisplayName,
        amountKobo = amountKobo,
        isRead = isRead,
        createdAt = createdAt
    )
}
