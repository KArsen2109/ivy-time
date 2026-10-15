package com.ivy.time.data

import android.content.Context
import androidx.room.Room
import com.ivy.time.domain.model.CategoryRepository
import com.ivy.time.domain.model.GoalRepository
import com.ivy.time.domain.model.PlanRepository
import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): IvyTimeDatabase =
        Room.databaseBuilder(context, IvyTimeDatabase::class.java, "ivy-time.db")
            // The 0.1 schema held only projects; there is nothing worth migrating from it.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides
    @Singleton
    fun timeRepository(db: IvyTimeDatabase): TimeRepository = RoomTimeRepository(db.timeEntryDao())

    @Provides
    @Singleton
    fun categoryRepository(db: IvyTimeDatabase): CategoryRepository = RoomCategoryRepository(db)

    @Provides
    @Singleton
    fun projectRepository(db: IvyTimeDatabase): ProjectRepository = RoomProjectRepository(db)

    @Provides
    @Singleton
    fun planRepository(db: IvyTimeDatabase): PlanRepository = RoomPlanRepository(db.planDao())

    @Provides
    @Singleton
    fun goalRepository(db: IvyTimeDatabase): GoalRepository = RoomGoalRepository(db.goalDao())
}
