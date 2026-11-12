package com.example.epubtest.reader

import android.app.Application
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.util.UUID

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Ready(val publication: Publication) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings(dark = ThemePrefs.isDark(application)))
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _annotations = MutableStateFlow<List<Annotation>>(emptyList())
    val annotations: StateFlow<List<Annotation>> = _annotations.asStateFlow()

    private val _popup = MutableStateFlow<PopupState?>(null)
    val popup: StateFlow<PopupState?> = _popup.asStateFlow()

    private val _selectionEvents = MutableSharedFlow<SelectionEvent>(extraBufferCapacity = 8)
    val selectionEvents: SharedFlow<SelectionEvent> = _selectionEvents.asSharedFlow()

    private val prefs = application.getSharedPreferences("reader", Context.MODE_PRIVATE)

    /** 마지막 읽은 위치 (앱 재실행 시 복원). */
    var lastLocator: Locator? = prefs.getString(KEY_LOCATOR, null)
        ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
        private set

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(application.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            application,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        // EPUB 파일 직접 열기: 지금은 더미 EPUB을 만들어서 연다.
        val file = withContext(Dispatchers.IO) {
            DummyBook.writeEpub(getApplication<Application>().cacheDir)
        }
        val asset = assetRetriever.retrieve(file).getOrElse {
            _state.value = UiState.Error(it.message)
            return
        }
        val publication = publicationOpener.open(asset, allowUserInteraction = false).getOrElse {
            _state.value = UiState.Error(it.message)
            return
        }
        _state.value = UiState.Ready(publication)
    }

    /**
     * EpubNavigatorFragment를 만드는 팩토리. Activity가 (재)생성될 때마다 최신 위치/설정으로 만든다.
     */
    fun createFragmentFactory(publication: Publication): FragmentFactory =
        EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = lastLocator,
            initialPreferences = _settings.value.toEpubPreferences(),
            configuration = EpubNavigatorFragment.Configuration {
                // 롱프레스 선택 시 기본 복사/공유 메뉴 대신 우리 Compose 팝업을 띄운다.
                selectionActionModeCallback = actionModeCallback
                // 인셋은 Compose 상/하단 바에서 처리
                shouldApplyInsetsPadding = false
                // Underline(isActive=true) = 배경 + 밑줄 → Figma "문장 선택" 표현
                // Highlight = 좋아요한 문장
                decorationTemplates = HtmlDecorationTemplates.defaultTemplates(
                    lineWeight = 1,
                    cornerRadius = 2,
                    alpha = 0.28,
                )
            },
        )

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.clear()
            _selectionEvents.tryEmit(SelectionEvent.Started)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu?.clear()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode?) {
            _selectionEvents.tryEmit(SelectionEvent.Ended)
        }
    }

    fun saveLocator(locator: Locator) {
        lastLocator = locator
        prefs.edit { putString(KEY_LOCATOR, locator.toJSON().toString()) }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        _settings.update(transform)
        ThemePrefs.setDark(getApplication(), _settings.value.dark)
    }

    fun showPopup(popup: PopupState?) {
        _popup.value = popup
    }

    fun annotationFor(locator: Locator): Annotation? =
        _annotations.value.firstOrNull { it.locator.sameTarget(locator) }

    fun annotationById(id: String): Annotation? =
        _annotations.value.firstOrNull { it.id == id }

    fun toggleLike(locator: Locator, text: String) {
        _annotations.update { list ->
            val existing = list.firstOrNull { it.locator.sameTarget(locator) }
            if (existing == null) {
                list + Annotation(UUID.randomUUID().toString(), locator, text, liked = true)
            } else {
                list.map { if (it.id == existing.id) it.copy(liked = !it.liked) else it }
                    .filterNot { it.isEmpty }
            }
        }
    }

    fun saveMemo(locator: Locator, text: String, memo: String) {
        val value = memo.trim().ifBlank { null }
        _annotations.update { list ->
            val existing = list.firstOrNull { it.locator.sameTarget(locator) }
            if (existing == null) {
                if (value == null) list
                else list + Annotation(UUID.randomUUID().toString(), locator, text, memo = value)
            } else {
                list.map { if (it.id == existing.id) it.copy(memo = value) else it }
                    .filterNot { it.isEmpty }
            }
        }
    }

    fun deleteAnnotation(id: String) {
        _annotations.update { list -> list.filterNot { it.id == id } }
    }

    override fun onCleared() {
        (_state.value as? UiState.Ready)?.publication?.close()
        super.onCleared()
    }

    private companion object {
        const val KEY_LOCATOR = "last_locator"
    }
}
