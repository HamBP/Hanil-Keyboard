package me.algosketch.hanilkeyboard.engine

import me.algosketch.hanilkeyboard.conversion.Candidate
import me.algosketch.hanilkeyboard.hangul.HangulComposer
import me.algosketch.hanilkeyboard.hangul.HangulKanaMapper
import me.algosketch.hanilkeyboard.hangul.InputUnit
import me.algosketch.hanilkeyboard.hangul.KanaResult

/**
 * 입력 버퍼의 스냅샷. 키 시퀀스([keys])가 유일한 원본이며 나머지는 모두 파생값이다.
 *
 *   keys ──compose──▶ units(한글) ──map──▶ kana(히라가나) ──convert──▶ candidates
 */
class Composition(
    val keys: List<Char>,
    val units: List<InputUnit>,
    val kana: KanaResult,
    val candidates: List<Candidate>,
) {
    val hangul: String get() = HangulComposer.text(units)

    /** 후보 [index] 가 소비하는 입력 단위 수. */
    fun consumedUnits(index: Int): Int {
        val c = candidates[index]
        if (c.readingLength >= kana.kana.length) return units.size
        return kana.unitsFor(c.readingLength).takeIf { it >= 0 } ?: units.size
    }

    /** 후보 [index] 를 확정했을 때 버퍼에 남는 키. */
    fun remainingKeys(index: Int): List<Char> =
        keys.drop(units.take(consumedUnits(index)).sumOf { it.keyCount })

    /** 후보 [index] 를 화면에 보여줄 때의 조합 문자열: 후보 + 남은 한글. */
    fun preview(index: Int): String =
        candidates[index].text + HangulComposer.text(units.drop(consumedUnits(index)))

    /** 후보 [index] 와 나머지 입력(히라가나)을 한 번에 확정할 때의 문자열. */
    fun commitAllText(index: Int): String =
        candidates[index].text + kana.kana.substring(candidates[index].readingLength.coerceAtMost(kana.kana.length))
}

sealed interface ImeState {
    /** 조합 중인 입력이 없음. */
    data object Idle : ImeState

    /** 한글 입력 중. 에디터에는 한글이, 후보 바에는 히라가나(0번, 포커스)부터 후보가 표시된다. */
    data class Composing(val composition: Composition) : ImeState

    /** 스페이스로 후보를 고르는 중. 에디터에는 포커스된 후보(+ 남은 한글)가 표시된다. */
    data class Converting(val composition: Composition, val focus: Int) : ImeState
}

sealed interface ImeEvent {
    /** 버퍼에 들어가는 입력: 한글 자모 또는 독음의 일부가 되는 문자(장음 ー). */
    data class Key(val char: Char) : ImeEvent

    /** 버퍼에 들어가지 않고 바로 입력되는 문자(구두점·기호·숫자). 조합 중이면 먼저 확정한다. */
    data class Text(val text: String) : ImeEvent

    data object Backspace : ImeEvent
    data object Space : ImeEvent
    data object Enter : ImeEvent
    data class SelectCandidate(val index: Int) : ImeEvent

    /** 커서 이동 등 외부 요인으로 조합을 끝낼 때. 현재 포커스 후보 기준으로 확정한다. */
    data object Flush : ImeEvent

    /** 입력 필드가 바뀌는 등 조합을 버릴 때. */
    data object Reset : ImeEvent
}

sealed interface ImeEffect {
    /** 조합 영역을 대체하여 [text] 를 확정 입력한다. */
    data class Commit(val text: String) : ImeEffect

    /** 커서 앞 한 글자 삭제(조합 중이 아닐 때의 백스페이스). */
    data object DeleteBackward : ImeEffect

    /** 엔터: 에디터 액션(검색/이동/전송…) 또는 줄바꿈. */
    data object EditorEnter : ImeEffect
}

data class Transition(val state: ImeState, val effects: List<ImeEffect> = emptyList())

/** 상태에 따라 에디터 조합 영역에 표시할 문자열. */
val ImeState.displayText: String
    get() = when (this) {
        ImeState.Idle -> ""
        is ImeState.Composing -> composition.hangul
        is ImeState.Converting -> composition.preview(focus)
    }

/** 후보 바에 표시할 후보와 포커스. */
val ImeState.candidates: List<Candidate>
    get() = when (this) {
        ImeState.Idle -> emptyList()
        is ImeState.Composing -> composition.candidates
        is ImeState.Converting -> composition.candidates
    }

