package com.quickdrop.core.repository

import com.quickdrop.core.domain.ClassOccurrence
import com.quickdrop.core.domain.DraftValidator
import com.quickdrop.core.domain.ExtractionOutcome
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.domain.FileIntakeStatus
import com.quickdrop.core.domain.GeminiClient
import com.quickdrop.core.domain.GeminiPipeline
import com.quickdrop.core.domain.GeminiResponse
import com.quickdrop.core.domain.QuizSelectionResult
import com.quickdrop.core.domain.QuizSelector
import com.quickdrop.core.domain.SelectionCandidate
import com.quickdrop.core.domain.SlotOverlapWarning
import com.quickdrop.core.domain.SpacedRepetitionScheduler
import com.quickdrop.core.domain.TimetableCalculator
import com.quickdrop.core.model.AiErrorCode
import com.quickdrop.core.model.AiJob
import com.quickdrop.core.model.AiJobType
import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.AnswerOption
import com.quickdrop.core.model.ApprovalStatus
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.ConfidenceLabel
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.DropState
import com.quickdrop.core.model.DropVisibilityState
import com.quickdrop.core.model.FileJobState
import com.quickdrop.core.model.LearningItem
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.Lecture
import com.quickdrop.core.model.LectureStatus
import com.quickdrop.core.model.QuizResponse
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.QuizSession
import com.quickdrop.core.model.QuizSessionStatus
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ReviewConfidence
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.SnapshotOption
import com.quickdrop.core.model.SourceChunk
import com.quickdrop.core.model.SourceFile
import com.quickdrop.core.model.Subject
import com.quickdrop.core.model.Topic
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.TopicReviewState
import com.quickdrop.core.model.UserPreferences
import com.quickdrop.core.time.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

interface SubjectRepository {
    fun observeActiveSubjects(): Flow<List<Subject>>
    fun observeAllSubjects(): Flow<List<Subject>>
    suspend fun createOrUpdateSubject(
        id: String? = null,
        name: String,
        colorKey: String,
        iconKey: String,
        term: String? = null,
        instructor: String? = null
    ): Subject
    suspend fun archiveSubject(subjectId: String)
}

interface TimetableRepository {
    fun observeSlots(): Flow<List<ClassSlot>>
    fun observeExceptions(): Flow<List<ScheduleException>>
    suspend fun upsertClassSlot(slot: ClassSlot): Pair<ClassSlot, List<SlotOverlapWarning>>
    suspend fun deleteClassSlot(slotId: String)
    suspend fun upsertException(exception: ScheduleException): ScheduleException
    suspend fun removeException(exceptionId: String)
    suspend fun getUpcomingOccurrences(daysAhead: Int = 14): List<ClassOccurrence>
}

interface LectureRepository {
    fun observeLecturesForSubject(subjectId: String): Flow<List<Lecture>>
    fun observeSourceFilesForLecture(lectureId: String): Flow<List<SourceFile>>
    fun observeChunksForFile(fileId: String): Flow<List<SourceChunk>>
    suspend fun createOrUpdateLecture(
        id: String? = null,
        subjectId: String,
        title: String,
        localDateIso: String,
        topicLabel: String? = null,
        notes: String = ""
    ): Lecture
    suspend fun attachAndExtractFile(
        lectureId: String,
        displayName: String,
        bytes: ByteArray,
        privateRelativePath: String,
        ocrFallbackText: String? = null
    ): Pair<SourceFile, ExtractionOutcome>
    suspend fun removeSourceFile(
        fileId: String,
        deleteGeneratedItems: Boolean,
        retainApprovedItemsWithoutSource: Boolean
    )
    suspend fun deleteLecture(
        lectureId: String,
        deleteGeneratedItems: Boolean,
        retainApprovedItemsWithoutSource: Boolean
    )
}

interface StudyRepository {
    fun observeTopicsForSubject(subjectId: String): Flow<List<Topic>>
    fun observeLearningItemsForLecture(lectureId: String): Flow<List<LearningItem>>
    fun observeDraftItems(): Flow<List<LearningItem>>
    fun observeApprovedDrops(): Flow<List<Pair<LearningItem, DropState>>>
    fun observeAiJobs(): Flow<List<AiJob>>
    suspend fun createOrGetTopic(subjectId: String, name: String, coreWeight: Double = 0.5): Topic
    suspend fun createManualItem(
        subjectId: String,
        topicName: String,
        lectureId: String? = null,
        kind: LearningItemKind,
        promptOrBody: String,
        explanation: String,
        difficulty: Difficulty,
        options: List<Pair<String, Boolean>> = emptyList()
    ): Result<LearningItem>
    suspend fun generateDraftsWithAi(
        lectureId: String,
        apiKey: String,
        client: GeminiClient
    ): AiJob
    suspend fun editAndRevalidateItem(
        itemId: String,
        newPromptOrBody: String,
        newExplanation: String,
        newDifficulty: Difficulty,
        newOptions: List<Pair<String, Boolean>>
    ): Result<LearningItem>
    suspend fun approveItem(itemId: String): Result<LearningItem>
    suspend fun rejectItem(itemId: String)
    suspend fun updateDropState(itemId: String, visibilityState: DropVisibilityState)
}

interface QuizRepository {
    fun observeActiveSession(): Flow<QuizSession?>
    fun observeSessionById(sessionId: String): Flow<QuizSession?>
    fun observeResponsesForSession(sessionId: String): Flow<List<QuizResponse>>
    suspend fun startNewQuizSession(
        scope: QuizScopeSnapshot,
        random: Random = Random.Default
    ): Pair<QuizSession?, QuizSelectionResult>
    suspend fun recordAnswer(
        sessionId: String,
        itemId: String,
        selectedOptionId: String?,
        latencyMs: Long,
        confidence: ReviewConfidence = ReviewConfidence.HIGH
    ): QuizResponse
    suspend fun navigateQuestion(sessionId: String, targetIndex: Int)
    suspend fun completeQuizSession(sessionId: String, stillStudying: Boolean = true): QuizSession
    suspend fun discardQuizSession(sessionId: String)
}

interface ProgressRepository {
    fun observeTopicReviewStates(): Flow<Map<String, TopicReviewState>>
    fun observeCompletedSessions(): Flow<List<QuizSession>>
    suspend fun getSubjectReadinessSummary(subjectId: String): SubjectReadinessSummary
}

interface PreferenceRepository {
    fun observePreferences(): Flow<UserPreferences>
    suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences)
    suspend fun exportLocalDataJson(): String
    suspend fun clearAllLocalData()
}

data class SubjectReadinessSummary(
    val subjectId: String,
    val totalTopics: Int,
    val statusCounts: Map<TopicMasteryStatus, Int>,
    val dueTopicCount: Int,
    val overallAccuracyPercent: Int?
)

@Serializable
data class LocalDataExportBundle(
    val exportedAtEpochMs: Long,
    val preferences: UserPreferences,
    val subjects: List<Subject>,
    val classSlots: List<ClassSlot>,
    val scheduleExceptions: List<ScheduleException>,
    val lectures: List<Lecture>,
    val sourceFiles: List<SourceFile>,
    val topics: List<Topic>,
    val learningItems: List<LearningItem>,
    val quizSessions: List<QuizSession>,
    val quizResponses: List<QuizResponse>,
    val topicReviewStates: List<TopicReviewState>,
    val dropStates: List<DropState>
    // Note: Gemini API key is strictly excluded from export per privacy & BYOK requirements
)
