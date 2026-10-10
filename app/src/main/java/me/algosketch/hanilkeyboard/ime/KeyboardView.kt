package me.algosketch.hanilkeyboard.ime

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import me.algosketch.hanilkeyboard.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.algosketch.hanilkeyboard.conversion.Candidate

/** 키보드 화면에 필요한 모든 상태. 서비스가 만들어 전달한다. */
data class KeyboardUiState(
    val layout: KeyboardLayoutType,
    val mode: KeyboardMode,
    val symbolPage: Int,
    val shift: Boolean,
    val candidates: List<Candidate>,
    val focusedIndex: Int,
    val composing: Boolean,
    val converting: Boolean,
    /** 엔터 키 라벨. null 이면 줄바꿈 아이콘을 표시한다. */
    val enterLabel: String?,
)

private data class KeyboardColors(
    val background: Color,
    val key: Color,
    val specialKey: Color,
    val pressed: Color,
    val text: Color,
    val subText: Color,
    val accent: Color,
    val focus: Color,
)

@Composable
private fun keyboardColors(): KeyboardColors = if (isSystemInDarkTheme()) {
    KeyboardColors(
        background = Color(0xFF1E1F22), key = Color(0xFF3A3B3F), specialKey = Color(0xFF2B2C30),
        pressed = Color(0xFF55575C), text = Color(0xFFECECEC), subText = Color(0xFF9AA0A6),
        accent = Color(0xFF8AB4F8), focus = Color(0xFF34466B),
    )
} else {
    KeyboardColors(
        background = Color(0xFFE8EAED), key = Color(0xFFFFFFFF), specialKey = Color(0xFFC9CDD3),
        pressed = Color(0xFFB4B8BE), text = Color(0xFF1F1F1F), subText = Color(0xFF5F6368),
        accent = Color(0xFF1A5FD0), focus = Color(0xFFD6E2FB),
    )
}

