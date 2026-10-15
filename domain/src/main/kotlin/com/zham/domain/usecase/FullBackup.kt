package com.zham.domain.usecase

import com.zham.domain.model.Category
import com.zham.domain.model.CategoryRepository
import com.zham.domain.model.Goal
import com.zham.domain.model.GoalPeriod
import com.zham.domain.model.GoalRepository
import com.zham.domain.model.GoalType
import com.zham.domain.model.PlanBlock
import com.zham.domain.model.PlanCompletion
import com.zham.domain.model.PlanRepository
import com.zham.domain.model.Project
import com.zham.domain.model.ProjectRepository
import com.zham.domain.model.RepeatRule
import com.zham.domain.model.TimeEntry
import com.zham.domain.model.TimeRepository
import kotlinx.coroutines.flow.firstOrNull
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * Everything the app holds, with links between things written as names so the file does not
 * depend on database ids. The CSV backup fills only [entries].
 */
data class BackupData(
    val categories: List<CategoryItem> = emptyList(),
    val projects: List<ProjectItem> = emptyList(),
    val entries: List<EntryItem> = emptyList(),
    val plans: List<PlanItem> = emptyList(),
    val completions: List<CompletionItem> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
) {
    data class CategoryItem(val name: String, val color: Int, val icon: String, val order: Double)
    data class ProjectItem(val name: String, val category: String, val color: Int, val archived: Boolean)
    data class EntryItem(
        val start: Instant,
        val end: Instant?,
        val category: String,
        val project: String,
        val note: String,
        val description: String,
    )
    data class PlanItem(
        val category: String,
        val project: String,
        val note: String,
        val description: String,
        val start: LocalTime,
        val end: LocalTime,
        val startDate: LocalDate,
        val repeat: RepeatRule,
    )
    /** [plan] is the position of the block in [plans]. */
    data class CompletionItem(val plan: Int, val date: LocalDate)
    data class GoalItem(
        val name: String,
        val category: String,
        val targetMinutes: Long,
        val period: GoalPeriod,
        val type: GoalType,
    )

    val isEmpty: Boolean
        get() = categories.isEmpty() && projects.isEmpty() && entries.isEmpty() &&
            plans.isEmpty() && goals.isEmpty()
}

object FullBackup {
    const val VERSION = 1

    fun fromCsv(csv: String): BackupData = BackupData(
        entries = Csv.parse(csv).map {
            BackupData.EntryItem(it.start, it.end, it.category, it.project, it.note, it.description)
        },
    )

    fun encode(data: BackupData): String = Json.write(
        linkedMapOf(
            "app" to "zham",
            "version" to VERSION,
            "categories" to data.categories.map {
                mapOf("name" to it.name, "color" to it.color, "icon" to it.icon, "order" to it.order)
            },
            "projects" to data.projects.map {
                mapOf("name" to it.name, "category" to it.category, "color" to it.color, "archived" to it.archived)
            },
            "entries" to data.entries.map {
                mapOf(
                    "start" to it.start.toString(),
                    "end" to it.end?.toString(),
                    "category" to it.category,
                    "project" to it.project,
                    "note" to it.note,
                    "description" to it.description,
                )
            },
            "plans" to data.plans.map {
                mapOf(
                    "category" to it.category,
                    "project" to it.project,
                    "note" to it.note,
                    "description" to it.description,
                    "start" to it.start.toString(),
                    "end" to it.end.toString(),
                    "startDate" to it.startDate.toString(),
                    "repeat" to when (it.repeat) {
                        RepeatRule.Once -> "Once"
                        RepeatRule.Daily -> "Daily"
                        RepeatRule.Monthly -> "Monthly"
                        is RepeatRule.Weekly -> "Weekly"
                    },
                    "days" to ((it.repeat as? RepeatRule.Weekly)?.days.orEmpty().map { day -> day.name }),
                )
            },
            "completions" to data.completions.map { mapOf("plan" to it.plan, "date" to it.date.toString()) },
            "goals" to data.goals.map {
                mapOf(
                    "name" to it.name,
                    "category" to it.category,
                    "targetMinutes" to it.targetMinutes,
                    "period" to it.period.name,
                    "type" to it.type.name,
                )
            },
        )
    )

