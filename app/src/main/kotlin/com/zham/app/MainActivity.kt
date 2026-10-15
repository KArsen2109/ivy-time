package com.zham.app

import androidx.compose.runtime.DisposableEffect
import androidx.activity.SystemBarStyle
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zham.design.system.ZhamAppTheme
import com.zham.data.SettingsStore
import com.zham.app.day.SelectedDay
import com.zham.data.ThemeMode
import com.zham.app.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var selectedDay: SelectedDay

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsStore.settings.collectAsState(null)
            val theme = settings?.theme ?: ThemeMode.System
            val dark = when (theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark, ThemeMode.AmoledDark -> true
            }
            // The bars' icons follow the app's own theme, which can differ from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            ZhamAppTheme(
                dark = dark,
                amoled = theme == ThemeMode.AmoledDark,
            ) {
                MainScreen(selectedDay)
            }
        }
    }
}
