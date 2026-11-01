package com.zham.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL LIMIT 1")
    suspend fun findRunning(): TimeEntryEntity?

    @Query("SELECT * FROM time_entries WHERE id = :id")
    suspend fun findById(id: Long): TimeEntryEntity?

    @Upsert
    suspend fun save(entry: TimeEntryEntity)

    @Query("DELETE FROM time_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE time_entries SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: Long)

    @Query("UPDATE time_entries SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY orderNum, name COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun save(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun save(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE projects SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GoalEntity>>

    @Upsert
    suspend fun save(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM goals WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    @Query("DELETE FROM goals WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: Long)
}

@Database(
    entities = [
        CategoryEntity::class,
        ProjectEntity::class,
        TimeEntryEntity::class,
        PlanBlockEntity::class,
        PlanCompletionEntity::class,
        GoalEntity::class,
        NoteEntity::class,
        AccountEntity::class,
        MoneyCategoryEntity::class,
        TransactionEntity::class,
        PresetEntity::class,
        LoanEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class ZhamDatabase : RoomDatabase() {
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun planDao(): PlanDao
    abstract fun goalDao(): GoalDao
    abstract fun noteDao(): NoteDao
    abstract fun financeDao(): FinanceDao
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_blocks ORDER BY startSecondOfDay")
    fun observeAll(): Flow<List<PlanBlockEntity>>

    @Query("SELECT * FROM plan_blocks WHERE id = :id")
    suspend fun findById(id: Long): PlanBlockEntity?

    @Upsert
    suspend fun save(plan: PlanBlockEntity)

    @Query("DELETE FROM plan_blocks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM plan_completions WHERE planId = :planId")
    suspend fun deleteCompletions(planId: Long)

    @Query("SELECT * FROM plan_completions")
    fun observeCompletions(): Flow<List<PlanCompletionEntity>>

    @Upsert
    suspend fun complete(completion: PlanCompletionEntity)

    @Query("DELETE FROM plan_completions WHERE planId = :planId AND epochDay = :epochDay")
    suspend fun uncomplete(planId: Long, epochDay: Long)

    @Query("UPDATE plan_blocks SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: Long)

    @Query("UPDATE plan_blocks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)
}

/** 3 to 4 adds the description to records and plan blocks, keeping every existing row. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_entries ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE plan_blocks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}

/** 4 to 5 adds the optional reminder to plan blocks; existing blocks get none. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plan_blocks ADD COLUMN reminderMinutes INTEGER")
    }
}

/** 5 to 6 lets a goal follow one project or one title; existing goals keep counting their category. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE goals ADD COLUMN projectId INTEGER")
        db.execSQL("ALTER TABLE goals ADD COLUMN matchTitle INTEGER NOT NULL DEFAULT 0")
    }
}

/** 6 to 7 lets a project carry an icon or emoji of its own. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE projects ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
    }
}

/** 7 to 8 adds moments without length, and the link from a record to the block it was made from. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE time_entries ADD COLUMN point INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE time_entries ADD COLUMN planId INTEGER")
        db.execSQL("ALTER TABLE time_entries ADD COLUMN planEpochDay INTEGER")
        db.execSQL("ALTER TABLE plan_blocks ADD COLUMN point INTEGER NOT NULL DEFAULT 0")
    }
}

/** 8 to 9 lets a block have several reminders and one before its end; the old single one is carried over. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plan_blocks ADD COLUMN reminders TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE plan_blocks ADD COLUMN endReminder INTEGER")
        db.execSQL("UPDATE plan_blocks SET reminders = CAST(reminderMinutes AS TEXT) WHERE reminderMinutes IS NOT NULL")
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedMillis DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: Long): NoteEntity?

    @Upsert
    suspend fun save(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE notes SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachCategory(categoryId: Long)

    @Query("UPDATE notes SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: Long)
}

/** 9 to 10 adds notes. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `categoryId` INTEGER, `projectId` INTEGER, " +
                "`createdMillis` INTEGER NOT NULL, `updatedMillis` INTEGER NOT NULL, `pinned` INTEGER NOT NULL)"
        )
    }
}

@Dao
interface FinanceDao {
    @Query("SELECT * FROM accounts ORDER BY orderNum, name COLLATE NOCASE")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Upsert
    suspend fun saveAccount(account: AccountEntity): Long

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteTransactionsOfAccount(accountId: Long)

    @Query("UPDATE presets SET accountId = NULL WHERE accountId = :accountId")
    suspend fun detachPresetsFromAccount(accountId: Long)

    @Query("UPDATE loans SET accountId = NULL WHERE accountId = :accountId")
    suspend fun detachLoansFromAccount(accountId: Long)

    @Query("SELECT * FROM money_categories ORDER BY orderNum, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<MoneyCategoryEntity>>

    @Upsert
    suspend fun saveCategory(category: MoneyCategoryEntity): Long

    @Query("DELETE FROM money_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachTransactionsFromCategory(categoryId: Long)

    @Query("UPDATE presets SET categoryId = NULL WHERE categoryId = :categoryId AND kind != 'Activity'")
    suspend fun detachPresetsFromCategory(categoryId: Long)

    @Query("UPDATE loans SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun detachLoansFromCategory(categoryId: Long)

    @Query("SELECT * FROM transactions ORDER BY timeMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findTransaction(id: Long): TransactionEntity?

    @Upsert
    suspend fun saveTransaction(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("UPDATE transactions SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachTransactionsFromProject(projectId: Long)

    @Query("SELECT * FROM presets ORDER BY orderNum, name COLLATE NOCASE")
    fun observePresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun findPreset(id: Long): PresetEntity?

    @Upsert
    suspend fun savePreset(preset: PresetEntity): Long

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePreset(id: Long)

    @Query("SELECT * FROM loans ORDER BY name COLLATE NOCASE")
    fun observeLoans(): Flow<List<LoanEntity>>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun findLoan(id: Long): LoanEntity?

    @Upsert
    suspend fun saveLoan(loan: LoanEntity): Long

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteLoan(id: Long)
}

/** 10 to 11 adds money: accounts, money categories, transactions, ready-made entries and loans. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `color` INTEGER NOT NULL, `icon` TEXT NOT NULL, `initialMinor` INTEGER NOT NULL, " +
                "`orderNum` REAL NOT NULL, `archived` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `money_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`color` INTEGER NOT NULL, `icon` TEXT NOT NULL, `orderNum` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `toAccountId` INTEGER, `toAmountMinor` INTEGER, " +
                "`categoryId` INTEGER, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `timeMillis` INTEGER NOT NULL, `projectId` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `presets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `title` TEXT NOT NULL, `categoryId` INTEGER, `projectId` INTEGER, `accountId` INTEGER, " +
                "`amountMinor` INTEGER NOT NULL, `durationMinutes` INTEGER NOT NULL, `orderNum` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`currency` TEXT NOT NULL, `principalMinor` INTEGER NOT NULL, `ratePercent` REAL NOT NULL, `rateKind` TEXT NOT NULL, " +
                "`termMonths` INTEGER NOT NULL, `type` TEXT NOT NULL, `firstPaymentEpochDay` INTEGER NOT NULL, " +
                "`paidCount` INTEGER NOT NULL, `accountId` INTEGER, `categoryId` INTEGER)"
        )
    }
}
