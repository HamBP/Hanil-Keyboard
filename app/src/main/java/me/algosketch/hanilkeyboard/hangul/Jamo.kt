package me.algosketch.hanilkeyboard.hangul

/**
 * 한글 호환 자모(U+3131~) 기반 테이블.
 * 키보드는 호환 자모 문자를 그대로 입력 키로 사용한다.
 */
object Jamo {
    const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"

    /** 인덱스 0은 받침 없음. */
    const val JONG = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val compoundJung = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ',
        "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ',
        "ㅡㅣ" to 'ㅢ',
    )

    private val compoundJong = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ',
        "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ',
        "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
    )
    private val splitJong = compoundJong.entries.associate { (k, v) -> v to (k[0] to k[1]) }

    fun isConsonant(c: Char) = c in CHO
    fun isVowel(c: Char) = c in JUNG
    fun isJamo(c: Char) = isConsonant(c) || isVowel(c)

    /** 받침이 될 수 있는 단일 자음인지 (ㄸ ㅃ ㅉ 는 받침 불가). */
    fun canBeJong(c: Char) = isConsonant(c) && c in JONG

    fun combineJung(first: Char, second: Char): Char? = compoundJung["$first$second"]
    fun combineJong(first: Char, second: Char): Char? = compoundJong["$first$second"]

    /** 겹받침이면 (앞 자음, 뒤 자음)을, 아니면 null 을 반환한다. */
    fun splitJong(jong: Char): Pair<Char, Char>? = splitJong[jong]

    fun syllable(cho: Char, jung: Char, jong: Char?): Char {
        val c = CHO.indexOf(cho)
        val v = JUNG.indexOf(jung)
        val t = if (jong == null) 0 else JONG.indexOf(jong)
        return (0xAC00 + (c * 21 + v) * 28 + t).toChar()
    }
}
