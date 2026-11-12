package com.example.epubtest.custom.epub

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.max

// ───────────────────────────── 결과 모델 ─────────────────────────────

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
    val toc: List<TocEntry>,
)

data class EpubChapter(
    val index: Int,
    val href: String,
    val title: String,
    val blocks: List<EpubBlock>,
)

data class TocEntry(val title: String, val chapterIndex: Int, val depth: Int)

/** 블록(문단) 하나의 레이아웃 정보. 길이는 모두 본문 기본 글자 크기 기준 em. */
data class BlockStyle(
    val fontSizeEm: Float,
    val bold: Boolean,
    val italic: Boolean,
    val monospace: Boolean,
    val textAlign: TextAlign?,
    val letterSpacingEm: Float,
    val lineHeight: Float?,
    val textIndentEm: Float,
    val opacity: Float,
    val marginTop: Float,
    val marginBottom: Float,
    val marginStart: Float,
    val marginEnd: Float,
)

sealed interface EpubBlock {
    val style: BlockStyle

    /** 문단/제목 등 텍스트 블록. [text] 안에 굵게/기울임/크기 등 인라인 스타일이 span 으로 들어 있다. */
    data class Text(
        val id: String?,
        val tag: String,
        val text: AnnotatedString,
        override val style: BlockStyle,
    ) : EpubBlock

    data class Image(
        val bitmap: ImageBitmap,
        val alt: String?,
        override val style: BlockStyle,
    ) : EpubBlock

    data class Rule(override val style: BlockStyle) : EpubBlock
}

/**
 * 외부 라이브러리 없이 EPUB(2/3) 을 읽는다.
 *
 * 1. ZIP 열기 ([ZipFile])
 * 2. `META-INF/container.xml` → OPF 경로
 * 3. OPF → 메타데이터 / manifest / spine(읽는 순서)
 * 4. EPUB3 nav.xhtml 또는 EPUB2 toc.ncx → 목차
 * 5. spine 의 XHTML 마다 CSS(link/style) 를 적용해 블록 목록으로 변환
 */
object EpubParser {

    private val BLOCK_TAGS = setOf(
        "html", "body", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "dl", "dt", "dd",
        "blockquote", "section", "article", "aside", "header", "footer", "nav", "main", "figure",
        "figcaption", "address", "pre", "hr", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
        "caption", "center", "details", "summary",
    )
    private val SKIP_TAGS = setOf("head", "script", "style", "title", "rt", "rp", "noscript", "template")
    private const val MAX_IMAGE_PX = 1600

    fun parse(file: File): EpubBook = ZipFile(file).use { zip ->
        fun bytes(path: String): ByteArray? = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
        fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)

        // 2. container.xml
        val container = XmlTree.parse(bytes("META-INF/container.xml") ?: error("META-INF/container.xml 이 없습니다"))
        val opfPath = container.descendants("rootfile").firstOrNull()?.attr("full-path")
            ?: error("container.xml 에 rootfile 이 없습니다")

        // 3. OPF
        val opf = XmlTree.parse(bytes(opfPath) ?: error("OPF 파일이 없습니다: $opfPath"))
        val metadata = opf.child("metadata")
        val title = metadata?.child("title")?.textContent()?.trim().orEmpty().ifEmpty { file.nameWithoutExtension }
        val author = metadata?.child("creator")?.textContent()?.trim().orEmpty()

        data class Item(val id: String, val path: String, val mediaType: String, val properties: String)

        val manifest = opf.child("manifest")?.elements().orEmpty()
            .filter { it.name == "item" }
            .mapNotNull { item ->
                val id = item.attr("id") ?: return@mapNotNull null
                val href = item.attr("href") ?: return@mapNotNull null
                Item(id, resolve(opfPath, href), item.attr("media-type").orEmpty(), item.attr("properties").orEmpty())
            }
            .associateBy { it.id }

        val spine = opf.child("spine")
        val spinePaths = spine?.elements().orEmpty()
            .filter { it.name == "itemref" }
            .mapNotNull { manifest[it.attr("idref")]?.path }

        // 4. 목차 (href → 제목)
        val tocLinks: List<Triple<String, String, Int>> = run {
            val nav = manifest.values.firstOrNull { "nav" in it.properties.split(' ') }
            if (nav != null) {
                bytes(nav.path)?.let { runCatching { parseNav(XmlTree.parse(it), nav.path) }.getOrNull() }
            } else {
                val ncx = manifest[spine?.attr("toc")] ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
                ncx?.let { item -> bytes(item.path)?.let { runCatching { parseNcx(XmlTree.parse(it), item.path) }.getOrNull() } }
            }
        }.orEmpty()

