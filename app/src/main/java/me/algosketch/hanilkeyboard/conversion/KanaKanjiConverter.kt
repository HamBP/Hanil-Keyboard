package me.algosketch.hanilkeyboard.conversion

/**
 * 변환 후보. [readingLength] 는 이 후보가 소비하는 히라가나 독음의 길이(앞에서부터)이다.
 * 독음 전체보다 짧으면 부분 후보로, 확정 시 나머지 입력은 조합 상태로 남는다.
 */
data class Candidate(val text: String, val readingLength: Int)

/** 변환에 필요한 데이터 묶음. */
class LanguageModel(val dictionary: Dictionary, val connection: Connection)

/**
 * 히라가나 → 후보 목록.
 *
 * 순서: [히라가나 원문] → [문장 변환(복수 어절)] → [완전 일치 상위] → [가타카나] → [완전 일치 나머지] → [접두 부분 후보]
 * 언어 모델(사전)이 아직 로드되지 않았으면 히라가나/가타카나만 반환한다.
 */
class KanaKanjiConverter(
    private val model: () -> LanguageModel?,
    private val katakana: KatakanaConverter,
) {

    /**
     * @param kana 변환할 히라가나 독음
     * @param boundaries 부분 후보가 끝날 수 있는 독음 위치(입력 음절 경계)
     */
    fun convert(kana: String, boundaries: IntArray): List<Candidate> {
        if (kana.isEmpty()) return emptyList()
        val out = LinkedHashSet<Candidate>()
        val n = kana.length
        out += Candidate(kana, n)

        val lm = model()
        if (lm == null) {
            out += Candidate(katakana.toKatakana(kana), n)
            return out.toList()
        }
        val dict = lm.dictionary

        val norm = Dictionary.normalizeReading(kana)
        val exact = dict.lookup(norm).distinctBy { it.surface }

        if (n <= MAX_SENTENCE_LENGTH) segment(lm, kana, norm)?.let { out += Candidate(it, n) }
        exact.take(TOP_EXACT).forEach { out += Candidate(it.surface, n) }
        out += Candidate(katakana.toKatakana(kana), n)
        exact.drop(TOP_EXACT).forEach { out += Candidate(it.surface, n) }

        boundaries.filter { it in 1 until n }.sortedDescending().forEach { len ->
            dict.lookup(norm.substring(0, len)).distinctBy { it.surface }.take(PREFIX_PER_LENGTH).forEach {
                out += Candidate(it.surface, len)
            }
        }
        return out.take(MAX_CANDIDATES)
    }

    private class Node(
        val end: Int,
        val reading: String,
        val entry: DictEntry,
        val cost: Int,
        val prev: Node?,
    )

    /**
     * 단어 비용 + 품사 연접 비용의 합이 최소인 경로를 찾는다(Viterbi).
     * 2개 이상의 어절로 나뉠 때만 문장 후보로 반환한다.
     */
    private fun segment(lm: LanguageModel, kana: String, norm: String): String? {
        val dict = lm.dictionary
        val conn = lm.connection
        val n = norm.length
        val endingAt = Array(n + 1) { ArrayList<Node>() }
        endingAt[0] += Node(0, "", DictEntry("", 0), 0, null) // BOS
        val maxLen = minOf(dict.maxReadingLength, MAX_WORD_LENGTH)

        for (i in 0 until n) {
            val prevs = endingAt[i]
            if (prevs.isEmpty()) continue
            for (len in 1..minOf(maxLen, n - i)) {
                val reading = norm.substring(i, i + len)
                var entries = dict.lookup(reading)
                    .distinctBy { it.lid to it.rid }
                    .take(LATTICE_PER_READING)
                if (entries.isEmpty()) {
                    if (len > 1) continue
                    entries = listOf(DictEntry(kana.substring(i, i + 1), UNKNOWN_COST))
                }
                for (e in entries) {
                    var best: Node? = null
                    var bestCost = Int.MAX_VALUE
                    for (p in prevs) {
                        val c = p.cost + conn.cost(p.entry.rid, e.lid)
                        if (c < bestCost) {
                            bestCost = c; best = p
                        }
                    }
                    endingAt[i + len] += Node(i + len, reading, e, bestCost + e.cost + kanaPenalty(e, reading), best)
                }
            }
        }

        val last = endingAt[n].minByOrNull { it.cost + conn.cost(it.entry.rid, 0) } ?: return null
        val path = generateSequence(last) { it.prev }.takeWhile { it.prev != null }.toList().asReversed()
        if (path.size < 2) return null
        var start = 0
        val text = path.joinToString("") { node ->
            val s = displaySurface(dict, node, kana.substring(start, node.end))
            start = node.end
            s
        }
        return text.takeIf { it != kana }
    }

    /**
     * 문장 후보에서는 내용어의 히라가나 표기보다 한자 표기를 선호하도록 히라가나 그대로인 내용어에 페널티를 준다.
     * (わたし → 私). 품사 연접 비용이 함께 계산되므로 문법에 맞지 않는 한자 치환은 일어나지 않는다.
     */
    private fun kanaPenalty(e: DictEntry, reading: String): Int =
        if (!e.isFunctionWord && e.surface == reading) KANA_SURFACE_PENALTY else 0

    /** 어절의 표시 표기. 기능어(조사·조동사)는 가타카나 표기(デス 등) 대신 히라가나를 쓴다. */
    private fun displaySurface(dict: Dictionary, node: Node, original: String): String {
        val chosen = node.entry
        val surface = if (chosen.isFunctionWord && !chosen.surface.all(::isHiragana)) {
            dict.lookup(node.reading).firstOrNull { it.isFunctionWord && it.surface.all(::isHiragana) }?.surface
                ?: chosen.surface
        } else {
            chosen.surface
        }
        return if (surface == node.reading) original else surface
    }

    private fun isHiragana(c: Char) = c in 'ぁ'..'ゖ' || c == 'ー'

    companion object {
        private const val TOP_EXACT = 3
        private const val PREFIX_PER_LENGTH = 5
        private const val MAX_CANDIDATES = 80
        private const val MAX_WORD_LENGTH = 20
        private const val MAX_SENTENCE_LENGTH = 48
        private const val LATTICE_PER_READING = 16
        private const val UNKNOWN_COST = 15000
        private const val KANA_SURFACE_PENALTY = 1000
    }
}
