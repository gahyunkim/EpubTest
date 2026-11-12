package com.example.epubtest.reader

import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

/**
 * "한 줄(문단) 탭" 구현.
 *
 * Readium은 탭 좌표(TapEvent.point = clientX/Y × devicePixelRatio)만 주므로,
 * [EpubNavigatorFragment.evaluateJavascript]로 현재 페이지 WebView에서 해당 좌표의 문단을 찾아
 * CSS selector + 텍스트 + 화면 사각형을 돌려받는다.
 * 이 값으로 만든 [Locator]는 Readium decoration(하이라이트/밑줄)에 그대로 쓸 수 있다.
 */
object ParagraphHitTest {

    data class Hit(
        val cssSelector: String,
        val text: String,
        val top: Float,
        val bottom: Float,
    )

    suspend fun find(navigator: EpubNavigatorFragment, x: Float, y: Float): Hit? {
        val raw = navigator.evaluateJavascript(script(x, y)) ?: return null
        if (raw == "null" || raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            Hit(
                cssSelector = json.getString("cssSelector"),
                text = json.getString("text"),
                top = json.getDouble("top").toFloat(),
                bottom = json.getDouble("bottom").toFloat(),
            )
        }.getOrNull()
    }

    /** 탭한 문단을 가리키는 Locator. text.highlight + cssSelector 로 Readium JS가 범위를 찾는다. */
    fun Hit.toLocator(current: Locator): Locator = current.copy(
        locations = Locator.Locations(
            progression = current.locations.progression,
            otherLocations = mapOf("cssSelector" to cssSelector),
        ),
        text = Locator.Text(highlight = text),
    )

    private fun script(x: Float, y: Float): String = """
        (function() {
          var dpr = window.devicePixelRatio || 1;
          var el = document.elementFromPoint($x / dpr, $y / dpr);
          if (!el) return null;
          if (el.nodeType !== 1) el = el.parentElement;
          var block = el.closest('p, li, blockquote, dd, figcaption, h1, h2, h3, h4, h5, h6');
          if (!block) return null;
          var text = block.textContent;
          if (!text || !text.trim()) return null;

          function cssPath(node) {
            if (node.id) return '#' + CSS.escape(node.id);
            var parts = [];
            while (node && node.nodeType === 1 && node !== document.body && node !== document.documentElement) {
              if (node.id) { parts.unshift('#' + CSS.escape(node.id)); return parts.join(' > '); }
              var i = 1, s = node;
              while ((s = s.previousElementSibling)) { if (s.tagName === node.tagName) i++; }
              parts.unshift(node.tagName.toLowerCase() + ':nth-of-type(' + i + ')');
              node = node.parentElement;
            }
            parts.unshift('body');
            return parts.join(' > ');
          }

          // 페이지(컬럼) 모드에서 문단이 여러 페이지에 걸칠 수 있으므로 화면에 보이는 조각만 사용
          var vw = window.innerWidth, vh = window.innerHeight;
          var rects = Array.prototype.filter.call(block.getClientRects(), function(r) {
            return r.width > 0 && r.height > 0 && r.right > 0 && r.left < vw && r.bottom > 0 && r.top < vh;
          });
          if (rects.length === 0) rects = [block.getBoundingClientRect()];
          var top = Infinity, bottom = -Infinity;
          rects.forEach(function(r) { top = Math.min(top, r.top); bottom = Math.max(bottom, r.bottom); });

          return { cssSelector: cssPath(block), text: text, top: top * dpr, bottom: bottom * dpr };
        })();
    """.trimIndent()
}
