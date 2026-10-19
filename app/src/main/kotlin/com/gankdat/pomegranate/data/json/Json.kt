package com.gankdat.pomegranate.data.json

/**
 * Minimal strict JSON reader. Exists because the platform's `org.json` is a
 * stub on the host JVM, which would leave the OAuth and Picker response
 * parsing untestable. Objects become `Map<String, Any?>`, arrays `List<Any?>`,
 * numbers `Long` (integral) or `Double`, plus `String`, `Boolean` and null.
 */
internal object Json {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) reader.fail("trailing characters")
        return value
    }

    // Safe: the reader only ever builds LinkedHashMap<String, Any?> for objects.
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): JsonObject = parse(text) as? JsonObject ?: throw JsonException("expected a JSON object")

    /** A JSON string literal, quotes included. */
    fun quote(text: String): String = quoteString(text)

    private class Reader(private val s: String) {
        private var i = 0

        fun atEnd() = i >= s.length

        fun fail(message: String): Nothing = throw JsonException("$message at offset $i")

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) fail("unexpected end of input")
            return when (val c = s[i]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else fail("unexpected character '$c'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) fail("invalid literal")
            i += word.length
            return value
        }

        private fun readObject(): JsonObject {
            i++ // {
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                i++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("expected string key")
                val key = readString()
                skipWhitespace()
                if (peek() != ':') fail("expected ':'")
                i++
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return result
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun readArray(): List<Any?> {
            i++ // [
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                i++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return result
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("unterminated escape")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("bad unicode escape")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("bad unicode escape")
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> fail("bad escape '\\$e'")
                        }
                    }
                    else -> {
                        if (c < ' ') fail("control character in string")
                        sb.append(c)
                    }
                }
            }
        }

        private fun readNumber(): Any {
            val start = i
            if (peek() == '-') i++
            while (!atEnd() && s[i].isDigit()) i++
            var isDouble = false
            if (!atEnd() && s[i] == '.') {
                isDouble = true
                i++
                while (!atEnd() && s[i].isDigit()) i++
            }
            if (!atEnd() && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (!atEnd() && (s[i] == '+' || s[i] == '-')) i++
                while (!atEnd() && s[i].isDigit()) i++
            }
            val text = s.substring(start, i)
            if (text == "-" || text.isEmpty()) fail("bad number")
            return if (isDouble) text.toDoubleOrNull() ?: fail("bad number") else text.toLongOrNull() ?: text.toDoubleOrNull() ?: fail("bad number")
        }

        private fun peek(): Char = if (atEnd()) fail("unexpected end of input") else s[i]
    }
}

internal typealias JsonObject = Map<String, Any?>

internal class JsonException(message: String) : RuntimeException(message)

internal fun JsonObject.string(key: String): String? = this[key] as? String

internal fun JsonObject.requireString(key: String): String = string(key) ?: throw JsonException("missing string '$key'")

internal fun JsonObject.long(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Double -> v.toLong()
    is String -> v.toLongOrNull()
    else -> null
}

internal fun JsonObject.bool(key: String): Boolean? = this[key] as? Boolean

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.array(key: String): List<Any?>? = this[key] as? List<Any?>

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.objects(key: String): List<JsonObject> = array(key)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Serialises a flat map of strings/numbers/booleans/nulls; enough for the token file. */
internal fun jsonOf(vararg pairs: Pair<String, Any?>): String = buildString {
    append('{')
    pairs.forEachIndexed { index, (key, value) ->
        if (index > 0) append(',')
        append(quoteString(key)).append(':')
        when (value) {
            null -> append("null")
            is String -> append(quoteString(value))
            is Boolean, is Long, is Int, is Double -> append(value.toString())
            else -> throw JsonException("unsupported value for '$key'")
        }
    }
    append('}')
}

private fun quoteString(text: String): String = buildString {
    append('"')
    for (c in text) {
        when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
        }
    }
    append('"')
}
