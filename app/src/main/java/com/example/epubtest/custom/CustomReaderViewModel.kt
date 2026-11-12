package com.example.epubtest.custom

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubtest.custom.epub.EpubBook
import com.example.epubtest.custom.epub.EpubParser
import com.example.epubtest.reader.DummyBook
import com.example.epubtest.reader.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CustomReaderViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface UiState {
        data object Loading : UiState
        data class Error(val message: String) : UiState
        data class Ready(val book: EpubBook) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(CustomReaderSettings(dark = ThemePrefs.isDark(application)))
    val settings: StateFlow<CustomReaderSettings> = _settings.asStateFlow()

    private val _annotations = MutableStateFlow<List<CustomAnnotation>>(emptyList())
    val annotations: StateFlow<List<CustomAnnotation>> = _annotations.asStateFlow()

    private val _popup = MutableStateFlow<CustomPopup?>(null)
    val popup: StateFlow<CustomPopup?> = _popup.asStateFlow()

    private val prefs = application.getSharedPreferences("custom_reader", Context.MODE_PRIVATE)

    /** 마지막 읽은 위치 (앱 재실행 / 설정 변경으로 페이지가 다시 나뉘어도 복원). */
    var position: ReadingPosition = ReadingPosition(
        prefs.getInt(KEY_CHAPTER, 0),
        prefs.getInt(KEY_BLOCK, 0),
        prefs.getInt(KEY_OFFSET, 0),
    )
        private set

    init {
        viewModelScope.launch {
            _state.value = runCatching {
                withContext(Dispatchers.IO) {
                    // Readium 화면과 "같은 EPUB 파일"을 직접 파싱한다.
                    val file = DummyBook.writeEpub(getApplication<Application>().cacheDir)
                    EpubParser.parse(file)
                }
            }.fold(
                onSuccess = { UiState.Ready(it) },
                onFailure = { UiState.Error(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    fun savePosition(p: ReadingPosition) {
        if (p == position) return
        position = p
        prefs.edit {
            putInt(KEY_CHAPTER, p.chapter)
            putInt(KEY_BLOCK, p.block)
            putInt(KEY_OFFSET, p.offset)
        }
    }

    fun updateSettings(transform: (CustomReaderSettings) -> CustomReaderSettings) {
        _settings.update(transform)
        ThemePrefs.setDark(getApplication(), _settings.value.dark)
    }

    fun showPopup(popup: CustomPopup?) {
        _popup.value = popup
    }

    fun annotationFor(chapter: Int, block: Int): CustomAnnotation? =
        _annotations.value.firstOrNull { it.chapter == chapter && it.block == block }

    fun toggleLike(chapter: Int, block: Int, text: String) = _annotations.update { list ->
        val existing = list.firstOrNull { it.chapter == chapter && it.block == block }
        if (existing == null) {
            list + CustomAnnotation(UUID.randomUUID().toString(), chapter, block, text, liked = true)
        } else {
            list.map { if (it.id == existing.id) it.copy(liked = !it.liked) else it }.filterNot { it.isEmpty }
        }
    }

    fun saveMemo(chapter: Int, block: Int, text: String, memo: String) = _annotations.update { list ->
        val value = memo.trim().ifBlank { null }
        val existing = list.firstOrNull { it.chapter == chapter && it.block == block }
        if (existing == null) {
            if (value == null) list else list + CustomAnnotation(UUID.randomUUID().toString(), chapter, block, text, memo = value)
        } else {
            list.map { if (it.id == existing.id) it.copy(memo = value) else it }.filterNot { it.isEmpty }
        }
    }

    fun deleteAnnotation(id: String) = _annotations.update { list -> list.filterNot { it.id == id } }

    private companion object {
        const val KEY_CHAPTER = "chapter"
        const val KEY_BLOCK = "block"
        const val KEY_OFFSET = "offset"
    }
}
