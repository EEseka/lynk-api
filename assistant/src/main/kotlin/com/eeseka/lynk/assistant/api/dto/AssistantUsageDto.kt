package com.eeseka.lynk.assistant.api.dto

data class AssistantUsageDto(
    val used: Int,
    val limit: Int,
    val resetsInSeconds: Long? // Null until the first message of the day
)
