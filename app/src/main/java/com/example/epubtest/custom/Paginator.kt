package com.example.epubtest.custom

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.epubtest.custom.epub.EpubBlock
import com.example.epubtest.custom.epub.EpubBook
import kotlin.math.max
import kotlin.math.min

/**
 * 레이아웃 기준값.
 * @param baseSp 본문 기본 글자 크기(sp) = CSS 의 1em
 * @param emPx 1em 의 픽셀 값
 */
data class ReaderMetrics(
    val baseSp: Float,
    val emPx: Float,
    val density: Float,
    val defaultLineHeight: Float,
)

/** 블록 스타일(CSS 계산 결과) → Compose TextStyle */
fun EpubBlock.Text.textStyle(metrics: ReaderMetrics, color: Color, continuation: Boolean): TextStyle {
    val s = style
    return TextStyle(
        color = color.copy(alpha = color.alpha * s.opacity),
        fontSize = (metrics.baseSp * s.fontSizeEm).sp,
        fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (s.italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (s.monospace) FontFamily.Monospace else FontFamily.Default,
        textAlign = s.textAlign ?: TextAlign.Start,
        letterSpacing = (s.letterSpacingEm / s.fontSizeEm).em,
        lineHeight = (s.lineHeight ?: metrics.defaultLineHeight).em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        // 페이지가 넘어가 이어지는 조각에는 첫 줄 들여쓰기를 주지 않는다.
        textIndent = if (!continuation && s.textIndentEm > 0f) TextIndent(firstLine = (s.textIndentEm / s.fontSizeEm).em) else TextIndent.None,
    )
}

/** 블록 사이 간격(px): 위 블록 margin-bottom 과 아래 블록 margin-top 중 큰 값 (CSS margin collapse). */
fun gapPx(prev: EpubBlock?, next: EpubBlock, metrics: ReaderMetrics): Float =
    if (prev == null) 0f else max(prev.style.marginBottom, next.style.marginTop) * metrics.emPx

/** 이미지가 차지할 크기(px). 가로는 영역 폭을 넘지 않고, 세로는 [maxHeight] 를 넘지 않는다. */
fun imageSizePx(block: EpubBlock.Image, maxWidth: Float, maxHeight: Float, metrics: ReaderMetrics): Pair<Float, Float> {
    val bw = block.bitmap.width.toFloat().coerceAtLeast(1f)
    val bh = block.bitmap.height.toFloat().coerceAtLeast(1f)
    var w = min(maxWidth, bw * metrics.density)
    var h = w * bh / bw
    if (h > maxHeight) {
        h = maxHeight
        w = h * bw / bh
    }
    return w to h
}

/**
 * 페이지 한 장에 들어가는 조각.
 * 텍스트 블록이 페이지 경계에 걸리면 줄 단위로 잘라 [start]~[end] 글자 범위만 담는다.
 */
data class PageItem(
    val chapter: Int,
    val block: Int,
    val start: Int,
    val end: Int,
    val gapPx: Float,
    val heightPx: Float,
    val widthPx: Float = 0f,
)

data class Page(val chapter: Int, val items: List<PageItem>) {
    val position: ReadingPosition
        get() = items.firstOrNull()?.let { ReadingPosition(it.chapter, it.block, it.start) } ?: ReadingPosition(chapter)
}

fun List<Page>.indexOf(position: ReadingPosition): Int =
    indexOfLast { it.position <= position }.coerceAtLeast(0)

/**
 * WebView 의 CSS column 대신 직접 페이지를 나눈다.
 * 블록마다 [TextMeasurer] 로 줄 배치를 측정하고, 남은 높이에 들어가는 줄까지만 현재 페이지에 넣는다.
 * 챕터(spine 항목)마다 새 페이지에서 시작한다.
 */
object Paginator {

    fun paginate(
        book: EpubBook,
        measurer: TextMeasurer,
        pageWidthPx: Int,
        pageHeightPx: Int,
        metrics: ReaderMetrics,
    ): List<Page> {
        val pages = mutableListOf<Page>()
        // 측정 오차로 마지막 줄이 잘리지 않게 약간의 여유를 둔다.
        val pageHeight = pageHeightPx - 2f * metrics.density

        for (chapter in book.chapters) {
            val items = mutableListOf<PageItem>()
            var y = 0f
            fun flush() {
                pages += Page(chapter.index, items.toList())
                items.clear()
                y = 0f
            }

            chapter.blocks.forEachIndexed { b, block ->
                val gap = gapPx(chapter.blocks.getOrNull(b - 1), block, metrics)
                val contentWidth = (pageWidthPx - (block.style.marginStart + block.style.marginEnd) * metrics.emPx)
                    .coerceAtLeast(metrics.emPx * 4)

                when (block) {
                    is EpubBlock.Text -> {
                        val layout = measurer.measure(
                            text = block.text,
                            style = block.textStyle(metrics, Color.Unspecified, continuation = false),
                            constraints = Constraints(maxWidth = contentWidth.toInt()),
                        )
                        var line = 0
                        while (line < layout.lineCount) {
                            val usedGap = if (items.isEmpty()) 0f else gap
                            val top = layout.getLineTop(line)
                            val available = pageHeight - y - usedGap
                            var last = line - 1
                            while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= available) last++
                            if (last < line) {
                                if (items.isNotEmpty()) { flush(); continue }
                                last = line // 한 줄이 페이지보다 큰 경우에도 최소 한 줄은 넣는다.
                            }
                            val end = if (last == layout.lineCount - 1) block.text.length else layout.getLineStart(last + 1)
                            val height = layout.getLineBottom(last) - top
                            items += PageItem(chapter.index, b, layout.getLineStart(line), end, usedGap, height)
                            y += usedGap + height
                            line = last + 1
                            if (line < layout.lineCount) flush()
                        }
                    }

                    is EpubBlock.Image -> {
                        val (w, h) = imageSizePx(block, contentWidth, pageHeight, metrics)
                        if (items.isNotEmpty() && y + gap + h > pageHeight) flush()
                        val usedGap = if (items.isEmpty()) 0f else gap
                        items += PageItem(chapter.index, b, 0, 0, usedGap, h, w)
                        y += usedGap + h
                    }

                    is EpubBlock.Rule -> {
                        val h = metrics.density
                        if (items.isNotEmpty() && y + gap + h > pageHeight) flush()
                        val usedGap = if (items.isEmpty()) 0f else gap
                        items += PageItem(chapter.index, b, 0, 0, usedGap, h)
                        y += usedGap + h
                    }
                }
            }
            if (items.isNotEmpty() || pages.none { it.chapter == chapter.index }) flush()
        }
        return pages
    }
}
