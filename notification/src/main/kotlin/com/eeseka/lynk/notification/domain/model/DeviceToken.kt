package com.eeseka.lynk.notification.domain.model

import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

data class DeviceToken(
    val userId: UserId,
    val token: String,
    val platform: Platform,
    val createdAt: Instant = Instant.now()
)