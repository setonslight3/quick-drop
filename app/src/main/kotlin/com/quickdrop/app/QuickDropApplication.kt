package com.quickdrop.app

import android.app.Application
import android.util.Log
import com.quickdrop.app.integration.gemini.DirectHttpsGeminiClient
import com.quickdrop.app.integration.notifications.QuickDropNotificationScheduler
import com.quickdrop.app.security.AndroidKeystoreApiKeyVault
import com.quickdrop.core.domain.FakeGeminiClient
import com.quickdrop.core.domain.GeminiClient
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.security.ApiKeyVault
import com.quickdrop.core.time.AppClock
import com.quickdrop.core.time.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickDropApplication : Application() {

    companion object {
        const val VERSION_NAME = "0.0.1"
        const val VERSION_CODE = 1
        const val TAG = "QuickDrop"
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var clock: AppClock
        private set

    lateinit var apiKeyVault: ApiKeyVault
        private set

    lateinit var localStore: QuickDropLocalStore
        private set

    lateinit var geminiClient: GeminiClient
        private set

    val fakeGeminiClient = FakeGeminiClient()
    var useFakeGeminiForOfflineDemo: Boolean = false

    lateinit var notificationScheduler: QuickDropNotificationScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "=======================================================")
        Log.i(TAG, "🚀 QuickDrop Mobile v$VERSION_NAME (Build $VERSION_CODE) Initialized")
        Log.i(TAG, "📦 Architecture: Local-First Spaced Repetition & Timetable System")
        Log.i(TAG, "🔐 Security: Encrypted SQLite Keystore • BYOK Direct HTTPS")
        Log.i(TAG, "=======================================================")

        clock = SystemClock()
        apiKeyVault = AndroidKeystoreApiKeyVault(this)
        localStore = QuickDropLocalStore(
            clock = clock,
            onClearKeystoreKey = { apiKeyVault.removeApiKey() }
        )
        geminiClient = DirectHttpsGeminiClient()
        notificationScheduler = QuickDropNotificationScheduler(this, clock)
        notificationScheduler.ensureChannelsCreated()

        // Auto-seed syllabus on first mobile launch so app is never blank
        appScope.launch {
            seedInitialDataIfEmpty()
        }
    }

    private suspend fun seedInitialDataIfEmpty() {
        val existing = localStore.observeActiveSubjects().first()
        if (existing.isNotEmpty()) return

        Log.i(TAG, "🌱 First launch detected: Seeding Cybersecurity 200L academic syllabus...")

        // Course 1: SEN 201
        val sen = localStore.createOrUpdateSubject(
            name = "SEN 201 - Software Engineering",
            colorKey = "violet",
            iconKey = "code",
            term = "First Semester 2026/2027",
            instructor = "Prof. Adebayo"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-sen201",
                subjectId = sen.id,
                weekdayMask = 1, // Monday
                startMinuteOfDay = 10 * 60,
                endMinuteOfDay = 12 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true,
                reminderEnabled = true,
                reminderLeadMinutes = 15
            )
        )
        val senLec = localStore.createOrUpdateLecture(
            subjectId = sen.id,
            title = "Agile Architecture & System Design",
            localDateIso = clock.localDate().toString(),
            topicLabel = "Agile Architecture",
            notes = "Software architecture defines structural design patterns. Component coupling should be minimized through clean dependency inversion and domain-driven design boundaries."
        )
        localStore.createManualItem(
            subjectId = sen.id,
            topicName = "Agile Architecture",
            lectureId = senLec.id,
            kind = com.quickdrop.core.model.LearningItemKind.DROP,
            promptOrBody = "Dependency Inversion requires high-level modules to depend on abstract interfaces rather than concrete implementations.",
            explanation = "Core SOLID Principle for extensible system design.",
            difficulty = com.quickdrop.core.model.Difficulty.EASY
        )
        localStore.createManualItem(
            subjectId = sen.id,
            topicName = "Agile Architecture",
            lectureId = senLec.id,
            kind = com.quickdrop.core.model.LearningItemKind.MCQ,
            promptOrBody = "Which SOLID design principle specifically minimizes tight coupling between high-level policy and low-level details?",
            explanation = "The Dependency Inversion Principle (DIP) states that high-level modules should not depend on low-level modules; both should depend on abstractions.",
            difficulty = com.quickdrop.core.model.Difficulty.MEDIUM,
            options = listOf(
                "Dependency Inversion Principle (DIP)" to true,
                "Single Responsibility Principle (SRP)" to false,
                "Interface Segregation Principle (ISP)" to false,
                "Liskov Substitution Principle (LSP)" to false
            )
        )

        // Course 2: CYB 203
        val cyb203 = localStore.createOrUpdateSubject(
            name = "CYB 203 - Cybercrime & Law",
            colorKey = "rose",
            iconKey = "security",
            term = "First Semester 2026/2027",
            instructor = "Dr. Okafor"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cyb203-mon",
                subjectId = cyb203.id,
                weekdayMask = 1, // Monday
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 13 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cyb203-tue",
                subjectId = cyb203.id,
                weekdayMask = 2, // Tuesday
                startMinuteOfDay = 11 * 60,
                endMinuteOfDay = 12 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )
        val cyb203Lec = localStore.createOrUpdateLecture(
            subjectId = cyb203.id,
            title = "Digital Forensics & Evidence Admissibility",
            localDateIso = clock.localDate().toString(),
            topicLabel = "Chain of Custody",
            notes = "Forensic evidence must maintain an unbroken chain of custody with cryptographic hash verification (SHA-256) to be admissible in criminal court proceedings."
        )
        localStore.createManualItem(
            subjectId = cyb203.id,
            topicName = "Chain of Custody",
            lectureId = cyb203Lec.id,
            kind = com.quickdrop.core.model.LearningItemKind.DROP,
            promptOrBody = "Cryptographic hashing proves digital evidence was not altered between seizure and court presentation.",
            explanation = "Evidence integrity verification in judicial digital forensics.",
            difficulty = com.quickdrop.core.model.Difficulty.EASY
        )

        // Course 3: WU-CYB 205
        val cyb205 = localStore.createOrUpdateSubject(
            name = "WU-CYB 205 - Data Security",
            colorKey = "emerald",
            iconKey = "security",
            term = "First Semester 2026/2027",
            instructor = "Prof. Johnson"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cyb205",
                subjectId = cyb205.id,
                weekdayMask = 1, // Monday
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 16 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )

        // Course 4: IFT 211
        val ift = localStore.createOrUpdateSubject(
            name = "IFT 211 - Digital Logic Design",
            colorKey = "amber",
            iconKey = "memory",
            term = "First Semester 2026/2027",
            instructor = "Dr. Bello"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-ift211",
                subjectId = ift.id,
                weekdayMask = 2, // Tuesday
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )

        // Course 5: COS 201
        val cos = localStore.createOrUpdateSubject(
            name = "COS 201 - Computer Programming I",
            colorKey = "cyan",
            iconKey = "code",
            term = "First Semester 2026/2027",
            instructor = "Dr. Eze"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cos201-lec",
                subjectId = cos.id,
                weekdayMask = 2, // Tuesday
                startMinuteOfDay = 12 * 60,
                endMinuteOfDay = 13 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cos201-lab",
                subjectId = cos.id,
                weekdayMask = 2, // Tuesday
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 16 * 60,
                zoneId = clock.zoneId().id,
                location = "Lab 2",
                enabled = true
            )
        )

        // Course 6: CYB 201
        val cyb201 = localStore.createOrUpdateSubject(
            name = "CYB 201 - Intro to Cybersecurity",
            colorKey = "cyan",
            iconKey = "shield",
            term = "First Semester 2026/2027",
            instructor = "Dr. Williams"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-cyb201",
                subjectId = cyb201.id,
                weekdayMask = 8, // Thursday
                startMinuteOfDay = 8 * 60,
                endMinuteOfDay = 10 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )

        // Course 7: ENT 211
        val ent = localStore.createOrUpdateSubject(
            name = "ENT 211 - Entrepreneurship",
            colorKey = "rose",
            iconKey = "business",
            term = "First Semester 2026/2027",
            instructor = "Prof. Danladi"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-ent211",
                subjectId = ent.id,
                weekdayMask = 8, // Thursday
                startMinuteOfDay = 11 * 60,
                endMinuteOfDay = 13 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )

        // Course 8: WU 211
        val wu211 = localStore.createOrUpdateSubject(
            name = "WU 211 - Leadership in Action",
            colorKey = "violet",
            iconKey = "school",
            term = "First Semester 2026/2027",
            instructor = "Dean's Office"
        )
        localStore.upsertClassSlot(
            com.quickdrop.core.model.ClassSlot(
                id = "slot-wu211",
                subjectId = wu211.id,
                weekdayMask = 8, // Thursday
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 16 * 60,
                zoneId = clock.zoneId().id,
                location = "LH 30",
                enabled = true
            )
        )

        apiKeyVault.saveApiKey("AIzaSyDemoStudentKey1234")
        rescheduleRemindersAsync()
        Log.i(TAG, "✅ Syllabus successfully seeded with 8 courses and 10 class slots!")
    }

    fun activeGeminiClient(): GeminiClient =
        if (useFakeGeminiForOfflineDemo) fakeGeminiClient else geminiClient

    fun rescheduleRemindersAsync() {
        appScope.launch {
            val occurrences = localStore.getUpcomingOccurrences(daysAhead = 14)
            val prefs = localStore.observePreferences().first()
            notificationScheduler.rebuildUpcomingClassReminders(occurrences, prefs)
        }
    }
}
