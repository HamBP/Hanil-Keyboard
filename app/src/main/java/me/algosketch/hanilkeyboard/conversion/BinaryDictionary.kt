package me.algosketch.hanilkeyboard.conversion

import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/**
 * tools/dictgen/build_dictionary.py 가 만든 사전(Mozc OSS 사전 기반)을 읽는다.
 * 실행 중에는 압축 해제된 파일을 메모리 맵([ByteBuffer])으로 열어 힙을 거의 쓰지 않는다.
 *
 * 포맷(빅엔디언):
 *  "HKD2", nReadings, nEntries, readingCharsLen, surfaceCharsLen, maxReadingLen (int)
 *  readingOff[nReadings+1], entryStart[nReadings+1], surfaceOff[nEntries+1] (int)
 *  cost[nEntries], lid[nEntries], rid[nEntries] (short), flags[nEntries] (byte)
 *  readingChars, surfaceChars (UTF-16)
 * 독음은 정렬되어 있어 이진 탐색으로 조회한다.
 */
class BinaryDictionary(private val buf: ByteBuffer) : Dictionary {

    private val readingCountValue: Int
    private val entryCount: Int
    override val maxReadingLength: Int

    private val readingOffPos: Int
    private val entryStartPos: Int
    private val surfaceOffPos: Int
    private val costPos: Int
    private val lidPos: Int
    private val ridPos: Int
    private val flagsPos: Int
    private val readingCharsPos: Int
    private val surfaceCharsPos: Int

    init {
        require(buf.getInt(0) == MAGIC) { "invalid dictionary" }
        readingCountValue = buf.getInt(4)
        entryCount = buf.getInt(8)
        val readingLen = buf.getInt(12)
        maxReadingLength = buf.getInt(20)
        readingOffPos = 24
        entryStartPos = readingOffPos + (readingCountValue + 1) * 4
        surfaceOffPos = entryStartPos + (readingCountValue + 1) * 4
        costPos = surfaceOffPos + (entryCount + 1) * 4
        lidPos = costPos + entryCount * 2
        ridPos = lidPos + entryCount * 2
        flagsPos = ridPos + entryCount * 2
        readingCharsPos = flagsPos + entryCount
        surfaceCharsPos = readingCharsPos + readingLen * 2
    }

    val readingCount: Int get() = readingCountValue

    override fun lookup(reading: String): List<DictEntry> {
        val idx = find(reading)
        if (idx < 0) return emptyList()
        val from = buf.getInt(entryStartPos + idx * 4)
        val to = buf.getInt(entryStartPos + (idx + 1) * 4)
        return (from until to).map { e ->
            DictEntry(
                surface = surface(e),
                cost = buf.getShort(costPos + e * 2).toInt(),
                lid = buf.getShort(lidPos + e * 2).toInt(),
                rid = buf.getShort(ridPos + e * 2).toInt(),
                isFunctionWord = (buf.get(flagsPos + e).toInt() and FLAG_FUNCTION_WORD) != 0,
            )
        }
    }

    private fun surface(e: Int): String {
        val start = buf.getInt(surfaceOffPos + e * 4)
        val end = buf.getInt(surfaceOffPos + (e + 1) * 4)
        val chars = CharArray(end - start) { buf.getChar(surfaceCharsPos + (start + it) * 2) }
        return String(chars)
    }

    private fun find(key: String): Int {
        var lo = 0
        var hi = readingCountValue - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(mid, key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    private fun compare(index: Int, key: String): Int {
        val start = buf.getInt(readingOffPos + index * 4)
        val len = buf.getInt(readingOffPos + (index + 1) * 4) - start
        val n = minOf(len, key.length)
        for (i in 0 until n) {
            val d = buf.getChar(readingCharsPos + (start + i) * 2) - key[i]
            if (d != 0) return d
        }
        return len - key.length
    }

    companion object {
        private const val FLAG_FUNCTION_WORD = 1
        private const val MAGIC = 0x484B4432 // "HKD2"

        /** gzip 스트림을 힙에 풀어서 연다. (테스트용, 앱은 [DictionaryRepository] 의 메모리 맵 사용) */
        fun load(gzipped: InputStream): BinaryDictionary =
            BinaryDictionary(ByteBuffer.wrap(GZIPInputStream(gzipped.buffered()).use { it.readBytes() }))
    }
}

/**
 * Mozc 연접 비용 행렬(1바이트 양자화).
 * 포맷: "HKC1", n, step (int), matrix[rid * n + lid] (unsigned byte, 실제 비용 = 값 * step)
 */
class BinaryConnection(private val buf: ByteBuffer) : Connection {
    private val size: Int
    private val step: Int

    init {
        require(buf.getInt(0) == MAGIC) { "invalid connection matrix" }
        size = buf.getInt(4)
        step = buf.getInt(8)
    }

    override fun cost(rid: Int, lid: Int): Int {
        if (rid !in 0 until size || lid !in 0 until size) return 0
        return (buf.get(HEADER + rid * size + lid).toInt() and 0xFF) * step
    }

    companion object {
        private const val MAGIC = 0x484B4331 // "HKC1"
        private const val HEADER = 12

        fun load(gzipped: InputStream): BinaryConnection =
            BinaryConnection(ByteBuffer.wrap(GZIPInputStream(gzipped.buffered()).use { it.readBytes() }))
    }
}
