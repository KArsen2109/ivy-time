package com.ivy.time.data

import androidx.room.withTransaction
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeEntry
import com.ivy.time.domain.model.TimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomTimeRepository(
    private val dao: TimeEntryDao,
) : TimeRepository {
    override fun observeEntries(): Flow<List<TimeEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findRunning(): TimeEntry? = dao.findRunning()?.toDomain()

    override suspend fun save(entry: TimeEntry) = dao.save(entry.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomProjectRepository(
    private val db: IvyTimeDatabase,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> =
        db.projectDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(project: Project) = db.projectDao().save(project.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        db.timeEntryDao().detachProject(id)
        db.projectDao().delete(id)
    }
}
