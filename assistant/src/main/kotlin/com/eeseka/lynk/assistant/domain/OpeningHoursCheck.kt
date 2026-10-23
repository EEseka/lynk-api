package com.eeseka.lynk.assistant.domain

import com.eeseka.lynk.spot.domain.model.SpotOpeningHours
import java.time.*

/** Whether a place is open at a given moment, from its weekly periods on the place's own clock **/
object OpeningHoursCheck {
    private const val MINUTES_PER_DAY = 24 * 60
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

    // Google ends its 7-day window at 23:59, so a night that runs past it shows as closing then
    private const val LAST_MINUTE_OF_DAY = MINUTES_PER_DAY - 1

    fun isOpenAt(openingHours: SpotOpeningHours, at: Instant): Boolean? {
        val placeZone = placeZoneOf(openingHours) ?: return null
        if (openingHours.periods.isEmpty()) return null

        val placeTime = at.atZone(placeZone)
        val minuteOfWeek = minuteOfWeek(placeTime.dayOfWeek, placeTime.hour * 60 + placeTime.minute)

        return openingHours.periods.any { period ->
            val closeDay = period.closeDay ?: return@any true
            val closeMinuteOfDay = period.closeMinuteOfDay ?: return@any true

            val opensAt = minuteOfWeek(period.openDay, period.openMinuteOfDay)
            var closesAt = minuteOfWeek(closeDay, closeMinuteOfDay)
            if (closeMinuteOfDay == LAST_MINUTE_OF_DAY) closesAt++ // Counting that cut-off minute joins it to the next morning's stretch, instead of a minute closed
            if (closesAt <= opensAt) closesAt += MINUTES_PER_WEEK

            minuteOfWeek in opensAt until closesAt || minuteOfWeek + MINUTES_PER_WEEK in opensAt until closesAt
        }
    }

    private fun placeZoneOf(openingHours: SpotOpeningHours): ZoneId? {
        openingHours.timeZoneId?.let { timeZoneId ->
            try {
                return ZoneId.of(timeZoneId)
            } catch (_: DateTimeException) {
                // A zone Java doesn't know; the offset below is the next best thing
            }
        }
        return openingHours.utcOffsetMinutes?.let { ZoneOffset.ofTotalSeconds(it * 60) }
    }

    private fun minuteOfWeek(day: DayOfWeek, minuteOfDay: Int): Int = (day.value - 1) * MINUTES_PER_DAY + minuteOfDay
}
