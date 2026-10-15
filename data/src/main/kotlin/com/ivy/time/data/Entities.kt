package com.ivy.time.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.time.domain.model.Category
import com.ivy.time.domain.model.Goal
import com.ivy.time.domain.model.GoalPeriod
import com.ivy.time.domain.model.GoalType
import com.ivy.time.domain.model.PlanBlock
import com.ivy.time.domain.model.PlanCompletion
import com.ivy.time.domain.model.Project
import com.ivy.time.domain.model.RepeatRule
import com.ivy.time.domain.model.TimeEntry
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
) {
    fun toDomain() = Project(id, name, categoryId, color, archived)
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
) {
    fun toDomain() = TimeEntry(
        id = id,
        categoryId = categoryId,
        projectId = projectId,
        note = note,
        start = Instant.ofEpochMilli(startMillis),
        end = endMillis?.let(Instant::ofEpochMilli),
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
) {
    fun toDomain() = Goal(
        id = id,
        name = name,
        categoryId = categoryId,
        target = Duration.ofMinutes(minutes),
        period = parseEnum(period, GoalPeriod.Week),
        type = parseEnum(type, GoalType.AtLeast),
    )
}

fun Category.toEntity() = CategoryEntity(id, name, color, icon, orderNum)

fun Project.toEntity() = ProjectEntity(id, name, categoryId, color, archived)

fun TimeEntry.toEntity() = TimeEntryEntity(
    id = id,
    categoryId = categoryId,
    projectId = projectId,
    note = note,
    startMillis = start.toEpochMilli(),
    endMillis = end?.toEpochMilli(),
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    minutes = target.toMinutes(),
    period = period.name,
    type = type.name,
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
