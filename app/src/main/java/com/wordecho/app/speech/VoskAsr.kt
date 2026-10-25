package com.wordecho.app.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream

/**
 * 端侧离线语音识别（Vosk）。
 *
 * 模型放在 assets/vosk-cn 或 assets/vosk-en 目录下（把解压后的模型内容整个丢进去），
 * 首次使用会拷贝到 filesDir，之后完全离线运行。
 *
 * 没有放模型也不会崩，只是会返回「模型未就绪」，此时可以走键盘输入。
 */
class VoskAsr(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val LANG_CN = "cn"
        const val LANG_EN = "en"

        /** 说完后静音多久算「这句讲完了」 */
        private const val SILENCE_MS = 1100L

        /** 16bit PCM 的语音阈值，低于这个当作环境噪声 */
        private const val RMS_SPEECH = 450.0
    }

    private var model: Model? = null
    private var modelLang: String? = null

    private var recognizer: Recognizer? = null
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var loading = false

    private fun modelDir(lang: String) = File(context.filesDir, "vosk-$lang")

    fun isReady(lang: String): Boolean = model != null && modelLang == lang

    fun isModelPresent(lang: String): Boolean {
        val d = modelDir(lang)
        if (File(d, "am").exists() || File(d, "conf").exists()) return true
        // 只认 am/ 子目录，避免 assets 里放了说明文件就被误判为「已就绪」
        return runCatching {
            context.assets.list("vosk-$lang/am")?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    /** 加载模型（后台线程），回调会在工作线程触发 */
    fun ensureModel(lang: String, onResult: (Boolean, String) -> Unit) {
        if (isReady(lang)) {
            onResult(true, "")
            return
        }
        if (loading) {
            onResult(false, "语音模型正在加载，请稍候…")
            return
        }
        loading = true
        Thread {
            try {
                unloadModel()
                val dir = modelDir(lang)
                if (!File(dir, "am").exists() && !File(dir, "conf").exists()) {
                    copyAssetDir("vosk-$lang", dir)
                }
                if (!File(dir, "am").exists() && !File(dir, "conf").exists()) {
                    loading = false
                    onResult(false, "缺少离线语音模型 vosk-$lang（放到 assets/ 下即可）")
                    return@Thread
                }
                model = Model(dir.absolutePath)
                modelLang = lang
                loading = false
                onResult(true, "")
            } catch (e: Throwable) {
                loading = false
                onResult(false, e.message ?: "语音模型加载失败")
            }
        }.start()
    }

    /**
     * 开始持续监听。
     * @param source MediaRecorder.AudioSource.MIC 或 VOICE_COMMUNICATION（听筒模式）
     */
    fun start(
        source: Int,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val m = model
        if (m == null) {
            onError("语音模型未就绪")
            return
        }
        stopInternal()
        try {
            val rec = Recognizer(m, SAMPLE_RATE.toFloat())
            recognizer = rec

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096) * 2

            val ar = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .build()

            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                onError("麦克风初始化失败")
                return
            }

            record = ar
            ar.startRecording()
            running = true

            worker = Thread {
                val buf = ByteArray(4096)
                // 累积已经"成句"的片段，避免长句被切碎后只取到最后一段
                val settled = StringBuilder()
                var lastVoiceAt = System.currentTimeMillis()
                var submitted = false
                var heardVoice = false
                var warnedNoVoice = false
                val startedAt = System.currentTimeMillis()

                while (running && !submitted) {
                    val n = try {
                        ar.read(buf, 0, buf.size)
                    } catch (e: Throwable) {
                        -1
                    }
                    if (n < 0) break
                    if (n == 0) continue

                    val now = System.currentTimeMillis()
                    if (rms(buf, n) > RMS_SPEECH) {
                        lastVoiceAt = now
                        heardVoice = true
                    }

                    // 3.5 秒完全没检测到人声 → 明确报出来，别让用户对着手机干等
                    if (!heardVoice && !warnedNoVoice && now - startedAt > 3500) {
                        warnedNoVoice = true
                        onError("麦克风没有声音")
                        break
                    }

                    try {
                        if (rec.acceptWaveForm(buf, n)) {
                            // 检测到一句结束：把这段并入 settled
                            val t = pick(rec.result, "text")
                            if (t.isNotBlank()) {
                                settled.append(t)
                                lastVoiceAt = now
                                onPartial(settled.toString())
                            }
                        } else {
                            val p = pick(rec.partialResult, "partial")
                            if (p.isNotBlank()) lastVoiceAt = now
                        }

                        // 关键：不是一有结果就提交，而是等「说完后静音 1.1 秒」
                        val live = pick(rec.partialResult, "partial")
                        val full = settled.toString() + live
                        if (full.isNotBlank()) onPartial(full)

                        if (full.isNotBlank() && now - lastVoiceAt > SILENCE_MS) {
                            submitted = true
                            onFinal(full.trim())
                        }
                    } catch (e: Throwable) {
                        break
                    }
                }

                // 被 stop() 打断时，把已听到的内容兜底交出去，别白说
                if (!submitted) {
                    val tail = settled.toString().trim()
                    if (tail.isNotEmpty()) {
                        submitted = true
                        onFinal(tail)
                    }
                }
            }.also { it.start() }
        } catch (e: Throwable) {
            onError(e.message ?: "麦克风启动失败")
        }
    }

    /** 16bit PCM 的均方根，用来区分「有人说话」和「环境噪声」 */
    private fun rms(buf: ByteArray, n: Int): Double {
        var sum = 0.0
        var i = 0
        var count = 0
        while (i + 1 < n) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort()
            sum += s.toDouble() * s
            count++
            i += 2
        }
        if (count == 0) return 0.0
        return kotlin.math.sqrt(sum / count)
    }

    fun stop() = stopInternal()

    private fun stopInternal() {
        running = false
        runCatching { record?.stop() }
        val w = worker
        worker = null
        if (w != null && w !== Thread.currentThread()) {
            runCatching { w.join(300) }
        }
        runCatching { record?.release() }
        record = null
        runCatching { recognizer?.close() }
        recognizer = null
    }

    private fun unloadModel() {
        stopInternal()
        runCatching { model?.close() }
        model = null
        modelLang = null
    }

    fun release() {
        unloadModel()
    }

    private fun pick(json: String?, key: String): String =
        runCatching { JSONObject(json ?: "").optString(key, "") }.getOrDefault("")

    /** 把 assets 里的整个模型目录拷到私有目录 */
    private fun copyAssetDir(assetPath: String, target: File) {
        val children = runCatching { context.assets.list(assetPath) }.getOrNull()
        if (children == null || children.isEmpty()) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out -> input.copyTo(out, 64 * 1024) }
                }
            }
            return
        }
        target.mkdirs()
        for (c in children) {
            copyAssetDir("$assetPath/$c", File(target, c))
        }
    }
}
