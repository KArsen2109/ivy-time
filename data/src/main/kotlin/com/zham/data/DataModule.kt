package com.zham.data

import android.content.Context
import androidx.room.Room
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    private const val DATABASE_NAME = "zham.db"
    private const val SETTINGS_NAME = "zham-settings"

    // The file names the first builds used. They exist here only so those files can be adopted.
    private const val EARLIER_DATABASE_NAME = "ivy-time.db"
    private const val EARLIER_SETTINGS_NAME = "ivy-time-settings"

    /**
     * An earlier build named its files after a different name. If those files exist and the new
     * ones do not, they are renamed before anything opens them, so no record or setting is lost.
     */
    private fun adoptEarlierFiles(context: Context) {
        val suffixes = listOf("", "-wal", "-shm")
        val old = context.getDatabasePath(EARLIER_DATABASE_NAME)
        val new = context.getDatabasePath(DATABASE_NAME)
        if (old.exists() && !new.exists()) {
            suffixes.forEach { suffix ->
                val from = java.io.File(old.path + suffix)
                if (from.exists()) from.renameTo(java.io.File(new.path + suffix))
            }
        }
        val settingsDir = java.io.File(context.filesDir, "datastore")
        val oldSettings = java.io.File(settingsDir, "$EARLIER_SETTINGS_NAME.preferences_pb")
        val newSettings = java.io.File(settingsDir, "$SETTINGS_NAME.preferences_pb")
        if (oldSettings.exists() && !newSettings.exists()) oldSettings.renameTo(newSettings)
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZhamDatabase {
        adoptEarlierFiles(context)
        return Room.databaseBuilder(context, ZhamDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_3_4)
            // Versions 1 and 2 were early builds with nothing worth carrying over.
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
    }

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore {
        // Idempotent, and cheap, so the settings file is adopted whichever is built first.
        adoptEarlierFiles(context)
        return SettingsStore(context)
    }

    @Provides
    @Singleton
    fun timeRepository(db: ZhamDatabase): TimeRepository = RoomTimeRepository(db.timeEntryDao())

    @Provides
    @Singleton
    fun categoryRepository(db: ZhamDatabase): CategoryRepository = RoomCategoryRepository(db)

    @Provides
    @Singleton
    fun projectRepository(db: ZhamDatabase): ProjectRepository = RoomProjectRepository(db)

    @Provides
    @Singleton
    fun planRepository(db: ZhamDatabase): PlanRepository = RoomPlanRepository(db.planDao())

    @Provides
    @Singleton
    fun goalRepository(db: ZhamDatabase): GoalRepository = RoomGoalRepository(db.goalDao())
}
