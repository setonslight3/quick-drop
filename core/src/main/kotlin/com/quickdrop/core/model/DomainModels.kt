package com.quickdrop.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

@Serializable
enum class ScheduleExceptionAction {
    CANCEL, MOVE
}

@Serializable
enum class LectureStatus {
    DRAFT, READY, ARCHIVED
}

@Serializable
enum class FileJobState {
    SELECTED,
    COPYING,
    QUEUED,
    EXTRACTING,
    EXTRACTED,
    DRAFTING,
    REVIEW_READY,
    COMPLETE,
    WAITING_FOR_NETWORK,
    NEEDS_USER_ACTION,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    CANCELLED
}

@Serializable
enum class AnchorKind {
    PAGE, SLIDE, IMAGE, NOTE
}

@Serializable
enum class LearningItemKind {
    MCQ, DROP
}

@Serializable
enum class Difficulty {
    EASY, MEDIUM, HARD
}

@Serializable
enum class QuizDifficultyFilter {
    EASY, MEDIUM, HARD, MIXED
}

@Serializable
enum class ApprovalStatus {
    DRAFT, APPROVED, REJECTED
}

@Serializable
enum class ConfidenceLabel {
    HIGH, REVIEW
}

@Serializable
enum class QuizSessionStatus {
    IN_PROGRESS, COMPLETED, ABANDONED
}

@Serializable
enum class ResponseOutcome {
    CORRECT, INCORRECT, SKIPPED
}

@Serializable
enum class ReviewConfidence {
    HIGH, LOW
}

@Serializable
enum class TopicMasteryStatus {
    NEW, LEARNING, DUE, STABLE, PAUSED
}

@Serializable
enum class DropVisibilityState {
    UNSEEN, SAVED, UNDERSTOOD, REVIEW
}

@Serializable
enum class AiJobType {
    EXTRACT_AND_DRAFT,
    TEST_CONNECTION
}

@Serializable
enum class AiErrorCode {
    NONE,
    NO_KEY,
    INVALID_KEY,
    QUOTA_EXCEEDED,
    OFFLINE_OR_TIMEOUT,
    UNSUPPORTED_MODEL,
    MALFORMED_RESPONSE,
    VALIDATION_FAILED,
    PROVIDER_OUTAGE,
    CONSENT_REQUIRED
}

