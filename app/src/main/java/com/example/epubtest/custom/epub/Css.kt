package com.example.epubtest.custom.epub

import androidx.compose.ui.text.style.TextAlign
import kotlin.math.max

/**
 * EPUB 렌더링에 필요한 만큼만 구현한 최소 CSS 엔진.
 *
 * - 선택자: `tag`, `.class`, `#id`, `tag.class#id`, 후손/자식 결합자(`a b`, `a > b` → 후손으로 취급), 콤마 그룹
 * - 속성: font-size, font-weight, font-style, font-family, text-align, text-indent, letter-spacing,
 *   line-height, opacity, text-decoration, vertical-align, display, margin(-*), padding(-*)
 * - 색상은 리더 테마(다크/라이트)가 덮어쓰므로 무시한다.
 * - 길이 단위는 모두 "본문 기본 글자 크기 = 1em" 기준 값으로 환산해 둔다.
 */
internal data class SimpleSelector(val tag: String?, val id: String?, val classes: List<String>) {
    fun matches(el: XElement): Boolean =
        (tag == null || tag == el.name) &&
            (id == null || id == el.id) &&
            classes.all { it in el.classes }
}

internal class CssSelector(private val chain: List<SimpleSelector>) {
    val specificity: Int = chain.sumOf { s ->
        (if (s.id != null) 100 else 0) + s.classes.size * 10 + (if (s.tag != null) 1 else 0)
    }

    /** @param ancestors 루트 → 부모 순서 */
    fun matches(el: XElement, ancestors: List<XElement>): Boolean {
        if (!chain.last().matches(el)) return false
        var ai = ancestors.lastIndex
        for (i in chain.lastIndex - 1 downTo 0) {
            while (ai >= 0 && !chain[i].matches(ancestors[ai])) ai--
            if (ai < 0) return false
            ai--
        }
        return true
    }
}

internal class CssRule(val selector: CssSelector, val declarations: Map<String, String>, val order: Int)

internal object CssParser {
    private val COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val SIMPLE = Regex("^([a-zA-Z][a-zA-Z0-9-]*|\\*)?((?:[.#][a-zA-Z0-9_-]+)*)$")
    private val PART = Regex("[.#][a-zA-Z0-9_-]+")

    fun parse(css: String, orderOffset: Int = 0): List<CssRule> {
        val src = css.replace(COMMENT, "")
        val rules = mutableListOf<CssRule>()
        var i = 0
        var order = orderOffset
        while (i < src.length) {
            while (i < src.length && src[i].isWhitespace()) i++
            if (i >= src.length) break
            if (src[i] == '@') {
                // @charset/@import ...; 는 건너뛰고, @media/@font-face {...} 블록도 통째로 건너뛴다.
                val semi = src.indexOf(';', i).let { if (it < 0) Int.MAX_VALUE else it }
                val open = src.indexOf('{', i).let { if (it < 0) Int.MAX_VALUE else it }
                if (semi < open) {
                    i = semi + 1
                } else if (open == Int.MAX_VALUE) {
                    break
                } else {
                    var depth = 0
                    var j = open
                    while (j < src.length) {
                        if (src[j] == '{') depth++
                        if (src[j] == '}') { depth--; if (depth == 0) break }
                        j++
                    }
                    i = j + 1
                }
                continue
            }
            val open = src.indexOf('{', i)
            if (open < 0) break
            val close = src.indexOf('}', open)
            if (close < 0) break
            val selectorText = src.substring(i, open)
            val declarations = parseDeclarations(src.substring(open + 1, close))
            i = close + 1
            if (declarations.isEmpty()) continue
            selectorText.split(',').forEach { raw ->
                parseSelector(raw.trim())?.let { rules += CssRule(it, declarations, order++) }
            }
        }
        return rules
    }

    fun parseDeclarations(body: String): Map<String, String> =
        body.split(';').mapNotNull { decl ->
            val colon = decl.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val name = decl.substring(0, colon).trim().lowercase()
            val value = decl.substring(colon + 1).replace("!important", "").trim()
            if (name.isEmpty() || value.isEmpty()) null else name to value
        }.toMap()

    private fun parseSelector(text: String): CssSelector? {
        if (text.isEmpty()) return null
        // 가상 클래스/속성 선택자/형제 결합자는 지원하지 않음 → 규칙 무시
        if (text.any { it == ':' || it == '[' || it == '+' || it == '~' }) return null
        val tokens = text.replace(">", " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val chain = tokens.map { token ->
            val m = SIMPLE.matchEntire(token) ?: return null
            val tag = m.groupValues[1].takeIf { it.isNotEmpty() && it != "*" }?.lowercase()
            val parts = PART.findAll(m.groupValues[2]).map { it.value }.toList()
            SimpleSelector(
                tag = tag,
                id = parts.firstOrNull { it.startsWith("#") }?.drop(1),
                classes = parts.filter { it.startsWith(".") }.map { it.drop(1) },
            )
        }
        return if (chain.isEmpty()) null else CssSelector(chain)
    }
}

