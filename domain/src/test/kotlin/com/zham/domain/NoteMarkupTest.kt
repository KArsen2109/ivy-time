package com.zham.domain

import com.zham.domain.usecase.Edit
import com.zham.domain.usecase.LineKind
import com.zham.domain.usecase.NoteSpan
import com.zham.domain.usecase.continueList
import com.zham.domain.usecase.parseNote
import com.zham.domain.usecase.parseSpans
import com.zham.domain.usecase.plainText
import com.zham.domain.usecase.setLineKind
import com.zham.domain.usecase.toggleCheck
import com.zham.domain.usecase.toggleWrap
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NoteMarkupTest {
    @Test
    fun `lines get their kind, number and checked state`() {
        val lines = parseNote("Shopping\n- milk\n1. first\n2. second\n[ ] bread\n[x] eggs\nnote")
        lines.map { it.kind } shouldBe listOf(
            LineKind.Plain, LineKind.Bullet, LineKind.Numbered, LineKind.Numbered,
            LineKind.Check, LineKind.Check, LineKind.Plain,
        )
        lines.map { it.number } shouldBe listOf(0, 0, 1, 2, 0, 0, 0)
        lines.map { it.checked } shouldBe listOf(false, false, false, false, false, true, false)
    }

    @Test
    fun `inline markers become styled pieces`() {
        parseSpans("a **b** *c* [c=#FF0000]d[/c]") shouldBe listOf(
            NoteSpan("a "), NoteSpan("b", bold = true), NoteSpan(" "), NoteSpan("c", italic = true),
            NoteSpan(" "), NoteSpan("d", color = 0xFFFF0000.toInt()),
        )
    }

    @Test
    fun `a backslash writes a marker literally and an unclosed one lasts to the end`() {
        parseSpans("2 \\* 3") shouldBe listOf(NoteSpan("2 * 3"))
        parseSpans("**all bold") shouldBe listOf(NoteSpan("all bold", bold = true))
    }

    @Test
    fun `a tap on a checkbox flips only that line`() {
        toggleCheck("[ ] a\n[x] b", 0) shouldBe "[x] a\n[x] b"
        toggleCheck("[ ] a\n[x] b", 1) shouldBe "[ ] a\n[ ] b"
        toggleCheck("plain", 0) shouldBe "plain"
    }

    @Test
    fun `wrapping a selection and unwrapping it again`() {
        toggleWrap("hello world", 6, 11, "**") shouldBe Edit("hello **world**", 8, 13)
        toggleWrap("hello **world**", 8, 13, "**") shouldBe Edit("hello world", 6, 11)
        toggleWrap("ab", 1, 1, "*") shouldBe Edit("a**b", 2, 2)
    }

    @Test
    fun `a line takes a list style and gives it back`() {
        setLineKind("milk", 2, LineKind.Bullet) shouldBe Edit("- milk", 4, 4)
        setLineKind("- milk", 4, LineKind.Bullet) shouldBe Edit("milk", 2, 2)
        setLineKind("- milk", 4, LineKind.Check) shouldBe Edit("[ ] milk", 6, 6)
        setLineKind("a\nmilk", 4, LineKind.Numbered) shouldBe Edit("a\n1. milk", 7, 7)
    }

    @Test
    fun `enter after a list item continues the list and enter on an empty one ends it`() {
        continueList("- milk", "- milk\n", 7) shouldBe Edit("- milk\n- ", 9, 9)
        continueList("1. a", "1. a\n", 5) shouldBe Edit("1. a\n2. ", 8, 8)
        continueList("[x] a", "[x] a\n", 6) shouldBe Edit("[x] a\n[ ] ", 10, 10)
        continueList("- a\n- ", "- a\n- \n", 7) shouldBe Edit("- a\n\n", 5, 5)
        continueList("plain", "plain\n", 6) shouldBe null
        continueList("ab", "axb", 2) shouldBe null
    }

    @Test
    fun `plain text keeps the list shape without markers`() {
        plainText("**Title**\n- a\n[x] b") shouldBe "Title\n• a\n☑ b"
    }
}
