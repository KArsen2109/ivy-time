package com.zham.app.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.viewModelScope
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import com.zham.domain.usecase.SaveCategoryUseCase
import com.zham.domain.usecase.formatShort
import com.zham.domain.usecase.summarize
import com.zham.app.ui.ComposeViewModel
import com.zham.app.ui.message
import com.zham.app.ui.tickingNow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@Immutable
data class CategoriesState(
    val categories: ImmutableList<CategoryItem>,
    val error: String?,
)

@Immutable
data class CategoryItem(
    val id: Long,
    val name: String,
    val color: Color,
    val icon: String,
    val total: String,
    val projectCount: Int,
)

sealed interface CategoriesEvent {
    data class Save(val id: Long, val name: String, val color: Color, val icon: String) :
        CategoriesEvent
    data class Delete(val id: Long) : CategoriesEvent
    data object DismissError : CategoriesEvent
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val timeRepository: TimeRepository,
    private val projectRepository: ProjectRepository,
    private val saveCategory: SaveCategoryUseCase,
    private val clock: Clock,
) : ComposeViewModel<CategoriesState, CategoriesEvent>() {

    private val error = mutableStateOf<String?>(null)

    @Composable
    override fun uiState(): CategoriesState {
        val categories by remember { categoryRepository.observeCategories() }
            .collectAsState(emptyList())
        val entries by remember { timeRepository.observeEntries() }.collectAsState(emptyList())
        val projects by remember { projectRepository.observeProjects() }.collectAsState(emptyList())
        val now by tickingNow(clock, active = entries.any { it.isRunning })

        val totals = summarize(entries, Instant.EPOCH, FAR_FUTURE, now)
            .associate { it.categoryId to it.total }

        return CategoriesState(
            categories = categories.map {
                CategoryItem(
                    id = it.id,
                    name = it.name,
                    color = Color(it.color),
                    icon = it.icon,
                    total = (totals[it.id] ?: Duration.ZERO).formatShort(),
                    projectCount = projects.count { project -> project.categoryId == it.id },
                )
            }.toImmutableList(),
            error = error.value,
        )
    }

    override fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.Save -> viewModelScope.launch {
                saveCategory(
                    id = event.id,
                    name = event.name,
                    color = event.color.toArgb(),
                    icon = event.icon,
                    orderNum = event.id.toDouble(),
                ).fold(
                    ifLeft = { error.value = it.message() },
                    ifRight = { error.value = null },
                )
            }
            is CategoriesEvent.Delete -> viewModelScope.launch {
                categoryRepository.delete(event.id)
            }
            CategoriesEvent.DismissError -> error.value = null
        }
    }

    private companion object {
        /** Room stores millis, so a plain far-future instant is enough as an open upper bound. */
        val FAR_FUTURE: Instant = Instant.ofEpochMilli(Long.MAX_VALUE / 2)
    }
}
