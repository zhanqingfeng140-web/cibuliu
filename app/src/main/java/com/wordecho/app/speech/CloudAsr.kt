package com.wordecho.app.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云端语音识别（OpenAI 兼容的 /audio/transcriptions 接口）。
 *
 * 为什么需要它：Vosk small 中文模型对带口音/连读的短句识别率一般，
 * 系统识别在国产 ROM 上又常常不可用。云端 ASR 是目前唯一能稳定听懂人话的路子。
 *
 * 兼容对象（填不同的 baseUrl 即可）：
 *   - 硅基流动   https://api.siliconflow.cn/v1   模型 FunAudioLLM/SenseVoiceSmall
 *   - OpenAI    https://api.openai.com/v1        模型 whisper-1
 *   - 其他任何 OpenAI 兼容网关
 *
 * 做法：整段录下来（静音 1.1 秒判定说完）→ 编码 WAV → 一次性上传。
 * 延迟大概 1~2 秒，比流式差一点，但换来的是识别质量。
 */
class CloudAsr(private val context: Context) {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    fun start(
        baseUrl: String,
        apiKey: String,
        model: String,
        langTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stopInternal()

        val minBuf = runCatching {
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        }.getOrDefault(0)
        if (minBuf <= 0) {
            onError("麦克风不支持 16k 录音")
            return
        }

        val ar = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()
        }.getOrNull()

        if (ar == null || ar.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { ar?.release() }
            onError("麦克风初始化失败")
            return
        }

        record = ar
        running = true
        onPartial("正在听…")

        worker = Thread {
            val pcm = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            var lastVoiceAt = System.currentTimeMillis()
            var heardVoice = false
            val startedAt = System.currentTimeMillis()

            runCatching { ar.startRecording() }

            while (running) {
                val n = try {
                    ar.read(buf, 0, buf.size)
                } catch (e: Throwable) {
                    -1
                }
                if (n < 0) break
                if (n > 0) {
                    pcm.write(buf, 0, n)
                    val now = System.currentTimeMillis()
                    if (rms(buf, n) > RMS_SPEECH) {
                        lastVoiceAt = now
                        heardVoice = true
                    }

                    // 3.5 秒没听到人声，直接报错，别录一段静音去浪费请求
                    if (!heardVoice && now - startedAt > 3500) {
                        onError("麦克风没有声音")
                        break
                    }

                    // 说完静音 1.1 秒，或者录满 15 秒，就上传
                    val silenceDone = heardVoice && now - lastVoiceAt > 1100
                    val tooLong = now - startedAt > 15_000
                    if (silenceDone || tooLong) break
                }
            }

            val audio = pcm.toByteArray()
            stopInternal()

            if (!heardVoice) return@Thread
            if (audio.size < SAMPLE_RATE) {
                onError("录音太短")
                return@Thread
            }

            onPartial("识别中…")
            val text = runCatching {
                upload(toWav(audio), baseUrl, apiKey, model, langTag)
            }.getOrNull()

            if (text.isNullOrBlank()) {
                onError("云端识别失败")
            } else {
                onFinal(text.trim())
            }
        }.also { it.start() }
    }

    fun stop() = stopInternal()

    fun release() = stopInternal()

    private fun stopInternal() {
        running = false
        runCatching { record?.stop() }
        val w = worker
        worker = null
        if (w != null && w !== Thread.currentThread()) runCatching { w.join(200) }
        runCatching { record?.release() }
        record = null
    }

    // ---------------- 上传 ----------------

    private fun upload(
        wav: ByteArray,
        baseUrl: String,
        apiKey: String,
        model: String,
        langTag: String
    ): String? {
        val url = URL(baseUrl.trimEnd('/') + "/audio/transcriptions")
        val boundary = "----WordEcho" + System.currentTimeMillis()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(conn.outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("model", model)
            field("language", if (langTag.startsWith("en")) "en" else "zh")
            field("response_format", "json")

            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"a.wav\"\r\n")
            out.writeBytes("Content-Type: audio/wav\r\n\r\n")
            out.write(wav)
            out.writeBytes("\r\n--$boundary--\r\n")
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) return null
        return runCatching { JSONObject(body).optString("text", "") }.getOrDefault("")
    }

    /** 16bit PCM 加 44 字节 WAV 头 */
    private fun toWav(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val dataLen = pcm.size
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray()); le32(dataLen + 36)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
        le32(SAMPLE_RATE); le32(SAMPLE_RATE * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(dataLen)
        out.write(pcm)
        return out.toByteArray()
    }

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

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val RMS_SPEECH = 450.0
    }
}
