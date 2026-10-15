package com.ivy.time.data

import android.content.Context
import androidx.room.Room
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
        Room.databaseBuilder(context, IvyTimeDatabase::class.java, "ivy-time.db").build()

    @Provides
    @Singleton
    fun timeRepository(db: IvyTimeDatabase): TimeRepository = RoomTimeRepository(db.timeEntryDao())

    @Provides
    @Singleton
    fun projectRepository(db: IvyTimeDatabase): ProjectRepository = RoomProjectRepository(db)
}
