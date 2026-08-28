package com.eeseka.lynk.notification.api.dto

import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

data class DeviceTokenDto(
    val userId: UserId,
    val token: String,
    val createdAt: Instant
)
