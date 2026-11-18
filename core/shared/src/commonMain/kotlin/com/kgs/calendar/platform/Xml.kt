package com.kgs.calendar.platform

/**
 * A minimal namespace-aware XML DOM for the WebDAV and OCS responses the app reads. DTDs are
 * skipped and never resolved, so external entities can't be loaded.
 */
class XmlElement internal constructor(
    val localName: String,
    val namespaceURI: String?,
    val attributes: Map<String, String>,
) {
    val children: MutableList<Any> = mutableListOf() // XmlElement or String

    val childElements: List<XmlElement> get() = children.filterIsInstance<XmlElement>()

    /** All text inside, as DOM's textContent. */
    val textContent: String
        get() = buildString { appendText(this@XmlElement, this) }

    /** Descendants (not this element) in document order whose local name is [localName]; "*" matches any namespace. */
    fun getElementsByTagNameNS(namespace: String, localName: String): List<XmlElement> {
        val result = mutableListOf<XmlElement>()
        fun visit(element: XmlElement) {
            for (child in element.childElements) {
                if ((localName == "*" || child.localName == localName) && (namespace == "*" || child.namespaceURI == namespace)) result += child
                visit(child)
            }
        }
        visit(this)
        return result
    }

    fun getElementsByTagName(name: String): List<XmlElement> = getElementsByTagNameNS("*", name.substringAfter(':'))

    fun attribute(name: String): String? = attributes[name] ?: attributes.entries.firstOrNull { it.key.substringAfter(':') == name }?.value

    private fun appendText(element: XmlElement, out: StringBuilder) {
        for (child in element.children) when (child) {
            is String -> out.append(child)
            is XmlElement -> appendText(child, out)
        }
    }
}

class XmlDocument(val documentElement: XmlElement) {
    fun getElementsByTagNameNS(namespace: String, localName: String): List<XmlElement> {
        val inside = documentElement.getElementsByTagNameNS(namespace, localName)
        val self = (localName == "*" || documentElement.localName == localName) && (namespace == "*" || documentElement.namespaceURI == namespace)
        return if (self) listOf(documentElement) + inside else inside
    }

    fun getElementsByTagName(name: String): List<XmlElement> = getElementsByTagNameNS("*", name.substringAfter(':'))
}

class XmlParseException(message: String) : IllegalArgumentException(message)

/** As XML 1.0 section 2.11: line breaks are normalised to LF before parsing (&#13; still yields CR). */
fun parseXmlDocument(xml: String): XmlDocument = XmlParser(xml.replace("\r\n", "\n").replace('\r', '\n')).parse()

private class XmlParser(private val s: String) {
    private var i = 0

    fun parse(): XmlDocument {
        if (s.startsWith("﻿")) i = 1
        var root: XmlElement? = null
        while (i < s.length) {
            skipWhitespace()
            if (i >= s.length) break
            when {
                s.startsWith("<?", i) -> skipPast("?>")
                s.startsWith("<!--", i) -> skipPast("-->")
                s.startsWith("<!DOCTYPE", i) || s.startsWith("<!doctype", i) -> skipDoctype()
                s[i] == '<' -> {
                    if (root != null) throw XmlParseException("Content is not allowed after the root element")
                    root = readElement(mapOf("xml" to "http://www.w3.org/XML/1998/namespace"))
                }
                else -> throw XmlParseException("Content is not allowed in prolog")
            }
        }
        return XmlDocument(root ?: throw XmlParseException("Premature end of file"))
    }

