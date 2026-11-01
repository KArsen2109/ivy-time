package com.zham.data

import com.zham.domain.model.TxType
import com.zham.domain.model.Transaction
import com.zham.domain.model.RateKind
import com.zham.domain.model.PresetKind
import com.zham.domain.model.Preset
import com.zham.domain.model.MoneyCategory
import com.zham.domain.model.Loan
import com.zham.domain.model.AmortizationType
import com.zham.domain.model.Account
import com.zham.domain.model.Note
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zham.domain.model.Category
import com.zham.domain.model.Goal
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalType
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.Project
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import java.time.Duration
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val orderNum: Double,
) {
    fun toDomain() = Category(id, name, color, icon, orderNum)
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val categoryId: Long?,
    val color: Int,
    val archived: Boolean,
    val icon: String = "",
) {
    fun toDomain() = Project(id, name, categoryId, color, archived, icon)
}

@Entity(tableName = "time_entries")
data class TimeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val startMillis: Long,
    /** Null while the timer is running. */
    val endMillis: Long?,
    val description: String = "",
    val point: Boolean = false,
    val planId: Long? = null,
    val planEpochDay: Long? = null,
) {
    fun toDomain() = TimeEntry(
        id = id,
        categoryId = categoryId,
        projectId = projectId,
        note = note,
        start = Instant.ofEpochMilli(startMillis),
        end = endMillis?.let(Instant::ofEpochMilli),
        description = description,
        point = point,
        planId = planId,
        planDate = planEpochDay?.let(LocalDate::ofEpochDay),
    )
}

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val categoryId: Long?,
    val minutes: Long,
    val period: String,
    val type: String,
    val projectId: Long? = null,
    val matchTitle: Boolean = false,
) {
    fun toDomain() = Goal(
        id = id,
        name = name,
        categoryId = categoryId,
        target = Duration.ofMinutes(minutes),
        period = parseEnum(period, GoalPeriod.Week),
        type = parseEnum(type, GoalType.AtLeast),
        projectId = projectId,
        matchTitle = matchTitle,
    )
}

fun Category.toEntity() = CategoryEntity(id, name, color, icon, orderNum)

fun Project.toEntity() = ProjectEntity(id, name, categoryId, color, archived, icon)

fun TimeEntry.toEntity() = TimeEntryEntity(
    id = id,
    categoryId = categoryId,
    projectId = projectId,
    note = note,
    startMillis = start.toEpochMilli(),
    endMillis = end?.toEpochMilli(),
    description = description,
    point = point,
    planId = planId,
    planEpochDay = planDate?.toEpochDay(),
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    minutes = target.toMinutes(),
    period = period.name,
    type = type.name,
    projectId = projectId,
    matchTitle = matchTitle,
)

/** Falls back instead of throwing, so a hand-edited database cannot crash the app. */
private inline fun <reified T : Enum<T>> parseEnum(name: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

@Entity(tableName = "plan_blocks")
data class PlanBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val categoryId: Long?,
    val projectId: Long?,
    val note: String,
    val startSecondOfDay: Int,
    val endSecondOfDay: Int,
    val startEpochDay: Long,
    /** "ONCE", "DAILY", "MONTHLY" or "WEEKLY:MONDAY,WEDNESDAY". */
    val repeat: String,
    val description: String = "",
    /** The single reminder of earlier versions. Kept only because the column is still in the table. */
    val reminderMinutes: Int? = null,
    val point: Boolean = false,
    /** Minutes before the start, comma separated: "10,30". */
    val reminders: String = "",
    val endReminder: Int? = null,
) {
    fun toDomain() = PlanBlock(
        id = id,
        categoryId = categoryId,
        projectId = projectId,
        note = note,
        start = LocalTime.ofSecondOfDay(startSecondOfDay.toLong()),
        end = LocalTime.ofSecondOfDay(endSecondOfDay.toLong()),
        startDate = LocalDate.ofEpochDay(startEpochDay),
        repeat = parseRepeat(repeat),
        description = description,
        reminders = reminders.split(",").mapNotNull { it.trim().toIntOrNull() },
        point = point,
        endReminder = endReminder,
    )
}

@Entity(tableName = "plan_completions", primaryKeys = ["planId", "epochDay"])
data class PlanCompletionEntity(
    val planId: Long,
    val epochDay: Long,
) {
    fun toDomain() = PlanCompletion(planId, LocalDate.ofEpochDay(epochDay))
}

