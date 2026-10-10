package me.algosketch.hanilkeyboard.hangul

/** 완성형 한글 문자열을 두벌식 키 입력 시퀀스로 분해한다. (테스트·디버깅용) */
object HangulKeys {
    private val jungKeys = mapOf(
        'ㅘ' to "ㅗㅏ", 'ㅙ' to "ㅗㅐ", 'ㅚ' to "ㅗㅣ",
        'ㅝ' to "ㅜㅓ", 'ㅞ' to "ㅜㅔ", 'ㅟ' to "ㅜㅣ", 'ㅢ' to "ㅡㅣ",
    )

    fun of(text: String): List<Char> = buildList {
        for (ch in text) {
            if (ch in '가'..'힣') {
                val idx = ch - '가'
                add(Jamo.CHO[idx / (21 * 28)])
                val jung = Jamo.JUNG[(idx / 28) % 21]
                jungKeys[jung]?.forEach { add(it) } ?: add(jung)
                val jongIdx = idx % 28
                if (jongIdx != 0) {
                    val jong = Jamo.JONG[jongIdx]
                    val split = Jamo.splitJong(jong)
                    if (split != null) {
                        add(split.first); add(split.second)
                    } else {
                        add(jong)
                    }
                }
            } else {
                jungKeys[ch]?.forEach { add(it) } ?: add(ch)
            }
        }
    }
}
