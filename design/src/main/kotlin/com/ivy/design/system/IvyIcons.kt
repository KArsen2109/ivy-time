package com.ivy.design.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** The icon set categories can pick from. Stored by key, so renaming a key breaks old data. */
object IvyIcons {
    val keys: List<String> = listOf(
        "work", "code", "study", "sport", "rest", "food", "social", "fun",
        "music", "travel", "home", "health", "money", "shopping", "other",
    )

    fun of(key: String?): ImageVector = when (key) {
        "work" -> Icons.Rounded.Work
        "code" -> Icons.Rounded.Code
        "study" -> Icons.Rounded.MenuBook
        "sport" -> Icons.Rounded.DirectionsRun
        "rest" -> Icons.Rounded.Bedtime
        "food" -> Icons.Rounded.Restaurant
        "social" -> Icons.Rounded.Groups
        "fun" -> Icons.Rounded.SportsEsports
        "music" -> Icons.Rounded.MusicNote
        "travel" -> Icons.Rounded.Flight
        "home" -> Icons.Rounded.Home
        "health" -> Icons.Rounded.Favorite
        "money" -> Icons.Rounded.AttachMoney
        "shopping" -> Icons.Rounded.ShoppingCart
        else -> Icons.Rounded.Label
    }
}
