package com.ivy.design.system

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalIvyExtra = staticCompositionLocalOf {
    IvyExtraColors(
        signal = Ink.Signal,
        line = Ink.LineLight,
        slate = Ink.SlateLight,
        planFill = Ink.LineLight,
        raised = Ink.White,
    )
}

object IvyTheme {
    val extra: IvyExtraColors
        @Composable
        @ReadOnlyComposable
        get() = LocalIvyExtra.current
}

@Composable
fun IvyTimeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val background = when {
        dark && amoled -> Ink.True
        dark -> Ink.Midnight
        else -> Ink.Paper
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Ink.Violet,
            onPrimary = Ink.White,
            primaryContainer = Ink.Violet.copy(alpha = 0.22f),
            onPrimaryContainer = Ink.White,
            secondary = Ink.SlateDark,
            onSecondary = Ink.Midnight,
            background = background,
            onBackground = Ink.White,
            surface = if (amoled) Ink.True else Ink.SurfaceDark,
            onSurface = Ink.White,
            surfaceVariant = if (amoled) Ink.SurfaceDark else Ink.SurfaceDarkRaised,
            onSurfaceVariant = Ink.SlateDark,
            outline = Ink.LineDark,
            outlineVariant = Ink.LineDark,
            error = Color_Error,
            onError = Ink.White,
        )
    } else {
        lightColorScheme(
            primary = Ink.Violet,
            onPrimary = Ink.White,
            primaryContainer = Ink.VioletSoft,
            onPrimaryContainer = Ink.Violet,
            secondary = Ink.SlateLight,
            onSecondary = Ink.White,
            background = background,
            onBackground = Ink.Midnight,
            surface = Ink.White,
            onSurface = Ink.Midnight,
            surfaceVariant = Ink.White,
            onSurfaceVariant = Ink.SlateLight,
            outline = Ink.LineLight,
            outlineVariant = Ink.LineLight,
            error = Color_Error,
            onError = Ink.White,
        )
    }

    val extra = IvyExtraColors(
        signal = Ink.Signal,
        line = if (dark) Ink.LineDark else Ink.LineLight,
        slate = if (dark) Ink.SlateDark else Ink.SlateLight,
        planFill = if (dark) Ink.SurfaceDarkRaised else Ink.White,
        raised = if (dark) Ink.SurfaceDarkRaised else Ink.White,
    )

    CompositionLocalProvider(LocalIvyExtra provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IvyTypography,
            shapes = Shapes(
                extraSmall = IvyShapes.Block,
                small = IvyShapes.Field,
                medium = IvyShapes.Field,
                large = IvyShapes.Card,
                extraLarge = IvyShapes.Card,
            ),
            content = content,
        )
    }
}

private val Color_Error = androidx.compose.ui.graphics.Color(0xFFF2537A)
