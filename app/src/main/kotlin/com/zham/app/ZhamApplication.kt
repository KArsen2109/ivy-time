package com.zham.app

import com.zham.design.system.ZhamEmojis
import com.zham.design.system.BrightPalette
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.MoneyCategory
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.Account
import android.app.Application
import androidx.compose.ui.graphics.toArgb
import com.zham.design.system.CategoryPalette
import com.zham.app.reminders.ReminderScheduler
import com.zham.data.SettingsStore
import com.zham.domain.model.PlanRepository
import kotlinx.coroutines.flow.combine
import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ZhamApplication : Application() {

    @Inject
    lateinit var categoryRepository: CategoryRepository

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var planRepository: PlanRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var moneyCategoryRepository: MoneyCategoryRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        seedDefaultCategories()
        seedMoney()
        keepRemindersCurrent()
    }

    /**
     * Money starts with one cash account in the phone's own currency and the usual categories, so the
     * first expense takes seconds. Like the time categories it runs once, and what is deleted stays deleted.
     */
    private fun seedMoney() {
        scope.launch {
            runCatching {
                if (settingsStore.settings.first().moneySeeded) return@launch
                if (accountRepository.observeAccounts().first().isEmpty()) {
                    val code = runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode }
                        .getOrDefault("USD")
                    accountRepository.save(
                        Account(0, "Cash", code, CategoryPalette[2].toArgb(), ZhamEmojis.key("💵"), 0, 0.0)
                    )
                }
                if (moneyCategoryRepository.observeCategories().first().isEmpty()) {
                    listOf(
                        "Food" to "🍔", "Transport" to "🚌", "Home" to "🏠", "Health" to "💊",
                        "Shopping" to "🛍️", "Fun" to "🎮", "Salary" to "💰", "Other" to "⭐",
                    ).forEachIndexed { index, (name, emoji) ->
                        moneyCategoryRepository.save(
                            MoneyCategory(0, name, BrightPalette[(index * 3) % BrightPalette.size].toArgb(), ZhamEmojis.key(emoji), index.toDouble())
                        )
                    }
                }
                settingsStore.setMoneySeeded(true)
            }
        }
    }

    /** Re-plans the reminder alarm at start and whenever a block or tick-off changes. */
    private fun keepRemindersCurrent() {
        scope.launch {
            runCatching {
                combine(planRepository.observePlans(), planRepository.observeCompletions()) { _, _ -> }
                    .collect { runCatching { reminderScheduler.sync() } }
            }
        }
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
