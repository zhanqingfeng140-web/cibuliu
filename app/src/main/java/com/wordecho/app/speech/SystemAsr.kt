package com.wordecho.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 系统语音识别（android.speech.SpeechRecognizer）。
 *
 * 为什么要有这个：Vosk 小模型要 42MB 还得手动导入，导入不了就直接退化成键盘输入，
 * 而用户要的是「像打电话一样」全语音。系统识别零体积、装完即用，
 * 国产 ROM 多半接了讯飞/百度，中文短句准确率明显好于 Vosk small。
 *
 * 代价：部分实现会把音频送到厂商云端，不是纯离线。所以设置里保留了
 * 「离线 Vosk」选项，介意隐私的人可以切过去。
 *
 * 关键点：说完自动停（onEndOfSpeech / onResults），正好贴合「一问一答」的节奏，
 * 不需要用户点按钮结束录音。
 */
class SystemAsr(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    /** 本轮是否检测到人开始说话 */
    @Volatile
    private var beganSpeech = false

    private val main = Handler(Looper.getMainLooper())

    /** 3.5 秒都没开始说话 → 判定麦克风没声音 */
    private val noSpeechCheck = Runnable {
        if (!beganSpeech) cbError?.invoke("麦克风没有声音")
    }
    private var cbPartial: ((String) -> Unit)? = null
    private var cbFinal: ((String) -> Unit)? = null
    private var cbError: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = runCatching {
        SpeechRecognizer.isRecognitionAvailable(context)
    }.getOrDefault(false)

    fun start(
        langTag: String = "zh-CN",
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        cbPartial = onPartial
        cbFinal = onFinal
        cbError = onError
        beganSpeech = false

        releaseRecognizer()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            cbError?.invoke("系统语音识别不可用")
            return
        }
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() {
                beganSpeech = true
            }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                main.removeCallbacks(noSpeechCheck)
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没听清"
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音服务需要网络"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别忙"
                    SpeechRecognizer.ERROR_CLIENT -> "识别被中断"
                    else -> "识别失败($error)"
                }
                cbError?.invoke(msg)
            }

            override fun onResults(results: Bundle?) {
                main.removeCallbacks(noSpeechCheck)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) cbError?.invoke("没听清") else cbFinal?.invoke(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) cbPartial?.invoke(text)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // 缩短「说完」的静音判定，否则要干等好几秒
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                900L
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

        runCatching { r.startListening(intent) }
            .onFailure { cbError?.invoke("启动识别失败") }

        main.removeCallbacks(noSpeechCheck)
        main.postDelayed(noSpeechCheck, 3500L)
    }

    fun stop() {
        main.removeCallbacks(noSpeechCheck)
        // 先摘掉回调，避免 cancel 触发的 onError 又被当成「没听清」
        cbPartial = null
        cbFinal = null
        cbError = null
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.cancel() }
        releaseRecognizer()
    }

    fun release() = stop()

    private fun releaseRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }
}
