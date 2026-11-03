package com.kgs.calendar.json

/*
 * A small, multiplatform stand-in for Android's org.json with the same behaviour where this app
 * relies on it: insertion order, Android's escaping (including "\/"), whole doubles written without
 * a fraction, put(name, null) removing the name, and lenient coercion in the get…/opt… readers.
 */

class JSONException(message: String) : RuntimeException(message)

class JSONObject() {
    private val values = LinkedHashMap<String, Any>()

    constructor(json: String) : this() {
        val parsed = JsonParser(json).nextValue()
        if (parsed !is JSONObject) throw JSONException("Value $json of type ${parsed::class.simpleName} cannot be converted to JSONObject")
        values.putAll(parsed.values)
    }

    constructor(map: Map<*, *>) : this() {
        map.forEach { (k, v) -> if (k != null && v != null) values[k.toString()] = wrap(v) ?: NULL }
    }

    fun length(): Int = values.size

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) {
            values.remove(name)
            return this
        }
        if (value is Double) checkDouble(value)
        if (value is Float) checkDouble(value.toDouble())
        values[name] = value
        return this
    }

    fun put(name: String, value: Boolean): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Int): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Long): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Double): JSONObject = put(name, value as Any?)

    fun putOpt(name: String?, value: Any?): JSONObject {
        if (name == null || value == null) return this
        return put(name, value)
    }

    fun accumulate(name: String, value: Any?): JSONObject {
        val current = values[name] ?: return put(name, value)
        if (current is JSONArray) current.put(value) else values[name] = JSONArray().put(current).put(value)
        return this
    }

    fun remove(name: String?): Any? = name?.let(values::remove)

    fun isNull(name: String?): Boolean {
        val value = name?.let(values::get)
        return value == null || value === NULL
    }

    fun has(name: String?): Boolean = name != null && values.containsKey(name)

    fun opt(name: String?): Any? = name?.let(values::get)

    fun get(name: String): Any = values[name] ?: throw JSONException("No value for $name")

    fun getBoolean(name: String): Boolean = toBoolean(get(name)) ?: throw typeMismatch(name, get(name), "boolean")
    fun optBoolean(name: String?): Boolean = optBoolean(name, false)
    fun optBoolean(name: String?, fallback: Boolean): Boolean = toBoolean(opt(name)) ?: fallback

    fun getDouble(name: String): Double = toDouble(get(name)) ?: throw typeMismatch(name, get(name), "double")
    fun optDouble(name: String?): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String?, fallback: Double): Double = toDouble(opt(name)) ?: fallback

    fun getInt(name: String): Int = toInt(get(name)) ?: throw typeMismatch(name, get(name), "int")
    fun optInt(name: String?): Int = optInt(name, 0)
    fun optInt(name: String?, fallback: Int): Int = toInt(opt(name)) ?: fallback

    fun getLong(name: String): Long = toLong(get(name)) ?: throw typeMismatch(name, get(name), "long")
    fun optLong(name: String?): Long = optLong(name, 0L)
    fun optLong(name: String?, fallback: Long): Long = toLong(opt(name)) ?: fallback

    fun getString(name: String): String = jsonToString(get(name)) ?: throw typeMismatch(name, get(name), "String")
    fun optString(name: String?): String = optString(name, "")
    fun optString(name: String?, fallback: String): String = jsonToString(opt(name)) ?: fallback

    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw typeMismatch(name, get(name), "JSONArray")
    fun optJSONArray(name: String?): JSONArray? = opt(name) as? JSONArray

    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw typeMismatch(name, get(name), "JSONObject")
    fun optJSONObject(name: String?): JSONObject? = opt(name) as? JSONObject

    fun keys(): Iterator<String> = values.keys.toList().iterator()
    fun keySet(): Set<String> = values.keys
    fun names(): JSONArray? = if (values.isEmpty()) null else JSONArray(values.keys.toList())

    override fun toString(): String = StringBuilder().also { writeTo(it) }.toString()

    fun toString(indentSpaces: Int): String = StringBuilder().also { writeTo(it, indentSpaces, 0) }.toString()

    internal fun writeTo(out: StringBuilder, indent: Int = 0, depth: Int = 0) {
        out.append('{')
        var first = true
        for ((key, value) in values) {
            if (!first) out.append(',')
            first = false
            newline(out, indent, depth + 1)
            quote(key, out)
            out.append(':')
            if (indent > 0) out.append(' ')
            writeValue(value, out, indent, depth + 1)
        }
        if (!first) newline(out, indent, depth)
        out.append('}')
    }

    override fun equals(other: Any?): Boolean = other is JSONObject && other.values == values
    override fun hashCode(): Int = values.hashCode()

    companion object {
        /** JSON's null, distinct from a missing name. (Hidden from Objective-C, where NULL is a macro.) */
        @OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
        @kotlin.native.HiddenFromObjC
        val NULL: Any = object {
            override fun equals(other: Any?): Boolean = other === this || other == null
            override fun hashCode(): Int = 0
            override fun toString(): String = "null"
        }

        fun quote(data: String?): String = StringBuilder().also { quote(data.orEmpty(), it) }.toString()

        fun numberToString(number: Number): String = numberText(number)

        fun wrap(value: Any?): Any? = when (value) {
            null -> NULL
            is JSONArray, is JSONObject -> value
            NULL -> value
            is Collection<*> -> JSONArray(value)
            is Array<*> -> JSONArray(value.toList())
            is Map<*, *> -> JSONObject(value)
            is Boolean, is Byte, is Char, is Double, is Float, is Int, is Long, is Short, is String -> value
            else -> value.toString()
        }
    }
}

