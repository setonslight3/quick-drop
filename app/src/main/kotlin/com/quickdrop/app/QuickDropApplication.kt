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
