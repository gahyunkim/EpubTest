package com.example.epubtest.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val MEMO_MAX_LENGTH = 500

/**
 * 메모 작성/수정 시트.
 *
 * 상단: 제목 + (기존 메모면) 삭제
 * 인용: 강조색 세로줄 + 대상 문장 (최대 3줄)
 * 입력: 테마 색을 따르는 넓은 입력칸 (열리면 바로 키보드)
 * 하단: 글자 수 / 취소 · 저장
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoSheet(
    palette: ReaderPalette,
    initial: String,
    quote: String,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = text.isNotBlank() && text.trim() != initial.trim()

    LaunchedEffect(Unit) {
        delay(250) // 시트가 올라온 뒤 키보드
        runCatching { focus.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.popupBackground,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ReaderIcons.Edit, null, tint = palette.highlightTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (initial.isBlank()) "메모 남기기" else "메모 수정",
                    color = palette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    Text(
                        "삭제",
                        color = palette.subText,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 인용 문장
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.highlightTint)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    quote,
                    color = palette.subText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 입력칸
            val fieldShape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 260.dp)
                    .clip(fieldShape)
                    .background(palette.chipBackground)
                    .border(1.dp, palette.popupBorder, fieldShape)
                    .padding(16.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "이 문장에서 떠오른 생각을 적어보세요",
                        color = palette.subText,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= MEMO_MAX_LENGTH) text = it },
                    textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 24.sp),
                    cursorBrush = SolidColor(palette.highlightTint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${text.length} / $MEMO_MAX_LENGTH",
                    color = palette.subText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetButton("취소", palette.chipBackground, palette.text, enabled = true, onClick = onDismiss)
                    SheetButton("저장", palette.highlightTint, palette.background, enabled = canSave) { onSave(text) }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 문장 팝업 안에 들어가는 메모 미리보기 카드. 누르면 수정 시트를 연다. */
@Composable
internal fun MemoPreview(palette: ReaderPalette, memo: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.highlightTint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(ReaderIcons.Edit, null, tint = palette.highlightTint, modifier = Modifier.padding(top = 2.dp).size(12.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("내 메모", color = palette.highlightTint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                memo,
                color = palette.text,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
