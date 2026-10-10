package me.algosketch.hanilkeyboard.conversion

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/**
 * 앱 에셋의 사전·연접 행렬을 내부 저장소(noBackupFilesDir)에 한 번 풀어 두고 메모리 맵으로 연다.
 * 모든 처리는 기기 내부에서만 이루어지며 네트워크를 사용하지 않는다.
 */
object LanguageModelRepository {
    private const val TAG = "LanguageModel"

    @Volatile
    var model: LanguageModel? = null
        private set

    @Volatile
    private var loading = false

    fun loadAsync(context: Context, onLoaded: () -> Unit = {}) {
        if (model != null || loading) return
        loading = true
        val app = context.applicationContext
        thread(name = "dict-loader") {
            try {
                val dict = BinaryDictionary(map(app, "dict/ja_dict.bin.gzip", "ja_dict.bin"))
                val conn = BinaryConnection(map(app, "dict/connection.bin.gzip", "connection.bin"))
                model = LanguageModel(dict, conn)
                onLoaded()
            } catch (e: Exception) {
                Log.e(TAG, "failed to load dictionary", e)
            } finally {
                loading = false
            }
        }
    }

    private fun map(context: Context, asset: String, name: String): ByteBuffer {
        val dir = File(context.noBackupFilesDir, "dict").apply { mkdirs() }
        val target = File(dir, name)
        val stampFile = File(dir, "$name.stamp")
        val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()

        if (!target.exists() || !stampFile.exists() || stampFile.readText() != stamp) {
            val tmp = File(dir, "$name.tmp")
            GZIPInputStream(context.assets.open(asset).buffered()).use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                check(tmp.renameTo(target)) { "cannot write $target" }
            }
            stampFile.writeText(stamp)
        }

        return RandomAccessFile(target, "r").use { file ->
            file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }
}
