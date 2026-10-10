package me.algosketch.hanilkeyboard.conversion

/**
 * 사전 항목. [cost] 가 낮을수록 빈도가 높다(Mozc 비용 기준).
 * [lid]/[rid] 는 품사(좌/우 문맥) ID 로, [Connection] 비용 조회에 쓴다.
 */
data class DictEntry(
    val surface: String,
    val cost: Int,
    val lid: Int = 0,
    val rid: Int = 0,
    val isFunctionWord: Boolean = false,
)

interface Dictionary {
    /** 가장 긴 독음 길이. 공통 접두 탐색의 상한으로 쓴다. */
    val maxReadingLength: Int

    /** [reading] 과 정확히 일치하는 항목을 비용 오름차순으로 반환한다. [reading] 은 [normalizeReading] 된 값이어야 한다. */
    fun lookup(reading: String): List<DictEntry>

    companion object {
        /** 한글 입력으로 구분하기 어려운 ぢ/づ 를 じ/ず 로 통합한다. 사전 생성 스크립트와 동일한 규칙. */
        fun normalizeReading(reading: String): String =
            reading.replace('ぢ', 'じ').replace('づ', 'ず')
    }
}

/** 품사 연접 비용. 앞 단어의 [rid] 와 뒷 단어의 [lid] 사이 비용. ID 0 은 문장 시작/끝(BOS/EOS). */
fun interface Connection {
    fun cost(rid: Int, lid: Int): Int

    companion object {
        val NONE = Connection { _, _ -> 0 }
    }
}
