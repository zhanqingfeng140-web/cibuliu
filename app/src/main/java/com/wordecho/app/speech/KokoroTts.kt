package com.wordecho.app.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest

/**
 * 端侧 Kokoro 语音合成。
 *
 * 为什么是 Kokoro 而不是别的：它只吃音素、不吃文字，音素表是纯 JSON →
 * 整条链路不需要 NDK 编译（Piper 依赖 espeak-ng 的 C 库，Android 上得自己编）。
 * 代价是模型 82MB、手机上单次合成约 0.5–2 秒，所以配了磁盘缓存：
 * 同一个词第二次是 0 延迟，背单词场景一个词通常要听两三遍。
 *
 * 失败一律返回 null，由 TtsSpeaker 退回系统 TTS —— 不能因为发音引擎
 * 出问题就让整个背诵流程哑掉。
 */
class KokoroTts(private val context: Context, private val store: KokoroModelStore) {

    /** 是否启用（设置里的开关，由 MainActivity 同步过来） */
    @Volatile
    var enabled: Boolean = false

    /** uk / us，跟着设置里的口音走；换音色不用重建 session */
    @Volatile
    var accent: String = "us"

    @Volatile
    private var loadedAccent: String = ""

    @Volatile
    private var session: OrtSession? = null

    @Volatile
    private var env: OrtEnvironment? = null

    @Volatile
    private var lexicon: KokoroLexicon? = null

    @Volatile
    private var voice: FloatArray? = null

    /** 加载或推理失败一次就别再试了，避免每个词都卡一轮 */
    @Volatile
    private var broken = false

    private val loadLock = Mutex()
    private val runLock = Mutex()

    fun isInstalled(): Boolean = store.isReady()

    /** 当前能不能真的拿去发音：开关打开 + 模型在 + 没坏过 */
    fun usable(): Boolean = enabled && !broken && store.isReady()

    fun isReady(): Boolean = session != null && !broken

    /** 这个单词能不能读（查不到音素就要走系统 TTS） */
    fun canRead(text: String): Boolean = runCatching {
        lexicon?.phonemes(text) != null
    }.getOrDefault(false)

    fun phonemesOf(text: String): String? = runCatching { lexicon?.phonemes(text) }.getOrNull()

    /**
     * 加载模型 + 词典 + 音色。首次调用几秒，之后直接命中。
     * 放在 IO 线程，调用方每次 speak 前 await 一下也不会疼。
     */
    suspend fun ensureLoaded(): Boolean = withContext(Dispatchers.IO) {
        if (broken) return@withContext false
        if (session != null && loadedAccent == accent) return@withContext true
        loadLock.withLock {
            if (broken) return@withLock false
            if (session == null) {
                if (!store.isReady()) return@withLock false
                val ok = runCatching {
                    val e = OrtEnvironment.getEnvironment()
                    val opts = OrtSession.SessionOptions().apply {
                        // 手机 8 核，给 4 核足够；再多会跟别的任务抢
                        setIntraOpNumThreads(4)
                        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    }
                    val s = e.createSession(store.file().absolutePath, opts)
                    env = e
                    session = s
                    lexicon = KokoroLexicon.load(context)
                }
                if (ok.isFailure) {
                    broken = true
                    session = null
                    return@withLock false
                }
            }
            if (loadedAccent != accent || voice == null) {
                val v = runCatching { readVoice(accent) }.getOrNull()
                if (v == null) {
                    broken = true
                    return@withLock false
                }
                voice = v
                loadedAccent = accent
                // 热身：第一次推理要分配 arena，先跑一个短词，
                // 免得用户听到的第一个词等五六秒
                val lex = lexicon
                if (lex != null) runCatching { infer(lex.phonemes("a") ?: "a", 1.0f) }
            }
            true
        }
    }

    /**
     * 合成到缓存文件。命中缓存直接返回，不发一个算子。
     * 返回 null 表示这个词读不了（没收录音素 / 模型没装 / 推理出错）。
     */
    suspend fun synthesize(text: String, speed: Float = 1.0f): File? = withContext(Dispatchers.IO) {
        val word = text.trim()
        if (word.isEmpty()) return@withContext null
        val target = cacheFile(word, speed)
        if (target.exists() && target.length() > 100) {
            target.setLastModified(System.currentTimeMillis())
            return@withContext target
        }
        if (!ensureLoaded()) return@withContext null
        val lex = lexicon ?: return@withContext null
        val ph = lex.phonemes(word) ?: return@withContext null
        val audio = runCatching { infer(ph, speed) }.getOrElse {
            broken = true
            return@withContext null
        }
        if (audio.isEmpty()) return@withContext null
        runCatching {
            writeWav(target, audio)
            trimCache()
        }
        if (target.exists()) target else null
    }

