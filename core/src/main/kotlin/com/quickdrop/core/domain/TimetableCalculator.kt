package com.quickdrop.core.domain

import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.ScheduleExceptionAction
import com.quickdrop.core.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ClassOccurrence(
    val occurrenceId: String,
    val slotId: String,
    val subjectId: String,
    val originalLocalDate: LocalDate,
    val effectiveLocalDate: LocalDate,
    val startInstant: Instant,
    val endInstant: Instant,
    val startZoned: ZonedDateTime,
    val endZoned: ZonedDateTime,
    val location: String,
    val isMoved: Boolean,
    val reminderEnabled: Boolean,
    val reminderLeadMinutes: Int
)

data class SlotOverlapWarning(
    val firstSlotId: String,
    val secondSlotId: String,
    val dayOfWeekIso: Int,
    val overlapStartMinute: Int,
    val overlapEndMinute: Int
)

/**
 * Deterministic timetable calculator handling recurring weekday masks, CANCEL/MOVE one-off
 * exceptions without mutating recurring templates, DST spring-forward gaps & fall-back overlaps,
 * and timezone changes.
 */
object TimetableCalculator {

    /**
     * Resolves a local date + minute-of-day into a [ZonedDateTime] in [zoneId].
     *
     * DST Policy:
     * - Spring-forward gap: `atZone(zoneId)` automatically shifts forward by the length of the gap
     *   (Java Time standard behavior), preserving valid wall-clock progression.
     * - Fall-back overlap: `withEarlierOffsetAtOverlap()` deterministically selects the first
     *   occurrence in the overlap so students are never late for a class during DST fall-back.
     */
    fun resolveLocalTime(
        date: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId
    ): ZonedDateTime {
        val hours = (minuteOfDay / 60).coerceIn(0, 23)
        val minutes = (minuteOfDay % 60).coerceIn(0, 59)
        val localDateTime = LocalDateTime.of(date, LocalTime.of(hours, minutes))
        return localDateTime.atZone(zoneId).withEarlierOffsetAtOverlap()
    }

