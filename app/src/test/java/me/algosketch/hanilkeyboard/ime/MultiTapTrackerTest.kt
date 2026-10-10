package me.algosketch.hanilkeyboard.ime

import me.algosketch.hanilkeyboard.ime.MultiTapTracker.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiTapTrackerTest {
    private val punct = KeyAction.MultiTap(listOf("、", "。"))
    private val tracker = MultiTapTracker(timeoutMs = 1000)

    @Test fun pressTwiceSwitchesAndCycles() {
        assertEquals(Result.Insert("、"), tracker.press(punct, 0))
        assertEquals(Result.Replace("、", "。"), tracker.press(punct, 300))
        assertEquals(Result.Replace("。", "、"), tracker.press(punct, 600))
    }

    @Test fun timeoutStartsNewInsert() {
        tracker.press(punct, 0)
        assertEquals(Result.Insert("、"), tracker.press(punct, 1500))
    }

    @Test fun resetStartsNewInsert() {
        tracker.press(punct, 0)
        tracker.reset()
        assertEquals(Result.Insert("、"), tracker.press(punct, 100))
    }
}
