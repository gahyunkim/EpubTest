package com.example.epubtest

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.epubtest.custom.CustomReaderActivity
import com.example.epubtest.reader.DummyBook
import com.example.epubtest.reader.ReadiumReaderActivity
import com.example.epubtest.reader.ReaderIcons
import com.example.epubtest.reader.ReaderPalette

/**
 * 시작 화면. 같은 EPUB 파일을 두 가지 방식으로 연다.
 *  1) Readium kotlin-toolkit 으로 열기  → [ReadiumReaderActivity]
 *  2) 라이브러리 없이 직접 파싱/렌더링 → [CustomReaderActivity]
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            HomeScreen(
                onOpenReadium = { startActivity(Intent(this, ReadiumReaderActivity::class.java)) },
                onOpenCustom = { startActivity(Intent(this, CustomReaderActivity::class.java)) },
            )
        }
    }
}

@Composable
private fun HomeScreen(onOpenReadium: () -> Unit, onOpenCustom: () -> Unit) {
    val palette = ReaderPalette.Dark
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text("EPUB READER LAB", color = palette.highlightTint, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(DummyBook.TITLE, color = palette.text, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text(DummyBook.AUTHOR, color = palette.subText, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "같은 EPUB 파일을 두 가지 엔진으로 열어 비교해 보세요.",
            color = palette.subText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(32.dp))

        EngineCard(
            badge = "01",
            title = "Readium으로 열기",
            description = "readium kotlin-toolkit · EpubNavigatorFragment(WebView)",
            accent = Color(0xFFA9B9D3),
            onClick = onOpenReadium,
        )
        Spacer(Modifier.height(14.dp))
        EngineCard(
            badge = "02",
            title = "커스텀 리더로 열기",
            description = "라이브러리 없이 ZIP · OPF · XHTML · CSS 직접 파싱 → Compose 렌더링",
            accent = palette.highlightTint,
            onClick = onOpenCustom,
        )
    }
}

@Composable
private fun EngineCard(
    badge: String,
    title: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val palette = ReaderPalette.Dark
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.popupBackground)
            .border(1.dp, palette.popupBorder, shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(badge, color = palette.background, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, color = palette.subText, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(palette.chipBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ReaderIcons.ArrowForward, null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}
