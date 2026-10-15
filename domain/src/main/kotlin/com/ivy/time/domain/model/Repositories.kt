package com.ivy.time.domain.model

import kotlinx.coroutines.flow.Flow

interface TimeRepository {
    fun observeEntries(): Flow<List<TimeEntry>>
    suspend fun findRunning(): TimeEntry?
    suspend fun findById(id: Long): TimeEntry?

    /** Inserts when [TimeEntry.id] is 0, otherwise updates. */
    suspend fun save(entry: TimeEntry)
    suspend fun delete(id: Long)
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun save(category: Category)
    suspend fun delete(id: Long)
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun save(project: Project)
    suspend fun delete(id: Long)
}

interface PlannedRepository {
    fun observePlanned(): Flow<List<PlannedEntry>>
    suspend fun findById(id: Long): PlannedEntry?
    suspend fun save(planned: PlannedEntry)
    suspend fun delete(id: Long)
}

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>
    suspend fun save(goal: Goal)
    suspend fun delete(id: Long)
}
