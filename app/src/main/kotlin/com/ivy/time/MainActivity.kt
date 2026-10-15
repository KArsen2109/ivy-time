package com.ivy.time

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ivy.design.system.IvyMaterial3Theme
import com.ivy.time.data.SettingsStore
import com.ivy.time.data.ThemeMode
import com.ivy.time.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsStore.settings.collectAsState(null)
            val theme = settings?.theme ?: ThemeMode.System
            IvyMaterial3Theme(
                isTrueBlack = theme == ThemeMode.AmoledDark,
                dark = when (theme) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark, ThemeMode.AmoledDark -> true
                },
            ) {
                MainScreen()
            }
        }
    }
}
