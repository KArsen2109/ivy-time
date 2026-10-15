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

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZhamDatabase {
        return Room.databaseBuilder(context, ZhamDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore {
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
