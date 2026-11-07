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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.random.Random

/**
 * Transactional, Flow-backed local store implementing all Quick Drop repository interfaces.
 * Enforces foreign key integrity, historical quiz response snapshotting, idempotent quiz completion,
 * source retention choices on file/lecture deletion, and privacy-safe JSON export/clear.
 */
class QuickDropLocalStore(
    private val clock: AppClock,
    private val onClearKeystoreKey: () -> Unit = {}
) : SubjectRepository,
    TimetableRepository,
    LectureRepository,
    StudyRepository,
    QuizRepository,
    ProgressRepository,
    PreferenceRepository {

    private val mutex = Mutex()
    private val jsonPretty = Json { prettyPrint = true; encodeDefaults = true }

    private val preferencesFlow = MutableStateFlow(UserPreferences())
    private val subjectsFlow = MutableStateFlow<List<Subject>>(emptyList())
    private val slotsFlow = MutableStateFlow<List<ClassSlot>>(emptyList())
    private val exceptionsFlow = MutableStateFlow<List<ScheduleException>>(emptyList())
    private val lecturesFlow = MutableStateFlow<List<Lecture>>(emptyList())
    private val filesFlow = MutableStateFlow<List<SourceFile>>(emptyList())
    private val chunksFlow = MutableStateFlow<List<SourceChunk>>(emptyList())
    private val topicsFlow = MutableStateFlow<List<Topic>>(emptyList())
    private val itemsFlow = MutableStateFlow<List<LearningItem>>(emptyList())
    private val sessionsFlow = MutableStateFlow<List<QuizSession>>(emptyList())
    private val responsesFlow = MutableStateFlow<List<QuizResponse>>(emptyList())
    private val reviewStatesFlow = MutableStateFlow<Map<String, TopicReviewState>>(emptyMap())
    private val dropStatesFlow = MutableStateFlow<Map<String, DropState>>(emptyMap())
    private val aiJobsFlow = MutableStateFlow<List<AiJob>>(emptyList())

    // --- SubjectRepository ---

    override fun observeActiveSubjects(): Flow<List<Subject>> =
        subjectsFlow.map { list -> list.filter { it.archivedAtEpochMs == null }.sortedBy { it.name } }

    override fun observeAllSubjects(): Flow<List<Subject>> = subjectsFlow

    override suspend fun createOrUpdateSubject(
        id: String?,
        name: String,
        colorKey: String,
        iconKey: String,
        term: String?,
        instructor: String?
    ): Subject = mutex.withLock {
        require(name.isNotBlank()) { "Subject name is required" }
        val now = clock.now().toEpochMilli()
        val existing = id?.let { targetId -> subjectsFlow.value.find { it.id == targetId } }
        val subject = if (existing != null) {
            existing.copy(
                name = name.trim(),
                colorKey = colorKey,
                iconKey = iconKey,
                term = term?.trim()?.ifEmpty { null },
                instructor = instructor?.trim()?.ifEmpty { null },
                updatedAtEpochMs = now
            )
        } else {
            Subject(
                id = id ?: UUID.randomUUID().toString(),
                name = name.trim(),
                colorKey = colorKey,
                iconKey = iconKey,
                term = term?.trim()?.ifEmpty { null },
                instructor = instructor?.trim()?.ifEmpty { null },
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        }
        subjectsFlow.update { list -> list.filterNot { it.id == subject.id } + subject }
        subject
    }

    override suspend fun archiveSubject(subjectId: String) = mutex.withLock {
        val now = clock.now().toEpochMilli()
        subjectsFlow.update { list ->
            list.map { if (it.id == subjectId) it.copy(archivedAtEpochMs = now, updatedAtEpochMs = now) else it }
        }
    }

    // --- TimetableRepository ---

    override fun observeSlots(): Flow<List<ClassSlot>> = slotsFlow

    override fun observeExceptions(): Flow<List<ScheduleException>> = exceptionsFlow

    override suspend fun upsertClassSlot(slot: ClassSlot): Pair<ClassSlot, List<SlotOverlapWarning>> = mutex.withLock {
        require(subjectsFlow.value.any { it.id == slot.subjectId }) { "Subject ${slot.subjectId} does not exist" }
        val now = clock.now().toEpochMilli()
        val updated = slot.copy(
            createdAtEpochMs = if (slot.createdAtEpochMs == 0L) now else slot.createdAtEpochMs,
            updatedAtEpochMs = now
        )
        slotsFlow.update { list -> list.filterNot { it.id == updated.id } + updated }
        val warnings = TimetableCalculator.detectOverlaps(slotsFlow.value)
        updated to warnings
    }

    override suspend fun deleteClassSlot(slotId: String) = mutex.withLock {
        slotsFlow.update { list -> list.filterNot { it.id == slotId } }
        exceptionsFlow.update { list -> list.filterNot { it.slotId == slotId } }
    }

    override suspend fun upsertException(exception: ScheduleException): ScheduleException = mutex.withLock {
        require(slotsFlow.value.any { it.id == exception.slotId }) { "Slot ${exception.slotId} does not exist" }
        val now = clock.now().toEpochMilli()
        val saved = exception.copy(
            id = exception.id.ifBlank { UUID.randomUUID().toString() },
            createdAtEpochMs = if (exception.createdAtEpochMs == 0L) now else exception.createdAtEpochMs
        )
        // Unique constraint on (slotId, originalLocalDateIso); recurring template in slotsFlow is never mutated
        exceptionsFlow.update { list ->
            list.filterNot {
                it.id == saved.id ||
                    (it.slotId == saved.slotId && it.originalLocalDateIso == saved.originalLocalDateIso)
            } + saved
        }
        saved
    }

    override suspend fun removeException(exceptionId: String) = mutex.withLock {
        exceptionsFlow.update { list -> list.filterNot { it.id == exceptionId } }
    }

    override suspend fun getUpcomingOccurrences(daysAhead: Int): List<ClassOccurrence> {
        return TimetableCalculator.computeOccurrences(
            slots = slotsFlow.value,
            exceptions = exceptionsFlow.value,
            fromInstant = clock.now(),
            daysAhead = daysAhead,
            displayZone = clock.zoneId()
        )
    }

    // --- LectureRepository ---

    override fun observeLecturesForSubject(subjectId: String): Flow<List<Lecture>> =
        lecturesFlow.map { list ->
            list.filter { it.subjectId == subjectId }
                .sortedWith(compareByDescending<Lecture> { it.localDateIso }.thenByDescending { it.createdAtEpochMs })
        }

    override fun observeSourceFilesForLecture(lectureId: String): Flow<List<SourceFile>> =
        filesFlow.map { list -> list.filter { it.lectureId == lectureId } }

    override fun observeChunksForFile(fileId: String): Flow<List<SourceChunk>> =
        chunksFlow.map { list -> list.filter { it.fileId == fileId }.sortedBy { it.ordinal } }

    override suspend fun createOrUpdateLecture(
        id: String?,
        subjectId: String,
        title: String,
        localDateIso: String,
        topicLabel: String?,
        notes: String
    ): Lecture = mutex.withLock {
        require(title.isNotBlank()) { "Lecture title is required" }
        require(subjectsFlow.value.any { it.id == subjectId }) { "Subject does not exist" }
        val now = clock.now().toEpochMilli()
        val existing = id?.let { tid -> lecturesFlow.value.find { it.id == tid } }
        val lecture = if (existing != null) {
            existing.copy(
                title = title.trim(),
                localDateIso = localDateIso,
                topicLabel = topicLabel?.trim()?.ifEmpty { null },
                notes = notes,
                updatedAtEpochMs = now
            )
        } else {
            Lecture(
                id = id ?: UUID.randomUUID().toString(),
                subjectId = subjectId,
                title = title.trim(),
                localDateIso = localDateIso,
                topicLabel = topicLabel?.trim()?.ifEmpty { null },
                notes = notes,
                status = LectureStatus.READY,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        }
        lecturesFlow.update { list -> list.filterNot { it.id == lecture.id } + lecture }

        // If a topicLabel was provided, ensure the Topic exists
        if (!lecture.topicLabel.isNullOrBlank()) {
            ensureTopicInternal(subjectId, lecture.topicLabel, 0.5)
        }
        lecture
    }

    override suspend fun attachAndExtractFile(
        lectureId: String,
        displayName: String,
        bytes: ByteArray,
        privateRelativePath: String,
        ocrFallbackText: String?
    ): Pair<SourceFile, ExtractionOutcome> = mutex.withLock {
        require(lecturesFlow.value.any { it.id == lectureId }) { "Lecture does not exist" }
        val now = clock.now().toEpochMilli()
        val sha256 = FileExtractor.sha256Hex(bytes)

        // Deduplicate if the same SHA-256 is already attached to this lecture
        val duplicate = filesFlow.value.find { it.lectureId == lectureId && it.sha256 == sha256 }
        if (duplicate != null && duplicate.extractionState == FileJobState.EXTRACTED) {
            val existingChunks = chunksFlow.value.filter { it.fileId == duplicate.id }
            return duplicate to ExtractionOutcome(
                status = FileIntakeStatus.VALID,
                detectedMimeType = duplicate.mimeType,
                sha256 = sha256,
                pageOrSlideCount = duplicate.pageOrSlideCount,
                chunks = existingChunks,
                partialExtractionNotice = duplicate.partialExtractionNotice
            )
        }

        val fileId = duplicate?.id ?: UUID.randomUUID().toString()
        val outcome = FileExtractor.extract(
            fileId = fileId,
            bytes = bytes,
            fileName = displayName,
            ocrFallbackText = ocrFallbackText
        )

        val jobState = when (outcome.status) {
            FileIntakeStatus.VALID -> FileJobState.EXTRACTED
            FileIntakeStatus.SCANNED_OR_EMPTY_TEXT -> FileJobState.NEEDS_USER_ACTION
            FileIntakeStatus.UNSUPPORTED_MIME_OR_SIGNATURE,
            FileIntakeStatus.OVERSIZED_BYTES,
            FileIntakeStatus.EXCEEDS_PAGE_OR_SLIDE_LIMIT,
            FileIntakeStatus.ENCRYPTED_FILE,
            FileIntakeStatus.CORRUPT_FILE -> FileJobState.FAILED_FINAL
        }

        val sourceFile = SourceFile(
            id = fileId,
            lectureId = lectureId,
            displayName = displayName,
            mimeType = outcome.detectedMimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = outcome.sha256,
            privateRelativePath = privateRelativePath,
            uriPermissionHeld = true,
            extractionState = jobState,
            pageOrSlideCount = outcome.pageOrSlideCount,
            errorCode = if (outcome.status == FileIntakeStatus.VALID) null else outcome.status.name,
            partialExtractionNotice = outcome.partialExtractionNotice,
            createdAtEpochMs = now
        )

        filesFlow.update { list -> list.filterNot { it.id == fileId } + sourceFile }
        if (outcome.chunks.isNotEmpty()) {
            chunksFlow.update { list -> list.filterNot { it.fileId == fileId } + outcome.chunks }
        }

        sourceFile to outcome
    }

    override suspend fun removeSourceFile(
        fileId: String,
        deleteGeneratedItems: Boolean,
        retainApprovedItemsWithoutSource: Boolean
    ) = mutex.withLock {
        removeSourceFileInternal(fileId, deleteGeneratedItems, retainApprovedItemsWithoutSource)
    }

    private fun removeSourceFileInternal(
        fileId: String,
        deleteGeneratedItems: Boolean,
        retainApprovedItemsWithoutSource: Boolean
    ) {
        filesFlow.update { list -> list.filterNot { it.id == fileId } }
        chunksFlow.update { list -> list.filterNot { it.fileId == fileId } }

        itemsFlow.update { list ->
            list.mapNotNull { item ->
                if (item.sourceFileId != fileId) {
                    item
                } else if (item.approvalStatus == ApprovalStatus.APPROVED && retainApprovedItemsWithoutSource) {
                    item.copy(
                        sourceFileId = null,
                        sourceChunkId = null,
                        isManualOrRetainedWithoutSource = true
                    )
                } else if (deleteGeneratedItems) {
                    null
                } else {
                    item.copy(
                        sourceFileId = null,
                        sourceChunkId = null,
                        isManualOrRetainedWithoutSource = true
                    )
                }
            }
        }
    }

    override suspend fun deleteLecture(
        lectureId: String,
        deleteGeneratedItems: Boolean,
        retainApprovedItemsWithoutSource: Boolean
    ) = mutex.withLock {
        val fileIds = filesFlow.value.filter { it.lectureId == lectureId }.map { it.id }
        fileIds.forEach { fid ->
            removeSourceFileInternal(fid, deleteGeneratedItems, retainApprovedItemsWithoutSource)
        }
        lecturesFlow.update { list -> list.filterNot { it.id == lectureId } }
    }

    // --- StudyRepository ---

    override fun observeTopicsForSubject(subjectId: String): Flow<List<Topic>> =
        topicsFlow.map { list -> list.filter { it.subjectId == subjectId && it.archivedAtEpochMs == null } }

    override fun observeLearningItemsForLecture(lectureId: String): Flow<List<LearningItem>> =
        itemsFlow.map { list -> list.filter { it.lectureId == lectureId } }

    override fun observeDraftItems(): Flow<List<LearningItem>> =
        itemsFlow.map { list -> list.filter { it.approvalStatus == ApprovalStatus.DRAFT } }

    override fun observeApprovedDrops(): Flow<List<Pair<LearningItem, DropState>>> =
        combine(itemsFlow, dropStatesFlow) { items, states ->
            items.filter { it.kind == LearningItemKind.DROP && it.approvalStatus == ApprovalStatus.APPROVED }
                .map { item ->
                    val state = states[item.id] ?: DropState(learningItemId = item.id)
                    item to state
                }
        }

    override fun observeAiJobs(): Flow<List<AiJob>> = aiJobsFlow

    override suspend fun createOrGetTopic(subjectId: String, name: String, coreWeight: Double): Topic = mutex.withLock {
        ensureTopicInternal(subjectId, name, coreWeight)
    }

    private fun ensureTopicInternal(subjectId: String, name: String, coreWeight: Double): Topic {
        val key = DraftValidator.canonicalTopicKey(name)
        val existing = topicsFlow.value.find { it.subjectId == subjectId && it.canonicalKey == key }
        if (existing != null) return existing

        val created = Topic(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            name = name.trim(),
            canonicalKey = key,
            coreWeight = coreWeight.coerceIn(0.0, 1.0),
            createdAtEpochMs = clock.now().toEpochMilli()
        )
        topicsFlow.update { it + created }
        return created
    }

    override suspend fun createManualItem(
        subjectId: String,
        topicName: String,
        lectureId: String?,
        kind: LearningItemKind,
        promptOrBody: String,
        explanation: String,
        difficulty: Difficulty,
        options: List<Pair<String, Boolean>>
    ): Result<LearningItem> = mutex.withLock {
        if (promptOrBody.isBlank()) {
            return Result.failure(IllegalArgumentException("Prompt or body cannot be empty."))
        }
        if (kind == LearningItemKind.MCQ) {
            if (options.size < 2) {
                return Result.failure(IllegalArgumentException("MCQ requires at least 2 options."))
            }
            if (options.count { it.second } != 1) {
                return Result.failure(IllegalArgumentException("MCQ requires exactly 1 correct option."))
            }
        } else if (options.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Drop item cannot have MCQ options."))
        }

        val topic = ensureTopicInternal(subjectId, topicName, 0.5)
        val itemId = UUID.randomUUID().toString()
        val now = clock.now().toEpochMilli()
        val answerOptions = options.mapIndexed { index, (text, isCorrect) ->
            AnswerOption(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                ordinal = index + 1,
                body = text.trim(),
                isCorrect = isCorrect
            )
        }
        val hash = DraftValidator.computeContentHash(kind, promptOrBody, answerOptions.map { it.body })

        val item = LearningItem(
            id = itemId,
            topicId = topic.id,
            lectureId = lectureId,
            kind = kind,
            promptOrBody = promptOrBody.trim(),
            explanation = explanation.trim(),
            difficulty = difficulty,
            anchorKind = AnchorKind.NOTE,
            anchorIndex = 1,
            evidenceQuote = "Manual student entry",
            approvalStatus = ApprovalStatus.APPROVED, // Manual student-authored item is immediately approved
            isManualOrRetainedWithoutSource = true,
            confidenceLabel = ConfidenceLabel.HIGH,
            version = 1,
            contentHash = hash,
            options = answerOptions,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        itemsFlow.update { it + item }
        if (kind == LearningItemKind.DROP) {
            dropStatesFlow.update { it + (item.id to DropState(learningItemId = item.id)) }
        }
        Result.success(item)
    }

    override suspend fun generateDraftsWithAi(
        lectureId: String,
        apiKey: String,
        client: GeminiClient
    ): AiJob {
        val lecture = lecturesFlow.value.find { it.id == lectureId }
            ?: throw IllegalArgumentException("Lecture $lectureId not found")
        val subject = subjectsFlow.value.find { it.id == lecture.subjectId }
            ?: throw IllegalArgumentException("Subject ${lecture.subjectId} not found")
        val prefs = preferencesFlow.value

        val now = clock.now().toEpochMilli()
        val jobId = UUID.randomUUID().toString()

        if (!prefs.cloudTextSubmissionConsentGranted) {
            val consentJob = AiJob(
                id = jobId,
                jobType = AiJobType.EXTRACT_AND_DRAFT,
                lectureId = lectureId,
                state = FileJobState.NEEDS_USER_ACTION,
                attempts = 0,
                modelId = prefs.geminiModelId,
                promptVersion = GeminiPipeline.PROMPT_VERSION,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                errorCode = AiErrorCode.CONSENT_REQUIRED,
                sanitizedStatusMessage = "Explicit consent is required before sending selected lecture text to Google Gemini."
            )
            mutex.withLock {
                aiJobsFlow.update { it + consentJob }
            }
            return consentJob
        }

        val lectureFiles = filesFlow.value.filter { it.lectureId == lectureId }
        val fileChunks = chunksFlow.value.filter { chunk -> lectureFiles.any { it.id == chunk.fileId } }.toMutableList()

        // Include manual lecture notes as a chunk if present
        if (lecture.notes.isNotBlank()) {
            val virtualFileId = lectureFiles.firstOrNull()?.id ?: "manual-note-$lectureId"
            fileChunks.add(
                SourceChunk(
                    id = "note-chunk-$lectureId",
                    fileId = virtualFileId,
                    anchorKind = AnchorKind.NOTE,
                    anchorIndex = 1,
                    ordinal = fileChunks.size + 1,
                    extractedText = lecture.notes,
                    tokenEstimate = FileExtractor.estimateTokens(lecture.notes),
                    contentHash = FileExtractor.sha256Hex(lecture.notes.toByteArray(Charsets.UTF_8))
                )
            )
        }

        val existingApprovedHashes = itemsFlow.value
            .filter { it.approvalStatus == ApprovalStatus.APPROVED }
            .map { it.contentHash }
            .toSet()

        val (response, validDrafts) = GeminiPipeline.generateAndValidateWithSingleRetry(
            client = client,
            apiKey = apiKey,
            modelId = prefs.geminiModelId,
            subjectName = subject.name,
            lectureTitle = lecture.title,
            chunks = fileChunks,
            existingApprovedHashes = existingApprovedHashes
        )

        return mutex.withLock {
            val finishMs = clock.now().toEpochMilli()
            when (response) {
                is GeminiResponse.Failure -> {
                    val state = when (response.errorCode) {
                        AiErrorCode.OFFLINE_OR_TIMEOUT -> FileJobState.WAITING_FOR_NETWORK
                        AiErrorCode.NO_KEY, AiErrorCode.INVALID_KEY, AiErrorCode.CONSENT_REQUIRED -> FileJobState.NEEDS_USER_ACTION
                        AiErrorCode.QUOTA_EXCEEDED -> FileJobState.FAILED_RETRYABLE
                        else -> if (response.retryable) FileJobState.FAILED_RETRYABLE else FileJobState.FAILED_FINAL
                    }
                    val job = AiJob(
                        id = jobId,
                        jobType = AiJobType.EXTRACT_AND_DRAFT,
                        lectureId = lectureId,
                        sourceFileIds = lectureFiles.map { it.id },
                        state = state,
                        attempts = 1,
                        modelId = prefs.geminiModelId,
                        promptVersion = GeminiPipeline.PROMPT_VERSION,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = finishMs,
                        errorCode = response.errorCode,
                        retryAfterSeconds = response.retryAfterSeconds,
                        sanitizedStatusMessage = response.sanitizedMessage
                    )
                    aiJobsFlow.update { it + job }
                    job
                }

                is GeminiResponse.Success -> {
                    val createdItems = mutableListOf<LearningItem>()
                    for (draft in validDrafts) {
                        val topic = ensureTopicInternal(subject.id, draft.topicName, 0.5)
                        val itemId = UUID.randomUUID().toString()
                        val options = draft.options.mapIndexed { idx, opt ->
                            AnswerOption(
                                id = UUID.randomUUID().toString(),
                                itemId = itemId,
                                ordinal = idx + 1,
                                body = opt.text.trim(),
                                isCorrect = opt.isCorrect
                            )
                        }
                        val matchedChunk = fileChunks.firstOrNull {
                            it.fileId == draft.source.fileId &&
                                it.anchorKind == draft.source.anchorKind &&
                                it.anchorIndex == draft.source.anchorIndex
                        }
                        val hash = DraftValidator.computeContentHash(
                            kind = draft.kind,
                            promptOrBody = draft.promptOrBody,
                            options = options.map { it.body }
                        )

                        // Crucial requirement: AI generated items start in ApprovalStatus.DRAFT, NEVER APPROVED
                        createdItems.add(
                            LearningItem(
                                id = itemId,
                                topicId = topic.id,
                                lectureId = lectureId,
                                kind = draft.kind,
                                promptOrBody = draft.promptOrBody.trim(),
                                explanation = draft.explanation.trim(),
                                difficulty = draft.difficulty,
                                sourceFileId = draft.source.fileId.takeIf { id -> lectureFiles.any { it.id == id } },
                                sourceChunkId = matchedChunk?.id,
                                anchorKind = draft.source.anchorKind,
                                anchorIndex = draft.source.anchorIndex,
                                evidenceQuote = draft.evidenceQuote.trim(),
                                approvalStatus = ApprovalStatus.DRAFT,
                                isManualOrRetainedWithoutSource = draft.source.anchorKind == AnchorKind.NOTE,
                                confidenceLabel = draft.confidenceLabel,
                                version = 1,
                                contentHash = hash,
                                options = options,
                                createdAtEpochMs = finishMs,
                                updatedAtEpochMs = finishMs
                            )
                        )
                    }
                    itemsFlow.update { it + createdItems }

                    val job = AiJob(
                        id = jobId,
                        jobType = AiJobType.EXTRACT_AND_DRAFT,
                        lectureId = lectureId,
                        sourceFileIds = lectureFiles.map { it.id },
                        state = FileJobState.REVIEW_READY,
                        attempts = 1,
                        modelId = response.modelId,
                        promptVersion = response.promptVersion,
                        createdAtEpochMs = now,
                        updatedAtEpochMs = finishMs,
                        errorCode = AiErrorCode.NONE,
                        sanitizedStatusMessage = if (response.envelope.abstained) {
                            "Abstained: ${response.envelope.abstentionReason ?: "Insufficient source evidence"}"
                        } else {
                            "Generated ${createdItems.size} draft items ready for student review."
                        }
                    )
                    aiJobsFlow.update { it + job }
                    job
                }
            }
        }
    }

    override suspend fun editAndRevalidateItem(
        itemId: String,
        newPromptOrBody: String,
        newExplanation: String,
        newDifficulty: Difficulty,
        newOptions: List<Pair<String, Boolean>>
    ): Result<LearningItem> = mutex.withLock {
        val existing = itemsFlow.value.find { it.id == itemId }
            ?: return Result.failure(IllegalArgumentException("Item not found"))

        if (newPromptOrBody.trim().length < 8) {
            return Result.failure(IllegalArgumentException("Prompt/body must be at least 8 characters."))
        }
        if (existing.kind == LearningItemKind.MCQ) {
            if (newOptions.size < 2) {
                return Result.failure(IllegalArgumentException("MCQ requires at least 2 options."))
            }
            if (newOptions.count { it.second } != 1) {
                return Result.failure(IllegalArgumentException("MCQ requires exactly 1 correct option."))
            }
            if (newExplanation.trim().length < 8) {
                return Result.failure(IllegalArgumentException("MCQ explanation must be at least 8 characters."))
            }
        } else if (newOptions.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Drop item cannot have answer options."))
        }

        val now = clock.now().toEpochMilli()
        val updatedOptions = newOptions.mapIndexed { idx, (text, isCorrect) ->
            AnswerOption(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                ordinal = idx + 1,
                body = text.trim(),
                isCorrect = isCorrect
            )
        }
        val newHash = DraftValidator.computeContentHash(
            kind = existing.kind,
            promptOrBody = newPromptOrBody,
            options = updatedOptions.map { it.body }
        )

        val updated = existing.copy(
            promptOrBody = newPromptOrBody.trim(),
            explanation = newExplanation.trim(),
            difficulty = newDifficulty,
            options = updatedOptions,
            version = existing.version + 1,
            contentHash = newHash,
            updatedAtEpochMs = now
        )
        itemsFlow.update { list -> list.map { if (it.id == itemId) updated else it } }
        Result.success(updated)
    }

    override suspend fun approveItem(itemId: String): Result<LearningItem> = mutex.withLock {
        val existing = itemsFlow.value.find { it.id == itemId }
            ?: return Result.failure(IllegalArgumentException("Item not found"))

        if (existing.kind == LearningItemKind.MCQ) {
            if (existing.options.size < 2 || existing.options.count { it.isCorrect } != 1) {
                return Result.failure(IllegalStateException("MCQ must have >=2 options and exactly 1 correct option before approval."))
            }
        }

        val now = clock.now().toEpochMilli()
        val approved = existing.copy(
            approvalStatus = ApprovalStatus.APPROVED,
            updatedAtEpochMs = now
        )
        itemsFlow.update { list -> list.map { if (it.id == itemId) approved else it } }
        if (approved.kind == LearningItemKind.DROP && !dropStatesFlow.value.containsKey(approved.id)) {
            dropStatesFlow.update { it + (approved.id to DropState(learningItemId = approved.id)) }
        }
        Result.success(approved)
    }

    override suspend fun rejectItem(itemId: String) = mutex.withLock {
        val now = clock.now().toEpochMilli()
        itemsFlow.update { list ->
            list.map { if (it.id == itemId) it.copy(approvalStatus = ApprovalStatus.REJECTED, updatedAtEpochMs = now) else it }
        }
    }

    override suspend fun updateDropState(itemId: String, visibilityState: DropVisibilityState) = mutex.withLock {
        val now = clock.now().toEpochMilli()
        val current = dropStatesFlow.value[itemId] ?: DropState(learningItemId = itemId)
        val updated = current.copy(
            visibilityState = visibilityState,
            savedAtEpochMs = if (visibilityState == DropVisibilityState.SAVED) now else current.savedAtEpochMs,
            lastSeenAtEpochMs = now
        )
        dropStatesFlow.update { it + (itemId to updated) }
    }

    // --- QuizRepository ---

    override fun observeActiveSession(): Flow<QuizSession?> =
        sessionsFlow.map { list -> list.firstOrNull { it.status == QuizSessionStatus.IN_PROGRESS } }

    override fun observeSessionById(sessionId: String): Flow<QuizSession?> =
        sessionsFlow.map { list -> list.find { it.id == sessionId } }

    override fun observeResponsesForSession(sessionId: String): Flow<List<QuizResponse>> =
        responsesFlow.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.sequence } }

    override suspend fun startNewQuizSession(
        scope: QuizScopeSnapshot,
        random: Random
    ): Pair<QuizSession?, QuizSelectionResult> = mutex.withLock {
        val allTopics = topicsFlow.value.associateBy { it.id }
        val allLectures = lecturesFlow.value.associateBy { it.id }
        val reviewStates = reviewStatesFlow.value
        val validFileIds = filesFlow.value.map { it.id }.toSet()

        // Determine newest topic of primary subject (by newest lecture date or latest topic creation)
        val newestTopicIdForPrimarySubject: String? = scope.primarySubjectId?.let { subjId ->
            val newestLecture = lecturesFlow.value
                .filter { it.subjectId == subjId && !it.topicLabel.isNullOrBlank() }
                .maxWithOrNull(compareBy<Lecture> { it.localDateIso }.thenBy { it.createdAtEpochMs })
            val byLectureKey = newestLecture?.topicLabel?.let { DraftValidator.canonicalTopicKey(it) }
            val matchedByLecture = topicsFlow.value.find { it.subjectId == subjId && it.canonicalKey == byLectureKey }
            matchedByLecture?.id ?: topicsFlow.value
                .filter { it.subjectId == subjId && it.archivedAtEpochMs == null }
                .maxByOrNull { it.createdAtEpochMs }?.id
        }

        // Last correct answer timestamp per item across completed sessions
        val completedSessionIds = sessionsFlow.value
            .filter { it.status == QuizSessionStatus.COMPLETED }
            .map { it.id }
            .toSet()

        val lastCorrectByItem = responsesFlow.value
            .filter { it.sessionId in completedSessionIds && it.outcome == ResponseOutcome.CORRECT }
            .groupBy { it.itemId }
            .mapValues { (_, list) -> list.maxOf { it.answeredAtEpochMs } }

        val candidates = itemsFlow.value.mapNotNull { item ->
            val topic = allTopics[item.topicId] ?: return@mapNotNull null
            SelectionCandidate(
                item = item,
                topic = topic,
                reviewState = reviewStates[topic.id],
                isNewestTopicOfPrimarySubject = topic.id == newestTopicIdForPrimarySubject,
                isPrimarySubject = scope.primarySubjectId == null || topic.subjectId == scope.primarySubjectId,
                lastAnsweredCorrectlyAtEpochMs = lastCorrectByItem[item.id]
            )
        }

        val selectionResult = QuizSelector.selectQuestions(
            candidates = candidates,
            scope = scope,
            validSourceFileIds = validFileIds,
            clock = clock,
            random = random
        )

        if (selectionResult.selectedItems.isEmpty()) {
            return null to selectionResult
        }

        val session = QuizSession(
            id = UUID.randomUUID().toString(),
            scopeSnapshot = scope.copy(actualEligibleCount = selectionResult.availableEligibleCount),
            itemIdsInOrder = selectionResult.selectedItems.map { it.id },
            currentQuestionIndex = 0,
            status = QuizSessionStatus.IN_PROGRESS,
            startedAtEpochMs = clock.now().toEpochMilli()
        )
        sessionsFlow.update { it + session }
        session to selectionResult
    }

    override suspend fun recordAnswer(
        sessionId: String,
        itemId: String,
        selectedOptionId: String?,
        latencyMs: Long,
        confidence: ReviewConfidence
    ): QuizResponse = mutex.withLock {
        val session = sessionsFlow.value.find { it.id == sessionId }
            ?: throw IllegalArgumentException("Session $sessionId not found")
        require(session.status == QuizSessionStatus.IN_PROGRESS) { "Quiz session is no longer in progress" }

        val item = itemsFlow.value.find { it.id == itemId }
            ?: throw IllegalArgumentException("Item $itemId not found")

        val outcome = when {
            selectedOptionId == null -> ResponseOutcome.SKIPPED
            item.options.find { it.id == selectedOptionId }?.isCorrect == true -> ResponseOutcome.CORRECT
            else -> ResponseOutcome.INCORRECT
        }

        val sourceFile = item.sourceFileId?.let { fid -> filesFlow.value.find { it.id == fid } }
        val citation = if (sourceFile != null && item.anchorKind != null && item.anchorIndex != null) {
            "${sourceFile.displayName} (${item.anchorKind} ${item.anchorIndex})"
        } else {
            "Manual / Retained Study Note"
        }

        val sequenceIndex = session.itemIdsInOrder.indexOf(itemId).coerceAtLeast(0)
        val response = QuizResponse(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            itemId = item.id,
            itemVersion = item.version,
            topicId = item.topicId,
            sequence = sequenceIndex,
            selectedOptionId = selectedOptionId,
            outcome = outcome,
            latencyMs = latencyMs,
            answeredAtEpochMs = clock.now().toEpochMilli(),
            confidence = confidence,
            promptSnapshot = item.promptOrBody,
            explanationSnapshot = item.explanation,
            sourceCitationSnapshot = citation,
            optionsSnapshot = item.options.map { opt ->
                SnapshotOption(
                    id = opt.id,
                    ordinal = opt.ordinal,
                    body = opt.body,
                    isCorrect = opt.isCorrect
                )
            }
        )

        // Persist immediately so process death never loses committed answers
        responsesFlow.update { list ->
            list.filterNot { it.sessionId == sessionId && it.itemId == itemId } + response
        }

        val nextIdx = (sequenceIndex + 1).coerceAtMost((session.itemIdsInOrder.size - 1).coerceAtLeast(0))
        sessionsFlow.update { list ->
            list.map { if (it.id == sessionId) it.copy(currentQuestionIndex = nextIdx) else it }
        }
        response
    }

    override suspend fun navigateQuestion(sessionId: String, targetIndex: Int) = mutex.withLock {
        sessionsFlow.update { list ->
            list.map { session ->
                if (session.id == sessionId && session.status == QuizSessionStatus.IN_PROGRESS) {
                    session.copy(
                        currentQuestionIndex = targetIndex.coerceIn(0, (session.itemIdsInOrder.size - 1).coerceAtLeast(0))
                    )
                } else {
                    session
                }
            }
        }
    }

    /**
     * Atomically marks the quiz session completed and updates per-topic spaced repetition state.
     * Strictly idempotent: calling completeQuizSession multiple times on an already COMPLETED session
     * returns the completed session without double-counting topic review states.
     */
    override suspend fun completeQuizSession(sessionId: String, stillStudying: Boolean): QuizSession = mutex.withLock {
        val session = sessionsFlow.value.find { it.id == sessionId }
            ?: throw IllegalArgumentException("Session $sessionId not found")

        if (session.status == QuizSessionStatus.COMPLETED) {
            return session // Idempotent guard
        }
        require(session.status == QuizSessionStatus.IN_PROGRESS) { "Cannot complete an abandoned session" }

        val sessionResponses = responsesFlow.value
            .filter { it.sessionId == sessionId }
            .sortedBy { it.sequence }

        // Apply spaced repetition updates for each response in sequence
        val currentMap = reviewStatesFlow.value.toMutableMap()
        for (resp in sessionResponses) {
            val prevState = currentMap[resp.topicId]
            val updatedState = SpacedRepetitionScheduler.recordOutcome(
                currentState = prevState,
                topicId = resp.topicId,
                outcome = resp.outcome,
                confidence = resp.confidence,
                stillStudying = stillStudying,
                clock = clock
            )
            currentMap[resp.topicId] = updatedState
        }
        reviewStatesFlow.value = currentMap

        val correctCount = sessionResponses.count { it.outcome == ResponseOutcome.CORRECT }
        val answeredCount = sessionResponses.count { it.outcome != ResponseOutcome.SKIPPED }
        val skippedCount = session.itemIdsInOrder.size - answeredCount

        val completed = session.copy(
            status = QuizSessionStatus.COMPLETED,
            endedAtEpochMs = clock.now().toEpochMilli(),
            correctCount = correctCount,
            answeredCount = answeredCount,
            skippedCount = skippedCount
        )
        sessionsFlow.update { list -> list.map { if (it.id == sessionId) completed else it } }
        completed
    }

    /**
     * Discards an in-progress quiz session without mutating any [TopicReviewState].
     */
    override suspend fun discardQuizSession(sessionId: String) = mutex.withLock {
        val now = clock.now().toEpochMilli()
        sessionsFlow.update { list ->
            list.map {
                if (it.id == sessionId && it.status == QuizSessionStatus.IN_PROGRESS) {
                    it.copy(status = QuizSessionStatus.ABANDONED, endedAtEpochMs = now)
                } else {
                    it
                }
            }
        }
    }

    // --- ProgressRepository ---

    override fun observeTopicReviewStates(): Flow<Map<String, TopicReviewState>> = reviewStatesFlow

    override fun observeCompletedSessions(): Flow<List<QuizSession>> =
        sessionsFlow.map { list ->
            list.filter { it.status == QuizSessionStatus.COMPLETED }
                .sortedByDescending { it.endedAtEpochMs ?: it.startedAtEpochMs }
        }

    override suspend fun getSubjectReadinessSummary(subjectId: String): SubjectReadinessSummary {
        val topics = topicsFlow.value.filter { it.subjectId == subjectId && it.archivedAtEpochMs == null }
        val states = reviewStatesFlow.value
        val counts = mutableMapOf<TopicMasteryStatus, Int>()
        TopicMasteryStatus.entries.forEach { counts[it] = 0 }

        var totalCorrect = 0
        var totalAttempts = 0

        for (topic in topics) {
            val st = states[topic.id]
            val status = SpacedRepetitionScheduler.resolveMasteryStatus(st, clock)
            counts[status] = (counts[status] ?: 0) + 1
            if (st != null) {
                totalCorrect += st.correctCount
                totalAttempts += st.attemptCount
            }
        }

        val accuracyPercent = if (totalAttempts > 0) {
            ((totalCorrect.toDouble() / totalAttempts.toDouble()) * 100.0).toInt()
        } else {
            null
        }

        return SubjectReadinessSummary(
            subjectId = subjectId,
            totalTopics = topics.size,
            statusCounts = counts,
            dueTopicCount = counts[TopicMasteryStatus.DUE] ?: 0,
            overallAccuracyPercent = accuracyPercent
        )
    }

    // --- PreferenceRepository ---

    override fun observePreferences(): Flow<UserPreferences> = preferencesFlow

    override suspend fun updatePreferences(transform: (UserPreferences) -> UserPreferences) = mutex.withLock {
        preferencesFlow.update(transform)
    }

    override suspend fun exportLocalDataJson(): String = mutex.withLock {
        val bundle = LocalDataExportBundle(
            exportedAtEpochMs = clock.now().toEpochMilli(),
            preferences = preferencesFlow.value,
            subjects = subjectsFlow.value,
            classSlots = slotsFlow.value,
            scheduleExceptions = exceptionsFlow.value,
            lectures = lecturesFlow.value,
            sourceFiles = filesFlow.value,
            topics = topicsFlow.value,
            learningItems = itemsFlow.value,
            quizSessions = sessionsFlow.value,
            quizResponses = responsesFlow.value,
            topicReviewStates = reviewStatesFlow.value.values.toList(),
            dropStates = dropStatesFlow.value.values.toList()
        )
        jsonPretty.encodeToString(bundle)
    }

    override suspend fun clearAllLocalData() = mutex.withLock {
        preferencesFlow.value = UserPreferences()
        subjectsFlow.value = emptyList()
        slotsFlow.value = emptyList()
        exceptionsFlow.value = emptyList()
        lecturesFlow.value = emptyList()
        filesFlow.value = emptyList()
        chunksFlow.value = emptyList()
        topicsFlow.value = emptyList()
        itemsFlow.value = emptyList()
        sessionsFlow.value = emptyList()
        responsesFlow.value = emptyList()
        reviewStatesFlow.value = emptyMap()
        dropStatesFlow.value = emptyMap()
        aiJobsFlow.value = emptyList()
        onClearKeystoreKey()
    }
}
