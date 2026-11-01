package com.zham.data

import android.content.Context
import androidx.room.Room
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.LoanRepository
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.NoteRepository
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
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
    fun noteRepository(db: ZhamDatabase): NoteRepository = RoomNoteRepository(db.noteDao())

    @Provides
    @Singleton
    fun accountRepository(db: ZhamDatabase): AccountRepository = RoomAccountRepository(db)

    @Provides
    @Singleton
    fun moneyCategoryRepository(db: ZhamDatabase): MoneyCategoryRepository = RoomMoneyCategoryRepository(db)

    @Provides
    @Singleton
    fun transactionRepository(db: ZhamDatabase): TransactionRepository = RoomTransactionRepository(db.financeDao())

    @Provides
    @Singleton
    fun presetRepository(db: ZhamDatabase): PresetRepository = RoomPresetRepository(db.financeDao())

    @Provides
    @Singleton
    fun loanRepository(db: ZhamDatabase): LoanRepository = RoomLoanRepository(db.financeDao())

    @Provides
    @Singleton
    fun goalRepository(db: ZhamDatabase): GoalRepository = RoomGoalRepository(db.goalDao())
}
