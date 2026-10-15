package com.ivy.time

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ivy.design.system.IvyMaterial3Theme
import com.ivy.time.main.MainScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IvyMaterial3Theme(isTrueBlack = false) {
                MainScreen()
            }
        }
    }
}
