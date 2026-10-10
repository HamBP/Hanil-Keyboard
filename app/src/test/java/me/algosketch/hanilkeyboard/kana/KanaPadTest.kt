package me.algosketch.hanilkeyboard.kana

import me.algosketch.hanilkeyboard.kana.KanaPadComposer.Result.Insert
import me.algosketch.hanilkeyboard.kana.KanaPadComposer.Result.ReplaceLast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanaPadTest {
    private val pad = KanaPadComposer()

    @Test fun rowKeyInsertsAColumn() {
        assertEquals(Insert("か"), pad.pressRow(KanaRow.K))
        assertEquals(Insert("ん"), pad.pressRow(KanaRow.NN))
    }

    @Test fun columnAfterRowChangesColumn() {
        pad.pressRow(KanaRow.K)
        assertEquals(ReplaceLast("き"), pad.pressColumn(KanaColumn.I))
        // 단은 한 번만 바꾼다: 다음 오른쪽 키는 그대로 입력
        assertEquals(Insert("い"), pad.pressColumn(KanaColumn.I))
    }

    @Test fun columnWithoutRowInsertsItself() {
        assertEquals(Insert("う"), pad.pressColumn(KanaColumn.U))
        assertEquals(Insert("や"), pad.pressColumn(KanaColumn.YA))
    }

    @Test fun voicing() {
        assertEquals(Insert("が"), pad.pressRow(KanaRow.K, Voicing.DAKUTEN))
        assertEquals(ReplaceLast("ぐ"), pad.pressColumn(KanaColumn.U))
        assertEquals(Insert("ぱ"), pad.pressRow(KanaRow.H, Voicing.HANDAKUTEN))
        assertEquals(ReplaceLast("ぽ"), pad.pressColumn(KanaColumn.O))
        // 지원하지 않는 탁음은 청음
        assertEquals(Insert("な"), pad.pressRow(KanaRow.N, Voicing.DAKUTEN))
        assertEquals(Insert("さ"), pad.pressRow(KanaRow.S, Voicing.HANDAKUTEN))
    }

    @Test fun youon() {
        pad.pressRow(KanaRow.K)
        assertEquals(ReplaceLast("きゃ"), pad.pressColumn(KanaColumn.YA))
        pad.pressRow(KanaRow.S, Voicing.DAKUTEN)
        assertEquals(ReplaceLast("じゅ"), pad.pressColumn(KanaColumn.YU))
        pad.pressRow(KanaRow.W)
        assertEquals(Insert("よ"), pad.pressColumn(KanaColumn.YO))
    }

    @Test fun waRowAndN() {
        pad.pressRow(KanaRow.W)
        assertEquals(ReplaceLast("を"), pad.pressColumn(KanaColumn.O))
        pad.pressRow(KanaRow.NN)
        assertEquals(Insert("あ"), pad.pressColumn(KanaColumn.A))
    }

    @Test fun resetEndsPending() {
        pad.pressRow(KanaRow.T)
        pad.reset()
        assertEquals(Insert("え"), pad.pressColumn(KanaColumn.E))
    }

    @Test fun toggleSmall() {
        assertEquals('っ', KanaPadComposer.toggleSmall('つ'))
        assertEquals('つ', KanaPadComposer.toggleSmall('っ'))
        assertEquals('ゃ', KanaPadComposer.toggleSmall('や'))
        assertEquals('ぁ', KanaPadComposer.toggleSmall('あ'))
        assertNull(KanaPadComposer.toggleSmall('か'))
    }
}
