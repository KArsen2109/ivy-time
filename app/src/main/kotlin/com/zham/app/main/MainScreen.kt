package com.zham.app.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zham.design.system.ZhamTheme
import com.zham.app.categories.CategoriesScreen
import com.zham.app.day.SelectedDay
import com.zham.app.day.DayScreen
import com.zham.app.entry.EntryScreen
import com.zham.app.goals.GoalsScreen
import com.zham.app.more.MoreScreen
import com.zham.app.plan.PlanEditorScreen
import com.zham.app.projects.ProjectsScreen
import com.zham.app.reports.ReportsScreen
import com.zham.app.timer.TimerScreen
import com.zham.app.week.WeekScreen
import java.time.LocalDate

private object Routes {
    const val TIMER = "timer"
    const val DAY = "day"
    const val WEEK = "week"
    const val REPORTS = "reports"
    const val MORE = "more"
    const val CATEGORIES = "categories"
    const val PROJECTS = "projects"
    const val GOALS = "goals"
    const val ENTRY = "entry/{entryId}?date={date}"
    const val PLAN = "plan/{planId}?date={date}&minutes={minutes}"

    fun entry(id: Long, date: LocalDate? = null) =
        "entry/$id?date=${date?.toEpochDay() ?: -1}"

    fun plan(id: Long, date: LocalDate? = null, minutes: Int = 540) =
        "plan/$id?date=${date?.toEpochDay() ?: -1}&minutes=$minutes"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Timer(Routes.TIMER, "Timer", Icons.Rounded.Timer),
    Day(Routes.DAY, "Day", Icons.Rounded.Today),
    Week(Routes.WEEK, "Week", Icons.Rounded.CalendarViewWeek),
    Reports(Routes.REPORTS, "Reports", Icons.Rounded.BarChart),
    More(Routes.MORE, "More", Icons.Rounded.MoreHoriz),
}

@Composable
fun MainScreen(selectedDay: SelectedDay) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabsVisible = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (tabsVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = ZhamTheme.extra.slate,
                                unselectedTextColor = ZhamTheme.extra.slate,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TIMER) {
                TimerScreen(onEditRecord = { navController.navigate(Routes.entry(it)) })
            }
            composable(Routes.DAY) {
                DayScreen(
                    onPlanAt = { date, minutes ->
                        navController.navigate(Routes.plan(0, date, minutes))
                    },
                    onEditPlan = { navController.navigate(Routes.plan(it)) },
                    onEditEntry = { navController.navigate(Routes.entry(it)) },
                    onLogTime = { navController.navigate(Routes.entry(0, it)) },
                )
            }
            composable(Routes.WEEK) {
                WeekScreen(
                    onOpenDay = { date ->
                        selectedDay.set(date)
                        navController.switchTab(Routes.DAY)
                    }
                )
            }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen() }
            composable(Routes.PROJECTS) { ProjectsScreen() }
            composable(Routes.GOALS) { GoalsScreen() }
            composable(Routes.ENTRY) { EntryScreen(onClose = { navController.popBackStack() }) }
            composable(Routes.PLAN) { PlanEditorScreen(onClose = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
