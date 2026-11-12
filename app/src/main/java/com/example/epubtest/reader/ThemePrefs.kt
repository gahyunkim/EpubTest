package com.example.epubtest.reader

import android.content.Context
import androidx.core.content.edit

/** 다크/라이트 선택을 저장한다. Readium 리더와 커스텀 리더가 같은 값을 공유한다. */
object ThemePrefs {
    private const val FILE = "reader_theme"
    private const val KEY_DARK = "dark"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DARK, true)

    fun setDark(context: Context, dark: Boolean) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY_DARK, dark) }
}
