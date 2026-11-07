package com.quickdrop.core.domain

import com.quickdrop.core.model. ApprovalStatus
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItem
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.QuizDifficultyFilter
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.Topic
import com.quickdrop.core.model.TopicReviewState
import com.quickdrop.core.time.AppClock
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

data class SelectionCandidate(
    val item: LearningItem,
    val topic: Topic,
    val reviewState: TopicReviewState?,
    val isNewestTopicOfPrimarySubject: Boolean,
    val isPrimarySubject: Boolean,
    val lastAnsweredCorrectlyAtEpochMs: Long? = null
)

data class QuizSelectionResult(
    val selectedItems: List<LearningItem>,
    val requestedCount: Int,
    val availableEligibleCount: Int,
    val shortPoolDisclosed: Boolean,
    val topicBreakdown: Map<String, Int>
)

/**
 * Deterministic, explainable weighted quiz selector implementing Section 8 & Revision spec:
 *
 * Topic factors:
 * - U = 1 if overdue, else exp(-days_until_due / 7)
 * - R = exp(-age_days / 21)
 * - Smoothed accuracy A = (correct + 2) / (attempts + 4); weakness W = 1 - A
 * - C = student/default core weight (default 0.5)
 * - N = 1 never tested, 0.3 recently correct, 0.8 after lapse
 * - S = 0.30*U + 0.25*R + 0.25*W + 0.15*C + 0.05*N
 * - Item weight = max(0.05, S) * difficulty-fit (0.85..1.15)
 *
 * Targets:
 * - Upcoming-class mode: 60% newest topic of next subject, 25% due/weak older topics of that subject,
 *   15% other selected subjects if cross-subject review is enabled. Empty buckets renormalize.
 * - Cumulative mode: reserve >=20% due and >=20% older core where available.
 * - Cap any single topic at 40% of the quiz if >=3 topics have eligible candidates.
 * - Avoid repeating items answered correctly within the last 24 hours.
 * - Never include unapproved items or duplicate content hashes.
 */
object QuizSelector {

    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val TWENTY_FOUR_HOURS_MS = 86_400_000L

    fun computeTopicScore(
        topic: Topic,
        reviewState: TopicReviewState?,
        nowEpochMs: Long
    ): Double {
        // U: due urgency (1 when overdue, else exp(-days_until_due / 7))
        val u = when {
            reviewState?.dueAtEpochMs == null -> 0.5 // Never scheduled yet
            reviewState.dueAtEpochMs <= nowEpochMs -> 1.0
            else -> {
                val daysUntilDue = (reviewState.dueAtEpochMs - nowEpochMs) / MILLIS_PER_DAY
                exp(-daysUntilDue / 7.0)
            }
        }

        // R: recency exp(-age_days / 21)
        val ageDays = max(0.0, (nowEpochMs - topic.createdAtEpochMs) / MILLIS_PER_DAY)
        val r = exp(-ageDays / 21.0)

        // W: weakness = 1 - Laplace-smoothed accuracy (correct + 2) / (attempts + 4)
        val correct = reviewState?.correctCount ?: 0
        val attempts = reviewState?.attemptCount ?: 0
        val smoothedAccuracy = (correct + 2.0) / (attempts + 4.0)
        val w = 1.0 - smoothedAccuracy

        // C: core weight (0..1, default 0.5)
        val c = topic.coreWeight.coerceIn(0.0, 1.0)

        // N: novelty (1.0 never tested, 0.3 recently correct, 0.8 after lapse)
        val n = when {
            reviewState == null || reviewState.attemptCount == 0 -> 1.0
            reviewState.lastOutcome == ResponseOutcome.INCORRECT -> 0.8
            else -> 0.3
        }

        return 0.30 * u + 0.25 * r + 0.25 * w + 0.15 * c + 0.05 * n
    }

    fun computeDifficultyFit(
        itemDifficulty: Difficulty,
        filter: QuizDifficultyFilter
    ): Double {
        return when (filter) {
            QuizDifficultyFilter.EASY -> when (itemDifficulty) {
                Difficulty.EASY -> 1.15
                Difficulty.MEDIUM -> 0.95
                Difficulty.HARD -> 0.85
            }
            QuizDifficultyFilter.MEDIUM -> when (itemDifficulty) {
                Difficulty.MEDIUM -> 1.15
                Difficulty.EASY -> 0.95
                Difficulty.HARD -> 0.95
            }
            QuizDifficultyFilter.HARD -> when (itemDifficulty) {
                Difficulty.HARD -> 1.15
                Difficulty.MEDIUM -> 0.95
                Difficulty.EASY -> 0.85
            }
            QuizDifficultyFilter.MIXED -> 1.0
        }
    }

