package com.ivy.time.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.TimeEntry
import java.time.Instant

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val color: Int,
) {
    fun toDomain() = Project(id, name, color)
}

@Entity(tableName = "time_entries")
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val projectId: Long?,
    val note: String,
    val startMillis: Long,
    /** Null while the timer is running. */
    val endMillis: Long?,
) {
    fun toDomain() = TimeEntry(
        id = id,
        projectId = projectId,
        note = note,
        start = Instant.ofEpochMilli(startMillis),
        end = endMillis?.let(Instant::ofEpochMilli),
    )
}

fun Project.toEntity() = ProjectEntity(id, name, color)

fun TimeEntry.toEntity() = TimeEntryEntity(
    id = id,
    projectId = projectId,
    note = note,
    startMillis = start.toEpochMilli(),
    endMillis = end?.toEpochMilli(),
)
