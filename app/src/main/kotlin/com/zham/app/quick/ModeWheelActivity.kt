package com.zham.app.quick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zham.design.system.ZhamAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The quick-mode wheel: a see-through screen over whatever was open, opened from the Quick Settings
 * tile. One tap on a mode starts its timer and closes the wheel; a tap outside closes it.
 */
@AndroidEntryPoint
class ModeWheelActivity : ComponentActivity() {

    private val viewModel: ModeWheelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setContent {
            ZhamAppTheme(dark = isSystemInDarkTheme()) {
                val state = viewModel.uiState()
                LaunchedEffect(state.done) { if (state.done) finish() }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { finish() },
                    contentAlignment = Alignment.Center,
                ) {
                    val open = state.openCategory
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        Text(
                            when {
                                open != null -> open.label
                                state.runningLabel != null -> "Now: ${state.runningLabel}  ${state.elapsed}"
                                else -> "What are you doing?"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        RadialWheel(
                            items = if (open == null) state.categories else {
                                // One extra disc at the front for "no project", then the projects.
                                listOf(WheelItem(NO_PROJECT, "No project", open.color, open.glyph)) + state.projects
                            },
                            onPick = { item ->
                                if (open == null) {
                                    viewModel.onEvent(ModeWheelEvent.PickCategory(item))
                                } else {
                                    viewModel.onEvent(ModeWheelEvent.PickProject(item.id.takeIf { it != NO_PROJECT }))
                                }
                            },
                            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                        ) {
                            val label = when {
                                open != null -> "Back"
                                state.runningLabel != null -> "Stop"
                                else -> "Close"
                            }
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (state.runningLabel != null && open == null) MaterialTheme.colorScheme.error
                                        else Color.White.copy(alpha = 0.18f)
                                    )
                                    .clickable {
                                        when {
                                            open != null -> viewModel.onEvent(ModeWheelEvent.Back)
                                            state.runningLabel != null -> viewModel.onEvent(ModeWheelEvent.Stop)
                                            else -> finish()
                                        }
                                    }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val NO_PROJECT = -1L
    }
}
