package com.wordecho.app.speech

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 系统 TTS 封装。
 * - 优先使用系统自带引擎（多数机型英文语音包已内置，可完全离线）
 * - 支持语速调节
 * - earpiece = true 时走 STREAM_VOICE_CALL，配合听筒路由实现「打电话时也能背」
 */
class TtsSpeaker(private val context: Context) {

    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    /** 本次朗读是否真的开始出声了（用来区分「引擎没装」和「正在读」） */
    @Volatile
    private var utterStarted = false

    private var pendingAction: (() -> Unit)? = null
    private var doneCallback: (() -> Unit)? = null
    private var utterSeq = 0

    private val main = Handler(Looper.getMainLooper())

    /**
     * 兜底：设备上没有任何 TTS 引擎（部分模拟器 / 精简 ROM）时，
     * TextToSpeech 初始化会一直不回调，或者 speak 后毫无动静。
     * 没有这两个看门狗，背诵流程会永久卡在「正在朗读…」，用户只能杀进程。
     */
    private val noStartWatchdog = Runnable {
        if (!utterStarted) {
            // 引擎压根没起来，直接放行，让流程进入「听你说」
            notifyDone()
        }
    }

    private var hardCapWatchdog: Runnable? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        utterStarted = true
                    }

                    override fun onDone(utteranceId: String?) = notifyDone()
                    override fun onError(utteranceId: String?) = notifyDone()
                })
                ready = true
                pendingAction?.invoke()
                pendingAction = null
            } else {
                // 初始化失败：保持 ready=false，让看门狗兜底
                ready = false
            }
        }
    }

    fun isReady(): Boolean = ready

    /** 当前是否支持离线英文 */
    fun englishOffline(): Boolean {
        val e = engine ?: return false
        val r = e.isLanguageAvailable(Locale.US)
        return r == TextToSpeech.LANG_AVAILABLE || r == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            r == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    fun speak(
        text: String,
        lang: Locale = Locale.US,
        rate: Float = 0.85f,
        earpiece: Boolean = false,
        offlineOnly: Boolean = true,
        onDone: () -> Unit = {}
    ) {
        doneCallback = onDone
        utterStarted = false
        armWatchdogs(text)
        if (!ready) {
            pendingAction = { doSpeak(text, lang, rate, earpiece, offlineOnly) }
        } else {
            doSpeak(text, lang, rate, earpiece, offlineOnly)
        }
    }

    /** 两个看门狗：1.5s 内没开始出声就放行；总时长超上限也放行 */
    private fun armWatchdogs(text: String) {
        main.removeCallbacks(noStartWatchdog)
        main.postDelayed(noStartWatchdog, 1500L)

        hardCapWatchdog?.let { main.removeCallbacks(it) }
        val cap = Runnable { notifyDone() }
        hardCapWatchdog = cap
        // 按字符数估个上限，最快 1.8s，最长 12s
        val capMs = (1800L + text.length * 160L).coerceAtMost(12_000L)
        main.postDelayed(cap, capMs)
    }

    private fun doSpeak(text: String, lang: Locale, rate: Float, earpiece: Boolean, offlineOnly: Boolean) {
        val e = engine
        if (e == null) {
            notifyDone()
            return
        }
        val avail = e.isLanguageAvailable(lang)
        e.setLanguage(
            if (avail == TextToSpeech.LANG_MISSING_DATA || avail == TextToSpeech.LANG_NOT_SUPPORTED) {
                Locale.US
            } else {
                lang
            }
        )
        e.setSpeechRate(rate.coerceIn(0.3f, 2.0f))
        val params = Bundle().apply {
            putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                if (earpiece) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
            )
            // 离线开关：禁止引擎走网络合成
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, if (offlineOnly) "false" else "true")
        }
        e.speak(text, TextToSpeech.QUEUE_FLUSH, params, "u${++utterSeq}")
    }

    fun stop() {
        doneCallback = null
        pendingAction = null
        main.removeCallbacks(noStartWatchdog)
        hardCapWatchdog?.let { main.removeCallbacks(it) }
        hardCapWatchdog = null
        runCatching { engine?.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    private fun notifyDone() {
        main.post {
            main.removeCallbacks(noStartWatchdog)
            hardCapWatchdog?.let { main.removeCallbacks(it) }
            hardCapWatchdog = null
            val cb = doneCallback
            doneCallback = null
            cb?.invoke()
        }
    }
}
