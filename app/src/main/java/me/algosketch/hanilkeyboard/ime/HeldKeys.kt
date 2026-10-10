package me.algosketch.hanilkeyboard.ime

/**
 * 뗄 때 입력되는 키(드래그 키)가 눌려 있는 동안 다른 키가 눌리면, 눌려 있던 키를 먼저 입력해 입력 순서를 지킨다.
 * (빠르게 か → い 를 누르면 か 를 떼기 전에 い 가 눌려, 그대로 두면 い 가 먼저 입력된다)
 */
class HeldKeys {

    /** 눌려 있는 키 하나. [commit] 은 한 번만 입력한다. */
    inner class Held(private val input: () -> Unit) {
        var committed = false
            private set

        /** 키를 떼거나 다른 키가 눌렸을 때 입력한다. 이미 입력했으면 무시. */
        fun commit() {
            if (committed) return
            committed = true
            held.remove(this)
            input()
        }

        /** 입력하지 않고 버린다 (제스처 취소). */
        fun cancel() {
            committed = true
            held.remove(this)
        }
    }

    private val held = mutableListOf<Held>()

    /** 뗄 때 입력되는 키가 눌렸다. 다른 키가 눌리거나 [Held.commit] 될 때 [input] 이 호출된다. */
    fun hold(input: () -> Unit): Held {
        flush()
        return Held(input).also { held += it }
    }

    /** 눌려 있는 키를 누른 순서대로 모두 입력한다. 다른 키를 입력하기 직전에 호출한다. */
    fun flush() {
        held.toList().forEach { it.commit() }
    }
}
