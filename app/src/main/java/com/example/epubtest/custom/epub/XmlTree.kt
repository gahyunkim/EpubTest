package com.example.epubtest.custom.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

/**
 * 플랫폼 내장 XmlPullParser(KXml)로 XML/XHTML 을 아주 작은 DOM 트리로 만든다.
 * (외부 라이브러리 없이 OPF / nav / XHTML 을 모두 이걸로 읽는다.)
 */
internal sealed interface XNode

internal class XText(val text: String) : XNode

internal class XElement(
    /** 소문자 local name (네임스페이스 접두사 제거). 예) `dc:title` → `title` */
    val name: String,
    /** local name 과 `prefix:name` 둘 다 키로 들어간다. 예) `epub:type` → `type`, `epub:type` */
    val attrs: Map<String, String>,
) : XNode {
    val children = mutableListOf<XNode>()

    val id: String? get() = attrs["id"]
    val classes: List<String> by lazy {
        attrs["class"]?.split(WHITESPACE)?.filter { it.isNotEmpty() }.orEmpty()
    }

    fun attr(name: String): String? = attrs[name]

    fun elements(): List<XElement> = children.filterIsInstance<XElement>()

    fun child(name: String): XElement? = elements().firstOrNull { it.name == name }

    fun descendants(): Sequence<XElement> = sequence {
        for (c in elements()) {
            yield(c)
            yieldAll(c.descendants())
        }
    }

    fun descendants(name: String): Sequence<XElement> = descendants().filter { it.name == name }

    fun textContent(): String = buildString {
        fun walk(node: XNode) {
            when (node) {
                is XText -> append(node.text)
                is XElement -> node.children.forEach(::walk)
            }
        }
        walk(this@XElement)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

internal object XmlTree {

    /** XHTML 에서 자주 나오지만 XML 에는 정의되지 않은 HTML 엔티티. */
    private val HTML_ENTITIES = mapOf(
        "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
        "zwnj" to "‌", "zwj" to "‍", "shy" to "­",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "middot" to "·",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
        "laquo" to "«", "raquo" to "»", "bull" to "•", "copy" to "©",
        "reg" to "®", "trade" to "™", "deg" to "°", "times" to "×",
        "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓",
    )

    fun parse(bytes: ByteArray): XElement {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // 조금 깨진 XHTML 도 최대한 읽기 (KXml relaxed 모드)
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setInput(ByteArrayInputStream(bytes), null)
        HTML_ENTITIES.forEach { (name, value) ->
            runCatching { parser.defineEntityReplacementText(name, value) }
        }

        val root = XElement("#document", emptyMap())
        val stack = ArrayDeque<XElement>().apply { addLast(root) }
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val attrs = HashMap<String, String>(parser.attributeCount * 2)
                    for (i in 0 until parser.attributeCount) {
                        val local = parser.getAttributeName(i).lowercase()
                        val value = parser.getAttributeValue(i)
                        attrs.putIfAbsent(local, value)
                        parser.getAttributePrefix(i)?.let { attrs["${it.lowercase()}:$local"] = value }
                    }
                    val element = XElement(parser.name.lowercase(), attrs)
                    stack.last().children += element
                    stack.addLast(element)
                }

                XmlPullParser.END_TAG -> if (stack.size > 1) stack.removeLast()

                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrEmpty()) stack.last().children += XText(text)
                }

                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return root.elements().firstOrNull() ?: error("빈 XML 문서")
    }
}
