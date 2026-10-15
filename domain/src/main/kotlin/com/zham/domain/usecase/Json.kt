package com.zham.domain.usecase

/**
 * A small JSON reader and writer for the backup file, so the domain stays free of platform classes.
 * Values are Map<String, Any?>, List<Any?>, String, Long, Double, Boolean and null.
 */
internal object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean, is Int, is Long -> out.append(value.toString())
            is Double -> out.append(value.toString())
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    quote(out, key.toString())
                    out.append(':')
                    append(out, item)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            else -> error("Cannot write ${value::class}")
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        text.forEach { char ->
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    /** Throws [IllegalArgumentException] when [text] is not valid JSON. */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        require(reader.atEnd()) { "Unexpected text after the JSON value" }
        return value
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipSpace()
            require(pos < text.length) { "Unexpected end" }
            return when (val char = text[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (char == '-' || char.isDigit()) number() else fail("Unexpected '$char'")
            }
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException("$message at $pos")

        private fun literal(word: String, result: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("Expected $word")
            pos += word.length
            return result
        }

        private fun number(): Any {
            val start = pos
            if (text[pos] == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
            val raw = text.substring(start, pos)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: fail("Bad number $raw")
        }

        private fun string(): String {
            pos++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (pos >= text.length) fail("Unterminated string")
                val char = text[pos++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= text.length) fail("Unterminated escape")
                        when (val escaped = text[pos++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Bad unicode escape")
                                out.append(text.substring(pos, pos + 4).toIntOrNull(16)?.toChar()
                                    ?: fail("Bad unicode escape"))
                                pos += 4
                            }
                            else -> fail("Bad escape \\$escaped")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun array(): List<Any?> {
            pos++
            val items = mutableListOf<Any?>()
            skipSpace()
            if (pos < text.length && text[pos] == ']') { pos++; return items }
            while (true) {
                items += value()
                skipSpace()
                if (pos >= text.length) fail("Unterminated array")
                when (text[pos++]) {
                    ',' -> continue
                    ']' -> return items
                    else -> fail("Expected , or ]")
                }
            }
        }

        private fun obj(): Map<String, Any?> {
            pos++
            val items = linkedMapOf<String, Any?>()
            skipSpace()
            if (pos < text.length && text[pos] == '}') { pos++; return items }
            while (true) {
                skipSpace()
                if (pos >= text.length || text[pos] != '"') fail("Expected a key")
                val key = string()
                skipSpace()
                if (pos >= text.length || text[pos++] != ':') fail("Expected :")
                items[key] = value()
                skipSpace()
                if (pos >= text.length) fail("Unterminated object")
                when (text[pos++]) {
                    ',' -> continue
                    '}' -> return items
                    else -> fail("Expected , or }")
                }
            }
        }
    }
}
