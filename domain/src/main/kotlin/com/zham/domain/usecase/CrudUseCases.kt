package com.zham.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.Goal
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.GoalType
import com.zham.domain.model.Project
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeError
import java.time.Duration
import javax.inject.Inject

class SaveCategoryUseCase @Inject constructor(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        color: Int,
        icon: String,
        orderNum: Double,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        repository.save(Category(id, name.trim(), color, icon, orderNum))
        return Unit.right()
    }
}

class SaveProjectUseCase @Inject constructor(
    private val repository: ProjectRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        categoryId: Long?,
        color: Int,
        archived: Boolean = false,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        repository.save(Project(id, name.trim(), categoryId, color, archived))
        return Unit.right()
    }
}

class SaveGoalUseCase @Inject constructor(
    private val repository: GoalRepository,
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        categoryId: Long?,
        target: Duration,
        period: GoalPeriod,
        type: GoalType,
        projectId: Long? = null,
        matchTitle: Boolean = false,
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        if (target.isZero || target.isNegative) return TimeError.NonPositiveDuration.left()
        // A project or a title only narrows a category, so without one there is nothing to narrow.
        val narrowed = categoryId != null
        repository.save(
            Goal(
                id, name.trim(), categoryId, target, period, type,
                projectId = projectId.takeIf { narrowed },
                matchTitle = matchTitle && narrowed && projectId == null,
            )
        )
        return Unit.right()
    }
}
