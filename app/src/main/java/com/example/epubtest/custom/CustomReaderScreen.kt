package com.example.epubtest.custom

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubtest.custom.epub.EpubBlock
import com.example.epubtest.custom.epub.EpubBook
import com.example.epubtest.reader.DummyBook
import com.example.epubtest.reader.MemoSheet
import com.example.epubtest.reader.PopupLayer
import com.example.epubtest.reader.ReaderBottomBar
import com.example.epubtest.reader.ReaderIcons
import com.example.epubtest.reader.ReaderPalette
import com.example.epubtest.reader.ReaderTopBar
import com.example.epubtest.reader.SentencePopup
import com.example.epubtest.reader.SettingLabel
import com.example.epubtest.reader.ThemeChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val PAGE_H_PADDING = 28.dp

/** 페이지/스크롤 모드가 하단 바 · 목차 이동에 쓰는 동작을 등록하는 곳. */
@Stable
private class ReaderNavigator {
    var label by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var previous: () -> Unit = {}
    var next: () -> Unit = {}
    var goTo: (ReadingPosition) -> Unit = {}
}

/** 블록 하나를 그리는 데 필요한 공통 값 */
private class BlockEnv(
    val book: EpubBook,
    val metrics: ReaderMetrics,
    val palette: ReaderPalette,
    val annotations: List<CustomAnnotation>,
    val popup: CustomPopup?,
    val readerTop: () -> Float,
    val onLongPress: (chapter: Int, block: Int, top: Float, bottom: Float) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun CustomReaderScreen(vm: CustomReaderViewModel, book: EpubBook, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val annotations by vm.annotations.collectAsState()
    val popup by vm.popup.collectAsState()
    val palette = ReaderPalette.of(settings.dark)
    val context = LocalContext.current
    val density = LocalDensity.current

    val navigator = remember { ReaderNavigator() }
    var showSettings by remember { mutableStateOf(false) }
    var memoTarget by remember { mutableStateOf<CustomPopup?>(null) }
    var readerTop by remember { mutableFloatStateOf(0f) }

    val metrics = remember(settings.fontScale, settings.lineHeight, density) {
        val baseSp = 17f * settings.fontScale
        ReaderMetrics(
            baseSp = baseSp,
            emPx = with(density) { baseSp.sp.toPx() },
            density = density.density,
            defaultLineHeight = settings.lineHeight,
        )
    }

    val env = BlockEnv(
        book = book,
        metrics = metrics,
        palette = palette,
        annotations = annotations,
        popup = popup,
        readerTop = { readerTop },
        onLongPress = { chapter, block, top, bottom ->
            val text = (book.chapters[chapter].blocks[block] as? EpubBlock.Text)?.text?.text.orEmpty()
            vm.showPopup(CustomPopup(chapter, block, text.normalized(), top, bottom))
        },
        onDismiss = { vm.showPopup(null) },
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        ReaderTopBar(
            title = book.title,
            author = book.author,
            palette = palette,
            dark = settings.dark,
            onBack = onBack,
            onToggleTheme = { vm.updateSettings { it.copy(dark = !it.dark) } },
            onSettings = { showSettings = true },
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { readerTop = it.positionInRoot().y }
        ) {
            if (settings.scroll) {
                ScrollReader(vm, env, navigator)
            } else {
                PagedReader(vm, env, navigator)
            }

            popup?.let { p ->
                val annotation = annotations.firstOrNull { it.chapter == p.chapter && it.block == p.block }
                PopupLayer(anchorTop = p.anchorTop, anchorBottom = p.anchorBottom) {
                    SentencePopup(
                        palette = palette,
                        characters = DummyBook.charactersFor(p.chapter),
                        liked = annotation?.liked == true,
                        memo = annotation?.memo,
                        onCharacter = { character ->
                            Toast.makeText(context, "${character.name}와(과)의 대화 → 04 인물 대화 화면 (미구현)", Toast.LENGTH_SHORT).show()
                        },
                        onLike = { vm.toggleLike(p.chapter, p.block, p.text) },
                        onMemo = { memoTarget = p },
                        onClose = { vm.showPopup(null) },
                    )
                }
            }
        }

        ReaderBottomBar(
            palette = palette,
            pageLabel = navigator.label,
            canGoBack = navigator.canGoBack,
            onPrevious = { navigator.previous() },
            onNext = { navigator.next() },
        )
    }

    if (showSettings) {
        CustomSettingsSheet(
            book = book,
            settings = settings,
            annotations = annotations,
            palette = palette,
            onChange = { s -> vm.updateSettings { s } },
            onOpen = { position ->
                showSettings = false
                vm.showPopup(null)
                navigator.goTo(position)
            },
            onDeleteAnnotation = { vm.deleteAnnotation(it.id) },
            onDismiss = { showSettings = false },
        )
    }

    memoTarget?.let { target ->
        val existing = vm.annotationFor(target.chapter, target.block)?.memo.orEmpty()
        MemoSheet(
            palette = palette,
            initial = existing,
            quote = target.text,
            onSave = { memo ->
                vm.saveMemo(target.chapter, target.block, target.text, memo)
                memoTarget = null
            },
            onDelete = if (existing.isBlank()) null else {
                {
                    vm.saveMemo(target.chapter, target.block, target.text, "")
                    memoTarget = null
                }
            },
            onDismiss = { memoTarget = null },
        )
    }
}

// ───────────────────────────── 페이지 모드 ─────────────────────────────

@Composable
private fun PagedReader(vm: CustomReaderViewModel, env: BlockEnv, navigator: ReaderNavigator) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val vPadding = if (landscape) 16.dp else 24.dp // Readium 화면과 같은 위/아래 여백

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val pageWidth = constraints.maxWidth - with(density) { (PAGE_H_PADDING * 2).roundToPx() }
        val pageHeight = constraints.maxHeight - with(density) { (vPadding * 2).roundToPx() }

        val pages by produceState<List<Page>?>(null, env.book, env.metrics, pageWidth, pageHeight) {
            value = withContext(Dispatchers.Default) {
                Paginator.paginate(env.book, measurer, pageWidth, pageHeight, env.metrics)
            }
        }

        val list = pages
        if (list == null || list.isEmpty()) {
            CircularProgressIndicator(color = env.palette.subText, modifier = Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }

        key(list) {
            val pagerState = rememberPagerState(initialPage = list.indexOf(vm.position)) { list.size }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.drop(1).collect { vm.savePosition(list[it].position) }
            }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.isScrollInProgress }.filter { it }.collect { vm.showPopup(null) }
            }

            val current = pagerState.currentPage
            SideEffect {
                navigator.label = "${current + 1} / ${list.size}"
                navigator.canGoBack = current > 0
                navigator.previous = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } }
                navigator.next = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(list.lastIndex)) } }
                navigator.goTo = { position ->
                    vm.savePosition(position)
                    scope.launch { pagerState.scrollToPage(list.indexOf(position)) }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { index ->
                val page = list[index]
                Column(
                    Modifier
                        .fillMaxSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { vm.showPopup(null) }
                        .padding(vertical = vPadding)
                ) {
                    page.items.forEach { item ->
                        if (item.gapPx > 0f) Spacer(Modifier.height(item.gapPx.toDp(env.metrics)))
                        BlockView(env, item.chapter, item.block, item.start, item.end, item)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── 스크롤 모드 ─────────────────────────────

@Composable
private fun ScrollReader(vm: CustomReaderViewModel, env: BlockEnv, navigator: ReaderNavigator) {
    val scope = rememberCoroutineScope()
    val entries = remember(env.book) {
        env.book.chapters.flatMap { ch -> ch.blocks.indices.map { ReadingPosition(ch.index, it) } }
    }
    fun indexOf(position: ReadingPosition) =
        entries.indexOfLast { it <= position.copy(offset = 0) }.coerceAtLeast(0)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(vm.position))

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.drop(1).collect { i ->
            entries.getOrNull(i)?.let(vm::savePosition)
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { vm.showPopup(null) }
    }

    val currentChapter = entries.getOrNull(listState.firstVisibleItemIndex)?.chapter ?: 0
    SideEffect {
        navigator.label = "${currentChapter + 1} / ${env.book.chapters.size}"
        navigator.canGoBack = listState.firstVisibleItemIndex > 0
        val chapterStart = { chapter: Int -> entries.indexOfFirst { it.chapter == chapter }.coerceAtLeast(0) }
        navigator.previous = {
            val first = entries.getOrNull(listState.firstVisibleItemIndex) ?: ReadingPosition()
            // 챕터 중간이면 그 챕터 처음으로, 이미 처음이면 이전 챕터로
            val target = if (first.block > 0) first.chapter else (first.chapter - 1).coerceAtLeast(0)
            scope.launch { listState.animateScrollToItem(chapterStart(target)) }
        }
        navigator.next = {
            val first = entries.getOrNull(listState.firstVisibleItemIndex) ?: ReadingPosition()
            if (first.chapter + 1 < env.book.chapters.size) {
                scope.launch { listState.animateScrollToItem(chapterStart(first.chapter + 1)) }
            }
        }
        navigator.goTo = { position ->
            vm.savePosition(position)
            scope.launch { listState.scrollToItem(indexOf(position)) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null) { vm.showPopup(null) },
        contentPadding = PaddingValues(vertical = 24.dp),
    ) {
        itemsIndexed(entries, key = { _, p -> "${p.chapter}-${p.block}" }) { i, p ->
            val blocks = env.book.chapters[p.chapter].blocks
            if (p.block == 0 && i > 0) {
                // 챕터 경계
                HorizontalDivider(
                    color = env.palette.divider,
                    modifier = Modifier.padding(horizontal = PAGE_H_PADDING, vertical = 40.dp),
                )
            } else {
                val gap = gapPx(blocks.getOrNull(p.block - 1), blocks[p.block], env.metrics)
                if (gap > 0f) Spacer(Modifier.height(gap.toDp(env.metrics)))
            }
            BlockView(env, p.chapter, p.block, 0, Int.MAX_VALUE, pageItem = null)
        }
    }
}

// ───────────────────────────── 블록 렌더링 ─────────────────────────────

/**
 * EPUB 블록 하나(또는 페이지에 걸친 조각)를 그린다.
 * @param pageItem 페이지 모드일 때 이미지 크기 등 미리 계산된 값
 */
@Composable
private fun BlockView(env: BlockEnv, chapter: Int, blockIndex: Int, start: Int, end: Int, pageItem: PageItem?) {
    val block = env.book.chapters[chapter].blocks[blockIndex]
    val style = block.style
    // 페이지 좌우 여백(gutter) + CSS 좌우 margin. gutter 는 메모 표시 자리로도 쓴다.
    val sidePadding = Modifier.padding(
        start = PAGE_H_PADDING + (style.marginStart * env.metrics.emPx).toDp(env.metrics),
        end = PAGE_H_PADDING + (style.marginEnd * env.metrics.emPx).toDp(env.metrics),
    )

    when (block) {
        is EpubBlock.Text -> {
            val annotation = env.annotations.firstOrNull { it.chapter == chapter && it.block == blockIndex }
            val selected = env.popup?.let { it.chapter == chapter && it.block == blockIndex } == true
            val slice = remember(block, start, end) { block.text.slice(start, end) }
            val hasMemo = !annotation?.memo.isNullOrBlank()
            val decorated = remember(slice, annotation, selected, env.palette) {
                decorate(slice, env.palette, liked = annotation?.liked == true, selected = selected)
            }
            var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            val haptic = LocalHapticFeedback.current
            val openPopup = {
                coordinates?.let { c ->
                    val top = c.positionInRoot().y - env.readerTop()
                    env.onLongPress(chapter, blockIndex, top, top + c.size.height)
                }
            }
            Box(Modifier.fillMaxWidth()) {
            Text(
                text = decorated,
                style = block.textStyle(env.metrics, env.palette.text, continuation = start > 0),
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sidePadding)
                    // 메모가 있는 문단: 줄마다 강조색 점선 밑줄
                    .drawBehind {
                        val l = layout
                        if (hasMemo && l != null) {
                            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
                            for (line in 0 until l.lineCount) {
                                val y = l.getLineBaseline(line) + 4.dp.toPx()
                                drawLine(
                                    color = env.palette.highlightTint,
                                    start = Offset(l.getLineLeft(line), y),
                                    end = Offset(l.getLineRight(line), y),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dash,
                                )
                            }
                        }
                    }
                    .onGloballyPositioned { coordinates = it }
                    // 한 번 탭 = 팝업 닫기, 꾹 누르기 = 이 문단 팝업 열기
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = env.onDismiss,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openPopup()
                        },
                    ),
            )
            // 메모 표시: 문단 첫 줄 오른쪽 여백에 작은 메모 배지. 누르면 메모가 담긴 팝업이 열린다.
            val l = layout
            if (hasMemo && start == 0 && l != null && l.lineCount > 0) {
                val lineCenterPx = (l.getLineTop(0) + l.getLineBottom(0)) / 2f
                val badge = 22.dp
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(PAGE_H_PADDING)
                        .offset(y = lineCenterPx.toDp(env.metrics) - badge / 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(badge)
                            .clip(CircleShape)
                            .background(env.palette.highlightTint.copy(alpha = 0.18f))
                            .clickable { openPopup() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ReaderIcons.Edit, "메모 보기", tint = env.palette.highlightTint, modifier = Modifier.size(12.dp))
                    }
                }
            }
            }
        }

        is EpubBlock.Image -> {
            val ratio = block.bitmap.width.toFloat() / block.bitmap.height.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().then(sidePadding), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = block.bitmap,
                    contentDescription = block.alt,
                    contentScale = ContentScale.Fit,
                    modifier = if (pageItem != null) {
                        Modifier
                            .width(pageItem.widthPx.toDp(env.metrics))
                            .height(pageItem.heightPx.toDp(env.metrics))
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp)
                            .aspectRatio(ratio)
                    },
                )
            }
        }

        is EpubBlock.Rule -> HorizontalDivider(color = env.palette.divider, modifier = sidePadding)
    }
}