fun PlanBlock.toEntity() = PlanBlockEntity(
    id = id,
    categoryId = categoryId,
    projectId = projectId,
    note = note,
    startSecondOfDay = start.toSecondOfDay(),
    endSecondOfDay = end.toSecondOfDay(),
    startEpochDay = startDate.toEpochDay(),
    repeat = writeRepeat(repeat),
    description = description,
    reminderMinutes = null,
    point = point,
    reminders = reminders.joinToString(","),
    endReminder = endReminder,
)

private fun writeRepeat(rule: RepeatRule): String = when (rule) {
    RepeatRule.Once -> "ONCE"
    RepeatRule.Daily -> "DAILY"
    RepeatRule.Monthly -> "MONTHLY"
    is RepeatRule.Weekly -> "WEEKLY:" + rule.days.sorted().joinToString(",") { it.name }
}

private fun parseRepeat(raw: String): RepeatRule = when {
    raw == "DAILY" -> RepeatRule.Daily
    raw == "MONTHLY" -> RepeatRule.Monthly
    raw.startsWith("WEEKLY:") -> RepeatRule.Weekly(
        raw.removePrefix("WEEKLY:")
            .split(",")
            .mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
            .toSet()
    )
    else -> RepeatRule.Once
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val body: String,
    val categoryId: Long?,
    val projectId: Long?,
    val createdMillis: Long,
    val updatedMillis: Long,
    val pinned: Boolean,
) {
    fun toDomain() = Note(
        id, title, body, categoryId, projectId,
        Instant.ofEpochMilli(createdMillis), Instant.ofEpochMilli(updatedMillis), pinned,
    )
}

fun Note.toEntity() = NoteEntity(
    id, title, body, categoryId, projectId, created.toEpochMilli(), updated.toEpochMilli(), pinned,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val currency: String,
    val color: Int,
    val icon: String,
    val initialMinor: Long,
    val orderNum: Double,
    val archived: Boolean,
) {
    fun toDomain() = Account(id, name, currency, color, icon, initialMinor, orderNum, archived)
}

fun Account.toEntity() = AccountEntity(id, name, currency, color, icon, initialMinor, orderNum, archived)

@Entity(tableName = "money_categories")
data class MoneyCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val color: Int,
    val icon: String,
    val orderNum: Double,
) {
    fun toDomain() = MoneyCategory(id, name, color, icon, orderNum)
}

fun MoneyCategory.toEntity() = MoneyCategoryEntity(id, name, color, icon, orderNum)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val type: String,
    val amountMinor: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val toAmountMinor: Long?,
    val categoryId: Long?,
    val title: String,
    val description: String,
    val timeMillis: Long,
    val projectId: Long?,
) {
    fun toDomain() = Transaction(
        id, parseEnum(type, TxType.Expense), amountMinor, accountId, toAccountId, toAmountMinor,
        categoryId, title, description, Instant.ofEpochMilli(timeMillis), projectId,
    )
}

fun Transaction.toEntity() = TransactionEntity(
    id, type.name, amountMinor, accountId, toAccountId, toAmountMinor, categoryId, title, description,
    time.toEpochMilli(), projectId,
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val kind: String,
    val name: String,
    val title: String,
    val categoryId: Long?,
    val projectId: Long?,
    val accountId: Long?,
    val amountMinor: Long,
    val durationMinutes: Int,
    val orderNum: Double,
) {
    fun toDomain() = Preset(
        id, parseEnum(kind, PresetKind.Expense), name, title, categoryId, projectId, accountId,
        amountMinor, durationMinutes, orderNum,
    )
}

fun Preset.toEntity() = PresetEntity(
    id, kind.name, name, title, categoryId, projectId, accountId, amountMinor, durationMinutes, orderNum,
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val currency: String,
    val principalMinor: Long,
    val ratePercent: Double,
    val rateKind: String,
    val termMonths: Int,
    val type: String,
    val firstPaymentEpochDay: Long,
    val paidCount: Int,
    val accountId: Long?,
    val categoryId: Long?,
) {
    fun toDomain() = Loan(
        id, name, currency, principalMinor, ratePercent, parseEnum(rateKind, RateKind.Nominal), termMonths,
        parseEnum(type, AmortizationType.Annuity), LocalDate.ofEpochDay(firstPaymentEpochDay), paidCount,
        accountId, categoryId,
    )
}

fun Loan.toEntity() = LoanEntity(
    id, name, currency, principalMinor, annualRatePercent, rateKind.name, termMonths, type.name,
    firstPaymentDate.toEpochDay(), paidCount, accountId, categoryId,
)
