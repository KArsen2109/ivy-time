package com.zham.app.quick

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.TimeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tile in the phone's quick settings. It shows whether a timer runs and, when tapped, opens the
 * mode wheel over whatever is on screen, without opening the app itself.
 */
@AndroidEntryPoint
class ZhamTileService : TileService() {

    @Inject
    lateinit var timeRepository: TimeRepository

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val running = runCatching { timeRepository.findRunning() }.getOrNull()
            val name = running?.let { entry ->
                entry.note.ifBlank {
                    runCatching { categoryRepository.observeCategories() }.getOrNull()
                        ?.let { flow -> flow.firstOrNull().orEmpty() }
                        ?.firstOrNull { it.id == entry.categoryId }?.name
                } ?: "Tracking"
            }
            qsTile?.apply {
                state = if (running != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                label = "Zham"
                if (Build.VERSION.SDK_INT >= 29) subtitle = name ?: "Choose a mode"
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ModeWheelActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
