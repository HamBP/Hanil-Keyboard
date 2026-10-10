package me.algosketch.hanilkeyboard.hangul

/**
 * 입력 버퍼를 이루는 단위. 한글 음절(조합 중인 것 포함) 또는 리터럴 문자(장음 기호 ー 등).
 * [keyCount] 는 이 단위를 만드는 데 쓰인 입력 키 수로, 후보를 부분 확정할 때
 * 앞쪽 단위에 해당하는 키만 버퍼에서 제거하는 데 사용한다.
 */
sealed interface InputUnit {
    val keyCount: Int
    val text: String
}

data class Syllable(
    val cho: Char?,
    val jung: Char?,
    val jong: Char?,
    override val keyCount: Int,
) : InputUnit {
    override val text: String
        get() = when {
            cho != null && jung != null -> Jamo.syllable(cho, jung, jong).toString()
            cho != null -> cho.toString()
            jung != null -> jung.toString()
            else -> ""
        }
}

data class Literal(val char: Char) : InputUnit {
    override val keyCount: Int get() = 1
    override val text: String get() = char.toString()
}

/**
 * 두벌식 오토마타. 입력 키 시퀀스 전체로부터 음절 단위를 계산하는 순수 함수이다.
 *
 * 키 시퀀스를 원본 상태로 두고 매번 전체를 다시 조합하므로
 * - 백스페이스 = 마지막 키 제거
 * - 부분 확정 = 앞쪽 키 제거
 * 가 모두 단순한 리스트 연산이 된다.
 */
object HangulComposer {

    private class Building(var cho: Char?, var jung: Char?, var jong: Char?, var keys: Int)

    fun compose(keys: List<Char>): List<InputUnit> {
        val out = ArrayList<Any>() // Building 또는 Literal
        for (k in keys) {
            val last = out.lastOrNull() as? Building
            when {
                Jamo.isConsonant(k) -> {
                    if (last != null && last.cho != null && last.jung != null) {
                        val jong = last.jong
                        if (jong == null && Jamo.canBeJong(k)) {
                            last.jong = k; last.keys++; continue
                        }
                        val combined = jong?.let { Jamo.combineJong(it, k) }
                        if (combined != null) {
                            last.jong = combined; last.keys++; continue
                        }
                    }
                    out += Building(k, null, null, 1)
                }

                Jamo.isVowel(k) -> {
                    if (last != null) {
                        val jung = last.jung
                        val jong = last.jong
                        if (last.cho != null && jung == null) {
                            last.jung = k; last.keys++; continue
                        }
                        if (jung != null && jong == null) {
                            val combined = Jamo.combineJung(jung, k)
                            if (combined != null) {
                                last.jung = combined; last.keys++; continue
                            }
                        }
                        if (jung != null && jong != null) {
                            // 받침이 다음 음절의 초성으로 이동한다. (각 + ㅗ → 가고, 닭 + ㅏ → 달가)
                            val split = Jamo.splitJong(jong)
                            val moved: Char
                            if (split != null) {
                                last.jong = split.first; moved = split.second
                            } else {
                                last.jong = null; moved = jong
                            }
                            last.keys--
                            out += Building(moved, k, null, 2)
                            continue
                        }
                    }
                    out += Building(null, k, null, 1)
                }

                else -> out += Literal(k)
            }
        }
        return out.map {
            if (it is Building) Syllable(it.cho, it.jung, it.jong, it.keys) else it as Literal
        }
    }

    fun text(units: List<InputUnit>): String = units.joinToString("") { it.text }
}
