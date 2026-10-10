package me.algosketch.hanilkeyboard.ime

/**
 * 한 키에 여러 문자를 두고 연속으로 눌러 바꾸는 키(예: 、→。→、)의 상태.
 * 같은 키를 [timeoutMs] 안에 다시 누르고 그 사이 다른 입력이 없으면 직전 문자를 다음 문자로 바꾼다.
 */
class MultiTapTracker(private val timeoutMs: Long = 1000L) {

    sealed interface Result {
        /** 새로 [text] 를 입력한다. */
        data class Insert(val text: String) : Result

        /** 직전에 입력한 [previous] 를 [text] 로 바꾼다. */
        data class Replace(val previous: String, val text: String) : Result
    }

    private var lastKey: KeyAction.MultiTap? = null
    private var index = 0
    private var lastTime = 0L

    fun press(key: KeyAction.MultiTap, now: Long): Result {
        val cycling = lastKey == key && now - lastTime <= timeoutMs
        lastTime = now
        if (!cycling) {
            lastKey = key
            index = 0
            return Result.Insert(key.options[0])
        }
        val previous = key.options[index]
        index = (index + 1) % key.options.size
        return Result.Replace(previous, key.options[index])
    }

    /** 다른 입력이 들어오면 연속 입력을 끊는다. */
    fun reset() {
        lastKey = null
    }
}