val ImeState.focusedIndex: Int
    get() = when (this) {
        ImeState.Idle -> -1
        is ImeState.Composing -> 0
        is ImeState.Converting -> focus
    }

/**
 * 한글 → 히라가나 → 한자 변환 상태 머신. 부수효과가 없는 순수 리듀서이다.
 * 상태 전이표는 docs/SPEC.md 의 "상태 머신" 절 참고.
 */
class ImeEngine(
    private val convert: (kana: String, boundaries: IntArray) -> List<Candidate>,
) {

    fun compose(keys: List<Char>): Composition? {
        if (keys.isEmpty()) return null
        val units = HangulComposer.compose(keys)
        val kana = HangulKanaMapper.map(units)
        return Composition(keys, units, kana, convert(kana.kana, kana.unitEnds))
    }

    private fun stateOf(keys: List<Char>): ImeState =
        compose(keys)?.let { ImeState.Composing(it) } ?: ImeState.Idle

    fun reduce(state: ImeState, event: ImeEvent): Transition = when (state) {
        ImeState.Idle -> idle(event)
        is ImeState.Composing -> composing(state, event)
        is ImeState.Converting -> converting(state, event)
    }

    private fun idle(event: ImeEvent): Transition = when (event) {
        is ImeEvent.Key -> Transition(stateOf(listOf(event.char)))
        is ImeEvent.Text -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(event.text)))
        ImeEvent.Backspace -> Transition(ImeState.Idle, listOf(ImeEffect.DeleteBackward))
        ImeEvent.Space -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(" ")))
        ImeEvent.Enter -> Transition(ImeState.Idle, listOf(ImeEffect.EditorEnter))
        is ImeEvent.SelectCandidate, ImeEvent.Flush, ImeEvent.Reset -> Transition(ImeState.Idle)
    }

    private fun composing(state: ImeState.Composing, event: ImeEvent): Transition {
        val comp = state.composition
        return when (event) {
            is ImeEvent.Key -> Transition(stateOf(comp.keys + event.char))
            is ImeEvent.Text -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(comp.commitAllText(0) + event.text)))
            ImeEvent.Backspace -> Transition(stateOf(comp.keys.dropLast(1)))
            ImeEvent.Space ->
                if (comp.candidates.isEmpty()) Transition(state)
                else Transition(ImeState.Converting(comp, if (comp.candidates.size > 1) 1 else 0))
            ImeEvent.Enter -> commitCandidate(comp, 0)
            is ImeEvent.SelectCandidate -> commitCandidate(comp, event.index)
            ImeEvent.Flush -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(comp.commitAllText(0))))
            ImeEvent.Reset -> Transition(ImeState.Idle)
        }
    }

    private fun converting(state: ImeState.Converting, event: ImeEvent): Transition {
        val comp = state.composition
        val focus = state.focus
        return when (event) {
            is ImeEvent.Key -> {
                // 포커스 후보를 확정하고, 남은 입력 뒤에 새 키를 이어서 조합한다.
                Transition(
                    stateOf(comp.remainingKeys(focus) + event.char),
                    listOf(ImeEffect.Commit(comp.candidates[focus].text)),
                )
            }
            is ImeEvent.Text -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(comp.commitAllText(focus) + event.text)))
            ImeEvent.Backspace -> Transition(ImeState.Composing(comp))
            ImeEvent.Space -> Transition(ImeState.Converting(comp, (focus + 1) % comp.candidates.size))
            ImeEvent.Enter -> commitCandidate(comp, focus)
            is ImeEvent.SelectCandidate -> commitCandidate(comp, event.index)
            ImeEvent.Flush -> Transition(ImeState.Idle, listOf(ImeEffect.Commit(comp.commitAllText(focus))))
            ImeEvent.Reset -> Transition(ImeState.Idle)
        }
    }

    /** 후보 확정. 부분 후보이면 남은 입력으로 다시 조합 상태가 된다. */
    private fun commitCandidate(comp: Composition, index: Int): Transition {
        if (index !in comp.candidates.indices) return Transition(ImeState.Composing(comp))
        return Transition(
            stateOf(comp.remainingKeys(index)),
            listOf(ImeEffect.Commit(comp.candidates[index].text)),
        )
    }
}
