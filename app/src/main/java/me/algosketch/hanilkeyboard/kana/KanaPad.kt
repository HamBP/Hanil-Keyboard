package me.algosketch.hanilkeyboard.kana

/** 탁음 표시. 왼쪽 자판을 왼쪽으로 드래그하면 탁음, 오른쪽으로 드래그하면 반탁음. */
enum class Voicing { PLAIN, DAKUTEN, HANDAKUTEN }

/** 오른쪽 자판: 단(あいうえお)과 요음(やゆよ). */
enum class KanaColumn(val alone: String, val small: String?) {
    A("あ", null), I("い", null), U("う", null), E("え", null), O("お", null),
    YA("や", "ゃ"), YU("ゆ", "ゅ"), YO("よ", "ょ");

    val isVowel: Boolean get() = small == null
}

/**
 * 왼쪽 자판: 행. 각 문자열은 あいうえお 단 순서의 가나 5개.
 * [modifiable] 가 false 인 행(ん)은 오른쪽 자판으로 단을 바꿀 수 없다.
 */
enum class KanaRow(
    private val plain: List<String>,
    private val dakuten: List<String>? = null,
    private val handakuten: List<String>? = null,
) {
    K(columns("かきくけこ"), columns("がぎぐげご")),
    S(columns("さしすせそ"), columns("ざじずぜぞ")),
    T(columns("たちつてと"), columns("だぢづでど")),
    N(columns("なにぬねの")),
    H(columns("はひふへほ"), columns("ばびぶべぼ"), columns("ぱぴぷぺぽ")),
    M(columns("まみむめも")),
    R(columns("らりるれろ")),
    W(listOf("わ", "うぃ", "う", "うぇ", "を")),
    NN(listOf("ん", "ん", "ん", "ん", "ん"));

    val modifiable: Boolean get() = this != NN

    /** 이 행이 [voicing] 을 지원하는지. 지원하지 않으면 청음으로 입력한다. */
    fun supports(voicing: Voicing): Boolean = when (voicing) {
        Voicing.PLAIN -> true
        Voicing.DAKUTEN -> dakuten != null
        Voicing.HANDAKUTEN -> handakuten != null
    }

    fun kana(voicing: Voicing, column: KanaColumn = KanaColumn.A): String {
        val table = when (voicing) {
            Voicing.PLAIN -> plain
            Voicing.DAKUTEN -> dakuten
            Voicing.HANDAKUTEN -> handakuten
        } ?: plain
        return when {
            column.isVowel -> table[column.ordinal]
            // 요음: い단 + 작은 ゃゅょ (きゃ, じゅ, ぴょ …)
            else -> table[KanaColumn.I.ordinal] + column.small
        }
    }
}

private fun columns(chars: String): List<String> = chars.map { it.toString() }

/**
 * 행·단 분리 가나 자판의 입력 규칙(순수 로직).
 *
 * - 왼쪽(행) 키를 누르면 그 행의 あ단 가나가 입력되고, 단을 바꿀 수 있는 대기 상태가 된다.
 * - 대기 중에 오른쪽(단) 키를 누르면 직전 가나의 단을 바꾼다 (か + い → き, か + や → きゃ).
 * - 대기 중이 아니면 오른쪽 키는 그 문자(あいうえお・やゆよ)를 그대로 입력한다.
 * - 다른 키가 입력되면 [reset] 으로 대기를 끝낸다.
 */
class KanaPadComposer {

    sealed interface Result {
        /** [text] 를 독음 버퍼 끝에 추가한다. */
        data class Insert(val text: String) : Result

        /** 독음 버퍼의 마지막 문자를 [text] 로 바꾼다. */
        data class ReplaceLast(val text: String) : Result
    }

    private var pendingRow: KanaRow? = null
    private var pendingVoicing = Voicing.PLAIN

    fun pressRow(row: KanaRow, voicing: Voicing = Voicing.PLAIN): Result {
        val v = if (row.supports(voicing)) voicing else Voicing.PLAIN
        pendingRow = row.takeIf { it.modifiable }
        pendingVoicing = v
        return Result.Insert(row.kana(v))
    }

    fun pressColumn(column: KanaColumn): Result {
        val row = pendingRow
        pendingRow = null
        if (row == null || (row == KanaRow.W && !column.isVowel)) return Result.Insert(column.alone)
        return Result.ReplaceLast(row.kana(pendingVoicing, column))
    }

    fun reset() {
        pendingRow = null
    }

    companion object {
        private const val LARGE = "あいうえおつやゆよわ"
        private const val SMALL = "ぁぃぅぇぉっゃゅょゎ"

        /** 小 키: 큰 가나 ↔ 작은 가나 전환. 작은 형태가 없으면 null. */
        fun toggleSmall(c: Char): Char? {
            LARGE.indexOf(c).takeIf { it >= 0 }?.let { return SMALL[it] }
            SMALL.indexOf(c).takeIf { it >= 0 }?.let { return LARGE[it] }
            return null
        }
    }
}
