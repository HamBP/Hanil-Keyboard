package me.algosketch.hanilkeyboard.ime

sealed interface KeyAction {
    /** 한글 자모. 시프트 시 [shifted] 입력. */
    data class Jamo(val normal: Char, val shifted: Char = normal) : KeyAction

    /** 독음 버퍼에 들어가는 문자(장음 ー). */
    data class Reading(val char: Char) : KeyAction

    /** 바로 입력되는 문자(구두점·기호·숫자). */
    data class Text(val text: String) : KeyAction

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

data class Key(val action: KeyAction, val weight: Float = 1f)

enum class KeyboardMode { LETTER, SYMBOL }

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
        Key(PUNCTUATION),
        Key(KeyAction.Space, 5f),
        Key(KeyAction.Reading('ー')),
        Key(KeyAction.Enter, 1.5f),
    )

    val letter: List<List<Key>> = listOf(
        textRow("1234567890"),
        jamoRow("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ"),
        listOf(Key(KeyAction.Spacer, 0.5f)) + jamoRow("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ") + Key(KeyAction.Spacer, 0.5f),
        listOf(Key(KeyAction.Shift, 1.5f)) + jamoRow("ㅋㅌㅊㅍㅠㅜㅡ") + Key(KeyAction.Backspace, 1.5f),
        bottomRow(KeyAction.SymbolMode),
    )

    val symbolPages: List<List<List<Key>>> = listOf(
        listOf(
            textRow("1234567890"),
            textRow("！？「」『』（）・～"),
            listOf(Key(KeyAction.SymbolPage, 1.5f)) +
                textRow("。…：；＠＃※") + Key(KeyAction.Backspace, 1.5f),
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