class JSONArray() {
    private val values = ArrayList<Any>()

    constructor(json: String) : this() {
        val parsed = JsonParser(json).nextValue()
        if (parsed !is JSONArray) throw JSONException("Value $json cannot be converted to JSONArray")
        values.addAll(parsed.values)
    }

    constructor(collection: Collection<*>) : this() {
        collection.forEach { values.add(JSONObject.wrap(it) ?: JSONObject.NULL) }
    }

    fun length(): Int = values.size

    fun put(value: Any?): JSONArray {
        if (value is Double) checkDouble(value)
        values.add(value ?: JSONObject.NULL)
        return this
    }

    fun put(value: Boolean): JSONArray = put(value as Any?)
    fun put(value: Int): JSONArray = put(value as Any?)
    fun put(value: Long): JSONArray = put(value as Any?)
    fun put(value: Double): JSONArray = put(value as Any?)

    fun put(index: Int, value: Any?): JSONArray {
        if (value is Double) checkDouble(value)
        while (values.size <= index) values.add(JSONObject.NULL)
        values[index] = value ?: JSONObject.NULL
        return this
    }

    fun isNull(index: Int): Boolean {
        val value = opt(index)
        return value == null || value === JSONObject.NULL
    }

    fun get(index: Int): Any {
        if (index < 0 || index >= values.size) throw JSONException("Index $index out of range [0..${values.size})")
        return values[index]
    }

    fun opt(index: Int): Any? = values.getOrNull(index)
    fun remove(index: Int): Any? = if (index in values.indices) values.removeAt(index) else null

    fun getBoolean(index: Int): Boolean = toBoolean(get(index)) ?: throw JSONException("Value at $index is not a boolean")
    fun optBoolean(index: Int, fallback: Boolean = false): Boolean = toBoolean(opt(index)) ?: fallback
    fun getDouble(index: Int): Double = toDouble(get(index)) ?: throw JSONException("Value at $index is not a double")
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = toDouble(opt(index)) ?: fallback
    fun getInt(index: Int): Int = toInt(get(index)) ?: throw JSONException("Value at $index is not an int")
    fun optInt(index: Int, fallback: Int = 0): Int = toInt(opt(index)) ?: fallback
    fun getLong(index: Int): Long = toLong(get(index)) ?: throw JSONException("Value at $index is not a long")
    fun optLong(index: Int, fallback: Long = 0L): Long = toLong(opt(index)) ?: fallback
    fun getString(index: Int): String = jsonToString(get(index)) ?: throw JSONException("Value at $index is not a String")
    fun optString(index: Int, fallback: String = ""): String = jsonToString(opt(index)) ?: fallback
    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("Value at $index is not a JSONArray")
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("Value at $index is not a JSONObject")
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    override fun toString(): String = StringBuilder().also { writeTo(it) }.toString()
    fun toString(indentSpaces: Int): String = StringBuilder().also { writeTo(it, indentSpaces, 0) }.toString()

