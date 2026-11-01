package com.zham.domain.usecase

/**
 * The small markup notes are written in. It is plain text, so a note is readable anywhere:
 *
 *   `- item` bullet, `1. item` numbered, `[ ] item` and `[x] item` checklist,
 *   `**bold**`, `*italic*`, `[c=#RRGGBB]coloured[/c]`, and a backslash before a marker to write it literally.
 */
enum class LineKind { Plain, Bullet, Numbered, Check }

data class NoteSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** ARGB, or null for the normal text colour. */
    val color: Int? = null,
)

data class NoteLine(
    val kind: LineKind,
    val checked: Boolean,
    /** Position in a run of numbered lines, counting from 1. */
    val number: Int,
    val spans: List<NoteSpan>,
    /** Which line of the body this came from, so a tap on a checkbox can change that line. */
    val sourceIndex: Int,
)

private val NUMBERED = Regex("^(\\d{1,3})\\. ")
private val COLOR_OPEN = Regex("^\\[c=#([0-9a-fA-F]{6})]")

private class Prefix(val kind: LineKind, val checked: Boolean, val length: Int)

private fun prefixOf(line: String): Prefix = when {
    line.startsWith("[ ] ") -> Prefix(LineKind.Check, false, 4)
    line.startsWith("[x] ") || line.startsWith("[X] ") -> Prefix(LineKind.Check, true, 4)
    line.startsWith("- ") -> Prefix(LineKind.Bullet, false, 2)
    else -> NUMBERED.find(line)?.let { Prefix(LineKind.Numbered, false, it.value.length) }
        ?: Prefix(LineKind.Plain, false, 0)
}

fun parseNote(body: String): List<NoteLine> {
    var run = 0
    return body.split("\n").mapIndexed { index, line ->
        val prefix = prefixOf(line)
        run = if (prefix.kind == LineKind.Numbered) run + 1 else 0
        NoteLine(prefix.kind, prefix.checked, run, parseSpans(line.substring(prefix.length)), index)
    }
}

/** Turns the inline markers of one line into styled pieces. A marker never closed lasts to the line's end. */
fun parseSpans(text: String): List<NoteSpan> {
    val spans = mutableListOf<NoteSpan>()
    val current = StringBuilder()
    var bold = false
    var italic = false
    var color: Int? = null
    fun flush() {
        if (current.isNotEmpty()) {
            spans += NoteSpan(current.toString(), bold, italic, color)
            current.clear()
        }
    }
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        when {
            rest.startsWith("\\") && rest.length > 1 -> {
                current.append(rest[1])
                i += 2
            }
            rest.startsWith("**") -> {
                flush(); bold = !bold; i += 2
            }
            rest.startsWith("*") -> {
                flush(); italic = !italic; i += 1
            }
            rest.startsWith("[/c]") -> {
                flush(); color = null; i += 4
            }
            COLOR_OPEN.containsMatchIn(rest) -> {
                val match = COLOR_OPEN.find(rest)!!
                flush()
                color = (0xFF000000 or match.groupValues[1].toLong(16)).toInt()
                i += match.value.length
            }
            else -> {
                current.append(rest[0]); i += 1
            }
        }
    }
    flush()
    return spans
}

/** The note with the checkbox on [lineIndex] flipped; any other line is left alone. */
fun toggleCheck(body: String, lineIndex: Int): String {
    val lines = body.split("\n").toMutableList()
    val line = lines.getOrNull(lineIndex) ?: return body
    lines[lineIndex] = when {
        line.startsWith("[ ] ") -> "[x] " + line.substring(4)
        line.startsWith("[x] ") || line.startsWith("[X] ") -> "[ ] " + line.substring(4)
        else -> return body
    }
    return lines.joinToString("\n")
}

/** The text without any markup, for a list preview. */
fun plainText(body: String): String = parseNote(body).joinToString("\n") { line ->
    val text = line.spans.joinToString("") { it.text }
    when (line.kind) {
        LineKind.Bullet -> "• $text"
        LineKind.Numbered -> "${line.number}. $text"
        LineKind.Check -> (if (line.checked) "☑ " else "☐ ") + text
        LineKind.Plain -> text
    }
}

/** A text and where the selection sits in it, after an editing action. */
data class Edit(val text: String, val selStart: Int, val selEnd: Int)

/**
 * Wraps the selection in a marker pair, or takes the pair off when it is already there. With nothing
 * selected the pair is inserted and the cursor placed between.
 */
fun toggleWrap(text: String, selStart: Int, selEnd: Int, open: String, close: String = open): Edit {
    val start = minOf(selStart, selEnd).coerceIn(0, text.length)
    val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
    val before = text.substring(0, start)
    val inside = text.substring(start, end)
    val after = text.substring(end)
    return if (before.endsWith(open) && after.startsWith(close)) {
        Edit(before.dropLast(open.length) + inside + after.drop(close.length), start - open.length, end - open.length)
    } else {
        Edit(before + open + inside + close + after, start + open.length, end + open.length)
    }
}

/** Gives the line holding the cursor the list style [kind], or removes it when the line already has it. */
fun setLineKind(text: String, cursor: Int, kind: LineKind): Edit {
    val at = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', at - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', at).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)
    val old = prefixOf(line)
    val newPrefix = if (old.kind == kind || kind == LineKind.Plain) "" else when (kind) {
        LineKind.Bullet -> "- "
        LineKind.Numbered -> "1. "
        LineKind.Check -> "[ ] "
        LineKind.Plain -> ""
    }
    val newLine = newPrefix + line.substring(old.length)
    val moved = (at + newPrefix.length - old.length).coerceIn(lineStart, lineStart + newLine.length)
    return Edit(text.substring(0, lineStart) + newLine + text.substring(lineEnd), moved, moved)
}

/**
 * After a new line is typed at the end of a list item, carries the list on: the next bullet, the next
 * number, a fresh unchecked box. Pressing it on an empty item ends the list. Returns null when
 * the change was not a new line typed after a list item.
 */
fun continueList(old: String, new: String, cursor: Int): Edit? {
    if (new.length != old.length + 1 || cursor < 1 || new.getOrNull(cursor - 1) != '\n') return null
    if (new.removeRange(cursor - 1, cursor) != old) return null
    val previousStart = new.lastIndexOf('\n', cursor - 2).let { if (it < 0) 0 else it + 1 }
    val previous = new.substring(previousStart, cursor - 1)
    val prefix = prefixOf(previous)
    if (prefix.kind == LineKind.Plain) return null
    val content = previous.substring(prefix.length)
    if (content.isBlank()) {
        // An empty item: remove it and leave the list.
        val text = new.substring(0, previousStart) + new.substring(cursor - 1)
        return Edit(text, previousStart + 1, previousStart + 1)
    }
    val next = when (prefix.kind) {
        LineKind.Bullet -> "- "
        LineKind.Check -> "[ ] "
        LineKind.Numbered -> "${(NUMBERED.find(previous)!!.groupValues[1].toInt()) + 1}. "
        LineKind.Plain -> ""
    }
    val text = new.substring(0, cursor) + next + new.substring(cursor)
    return Edit(text, cursor + next.length, cursor + next.length)
}
