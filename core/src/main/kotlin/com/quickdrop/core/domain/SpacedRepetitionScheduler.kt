package com.quickdrop.core.domain

import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ReviewConfidence
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.TopicReviewState
import com.quickdrop.core.time.AppClock
import kotlin.math.min

/**
 * Deterministic Spaced Repetition Scheduler implementing Section 8.1:
 * - Review interval starts at 1.0 day.
 * - Correct/HIGH confidence multiplies interval by stability factor starting at 2.0, capped at 2.8.
 * - Correct/LOW confidence uses multiplier 1.4.
 * - Incorrect increments lapse; due in 10 minutes if stillStudying=true, otherwise next day (1.0 day).
 * - Interval capped at 180 days.
 * - Skip changes neither accuracy nor interval.
 * - Stable status requires 3 correct reviews on distinct local days, with at least one correct review
 *   occurring >= 7 days after a prior correct response.
 */
object SpacedRepetitionScheduler {

    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MAX_INTERVAL_DAYS = 180.0

    fun recordOutcome(
        currentState: TopicReviewState?,
        topicId: String,
        outcome: ResponseOutcome,
        confidence: ReviewConfidence = ReviewConfidence.HIGH,
        stillStudying: Boolean = true,
        clock: AppClock
    ): TopicReviewState {
        val base = currentState ?: TopicReviewState(topicId = topicId)
        if (outcome == ResponseOutcome.SKIPPED) {
            // Skip changes neither accuracy nor interval
            return base
        }

        val nowMs = clock.now().toEpochMilli()
        val todayIso = clock.localDate().toString()

        return when (outcome) {
            ResponseOutcome.CORRECT -> {
                val newRepetitions = base.repetitions + 1
                val previousIntervalDays = if (base.repetitions == 0) 1.0 else base.stabilityDays

                val nextIntervalDays = if (base.repetitions == 0) {
                    1.0
                } else {
                    val multiplier = when (confidence) {
                        ReviewConfidence.HIGH -> min(2.8, 2.0 + (base.repetitions - 1) * 0.2)
                        ReviewConfidence.LOW -> 1.4
                    }
                    min(MAX_INTERVAL_DAYS, previousIntervalDays * multiplier)
                }

                val dueAtMs = nowMs + (nextIntervalDays * MILLIS_PER_DAY).toLong()

                val priorCorrectDay = base.distinctCorrectReviewDaysIso.lastOrNull()
                val hadSevenDaySpacing = base.hasSevenDaySpacedCorrect || (
                    priorCorrectDay != null &&
                        java.time.temporal.ChronoUnit.DAYS.between(
                            java.time.LocalDate.parse(priorCorrectDay),
                            clock.localDate()
                        ) >= 7L
                    )

                val updatedDays = (base.distinctCorrectReviewDaysIso + todayIso).distinct()

                base.copy(
                    stabilityDays = nextIntervalDays,
                    dueAtEpochMs = dueAtMs,
                    lastReviewEpochMs = nowMs,
                    repetitions = newRepetitions,
                    correctCount = base.correctCount + 1,
                    attemptCount = base.attemptCount + 1,
                    lastOutcome = ResponseOutcome.CORRECT,
                    distinctCorrectReviewDaysIso = updatedDays,
                    hasSevenDaySpacedCorrect = hadSevenDaySpacing
                )
            }

            ResponseOutcome.INCORRECT -> {
                val dueAtMs = if (stillStudying) {
                    nowMs + 10L * MILLIS_PER_MINUTE
                } else {
                    nowMs + MILLIS_PER_DAY
                }

                base.copy(
                    stabilityDays = 1.0,
                    dueAtEpochMs = dueAtMs,
                    lastReviewEpochMs = nowMs,
                    repetitions = 0,
                    lapses = base.lapses + 1,
                    attemptCount = base.attemptCount + 1,
                    lastOutcome = ResponseOutcome.INCORRECT
                )
            }

            ResponseOutcome.SKIPPED -> base
        }
    }

    fun resolveMasteryStatus(
        state: TopicReviewState?,
        clock: AppClock
    ): TopicMasteryStatus {
        if (state == null || state.attemptCount == 0) return TopicMasteryStatus.NEW
        if (state.paused) return TopicMasteryStatus.PAUSED

        val nowMs = clock.now().toEpochMilli()
        if (state.dueAtEpochMs != null && state.dueAtEpochMs <= nowMs) {
            return TopicMasteryStatus.DUE
        }

        val isStable = state.distinctCorrectReviewDaysIso.size >= 3 &&
            state.hasSevenDaySpacedCorrect &&
            state.lastOutcome == ResponseOutcome.CORRECT

        return if (isStable) TopicMasteryStatus.STABLE else TopicMasteryStatus.LEARNING
    }
}
