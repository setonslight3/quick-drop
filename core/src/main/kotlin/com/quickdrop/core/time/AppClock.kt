package com.quickdrop.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic clock abstraction for schedule calculations, spaced repetition due dates,
 * quiet hours, and quiz latency tracking.
 */
interface AppClock {
    fun now(): Instant
    fun zoneId(): ZoneId

    fun zonedNow(): ZonedDateTime = now().atZone(zoneId())
    fun localDate(): LocalDate = zonedNow().toLocalDate()
    fun localDateTime(): LocalDateTime = zonedNow().toLocalDateTime()
}

class SystemClock(
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zoneId(): ZoneId = zoneProvider()
}

class FakeClock(
    private var currentInstant: Instant,
    private var currentZone: ZoneId = ZoneId.of("UTC")
) : AppClock {
    override fun now(): Instant = currentInstant
    override fun zoneId(): ZoneId = currentZone

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }

    fun setZone(zoneId: ZoneId) {
        currentZone = zoneId
    }

    fun advanceSeconds(seconds: Long) {
        currentInstant = currentInstant.plusSeconds(seconds)
    }

    fun advanceDays(days: Long) {
        currentInstant = currentInstant.plusSeconds(days * 86_400L)
    }
}
