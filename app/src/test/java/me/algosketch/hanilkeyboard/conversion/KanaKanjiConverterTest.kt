package me.algosketch.hanilkeyboard.conversion

import me.algosketch.hanilkeyboard.hangul.HangulComposer
import me.algosketch.hanilkeyboard.hangul.HangulKanaMapper
import me.algosketch.hanilkeyboard.hangul.HangulKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KanaKanjiConverterTest {

    companion object {
        val dictionary: BinaryDictionary by lazy {
            File("src/main/assets/dict/ja_dict.bin.gzip").inputStream().use { BinaryDictionary.load(it) }
        }
        val model: LanguageModel by lazy {
            val connection = File("src/main/assets/dict/connection.bin.gzip").inputStream().use { BinaryConnection.load(it) }
            LanguageModel(dictionary, connection)
        }
    }

    private val converter = KanaKanjiConverter({ model }, SimpleKatakanaConverter)

    private fun candidates(hangul: String): List<Candidate> {
        val kana = HangulKanaMapper.map(HangulComposer.compose(HangulKeys.of(hangul)))
        return converter.convert(kana.kana, kana.unitEnds)
    }

    @Test fun dictionaryLoads() {
        assertTrue(dictionary.readingCount > 100_000)
        assertEquals("学校", dictionary.lookup("がっこう").first().surface)
    }

    @Test fun hiraganaFirstThenKanjiAndKatakana() {
        val c = candidates("각코우").map { it.text }
        assertEquals("がっこう", c[0])
        assertTrue(c.contains("学校"))
        assertTrue(c.contains("ガッコウ"))
    }

    @Test fun zasshi() {
        assertTrue(candidates("잦시").any { it.text == "雑誌" })
    }

    @Test fun normalizedReadingFindsDzu() {
        // つずく 로 입력해도 続く 를 찾는다.
        assertTrue(candidates("츠즈쿠").any { it.text == "続く" })
    }

    @Test fun sentenceCandidate() {
        assertEquals("私は学生です", candidates("와타시하각세이데스")[1].text)
        assertEquals("学校に行きます", candidates("각코우니이키마스")[1].text)
        assertEquals("コーヒーを飲みたい", candidates("코ー히ー워노미타이")[1].text)
    }

    @Test fun prefixCandidatesAlignToSyllables() {
        val c = candidates("각코우니")
        val prefix = c.filter { it.readingLength < 5 }
        assertTrue(prefix.any { it.text == "学校" && it.readingLength == 4 })
        // がっ 처럼 음절 중간에서 끝나는 부분 후보는 없어야 한다 (경계: 2,3,4,5)
        assertTrue(prefix.all { it.readingLength in setOf(2, 3, 4) })
    }

    @Test fun noDictionaryFallsBackToKana() {
        val c = KanaKanjiConverter({ null }, SimpleKatakanaConverter).convert("かな", intArrayOf(1, 2))
        assertEquals(listOf("かな", "カナ"), c.map { it.text })
    }
}

