package me.algosketch.hanilkeyboard.ime

import me.algosketch.hanilkeyboard.kana.KanaColumn
import me.algosketch.hanilkeyboard.kana.KanaRow
import me.algosketch.hanilkeyboard.kana.Voicing

sealed interface KeyAction {
    /** 한글 자모. 시프트 시 [shifted] 입력. */
    data class Jamo(val normal: Char, val shifted: Char = normal) : KeyAction {
        /** 시프트 문자가 따로 있어 아래로 드래그해 입력할 수 있는 키인지. (ㅂ→ㅃ, ㅐ→ㅒ 등) */
        val supportsDragShift: Boolean get() = shifted != normal

        /** 아래로 [dy] 만큼 드래그하고 뗐을 때 입력할 키. [thresholdPx] 이상이면 시프트 문자. */
        fun resolveDrag(dy: Float, thresholdPx: Float): Jamo =
            if (supportsDragShift && dy >= thresholdPx) Jamo(shifted) else this
    }

    /** 독음 버퍼에 들어가는 문자(장음 ー). */
    data class Reading(val char: Char) : KeyAction

    /** 바로 입력되는 문자(구두점·기호·숫자). 위로 드래그하면 [up] 을 입력한다. */
    data class Text(val text: String, val up: String? = null) : KeyAction

    /** 가나 자판 왼쪽(행) 키. 왼쪽 드래그 탁음, 오른쪽 드래그 반탁음, 위 드래그 숫자 [digit]. */
    data class KanaRowKey(val row: KanaRow, val digit: Char, val voicing: Voicing = Voicing.PLAIN) : KeyAction

    /** 가나 자판 오른쪽(단) 키. */
    data class KanaColumnKey(val column: KanaColumn) : KeyAction

    /** 小: 직전 가나를 작은 가나로(작은 가나면 큰 가나로) 바꾼다. */
    data object Small : KeyAction

    /** 연속으로 눌러 [options] 를 차례로 바꾸는 키 (예: 、→。). */
    data class MultiTap(val options: List<String>) : KeyAction

    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object Shift : KeyAction
    data object SymbolMode : KeyAction
    data object LetterMode : KeyAction
    data object SymbolPage : KeyAction

    /** 키가 없는 빈 공간(행 들여쓰기용). */
    data object Spacer : KeyAction
}

/**
 * 자판의 키 하나. [weight] 는 한 줄(10칸) 안에서의 가로 칸 수.
 * [wideGapStart]/[wideGapEnd] 이면 그쪽 옆 키와의 간격을 기본의 두 배로 한다(키 자신의 폭에서 줄인다).
 */
data class Key(
    val action: KeyAction,
    val weight: Float = 1f,
    val wideGapStart: Boolean = false,
    val wideGapEnd: Boolean = false,
)

/** 드래그 방향에 따라 입력할 키가 바뀌는 키인지. */
val KeyAction.supportsDrag: Boolean
    get() = when (this) {
        is KeyAction.Jamo -> supportsDragShift
        is KeyAction.KanaRowKey -> true
        is KeyAction.Text -> up != null
        else -> false
    }

/**
 * ([dx], [dy]) 만큼 드래그하고 뗐을 때 입력할 키. 이동이 큰 축의 방향이 [thresholdPx] 이상일 때만 바뀐다.
 * 한글 자모는 아래, 가나 행 키는 왼쪽(탁음)·오른쪽(반탁음)·위(숫자), 기호 키는 위.
 */
fun KeyAction.dragTarget(dx: Float, dy: Float, thresholdPx: Float): KeyAction {
    val horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy)
    val up = !horizontal && dy <= -thresholdPx
    return when (this) {
        is KeyAction.Jamo -> resolveDrag(dy, thresholdPx)
        is KeyAction.KanaRowKey -> {
            val voicing = when {
                up -> return KeyAction.Text(digit.toString())
                horizontal && dx <= -thresholdPx -> Voicing.DAKUTEN
                horizontal && dx >= thresholdPx -> Voicing.HANDAKUTEN
                else -> Voicing.PLAIN
            }
            if (row.supports(voicing)) copy(voicing = voicing) else this
        }
        is KeyAction.Text -> if (up && this.up != null) KeyAction.Text(this.up) else this
        else -> this
    }
}

enum class KeyboardMode { LETTER, SYMBOL }

/** 글자 자판 종류. 앱 화면에서 고른다. */
enum class KeyboardLayoutType { HANGUL_QWERTY, KANA_PAD }

/**
 * 자판 배열. 삼성 키보드 일본어(쿼티) 자판의 구성(、。ー 키, 기호 전환)을 따르되
 * 글자 키는 두벌식 한글 배열을 사용한다.
 */
