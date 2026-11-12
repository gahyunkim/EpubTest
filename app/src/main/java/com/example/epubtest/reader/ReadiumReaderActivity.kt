package com.example.epubtest.reader

import android.graphics.Color
import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import com.example.epubtest.reader.ReaderViewModel.UiState
import org.readium.r2.shared.publication.Publication

/**
 * [Readium 버전] EpubNavigatorFragment(Readium)는 Fragment라서 FragmentActivity + fragment-compose의
 * AndroidFragment로 Compose 화면 안에 넣는다.
 */
class ReadiumReaderActivity : FragmentActivity() {

    private val vm: ReaderViewModel by viewModels()
    private var factoryInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // FragmentManager가 (회전 등으로) EpubNavigatorFragment를 복원하기 전에 팩토리를 지정해야 한다.
        val ready = vm.state.value as? UiState.Ready
        if (ready != null) installNavigatorFactory(ready.publication)
        // 프로세스가 종료된 뒤 복원되는 경우: Publication을 다시 열어야 하므로 이전 Fragment 상태는 버린다.
        super.onCreate(if (ready == null) null else savedInstanceState)

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
                UiState.Loading -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.background),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = palette.text) }

                is UiState.Error -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("EPUB을 열 수 없습니다\n${s.message}", color = palette.text) }

                is UiState.Ready -> {
                    // 첫 진입: AndroidFragment가 Fragment를 만들기 전에 팩토리 지정 (중복 호출해도 1회만 적용)
                    installNavigatorFactory(s.publication)
                    ReaderScreen(vm = vm, publication = s.publication, onBack = { finish() })
                }
            }
        }
    }

    /** EpubNavigatorFragment 생성용 팩토리를 한 번만 지정한다. */
    private fun installNavigatorFactory(publication: Publication) {
        if (factoryInstalled) return
        supportFragmentManager.fragmentFactory = vm.createFragmentFactory(publication)
        factoryInstalled = true
    }
}