    /** Null when [text] is not a Zham backup. Items that cannot be read are skipped. */
    fun decode(text: String): BackupData? {
        val root = runCatching { Json.parse(text) }.getOrNull() as? Map<*, *> ?: return null
        if (root["app"] != "zham") return null

        fun items(key: String): List<Map<*, *>> =
            (root[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

        fun Map<*, *>.str(key: String) = this[key] as? String ?: ""
        fun Map<*, *>.int(key: String) = (this[key] as? Number)?.toInt()

        return BackupData(
            categories = items("categories").mapNotNull {
                BackupData.CategoryItem(
                    name = it.str("name").ifBlank { return@mapNotNull null },
                    color = it.int("color") ?: return@mapNotNull null,
                    icon = it.str("icon").ifBlank { "other" },
                    order = (it["order"] as? Number)?.toDouble() ?: 0.0,
                )
            },
            projects = items("projects").mapNotNull {
                BackupData.ProjectItem(
                    name = it.str("name").ifBlank { return@mapNotNull null },
                    category = it.str("category"),
                    color = it.int("color") ?: return@mapNotNull null,
                    archived = it["archived"] == true,
                )
            },
            entries = items("entries").mapNotNull {
                BackupData.EntryItem(
                    start = runCatching { Instant.parse(it.str("start")) }.getOrNull()
                        ?: return@mapNotNull null,
                    end = runCatching { Instant.parse(it.str("end")) }.getOrNull(),
                    category = it.str("category"),
                    project = it.str("project"),
                    note = it.str("note"),
                    description = it.str("description"),
                )
            },
            plans = items("plans").mapNotNull {
                val days = (it["days"] as? List<*>).orEmpty()
                    .mapNotNull { name -> DayOfWeek.entries.firstOrNull { day -> day.name == name } }
                    .toSet()
                BackupData.PlanItem(
                    category = it.str("category"),
                    project = it.str("project"),
                    note = it.str("note"),
                    description = it.str("description"),
                    start = runCatching { LocalTime.parse(it.str("start")) }.getOrNull()
                        ?: return@mapNotNull null,
                    end = runCatching { LocalTime.parse(it.str("end")) }.getOrNull()
                        ?: return@mapNotNull null,
                    startDate = runCatching { LocalDate.parse(it.str("startDate")) }.getOrNull()
                        ?: return@mapNotNull null,
                    repeat = when (it.str("repeat")) {
                        "Daily" -> RepeatRule.Daily
                        "Monthly" -> RepeatRule.Monthly
                        "Weekly" -> RepeatRule.Weekly(days)
                        else -> RepeatRule.Once
                    },
                )
            },
            completions = items("completions").mapNotNull {
                BackupData.CompletionItem(
                    plan = it.int("plan") ?: return@mapNotNull null,
                    date = runCatching { LocalDate.parse(it.str("date")) }.getOrNull()
                        ?: return@mapNotNull null,
                )
            },
            goals = items("goals").mapNotNull {
                BackupData.GoalItem(
                    name = it.str("name").ifBlank { return@mapNotNull null },
                    category = it.str("category"),
                    targetMinutes = (it["targetMinutes"] as? Number)?.toLong()
                        ?.takeIf { minutes -> minutes > 0 } ?: return@mapNotNull null,
                    period = GoalPeriod.entries.firstOrNull { p -> p.name == it.str("period") }
                        ?: return@mapNotNull null,
                    type = GoalType.entries.firstOrNull { t -> t.name == it.str("type") }
                        ?: return@mapNotNull null,
                )
            },
        )
    }
}

/** Reads everything the app holds into a [BackupData]. */
class BuildBackupUseCase @Inject constructor(
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val planRepository: PlanRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(): BackupData {
        val categories = categoryRepository.observeCategories().firstOrNull().orEmpty()
        val projects = projectRepository.observeProjects().firstOrNull().orEmpty()
        val plans = planRepository.observePlans().firstOrNull().orEmpty()
        val completions = planRepository.observeCompletions().firstOrNull().orEmpty()
        val goals = goalRepository.observeGoals().firstOrNull().orEmpty()
        val entries = timeRepository.observeEntries().firstOrNull().orEmpty()

        val categoryNames = categories.associate { it.id to it.name }
        val projectNames = projects.associate { it.id to it.name }
        val planIndex = plans.withIndex().associate { (index, plan) -> plan.id to index }

        return BackupData(
            categories = categories.map { BackupData.CategoryItem(it.name, it.color, it.icon, it.orderNum) },
            projects = projects.map {
                BackupData.ProjectItem(it.name, categoryNames[it.categoryId].orEmpty(), it.color, it.archived)
            },
            entries = entries.sortedBy { it.start }.map {
                BackupData.EntryItem(
                    it.start, it.end, categoryNames[it.categoryId].orEmpty(),
                    projectNames[it.projectId].orEmpty(), it.note, it.description,
                )
            },
            plans = plans.map {
                BackupData.PlanItem(
                    categoryNames[it.categoryId].orEmpty(), projectNames[it.projectId].orEmpty(),
                    it.note, it.description, it.start, it.end, it.startDate, it.repeat,
                )
            },
            completions = completions.mapNotNull { completion ->
                planIndex[completion.planId]?.let { BackupData.CompletionItem(it, completion.date) }
            },
            goals = goals.map {
                BackupData.GoalItem(
                    it.name, categoryNames[it.categoryId].orEmpty(), it.target.toMinutes(), it.period, it.type,
                )
            },
        )
    }
}

data class RestoreResult(val entries: Int, val plans: Int, val goals: Int, val skipped: Int)

/**
 * Merges a [BackupData] into what is already there. Things are matched by name or by their
 * content, so restoring the same file twice adds nothing the second time.
 */
class RestoreBackupUseCase @Inject constructor(
    private val timeRepository: TimeRepository,
    private val categoryRepository: CategoryRepository,
    private val projectRepository: ProjectRepository,
    private val planRepository: PlanRepository,
    private val goalRepository: GoalRepository,
) {
    suspend operator fun invoke(data: BackupData, defaultColor: (Int) -> Int): RestoreResult {
        // Categories first: everything else points at them by name.
        var categories = categoryRepository.observeCategories().firstOrNull().orEmpty()
        val wantedCategories = (data.categories.map { it.name } +
            data.projects.map { it.category } + data.entries.map { it.category } +
            data.plans.map { it.category } + data.goals.map { it.category })
            .filter { it.isNotBlank() }
        val byName = data.categories.associateBy { it.name.lowercase() }
        wantedCategories.distinctBy { it.lowercase() }.forEach { name ->
            if (categories.none { it.name.equals(name, ignoreCase = true) }) {
                val item = byName[name.lowercase()]
                categoryRepository.save(
                    Category(
                        id = 0,
                        name = name,
                        color = item?.color ?: defaultColor(categories.size),
                        icon = item?.icon ?: "other",
                        orderNum = item?.order ?: categories.size.toDouble(),
                    )
                )
                categories = categoryRepository.observeCategories().firstOrNull().orEmpty()
            }
        }
        fun categoryId(name: String): Long? =
            categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id

        var projects = projectRepository.observeProjects().firstOrNull().orEmpty()
        val projectKeys = (data.projects.map { it.category to it.name } +
            (data.entries.map { it.category to it.project } + data.plans.map { it.category to it.project })
                .filter { it.second.isNotBlank() })
            .distinctBy { it.first.lowercase() to it.second.lowercase() }
        projectKeys.forEach { (categoryName, name) ->
            val owner = categoryId(categoryName)
            if (projects.none { it.name.equals(name, true) && it.categoryId == owner }) {
                val item = data.projects.firstOrNull {
                    it.name.equals(name, true) && it.category.equals(categoryName, true)
                }
                projectRepository.save(
                    Project(0, name, owner, item?.color ?: defaultColor(projects.size), item?.archived ?: false)
                )
                projects = projectRepository.observeProjects().firstOrNull().orEmpty()
            }
        }
        fun projectId(categoryName: String, name: String): Long? {
            if (name.isBlank()) return null
            val owner = categoryId(categoryName)
            return projects.firstOrNull { it.name.equals(name, true) && it.categoryId == owner }?.id
        }

        val existing = timeRepository.observeEntries().firstOrNull().orEmpty()
        val seen = existing.map { Triple(it.start, it.end, it.note) }.toMutableSet()
        var entriesAdded = 0
        var skipped = 0
        data.entries.forEach { item ->
            val end = item.end ?: item.start.plusSeconds(60)
            if (!seen.add(Triple(item.start, end, item.note))) {
                skipped++
                return@forEach
            }
            timeRepository.save(
                TimeEntry(
                    id = 0,
                    categoryId = categoryId(item.category),
                    projectId = projectId(item.category, item.project),
                    note = item.note,
                    start = item.start,
                    end = end,
                    description = item.description,
                )
            )
            entriesAdded++
        }

        val knownPlans = planRepository.observePlans().firstOrNull().orEmpty()
            .map { it.copy(id = 0) }.toMutableSet()
        var plansAdded = 0
        val wanted = data.plans.map {
            PlanBlock(
                0, categoryId(it.category), projectId(it.category, it.project), it.note,
                it.start, it.end, it.startDate, it.repeat, it.description,
            )
        }
        wanted.forEach { block ->
            if (knownPlans.add(block)) {
                planRepository.save(block)
                plansAdded++
            }
        }
        // Completions need the ids the plans now have.
        if (data.completions.isNotEmpty()) {
            val saved = planRepository.observePlans().firstOrNull().orEmpty()
            data.completions.forEach { completion ->
                val block = wanted.getOrNull(completion.plan) ?: return@forEach
                saved.firstOrNull { it.copy(id = 0) == block }?.let {
                    planRepository.setCompleted(it.id, completion.date, completed = true)
                }
            }
        }

        val knownGoals = goalRepository.observeGoals().firstOrNull().orEmpty()
            .map { it.copy(id = 0) }.toMutableSet()
        var goalsAdded = 0
        data.goals.forEach { item ->
            val goal = Goal(
                0, item.name, categoryId(item.category).takeIf { item.category.isNotBlank() },
                Duration.ofMinutes(item.targetMinutes), item.period, item.type,
            )
            if (knownGoals.add(goal)) {
                goalRepository.save(goal)
                goalsAdded++
            }
        }

        return RestoreResult(entriesAdded, plansAdded, goalsAdded, skipped)
    }
}
