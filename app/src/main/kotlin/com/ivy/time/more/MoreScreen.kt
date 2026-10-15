package com.ivy.time.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivy.design.system.ScreenHeading
import com.ivy.design.system.SectionLabel
import com.ivy.design.system.SettingRow

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
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            ScreenHeading("More")
            SectionLabel("Organise")
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
                subtitle = "Weekly and monthly targets",
                onClick = onOpenGoals,
            )
            SectionLabel("App")
            SettingRow(
                title = "Settings",
                subtitle = "Theme, week start, backup",
                onClick = onOpenSettings,
            )
        }
    }
}
