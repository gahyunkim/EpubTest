package com.example.epubtest.custom

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.epubtest.reader.ReaderPalette

/**
 * [커스텀 버전] Readium 없이 EPUB 을 직접 읽어서 Compose 로 그리는 리더.
 *
 * - 파싱: [com.example.epubtest.custom.epub.EpubParser] (java.util.zip + 플랫폼 XmlPullParser + 자체 CSS 해석)
 * - 페이지 나누기: [Paginator] (TextMeasurer 로 줄 단위 측정)
 * - 렌더링: [CustomReaderScreen] (HorizontalPager / LazyColumn)
 */
class CustomReaderActivity : ComponentActivity() {

    private val vm: CustomReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsState()
            val settings by vm.settings.collectAsState()
            val palette = ReaderPalette.of(settings.dark)

            LaunchedEffect(settings.dark) {
                val style = if (settings.dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            when (val s = state) {
                CustomReaderViewModel.UiState.Loading -> Box(
                    Modifier.fillMaxSize().background(palette.background),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = palette.text) }

                is CustomReaderViewModel.UiState.Error -> Box(
                    Modifier.fillMaxSize().background(palette.background).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("EPUB을 열 수 없습니다\n${s.message}", color = palette.text) }

                is CustomReaderViewModel.UiState.Ready ->
                    CustomReaderScreen(vm = vm, book = s.book, onBack = { finish() })
            }
        }
    }
}