    internal fun writeTo(out: StringBuilder, indent: Int = 0, depth: Int = 0) {
        out.append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) out.append(',')
            newline(out, indent, depth + 1)
            writeValue(value, out, indent, depth + 1)
        }
        if (values.isNotEmpty()) newline(out, indent, depth)
        out.append(']')
    }

    override fun equals(other: Any?): Boolean = other is JSONArray && other.values == values
    override fun hashCode(): Int = values.hashCode()
}

private fun newline(out: StringBuilder, indent: Int, depth: Int) {
    if (indent <= 0) return
    out.append('\n')
    repeat(indent * depth) { out.append(' ') }
}

private fun writeValue(value: Any?, out: StringBuilder, indent: Int, depth: Int) {
    when {
        value == null || value === JSONObject.NULL -> out.append("null")
        value is JSONObject -> value.writeTo(out, indent, depth)
        value is JSONArray -> value.writeTo(out, indent, depth)
        value is Boolean -> out.append(value.toString())
        value is Number -> out.append(numberText(value))
        else -> quote(value.toString(), out)
    }
}

private fun quote(value: String, out: StringBuilder) {
    out.append('"')
    for (c in value) {
        when (c) {
            '"', '\\', '/' -> out.append('\\').append(c)
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\u000C' -> out.append("\\f")
            else -> if (c.code <= 0x1F) {
                out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else {
                out.append(c)
            }
        }
    }
    out.append('"')
}

private fun numberText(number: Number): String {
    val doubleValue = number.toDouble()
    checkDouble(doubleValue)
    if (number is Int || number is Long || number is Short || number is Byte) return number.toString()
    val longValue = number.toLong()
    if (doubleValue == longValue.toDouble()) return longValue.toString()
    return number.toString()
}

private fun checkDouble(value: Double) {
    if (value.isNaN() || value.isInfinite()) throw JSONException("Forbidden numeric value: $value")
}

private fun typeMismatch(name: Any, actual: Any?, type: String): JSONException =
    if (actual == null || actual === JSONObject.NULL) JSONException("Value at $name is null.")
    else JSONException("Value $actual at $name of type ${actual::class.simpleName} cannot be converted to $type")

private fun toBoolean(value: Any?): Boolean? = when (value) {
    is Boolean -> value
    is String -> when {
        value.equals("true", ignoreCase = true) -> true
        value.equals("false", ignoreCase = true) -> false
        else -> null
    }
    else -> null
}

private fun toDouble(value: Any?): Double? = when (value) {
    is Double -> value
    is Number -> value.toDouble()
    is String -> value.trim().toDoubleOrNull()
    else -> null
}

private fun toInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Number -> value.toInt()
    is String -> value.trim().toDoubleOrNull()?.toInt()
    else -> null
}

private fun toLong(value: Any?): Long? = when (value) {
    is Long -> value
    is Number -> value.toLong()
    is String -> value.trim().let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }
    else -> null
}

/** As Android's JSON.toString: a JSON null reads "null"; only a missing value is null. */
private fun jsonToString(value: Any?): String? = when (value) {
    null -> null
    is String -> value
    is Number -> numberText(value)
    else -> value.toString()
}

private class JsonParser(private val input: String) {
    private var pos = 0

    fun nextValue(): Any {
        skipWhitespace()
        if (pos >= input.length) throw JSONException("End of input at character $pos of $input")
        return when (val c = input[pos]) {
            '{' -> { pos++; readObject() }
            '[' -> { pos++; readArray() }
            '"', '\'' -> { pos++; readString(c) }
            else -> readLiteral()
        }
    }