    fun computeItemWeight(
        candidate: SelectionCandidate,
        filter: QuizDifficultyFilter,
        nowEpochMs: Long
    ): Double {
        val s = computeTopicScore(candidate.topic, candidate.reviewState, nowEpochMs)
        val fit = computeDifficultyFit(candidate.item.difficulty, filter)
        return max(0.05, s) * fit
    }

    fun selectQuestions(
        candidates: List<SelectionCandidate>,
        scope: QuizScopeSnapshot,
        validSourceFileIds: Set<String>,
        clock: AppClock,
        random: Random = Random.Default
    ): QuizSelectionResult {
        val nowMs = clock.now().toEpochMilli()

        // 1. Filter strictly to eligible approved MCQs with valid source (or marked manual/retained),
        //    excluding items answered correctly within 24h, and deduplicating by contentHash.
        val seenHashes = mutableSetOf<String>()
        val eligible = candidates.filter { cand ->
            val item = cand.item
            val isMcqAndApproved = item.kind == LearningItemKind.MCQ &&
                item.approvalStatus == ApprovalStatus.APPROVED &&
                item.options.size >= 2 &&
                item.options.count { it.isCorrect } == 1

            val hasValidSource = item.isManualOrRetainedWithoutSource ||
                (item.sourceFileId != null && item.sourceFileId in validSourceFileIds)

            val notPaused = cand.reviewState?.paused != true

            val notRecentlyCorrect = cand.lastAnsweredCorrectlyAtEpochMs == null ||
                (nowMs - cand.lastAnsweredCorrectlyAtEpochMs) >= TWENTY_FOUR_HOURS_MS

            val scopeAllowed = when {
                scope.primarySubjectId == null -> true
                cand.isPrimarySubject -> true
                scope.includeCrossSubjectReview -> true
                else -> false
            } && (scope.includeOlderTopics || cand.isNewestTopicOfPrimarySubject)

            if (isMcqAndApproved && hasValidSource && notPaused && notRecentlyCorrect && scopeAllowed) {
                seenHashes.add(item.contentHash)
            } else {
                false
            }
        }

        val availableCount = eligible.size
        val targetCount = minOf(scope.requestedCount.coerceAtLeast(1), availableCount)
        if (targetCount == 0) {
            return QuizSelectionResult(
                selectedItems = emptyList(),
                requestedCount = scope.requestedCount,
                availableEligibleCount = 0,
                shortPoolDisclosed = scope.requestedCount > 0,
                topicBreakdown = emptyMap()
            )
        }

        val distinctTopicCount = eligible.map { it.topic.id }.distinct().size
        val maxPerTopic = if (distinctTopicCount >= 3) {
            max(1, ceil(targetCount * 0.40).toInt())
        } else {
            targetCount
        }

        val selected = mutableListOf<SelectionCandidate>()
        val topicCounts = mutableMapOf<String, Int>()

        fun canSelect(cand: SelectionCandidate): Boolean {
            if (selected.any { it.item.id == cand.item.id }) return false
            val currentTopicCount = topicCounts[cand.topic.id] ?: 0
            return currentTopicCount < maxPerTopic
        }

        fun recordSelection(cand: SelectionCandidate) {
            selected.add(cand)
            topicCounts[cand.topic.id] = (topicCounts[cand.topic.id] ?: 0) + 1
        }

        fun sampleFromBucket(bucket: List<SelectionCandidate>, countToPick: Int) {
            var picked = 0
            while (picked < countToPick && selected.size < targetCount) {
                val pool = bucket.filter { canSelect(it) }
                if (pool.isEmpty()) break
                val chosen = weightedSampleOne(pool, scope.difficultyFilter, nowMs, selected, random)
                recordSelection(chosen)
                picked++
            }
        }

        if (!scope.isCumulativeMode && scope.primarySubjectId != null) {
            // Upcoming-class targets: 60% newest topic, 25% due/weak older topics of primary subject, 15% cross-subject
            val newestBucket = eligible.filter { it.isPrimarySubject && it.isNewestTopicOfPrimarySubject }
            val olderPrimaryBucket = eligible.filter { it.isPrimarySubject && !it.isNewestTopicOfPrimarySubject }
            val crossSubjectBucket = eligible.filter { !it.isPrimarySubject }

            var wNewest = if (newestBucket.isNotEmpty()) 0.60 else 0.0
            var wOlder = if (olderPrimaryBucket.isNotEmpty()) 0.25 else 0.0
            var wCross = if (crossSubjectBucket.isNotEmpty() && scope.includeCrossSubjectReview) 0.15 else 0.0
            val sumW = wNewest + wOlder + wCross
            if (sumW > 0.0) {
                wNewest /= sumW
                wOlder /= sumW
                wCross /= sumW
            }

            val quotaNewest = ceil(targetCount * wNewest).toInt()
            val quotaOlder = ceil(targetCount * wOlder).toInt()
            val quotaCross = ceil(targetCount * wCross).toInt()

            sampleFromBucket(newestBucket, quotaNewest)
            sampleFromBucket(olderPrimaryBucket, quotaOlder)
            sampleFromBucket(crossSubjectBucket, quotaCross)
        } else {
            // Cumulative mode: reserve >=20% due and >=20% older core where available
            val dueBucket = eligible.filter {
                val due = it.reviewState?.dueAtEpochMs
                due != null && due <= nowMs
            }
            val olderCoreBucket = eligible.filter {
                !it.isNewestTopicOfPrimarySubject && it.topic.coreWeight >= 0.5
            }

            val quotaDue = ceil(targetCount * 0.20).toInt()
            val quotaOlderCore = ceil(targetCount * 0.20).toInt()

            sampleFromBucket(dueBucket, quotaDue)
            sampleFromBucket(olderCoreBucket, quotaOlderCore)
        }

        // Fill any remaining slots from the full eligible pool respecting topic cap first,
        // and if all un-capped topics are exhausted, relax cap so we still return available items.
        sampleFromBucket(eligible, targetCount - selected.size)

        if (selected.size < targetCount) {
            val remainingWithoutCap = eligible.filter { cand -> selected.none { it.item.id == cand.item.id } }
            for (cand in remainingWithoutCap) {
                if (selected.size >= targetCount) break
                recordSelection(cand)
            }
        }

        return QuizSelectionResult(
            selectedItems = selected.map { it.item },
            requestedCount = scope.requestedCount,
            availableEligibleCount = availableCount,
            shortPoolDisclosed = availableCount < scope.requestedCount,
            topicBreakdown = topicCounts.toMap()
        )
    }

