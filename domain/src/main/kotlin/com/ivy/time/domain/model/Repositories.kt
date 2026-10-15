package com.ivy.time.domain.model

import kotlinx.coroutines.flow.Flow

interface TimeRepository {
    fun observeEntries(): Flow<List<TimeEntry>>
    suspend fun findRunning(): TimeEntry?

    /** Inserts when [TimeEntry.id] is 0, otherwise updates. */
    suspend fun save(entry: TimeEntry)
    suspend fun delete(id: Long)
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>

    /** Inserts when [Project.id] is 0, otherwise updates. */
    suspend fun save(project: Project)
    suspend fun delete(id: Long)
}
