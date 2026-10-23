package com.eeseka.lynk.spot.api.dto

data class SpotOpeningHoursDto(
    val weekdayDescriptions: List<String>,
    val utcOffsetMinutes: Int?,
    val timeZoneId: String?,
    val periods: List<SpotOpeningPeriodDto>
)