        // 5. 본문
        val cssCache = HashMap<String, List<CssRule>>()
        val chapters = spinePaths.mapIndexed { index, path ->
            val doc = runCatching { XmlTree.parse(bytes(path) ?: error("missing")) }.getOrNull()
            if (doc == null) {
                EpubChapter(index, path, "${index + 1}", emptyList())
            } else {
                // <link rel=stylesheet> + <style> 순서대로 CSS 규칙을 모은다.
                val rules = mutableListOf<CssRule>()
                doc.child("head")?.elements().orEmpty().forEach { el ->
                    when {
                        el.name == "link" && el.attr("rel")?.contains("stylesheet") == true -> {
                            val cssPath = resolve(path, el.attr("href") ?: return@forEach)
                            val parsed = cssCache.getOrPut(cssPath) { text(cssPath)?.let { CssParser.parse(it) }.orEmpty() }
                            parsed.forEach { r -> rules += CssRule(r.selector, r.declarations, rules.size) }
                        }
                        el.name == "style" -> CssParser.parse(el.textContent(), rules.size).forEach { rules += it }
                    }
                }
                val blocks = ChapterBuilder(path, StyleResolver(rules), ::bytes).build(doc)
                val chapterTitle = tocLinks.firstOrNull { it.first == path }?.second
                    ?: doc.child("head")?.child("title")?.textContent()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: blocks.filterIsInstance<EpubBlock.Text>().firstOrNull { it.tag.startsWith("h") }?.text?.text
                    ?: "${index + 1}"
                EpubChapter(index, path, chapterTitle, blocks)
            }
        }

        val toc = tocLinks.mapNotNull { (href, label, depth) ->
            val chapterIndex = spinePaths.indexOf(href)
            if (chapterIndex < 0) null else TocEntry(label, chapterIndex, depth)
        }.ifEmpty { chapters.map { TocEntry(it.title, it.index, 0) } }

