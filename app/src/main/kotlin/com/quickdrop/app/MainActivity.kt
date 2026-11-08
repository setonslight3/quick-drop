package com.quickdrop.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quickdrop.app.ui.QuickDropAppShell
import com.quickdrop.app.ui.theme.QuickDropTheme
import com.quickdrop.core.model.UserPreferences

class MainActivity : ComponentActivity() {

    private var deepLinkUriState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLinkUriState = intent?.data?.toString()

        val app = application as QuickDropApplication

        setContent {
            val preferences by app.localStore.observePreferences().collectAsState(initial = UserPreferences())
            QuickDropTheme(themePreference = preferences.themePreference) {
                QuickDropAppShell(
                    app = app,
                    initialDeepLinkUri = deepLinkUriState,
                    onConsumeDeepLink = { deepLinkUriState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState = intent.data?.toString()
    }
}
