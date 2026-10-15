package com.ivy.time.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel

/** A simple base ViewModel utilizing Compose' reactivity (same as in Ivy Wallet). */
@Stable
abstract class ComposeViewModel<UiState, UiEvent> : ViewModel() {
    @Composable
    abstract fun uiState(): UiState

    abstract fun onEvent(event: UiEvent)
}
