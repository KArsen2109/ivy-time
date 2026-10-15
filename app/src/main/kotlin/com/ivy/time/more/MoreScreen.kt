package com.ivy.time.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.design.system.SectionTitle
import com.ivy.time.ui.ScreenTitle
import com.ivy.time.ui.SettingRow

@Composable
fun MoreScreen(
    onOpenCategories: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    ) {
        item {
            ScreenTitle("More")
            SectionTitle("Organise")
            SettingRow(
                title = "Categories",
                subtitle = "What your time goes to",
                onClick = onOpenCategories,
            )
            SettingRow(
                title = "Projects",
                subtitle = "Longer efforts inside a category",
                onClick = onOpenProjects,
            )
            SettingRow(
                title = "Goals",
                subtitle = "At least or at most, per week or month",
                onClick = onOpenGoals,
            )
            SectionTitle("App")
            SettingRow(
                title = "Settings",
                subtitle = "Theme, week start, backup",
                onClick = onOpenSettings,
            )
        }
    }
}
