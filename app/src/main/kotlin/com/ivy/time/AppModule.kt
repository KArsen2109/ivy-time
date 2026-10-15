package com.ivy.time

import com.ivy.time.domain.model.ProjectRepository
import com.ivy.time.domain.model.TimeRepository
import com.ivy.time.domain.usecase.LogTimeUseCase
import com.ivy.time.domain.usecase.SaveProjectUseCase
import com.ivy.time.domain.usecase.StartTimerUseCase
import com.ivy.time.domain.usecase.StopTimerUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun startTimer(repo: TimeRepository, clock: Clock) = StartTimerUseCase(repo, clock)

    @Provides
    fun stopTimer(repo: TimeRepository, clock: Clock) = StopTimerUseCase(repo, clock)

    @Provides
    fun logTime(repo: TimeRepository, clock: Clock) = LogTimeUseCase(repo, clock)

    @Provides
    fun saveProject(repo: ProjectRepository) = SaveProjectUseCase(repo)
}