/** 요소 하나의 계산된 스타일. 길이 값은 모두 본문 기본 글자 크기 기준 em. */
internal data class ComputedStyle(
    // ── 상속되는 값
    val fontSizeEm: Float = 1f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val textAlign: TextAlign? = null,
    val letterSpacingEm: Float = 0f,
    /** 배수(예: 1.5). null 이면 리더 설정값 사용 */
    val lineHeight: Float? = null,
    val textIndentEm: Float = 0f,
    val opacity: Float = 1f,
    val underline: Boolean = false,
    val pre: Boolean = false,
    // ── 상속되지 않는 값
    val baselineShift: Float = 0f,
    val display: String? = null,
    val marginTop: Float = 0f,
    val marginBottom: Float = 0f,
    val marginStart: Float = 0f,
    val marginEnd: Float = 0f,
) {
    val hidden: Boolean get() = display == "none"

    fun inherited(): ComputedStyle = copy(
        baselineShift = 0f, display = null,
        marginTop = 0f, marginBottom = 0f, marginStart = 0f, marginEnd = 0f,
    )
}

internal class StyleResolver(private val rules: List<CssRule>) {

    private val sortedRules = rules.sortedWith(compareBy<CssRule>({ it.selector.specificity }, { it.order }))

    fun compute(el: XElement, parent: ComputedStyle, ancestors: List<XElement>): ComputedStyle {
        val declarations = LinkedHashMap<String, String>()
        UA_DEFAULTS[el.name]?.let(declarations::putAll)
        for (rule in sortedRules) {
            if (rule.selector.matches(el, ancestors)) declarations.putAll(rule.declarations)
        }
        el.attr("style")?.let { declarations.putAll(CssParser.parseDeclarations(it)) }
        el.attr("align")?.let { declarations.putIfAbsent("text-align", it) }

        var s = parent.inherited()
        // font-size 를 먼저 계산해야 em 단위 margin/indent 를 환산할 수 있다.
        declarations["font-size"]?.let { v -> fontSize(v, parent.fontSizeEm)?.let { s = s.copy(fontSizeEm = it) } }
        val em = s.fontSizeEm

        for ((name, raw) in declarations) {
            val v = raw.trim().lowercase()
            s = when (name) {
                "font-weight" -> s.copy(
                    bold = when (v) {
                        "bold", "bolder" -> true
                        "normal", "lighter" -> false
                        else -> (v.toIntOrNull() ?: 400) >= 600
                    }
                )
                "font-style" -> s.copy(italic = v == "italic" || v == "oblique")
                "font-family" -> s.copy(monospace = "mono" in v || "courier" in v)
                "text-align" -> s.copy(
                    textAlign = when (v) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        "left" -> TextAlign.Left
                        "justify" -> TextAlign.Justify
                        "end" -> TextAlign.End
                        else -> TextAlign.Start
                    }
                )
                "text-indent" -> length(v, em)?.let { s.copy(textIndentEm = it) } ?: s
                "letter-spacing" -> if (v == "normal") s.copy(letterSpacingEm = 0f) else length(v, em)?.let { s.copy(letterSpacingEm = it) } ?: s
                "line-height" -> s.copy(lineHeight = lineHeight(v, em))
                "opacity" -> v.toFloatOrNull()?.let { s.copy(opacity = parent.opacity * it.coerceIn(0f, 1f)) } ?: s
                "text-decoration", "text-decoration-line" -> s.copy(underline = "underline" in v)
                "vertical-align" -> s.copy(
                    baselineShift = when (v) {
                        "super" -> 0.5f
                        "sub" -> -0.25f
                        else -> 0f
                    }
                )
                "white-space" -> s.copy(pre = v.startsWith("pre"))
                "display" -> s.copy(display = v)
                "margin", "padding" -> box(v, em)?.let { (t, r, b, l) ->
                    if (name == "margin") s.copy(marginTop = t, marginEnd = r, marginBottom = b, marginStart = l)
                    else s.copy(
                        marginTop = s.marginTop + t, marginEnd = s.marginEnd + r,
                        marginBottom = s.marginBottom + b, marginStart = s.marginStart + l,
                    )
                } ?: s
                "margin-top" -> length(v, em)?.let { s.copy(marginTop = it) } ?: s
                "margin-bottom" -> length(v, em)?.let { s.copy(marginBottom = it) } ?: s
                "margin-left" -> length(v, em)?.let { s.copy(marginStart = it) } ?: s
                "margin-right" -> length(v, em)?.let { s.copy(marginEnd = it) } ?: s
                "padding-top" -> length(v, em)?.let { s.copy(marginTop = s.marginTop + it) } ?: s
                "padding-bottom" -> length(v, em)?.let { s.copy(marginBottom = s.marginBottom + it) } ?: s
                "padding-left" -> length(v, em)?.let { s.copy(marginStart = s.marginStart + it) } ?: s
                "padding-right" -> length(v, em)?.let { s.copy(marginEnd = s.marginEnd + it) } ?: s
                else -> s
            }
        }
        // 음수 margin 은 레이아웃을 깨뜨리므로 0 으로
        return s.copy(
            marginTop = max(0f, s.marginTop), marginBottom = max(0f, s.marginBottom),
            marginStart = max(0f, s.marginStart), marginEnd = max(0f, s.marginEnd),
        )
    }

    companion object {
        private val NUMBER_UNIT = Regex("^(-?[0-9]*\\.?[0-9]+)([a-z%]*)$")

        /** CSS 길이 → 기본 글자 크기 기준 em. % / auto / vw 등은 null(무시). */
        fun length(value: String, em: Float): Float? {
            val v = value.trim().lowercase()
            if (v == "0") return 0f
            val m = NUMBER_UNIT.matchEntire(v) ?: return null
            val n = m.groupValues[1].toFloatOrNull() ?: return null
            return when (m.groupValues[2]) {
                "em" -> n * em
                "rem" -> n
                "px", "" -> n / 16f
                "pt" -> n * (4f / 3f) / 16f
                "ex", "ch" -> n * 0.5f * em
                else -> null
            }
        }

        private fun fontSize(value: String, parentEm: Float): Float? {
            val v = value.trim().lowercase()
            KEYWORD_SIZES[v]?.let { return it }
            when (v) {
                "smaller" -> return parentEm * 0.83f
                "larger" -> return parentEm * 1.2f
            }
            if (v.endsWith("%")) return v.dropLast(1).toFloatOrNull()?.let { parentEm * it / 100f }
            return length(v, parentEm)
        }

        private fun lineHeight(value: String, em: Float): Float? {
            if (value == "normal") return null
            value.toFloatOrNull()?.let { return it }
            if (value.endsWith("%")) return value.dropLast(1).toFloatOrNull()?.div(100f)
            return length(value, em)?.div(em)
        }

        private fun box(value: String, em: Float): List<Float>? {
            val parts = value.split(Regex("\\s+")).map { if (it == "auto") 0f else length(it, em) ?: return null }
            return when (parts.size) {
                1 -> listOf(parts[0], parts[0], parts[0], parts[0])
                2 -> listOf(parts[0], parts[1], parts[0], parts[1])
                3 -> listOf(parts[0], parts[1], parts[2], parts[1])
                4 -> parts
                else -> null
            }
        }

        private val KEYWORD_SIZES = mapOf(
            "xx-small" to 0.6f, "x-small" to 0.75f, "small" to 0.89f, "medium" to 1f,
            "large" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f,
        )

        /** 브라우저 기본(UA) 스타일시트의 핵심만. */
        private val UA_DEFAULTS: Map<String, Map<String, String>> = mapOf(
            "h1" to mapOf("font-size" to "2em", "font-weight" to "bold", "margin" to "0.67em 0"),
            "h2" to mapOf("font-size" to "1.5em", "font-weight" to "bold", "margin" to "0.83em 0"),
            "h3" to mapOf("font-size" to "1.17em", "font-weight" to "bold", "margin" to "1em 0"),
            "h4" to mapOf("font-weight" to "bold", "margin" to "1.33em 0"),
            "h5" to mapOf("font-size" to "0.83em", "font-weight" to "bold", "margin" to "1.67em 0"),
            "h6" to mapOf("font-size" to "0.67em", "font-weight" to "bold", "margin" to "2.33em 0"),
            "p" to mapOf("margin" to "1em 0"),
            "blockquote" to mapOf("margin" to "1em 2.5em"),
            "figure" to mapOf("margin" to "1em 2.5em"),
            "ul" to mapOf("margin" to "1em 0", "padding-left" to "2.5em"),
            "ol" to mapOf("margin" to "1em 0", "padding-left" to "2.5em"),
            "dl" to mapOf("margin" to "1em 0"),
            "dd" to mapOf("margin-left" to "2.5em"),
            "pre" to mapOf("font-family" to "monospace", "white-space" to "pre", "margin" to "1em 0"),
            "hr" to mapOf("margin" to "0.5em 0"),
            "center" to mapOf("text-align" to "center"),
            "b" to mapOf("font-weight" to "bold"),
            "strong" to mapOf("font-weight" to "bold"),
            "th" to mapOf("font-weight" to "bold"),
            "i" to mapOf("font-style" to "italic"),
            "em" to mapOf("font-style" to "italic"),
            "cite" to mapOf("font-style" to "italic"),
            "dfn" to mapOf("font-style" to "italic"),
            "u" to mapOf("text-decoration" to "underline"),
            "small" to mapOf("font-size" to "smaller"),
            "big" to mapOf("font-size" to "larger"),
            "sup" to mapOf("font-size" to "smaller", "vertical-align" to "super"),
            "sub" to mapOf("font-size" to "smaller", "vertical-align" to "sub"),
            "code" to mapOf("font-family" to "monospace"),
            "kbd" to mapOf("font-family" to "monospace"),
            "tt" to mapOf("font-family" to "monospace"),
        )
    }
}