@Composable
fun KeyboardView(
    state: KeyboardUiState,
    onKey: (KeyAction) -> Unit,
    onCandidate: (Int) -> Unit,
    bottomInsetPx: Int = 0,
) {
    val colors = keyboardColors()
    val heldKeys = remember { HeldKeys() }
    val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(bottom = bottomInset)
    ) {
        // 후보가 있을 때만 후보 바를 표시한다 (없으면 숫자 줄이 맨 위)
        if (state.candidates.isNotEmpty()) CandidateBar(state, colors, onCandidate)
        val rows = when (state.mode) {
            KeyboardMode.LETTER -> KeyboardLayouts.letter(state.layout)
            KeyboardMode.SYMBOL -> KeyboardLayouts.symbolPages[state.symbolPage]
        }
        Column(Modifier.padding(horizontal = 3.dp, vertical = 4.dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().height(52.dp)) {
                    row.forEach { key ->
                        if (key.action == KeyAction.Spacer) {
                            Spacer(Modifier.weight(key.weight))
                            return@forEach
                        }
                        KeyButton(
                            key = key,
                            state = state,
                            colors = colors,
                            heldKeys = heldKeys,
                            modifier = Modifier.weight(key.weight),
                            onKey = onKey,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateBar(state: KeyboardUiState, colors: KeyboardColors, onCandidate: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.candidates, state.focusedIndex) {
        if (state.focusedIndex >= 0 && state.focusedIndex < state.candidates.size) {
            listState.animateScrollToItem(maxOf(0, state.focusedIndex - 1))
        } else {
            listState.scrollToItem(0)
        }
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(state.candidates) { index, candidate ->
            val focused = index == state.focusedIndex
            Box(
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) colors.focus else Color.Transparent)
                    .clickable { onCandidate(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = candidate.text,
                    color = if (focused) colors.accent else colors.text,
                    fontSize = 18.sp,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    key: Key,
    state: KeyboardUiState,
    colors: KeyboardColors,
    heldKeys: HeldKeys,
    modifier: Modifier,
    onKey: (KeyAction) -> Unit,
) {
    val action = key.action
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableStateOf<KeyAction?>(null) }
    val currentOnKey by rememberUpdatedState(onKey)
    val special = action !is KeyAction.Jamo && action !is KeyAction.Text && action !is KeyAction.Reading &&
        action !is KeyAction.MultiTap && action != KeyAction.Space &&
        action !is KeyAction.KanaRowKey && action !is KeyAction.KanaColumnKey
    val highlighted = (action == KeyAction.Shift && state.shift) ||
        (action == KeyAction.Enter && state.composing)

    fun fire(target: KeyAction = action) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        currentOnKey(target)
    }

    Box(
        modifier
            .fillMaxHeight()
            .padding(
                start = if (key.wideGapStart) KEY_HALF_GAP * 3 else KEY_HALF_GAP,
                end = if (key.wideGapEnd) KEY_HALF_GAP * 3 else KEY_HALF_GAP,
                top = 4.dp,
                bottom = 4.dp,
            )
            .clip(RoundedCornerShape(7.dp))
            .background(
                when {
                    pressed -> colors.pressed
                    highlighted -> colors.focus
                    special -> colors.specialKey
                    else -> colors.key
                }
            )
            .pointerInput(action) {
                if (action.supportsDrag) {
                    // 드래그 방향에 따라 다른 문자를 입력하는 키 (드래그 판정을 위해 뗄 때 입력).
                    // 누른 채로 다른 키가 눌리면 그 전에 현재 방향의 문자로 입력해 순서를 지킨다.
                    val threshold = DRAG_THRESHOLD.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        var target: KeyAction = action
                        val held = heldKeys.hold { fire(target) }
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!held.committed) {
                                    val delta = change.position - down.position
                                    target = action.dragTarget(delta.x, delta.y, threshold)
                                }
                                dragTarget = target.takeIf { it != action && !held.committed }
                                if (!change.pressed) {
                                    held.commit()
                                    break
                                }
                                change.consume()
                            }
                        } finally {
                            held.cancel()
                            pressed = false
                            dragTarget = null
                        }
                    }
                    return@pointerInput
                }
                detectTapGestures(onPress = {
                    pressed = true
                    heldKeys.flush() // 아직 떼지 않은 드래그 키를 먼저 입력
                    when {
                        action == KeyAction.Backspace -> {
                            // 길게 누르면 반복 삭제
                            val job: Job = scope.launch {
                                fire()
                                delay(400)
                                while (true) {
                                    fire(); delay(50)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                        else -> {
                            fire()
                            tryAwaitRelease()
                        }
                    }
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (action == KeyAction.Enter && state.enterLabel == null) {
            Icon(
                painter = painterResource(R.drawable.ic_key_enter),
                contentDescription = "엔터",
                tint = colors.text,
                modifier = Modifier.size(28.dp),
            )
            return@Box
        }
        val shown = dragTarget ?: action
        Text(
            text = labelOf(shown, state),
            color = if (highlighted) colors.accent else colors.text,
            fontSize = when (shown) {
                is KeyAction.Jamo, is KeyAction.Text, is KeyAction.Reading,
                is KeyAction.KanaRowKey, is KeyAction.KanaColumnKey -> 20.sp
                is KeyAction.MultiTap -> 15.sp // 여러 문자를 한 키에 표시
                KeyAction.Small -> 18.sp
                else -> 14.sp
            },
            maxLines = 1,
        )
        // 위로 드래그해 입력하는 숫자 힌트
        val hint = when (action) {
            is KeyAction.KanaRowKey -> action.digit.toString()
            is KeyAction.Text -> action.up
            else -> null
        }
        if (hint != null && dragTarget == null) {
            Text(
                text = hint,
                color = colors.subText,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
            )
        }
    }
}

/** 키 양옆 여백. 이웃한 두 키 사이 간격은 이 값의 두 배이고, 넓은 간격(wideGap)은 그 두 배다. */
private val KEY_HALF_GAP = 3.dp

/** 이 거리 이상 드래그하면 드래그 방향의 문자로 입력한다 (시프트 문자·탁음·숫자). */
private val DRAG_THRESHOLD = 20.dp

private fun labelOf(action: KeyAction, state: KeyboardUiState): String = when (action) {
    is KeyAction.Jamo -> (if (state.shift) action.shifted else action.normal).toString()
    is KeyAction.Reading -> action.char.toString()
    is KeyAction.Text -> action.text
    is KeyAction.KanaRowKey -> action.row.kana(action.voicing)
    is KeyAction.KanaColumnKey -> action.column.alone
    KeyAction.Small -> "小"
    is KeyAction.MultiTap -> action.options.joinToString("")
    KeyAction.Backspace -> "⌫"
    KeyAction.Space -> when {
        state.converting -> "다음 후보"
        state.composing -> "다음"
        else -> "띄어쓰기"
    }
    KeyAction.Enter -> state.enterLabel.orEmpty()
    KeyAction.Shift -> "⇧"
    KeyAction.SymbolMode -> "!#1"
    KeyAction.LetterMode -> "가あ"
    KeyAction.SymbolPage -> "${state.symbolPage + 1}/${KeyboardLayouts.symbolPages.size}"
    KeyAction.Spacer -> ""
}
