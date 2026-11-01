package com.zham.data

import com.zham.domain.model.TransactionRepository
import com.zham.domain.model.Transaction
import com.zham.domain.model.PresetRepository
import com.zham.domain.model.Preset
import com.zham.domain.model.MoneyCategoryRepository
import com.zham.domain.model.MoneyCategory
import com.zham.domain.model.LoanRepository
import com.zham.domain.model.Loan
import com.zham.domain.model.AccountRepository
import com.zham.domain.model.Account
import androidx.room.withTransaction
import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.Goal
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.Note
import com.zham.domain.model.NoteRepository
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.Project
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

internal class RoomTimeRepository(
    private val dao: TimeEntryDao,
) : TimeRepository {
    override fun observeEntries(): Flow<List<TimeEntry>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findRunning(): TimeEntry? = dao.findRunning()?.toDomain()

    override suspend fun findById(id: Long): TimeEntry? = dao.findById(id)?.toDomain()

    override suspend fun save(entry: TimeEntry) = dao.save(entry.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

/** Deleting a category detaches it everywhere instead of cascading the deletion. */
internal class RoomCategoryRepository(
    private val db: ZhamDatabase,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(category: Category) = db.categoryDao().save(category.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        db.timeEntryDao().detachCategory(id)
        db.noteDao().detachCategory(id)
        db.planDao().detachCategory(id)
        db.projectDao().detachCategory(id)
        db.goalDao().deleteByCategory(id)
        db.categoryDao().delete(id)
    }
}

internal class RoomProjectRepository(
    private val db: ZhamDatabase,
) : ProjectRepository {
    override fun observeProjects(): Flow<List<Project>> =
        db.projectDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(project: Project) = db.projectDao().save(project.toEntity())

    override suspend fun delete(id: Long) = db.withTransaction {
        db.timeEntryDao().detachProject(id)
        db.noteDao().detachProject(id)
        db.financeDao().detachTransactionsFromProject(id)
        db.planDao().detachProject(id)
        // A goal that followed this project has nothing left to count.
        db.goalDao().deleteByProject(id)
        db.projectDao().delete(id)
    }
}

internal class RoomGoalRepository(
    private val dao: GoalDao,
) : GoalRepository {
    override fun observeGoals(): Flow<List<Goal>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(goal: Goal) = dao.save(goal.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomPlanRepository(
    private val dao: PlanDao,
) : PlanRepository {
    override fun observePlans(): Flow<List<PlanBlock>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCompletions(): Flow<List<PlanCompletion>> =
        dao.observeCompletions().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): PlanBlock? = dao.findById(id)?.toDomain()

    override suspend fun save(plan: PlanBlock) = dao.save(plan.toEntity())

    override suspend fun delete(id: Long) {
        dao.deleteCompletions(id)
        dao.delete(id)
    }

    override suspend fun setCompleted(planId: Long, date: LocalDate, completed: Boolean) {
        if (completed) {
            dao.complete(PlanCompletionEntity(planId, date.toEpochDay()))
        } else {
            dao.uncomplete(planId, date.toEpochDay())
        }
    }
}

internal class RoomNoteRepository(
    private val dao: NoteDao,
) : NoteRepository {
    override fun observeNotes(): Flow<List<Note>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Note? = dao.findById(id)?.toDomain()

    override suspend fun save(note: Note): Long {
        val rowId = dao.save(note.toEntity())
        return if (note.id == 0L) rowId else note.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}

internal class RoomAccountRepository(private val db: ZhamDatabase) : AccountRepository {
    override fun observeAccounts(): Flow<List<Account>> =
        db.financeDao().observeAccounts().map { list -> list.map { it.toDomain() } }

    override suspend fun save(account: Account): Long {
        val rowId = db.financeDao().saveAccount(account.toEntity())
        return if (account.id == 0L) rowId else account.id
    }

    override suspend fun delete(id: Long) = db.withTransaction {
        db.financeDao().deleteTransactionsOfAccount(id)
        db.financeDao().detachPresetsFromAccount(id)
        db.financeDao().detachLoansFromAccount(id)
        db.financeDao().deleteAccount(id)
    }
}

internal class RoomMoneyCategoryRepository(private val db: ZhamDatabase) : MoneyCategoryRepository {
    override fun observeCategories(): Flow<List<MoneyCategory>> =
        db.financeDao().observeCategories().map { list -> list.map { it.toDomain() } }

    override suspend fun save(category: MoneyCategory): Long {
        val rowId = db.financeDao().saveCategory(category.toEntity())
        return if (category.id == 0L) rowId else category.id
    }

    override suspend fun delete(id: Long) = db.withTransaction {
        db.financeDao().detachTransactionsFromCategory(id)
        db.financeDao().detachPresetsFromCategory(id)
        db.financeDao().detachLoansFromCategory(id)
        db.financeDao().deleteCategory(id)
    }
}

internal class RoomTransactionRepository(private val dao: FinanceDao) : TransactionRepository {
    override fun observeTransactions(): Flow<List<Transaction>> =
        dao.observeTransactions().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Transaction? = dao.findTransaction(id)?.toDomain()

    override suspend fun save(transaction: Transaction): Long {
        val rowId = dao.saveTransaction(transaction.toEntity())
        return if (transaction.id == 0L) rowId else transaction.id
    }

    override suspend fun delete(id: Long) = dao.deleteTransaction(id)
}

internal class RoomPresetRepository(private val dao: FinanceDao) : PresetRepository {
    override fun observePresets(): Flow<List<Preset>> = dao.observePresets().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Preset? = dao.findPreset(id)?.toDomain()

    override suspend fun save(preset: Preset): Long {
        val rowId = dao.savePreset(preset.toEntity())
        return if (preset.id == 0L) rowId else preset.id
    }

    override suspend fun delete(id: Long) = dao.deletePreset(id)
}

internal class RoomLoanRepository(private val dao: FinanceDao) : LoanRepository {
    override fun observeLoans(): Flow<List<Loan>> = dao.observeLoans().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Loan? = dao.findLoan(id)?.toDomain()

    override suspend fun save(loan: Loan): Long {
        val rowId = dao.saveLoan(loan.toEntity())
        return if (loan.id == 0L) rowId else loan.id
    }

    override suspend fun delete(id: Long) = dao.deleteLoan(id)
}
