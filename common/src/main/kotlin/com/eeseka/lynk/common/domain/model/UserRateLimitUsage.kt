package com.eeseka.lynk.common.domain.model

data class UserRateLimitUsage(
    val used: Int,
    val resetsInSeconds: Long? // Null before the first use of the window
)
