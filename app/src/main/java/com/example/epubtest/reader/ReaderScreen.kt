package com.example.epubtest.reader

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.compose.AndroidFragment
import com.example.epubtest.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import kotlin.math.roundToInt

private const val GROUP_ANNOTATIONS = "annotations"
private const val GROUP_SELECTION = "selection"
private const val MEMO_SUFFIX = "#memo"

private fun String.normalized() = trim().replace(Regex("\\s+"), " ")

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    publication: Publication,
    onBack: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val annotations by vm.annotations.collectAsState()
    val popup by vm.popup.collectAsState()
    val palette = ReaderPalette.of(settings.dark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var memoTarget by remember { mutableStateOf<PopupState?>(null) }

    val locatorFlow: StateFlow<Locator?> = remember(navigator) {
        navigator?.currentLocator ?: MutableStateFlow(null)
    }
    val locator by locatorFlow.collectAsState()
    val readingOrder = publication.readingOrder
    val chapterIndex = locator?.let { loc ->
        readingOrder.indexOfFirst { it.url().isEquivalent(loc.href) }
    }?.coerceAtLeast(0) ?: 0
    val progression = locator?.locations?.progression ?: 0.0

    // 페이지 모드에서는 Readium이 WebView 위/아래에 여백(dimen)을 주므로 팝업 좌표 보정에 사용
    val verticalPaddingPx = remember {
        context.resources.getDimensionPixelSize(R.dimen.readium_navigator_epub_vertical_padding).toFloat()
    }
    val contentTopOffset = if (settings.scroll) 0f else verticalPaddingPx

    val currentChapter by rememberUpdatedState(chapterIndex)
    val currentTopOffset by rememberUpdatedState(contentTopOffset)

    // 읽은 위치 저장 + 페이지가 바뀌면 팝업 닫기
    LaunchedEffect(locator?.href, progression) {
        locator?.let { vm.saveLocator(it) }
        if (vm.popup.value?.fromSelection == false) vm.showPopup(null)
    }

    // 보기 설정 → Readium Preferences
    LaunchedEffect(navigator, settings) {
        navigator?.submitPreferences(settings.toEpubPreferences())
    }

    // 좋아요 / 메모 decoration
    LaunchedEffect(navigator, annotations, settings.dark) {
        val nav = navigator ?: return@LaunchedEffect
        val tint = palette.highlightTint.toArgb()
        // 좋아요 = 하이라이트, 메모 = 밑줄. 둘 다 있으면 두 decoration 을 겹쳐 그린다.
        nav.applyDecorations(
            annotations.flatMap { a ->
                buildList {
                    if (a.liked) add(Decoration(id = a.id, locator = a.locator, style = Decoration.Style.Highlight(tint)))
                    if (!a.memo.isNullOrBlank()) {
                        add(Decoration(id = "${a.id}$MEMO_SUFFIX", locator = a.locator, style = Decoration.Style.Underline(tint)))
                    }
                }
            },
            GROUP_ANNOTATIONS,
        )
    }

    // 선택된 문장(배경 + 밑줄) decoration
    LaunchedEffect(navigator, popup, settings.dark) {
        val nav = navigator ?: return@LaunchedEffect
        val p = popup
        nav.applyDecorations(
            if (p != null && !p.fromSelection) {
                listOf(
                    Decoration(
                        id = "selected",
                        locator = p.locator,
                        style = Decoration.Style.Underline(palette.selectionTint.toArgb(), isActive = true),
                    )
                )
            } else {
                emptyList()
            },
            GROUP_SELECTION,
        )
    }

    // 탭 / decoration 탭 리스너
    DisposableEffect(navigator) {
        val nav = navigator ?: return@DisposableEffect onDispose {}

        val inputListener = object : InputListener {
            // 한 번 탭은 팝업만 닫는다. 팝업은 꾹 누르기(텍스트 선택)로 연다.
            override fun onTap(event: TapEvent): Boolean {
                if (vm.popup.value == null) return false
                vm.showPopup(null)
                return true
            }
        }
        val decorationListener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val annotation = vm.annotationById(event.decoration.id.removeSuffix(MEMO_SUFFIX)) ?: return false
                val current = vm.popup.value
                if (current != null && current.locator.sameTarget(annotation.locator)) {
                    vm.showPopup(null)
                    return true
                }
                val top = event.rect?.top ?: event.point?.y ?: 0f
                val bottom = event.rect?.bottom ?: top
                vm.showPopup(
                    PopupState(annotation.locator, annotation.text, top, bottom, currentChapter)
                )
                return true
            }
        }
        nav.addInputListener(inputListener)
        nav.addDecorationListener(GROUP_ANNOTATIONS, decorationListener)
        onDispose {
            nav.removeInputListener(inputListener)
            nav.removeDecorationListener(decorationListener)
        }
    }

    // 롱프레스 텍스트 선택 → 같은 팝업
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        vm.selectionEvents.collect { event ->
            when (event) {
                SelectionEvent.Started -> {
                    delay(250) // WebView 선택 영역이 확정될 때까지 잠시 대기
                    val selection = nav.currentSelection() ?: return@collect
                    val text = selection.locator.text.highlight ?: return@collect
                    vm.showPopup(
                        PopupState(
                            locator = selection.locator,
                            text = text,
                            anchorTop = selection.rect?.top ?: 0f,
                            anchorBottom = selection.rect?.bottom ?: 0f,
                            chapterIndex = currentChapter,
                            fromSelection = true,
                        )
                    )
                }
                SelectionEvent.Ended ->
                    if (vm.popup.value?.fromSelection == true) vm.showPopup(null)
            }
        }
    }

    /** 선택(롱프레스) 팝업이면 최신 선택 영역으로 확정하고 네이티브 선택을 해제한다. */
    suspend fun resolveTarget(p: PopupState): PopupState {
        if (!p.fromSelection) return p
        val nav = navigator ?: return p
        val loc = nav.currentSelection()?.locator ?: p.locator
        val resolved = p.copy(locator = loc, text = loc.text.highlight ?: p.text, fromSelection = false)
        vm.showPopup(resolved)
        nav.clearSelection()
        return resolved
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        ReaderTopBar(
            title = publication.metadata.title ?: DummyBook.TITLE,
            author = publication.metadata.authors.firstOrNull()?.name ?: DummyBook.AUTHOR,
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
        ) {
            AndroidFragment<EpubNavigatorFragment>(modifier = Modifier.fillMaxSize()) { fragment ->
                navigator = fragment
            }

            popup?.let { p ->
                val annotation = annotations.firstOrNull { it.locator.sameTarget(p.locator) }
                PopupLayer(anchorTop = p.anchorTop, anchorBottom = p.anchorBottom) {
                    SentencePopup(
                        palette = palette,
                        characters = DummyBook.charactersFor(p.chapterIndex),
                        liked = annotation?.liked == true,
                        memo = annotation?.memo,
                        onCharacter = { character ->
                            Toast.makeText(
                                context,
                                "${character.name}와(과)의 대화 → 04 인물 대화 화면 (미구현)",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onLike = { scope.launch { resolveTarget(p).let { vm.toggleLike(it.locator, it.text.normalized()) } } },
                        onMemo = { scope.launch { memoTarget = resolveTarget(p) } },
                        onClose = {
                            if (p.fromSelection) navigator?.clearSelection()
                            vm.showPopup(null)
                        },
                    )
                }
            }
        }

        ReaderBottomBar(
            palette = palette,
            pageLabel = "${chapterIndex + 1} / ${readingOrder.size}",
            canGoBack = chapterIndex > 0 || progression > 0.0,
            onPrevious = { navigator?.goBackward(animated = true) },
            onNext = { navigator?.goForward(animated = true) },
        )
    }

    if (showSettings) {
        ReaderSettingsSheet(
            settings = settings,
            annotations = annotations,
            palette = palette,
            onChange = { vm.updateSettings { _ -> it } },
            onOpenAnnotation = { a ->
                showSettings = false
                navigator?.go(a.locator, animated = false)
            },
            onDeleteAnnotation = { vm.deleteAnnotation(it.id) },
            onDismiss = { showSettings = false },
        )
    }

    memoTarget?.let { target ->
        val existing = vm.annotationFor(target.locator)?.memo.orEmpty()
        MemoSheet(
            palette = palette,
            initial = existing,
            quote = target.text.normalized(),
            onSave = { memo ->
                vm.saveMemo(target.locator, target.text.normalized(), memo)
                memoTarget = null
            },
            onDelete = if (existing.isBlank()) null else {
                {
                    vm.saveMemo(target.locator, target.text.normalized(), "")
                    memoTarget = null
                }
            },
            onDismiss = { memoTarget = null },
        )
    }
}

// ───────────────────────────── UI 컴포넌트 ─────────────────────────────

@Composable
internal fun ReaderTopBar(
    title: String,
    author: String,
    palette: ReaderPalette,
    dark: Boolean,
    onBack: () -> Unit,
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ReaderIcons.ChevronLeft, "뒤로", tint = palette.text, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = palette.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(author, color = palette.subText, fontSize = 10.sp)
            }
            // 다크 ↔ 라이트 전환: 바꿀 대상 모드의 아이콘을 보여준다 (다크일 때 해, 라이트일 때 달)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (dark) ReaderIcons.LightMode else ReaderIcons.DarkMode,
                    if (dark) "라이트 모드로 전환" else "다크 모드로 전환",
                    tint = palette.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider(color = palette.divider, thickness = 1.dp)
    }
}

