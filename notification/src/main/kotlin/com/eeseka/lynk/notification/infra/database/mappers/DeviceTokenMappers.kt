package com.eeseka.lynk.notification.infra.database.mappers

import com.eeseka.lynk.notification.domain.model.DeviceToken
import com.eeseka.lynk.notification.infra.database.entities.DeviceTokenEntity

fun DeviceTokenEntity.toDeviceToken(): DeviceToken {
    return DeviceToken(
        userId = userId,
        token = token,
        platform = platform,
        createdAt = createdAt
    )
}

