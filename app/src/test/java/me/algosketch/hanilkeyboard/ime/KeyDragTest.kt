package me.algosketch.hanilkeyboard.ime

import me.algosketch.hanilkeyboard.kana.KanaRow
import me.algosketch.hanilkeyboard.kana.Voicing
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

    private val ka = KeyAction.KanaRowKey(KanaRow.K, '1')
    private val ha = KeyAction.KanaRowKey(KanaRow.H, '5')

    @Test fun kanaRowDragDirections() {
        assertEquals(ka.copy(voicing = Voicing.DAKUTEN), ka.dragTarget(dx = -60f, dy = 5f, thresholdPx = 50f))
        assertEquals(ha.copy(voicing = Voicing.HANDAKUTEN), ha.dragTarget(dx = 60f, dy = -5f, thresholdPx = 50f))
        assertEquals(KeyAction.Text("1"), ka.dragTarget(dx = 5f, dy = -60f, thresholdPx = 50f))
        // 반탁음이 없는 행은 그대로, 짧은 드래그·아래 드래그도 그대로
        assertEquals(ka, ka.dragTarget(dx = 60f, dy = 0f, thresholdPx = 50f))
        assertEquals(ka, ka.dragTarget(dx = -20f, dy = 0f, thresholdPx = 50f))
        assertEquals(ka, ka.dragTarget(dx = 0f, dy = 60f, thresholdPx = 50f))
    }

    @Test fun textUpDrag() {
        val tilde = KeyAction.Text("～", up = "0")
        assertEquals(KeyAction.Text("0"), tilde.dragTarget(dx = 0f, dy = -60f, thresholdPx = 50f))
        assertEquals(tilde, tilde.dragTarget(dx = 0f, dy = 60f, thresholdPx = 50f))
        assertFalse(KeyAction.Text("！").supportsDrag)
    }

    @Test fun kanaPadDigitsFollowPhoneKeypad() {
        val digits = KeyboardLayouts.kanaPad.flatten().map { it.action }.mapNotNull {
            when (it) {
                is KeyAction.KanaRowKey -> it.digit.toString()
                is KeyAction.Text -> it.up
                else -> null
            }
        }.joinToString("")
        assertEquals("1234567890", digits)
    }

    @Test fun layoutDragKeysAreTheShiftKeys() {
        val dragKeys = KeyboardLayouts.letter.flatten().map { it.action }
            .filterIsInstance<KeyAction.Jamo>().filter { it.supportsDragShift }
            .map { it.normal }.joinToString("")
        assertEquals("ㅂㅈㄷㄱㅅㅐㅔ", dragKeys)
    }
}
