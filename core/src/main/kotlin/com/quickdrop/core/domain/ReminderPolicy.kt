package com.quickdrop.core.domain

import com.quickdrop.core.model.UserPreferences
import com.quickdrop.core.time.AppClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ReminderDecisionKind {
    DELIVER_NOW,
    DEFER_UNTIL_QUIET_HOURS_END,
    SUPPRESS_STALE_PAST_CLASS_START,
    SUPPRESS_DISABLED_OR_NO_PERMISSION,
    SUPPRESS_DUPLICATE_OCCURRENCE
}

data class ReminderEvaluation(
    val occurrenceId: String,
    val decision: ReminderDecisionKind,
    val scheduledTriggerInstant: Instant? = null,
    val deepLinkUri: String
)

/**
 * Evaluates class reminders and study-prep nudges against:
 * - Android 13+ notification permission state (denial is safe and never crashes or blocks app)
 * - Quiet hours (including overnight intervals such as 22:00 -> 07:00)
 * - Stale class rule: deferred class reminders past class start are discarded, never delivered late
 * - Deduplication by deterministic `occurrenceId` (`slotId:YYYY-MM-DD`)
 * - Deep link generation (`quickdrop://open/class?subjectId=...&occurrenceId=...`)
 */
object ReminderPolicy {

    /**
     * Checks whether [minuteOfDay] (0..1439) falls inside `[quietStartMinute, quietEndMinute)`.
     * Supports both same-day intervals (e.g. 13:00..15:00) and overnight intervals (e.g. 22:00..07:00).
     */
    fun isWithinQuietHours(
        minuteOfDay: Int,
        quietHoursEnabled: Boolean,
        quietStartMinute: Int,
        quietEndMinute: Int
    ): Boolean {
        if (!quietHoursEnabled || quietStartMinute == quietEndMinute) return false
        return if (quietStartMinute < quietEndMinute) {
            minuteOfDay in quietStartMinute until quietEndMinute
        } else {
            // Overnight interval (e.g. 22:00 [1320] to 07:00 [420])
            minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
        }
    }

    /**
     * Computes the end of the active quiet-hours window in [zoneId] for a trigger falling at [triggerZoned].
     */
    fun resolveQuietHoursEnd(
        triggerZoned: ZonedDateTime,
        quietStartMinute: Int,
        quietEndMinute: Int
    ): ZonedDateTime {
        val minuteOfDay = triggerZoned.hour * 60 + triggerZoned.minute
        val endTime = LocalTime.of(quietEndMinute / 60, quietEndMinute % 60)
        val endDate: LocalDate = if (quietStartMinute > quietEndMinute && minuteOfDay >= quietStartMinute) {
            // Trigger is in the late-evening portion (e.g. 23:00), so quiet hours end tomorrow morning
            triggerZoned.toLocalDate().plusDays(1)
        } else {
            triggerZoned.toLocalDate()
        }
        return LocalDateTime.of(endDate, endTime).atZone(triggerZoned.zone).withEarlierOffsetAtOverlap()
    }

    fun evaluateClassReminder(
        occurrence: ClassOccurrence,
        preferences: UserPreferences,
        notificationPermissionGranted: Boolean,
        alreadyDeliveredOccurrenceIds: Set<String>,
        clock: AppClock
    ): ReminderEvaluation {
        val deepLink = "quickdrop://open/class?subjectId=${occurrence.subjectId}&occurrenceId=${occurrence.occurrenceId}"

        if (!notificationPermissionGranted || !preferences.classRemindersEnabled || !occurrence.reminderEnabled) {
            return ReminderEvaluation(
                occurrenceId = occurrence.occurrenceId,
                decision = ReminderDecisionKind.SUPPRESS_DISABLED_OR_NO_PERMISSION,
                deepLinkUri = deepLink
            )
        }

        if (occurrence.occurrenceId in alreadyDeliveredOccurrenceIds) {
            return ReminderEvaluation(
                occurrenceId = occurrence.occurrenceId,
                decision = ReminderDecisionKind.SUPPRESS_DUPLICATE_OCCURRENCE,
                deepLinkUri = deepLink
            )
        }

        val now = clock.now()
        // Never fire a class reminder at or after class start
        if (!now.isBefore(occurrence.startInstant)) {
            return ReminderEvaluation(
                occurrenceId = occurrence.occurrenceId,
                decision = ReminderDecisionKind.SUPPRESS_STALE_PAST_CLASS_START,
                deepLinkUri = deepLink
            )
        }

        val idealTriggerInstant = occurrence.startInstant.minusSeconds(occurrence.reminderLeadMinutes * 60L)
        val effectiveTriggerInstant = if (idealTriggerInstant.isBefore(now)) now else idealTriggerInstant
        val triggerZoned = effectiveTriggerInstant.atZone(clock.zoneId())
        val triggerMin = triggerZoned.hour * 60 + triggerZoned.minute

        if (isWithinQuietHours(
                minuteOfDay = triggerMin,
                quietHoursEnabled = preferences.quietHoursEnabled,
                quietStartMinute = preferences.quietHoursStartMinuteOfDay,
                quietEndMinute = preferences.quietHoursEndMinuteOfDay
            )
        ) {
            val quietEndZoned = resolveQuietHoursEnd(
                triggerZoned = triggerZoned,
                quietStartMinute = preferences.quietHoursStartMinuteOfDay,
                quietEndMinute = preferences.quietHoursEndMinuteOfDay
            )
            val quietEndInstant = quietEndZoned.toInstant()
            // Per spec: "discard class reminder after class starts"
            return if (!quietEndInstant.isBefore(occurrence.startInstant)) {
                ReminderEvaluation(
                    occurrenceId = occurrence.occurrenceId,
                    decision = ReminderDecisionKind.SUPPRESS_STALE_PAST_CLASS_START,
                    deepLinkUri = deepLink
                )
            } else {
                ReminderEvaluation(
                    occurrenceId = occurrence.occurrenceId,
                    decision = ReminderDecisionKind.DEFER_UNTIL_QUIET_HOURS_END,
                    scheduledTriggerInstant = quietEndInstant,
                    deepLinkUri = deepLink
                )
            }
        }

        return ReminderEvaluation(
            occurrenceId = occurrence.occurrenceId,
            decision = ReminderDecisionKind.DELIVER_NOW,
            scheduledTriggerInstant = effectiveTriggerInstant,
            deepLinkUri = deepLink
        )
    }
}
