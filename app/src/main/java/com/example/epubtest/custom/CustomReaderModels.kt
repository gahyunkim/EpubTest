package com.example.epubtest.custom

/** 커스텀 리더 보기 설정 (Readium 의 EpubPreferences 대신 직접 적용). */
data class CustomReaderSettings(
    val dark: Boolean = true,
    val fontScale: Float = 1.0f,
    /** CSS 에 line-height 가 없는 블록에 쓰는 줄 간격 배수 */
    val lineHeight: Float = 1.9f,
    val scroll: Boolean = false,
)

/** 책 안의 위치: 챕터 / 블록 / 블록 안 글자 오프셋. */
data class ReadingPosition(val chapter: Int = 0, val block: Int = 0, val offset: Int = 0) : Comparable<ReadingPosition> {
    override fun compareTo(other: ReadingPosition): Int =
        compareValuesBy(this, other, { it.chapter }, { it.block }, { it.offset })
}

/** 좋아요 / 메모. Readium Locator 대신 (챕터, 블록) 인덱스로 문단을 가리킨다. */
data class CustomAnnotation(
    val id: String,
    val chapter: Int,
    val block: Int,
    val text: String,
    val liked: Boolean = false,
    val memo: String? = null,
) {
    val isEmpty: Boolean get() = !liked && memo.isNullOrBlank()
}

/** 문단 위에 뜨는 팝업. anchorTop/Bottom 은 리더 영역 기준 px. */
data class CustomPopup(
    val chapter: Int,
    val block: Int,
    val text: String,
    val anchorTop: Float,
    val anchorBottom: Float,
)
