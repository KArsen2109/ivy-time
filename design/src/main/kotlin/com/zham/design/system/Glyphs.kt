package com.zham.design.system

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Emoji a category or project can wear. A glyph is stored as a single text key: either one of the
 * [ZhamIcons] keys, or "emoji:" followed by the emoji itself.
 */
object ZhamEmojis {
    private const val PREFIX = "emoji:"

    val groups: List<Pair<String, List<String>>> = listOf(
        "Work and study" to listOf(
            "💼", "💻", "🖥️", "⌨️", "📊", "📈", "📅", "📝", "📚", "🎓", "✏️", "🔬",
            "🧪", "📞", "📧", "🗂️", "📌", "💡", "🧠", "🏢", "🛠️", "⚙️", "🔧", "📐",
        ),
        "Body and health" to listOf(
            "🏃", "🏋️", "🚴", "🏊", "🧘", "⚽", "🏀", "🎾", "🏐", "🥊", "⛷️", "🏔️",
            "🚶", "💪", "❤️", "🩺", "💊", "🛌", "😴", "🍎", "🥗", "💧", "🧖", "🏥",
        ),
        "Home" to listOf(
            "🏠", "🧹", "🧺", "🛒", "🍳", "🔨", "🪴", "🌱", "🚿", "🛁", "🛋️", "🧼",
            "🗑️", "📦", "🔑", "🐕", "🐈", "👶", "👪", "🧸",
        ),
        "Food and drink" to listOf(
            "☕", "🍵", "🍕", "🍔", "🍜", "🍣", "🥐", "🍰", "🍺", "🍷", "🥤", "🍿", "🥑", "🥪",
        ),
        "Fun and people" to listOf(
            "🎮", "🎬", "🎵", "🎧", "🎸", "🎹", "🎨", "📷", "📖", "🎲", "🧩", "🎉",
            "👥", "💬", "📱", "📺", "🎭", "🎤",
        ),
        "Travel" to listOf(
            "✈️", "🚗", "🚌", "🚆", "🚲", "🛵", "⛵", "🚕", "🗺️", "🏖️", "⛺", "🧳", "🌍", "🚀",
        ),
        "Money and other" to listOf(
            "💰", "💳", "🏦", "🪙", "💵", "🧾", "📉", "🎁", "🛍️", "🏷️", "⭐", "🔥",
            "✅", "⏰", "⏳", "🎯", "🌙", "☀️", "🌧️", "🌳",
        ),
    )

    fun key(emoji: String): String = PREFIX + emoji

    fun isEmoji(key: String?): Boolean = key != null && key.startsWith(PREFIX)

    fun emojiOf(key: String): String = key.removePrefix(PREFIX)
}

/** Draws a stored glyph: an emoji in its own colours, or a vector icon in [tint]. */
@Composable
fun GlyphIcon(key: String?, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    if (ZhamEmojis.isEmoji(key)) {
        // An emoji is a character, so its size is a font size; slightly under the box keeps it from clipping.
        val fontSize: TextUnit = (size.value * 0.9f).sp
        Text(text = ZhamEmojis.emojiOf(key!!), fontSize = fontSize, modifier = modifier)
    } else {
        Icon(ZhamIcons.of(key), contentDescription = null, tint = tint, modifier = modifier.size(size))
    }
}
