package com.eeseka.lynk.assistant.domain

import com.eeseka.lynk.spot.domain.model.SpotOpeningHours
import com.eeseka.lynk.spot.domain.model.SpotOpeningPeriod
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpeningHoursCheckTest {

    private companion object {
        const val LAGOS_OFFSET_MINUTES = 60
    }

    @Test
    fun `is open during opening hours and closed outside them`() {
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.FRIDAY, 9 * 60, DayOfWeek.FRIDAY, 17 * 60))

        // 2026-10-16 is a Friday; 13:00 UTC is 2 PM in Lagos
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-16T13:00:00Z")))
        assertEquals(false, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-16T17:00:00Z")))
    }

    @Test
    fun `reads the time in the place time zone not UTC`() {
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.FRIDAY, 9 * 60, DayOfWeek.FRIDAY, 17 * 60))

        // 8:30 UTC is 9:30 in Lagos: already open there, though not yet 9 in UTC
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-16T08:30:00Z")))
    }

    @Test
    fun `a late night stays open past midnight`() {
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.FRIDAY, 22 * 60, DayOfWeek.SATURDAY, 2 * 60))

        // Saturday 1 AM in Lagos
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-17T00:00:00Z")))
        // Saturday 3 AM in Lagos
        assertEquals(false, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-17T02:00:00Z")))
    }

    @Test
    fun `a Sunday night that ends on Monday wraps past the end of the week`() {
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.SUNDAY, 22 * 60, DayOfWeek.MONDAY, 2 * 60))

        // Monday 1 AM in Lagos
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-19T00:00:00Z")))
        // Sunday 11 PM in Lagos
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-18T22:00:00Z")))
    }

    @Test
    fun `follows a clock change in the place instead of the offset from when it was fetched`() {
        // Fetched in summer, when London was an hour ahead of UTC
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.MONDAY, 9 * 60, DayOfWeek.MONDAY, 17 * 60))
            .copy(utcOffsetMinutes = 60, timeZoneId = "Europe/London")

        // Monday 2 November, after the clocks went back: 16:30 UTC is 4:30 PM in London, still open.
        // The summer offset alone would have said 5:30 PM and closed
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-11-02T16:30:00Z")))
    }

    @Test
    fun `a night cut off at the end of the Google week still runs into the next morning`() {
        val hours = hoursOf(
            SpotOpeningPeriod(DayOfWeek.TUESDAY, 0, DayOfWeek.TUESDAY, 2 * 60),
            SpotOpeningPeriod(DayOfWeek.MONDAY, 22 * 60, DayOfWeek.MONDAY, 23 * 60 + 59)
        )

        // Monday 11:59 PM in Lagos, the minute Google's cut-off would leave closed
        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-19T22:59:30Z")))
    }

    @Test
    fun `a place that never closes is always open`() {
        val hours = hoursOf(SpotOpeningPeriod(DayOfWeek.SUNDAY, 0, null, null))

        assertEquals(true, OpeningHoursCheck.isOpenAt(hours, Instant.parse("2026-10-14T03:00:00Z")))
    }

    @Test
    fun `says nothing when Google gave no hours or no clock`() {
        val noPeriods = hoursOf()
        val noClock = hoursOf(SpotOpeningPeriod(DayOfWeek.FRIDAY, 9 * 60, DayOfWeek.FRIDAY, 17 * 60)).copy(utcOffsetMinutes = null)

        assertNull(OpeningHoursCheck.isOpenAt(noPeriods, Instant.parse("2026-10-16T13:00:00Z")))
        assertNull(OpeningHoursCheck.isOpenAt(noClock, Instant.parse("2026-10-16T13:00:00Z")))
    }

    private fun hoursOf(vararg periods: SpotOpeningPeriod) = SpotOpeningHours(
        weekdayDescriptions = emptyList(),
        utcOffsetMinutes = LAGOS_OFFSET_MINUTES,
        timeZoneId = null,
        periods = periods.toList()
    )
}
