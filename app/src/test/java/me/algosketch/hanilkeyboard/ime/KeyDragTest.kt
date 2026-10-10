package me.algosketch.hanilkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyDragTest {
    private val bieup = KeyAction.Jamo('ㅂ', 'ㅃ')
    private val ae = KeyAction.Jamo('ㅐ', 'ㅒ')
    private val yo = KeyAction.Jamo('ㅛ')

    @Test fun dragDownPastThresholdGivesShifted() {
        assertEquals(KeyAction.Jamo('ㅃ'), bieup.resolveDrag(dy = 60f, thresholdPx = 50f))
        assertEquals(KeyAction.Jamo('ㅒ'), ae.resolveDrag(dy = 50f, thresholdPx = 50f))
    }

    @Test fun shortDragOrUpwardKeepsNormal() {
        assertEquals(bieup, bieup.resolveDrag(dy = 10f, thresholdPx = 50f))
        assertEquals(bieup, bieup.resolveDrag(dy = -80f, thresholdPx = 50f))
    }

    @Test fun onlyKeysWithShiftedVariantSupportDrag() {
        assertTrue(bieup.supportsDragShift)
        assertFalse(yo.supportsDragShift)
        assertEquals(yo, yo.resolveDrag(dy = 100f, thresholdPx = 50f))
    }

    @Test fun layoutDragKeysAreTheShiftKeys() {
        val dragKeys = KeyboardLayouts.letter.flatten().map { it.action }
            .filterIsInstance<KeyAction.Jamo>().filter { it.supportsDragShift }
            .map { it.normal }.joinToString("")
        assertEquals("ㅂㅈㄷㄱㅅㅐㅔ", dragKeys)
    }
}