        EpubBook(title, author, chapters, toc)
    }

    /** EPUB3 nav.xhtml 의 `<nav epub:type="toc">` → (경로, 제목, 깊이) */
    private fun parseNav(doc: XElement, navPath: String): List<Triple<String, String, Int>> {
        val nav = doc.descendants("nav").firstOrNull { it.attr("epub:type")?.contains("toc") == true }
            ?: doc.descendants("nav").firstOrNull()
            ?: return emptyList()
        val out = mutableListOf<Triple<String, String, Int>>()
        fun walk(list: XElement, depth: Int) {
            list.elements().filter { it.name == "li" }.forEach { li ->
                val a = li.elements().firstOrNull { it.name == "a" || it.name == "span" }
                val href = a?.attr("href")
                if (a != null && href != null) {
                    out += Triple(resolve(navPath, href), a.textContent().trim().replace(Regex("\\s+"), " "), depth)
                }
                li.elements().firstOrNull { it.name == "ol" || it.name == "ul" }?.let { walk(it, depth + 1) }
            }
        }
        nav.descendants().firstOrNull { it.name == "ol" || it.name == "ul" }?.let { walk(it, 0) }
        return out
    }

    /** EPUB2 toc.ncx 의 navMap → (경로, 제목, 깊이) */
    private fun parseNcx(doc: XElement, ncxPath: String): List<Triple<String, String, Int>> {
        val out = mutableListOf<Triple<String, String, Int>>()
        fun walk(parent: XElement, depth: Int) {
            parent.elements().filter { it.name == "navpoint" }.forEach { point ->
                val label = point.child("navlabel")?.child("text")?.textContent()?.trim().orEmpty()
                point.child("content")?.attr("src")?.let { out += Triple(resolve(ncxPath, it), label, depth) }
                walk(point, depth + 1)
            }
        }
        doc.child("navmap")?.let { walk(it, 0) }
        return out
    }

    /** [base] 파일 기준 상대 경로 [href] → ZIP 엔트리 경로 (`#fragment` 제거, `../` 처리, URL 디코딩) */
    internal fun resolve(base: String, href: String): String {
        val clean = Uri.decode(href.substringBefore('#'))
        if (clean.startsWith("/")) return clean.trimStart('/')
        val parts = base.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
        clean.split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }

    // ───────────────────────────── XHTML → 블록 ─────────────────────────────

    private class ChapterBuilder(
        private val path: String,
        private val resolver: StyleResolver,
        private val readBytes: (String) -> ByteArray?,
    ) {
        private val out = mutableListOf<EpubBlock>()

        fun build(doc: XElement): List<EpubBlock> {
            val body = doc.child("body") ?: doc
            val ancestors = if (body === doc) emptyList() else listOf(doc)
            val htmlStyle = resolver.compute(doc, ComputedStyle(), emptyList())
            val bodyStyle = if (body === doc) htmlStyle else resolver.compute(body, htmlStyle, ancestors)
            walkBlock(body, bodyStyle, ancestors)
            return out
        }

        private fun isBlock(el: XElement, style: ComputedStyle): Boolean = when (style.display) {
            "inline", "inline-block" -> false
            "block", "list-item", "table", "flex", "grid" -> true
            else -> el.name in BLOCK_TAGS
        }

        /** 이미지를 품고 있는 요소는 블록처럼 취급해 이미지를 별도 블록으로 뺀다. */
        private fun isImage(el: XElement) = el.name == "img" || el.name == "svg" || el.name == "image"

        private fun walkBlock(el: XElement, style: ComputedStyle, ancestors: List<XElement>) {
            if (style.hidden) return
            when {
                isImage(el) -> { emitImage(el, style); return }
                el.name == "hr" -> { out += EpubBlock.Rule(style.toBlockStyle()); return }
            }

            val childAncestors = ancestors + el
            val children = el.children.mapNotNull { node ->
                when (node) {
                    is XText -> node to null
                    is XElement -> if (node.name in SKIP_TAGS) null else node to resolver.compute(node, style, childAncestors)
                }
            }
            val hasBlockChild = children.any { (node, s) ->
                node is XElement && s != null && !s.hidden && (isBlock(node, s) || containsImage(node))
            }

            if (!hasBlockChild) {
                emitText(el, style, el.children, ancestors)
                return
            }

            // 블록 컨테이너: 블록 자식은 재귀, 사이사이의 인라인 내용은 익명 블록으로 묶는다.
            val start = out.size
            val pending = mutableListOf<XNode>()
            fun flush() {
                if (pending.isNotEmpty()) {
                    emitText(null, style.copy(marginTop = 0f, marginBottom = 0f), pending.toList(), childAncestors, inheritedOnly = true)
                    pending.clear()
                }
            }
            for ((node, s) in children) {
                if (node is XElement && s != null && (isBlock(node, s) || containsImage(node))) {
                    flush()
                    walkBlock(node, s, childAncestors)
                } else {
                    pending += node
                }
            }
            flush()

            // 컨테이너의 margin 을 첫/마지막 자식 블록에 합친다 (margin collapse 근사) + 좌우 여백 누적.
            if (out.size > start) {
                for (i in start until out.size) {
                    out[i] = out[i].withStyle {
                        it.copy(marginStart = it.marginStart + style.marginStart, marginEnd = it.marginEnd + style.marginEnd)
                    }
                }
                out[start] = out[start].withStyle { it.copy(marginTop = max(it.marginTop, style.marginTop)) }
                out[out.lastIndex] = out[out.lastIndex].withStyle { it.copy(marginBottom = max(it.marginBottom, style.marginBottom)) }
            }
        }

        private fun containsImage(el: XElement): Boolean = isImage(el) || el.descendants().any(::isImage)

        private fun emitImage(el: XElement, style: ComputedStyle) {
            val src = when (el.name) {
                "img" -> el.attr("src")
                "image" -> el.attr("href")
                else -> el.descendants("image").firstOrNull()?.attr("href")
                    ?: el.descendants("img").firstOrNull()?.attr("src")
            } ?: return
            val bytes = readBytes(resolve(path, src)) ?: return
            val bitmap = decode(bytes) ?: return
            out += EpubBlock.Image(bitmap.asImageBitmap(), el.attr("alt"), style.toBlockStyle())
        }

        private fun decode(bytes: ByteArray) = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        private fun emitText(
            el: XElement?,
            style: ComputedStyle,
            nodes: List<XNode>,
            ancestors: List<XElement>,
            inheritedOnly: Boolean = false,
        ) {
            val builder = InlineBuilder(style.pre)
            val inlineAncestors = if (el != null) ancestors + el else ancestors
            nodes.forEach { appendInline(builder, it, style, style, inlineAncestors) }
            val text = builder.build()
            if (text.isBlank()) return
            val blockStyle = if (inheritedOnly) style.inherited().toBlockStyle() else style.toBlockStyle()
            out += EpubBlock.Text(el?.id, el?.name ?: "span", text, blockStyle)
        }

        private fun appendInline(
            b: InlineBuilder,
            node: XNode,
            parent: ComputedStyle,
            block: ComputedStyle,
            ancestors: List<XElement>,
        ) {
            when (node) {
                is XText -> b.appendText(node.text)
                is XElement -> {
                    if (node.name in SKIP_TAGS) return
                    if (node.name == "br") { b.lineBreak(); return }
                    val s = resolver.compute(node, parent, ancestors)
                    if (s.hidden) return
                    b.span(spanFor(s, parent, block)) {
                        node.children.forEach { appendInline(b, it, s, block, ancestors + node) }
                    }
                }
            }
        }

        /** 부모 인라인 스타일과 달라진 부분만 SpanStyle 로. 글자 크기는 블록 대비 비율(em). */
        private fun spanFor(s: ComputedStyle, parent: ComputedStyle, block: ComputedStyle): SpanStyle? {
            val sizeChanged = abs(s.fontSizeEm - parent.fontSizeEm) > 0.001f
            val spacingChanged = abs(s.letterSpacingEm - parent.letterSpacingEm) > 0.001f
            val changed = s.bold != parent.bold || s.italic != parent.italic || s.monospace != parent.monospace ||
                s.underline != parent.underline || sizeChanged || spacingChanged || s.baselineShift != 0f
            if (!changed) return null
            return SpanStyle(
                fontWeight = if (s.bold != parent.bold) (if (s.bold) FontWeight.Bold else FontWeight.Normal) else null,
                fontStyle = if (s.italic != parent.italic) (if (s.italic) FontStyle.Italic else FontStyle.Normal) else null,
                fontFamily = if (s.monospace != parent.monospace) (if (s.monospace) FontFamily.Monospace else FontFamily.Default) else null,
                textDecoration = if (s.underline != parent.underline) (if (s.underline) TextDecoration.Underline else TextDecoration.None) else null,
                fontSize = if (sizeChanged) (s.fontSizeEm / block.fontSizeEm).em else TextUnit.Unspecified,
                letterSpacing = if (spacingChanged) (s.letterSpacingEm / s.fontSizeEm).em else TextUnit.Unspecified,
                baselineShift = if (s.baselineShift != 0f) BaselineShift(s.baselineShift) else null,
            )
        }
    }

    /** HTML 공백 규칙(연속 공백 → 하나, 블록 앞뒤 공백 제거)을 지키며 텍스트 + span 을 모은다. */
    private class InlineBuilder(private val pre: Boolean) {
        private val sb = StringBuilder()
        private val spans = mutableListOf<AnnotatedString.Range<SpanStyle>>()

        fun appendText(raw: String) {
            if (pre) { sb.append(raw); return }
            var t = raw.replace(COLLAPSE, " ")
            if (t.startsWith(" ") && (sb.isEmpty() || sb.last() == ' ' || sb.last() == '\n')) t = t.drop(1)
            sb.append(t)
        }

        fun lineBreak() {
            if (sb.endsWith(" ")) sb.setLength(sb.length - 1)
            sb.append('\n')
        }

        fun span(style: SpanStyle?, content: () -> Unit) {
            val start = sb.length
            content()
            if (style != null && sb.length > start) spans += AnnotatedString.Range(style, start, sb.length)
        }

        fun build(): AnnotatedString {
            var start = 0
            var end = sb.length
            while (start < end && (sb[start] == ' ' || sb[start] == '\n')) start++
            while (end > start && (sb[end - 1] == ' ' || sb[end - 1] == '\n')) end--
            val text = sb.substring(start, end)
            val ranges = spans.mapNotNull { r ->
                val s = (r.start - start).coerceIn(0, text.length)
                val e = (r.end - start).coerceIn(0, text.length)
                if (e > s) AnnotatedString.Range(r.item, s, e) else null
            }
            return AnnotatedString(text, ranges)
        }

        private companion object {
            //  (nbsp) 는 접지 않는다
            val COLLAPSE = Regex("[ \\t\\n\\r\\u000C]+")
        }
    }
}

private fun ComputedStyle.toBlockStyle() = BlockStyle(
    fontSizeEm = fontSizeEm,
    bold = bold,
    italic = italic,
    monospace = monospace,
    textAlign = textAlign,
    letterSpacingEm = letterSpacingEm,
    lineHeight = lineHeight,
    textIndentEm = textIndentEm,
    opacity = opacity,
    marginTop = marginTop,
    marginBottom = marginBottom,
    marginStart = marginStart,
    marginEnd = marginEnd,
)

private fun EpubBlock.withStyle(transform: (BlockStyle) -> BlockStyle): EpubBlock = when (this) {
    is EpubBlock.Text -> copy(style = transform(style))
    is EpubBlock.Image -> copy(style = transform(style))
    is EpubBlock.Rule -> copy(style = transform(style))
}
