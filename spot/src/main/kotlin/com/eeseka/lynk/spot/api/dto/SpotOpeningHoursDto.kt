package com.eeseka.lynk.spot.api.dto

import java.time.Instant

data class SpotOpeningHoursDto(
    val isOpenNow: Boolean?,
    val weekdayDescriptions: List<String>,
    val nextOpenTime: Instant?,
    val nextCloseTime: Instant?
)