@Composable
internal fun ReaderBottomBar(
    palette: ReaderPalette,
    pageLabel: String,
    canGoBack: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(color = palette.divider, thickness = 1.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = canGoBack, onClick = onPrevious)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .alpha(if (canGoBack) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ReaderIcons.ChevronLeft, null, tint = palette.subText, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("이전", color = palette.subText, fontSize = 12.sp)
            }
            Text(
                pageLabel,
                color = palette.text,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("다음", color = palette.text, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Icon(ReaderIcons.ArrowForward, null, tint = palette.text, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** 문단 위(공간이 없으면 아래)에 팝업을 배치한다. 팝업 밖 터치는 아래 리더로 그대로 전달된다. */
@Composable
internal fun PopupLayer(
    anchorTop: Float,
    anchorBottom: Float,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = 8.dp.roundToPx()
        var y = (anchorTop - placeable.height - gap).roundToInt()
        if (y < gap) {
            y = (anchorBottom + gap).roundToInt()
                .coerceAtMost(constraints.maxHeight - placeable.height - gap)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((constraints.maxWidth - placeable.width) / 2, y.coerceAtLeast(0))
        }
    }
}

@Composable
internal fun SentencePopup(
    palette: ReaderPalette,
    characters: List<DummyBook.BookCharacter>,
    liked: Boolean,
    memo: String?,
    onCharacter: (DummyBook.BookCharacter) -> Unit,
    onLike: () -> Unit,
    onMemo: () -> Unit,
    onClose: () -> Unit,
) {
    val hasMemo = !memo.isNullOrBlank()
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(palette.popupBackground)
            .border(1.dp, palette.popupBorder, shape)
            // 팝업 빈 영역 터치가 아래 WebView로 새지 않도록
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ReaderIcons.ChatBubble, null, tint = palette.subText, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(5.dp))
            Text("이 장면의 인물과 대화", color = palette.subText, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ReaderIcons.Close, "닫기", tint = palette.subText, modifier = Modifier.size(14.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            characters.take(2).forEach { character ->
                CharacterChip(character, palette, Modifier.weight(1f)) { onCharacter(character) }
            }
        }
        // 메모가 있으면 팝업 안에서 바로 보여준다
        if (hasMemo) {
            Box(Modifier.padding(top = 8.dp, end = 4.dp)) {
                MemoPreview(palette, memo.orEmpty(), onClick = onMemo)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            PopupAction(
                icon = if (liked) ReaderIcons.Favorite else ReaderIcons.FavoriteBorder,
                label = "좋아요",
                tint = if (liked) palette.highlightTint else palette.text,
                textColor = palette.text,
                onClick = onLike,
            )
            Spacer(Modifier.width(20.dp))
            PopupAction(
                icon = ReaderIcons.Edit,
                label = if (hasMemo) "메모 수정" else "메모",
                tint = if (hasMemo) palette.highlightTint else palette.text,
                textColor = palette.text,
                onClick = onMemo,
            )
        }
    }
}

@Composable
private fun CharacterChip(
    character: DummyBook.BookCharacter,
    palette: ReaderPalette,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.chipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(character.avatarColor)),
            contentAlignment = Alignment.Center,
        ) {
            Text(character.initial, color = Color(0xFF2B2E33), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            character.name,
            color = palette.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(ReaderIcons.ArrowForward, null, tint = palette.subText, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun PopupAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = textColor, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    annotations: List<Annotation>,
    palette: ReaderPalette,
    onChange: (ReaderSettings) -> Unit,
    onOpenAnnotation: (Annotation) -> Unit,
    onDeleteAnnotation: (Annotation) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.popupBackground,
        contentColor = palette.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf("보기 설정", "내 문장 ${annotations.size}").forEachIndexed { i, label ->
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
            if (tab == 0) {
                val sliderColors = SliderDefaults.colors(
                    thumbColor = palette.text,
                    activeTrackColor = palette.text,
                    inactiveTrackColor = palette.divider,
                )
                SettingLabel("글자 크기  ${(settings.fontScale * 100).roundToInt()}%", palette)
                Slider(
                    value = settings.fontScale.toFloat(),
                    onValueChange = { onChange(settings.copy(fontScale = (it * 10).roundToInt() / 10.0)) },
                    valueRange = 0.8f..1.6f,
                    steps = 7,
                    colors = sliderColors,
                )
                SettingLabel("줄 간격  ${"%.1f".format(settings.lineHeight)}", palette)
                Slider(
                    value = settings.lineHeight.toFloat(),
                    onValueChange = { onChange(settings.copy(lineHeight = (it * 10).roundToInt() / 10.0)) },
                    valueRange = 1.4f..2.0f,
                    steps = 5,
                    colors = sliderColors,
                )
                SettingLabel("테마", palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    ThemeChip("다크", settings.dark, palette) { onChange(settings.copy(dark = true)) }
                    ThemeChip("라이트", !settings.dark, palette) { onChange(settings.copy(dark = false)) }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            } else {
                if (annotations.isEmpty()) {
                    Text(
                        "아직 좋아요하거나 메모한 문장이 없어요.\n문장을 꾹 눌러서 좋아요/메모를 남겨보세요.",
                        color = palette.subText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(annotations, key = { it.id }) { a ->
                            AnnotationRow(a, palette, { onOpenAnnotation(a) }, { onDeleteAnnotation(a) })
                            HorizontalDivider(color = palette.divider)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingLabel(text: String, palette: ReaderPalette) {
    Text(text, color = palette.subText, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
internal fun ThemeChip(label: String, selected: Boolean, palette: ReaderPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.text else palette.chipBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) palette.background else palette.text, fontSize = 13.sp)
    }
}

@Composable
private fun AnnotationRow(
    annotation: Annotation,
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
                Text(
                    annotation.locator.title ?: "",
                    color = palette.subText,
                    fontSize = 11.sp,
                )
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
