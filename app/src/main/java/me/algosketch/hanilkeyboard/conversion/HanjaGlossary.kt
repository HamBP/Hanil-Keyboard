package me.algosketch.hanilkeyboard.conversion

import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * 한자 한 글자의 한국어 훈음.
 * [base] 는 훈음을 찾은 정자(일본 신자체 学 → 學). 글자 자체로 찾았으면 null.
 * [readings] 는 자주 쓰이는 음부터 ("배울 학").
 */
data class HanjaGloss(val char: String, val base: String?, val readings: List<String>)

/**
 * 한자 → 한국어 훈음 조회. 데이터는 tools/dictgen/build_hanja_gloss.py 로 만든
 * assets/dict/hanja_gloss.tsv.gzip (한자 \t 정자 \t 훈음 \t 훈음 ...).
 */
class HanjaGlossary(private val table: Map<Int, HanjaGloss>) {

    val size: Int get() = table.size

    fun lookup(char: Int): HanjaGloss? = table[char]

    /** [text] 에 들어 있는 한자의 훈음을 나온 순서대로 (같은 글자는 한 번만). 가나 등 훈음이 없는 글자는 건너뛴다. */
    fun glossesOf(text: String): List<HanjaGloss> =
        text.codePoints().toArray().distinct().mapNotNull { table[it] }

    companion object {
        fun parse(lines: Sequence<String>): HanjaGlossary {
            val table = HashMap<Int, HanjaGloss>()
            for (line in lines) {
                val p = line.split('\t')
                if (p.size < 3 || p[0].isEmpty()) continue
                table[p[0].codePointAt(0)] = HanjaGloss(p[0], p[1].ifEmpty { null }, p.drop(2))
            }
            return HanjaGlossary(table)
        }

        fun load(gzip: InputStream): HanjaGlossary =
            GZIPInputStream(gzip.buffered()).bufferedReader().useLines { parse(it) }
    }
}
