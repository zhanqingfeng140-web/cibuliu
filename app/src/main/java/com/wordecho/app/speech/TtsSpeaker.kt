package com.wordecho.app.speech

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 朗读统一入口。两条腿：
 *
 *  - **Kokoro**（设置里可开）：读任意单词，音素表查得到就行，不依赖预生成音频。
 *  - **系统 TTS**（默认）：零体积，但国产 ROM 的英文引擎普遍带中式口音。
 *
 * 什么时候退回系统 TTS：没开 Kokoro / 模型没下 / 这个词不在 18 万词音素表里 /
 * 推理出异常。降级时不做任何提示，用户最多觉得「这个词声音不太一样」，
 * 不会出现哑掉或者卡住。
 */
class TtsSpeaker(
    private val context: Context,
    private val kokoro: KokoroTts? = null,
    private val scope: CoroutineScope? = null
) {

    private var engine: TextToSpeech? = null
    private val player = WavPlayer()

    @Volatile
    private var ready = false

    /** 本次朗读是否真的开始出声了（用来区分「引擎没装」和「正在读」） */
    @Volatile
    private var utterStarted = false

    private var pendingAction: (() -> Unit)? = null
    private var doneCallback: (() -> Unit)? = null
    private var utterSeq = 0
    private var kokoroJob: Job? = null

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

    /** 设备上报的英文语音质量，给设置页做诊断用：0 没装，>0 越大越好 */
    fun englishVoiceQuality(lang: Locale): Int = runCatching {
        engine?.voices
            ?.filter { it.locale.language == lang.language }
            ?.maxOfOrNull { it.quality }
            ?.let { if (it == 0) 1 else it }
            ?: 0
    }.getOrDefault(0)

    /** 系统里到底有没有英文语音包。很多国产 ROM 压根没装，会用中文引擎硬读 */
    fun hasEnglishVoice(lang: Locale): Boolean = englishVoiceQuality(lang) > 0

    fun speak(
        text: String,
        lang: Locale = Locale.US,
        rate: Float = 0.85f,
        earpiece: Boolean = false,
        offlineOnly: Boolean = true,
        onDone: () -> Unit = {}
    ) {
        val k = kokoro
        val canKokoro = k != null && scope != null && k.usable() && lang.language == "en"
        if (canKokoro) {
            speakKokoro(text, rate, earpiece, onDone)
        } else {
            speakSystem(text, lang, rate, earpiece, offlineOnly, onDone)
        }
    }

    // ---- Kokoro 分支 ----

    private fun speakKokoro(text: String, rate: Float, earpiece: Boolean, onDone: () -> Unit) {
        val k = kokoro
        val sc = scope
        if (k == null || sc == null) {
            speakSystem(text, Locale.US, rate, earpiece, true, onDone)
            return
        }
        doneCallback = onDone
        utterStarted = false
        main.removeCallbacks(noStartWatchdog)
        clearCap()

        // 合成 + 播放的总上限。低端机单次合成可能 2 秒，留够余量但不至于卡死流程
        val cap = Runnable { notifyDone() }
        hardCapWatchdog = cap
        main.postDelayed(cap, 25_000L)

        kokoroJob?.cancel()
        kokoroJob = sc.launch {
            val file = k.synthesize(text, rate)
            if (!isActive) return@launch
            withContext(Dispatchers.Main) {
                if (file == null) {
                    // 音素表里没有，或者模型读不出来 —— 退回系统引擎
                    main.removeCallbacks(cap)
                    speakSystem(text, Locale.US, rate, earpiece, true, doneCallback ?: {})
                } else {
                    player.play(file, earpiece) { notifyDone() }
                }
            }
        }
    }

    // ---- 系统 TTS 分支 ----

    private fun speakSystem(
        text: String,
        lang: Locale,
        rate: Float,
        earpiece: Boolean,
        offlineOnly: Boolean,
        onDone: () -> Unit
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

        clearCap()
        val cap = Runnable { notifyDone() }
        hardCapWatchdog = cap
        // 按字符数估个上限，最快 1.8s，最长 12s
        val capMs = (1800L + text.length * 160L).coerceAtMost(12_000L)
        main.postDelayed(cap, capMs)
    }

    private fun clearCap() {
        hardCapWatchdog?.let { main.removeCallbacks(it) }
        hardCapWatchdog = null
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

    // ---- 控制 ----

    fun stop() {
        doneCallback = null
        pendingAction = null
        kokoroJob?.cancel()
        kokoroJob = null
        main.removeCallbacks(noStartWatchdog)
        clearCap()
        player.stop()
        runCatching { engine?.stop() }
    }

    /** 只是把当前播放音量掐掉，不动回调（用在开麦前，避免录到自己的声音） */
    fun duck() {
        player.stop()
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
            clearCap()
            val cb = doneCallback
            doneCallback = null
            cb?.invoke()
        }
    }
}
