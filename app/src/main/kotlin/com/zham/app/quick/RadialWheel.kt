package com.zham.app.quick

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zham.design.system.GlyphIcon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Immutable
data class WheelItem(val id: Long, val label: String, val color: Color, val glyph: String?)

/**
 * Items laid out round a circle, one tap to pick, with [center] in the middle. The first item sits at
 * the top and the rest follow clockwise. With many items the discs shrink and labels are dropped,
 * so the wheel stays usable rather than overlapping itself.
 */
@Composable
fun RadialWheel(
    items: List<WheelItem>,
    onPick: (WheelItem) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val span: Dp = min(maxWidth.value, maxHeight.value).dp
        val n = items.size.coerceAtLeast(1)
        val disc: Dp = when {
            n <= 8 -> 64.dp
            n <= 12 -> 52.dp
            else -> 42.dp
        }
        val showLabels = n <= 10
        // The ring fits inside the smaller side with room for the discs themselves.
        val radius: Dp = ((span - disc) / 2 - if (showLabels) 14.dp else 0.dp).coerceAtLeast(60.dp)

        Box(Modifier.size(span), contentAlignment = Alignment.Center) {
            Box(Modifier.size(disc * 1.6f), contentAlignment = Alignment.Center) { center() }
            items.forEachIndexed { index, item ->
                val angle = -PI / 2 + 2 * PI * index / n
                Column(
                    Modifier
                        .offset(x = radius * cos(angle).toFloat(), y = radius * sin(angle).toFloat())
                        .width(80.dp)
                        .clickable { onPick(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(disc)
                            .clip(CircleShape)
                            .background(item.color.copy(alpha = 0.28f))
                            .border(BorderStroke(2.dp, item.color), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.glyph != null) {
                            GlyphIcon(item.glyph, tint = item.color, size = disc * 0.5f)
                        } else {
                            Text(item.label.take(1).uppercase(), color = item.color, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    if (showLabels) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
