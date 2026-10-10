package me.algosketch.hanilkeyboard.hangul

import org.junit.Assert.assertEquals
import org.junit.Test

class HangulComposerTest {
    private fun compose(keys: String) = HangulComposer.text(HangulComposer.compose(keys.toList()))

    @Test fun basicSyllables() {
        assertEquals("각코우", compose("ㄱㅏㄱㅋㅗㅇㅜ"))
        assertEquals("가고", compose("ㄱㅏㄱㅗ")) // 받침 이동
        assertEquals("달가", compose("ㄷㅏㄹㄱㅏ")) // 겹받침 분리
        assertEquals("닭", compose("ㄷㅏㄹㄱ"))
        assertEquals("와", compose("ㅇㅗㅏ"))
        assertEquals("ㅋ", compose("ㅋ"))
        assertEquals("ㅏ", compose("ㅏ"))
        assertEquals("코ー히ー", compose("ㅋㅗーㅎㅣー"))
        assertEquals("ㄸ", compose("ㄸ")) // ㄸ 는 받침 불가
        assertEquals("가ㄸ", compose("ㄱㅏㄸ"))
    }

    @Test fun keyCountsCoverAllKeys() {
        val keys = HangulKeys.of("각코우왔닭")
        assertEquals(keys.size, HangulComposer.compose(keys).sumOf { it.keyCount })
    }

    @Test fun keysRoundTrip() {
        for (s in listOf("각코우", "잣시", "와타시와", "닭갈비", "의자", "궤도")) {
            assertEquals(s, HangulComposer.text(HangulComposer.compose(HangulKeys.of(s))))
        }
    }
}

class HangulKanaMapperTest {
    private fun kana(s: String) = HangulKanaMapper.map(s)

    @Test fun userExamples() {
        assertEquals("が", kana("가"))
        assertEquals("がっこう", kana("각코우"))
        assertEquals("がっこう", kana("갓코우"))
        assertEquals("ざっし", kana("잣시"))
        assertEquals("ざっし", kana("잦시"))
        assertEquals("ざっし", kana("잧시"))
    }

    @Test fun deferredMappingDependsOnNextSyllable() {
        assertEquals("がく", kana("각"))
        assertEquals("がっく", kana("각ㅋ"))
        assertEquals("がっこ", kana("각코"))
    }

    @Test fun rows() {
        assertEquals("わたしは", kana("와타시하"))
        assertEquals("ありがとう", kana("아리가토우"))
        assertEquals("こんにちは", kana("콘니치하"))
        assertEquals("つづく", kana("츠두쿠"))
        assertEquals("つずく", kana("쓰즈쿠"))
        assertEquals("きゃきゅきょ", kana("캬큐쿄"))
        assertEquals("しゃしゅしょ", kana("샤슈쇼"))
        assertEquals("じゃじゅじょ", kana("쟈쥬죠"))
        assertEquals("ちゃちゅちょ", kana("챠츄쵸"))
        assertEquals("ちゃちちゅちぇちょ", kana("차치추체초"))
        assertEquals("を", kana("워"))
        assertEquals("ふぁふぃふぇふぉ", kana("화휘훼훠"))
        assertEquals("てぃでぃ", kana("틔듸"))
        assertEquals("こーひー", kana("코ー히ー"))
        assertEquals("いぇ", kana("예"))
        assertEquals("しぇ", kana("셰"))
    }

    @Test fun finals() {
        assertEquals("ほん", kana("혼"))
        assertEquals("かんばん", kana("칸반"))
        assertEquals("がんば", kana("감바"))
        assertEquals("きむ", kana("킴"))
        assertEquals("まって", kana("맛테"))
        assertEquals("かっぷ", kana("캅푸"))
        assertEquals("ぱく", kana("팍"))
        assertEquals("あっ", kana("앗"))
        assertEquals("はる", kana("할"))
        assertEquals("さんぽ", kana("산포"))
    }

    @Test fun boundaries() {
        val r = HangulKanaMapper.map(HangulComposer.compose(HangulKeys.of("각코우")))
        assertEquals(listOf(2, 3, 4), r.unitEnds.toList()) // がっ|こ|う
        assertEquals(2, r.unitsFor(3))
        assertEquals(-1, r.unitsFor(1))
    }
}