    private fun weightedSampleOne(
        pool: List<SelectionCandidate>,
        filter: QuizDifficultyFilter,
        nowMs: Long,
        alreadySelected: List<SelectionCandidate>,
        random: Random
    ): SelectionCandidate {
        if (pool.size == 1) return pool.first()

        // For MIXED difficulty, nudge towards 30% easy / 50% medium / 20% hard
        val easyRatio = alreadySelected.count { it.item.difficulty == Difficulty.EASY }.toDouble() / max(1, alreadySelected.size)
        val mediumRatio = alreadySelected.count { it.item.difficulty == Difficulty.MEDIUM }.toDouble() / max(1, alreadySelected.size)
        val hardRatio = alreadySelected.count { it.item.difficulty == Difficulty.HARD }.toDouble() / max(1, alreadySelected.size)

        val weights = pool.map { cand ->
            var base = computeItemWeight(cand, filter, nowMs)
            if (filter == QuizDifficultyFilter.MIXED) {
                val mixMultiplier = when (cand.item.difficulty) {
                    Difficulty.EASY -> if (easyRatio < 0.30) 1.15 else 0.90
                    Difficulty.MEDIUM -> if (mediumRatio < 0.50) 1.15 else 0.90
                    Difficulty.HARD -> if (hardRatio < 0.20) 1.15 else 0.90
                }
                base *= mixMultiplier
            }
            max(0.01, base)
        }

        val totalWeight = weights.sum()
        var roll = random.nextDouble() * totalWeight
        for (i in pool.indices) {
            roll -= weights[i]
            if (roll <= 0.0) {
                return pool[i]
            }
        }
        return pool.last()
    }
}