    /**
     * Computes all upcoming class occurrences within `[windowStart, windowEnd]` across enabled slots,
     * applying one-off CANCEL and MOVE exceptions.
     *
     * If the student's current device timezone (`displayZone`) differs from the slot's stored `zoneId`,
     * occurrences are computed in the slot's `zoneId` and converted to `displayZone` so future
     * instances recalculate accurately after timezone changes.
     */
    fun computeOccurrences(
        slots: List<ClassSlot>,
        exceptions: List<ScheduleException>,
        fromInstant: Instant,
        daysAhead: Int = 14,
        displayZone: ZoneId
    ): List<ClassOccurrence> {
        val enabledSlots = slots.filter { it.enabled }
        if (enabledSlots.isEmpty()) return emptyList()

        val exceptionsBySlotAndDate = exceptions.associateBy { it.slotId to it.originalLocalDateIso }
        val results = mutableListOf<ClassOccurrence>()

        for (slot in enabledSlots) {
            val slotZone = runCatching { ZoneId.of(slot.zoneId) }.getOrDefault(displayZone)
            val startDateInSlotZone = fromInstant.atZone(slotZone).toLocalDate().minusDays(1)
            val endDateInSlotZone = startDateInSlotZone.plusDays(daysAhead.toLong() + 2L)

            var cursorDate = startDateInSlotZone
            while (!cursorDate.isAfter(endDateInSlotZone)) {
                val isoDay = cursorDate.dayOfWeek.value
                if (slot.includesDayOfWeek(isoDay)) {
                    val originalDateIso = cursorDate.toString()
                    val ex = exceptionsBySlotAndDate[slot.id to originalDateIso]

                    when (ex?.action) {
                        ScheduleExceptionAction.CANCEL -> {
                            // Cancelled for this specific date; recurring template remains intact
                        }
                        ScheduleExceptionAction.MOVE -> {
                            val movedDate = ex.replacementLocalDateIso?.let { LocalDate.parse(it) } ?: cursorDate
                            val movedStartMin = ex.replacementStartMinuteOfDay ?: slot.startMinuteOfDay
                            val movedEndMin = ex.replacementEndMinuteOfDay ?: slot.endMinuteOfDay
                            require(movedEndMin > movedStartMin) { "Moved end time must follow start time" }

                            val startInSlotZone = resolveLocalTime(movedDate, movedStartMin, slotZone)
                            val endInSlotZone = resolveLocalTime(movedDate, movedEndMin, slotZone)

                            if (endInSlotZone.toInstant().isAfter(fromInstant)) {
                                results.add(
                                    ClassOccurrence(
                                        occurrenceId = "${slot.id}:$originalDateIso",
                                        slotId = slot.id,
                                        subjectId = slot.subjectId,
                                        originalLocalDate = cursorDate,
                                        effectiveLocalDate = startInSlotZone.withZoneSameInstant(displayZone).toLocalDate(),
                                        startInstant = startInSlotZone.toInstant(),
                                        endInstant = endInSlotZone.toInstant(),
                                        startZoned = startInSlotZone.withZoneSameInstant(displayZone),
                                        endZoned = endInSlotZone.withZoneSameInstant(displayZone),
                                        location = ex.replacementLocation ?: slot.location,
                                        isMoved = true,
                                        reminderEnabled = slot.reminderEnabled,
                                        reminderLeadMinutes = slot.reminderLeadMinutes
                                    )
                                )
                            }
                        }
                        null -> {
                            val startInSlotZone = resolveLocalTime(cursorDate, slot.startMinuteOfDay, slotZone)
                            val endInSlotZone = resolveLocalTime(cursorDate, slot.endMinuteOfDay, slotZone)

                            if (endInSlotZone.toInstant().isAfter(fromInstant)) {
                                results.add(
                                    ClassOccurrence(
                                        occurrenceId = "${slot.id}:$originalDateIso",
                                        slotId = slot.id,
                                        subjectId = slot.subjectId,
                                        originalLocalDate = cursorDate,
                                        effectiveLocalDate = startInSlotZone.withZoneSameInstant(displayZone).toLocalDate(),
                                        startInstant = startInSlotZone.toInstant(),
                                        endInstant = endInSlotZone.toInstant(),
                                        startZoned = startInSlotZone.withZoneSameInstant(displayZone),
                                        endZoned = endInSlotZone.withZoneSameInstant(displayZone),
                                        location = slot.location,
                                        isMoved = false,
                                        reminderEnabled = slot.reminderEnabled,
                                        reminderLeadMinutes = slot.reminderLeadMinutes
                                    )
                                )
                            }
                        }
                    }
                }
                cursorDate = cursorDate.plusDays(1)
            }
        }

        return results.sortedBy { it.startInstant }
    }

    /**
     * Detects overlapping recurring slots on any shared weekday.
     * Per specification: overlap warns the student in UI but does not block saving.
     */
    fun detectOverlaps(slots: List<ClassSlot>): List<SlotOverlapWarning> {
        val enabled = slots.filter { it.enabled }
        val warnings = mutableListOf<SlotOverlapWarning>()
        for (i in enabled.indices) {
            for (j in i + 1 until enabled.size) {
                val a = enabled[i]
                val b = enabled[j]
                val sharedMask = a.weekdayMask and b.weekdayMask
                if (sharedMask != 0) {
                    val overlapStart = maxOf(a.startMinuteOfDay, b.startMinuteOfDay)
                    val overlapEnd = minOf(a.endMinuteOfDay, b.endMinuteOfDay)
                    if (overlapStart < overlapEnd) {
                        for (day in 1..7) {
                            if ((sharedMask and (1 shl (day - 1))) != 0) {
                                warnings.add(
                                    SlotOverlapWarning(
                                        firstSlotId = a.id,
                                        secondSlotId = b.id,
                                        dayOfWeekIso = day,
                                        overlapStartMinute = overlapStart,
                                        overlapEndMinute = overlapEnd
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return warnings
    }
}
