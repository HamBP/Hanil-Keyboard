package me.algosketch.hanilkeyboard.conversion

fun interface KatakanaConverter {
    fun toKatakana(hiragana: String): String
}

/** 히라가나 → 가타카나. Android 내장 ICU(Unicode License)의 Transliterator 를 사용한다. */
class IcuKatakanaConverter : KatakanaConverter {
    private val transliterator = runCatching {
        android.icu.text.Transliterator.getInstance("Hiragana-Katakana")
    }.getOrNull()

    override fun toKatakana(hiragana: String): String =
        transliterator?.transliterate(hiragana) ?: SimpleKatakanaConverter.toKatakana(hiragana)
}

/** ICU 를 쓸 수 없는 환경(JVM 단위 테스트 등)을 위한 대체 구현. 히라가나 블록을 가타카나 블록으로 옮긴다. */
object SimpleKatakanaConverter : KatakanaConverter {
    override fun toKatakana(hiragana: String): String = buildString(hiragana.length) {
        for (c in hiragana) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }
}
