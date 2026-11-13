package io.github.kshatriyaabhay.wizlights.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kshatriyaabhay.wizlights.WizApp

class MainActivity : ComponentActivity() {
    private enum class Screen { Lights, Settings, Themes }

    private var screen by mutableStateOf(Screen.Lights)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(KEY_SCREEN)?.let { s -> Screen.entries.firstOrNull { it.name == s } }?.let { screen = it }
        handle(intent)
        val repo = WizApp.of(this).repository
        setContent {
            // Draw nothing (the window background) until the saved theme is known, rather than one
            // frame in the default theme. DataStore reads its small file in a few milliseconds.
            val settings = repo.settingsStore.ready.collectAsState().value ?: return@setContent
            // Material You follows the system's dark mode; the key makes it recompute on a switch.
            val dark = isSystemInDarkTheme()
            val palette = remember(settings.themeName, dark) { Themes.palette(this, settings.themeName) }
            WizTheme(palette) {
                when (screen) {
                    Screen.Lights -> LightsScreen(repo, onSettings = { screen = Screen.Settings })
                    Screen.Settings -> {
                        BackHandler { screen = Screen.Lights }
                        SettingsScreen(repo, onBack = { screen = Screen.Lights }, onThemes = { screen = Screen.Themes })
                    }
                    Screen.Themes -> {
                        BackHandler { screen = Screen.Settings }
                        ThemesScreen(repo, onBack = { screen = Screen.Settings })
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, screen.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_SETTINGS) screen = Screen.Settings
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_SETTINGS = "settings"
        private const val KEY_SCREEN = "screen"
    }
}
