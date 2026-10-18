package me.algosketch.hanilkeyboard.conversion

import android.content.Context
import android.util.Log
import kotlin.concurrent.thread

/** 앱 에셋의 한자 해설 데이터를 백그라운드에서 한 번 읽어 둔다. 한자 해설을 켰을 때만 불러온다. */
object HanjaGlossaryRepository {
    private const val TAG = "HanjaGlossary"

    @Volatile
    var glossary: HanjaGlossary? = null
        private set

    @Volatile
    private var loading = false

    /** [onLoaded] 는 로더 스레드에서 호출된다. */
    fun loadAsync(context: Context, onLoaded: (HanjaGlossary) -> Unit = {}) {
        glossary?.let { onLoaded(it); return }
        if (loading) return
        loading = true
        val app = context.applicationContext
        thread(name = "hanja-loader") {
            try {
                val loaded = app.assets.open("dict/hanja_gloss.tsv.gzip").use { HanjaGlossary.load(it) }
                glossary = loaded
                onLoaded(loaded)
            } catch (e: Exception) {
                Log.e(TAG, "failed to load hanja glossary", e)
            } finally {
                loading = false
            }
        }
    }
}