    /**
     * 预取：进入答题就把这一轮的词全部排进后台，用户听到时多半已经好了。
     * 串行跑，不跟正在播的那个抢 CPU。
     */
    suspend fun prefetch(words: List<String>, speed: Float = 1.0f) {
        if (!enabled || broken || !store.isReady()) return
        for (w in words) {
            if (broken) break
            runCatching { synthesize(w, speed) }
        }
    }

    fun release() {
        runCatching { session?.close() }
        session = null
        lexicon = null
        voice = null
    }

    // ---- 内部 ----

    /** 音素串 → 模型 id 序列，首尾各补一个 0（Kokoro 的起止符） */
    private fun infer(ph: String, speed: Float): FloatArray {
        val s = session ?: return FloatArray(0)
        val e = env ?: return FloatArray(0)
        val v = voice ?: return FloatArray(0)
        val lex = lexicon ?: return FloatArray(0)

        val ids = LongArray(ph.length + 2)
        var n = 1
        for (c in ph) {
            val id = lex.idOf(c) ?: continue
            ids[n++] = id.toLong()
        }
        ids[n++] = 0L
        val seq = ids.copyOf(n)

        // 音色向量的行下标跟着音素长度走（和 Kokoro 官方实现一致）
        val row = (ph.length - 1).coerceIn(0, v.size / STYLE_DIM - 1)
        val style = FloatArray(STYLE_DIM)
        System.arraycopy(v, row * STYLE_DIM, style, 0, STYLE_DIM)

        var idsT: OnnxTensor? = null
        var styleT: OnnxTensor? = null
        var speedT: OnnxTensor? = null
        var result: OrtSession.Result? = null
        try {
            idsT = OnnxTensor.createTensor(e, LongBuffer.wrap(seq), longArrayOf(1, seq.size.toLong()))
            styleT = OnnxTensor.createTensor(e, FloatBuffer.wrap(style), longArrayOf(1, STYLE_DIM.toLong()))
            speedT = OnnxTensor.createTensor(
                e, FloatBuffer.wrap(floatArrayOf(speed.coerceIn(0.5f, 1.5f))), longArrayOf(1)
            )
            result = s.run(mapOf("input_ids" to idsT, "style" to styleT, "speed" to speedT))
            val out = result[0].value
            val grid = out as? Array<*> ?: return FloatArray(0)
            val first = grid.firstOrNull() as? FloatArray ?: return FloatArray(0)
            return first
        } finally {
            runCatching { result?.close() }
            runCatching { idsT?.close() }
            runCatching { styleT?.close() }
            runCatching { speedT?.close() }
        }
    }

    private fun readVoice(accent: String): FloatArray {
        // 英式 bf_emma、美式 af_heart，都是 Kokoro 官方音色里评分较高的女声
        val name = if (accent == "uk") "bf_emma.bin" else "af_heart.bin"
        val bytes = context.assets.open("kokoro/$name").use { it.readBytes() }
        require(bytes.size % (4 * STYLE_DIM) == 0) { "音色文件形状不对" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val arr = FloatArray(bytes.size / 4)
        bb.asFloatBuffer().get(arr)
        return arr
    }

    private fun cacheFile(word: String, speed: Float): File {
        val dir = File(context.filesDir, "kokoro_cache").apply { mkdirs() }
        val key = word.trim().lowercase() + "|" + "%.2f".format(speed) + "|" + accent
        val md = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        val name = md.joinToString("") { "%02x".format(it) } + ".wav"
        return File(dir, name)
    }

    /** 缓存上限 3000 条（约 3000 × 25KB ≈ 75MB），超了删最久没听的 */
    private fun trimCache() {
        val dir = File(context.filesDir, "kokoro_cache")
        val files = dir.listFiles() ?: return
        if (files.size <= CACHE_LIMIT) return
        files.sortBy { it.lastModified() }
        files.take(files.size - CACHE_LIMIT).forEach { runCatching { it.delete() } }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val pcm = ShortArray(samples.size)
        for (i in samples.indices) {
            pcm[i] = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        val bytes = pcm.size * 2
        val head = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }
        val body = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            asShortBuffer().put(pcm)
        }
        // 先写临时文件再改名，避免半截 wav 被当成缓存命中
        val tmp = File(file.absolutePath + ".tmp")
        FileOutputStream(tmp).use {
            it.write(head.array())
            it.write(body.array())
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val SAMPLE_RATE = 24000
        private const val STYLE_DIM = 256
        private const val CACHE_LIMIT = 3000
    }
}
