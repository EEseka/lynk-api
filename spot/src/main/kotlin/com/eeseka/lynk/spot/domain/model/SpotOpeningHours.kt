package com.eeseka.lynk.spot.domain.model

import java.time.Instant

data class SpotOpeningHours(
    val isOpenNow: Boolean?,
    val weekdayDescriptions: List<String>, // Google's own lines, one per day, e.g. "Monday: 11:00 AM – 1:00 AM"
    val nextOpenTime: Instant?,
    val nextCloseTime: Instant?
)
