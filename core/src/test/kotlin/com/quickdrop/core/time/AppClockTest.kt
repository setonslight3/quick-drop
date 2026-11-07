package com.quickdrop.core.time

import com.quickdrop.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AppClockTest {
    @Test
    fun `fake clock advances deterministically across timezones`() {
        val start = Instant.parse("2026-11-07T12:00:00Z")
        val clock = FakeClock(start, ZoneId.of("Europe/Berlin"))

        assertEquals(LocalDate.of(2026, 11, 7), clock.localDate())
        assertEquals(13, clock.zonedNow().hour)

        clock.advanceDays(1)
        assertEquals(LocalDate.of(2026, 11, 8), clock.localDate())

        clock.setZone(ZoneId.of("America/New_York"))
        assertEquals(7, clock.zonedNow().hour)
    }
}
