package com.ivy.time

import android.app.Application
import androidx.compose.ui.graphics.toArgb
import com.ivy.design.system.CategoryPalette
import com.ivy.time.data.SettingsStore
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class IvyTimeApp : Application() {

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var settingsStore: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedDefaultCategories()
    }

    /**
     * An empty app has nothing to tap, so the first launch fills in a starter set.
     * It runs once; deleting the categories afterwards is final.
     */
    private fun seedDefaultCategories() {
        scope.launch {
            if (settingsStore.settings.first().onboardingDone) return@launch
            defaultCategories().forEach { categoryRepository.save(it) }
            settingsStore.setOnboardingDone(true)
        }
    }

    private fun defaultCategories(): List<Category> = listOf(
        "Work" to "work",
        "Study" to "study",
        "Sport" to "sport",
        "Rest" to "rest",
        "Social" to "social",
        "Chores" to "home",
    ).mapIndexed { index, (name, icon) ->
        Category(
            id = 0,
            name = name,
            color = CategoryPalette[index % CategoryPalette.size].toArgb(),
            icon = icon,
            orderNum = index.toDouble(),
        )
    }
}
