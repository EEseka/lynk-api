package com.eeseka.lynk.spot.domain.model

data class SpotOpeningHours(
    val weekdayDescriptions: List<String>, // Google's own lines, one per day, e.g. "Monday: 11:00 AM – 1:00 AM"
    val utcOffsetMinutes: Int?, // The place's offset from UTC when Google answered; a clock change makes it stale, timeZoneId never goes stale
    val timeZoneId: String?, // IANA, e.g. "Europe/London"
    val periods: List<SpotOpeningPeriod>
)
