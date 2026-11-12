package com.example.epubtest.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.navigator.preferences.Color as ReadiumColor

/** Figma 「세계문학」 02/03 화면(다크, L·라이트) 기준 색상. */
data class ReaderPalette(
    val background: Color,
    val text: Color,
    val subText: Color,
    val divider: Color,
    val popupBackground: Color,
    val popupBorder: Color,
    val chipBackground: Color,
    val selectionTint: Color,
    val highlightTint: Color,
) {
    companion object {
        val Dark = ReaderPalette(
            background = Color(0xFF1B1E23),
            text = Color(0xFFD9DADC),
            subText = Color(0xFF8B9099),
            divider = Color(0xFF2B2F35),
            popupBackground = Color(0xFF262A30),
            popupBorder = Color(0xFF363B42),
            chipBackground = Color(0xFF30353C),
            selectionTint = Color(0xFFA9B9D3),
            highlightTint = Color(0xFFE3B34C),
        )
        val Light = ReaderPalette(
            background = Color(0xFFFBFBFA),
            text = Color(0xFF2A2B2D),
            subText = Color(0xFF8E9196),
            divider = Color(0xFFE8E8E6),
            popupBackground = Color(0xFFFFFFFF),
            popupBorder = Color(0xFFE6E7E9),
            chipBackground = Color(0xFFF3F4F6),
            selectionTint = Color(0xFF5F82BE),
            highlightTint = Color(0xFFF0B93A),
        )

        fun of(dark: Boolean) = if (dark) Dark else Light
    }
}

/** 사용자가 Aa 시트에서 바꾸는 보기 설정 → Readium [EpubPreferences]로 변환. */
data class ReaderSettings(
    val dark: Boolean = true,
    val fontScale: Double = 1.0,
    val lineHeight: Double = 1.9,
    val scroll: Boolean = false,
) {
    @OptIn(ExperimentalReadiumApi::class)
    fun toEpubPreferences(): EpubPreferences {
        val palette = ReaderPalette.of(dark)
        return EpubPreferences(
            theme = if (dark) Theme.DARK else Theme.LIGHT,
            backgroundColor = ReadiumColor(palette.background.toArgb()),
            textColor = ReadiumColor(palette.text.toArgb()),
            fontSize = fontScale,
            lineHeight = lineHeight,
            pageMargins = 1.4,
            paragraphIndent = 1.0,
            paragraphSpacing = 0.8,
            publisherStyles = false, // false여야 lineHeight / paragraphIndent 등이 적용됨
            textAlign = TextAlign.START,
            hyphens = false,
            scroll = scroll,
        )
    }
}

/** 좋아요(하이라이트) / 메모가 달린 문장. 지금은 메모리에만 저장(더미). */
data class Annotation(
    val id: String,
    val locator: Locator,
    val text: String,
    val liked: Boolean = false,
    val memo: String? = null,
) {
    val isEmpty: Boolean get() = !liked && memo.isNullOrBlank()
}

/**
 * 문장(문단) 위에 뜨는 팝업 상태.
 *
 * @param anchorTop/anchorBottom 탭한 문단의 화면 좌표(px, 리더 영역 기준)
 * @param fromSelection 롱프레스 텍스트 선택에서 열린 경우 true (네이티브 선택 영역 사용)
 */
data class PopupState(
    val locator: Locator,
    val text: String,
    val anchorTop: Float,
    val anchorBottom: Float,
    val chapterIndex: Int,
    val fromSelection: Boolean = false,
)

enum class SelectionEvent { Started, Ended }

fun Locator.sameTarget(other: Locator): Boolean =
    href.isEquivalent(other.href) &&
        text.highlight == other.text.highlight &&
        locations.otherLocations["cssSelector"] == other.locations.otherLocations["cssSelector"]
