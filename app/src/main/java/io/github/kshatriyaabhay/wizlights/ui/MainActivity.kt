package io.github.kshatriyaabhay.wizlights.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.kshatriyaabhay.wizlights.WizApp
import io.github.kshatriyaabhay.wizlights.theme.StockThemes

class MainActivity : ComponentActivity() {
    private enum class Screen { Lights, Settings }

    private var screen by mutableStateOf(Screen.Lights)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getString(KEY_SCREEN) == Screen.Settings.name) screen = Screen.Settings
        handle(intent)
        val repo = WizApp.of(this).repository
        setContent {
            val settings by repo.settings.collectAsState()
            val palette = remember(settings.themeName) { StockThemes.palette(settings.themeName) }
            WizTheme(palette) {
                when (screen) {
                    Screen.Lights -> LightsScreen(repo, onSettings = { screen = Screen.Settings })
                    Screen.Settings -> {
                        BackHandler { screen = Screen.Lights }
                        SettingsScreen(repo, onBack = { screen = Screen.Lights })
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
