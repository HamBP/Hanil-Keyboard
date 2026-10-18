package me.algosketch.hanilkeyboard.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HanjaGlossaryTest {

    companion object {
        val glossary: HanjaGlossary by lazy {
            File("src/main/assets/dict/hanja_gloss.tsv.gzip").inputStream().use { HanjaGlossary.load(it) }
        }
    }

    private fun gloss(c: String) = glossary.lookup(c.codePointAt(0))

    @Test fun parsesBaseAndReadings() {
        val g = HanjaGlossary.parse(sequenceOf("学\t學\t배울 학", "行\t\t다닐 행\t시장 항", "broken"))
        assertEquals(2, g.size)
        assertEquals(HanjaGloss("学", "學", listOf("배울 학")), g.lookup('学'.code))
        assertEquals(HanjaGloss("行", null, listOf("다닐 행", "시장 항")), g.lookup('行'.code))
    }

    @Test fun assetLoads() {
        assertTrue(glossary.size > 7000)
    }

    @Test fun glossesEachKanjiOfCandidate() {
        val g = glossary.glossesOf("学校に行く")
        assertEquals(listOf("学", "校", "行"), g.map { it.char })
        assertEquals("學", g[0].base)
        assertEquals("배울 학", g[0].readings.first())
        assertNull(g[1].base)
        assertEquals("학교 교", g[1].readings.first())
    }

    @Test fun shinjitaiUsesKyujitai() {
        assertEquals("氣", gloss("気")?.base)
        assertEquals("樂", gloss("楽")?.base)
        assertEquals("驛", gloss("駅")?.base)
        // 한국에서는 다른 글자인 신자체: 芸 은 향풀 운이 아니라 藝(재주 예)
        assertEquals("藝", gloss("芸")?.base)
        assertEquals("재주 예", gloss("芸")?.readings?.first())
    }

    @Test fun kanaAndJapaneseOnlyKanjiAreSkipped() {
        assertTrue(glossary.glossesOf("がっこうガッコウ").isEmpty())
        assertNull(gloss("込"))
    }

    @Test fun repeatedKanjiShownOnce() {
        assertEquals(listOf("日"), glossary.glossesOf("日日").map { it.char })
    }
}
