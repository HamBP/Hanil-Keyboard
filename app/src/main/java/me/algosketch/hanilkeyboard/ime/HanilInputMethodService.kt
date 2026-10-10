package me.algosketch.hanilkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.algosketch.hanilkeyboard.conversion.IcuKatakanaConverter
import me.algosketch.hanilkeyboard.conversion.KanaKanjiConverter
import me.algosketch.hanilkeyboard.conversion.LanguageModelRepository
import me.algosketch.hanilkeyboard.engine.ImeEffect
import me.algosketch.hanilkeyboard.engine.ImeEngine
import me.algosketch.hanilkeyboard.engine.ImeEvent
import me.algosketch.hanilkeyboard.engine.ImeState
import me.algosketch.hanilkeyboard.engine.candidates
import me.algosketch.hanilkeyboard.engine.displayText
import me.algosketch.hanilkeyboard.engine.focusedIndex

/**
 * 시스템 키보드(IME) 서비스.
 * 변환 로직은 [ImeEngine](순수 상태 머신)에 위임하고, 이 클래스는 이벤트 전달과
 * 결과(확정/조합 문자열/에디터 액션)를 InputConnection 에 반영하는 일만 한다.
 * 입력 문자열은 기기 내부에서만 처리하며 어디에도 저장·전송하지 않는다.
 */
class HanilInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val converter = KanaKanjiConverter({ LanguageModelRepository.model }, IcuKatakanaConverter())
    private val engine = ImeEngine(converter::convert)

    private var imeState by mutableStateOf<ImeState>(ImeState.Idle)
    private var mode by mutableStateOf(KeyboardMode.LETTER)
    private var symbolPage by mutableIntStateOf(0)
    private var shift by mutableStateOf(false)
    private var enterAction by mutableIntStateOf(EditorInfo.IME_ACTION_UNSPECIFIED)
    private var showSwitchKey by mutableStateOf(true)
    private var bottomInsetPx by mutableIntStateOf(0)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        LanguageModelRepository.loadAsync(this)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HanilInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@HanilInputMethodService)
            setContent {
                val state = imeState
                KeyboardView(
                    state = KeyboardUiState(
                        mode = mode,
                        symbolPage = symbolPage,
                        shift = shift,
                        candidates = state.candidates,
                        focusedIndex = state.focusedIndex,
                        composing = state != ImeState.Idle,
                        converting = state is ImeState.Converting,
                        enterLabel = enterLabel(state),
                        showSwitchKey = showSwitchKey,
                    ),
                    bottomInsetPx = bottomInsetPx,
                    onKey = ::onKey,
                    onCandidate = { dispatch(ImeEvent.SelectCandidate(it)) },
                    onSwitchImeLongPress = {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                )
            }
        }
    }

    // ---- 입력 필드 수명주기 ----

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        imeState = ImeState.Idle
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        imeState = ImeState.Idle
        shift = false
        symbolPage = 0
        showSwitchKey = shouldOfferSwitchingToNextInputMethod()
        updateBottomInset()
        mode = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyboardMode.SYMBOL
            else -> KeyboardMode.LETTER
        }
        enterAction = if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        // 키보드가 닫히면 조합 중인 입력을 현재 포커스 후보로 확정한다.
        if (imeState != ImeState.Idle) dispatch(ImeEvent.Flush)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        imeState = ImeState.Idle
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (imeState == ImeState.Idle || candidatesStart < 0) return
        val cursorAtEnd = newSelStart == newSelEnd && newSelEnd == candidatesEnd
        if (!cursorAtEnd) flushAt(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    /** 사용자가 조합 영역 밖으로 커서를 옮긴 경우: 조합을 확정하되 커서는 사용자가 둔 위치를 유지한다. */
    private fun flushAt(selStart: Int, selEnd: Int, compStart: Int, compEnd: Int) {
        val ic = currentInputConnection ?: return
        val transition = engine.reduce(imeState, ImeEvent.Flush)
        imeState = transition.state
        val text = transition.effects.filterIsInstance<ImeEffect.Commit>().joinToString("") { it.text }
        val delta = text.length - (compEnd - compStart)
        fun adjust(p: Int) = when {
            p >= compEnd -> p + delta
            p > compStart -> compStart + minOf(p - compStart, text.length)
            else -> p
        }
        ic.beginBatchEdit()
        ic.setComposingText(text, 1)
        ic.finishComposingText()
        ic.setSelection(adjust(selStart), adjust(selEnd))
        ic.endBatchEdit()
    }

    /**
     * 하단 시스템 영역 높이를 키보드 아래 여백으로 쓴다.
     * IME 창에서는 인셋이 입력 뷰까지 전달되지 않으므로 창의 루트 인셋을 직접 읽는다.
     * 제스처 내비게이션에서는 IME 가 그리는 ⌄·⌨ 버튼 영역이 navigationBars 가 아닌
     * tappableElement 로 보고되므로(예: 42px vs 135px) 둘 중 큰 값을 쓴다.
     */
    private fun updateBottomInset() {
        val decor = window.window?.decorView ?: return
        fun read() {
            val insets = decor.rootWindowInsets ?: return
            bottomInsetPx = maxOf(
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsets.Type.tappableElement()).bottom,
            )
        }
        read()
        decor.post { read() }
    }

    // ---- 키 입력 ----

    private fun onKey(action: KeyAction) {
        when (action) {
            is KeyAction.Jamo -> {
                dispatch(ImeEvent.Key(if (shift) action.shifted else action.normal))
                shift = false
            }
            is KeyAction.Reading -> dispatch(ImeEvent.Key(action.char))
            is KeyAction.Text -> dispatch(ImeEvent.Text(action.text))
            KeyAction.Backspace -> dispatch(ImeEvent.Backspace)
            KeyAction.Space -> dispatch(ImeEvent.Space)
            KeyAction.Enter -> dispatch(ImeEvent.Enter)
            KeyAction.Shift -> shift = !shift
            KeyAction.SymbolMode -> { mode = KeyboardMode.SYMBOL; symbolPage = 0 }
            KeyAction.LetterMode -> mode = KeyboardMode.LETTER
            KeyAction.SymbolPage -> symbolPage = (symbolPage + 1) % KeyboardLayouts.symbolPages.size
            KeyAction.SwitchIme -> switchToNextInputMethod(false)
        }
    }

    /** 상태 머신에 이벤트를 전달하고 결과를 에디터에 반영한다. */
    private fun dispatch(event: ImeEvent) {
        val ic = currentInputConnection
        if (ic == null) {
            imeState = ImeState.Idle
            return
        }
        val before = imeState
        val transition = engine.reduce(before, event)
        imeState = transition.state

        ic.beginBatchEdit()
        var committed = false
        for (effect in transition.effects) {
            when (effect) {
                is ImeEffect.Commit -> {
                    ic.commitText(effect.text, 1)
                    committed = true
                }
                ImeEffect.DeleteBackward -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                ImeEffect.EditorEnter -> performEnter()
            }
        }
        val display = transition.state.displayText
        if (display.isNotEmpty()) {
            ic.setComposingText(display, 1)
        } else if (before != ImeState.Idle && !committed) {
            ic.commitText("", 1) // 조합 영역 비우기
        }
        ic.endBatchEdit()
    }

    private fun performEnter() {
        val ic = currentInputConnection ?: return
        if (enterAction != EditorInfo.IME_ACTION_NONE && enterAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(enterAction)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun enterLabel(state: ImeState): String {
        if (state != ImeState.Idle) return "확정"
        return when (enterAction) {
            EditorInfo.IME_ACTION_SEARCH -> "검색"
            EditorInfo.IME_ACTION_GO -> "이동"
            EditorInfo.IME_ACTION_SEND -> "전송"
            EditorInfo.IME_ACTION_NEXT -> "다음"
            EditorInfo.IME_ACTION_DONE -> "완료"
            EditorInfo.IME_ACTION_PREVIOUS -> "이전"
            else -> "↵"
        }
    }
}
