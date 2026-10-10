package me.algosketch.hanilkeyboard.engine

import me.algosketch.hanilkeyboard.conversion.DictEntry
import me.algosketch.hanilkeyboard.conversion.Dictionary
import me.algosketch.hanilkeyboard.conversion.Connection
import me.algosketch.hanilkeyboard.conversion.KanaKanjiConverter
import me.algosketch.hanilkeyboard.conversion.LanguageModel
import me.algosketch.hanilkeyboard.conversion.SimpleKatakanaConverter
import me.algosketch.hanilkeyboard.hangul.HangulKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeEngineTest {

    private val fakeDict = object : Dictionary {
        val words = mapOf(
            "がっこう" to listOf("学校"),
            "ざっし" to listOf("雑誌"),
        )
        override val maxReadingLength = 8
        override fun lookup(reading: String) =
            words[reading].orEmpty().map { DictEntry(it, 1000) }
    }
    private val converter = KanaKanjiConverter({ LanguageModel(fakeDict, Connection.NONE) }, SimpleKatakanaConverter)
    private val engine = ImeEngine(converter::convert)

    private var state: ImeState = ImeState.Idle
    private val committed = StringBuilder()
    private val effects = mutableListOf<ImeEffect>()

    private fun send(event: ImeEvent) {
        val t = engine.reduce(state, event)
        state = t.state
        t.effects.forEach {
            effects += it
            if (it is ImeEffect.Commit) committed.append(it.text)
        }
    }

    private fun type(hangul: String) = HangulKeys.of(hangul).forEach { send(ImeEvent.Key(it)) }

    @Test fun composingShowsHangulAndHiraganaCandidate() {
        type("각코우")
        assertTrue(state is ImeState.Composing)
        assertEquals("각코우", state.displayText)
        assertEquals("がっこう", state.candidates[0].text)
        assertEquals(0, state.focusedIndex)
        assertEquals("", committed.toString())
    }

    @Test fun enterCommitsHiragana() {
        type("각코우")
        send(ImeEvent.Enter)
        assertEquals("がっこう", committed.toString())
        assertEquals(ImeState.Idle, state)
    }

    @Test fun spaceMovesFocusAndEnterCommitsFocused() {
        type("각코우")
        send(ImeEvent.Space)
        assertTrue(state is ImeState.Converting)
        assertEquals(1, state.focusedIndex)
        assertEquals("学校", state.displayText)
        send(ImeEvent.Space)
        assertEquals("ガッコウ", state.displayText)
        send(ImeEvent.Space) // 마지막 다음은 처음으로 순환
        assertEquals(0, state.focusedIndex)
        send(ImeEvent.Space)
        send(ImeEvent.Enter)
        assertEquals("学校", committed.toString())
        assertEquals(ImeState.Idle, state)
    }

    @Test fun backspaceInConvertingReturnsToComposing() {
        type("각코우")
        send(ImeEvent.Space)
        send(ImeEvent.Backspace)
        assertTrue(state is ImeState.Composing)
        assertEquals("각코우", state.displayText)
        send(ImeEvent.Backspace)
        assertEquals("각콩", state.displayText) // 백스페이스 = 마지막 키 입력 취소
    }

    @Test fun backspaceToEmptyReturnsIdleAndIdleBackspaceDeletes() {
        type("가")
        send(ImeEvent.Backspace)
        send(ImeEvent.Backspace)
        assertEquals(ImeState.Idle, state)
        send(ImeEvent.Backspace)
        assertEquals(ImeEffect.DeleteBackward, effects.last())
    }

    @Test fun typingWhileConvertingCommitsFocused() {
        type("각코우")
        send(ImeEvent.Space)
        type("가")
        assertEquals("学校", committed.toString())
        assertEquals("가", state.displayText)
    }

    @Test fun partialCandidateKeepsRest() {
        type("각코우잣시")
        val index = state.candidates.indexOfFirst { it.text == "学校" }
        assertTrue(index > 0)
        send(ImeEvent.SelectCandidate(index))
        assertEquals("学校", committed.toString())
        assertEquals("잣시", state.displayText)
        assertEquals("ざっし", state.candidates[0].text)
    }

    @Test fun partialCandidatePreviewShowsRemainingHangul() {
        type("각코우잣시")
        val index = state.candidates.indexOfFirst { it.text == "学校" }
        repeat(index) { send(ImeEvent.Space) }
        assertEquals("学校잣시", state.displayText)
    }

    @Test fun punctuationCommitsComposition() {
        type("각코우")
        send(ImeEvent.Text("。"))
        assertEquals("がっこう。", committed.toString())
        assertEquals(ImeState.Idle, state)
    }

    @Test fun idleSpaceAndEnter() {
        send(ImeEvent.Space)
        assertEquals(" ", committed.toString())
        send(ImeEvent.Enter)
        assertEquals(ImeEffect.EditorEnter, effects.last())
    }

    @Test fun replaceLastEditsBufferEnd() {
        "がっこ".forEach { send(ImeEvent.Key(it)) }
        send(ImeEvent.Key('か'))
        send(ImeEvent.ReplaceLast("う"))
        assertEquals("がっこう", state.displayText)
        assertTrue(state.candidates.any { it.text == "学校" })
        send(ImeEvent.ReplaceLast("きゃ"))
        assertEquals("がっこきゃ", state.displayText)
    }

    @Test fun replaceLastWhenIdleInserts() {
        send(ImeEvent.ReplaceLast("き"))
        assertEquals("き", state.displayText)
    }

    @Test fun replaceLastWhileConvertingCommitsFocusedFirst() {
        type("각코우")
        send(ImeEvent.Space)
        send(ImeEvent.ReplaceLast("き"))
        assertEquals("学校", committed.toString())
        assertEquals("き", state.displayText)
    }

    @Test fun flushCommitsFocusedAndRest() {
        type("각코우잣시")
        val index = state.candidates.indexOfFirst { it.text == "学校" }
        repeat(index) { send(ImeEvent.Space) }
        send(ImeEvent.Flush)
        assertEquals("学校ざっし", committed.toString())
    }
}
