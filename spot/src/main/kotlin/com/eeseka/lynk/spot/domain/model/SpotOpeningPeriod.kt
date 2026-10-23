package com.eeseka.lynk.spot.domain.model

import java.time.DayOfWeek

data class SpotOpeningPeriod(
    val openDay: DayOfWeek,
    val openMinuteOfDay: Int,
    val closeDay: DayOfWeek?, // Null when the place never closes
    val closeMinuteOfDay: Int?
)
