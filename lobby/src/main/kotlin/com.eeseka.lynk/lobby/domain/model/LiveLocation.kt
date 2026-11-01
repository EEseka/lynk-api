package com.eeseka.lynk.lobby.domain.model

import com.eeseka.lynk.common.domain.type.UserId
import java.time.Instant

data class LiveLocation(
    val userId: UserId,
    val latitude: Double,
    val longitude: Double,
    val sharedAt: Instant
)
