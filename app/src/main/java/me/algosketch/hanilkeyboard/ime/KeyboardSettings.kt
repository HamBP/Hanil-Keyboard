package me.algosketch.hanilkeyboard.ime

import android.content.Context

/** 앱 화면에서 바꾸는 키보드 설정. 입력 내용은 저장하지 않는다. */
object KeyboardSettings {
    private const val PREFS = "keyboard_settings"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_HANJA_GLOSS = "hanja_gloss"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun layout(context: Context): KeyboardLayoutType =
        prefs(context).getString(KEY_LAYOUT, null)
            ?.let { name -> KeyboardLayoutType.entries.firstOrNull { it.name == name } }
            ?: KeyboardLayoutType.HANGUL_QWERTY

    fun setLayout(context: Context, type: KeyboardLayoutType) {
        prefs(context).edit().putString(KEY_LAYOUT, type.name).apply()
    }

    /** 한자 해설: 포커스된 후보의 한자마다 한국어 뜻과 음을 보여준다. */
    fun hanjaGloss(context: Context): Boolean = prefs(context).getBoolean(KEY_HANJA_GLOSS, true)

    fun setHanjaGloss(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HANJA_GLOSS, enabled).apply()
    }
}
