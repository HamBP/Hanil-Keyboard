package me.algosketch.hanilkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutsTest {

    private fun bottom(rows: List<List<Key>>) = rows.last()

    @Test fun arrowsSurroundSpaceAndKeepRowWidth() {
        for (type in KeyboardLayoutType.entries) {
            val rows = KeyboardLayouts.letter(type)
            val arrowed = KeyboardLayouts.withFocusArrows(rows)
            val actions = bottom(arrowed).map { it.action }
            val space = actions.indexOf(KeyAction.Space)
            assertEquals(KeyAction.FocusPrevious, actions[space - 1])
            assertEquals(KeyAction.FocusNext, actions[space + 1])
            assertEquals(bottom(rows).sumOf { it.weight.toDouble() }, bottom(arrowed).sumOf { it.weight.toDouble() }, 1e-4)
            assertEquals(rows.dropLast(1), arrowed.dropLast(1))
        }
    }
}