    private fun skipWhitespace() {
        while (pos < input.length) {
            val c = input[pos]
            when {
                c == ' ' || c == '\t' || c == '\n' || c == '\r' -> pos++
                c == '/' && pos + 1 < input.length && input[pos + 1] == '/' -> {
                    while (pos < input.length && input[pos] != '\n' && input[pos] != '\r') pos++
                }
                c == '/' && pos + 1 < input.length && input[pos + 1] == '*' -> {
                    val end = input.indexOf("*/", pos + 2)
                    if (end < 0) throw JSONException("Unterminated comment")
                    pos = end + 2
                }
                else -> return
            }
        }
    }

    private fun readObject(): JSONObject {
        val result = JSONObject()
        skipWhitespace()
        if (peek() == '}') { pos++; return result }
        while (true) {
            skipWhitespace()
            val name = nextValue().toString()
            skipWhitespace()
            val sep = next()
            if (sep != ':' && sep != '=') throw JSONException("Expected ':' after $name at $pos")
            if (sep == '=' && peek() == '>') pos++
            result.put(name, nextValue())
            skipWhitespace()
            when (next()) {
                '}' -> return result
                ',', ';' -> continue
                else -> throw JSONException("Unterminated object at character $pos")
            }
        }
    }

    private fun readArray(): JSONArray {
        val result = JSONArray()
        var hasTrailingSeparator = false
        while (true) {
            skipWhitespace()
            when (peek()) {
                ']' -> {
                    pos++
                    if (hasTrailingSeparator) result.put(JSONObject.NULL)
                    return result
                }
                ',', ';' -> {
                    pos++
                    result.put(JSONObject.NULL)
                    hasTrailingSeparator = true
                    continue
                }
                null -> throw JSONException("Unterminated array at character $pos")
            }
            result.put(nextValue())
            skipWhitespace()
            when (next()) {
                ']' -> return result
                ',', ';' -> { hasTrailingSeparator = true; continue }
                else -> throw JSONException("Unterminated array at character $pos")
            }
        }
    }

    private fun readString(quote: Char): String {
        val sb = StringBuilder()
        while (pos < input.length) {
            val c = input[pos++]
            when {
                c == quote -> return sb.toString()
                c == '\\' -> {
                    if (pos >= input.length) throw JSONException("Unterminated escape sequence")
                    when (val e = input[pos++]) {
                        'u' -> {
                            if (pos + 4 > input.length) throw JSONException("Unterminated escape sequence")
                            sb.append(input.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        'f' -> sb.append('\u000C')
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("Unterminated string starting near $pos")
    }

    private fun readLiteral(): Any {
        val start = pos
        while (pos < input.length) {
            val c = input[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c in ",:]}/\\\"[{;=#") break
            pos++
        }
        val literal = input.substring(start, pos)
        if (literal.isEmpty()) throw JSONException("Expected literal value at character $pos")
        when {
            literal.equals("null", ignoreCase = true) -> return JSONObject.NULL
            literal.equals("true", ignoreCase = true) -> return true
            literal.equals("false", ignoreCase = true) -> return false
        }
        if (literal.indexOf('.') == -1 && literal.indexOf('e') == -1 && literal.indexOf('E') == -1) {
            val asLong = when {
                literal.startsWith("0x") || literal.startsWith("0X") -> literal.substring(2).toLongOrNull(16)
                literal.startsWith("0") && literal.length > 1 -> literal.substring(1).toLongOrNull(8)
                else -> literal.toLongOrNull()
            }
            if (asLong != null) {
                return if (asLong in Int.MIN_VALUE..Int.MAX_VALUE) asLong.toInt() else asLong
            }
        }
        literal.toDoubleOrNull()?.let { return it }
        return literal
    }

    private fun peek(): Char? = input.getOrNull(pos)
    private fun next(): Char? = input.getOrNull(pos++)
}
