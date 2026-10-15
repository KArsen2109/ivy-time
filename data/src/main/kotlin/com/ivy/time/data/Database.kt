package com.ivy.time.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL LIMIT 1")
    suspend fun findRunning(): TimeEntryEntity?

    @Upsert
    suspend fun save(entry: TimeEntryEntity)

    @Query("DELETE FROM time_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE time_entries SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun save(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [ProjectEntity::class, TimeEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class IvyTimeDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun projectDao(): ProjectDao
}