@Serializable
data class Subject(
    val id: String,
    val name: String,
    val colorKey: String,
    val iconKey: String,
    val term: String? = null,
    val instructor: String? = null,
    val archivedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Serializable
data class ClassSlot(
    val id: String,
    val subjectId: String,
    /**
     * Bitmask for ISO DayOfWeek (1=Monday .. 7=Sunday):
     * bit (dayOfWeek.value - 1), e.g., Monday = 1 shl 0 = 1, Sunday = 1 shl 6 = 64.
     */
    val weekdayMask: Int,
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int,   // 0..1439, must be > startMinuteOfDay
    val zoneId: String,        // IANA ZoneId, e.g. "Europe/Berlin"
    val location: String = "",
    val enabled: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderLeadMinutes: Int = 15,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L
) {
    init {
        require(startMinuteOfDay in 0..1439) { "startMinuteOfDay must be 0..1439" }
        require(endMinuteOfDay in 1..1440 && endMinuteOfDay > startMinuteOfDay) {
            "endMinuteOfDay must follow startMinuteOfDay"
        }
        require(weekdayMask in 1..127) { "weekdayMask must include at least one day (1..127)" }
    }

    fun includesDayOfWeek(isoDayOfWeek: Int): Boolean {
        require(isoDayOfWeek in 1..7)
        return (weekdayMask and (1 shl (isoDayOfWeek - 1))) != 0
    }
}

@Serializable
data class ScheduleException(
    val id: String,
    val slotId: String,
    val originalLocalDateIso: String, // YYYY-MM-DD
    val action: ScheduleExceptionAction,
    val replacementLocalDateIso: String? = null,
    val replacementStartMinuteOfDay: Int? = null,
    val replacementEndMinuteOfDay: Int? = null,
    val replacementLocation: String? = null,
    val createdAtEpochMs: Long = 0L
)

@Serializable
data class Lecture(
    val id: String,
    val subjectId: String,
    val title: String,
    val localDateIso: String, // YYYY-MM-DD
    val topicLabel: String? = null,
    val notes: String = "",
    val status: LectureStatus = LectureStatus.READY,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Serializable
data class SourceFile(
    val id: String,
    val lectureId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val privateRelativePath: String,
    val uriPermissionHeld: Boolean = true,
    val extractionState: FileJobState = FileJobState.SELECTED,
    val pageOrSlideCount: Int = 0,
    val errorCode: String? = null,
    val partialExtractionNotice: String? = null,
    val createdAtEpochMs: Long
)

@Serializable
data class SourceChunk(
    val id: String,
    val fileId: String,
    val anchorKind: AnchorKind,
    val anchorIndex: Int, // 1-based page or slide number
    val ordinal: Int,
    val extractedText: String,
    val tokenEstimate: Int,
    val contentHash: String
)

@Serializable
data class Topic(
    val id: String,
    val subjectId: String,
    val parentTopicId: String? = null,
    val name: String,
    val canonicalKey: String,
    val coreWeight: Double = 0.5, // 0.0..1.0
    val createdAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null
) {
    init {
        require(coreWeight in 0.0..1.0) { "coreWeight must be in 0.0..1.0" }
    }
}

@Serializable
data class AnswerOption(
    val id: String,
    val itemId: String,
    val ordinal: Int,
    val body: String,
    val isCorrect: Boolean
)

@Serializable
data class LearningItem(
    val id: String,
    val topicId: String,
    val lectureId: String? = null,
    val kind: LearningItemKind,
    val promptOrBody: String,
    val explanation: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val sourceFileId: String? = null,
    val sourceChunkId: String? = null,
    val anchorKind: AnchorKind? = null,
    val anchorIndex: Int? = null,
    val evidenceQuote: String = "",
    val approvalStatus: ApprovalStatus = ApprovalStatus.DRAFT,
    val isManualOrRetainedWithoutSource: Boolean = false,
    val confidenceLabel: ConfidenceLabel = ConfidenceLabel.HIGH,
    val version: Int = 1,
    val contentHash: String,
    val options: List<AnswerOption> = emptyList(),
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Serializable
data class QuizScopeSnapshot(
    val primarySubjectId: String?,
    val includeCrossSubjectReview: Boolean = false,
    val includeOlderTopics: Boolean = true,
    val isCumulativeMode: Boolean = false,
    val requestedCount: Int = 10,
    val actualEligibleCount: Int = 0,
    val difficultyFilter: QuizDifficultyFilter = QuizDifficultyFilter.MEDIUM
)

@Serializable
data class QuizSession(
    val id: String,
    val scopeSnapshot: QuizScopeSnapshot,
    val itemIdsInOrder: List<String>,
    val currentQuestionIndex: Int = 0,
    val status: QuizSessionStatus = QuizSessionStatus.IN_PROGRESS,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val correctCount: Int = 0,
    val answeredCount: Int = 0,
    val skippedCount: Int = 0
)

@Serializable
data class SnapshotOption(
    val id: String,
    val ordinal: Int,
    val body: String,
    val isCorrect: Boolean
)

@Serializable
data class QuizResponse(
    val id: String,
    val sessionId: String,
    val itemId: String,
    val itemVersion: Int,
    val topicId: String,
    val sequence: Int,
    val selectedOptionId: String?,
    val outcome: ResponseOutcome,
    val latencyMs: Long,
    val answeredAtEpochMs: Long,
    val confidence: ReviewConfidence = ReviewConfidence.HIGH,
    // Historical snapshot fields so later item edits never rewrite past quiz results
    val promptSnapshot: String,
    val explanationSnapshot: String,
    val sourceCitationSnapshot: String,
    val optionsSnapshot: List<SnapshotOption>
)

@Serializable
data class TopicReviewState(
    val topicId: String,
    val stabilityDays: Double = 1.0,
    val difficultyScore: Double = 0.5,
    val dueAtEpochMs: Long? = null,
    val lastReviewEpochMs: Long? = null,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val correctCount: Int = 0,
    val attemptCount: Int = 0,
    val lastOutcome: ResponseOutcome? = null,
    val distinctCorrectReviewDaysIso: List<String> = emptyList(),
    val hasSevenDaySpacedCorrect: Boolean = false,
    val paused: Boolean = false
)

@Serializable
data class DropState(
    val learningItemId: String,
    val visibilityState: DropVisibilityState = DropVisibilityState.UNSEEN,
    val savedAtEpochMs: Long? = null,
    val lastSeenAtEpochMs: Long? = null
)

@Serializable
data class AiJob(
    val id: String,
    val jobType: AiJobType,
    val lectureId: String? = null,
    val sourceFileIds: List<String> = emptyList(),
    val state: FileJobState,
    val attempts: Int = 0,
    val modelId: String,
    val promptVersion: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val errorCode: AiErrorCode = AiErrorCode.NONE,
    val retryAfterSeconds: Int? = null,
    val sanitizedStatusMessage: String = ""
)

@Serializable
data class UserPreferences(
    val displayName: String = "",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val weekStartIsoDay: Int = 1, // Monday
    val defaultQuizCount: Int = 10,
    val defaultDifficultyFilter: QuizDifficultyFilter = QuizDifficultyFilter.MEDIUM,
    val includeOlderTopicsByDefault: Boolean = true,
    val includeCrossSubjectByDefault: Boolean = false,
    val studyRemindersEnabled: Boolean = false,
    val classRemindersEnabled: Boolean = false,
    val defaultStudyTimeMinuteOfDay: Int = 20 * 60, // 20:00 previous evening
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartMinuteOfDay: Int = 22 * 60, // 22:00
    val quietHoursEndMinuteOfDay: Int = 7 * 60,    // 07:00 (overnight interval)
    val showStreaks: Boolean = true,
    val cloudTextSubmissionConsentGranted: Boolean = false,
    val cloudImageSubmissionConsentGranted: Boolean = false,
    val geminiModelId: String = "gemini-2.5-flash"
)
