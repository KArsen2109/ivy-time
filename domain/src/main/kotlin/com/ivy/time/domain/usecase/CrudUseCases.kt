package com.ivy.time.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalRepository
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeError
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
    ): Either<TimeError, Unit> {
        if (name.isBlank()) return TimeError.BlankName.left()
        if (target.isZero || target.isNegative) return TimeError.NonPositiveDuration.left()
        repository.save(Goal(id, name.trim(), categoryId, target, period, type))
        return Unit.right()
    }
}
