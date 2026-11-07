package com.quickdrop.core

import com.quickdrop.core.domain.DraftOptionPayload
import com.quickdrop.core.domain.DraftSourceAnchorPayload
import com.quickdrop.core.domain.DraftValidator
import com.quickdrop.core.domain.FakeGeminiClient
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.domain.FileIntakeStatus
import com.quickdrop.core.domain.GeminiPipeline
import com.quickdrop.core.domain.GeminiResponse
import com.quickdrop.core.domain.GeneratedDraftPayload
import com.quickdrop.core.domain.QuizSelector
import com.quickdrop.core.domain.ReminderDecisionKind
import com.quickdrop.core.domain.ReminderPolicy
import com.quickdrop.core.domain.SelectionCandidate
import com.quickdrop.core.domain.SpacedRepetitionScheduler
import com.quickdrop.core.domain.TimetableCalculator
import com.quickdrop.core.model.AiErrorCode
import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.AnswerOption
import com.quickdrop.core.model.ApprovalStatus
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.DropVisibilityState
import com.quickdrop.core.model.FileJobState
import com.quickdrop.core.model.LearningItem
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.QuizDifficultyFilter
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.QuizSessionStatus
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ReviewConfidence
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.ScheduleExceptionAction
import com.quickdrop.core.model.SourceChunk
import com.quickdrop.core.model.Topic
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.TopicReviewState
import com.quickdrop.core.model.UserPreferences
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.security.AesGcmKeyVault
import com.quickdrop.core.time.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class QuickDropAcceptanceTest {

    // =========================================================================
    // 1. SCHEDULE & TIMETABLE ACCEPTANCE TESTS (M1)
    // =========================================================================

    @Test
    fun `schedule recurrence, cancel and move exceptions preserve recurring template and handle DST & zone changes`() {
        val berlinZone = ZoneId.of("Europe/Berlin")
        val newYorkZone = ZoneId.of("America/New_York")

        // Monday (bit 0 = 1) and Wednesday (bit 2 = 4) -> mask = 5
        val slot = ClassSlot(
            id = "slot-cs101",
            subjectId = "subj-cs101",
            weekdayMask = 1 or 4,
            startMinuteOfDay = 9 * 60, // 09:00
            endMinuteOfDay = 10 * 60 + 30, // 10:30
            zoneId = berlinZone.id,
            location = "Hall A",
            enabled = true,
            reminderEnabled = true,
            reminderLeadMinutes = 15
        )

        // Cancel Monday 2026-11-09, Move Wednesday 2026-11-11 to Thursday 2026-11-12 at 14:00-15:30 in Room B
        val exceptions = listOf(
            ScheduleException(
                id = "ex-cancel",
                slotId = slot.id,
                originalLocalDateIso = "2026-11-09",
                action = ScheduleExceptionAction.CANCEL
            ),
            ScheduleException(
                id = "ex-move",
                slotId = slot.id,
                originalLocalDateIso = "2026-11-11",
                action = ScheduleExceptionAction.MOVE,
                replacementLocalDateIso = "2026-11-12",
                replacementStartMinuteOfDay = 14 * 60,
                replacementEndMinuteOfDay = 15 * 60 + 30,
                replacementLocation = "Room B"
            )
        )

        val fromInstant = Instant.parse("2026-11-07T12:00:00Z")
        val occurrencesBerlin = TimetableCalculator.computeOccurrences(
            slots = listOf(slot),
            exceptions = exceptions,
            fromInstant = fromInstant,
            daysAhead = 10,
            displayZone = berlinZone
        )

        // 2026-11-09 (Cancelled) must be absent; first upcoming is moved 2026-11-12, then normal Monday 2026-11-16
        assertTrue(occurrencesBerlin.none { it.originalLocalDate == LocalDate.of(2026, 11, 9) })

        val firstOcc = occurrencesBerlin.first()
        assertEquals(LocalDate.of(2026, 11, 11), firstOcc.originalLocalDate)
        assertEquals(LocalDate.of(2026, 11, 12), firstOcc.effectiveLocalDate)
        assertTrue(firstOcc.isMoved)
        assertEquals("Room B", firstOcc.location)
        assertEquals(14, firstOcc.startZoned.hour)

        // Recurring template is untouched for the next week (Monday 2026-11-16 at 09:00 Hall A)
        val nextWeekMonday = occurrencesBerlin.find { it.originalLocalDate == LocalDate.of(2026, 11, 16) }
        assertNotNull(nextWeekMonday)
        assertFalse(nextWeekMonday!!.isMoved)
        assertEquals("Hall A", nextWeekMonday.location)
        assertEquals(9, nextWeekMonday.startZoned.hour)

        // Recalculate future instances when student travels to America/New_York (6 hours behind Berlin in November)
        val occurrencesNewYork = TimetableCalculator.computeOccurrences(
            slots = listOf(slot),
            exceptions = exceptions,
            fromInstant = fromInstant,
            daysAhead = 10,
            displayZone = newYorkZone
        )
        val nextWeekInNy = occurrencesNewYork.find { it.originalLocalDate == LocalDate.of(2026, 11, 16) }!!
        assertEquals(3, nextWeekInNy.startZoned.hour) // 09:00 CET == 03:00 EST

        // DST Spring-Forward Gap test (2026-03-29 in Europe/Berlin jumps from 02:00 to 03:00)
        val dstGapResolved = TimetableCalculator.resolveLocalTime(
            date = LocalDate.of(2026, 3, 29),
            minuteOfDay = 2 * 60 + 30, // 02:30 (inside gap)
            zoneId = berlinZone
        )
        assertEquals(3, dstGapResolved.hour)
        assertEquals(30, dstGapResolved.minute)
    }

    @Test
    fun `slot overlap warns without blocking`() {
        val slotA = ClassSlot(
            id = "s1",
            subjectId = "sub1",
            weekdayMask = 1, // Monday
            startMinuteOfDay = 600,
            endMinuteOfDay = 660,
            zoneId = "UTC"
        )
        val slotB = ClassSlot(
            id = "s2",
            subjectId = "sub2",
            weekdayMask = 1, // Monday
            startMinuteOfDay = 630,
            endMinuteOfDay = 690,
            zoneId = "UTC"
        )
        val warnings = TimetableCalculator.detectOverlaps(listOf(slotA, slotB))
        assertEquals(1, warnings.size)
        assertEquals(630, warnings.first().overlapStartMinute)
        assertEquals(660, warnings.first().overlapEndMinute)
    }

    // =========================================================================
    // 2. FILE UPLOAD & EXTRACTION ACCEPTANCE TESTS (M2)
    // =========================================================================

    @Test
    fun `valid PDF and PPTX extract anchored chunks while corrupt, encrypted, unsupported, and 200+ page files return actionable errors`() {
        // 1. Valid 2-page PDF
        val validPdfBytes = createSyntheticPdf(
            pages = listOf(
                "Page one introduces dynamic programming and optimal substructure in algorithms.",
                "Page two explains memoization tables and bottom-up tabulation complexity."
            )
        )
        val pdfOutcome = FileExtractor.extract("file-pdf-1", validPdfBytes, "lecture1.pdf")
        assertEquals(FileIntakeStatus.VALID, pdfOutcome.status)
        assertEquals(2, pdfOutcome.pageOrSlideCount)
        assertEquals(2, pdfOutcome.chunks.size)
        assertEquals(AnchorKind.PAGE, pdfOutcome.chunks[0].anchorKind)
        assertEquals(1, pdfOutcome.chunks[0].anchorIndex)
        assertTrue(pdfOutcome.chunks[1].extractedText.contains("memoization"))

        // 2. Valid PPTX with slide text + embedded chart notice
        val validPptxBytes = createSyntheticPptx(
            slides = mapOf(
                1 to "Slide 1: Operating Systems Kernel Architecture and System Calls.",
                2 to "Slide 2: Virtual Memory Paging and Translation Lookaside Buffers."
            ),
            includeChart = true
        )
        val pptxOutcome = FileExtractor.extract("file-pptx-1", validPptxBytes, "slides.pptx")
        assertEquals(FileIntakeStatus.VALID, pptxOutcome.status)
        assertEquals(2, pptxOutcome.pageOrSlideCount)
        assertEquals(AnchorKind.SLIDE, pptxOutcome.chunks[0].anchorKind)
        assertEquals(1, pptxOutcome.chunks[0].anchorIndex)
        assertNotNull(pptxOutcome.partialExtractionNotice)

        // 3. Encrypted PDF -> ENCRYPTED_FILE actionable error
        val encryptedPdfBytes = createSyntheticPdf(
            pages = listOf("Secret content"),
            encryptedPassword = "secret"
        )
        val encryptedOutcome = FileExtractor.extract("file-enc", encryptedPdfBytes, "locked.pdf")
        assertEquals(FileIntakeStatus.ENCRYPTED_FILE, encryptedOutcome.status)
        assertNotNull(encryptedOutcome.userActionableMessage)

        // 4. PDF exceeding 200-page limit -> EXCEEDS_PAGE_OR_SLIDE_LIMIT
        val hugePagePdfBytes = createBlankMultiPagePdf(pageCount = 205)
        val hugePageOutcome = FileExtractor.extract("file-205", hugePagePdfBytes, "huge.pdf")
        assertEquals(FileIntakeStatus.EXCEEDS_PAGE_OR_SLIDE_LIMIT, hugePageOutcome.status)

        // 5. Corrupt PDF with valid magic header -> CORRUPT_FILE
        val corruptPdfBytes = "%PDF-1.7\nGARBAGE_NOT_A_VALID_XREF".toByteArray(Charsets.UTF_8)
        val corruptOutcome = FileExtractor.extract("file-corrupt", corruptPdfBytes, "corrupt.pdf")
        assertEquals(FileIntakeStatus.CORRUPT_FILE, corruptOutcome.status)

        // 6. Disguised executable or unknown signature with .pdf extension -> UNSUPPORTED_MIME_OR_SIGNATURE
        val fakeExtensionBytes = "MZ\u0090\u0000NotAPdfAtAll".toByteArray(Charsets.UTF_8)
        val unsupportedOutcome = FileExtractor.extract("file-fake", fakeExtensionBytes, "malware.pdf")
        assertEquals(FileIntakeStatus.UNSUPPORTED_MIME_OR_SIGNATURE, unsupportedOutcome.status)
    }

    // =========================================================================
    // 3. AI VALIDATION, APPROVAL GATE, & BYOK SECURITY TESTS (M3)
    // =========================================================================

    @Test
    fun `unapproved AI drafts are never eligible for quiz, invalid first output retries once with feedback, and source deletion respects retention choice`() = runTest {
        val clock = FakeClock(Instant.parse("2026-11-07T10:00:00Z"))
        var keystoreWiped = false
        val store = QuickDropLocalStore(clock, onClearKeystoreKey = { keystoreWiped = true })

        val subject = store.createOrUpdateSubject(
            name = "Distributed Systems",
            colorKey = "blue",
            iconKey = "cloud"
        )
        val lecture = store.createOrUpdateLecture(
            subjectId = subject.id,
            title = "Raft Consensus",
            localDateIso = "2026-11-07",
            topicLabel = "Raft Consensus",
            notes = "Raft elects a single leader per term using randomized election timeouts and majority quorum votes."
        )

        val pdfBytes = createSyntheticPdf(
            pages = listOf("Raft guarantees safety across network partitions by requiring a majority quorum for log commitment.")
        )
        store.attachAndExtractFile(lecture.id, "raft.pdf", pdfBytes, "sources/raft.pdf")

        // Grant cloud text submission consent
        store.updatePreferences { it.copy(cloudTextSubmissionConsentGranted = true) }

        // Test single-retry recovery when first AI output has 0 correct options
        val fakeClient = FakeGeminiClient().apply {
            mode = FakeGeminiClient.BehaviorMode.FIRST_ATTEMPT_INVALID_THEN_FIXED_ON_RETRY
        }
        val aiJob = store.generateDraftsWithAi(
            lectureId = lecture.id,
            apiKey = "AIzaSyFakeTestKey123456",
            client = fakeClient
        )
        assertEquals(FileJobState.REVIEW_READY, aiJob.state)
        assertEquals(2, fakeClient.callCount) // 1 initial invalid + 1 retry with feedback

        // Verify drafts exist but are DRAFT status
        val drafts = store.observeDraftItems().first()
        assertTrue(drafts.isNotEmpty())
        assertTrue(drafts.all { it.approvalStatus == ApprovalStatus.DRAFT })

        // Attempt to start a quiz BEFORE approving any draft -> must return null / 0 eligible items!
        val (sessionBeforeApproval, resultBeforeApproval) = store.startNewQuizSession(
            QuizScopeSnapshot(primarySubjectId = subject.id, requestedCount = 5)
        )
        assertNull(sessionBeforeApproval)
        assertEquals(0, resultBeforeApproval.availableEligibleCount)

        // Approve one MCQ draft and one DROP draft
        val mcqDraft = drafts.first { it.kind == LearningItemKind.MCQ }
        val dropDraft = drafts.first { it.kind == LearningItemKind.DROP }
        store.approveItem(mcqDraft.id)
        store.approveItem(dropDraft.id)

        // Now start a quiz -> only the 1 approved MCQ is eligible (short pool disclosed)
        val (sessionAfterApproval, resultAfterApproval) = store.startNewQuizSession(
            QuizScopeSnapshot(primarySubjectId = subject.id, requestedCount = 5)
        )
        assertNotNull(sessionAfterApproval)
        assertEquals(1, resultAfterApproval.availableEligibleCount)
        assertTrue(resultAfterApproval.shortPoolDisclosed)

        // Delete the source file while choosing to retain approved items without source
        val attachedFile = store.observeSourceFilesForLecture(lecture.id).first().first()
        store.removeSourceFile(
            fileId = attachedFile.id,
            deleteGeneratedItems = true,
            retainApprovedItemsWithoutSource = true
        )

        val remainingLectureItems = store.observeLearningItemsForLecture(lecture.id).first()
        // Unapproved drafts from that file were removed; approved items were retained and marked manual/retained
        assertTrue(remainingLectureItems.any { it.id == mcqDraft.id && it.isManualOrRetainedWithoutSource })
    }

    @Test
    fun `BYOK key vault encrypts with AES-GCM, masks preview, and never leaks into export or clear data`() = runTest {
        val secretKey = AesGcmKeyVault.generateEphemeralSecretKey()
        val vault = AesGcmKeyVault({ secretKey })
        val rawKey = "AIzaSySuperSecretStudentKey9876"

        assertFalse(vault.hasApiKey())
        vault.saveApiKey(rawKey)
        assertTrue(vault.hasApiKey())
        assertEquals(rawKey, vault.getDecryptedApiKey())
        assertEquals("••••••••••••••••9876", vault.getMaskedKeyPreview())

        val clock = FakeClock(Instant.parse("2026-11-07T10:00:00Z"))
        val store = QuickDropLocalStore(clock, onClearKeystoreKey = { vault.removeApiKey() })
        store.createOrUpdateSubject(name = "Linear Algebra", colorKey = "teal", iconKey = "math")

        val exportedJson = store.exportLocalDataJson()
        assertFalse("Exported JSON must never contain the API key", exportedJson.contains(rawKey))
        assertTrue(exportedJson.contains("Linear Algebra"))

        store.clearAllLocalData()
        assertFalse("Clear Local Data must wipe the API key from the vault", vault.hasApiKey())
        assertNull(vault.getDecryptedApiKey())
        assertTrue(store.observeActiveSubjects().first().isEmpty())
    }

    @Test
    fun `Gemini client handles invalid key, 429 quota with retry-after, and offline states cleanly`() = runTest {
        val client = FakeGeminiClient()
        val chunk = SourceChunk(
            id = "c1",
            fileId = "f1",
            anchorKind = AnchorKind.PAGE,
            anchorIndex = 1,
            ordinal = 1,
            extractedText = "TCP uses a three-way handshake (SYN, SYN-ACK, ACK) to establish reliable connections.",
            tokenEstimate = 18,
            contentHash = "h1"
        )

        client.mode = FakeGeminiClient.BehaviorMode.QUOTA_429
        client.retryAfterSecondsOnQuota = 120
        val (quotaResp, quotaDrafts) = GeminiPipeline.generateAndValidateWithSingleRetry(
            client = client,
            apiKey = "valid-key",
            modelId = "gemini-2.5-flash",
            subjectName = "Networking",
            lectureTitle = "TCP",
            chunks = listOf(chunk),
            existingApprovedHashes = emptySet()
        )
        assertTrue(quotaResp is GeminiResponse.Failure)
        val failure = quotaResp as GeminiResponse.Failure
        assertEquals(AiErrorCode.QUOTA_EXCEEDED, failure.errorCode)
        assertEquals(120, failure.retryAfterSeconds)
        assertTrue(quotaDrafts.isEmpty())
    }

    // =========================================================================
    // 4. WEIGHTED SELECTION, QUIZ SNAPSHOTS, & SPACED REPETITION TESTS (M4)
    // =========================================================================

    @Test
    fun `quiz selector weights newest, due, and weak topics, caps single topic at 40 percent when 3+ topics exist, and avoids 24h repeats`() {
        val nowMs = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli()
        val clock = FakeClock(Instant.ofEpochMilli(nowMs))

        val topicNewest = Topic("t-new", "subj-1", null, "Topic Newest", "topic-newest", 0.5, nowMs - 86_400_000L)
        val topicDueWeak = Topic("t-due", "subj-1", null, "Topic Due Weak", "topic-due", 0.8, nowMs - 14 * 86_400_000L)
        val topicStableOld = Topic("t-old", "subj-1", null, "Topic Old", "topic-old", 0.5, nowMs - 30 * 86_400_000L)

        val dueWeakState = TopicReviewState(
            topicId = topicDueWeak.id,
            dueAtEpochMs = nowMs - 3600_000L, // Overdue
            correctCount = 1,
            attemptCount = 6,
            lastOutcome = ResponseOutcome.INCORRECT
        )
        val stableState = TopicReviewState(
            topicId = topicStableOld.id,
            dueAtEpochMs = nowMs + 10 * 86_400_000L,
            correctCount = 10,
            attemptCount = 10,
            lastOutcome = ResponseOutcome.CORRECT
        )

        val scoreDueWeak = QuizSelector.computeTopicScore(topicDueWeak, dueWeakState, nowMs)
        val scoreStableOld = QuizSelector.computeTopicScore(topicStableOld, stableState, nowMs)
        assertTrue("Overdue weak topic must score higher than stable non-due old topic", scoreDueWeak > scoreStableOld)

        fun makeMcqCandidate(id: String, topic: Topic, state: TopicReviewState?, isNewest: Boolean, recentCorrectMs: Long? = null): SelectionCandidate {
            val item = LearningItem(
                id = id,
                topicId = topic.id,
                kind = LearningItemKind.MCQ,
                promptOrBody = "Question prompt for $id",
                explanation = "Explanation for $id",
                difficulty = Difficulty.MEDIUM,
                approvalStatus = ApprovalStatus.APPROVED,
                isManualOrRetainedWithoutSource = true,
                contentHash = "hash-$id",
                options = listOf(
                    AnswerOption("o1-$id", id, 1, "Correct", true),
                    AnswerOption("o2-$id", id, 2, "Wrong", false)
                ),
                createdAtEpochMs = nowMs,
                updatedAtEpochMs = nowMs
            )
            return SelectionCandidate(
                item = item,
                topic = topic,
                reviewState = state,
                isNewestTopicOfPrimarySubject = isNewest,
                isPrimarySubject = true,
                lastAnsweredCorrectlyAtEpochMs = recentCorrectMs
            )
        }

        val candidates = buildList {
            for (i in 1..6) add(makeMcqCandidate("new-$i", topicNewest, null, isNewest = true))
            for (i in 1..5) add(makeMcqCandidate("due-$i", topicDueWeak, dueWeakState, isNewest = false))
            for (i in 1..5) add(makeMcqCandidate("old-$i", topicStableOld, stableState, isNewest = false))
            // Add one answered correctly 2 hours ago -> must be excluded by 24h rule
            add(makeMcqCandidate("recent-correct", topicNewest, null, isNewest = true, recentCorrectMs = nowMs - 2 * 3600_000L))
        }

        val result = QuizSelector.selectQuestions(
            candidates = candidates,
            scope = QuizScopeSnapshot(
                primarySubjectId = "subj-1",
                requestedCount = 10,
                difficultyFilter = QuizDifficultyFilter.MEDIUM
            ),
            validSourceFileIds = emptySet(),
            clock = clock,
            random = Random(42)
        )

        assertEquals(10, result.selectedItems.size)
        assertFalse("Recently correct item (<24h) must not be selected", result.selectedItems.any { it.id == "recent-correct" })
        // With 3 topics and 10 questions, the 40% topic cap limits each topic to at most 4 items
        result.topicBreakdown.values.forEach { countForTopic ->
            assertTrue("Topic count ($countForTopic) must respect 40% cap (<=4)", countForTopic <= 4)
        }
    }

    @Test
    fun `spaced repetition scheduler deterministically computes intervals, lapses, skips, and 3-day + 7-day Stable mastery`() {
        val clock = FakeClock(Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("UTC"))
        val topicId = "topic-compilers"

        // 1. Skip changes neither accuracy nor interval
        val afterSkip = SpacedRepetitionScheduler.recordOutcome(
            currentState = null,
            topicId = topicId,
            outcome = ResponseOutcome.SKIPPED,
            clock = clock
        )
        assertEquals(0, afterSkip.attemptCount)
        assertEquals(TopicMasteryStatus.NEW, SpacedRepetitionScheduler.resolveMasteryStatus(afterSkip, clock))

        // 2. First correct review (Day 1: 2026-11-01) -> interval = 1.0 day
        val rev1 = SpacedRepetitionScheduler.recordOutcome(
            currentState = afterSkip,
            topicId = topicId,
            outcome = ResponseOutcome.CORRECT,
            confidence = ReviewConfidence.HIGH,
            clock = clock
        )
        assertEquals(1.0, rev1.stabilityDays, 0.001)
        assertEquals(1, rev1.repetitions)
        clock.advanceSeconds(3600) // 1 hour later, not due yet
        assertEquals(TopicMasteryStatus.LEARNING, SpacedRepetitionScheduler.resolveMasteryStatus(rev1, clock))

        // 3. Second correct review on Day 2 (2026-11-02) with HIGH confidence -> multiplier 2.0 -> 2.0 days
        clock.setInstant(Instant.parse("2026-11-02T12:00:00Z"))
        val rev2 = SpacedRepetitionScheduler.recordOutcome(
            currentState = rev1,
            topicId = topicId,
            outcome = ResponseOutcome.CORRECT,
            confidence = ReviewConfidence.HIGH,
            clock = clock
        )
        assertEquals(2.0, rev2.stabilityDays, 0.001)
        assertEquals(2, rev2.distinctCorrectReviewDaysIso.size)
        assertFalse(rev2.hasSevenDaySpacedCorrect)

        // 4. Third correct review on Day 10 (2026-11-10, 8 days after Day 2 >= 7 days) -> reaches STABLE!
        clock.setInstant(Instant.parse("2026-11-10T12:00:00Z"))
        val rev3 = SpacedRepetitionScheduler.recordOutcome(
            currentState = rev2,
            topicId = topicId,
            outcome = ResponseOutcome.CORRECT,
            confidence = ReviewConfidence.HIGH,
            clock = clock
        )
        assertTrue(rev3.hasSevenDaySpacedCorrect)
        assertEquals(3, rev3.distinctCorrectReviewDaysIso.size)
        assertEquals(TopicMasteryStatus.STABLE, SpacedRepetitionScheduler.resolveMasteryStatus(rev3, clock))

        // 5. Incorrect response while still studying -> increments lapse, resets repetitions, due in 10 minutes
        val afterLapse = SpacedRepetitionScheduler.recordOutcome(
            currentState = rev3,
            topicId = topicId,
            outcome = ResponseOutcome.INCORRECT,
            stillStudying = true,
            clock = clock
        )
        assertEquals(1, afterLapse.lapses)
        assertEquals(0, afterLapse.repetitions)
        assertEquals(clock.now().toEpochMilli() + 10 * 60_000L, afterLapse.dueAtEpochMs)
    }

    @Test
    fun `historical quiz response snapshot survives subsequent item edits and completion is idempotent`() = runTest {
        val clock = FakeClock(Instant.parse("2026-11-07T12:00:00Z"))
        val store = QuickDropLocalStore(clock)
        val subj = store.createOrUpdateSubject(name = "Databases", colorKey = "amber", iconKey = "db")

        val item = store.createManualItem(
            subjectId = subj.id,
            topicName = "ACID Transactions",
            kind = LearningItemKind.MCQ,
            promptOrBody = "Original prompt: What does the I in ACID stand for?",
            explanation = "Original explanation: Isolation ensures concurrent transactions do not interfere.",
            difficulty = Difficulty.MEDIUM,
            options = listOf("Isolation" to true, "Indexing" to false)
        ).getOrThrow()

        val (session, _) = store.startNewQuizSession(QuizScopeSnapshot(primarySubjectId = subj.id, requestedCount = 5))
        assertNotNull(session)

        val correctOptId = item.options.first { it.isCorrect }.id
        store.recordAnswer(
            sessionId = session!!.id,
            itemId = item.id,
            selectedOptionId = correctOptId,
            latencyMs = 1200L
        )

        val completed1 = store.completeQuizSession(session.id)
        val completed2 = store.completeQuizSession(session.id) // Idempotent repeat call
        assertEquals(QuizSessionStatus.COMPLETED, completed1.status)
        assertEquals(1, completed2.correctCount)

        val topicState = store.observeTopicReviewStates().first()[item.topicId]!!
        assertEquals("Idempotent completion must not double-count attempts", 1, topicState.attemptCount)

        // Now edit the learning item to v2
        val edited = store.editAndRevalidateItem(
            itemId = item.id,
            newPromptOrBody = "Edited v2 prompt: Which ACID property guarantees serializable-like execution?",
            newExplanation = "Edited v2 explanation.",
            newDifficulty = Difficulty.HARD,
            newOptions = listOf("Isolation (Edited)" to true, "Durability" to false)
        ).getOrThrow()
        assertEquals(2, edited.version)

        // Verify the historical quiz response snapshot still preserves v1 prompt & options
        val historicalResponses = store.observeResponsesForSession(session.id).first()
        assertEquals(1, historicalResponses.size)
        assertEquals(1, historicalResponses.first().itemVersion)
        assertEquals("Original prompt: What does the I in ACID stand for?", historicalResponses.first().promptSnapshot)
    }

    // =========================================================================
    // 5. NOTIFICATIONS, QUIET HOURS, & STALE DISCARD ACCEPTANCE TESTS (M5)
    // =========================================================================

    @Test
    fun `reminder policy handles permission denial, overnight quiet hours, stale class discard, and deduplication`() {
        val zone = ZoneId.of("Europe/Berlin")
        val prefs = UserPreferences(
            classRemindersEnabled = true,
            quietHoursEnabled = true,
            quietHoursStartMinuteOfDay = 22 * 60, // 22:00
            quietHoursEndMinuteOfDay = 7 * 60     // 07:00
        )

        val slot = ClassSlot(
            id = "slot-early",
            subjectId = "subj-math",
            weekdayMask = 1, // Monday
            startMinuteOfDay = 7 * 60 + 10, // 07:10 AM
            endMinuteOfDay = 8 * 60 + 10,
            zoneId = zone.id,
            reminderEnabled = true,
            reminderLeadMinutes = 20 // Ideal trigger: 06:50 AM (inside overnight quiet hours 22:00-07:00)
        )

        val clock = FakeClock(Instant.parse("2026-11-09T05:00:00Z"), zone) // 06:00 AM local Berlin time
        val occurrence = TimetableCalculator.computeOccurrences(
            slots = listOf(slot),
            exceptions = emptyList(),
            fromInstant = clock.now(),
            daysAhead = 2,
            displayZone = zone
        ).first()

        // 1. Permission denied -> safe suppression
        val deniedEval = ReminderPolicy.evaluateClassReminder(
            occurrence = occurrence,
            preferences = prefs,
            notificationPermissionGranted = false,
            alreadyDeliveredOccurrenceIds = emptySet(),
            clock = clock
        )
        assertEquals(ReminderDecisionKind.SUPPRESS_DISABLED_OR_NO_PERMISSION, deniedEval.decision)

        // 2. Trigger at 06:50 falls in quiet hours (22:00-07:00), quiet hours end at 07:00 which is BEFORE class start (07:10) -> defer to 07:00!
        val deferredEval = ReminderPolicy.evaluateClassReminder(
            occurrence = occurrence,
            preferences = prefs,
            notificationPermissionGranted = true,
            alreadyDeliveredOccurrenceIds = emptySet(),
            clock = clock
        )
        assertEquals(ReminderDecisionKind.DEFER_UNTIL_QUIET_HOURS_END, deferredEval.decision)
        assertEquals(7, deferredEval.scheduledTriggerInstant!!.atZone(zone).hour)
        assertEquals(0, deferredEval.scheduledTriggerInstant!!.atZone(zone).minute)

        // 3. Deduplication: if occurrenceId was already delivered, suppress duplicate
        val duplicateEval = ReminderPolicy.evaluateClassReminder(
            occurrence = occurrence,
            preferences = prefs,
            notificationPermissionGranted = true,
            alreadyDeliveredOccurrenceIds = setOf(occurrence.occurrenceId),
            clock = clock
        )
        assertEquals(ReminderDecisionKind.SUPPRESS_DUPLICATE_OCCURRENCE, duplicateEval.decision)

        // 4. If a class starts at 06:30 (before quiet hours end at 07:00), deferring to 07:00 would be past class start -> discard!
        val slotDuringQuiet = slot.copy(id = "slot-0630", startMinuteOfDay = 6 * 60 + 30, endMinuteOfDay = 7 * 60 + 30)
        val occDuringQuiet = TimetableCalculator.computeOccurrences(
            slots = listOf(slotDuringQuiet),
            exceptions = emptyList(),
            fromInstant = clock.now(),
            daysAhead = 2,
            displayZone = zone
        ).first()
        val staleEval = ReminderPolicy.evaluateClassReminder(
            occurrence = occDuringQuiet,
            preferences = prefs,
            notificationPermissionGranted = true,
            alreadyDeliveredOccurrenceIds = emptySet(),
            clock = clock
        )
        assertEquals(ReminderDecisionKind.SUPPRESS_STALE_PAST_CLASS_START, staleEval.decision)
    }

    // --- Synthetic file builders for tests ---

    private fun createSyntheticPdf(pages: List<String>, encryptedPassword: String? = null): ByteArray {
        PDDocument().use { doc ->
            for (text in pages) {
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 700f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            if (encryptedPassword != null) {
                val ap = AccessPermission()
                val spp = StandardProtectionPolicy(encryptedPassword, encryptedPassword, ap)
                spp.encryptionKeyLength = 128
                doc.protect(spp)
            }
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        }
    }

    private fun createBlankMultiPagePdf(pageCount: Int): ByteArray {
        PDDocument().use { doc ->
            repeat(pageCount) {
                doc.addPage(PDPage())
            }
            val baos = ByteArrayOutputStream()
            doc.save(baos)
            return baos.toByteArray()
        }
    }

    private fun createSyntheticPptx(slides: Map<Int, String>, includeChart: Boolean = false): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("<Types></Types>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((num, text) in slides) {
                zos.putNextEntry(ZipEntry("ppt/slides/slide$num.xml"))
                val xml = "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><a:t>$text</a:t></p:sld>"
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            if (includeChart) {
                zos.putNextEntry(ZipEntry("ppt/charts/chart1.xml"))
                zos.write("<c:chartSpace/>".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