/** 페이지 경계에서 잘린 범위. 끝의 줄바꿈은 빈 줄이 생기지 않게 제거한다. */
private fun AnnotatedString.slice(start: Int, end: Int): AnnotatedString {
    val s = start.coerceIn(0, length)
    var e = end.coerceIn(s, length)
    while (e > s && (this[e - 1] == '\n' || this[e - 1] == ' ')) e--
    return if (s == 0 && e == length) this else subSequence(s, e)
}

/** 좋아요(하이라이트) / 선택(배경+밑줄) 표시. 메모는 BlockView 에서 점선 밑줄 + 배지로 그린다. */
private fun decorate(text: AnnotatedString, palette: ReaderPalette, liked: Boolean, selected: Boolean): AnnotatedString {
    if (!liked && !selected) return text
    return buildAnnotatedString {
        append(text)
        if (liked) addStyle(SpanStyle(background = palette.highlightTint.copy(alpha = 0.28f)), 0, text.length)
        if (selected) {
            addStyle(
                SpanStyle(background = palette.selectionTint.copy(alpha = 0.28f), textDecoration = TextDecoration.Underline),
                0,
                text.length,
            )
        }
    }
}

private fun Float.toDp(metrics: ReaderMetrics): Dp = (this / metrics.density).dp

private fun String.normalized() = trim().replace(Regex("\\s+"), " ")

