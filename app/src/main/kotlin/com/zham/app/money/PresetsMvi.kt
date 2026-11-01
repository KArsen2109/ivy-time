package com.zham.app.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.PickerItem
import com.zham.app.ui.message
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.Preset
import com.zham.domain.model.PresetKind
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.usecase.ApplyPresetUseCase
import com.zham.domain.usecase.formatMinor
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.parseAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

@Immutable
data class PresetItem(
    val id: Long,
    val kind: PresetKind,
    val name: String,
    val title: String,
    val summary: String,
    val categoryId: Long?,
    val projectId: Long?,
    val accountId: Long?,
    val amount: String,
    val hours: String,
    val minutes: String,
)

@Immutable
data class PresetsState(
    val presets: ImmutableList<PresetItem>,
    val accounts: ImmutableList<PickerItem>,
    val moneyCategories: ImmutableList<PickerItem>,
    val timeCategories: ImmutableList<PickerItem>,
    val projects: ImmutableList<Pair<Long?, PickerItem>>,
    val error: String?,
)

sealed interface PresetsEvent {
    data class Save(
        val id: Long,
        val kind: PresetKind,
        val name: String,
        val title: String,
        val categoryId: Long?,
        val projectId: Long?,
        val accountId: Long?,
        val amount: String,
        val hours: String,
        val minutes: String,
    ) : PresetsEvent

    data class Delete(val id: Long) : PresetsEvent
}

@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val presetRepository: PresetRepository,
    private val accountRepository: AccountRepository,
    private val moneyCategoryRepository: MoneyCategoryRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
) : ComposeViewModel<PresetsState, PresetsEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): PresetsState {
        val presets by remember { presetRepository.observePresets() }.collectAsState(emptyList())
        val accounts by remember { accountRepository.observeAccounts() }.collectAsState(emptyList())
        val money by remember { moneyCategoryRepository.observeCategories() }.collectAsState(emptyList())
        val time by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val accountsById = accounts.associateBy { it.id }
        val moneyById = money.associateBy { it.id }
        val timeById = time.associateBy { it.id }

        return PresetsState(
            presets = presets.map { preset ->
                val money = preset.kind != PresetKind.Activity
                PresetItem(
                    preset.id, preset.kind, preset.name, preset.title,
                    summary = if (money) {
                        listOfNotNull(
                            formatMinor(preset.amountMinor, accountsById[preset.accountId]?.currency.orEmpty()),
                            moneyById[preset.categoryId]?.name,
                            accountsById[preset.accountId]?.name,
                        ).joinToString(" · ")
                    } else {
                        listOfNotNull(
                            Duration.ofMinutes(preset.durationMinutes.toLong()).formatShort(),
                            timeById[preset.categoryId]?.name,
                        ).joinToString(" · ")
                    },
                    preset.categoryId, preset.projectId, preset.accountId,
                    formatMinor(preset.amountMinor).replace(" ", ""),
                    (preset.durationMinutes / 60).toString(), (preset.durationMinutes % 60).toString(),
                )
            }.toImmutableList(),
            accounts = accounts.filter { !it.archived }.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            moneyCategories = money.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            timeCategories = time.map { PickerItem(it.id, it.name, Color(it.color), it.icon) }.toImmutableList(),
            projects = projects.filter { !it.archived }
                .map { it.categoryId to PickerItem(it.id, it.name, Color(it.color), it.icon.ifBlank { null }) }
                .toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: PresetsEvent) {
        error.value = null
        when (event) {
            is PresetsEvent.Save -> viewModelScope.launch {
                val moneyKind = event.kind != PresetKind.Activity
                val amount = if (moneyKind) parseAmount(event.amount) ?: 0L else 0L
                val minutes = (event.hours.toIntOrNull() ?: 0) * 60 + (event.minutes.toIntOrNull() ?: 0)
                when {
                    event.name.isBlank() -> error.value = "Enter a name."
                    moneyKind && amount <= 0 -> error.value = "Enter an amount above zero."
                    !moneyKind && minutes <= 0 -> error.value = "Enter how long it lasts."
                    else -> {
                        val order = presetRepository.observePresets().first().firstOrNull { it.id == event.id }?.orderNum
                            ?: System.currentTimeMillis().toDouble()
                        presetRepository.save(
                            Preset(
                                event.id, event.kind, event.name.trim(), event.title.trim(), event.categoryId,
                                event.projectId, event.accountId.takeIf { moneyKind }, amount,
                                if (moneyKind) 0 else minutes, order,
                            )
                        )
                    }
                }
            }
            is PresetsEvent.Delete -> viewModelScope.launch { presetRepository.delete(event.id) }
        }
    }
}

/** Taps on standard activities from the Timer and Day screens, kept in one place. */
@HiltViewModel
class ActivityPresetsViewModel @Inject constructor(
    private val presetRepository: PresetRepository,
    private val categoryRepository: CategoryRepository,
    private val applyPreset: ApplyPresetUseCase,
) : ComposeViewModel<ActivityPresetsState, ActivityPresetsEvent>() {

    private val message = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): ActivityPresetsState {
        val presets by remember { presetRepository.observePresets() }.collectAsState(emptyList())
        val categories by remember { categoryRepository.observeCategories() }.collectAsState(emptyList())
        val byId = categories.associateBy { it.id }
        return ActivityPresetsState(
            chips = presets.filter { it.kind == PresetKind.Activity }.map {
                PresetChip(
                    it.id, it.name, Duration.ofMinutes(it.durationMinutes.toLong()).formatShort(),
                    Color(byId[it.categoryId]?.color ?: 0xFF7A8CA8.toInt()),
                )
            }.toImmutableList(),
            message = message.value,
        )
    }

    override fun onEvent(event: ActivityPresetsEvent) {
        when (event) {
            is ActivityPresetsEvent.Apply -> viewModelScope.launch {
                applyPreset(event.id).fold(
                    ifLeft = { message.value = it.message() },
                    ifRight = { message.value = "Logged " + it.description },
                )
            }
            ActivityPresetsEvent.Dismiss -> message.value = null
        }
    }
}

@Immutable
data class ActivityPresetsState(val chips: ImmutableList<PresetChip>, val message: String?)

sealed interface ActivityPresetsEvent {
    data class Apply(val id: Long) : ActivityPresetsEvent
    data object Dismiss : ActivityPresetsEvent
}