object KeyboardLayouts {

    /** 쉼표·마침표 키: 한 번 누르면 、, 이어서 한 번 더 누르면 。 */
    val PUNCTUATION = KeyAction.MultiTap(listOf("、", "。"))

    private fun jamoRow(normal: String, shifted: String = normal) =
        normal.indices.map { Key(KeyAction.Jamo(normal[it], shifted[it])) }

    private fun textRow(chars: String) = chars.map { Key(KeyAction.Text(it.toString())) }

    private fun bottomRow(modeKey: KeyAction) = listOf(
        Key(modeKey, 1.5f),
        Key(KeyAction.Space, 5f),
        Key(KeyAction.Reading('ー')),
        Key(PUNCTUATION),
        Key(KeyAction.Enter, 1.5f),
    )

    val letter: List<List<Key>> = listOf(
        textRow("1234567890"),
        jamoRow("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ"),
        listOf(Key(KeyAction.Spacer, 0.5f)) + jamoRow("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ") + Key(KeyAction.Spacer, 0.5f),
        // ㅋ 은 ㄴ 의 시작, ㅡ 는 ㅏ 의 끝에 맞춘다 (⇧·⌫ 1.5칸, 옆 키와 간격 두 배)
        listOf(Key(KeyAction.Shift, 1.5f, wideGapEnd = true)) + jamoRow("ㅋㅌㅊㅍㅠㅜㅡ") +
            Key(KeyAction.Backspace, 1.5f, wideGapStart = true),
        bottomRow(KeyAction.SymbolMode),
    )

    /**
     * 행·단 분리 가나 자판. 왼쪽 3×4 는 행(かさたなはまらわん), 오른쪽 3×4 는 단(あいうえお)과 やゆよ.
     * 숫자는 왼쪽 키를 위로 드래그해 입력한다 (전화 키패드 배치: か=1 … ん=9, ～=0).
     */
    val kanaPad: List<List<Key>> = listOf(
        padRow(rowKey(KanaRow.K, '1'), rowKey(KanaRow.S, '2'), rowKey(KanaRow.T, '3'), columnKey(KanaColumn.A), columnKey(KanaColumn.I), columnKey(KanaColumn.U)),
        padRow(rowKey(KanaRow.N, '4'), rowKey(KanaRow.H, '5'), rowKey(KanaRow.M, '6'), columnKey(KanaColumn.E), columnKey(KanaColumn.O), columnKey(KanaColumn.YA)),
        padRow(rowKey(KanaRow.R, '7'), rowKey(KanaRow.W, '8'), rowKey(KanaRow.NN, '9'), columnKey(KanaColumn.YU), columnKey(KanaColumn.YO), KeyAction.Reading('っ')),
        padRow(KeyAction.Text("？"), KeyAction.Text("～", up = "0"), KeyAction.Text("！"), PUNCTUATION, KeyAction.Reading('ー'), KeyAction.Backspace),
        listOf(
            Key(KeyAction.SymbolMode, 1.2f),
            Key(KeyAction.Space, 2.6f),
            Key(KeyAction.Small),
            Key(KeyAction.Enter, 1.2f),
        ),
    )

    private fun rowKey(row: KanaRow, digit: Char) = KeyAction.KanaRowKey(row, digit)
    private fun columnKey(column: KanaColumn) = KeyAction.KanaColumnKey(column)

    /** 왼쪽 3키 + 오른쪽 3키. 두 묶음 사이는 간격을 두 배로 둔다. */
    private fun padRow(vararg actions: KeyAction) = actions.mapIndexed { i, action ->
        Key(action, wideGapEnd = i == 2, wideGapStart = i == 3)
    }

    fun letter(type: KeyboardLayoutType): List<List<Key>> = when (type) {
        KeyboardLayoutType.HANGUL_QWERTY -> letter
        KeyboardLayoutType.KANA_PAD -> kanaPad
    }

    val symbolPages: List<List<List<Key>>> = listOf(
        listOf(
            textRow("1234567890"),
            textRow("！？「」『』（）・～"),
            listOf(Key(KeyAction.SymbolPage, 1.5f)) +
                listOf(Key(PUNCTUATION)) + textRow("…：；＠＃※") + Key(KeyAction.Backspace, 1.5f),
            bottomRow(KeyAction.LetterMode),
        ),
        listOf(
            textRow("@#$%&*-+=/"),
            textRow("()[]<>\"'^_"),
            listOf(Key(KeyAction.SymbolPage, 1.5f)) + textRow(",.!?:;~") + Key(KeyAction.Backspace, 1.5f),
            bottomRow(KeyAction.LetterMode),
        ),
    )
}