// ───────────────────────────── 설정 / 목차 / 내 문장 시트 ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomSettingsSheet(
    book: EpubBook,
    settings: CustomReaderSettings,
    annotations: List<CustomAnnotation>,
    palette: ReaderPalette,
    onChange: (CustomReaderSettings) -> Unit,
    onOpen: (ReadingPosition) -> Unit,
    onDeleteAnnotation: (CustomAnnotation) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.popupBackground, contentColor = palette.text) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf("보기 설정", "목차", "내 문장 ${annotations.size}").forEachIndexed { i, label ->
                    Text(
                        label,
                        color = if (tab == i) palette.text else palette.subText,
                        fontSize = 15.sp,
                        fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> {
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = palette.text,
                        activeTrackColor = palette.text,
                        inactiveTrackColor = palette.divider,
                    )
                    SettingLabel("글자 크기  ${(settings.fontScale * 100).roundToInt()}%", palette)
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { onChange(settings.copy(fontScale = (it * 10).roundToInt() / 10f)) },
                        valueRange = 0.8f..1.6f,
                        steps = 7,
                        colors = sliderColors,
                    )
                    SettingLabel("줄 간격  ${"%.1f".format(settings.lineHeight)}", palette)
                    Slider(
                        value = settings.lineHeight,
                        onValueChange = { onChange(settings.copy(lineHeight = (it * 10).roundToInt() / 10f)) },
                        valueRange = 1.4f..2.0f,
                        steps = 5,
                        colors = sliderColors,
                    )
                    SettingLabel("테마", palette)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        ThemeChip("다크", settings.dark, palette) { onChange(settings.copy(dark = true)) }
                        ThemeChip("라이트", !settings.dark, palette) { onChange(settings.copy(dark = false)) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("스크롤 모드", color = palette.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.scroll,
                            onCheckedChange = { onChange(settings.copy(scroll = it)) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = palette.selectionTint,
                                uncheckedTrackColor = palette.chipBackground,
                            ),
                        )
                    }
                }

                1 -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(book.toc) { _, entry ->
                        Text(
                            entry.title,
                            color = palette.text,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(ReadingPosition(entry.chapterIndex)) }
                                .padding(start = (entry.depth * 16).dp, top = 14.dp, bottom = 14.dp),
                        )
                        HorizontalDivider(color = palette.divider)
                    }
                }

                else -> if (annotations.isEmpty()) {
                    Text(
                        "아직 좋아요하거나 메모한 문장이 없어요.\n문장을 꾹 눌러서 좋아요/메모를 남겨보세요.",
                        color = palette.subText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        itemsIndexed(annotations, key = { _, a -> a.id }) { _, a ->
                            AnnotationRow(
                                annotation = a,
                                chapterTitle = book.chapters.getOrNull(a.chapter)?.title.orEmpty(),
                                palette = palette,
                                onClick = { onOpen(ReadingPosition(a.chapter, a.block)) },
                                onDelete = { onDeleteAnnotation(a) },
                            )
                            HorizontalDivider(color = palette.divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationRow(
    annotation: CustomAnnotation,
    chapterTitle: String,
    palette: ReaderPalette,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (annotation.liked) {
                    Icon(ReaderIcons.Favorite, null, tint = palette.highlightTint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(chapterTitle, color = palette.subText, fontSize = 11.sp)
            }
            Text(
                annotation.text,
                color = palette.text,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            annotation.memo?.let {
                Text("✎ $it", color = palette.highlightTint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ReaderIcons.Close, "삭제", tint = palette.subText, modifier = Modifier.size(14.dp))
        }
    }
}
