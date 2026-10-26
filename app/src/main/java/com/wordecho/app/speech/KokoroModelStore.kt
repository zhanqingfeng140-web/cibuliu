package com.wordecho.app.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kokoro 模型文件的存放与下载。
 *
 * 模型 82MB，刻意**不**打进 APK：APK 从 70MB 涨到 150MB+ 去换一个可选功能不划算，
 * 用户此前也为了体积专门删掉过 OCR。改成「要用才下」，下载进度走 StateFlow。
 *
 * 支持断点续传：83MB 在手机网络下断一次很常见，从 0 重来体验太差。
 */
class KokoroModelStore(private val context: Context) {

    sealed interface State {
        data object Missing : State
        data class Downloading(val done: Long, val total: Long) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val dir: File get() = File(context.filesDir, "kokoro").apply { mkdirs() }
    private val partFile: File get() = File(dir, "model.onnx.part")
    private val modelFile: File get() = File(dir, "model.onnx")

    private val _state = MutableStateFlow<State>(
        if (modelFile.exists()) State.Ready else State.Missing
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun isReady(): Boolean = modelFile.exists() && modelFile.length() > MIN_SIZE

    fun file(): File = modelFile

    fun usedBytes(): Long = if (modelFile.exists()) modelFile.length() else partFile.length()

    fun delete() {
        runCatching { modelFile.delete() }
        runCatching { partFile.delete() }
        _state.value = State.Missing
    }

    /** 下载或续传；返回 true 表示可用 */
    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        if (isReady()) {
            _state.value = State.Ready
            return@withContext true
        }
        var lastError = "下载失败"
        for (url in URLS) {
            val ok = runCatching { fetch(url) }.getOrElse { e ->
                lastError = e.message ?: "网络异常"
                false
            }
            if (ok) {
                _state.value = State.Ready
                return@withContext true
            }
        }
        _state.value = State.Failed(lastError)
        false
    }

    private fun fetch(url: String): Boolean {
        val existing = partFile.length()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 40_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WordEcho/Android")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("服务器返回 $code")
            }
            val remain = conn.contentLengthLong
            val total = if (append) existing + remain else remain
            var done = if (append) existing else 0L
            if (!append) runCatching { partFile.delete() }

            _state.value = State.Downloading(done, total)
            conn.inputStream.use { input ->
                FileOutputStream(partFile, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // 每 512KB 更新一次进度，避免刷爆 Compose 重组
                        if (done - lastEmit > 512 * 1024) {
                            lastEmit = done
                            _state.value = State.Downloading(done, total)
                        }
                    }
                    out.flush()
                }
            }

            if (partFile.length() <= MIN_SIZE) {
                partFile.delete()
                throw IllegalStateException("文件不完整")
            }
            if (!partFile.renameTo(modelFile)) {
                partFile.copyTo(modelFile, overwrite = true)
                partFile.delete()
            }
            _state.value = State.Downloading(modelFile.length(), modelFile.length())
            return true
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    companion object {
        /** 低于这个长度肯定不是完整模型（实际 86,033,585 字节） */
        private const val MIN_SIZE = 70L * 1024 * 1024

        /** q8f16 质量最好；换成 int8 全量化再试一次，两者输入输出签名一致 */
        private val URLS = listOf(
            "https://hf-mirror.com/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_q8f16.onnx",
            "https://hf-mirror.com/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/main/onnx/model_quantized.onnx"
        )
    }
}
