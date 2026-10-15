package com.zham.design.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons a category can use. They are stored by key, so a key is never renamed or reused;
 * new icons are only ever added at the end of a group.
 */
object ZhamIcons {
    private val icons: Map<String, ImageVector> = linkedMapOf(
        // Work and study
        "work" to Icons.Rounded.Work,
        "code" to Icons.Rounded.Code,
        "computer" to Icons.Rounded.Computer,
        "meeting" to Icons.Rounded.Groups,
        "calls" to Icons.Rounded.Call,
        "mail" to Icons.Rounded.Mail,
        "writing" to Icons.Rounded.Edit,
        "planning" to Icons.Rounded.Event,
        "idea" to Icons.Rounded.Lightbulb,
        "study" to Icons.Rounded.MenuBook,
        "school" to Icons.Rounded.School,
        // Body and health
        "sport" to Icons.Rounded.DirectionsRun,
        "gym" to Icons.Rounded.FitnessCenter,
        "swim" to Icons.Rounded.Pool,
        "cycling" to Icons.Rounded.DirectionsBike,
        "hiking" to Icons.Rounded.Hiking,
        "meditation" to Icons.Rounded.SelfImprovement,
        "health" to Icons.Rounded.Favorite,
        "medical" to Icons.Rounded.MedicalServices,
        "rest" to Icons.Rounded.Bedtime,
        // Home and daily life
        "home" to Icons.Rounded.Home,
        "cleaning" to Icons.Rounded.CleaningServices,
        "laundry" to Icons.Rounded.LocalLaundryService,
        "repairs" to Icons.Rounded.Build,
        "food" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.LocalCafe,
        "shopping" to Icons.Rounded.ShoppingCart,
        "money" to Icons.Rounded.AttachMoney,
        "savings" to Icons.Rounded.Savings,
        "family" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "garden" to Icons.Rounded.Park,
        "nature" to Icons.Rounded.Eco,
        // People and leisure
        "social" to Icons.Rounded.Forum,
        "fun" to Icons.Rounded.SportsEsports,
        "movies" to Icons.Rounded.Movie,
        "music" to Icons.Rounded.MusicNote,
        "listening" to Icons.Rounded.Headphones,
        "art" to Icons.Rounded.Palette,
        "photos" to Icons.Rounded.PhotoCamera,
        "phone" to Icons.Rounded.Smartphone,
        // Getting around
        "travel" to Icons.Rounded.Flight,
        "car" to Icons.Rounded.DirectionsCar,
        "commute" to Icons.Rounded.DirectionsBus,
        // Anything else
        "star" to Icons.Rounded.Star,
        "other" to Icons.Rounded.Label,
    )

    val keys: List<String> = icons.keys.toList()

    fun of(key: String?): ImageVector = icons[key] ?: Icons.Rounded.Label
}
