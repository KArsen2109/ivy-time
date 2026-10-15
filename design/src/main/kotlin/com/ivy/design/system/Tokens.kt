package com.ivy.design.system

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.design.R

/**
 * The chrome is near monochrome on purpose: all colour in the app belongs to the
 * categories, so a glance at a screen reads as data rather than decoration.
 */
object Ink {
    val Midnight = Color(0xFF0B1220)
    val True = Color(0xFF000000)
    val Paper = Color(0xFFF4F6F9)
    val White = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF141C2B)
    val SurfaceDarkRaised = Color(0xFF1C2638)
    val SlateLight = Color(0xFF68718A)
    val SlateDark = Color(0xFF8A94AC)
    val LineLight = Color(0xFFE3E7EF)
    val LineDark = Color(0xFF202939)

    /** The one interactive accent. */
    val Violet = Color(0xFF5B4BFF)
    val VioletSoft = Color(0xFFE9E7FF)

    /** Reserved for the present moment and for a running timer. Nothing else. */
    val Signal = Color(0xFFFF8A3D)
}

/**
 * Eight category colours, picked to sit at a similar weight so no category shouts,
 * and to stay clear of [Ink.Signal] so the now-line is never mistaken for data.
 */
val CategoryPalette: List<Color> = listOf(
    Color(0xFF5B4BFF), // violet
    Color(0xFF2E9BFF), // azure
    Color(0xFF12B886), // jade
    Color(0xFFE8C53F), // mustard
    Color(0xFFF2537A), // rose
    Color(0xFFA05BFF), // grape
    Color(0xFF2FBFC9), // teal
    Color(0xFF7A8CA8), // steel
)

private val Instrument = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    Font(R.font.instrument_sans_bold, FontWeight.Bold),
)

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Instrument,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = Trim,
)

/** A 1.25 scale off 16sp. The large sizes exist for one thing each: the clock and the date. */
val IvyTypography = Typography(
    displayLarge = style(44, 46, FontWeight.Bold, -1.2),
    displayMedium = style(34, 38, FontWeight.Bold, -0.8),
    displaySmall = style(28, 32, FontWeight.Bold, -0.6),
    headlineMedium = style(25, 30, FontWeight.Bold, -0.4),
    headlineSmall = style(20, 26, FontWeight.SemiBold, -0.2),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.2),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(13, 18, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(14, 18, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.2),
)

/** Radii carry hierarchy: the bigger the surface, the softer the corner. */
object IvyShapes {
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Card = RoundedCornerShape(20.dp)
    val Field = RoundedCornerShape(14.dp)
    val Block = RoundedCornerShape(10.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

object Spacing {
    val Gutter = 20.dp
    val HourHeight = 56.dp
    val HourGutter = 46.dp
}

/** Extra colours Material3's scheme has no slot for. */
@Immutable
data class IvyExtraColors(
    val signal: Color,
    val line: Color,
    val slate: Color,
    val planFill: Color,
    val raised: Color,
)