    private fun readElement(parentNamespaces: Map<String, String>): XmlElement {
        i++ // <
        val name = readName()
        val rawAttributes = LinkedHashMap<String, String>()
        while (true) {
            skipWhitespace()
            if (i >= s.length) throw XmlParseException("Unterminated tag <$name>")
            if (s.startsWith("/>", i) || s[i] == '>') break
            val attrName = readName()
            skipWhitespace()
            expect('=')
            skipWhitespace()
            val quote = s[i]
            if (quote != '"' && quote != '\'') throw XmlParseException("Attribute value must be quoted")
            i++
            val end = s.indexOf(quote, i)
            if (end < 0) throw XmlParseException("Unterminated attribute value")
            rawAttributes[attrName] = decodeEntities(s.substring(i, end))
            i = end + 1
        }
        val namespaces = HashMap(parentNamespaces)
        rawAttributes.forEach { (k, v) ->
            if (k == "xmlns") namespaces[""] = v else if (k.startsWith("xmlns:")) namespaces[k.substringAfter(':')] = v
        }
        val prefix = if (':' in name) name.substringBefore(':') else ""
        val element = XmlElement(
            localName = name.substringAfter(':'),
            namespaceURI = namespaces[prefix]?.takeIf { it.isNotEmpty() },
            attributes = rawAttributes.filterKeys { it != "xmlns" && !it.startsWith("xmlns:") },
        )
        if (s.startsWith("/>", i)) {
            i += 2
            return element
        }
        i++ // >
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                element.children += text.toString()
                text.setLength(0)
            }
        }
        while (true) {
            if (i >= s.length) throw XmlParseException("Unterminated element <$name>")
            when {
                s.startsWith("</", i) -> {
                    flushText()
                    i += 2
                    val closing = readName()
                    if (closing != name) throw XmlParseException("Element <$name> closed by </$closing>")
                    skipWhitespace()
                    expect('>')
                    return element
                }
                s.startsWith("<!--", i) -> skipPast("-->")
                s.startsWith("<![CDATA[", i) -> {
                    val end = s.indexOf("]]>", i + 9)
                    if (end < 0) throw XmlParseException("Unterminated CDATA section")
                    text.append(s, i + 9, end)
                    i = end + 3
                }
                s.startsWith("<?", i) -> skipPast("?>")
                s[i] == '<' -> {
                    flushText()
                    element.children += readElement(namespaces)
                }
                else -> {
                    val next = s.indexOf('<', i).let { if (it < 0) s.length else it }
                    text.append(decodeEntities(s.substring(i, next)))
                    i = next
                }
            }
        }
    }

    private fun readName(): String {
        val start = i
        while (i < s.length && !s[i].isWhitespace() && s[i] != '>' && s[i] != '/' && s[i] != '=') i++
        if (start == i) throw XmlParseException("Expected a name at $i")
        return s.substring(start, i)
    }

    private fun expect(c: Char) {
        if (i >= s.length || s[i] != c) throw XmlParseException("Expected '$c' at $i")
        i++
    }

    private fun skipWhitespace() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun skipPast(marker: String) {
        val end = s.indexOf(marker, i)
        if (end < 0) throw XmlParseException("Expected $marker")
        i = end + marker.length
    }

    private fun skipDoctype() {
        var depth = 0
        while (i < s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> depth--
                '>' -> if (depth <= 0) { i++; return }
            }
            i++
        }
    }

    private fun decodeEntities(raw: String): String {
        if ('&' !in raw) return raw
        val out = StringBuilder(raw.length)
        var p = 0
        while (p < raw.length) {
            val c = raw[p]
            if (c == '&') {
                val end = raw.indexOf(';', p)
                if (end > p) {
                    val entity = raw.substring(p + 1, end)
                    val decoded = when {
                        entity == "amp" -> "&"
                        entity == "lt" -> "<"
                        entity == "gt" -> ">"
                        entity == "quot" -> "\""
                        entity == "apos" -> "'"
                        entity.startsWith("#x") || entity.startsWith("#X") -> entity.substring(2).toIntOrNull(16)?.let(::codePointText)
                        entity.startsWith("#") -> entity.substring(1).toIntOrNull()?.let(::codePointText)
                        else -> null
                    }
                    if (decoded != null) {
                        out.append(decoded)
                        p = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            p++
        }
        return out.toString()
    }

    private fun codePointText(codePoint: Int): String =
        if (codePoint < 0x10000) codePoint.toChar().toString()
        else {
            val v = codePoint - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
}

/** DOM NodeList-style access, so code written against org.w3c.dom reads unchanged. */
val List<XmlElement>.length: Int get() = size
fun List<XmlElement>.item(index: Int): XmlElement? = getOrNull(index)
