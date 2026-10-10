package me.algosketch.hanilkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class HeldKeysTest {
    private val keys = HeldKeys()
    private val out = StringBuilder()

    @Test fun releaseCommits() {
        val ka = keys.hold { out.append("か") }
        assertEquals("", out.toString())
        ka.commit()
        assertEquals("か", out.toString())
    }

    @Test fun otherKeyPressFlushesHeldKeyFirst() {
        val ka = keys.hold { out.append("か") }
        keys.flush() // い 가 눌리기 직전
        out.append("い")
        ka.commit() // か 를 늦게 뗌
        assertEquals("かい", out.toString())
    }

    @Test fun cancelDropsWithoutInput() {
        val ka = keys.hold { out.append("か") }
        ka.cancel()
        keys.flush()
        assertEquals("", out.toString())
    }

    @Test fun holdingAnotherReleaseKeyFlushesPrevious() {
        val ka = keys.hold { out.append("か") }
        val sa = keys.hold { out.append("さ") }
        sa.commit()
        ka.commit()
        assertEquals("かさ", out.toString())
    }
}
