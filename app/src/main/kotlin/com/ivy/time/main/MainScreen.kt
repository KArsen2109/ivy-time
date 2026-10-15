package com.ivy.time.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivy.design.system.GradientCircleButton
import com.ivy.design.system.IvyGradients
import com.ivy.time.categories.CategoriesScreen
import com.ivy.time.entry.EntryScreen
import com.ivy.time.goals.GoalsScreen
import com.ivy.time.home.HomeScreen
import com.ivy.time.more.MoreScreen
import com.ivy.time.planned.PlannedScreen
import com.ivy.time.projects.ProjectsScreen
import com.ivy.time.reports.ReportsScreen
import com.ivy.time.settings.SettingsScreen

private object Routes {
    const val HOME = "home"
    const val REPORTS = "reports"
    const val PLANNED = "planned"
    const val MORE = "more"
    const val CATEGORIES = "categories"
    const val PROJECTS = "projects"
    const val GOALS = "goals"
    const val SETTINGS = "settings"
    const val ENTRY = "entry/{entryId}"

    fun entry(id: Long) = "entry/$id"
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Timer", Icons.Rounded.Timer),
    Reports(Routes.REPORTS, "Reports", Icons.Rounded.BarChart),
    Planned(Routes.PLANNED, "Planned", Icons.Rounded.EventNote),
    More(Routes.MORE, "More", Icons.Rounded.MoreHoriz),
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabVisible = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (tabVisible) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tabVisible) {
                GradientCircleButton(
                    colors = IvyGradients.Ivy,
                    onClick = { navController.navigate(Routes.entry(0)) },
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "New record", tint = Color.White)
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onEditEntry = { navController.navigate(Routes.entry(it)) })
            }
            composable(Routes.REPORTS) { ReportsScreen() }
            composable(Routes.PLANNED) { PlannedScreen() }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                    onOpenGoals = { navController.navigate(Routes.GOALS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CATEGORIES) { CategoriesScreen() }
            composable(Routes.PROJECTS) { ProjectsScreen() }
            composable(Routes.GOALS) { GoalsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.ENTRY) {
                EntryScreen(onClose = { navController.popBackStack() })
            }
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
