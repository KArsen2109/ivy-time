package com.ivy.time.data

import androidx.room.withTransaction
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalRepository
import com.ivy.time.domain.model.PlanBlock
import com.ivy.time.domain.model.PlanCompletion
import com.ivy.time.domain.model.PlanRepository
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

internal class RoomTimeRepository(
    private val dao: TimeEntryDao,
) : TimeRepository {
    override fun observeEntries(): Flow<List<TimeEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findRunning(): TimeEntry? = dao.findRunning()?.toDomain()

    override suspend fun findById(id: Long): TimeEntry? = dao.findById(id)?.toDomain()

    override suspend fun save(entry: TimeEntry) = dao.save(entry.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

/** Deleting a category detaches it everywhere instead of cascading the deletion. */
internal class RoomCategoryRepository(
    private val db: IvyTimeDatabase,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(category: Category) = db.categoryDao().save(category.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        db.timeEntryDao().detachCategory(id)
        db.planDao().detachCategory(id)
        db.projectDao().detachCategory(id)
        db.goalDao().deleteByCategory(id)
        db.categoryDao().delete(id)
    }
}

internal class RoomProjectRepository(
    private val db: IvyTimeDatabase,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> =
        db.projectDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(project: Project) = db.projectDao().save(project.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        db.timeEntryDao().detachProject(id)
        db.planDao().detachProject(id)
        db.projectDao().delete(id)
    }
}

internal class RoomGoalRepository(
    private val dao: GoalDao,
) : GoalRepository {
    override fun observeGoals(): Flow<List<Goal>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(goal: Goal) = dao.save(goal.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomPlanRepository(
    private val dao: PlanDao,
) : PlanRepository {
    override fun observePlans(): Flow<List<PlanBlock>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCompletions(): Flow<List<PlanCompletion>> =
        dao.observeCompletions().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): PlanBlock? = dao.findById(id)?.toDomain()

    override suspend fun save(plan: PlanBlock) = dao.save(plan.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteCompletions(id)
        dao.delete(id)
    }

    override suspend fun setCompleted(planId: Long, date: LocalDate, completed: Boolean) {
        if (completed) {
            dao.complete(PlanCompletionEntity(planId, date.toEpochDay()))
        } else {
            dao.uncomplete(planId, date.toEpochDay())
        }
    }
}
