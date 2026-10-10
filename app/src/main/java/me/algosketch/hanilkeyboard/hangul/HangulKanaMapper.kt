package me.algosketch.hanilkeyboard.hangul

/**
 * 한글 → 히라가나 변환 결과.
 * [unitEnds][i] 는 i번째 입력 단위까지 변환했을 때의 [kana] 길이(경계)이다.
 */
class KanaResult(val kana: String, val unitEnds: IntArray) {
    /** 독음 길이 [length] 가 정확히 단위 경계에 맞으면 소비하는 단위 수를, 아니면 -1 을 반환. */
    fun unitsFor(length: Int): Int {
        if (length == 0) return 0
        val i = unitEnds.indexOfFirst { it == length }
        return if (i < 0) -1 else i + 1
    }
}

/**
 * 한글 → 히라가나 매핑. 규칙 전체는 docs/CONVERSION_RULES.md 참고.
 *
 * 각 단위의 변환은 "자기 자신 + 바로 다음 단위의 초성"에만 의존한다.
 * 따라서 버퍼 전체를 매번 다시 매핑하면 입력이 이어질 때 앞 음절의 결과가 자연스럽게 바뀌고
 * (각 → がく, 각코 → がっこ), 앞쪽 단위를 잘라내도 남은 단위의 결과는 그대로 유지된다.
 */
object HangulKanaMapper {

    fun map(units: List<InputUnit>): KanaResult {
        val sb = StringBuilder()
        val ends = IntArray(units.size)
        units.forEachIndexed { i, unit ->
            when (unit) {
                is Literal -> sb.append(unit.char)
                is Syllable -> {
                    val next = (units.getOrNull(i + 1) as? Syllable)?.cho
                    sb.append(syllable(unit, next))
                }
            }
            ends[i] = sb.length
        }
        return KanaResult(sb.toString(), ends)
    }

    fun map(text: String): String = map(HangulComposer.compose(HangulKeys.of(text))).kana

    private fun syllable(s: Syllable, nextCho: Char?): String {
        val cho = s.cho
        val jung = s.jung
        val head = when {
            jung != null -> body(cho, jung)
            cho != null -> lone(cho)
            else -> ""
        }
        val jong = s.jong ?: return head
        val split = Jamo.splitJong(jong)
        return if (split != null) {
            head + finalConsonant(split.first, split.second) + lone(split.second)
        } else {
            head + finalConsonant(jong, nextCho)
        }
    }

    // ---- 초성 + 중성 ----

    private enum class Row(val a: String, val i: String, val u: String, val e: String, val o: String) {
        VOWEL("あ", "い", "う", "え", "お"),
        K("か", "き", "く", "け", "こ"),
        G("が", "ぎ", "ぐ", "げ", "ご"),
        S("さ", "し", "す", "せ", "そ"),
        Z("ざ", "じ", "ず", "ぜ", "ぞ"),
        T("た", "ち", "つ", "て", "と"),
        D("だ", "ぢ", "づ", "で", "ど"),
        N("な", "に", "ぬ", "ね", "の"),
        H("は", "ひ", "ふ", "へ", "ほ"),
        B("ば", "び", "ぶ", "べ", "ぼ"),
        P("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"),
        M("ま", "み", "む", "め", "も"),
        R("ら", "り", "る", "れ", "ろ"),
        CH("ちゃ", "ち", "ちゅ", "ちぇ", "ちょ"),
    }

    private fun rowOf(cho: Char?): Row = when (cho) {
        null, 'ㅇ' -> Row.VOWEL
        'ㄱ' -> Row.G
        'ㄲ', 'ㅋ' -> Row.K
        'ㄴ' -> Row.N
        'ㄷ' -> Row.D
        'ㄸ', 'ㅌ' -> Row.T
        'ㄹ' -> Row.R
        'ㅁ' -> Row.M
        'ㅂ' -> Row.B
        'ㅃ', 'ㅍ' -> Row.P
        'ㅅ', 'ㅆ' -> Row.S
        'ㅈ' -> Row.Z
        'ㅊ', 'ㅉ' -> Row.CH
        'ㅎ' -> Row.H
        else -> Row.VOWEL
    }

    private fun body(cho: Char?, jung: Char): String {
        val row = rowOf(cho)
        val vowelOnly = row == Row.VOWEL
        return when (jung) {
            'ㅏ' -> row.a
            'ㅣ' -> row.i
            'ㅜ' -> row.u
            'ㅐ', 'ㅔ' -> row.e
            'ㅓ', 'ㅗ' -> row.o
            'ㅑ' -> if (vowelOnly) "や" else yoon(row, "ゃ")
            'ㅠ' -> if (vowelOnly) "ゆ" else yoon(row, "ゅ")
            'ㅕ', 'ㅛ' -> if (vowelOnly) "よ" else yoon(row, "ょ")
            'ㅒ', 'ㅖ' -> when (row) {
                Row.VOWEL -> "いぇ"
                Row.S, Row.Z -> row.i + "ぇ"
                else -> row.e
            }
            'ㅘ' -> if (vowelOnly) "わ" else row.u + "ぁ"
            'ㅝ' -> if (vowelOnly) "を" else row.u + "ぉ"
            'ㅙ', 'ㅚ', 'ㅞ' -> if (vowelOnly) "うぇ" else row.u + "ぇ"
            'ㅟ' -> if (vowelOnly) "うぃ" else row.u + "ぃ"
            'ㅡ' -> eu(cho)
            'ㅢ' -> when (cho) {
                'ㅌ', 'ㄸ' -> "てぃ"
                'ㄷ' -> "でぃ"
                null, 'ㅇ' -> "うい"
                else -> row.i
            }
            else -> ""
        }
    }

    /** 요음: CH 행은 자체 표기, 나머지는 い단 + 작은 ゃゅょ. */
    private fun yoon(row: Row, small: String): String = when (row) {
        Row.CH -> when (small) {
            "ゃ" -> "ちゃ"
            "ゅ" -> "ちゅ"
            else -> "ちょ"
        }
        else -> row.i + small
    }

    /** ㅡ 모음: 일본어에 없으므로 う단으로, 단 ㅆ/ㅊ/ㅉ는 つ, ㄷ/ㅌ/ㄸ는 お단. */
    private fun eu(cho: Char?): String = when (cho) {
        'ㅆ', 'ㅊ', 'ㅉ' -> "つ"
        'ㅌ', 'ㄸ' -> "と"
        'ㄷ' -> "ど"
        else -> rowOf(cho).u
    }

    /** 모음 없이 자음만 있는 단위. */
    private fun lone(cho: Char): String = when (cho) {
        'ㄴ', 'ㅇ' -> "ん"
        else -> eu(cho)
    }

    // ---- 받침 ----

    private fun finalConsonant(jong: Char, nextCho: Char?): String = when (jong) {
        'ㄱ', 'ㄲ', 'ㅋ' -> if (nextCho == 'ㄱ' || nextCho == 'ㄲ' || nextCho == 'ㅋ') "っ" else "く"
        'ㅂ', 'ㅍ' -> if (nextCho == 'ㅂ' || nextCho == 'ㅃ' || nextCho == 'ㅍ') "っ" else "ぷ"
        'ㄷ', 'ㅅ', 'ㅆ', 'ㅈ', 'ㅊ', 'ㅌ', 'ㅎ' -> "っ"
        'ㄴ', 'ㅇ' -> "ん"
        'ㅁ' -> if (nextCho != null && nextCho != 'ㅇ') "ん" else "む"
        'ㄹ' -> "る"
        else -> ""
    }
}
