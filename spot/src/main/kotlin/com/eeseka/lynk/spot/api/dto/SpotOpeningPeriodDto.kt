package com.eeseka.lynk.spot.api.dto

import java.time.DayOfWeek

data class SpotOpeningPeriodDto(
    val openDay: DayOfWeek,
    val openMinuteOfDay: Int,
    val closeDay: DayOfWeek?,
    val closeMinuteOfDay: Int?
)
