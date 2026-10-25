package com.wordecho.app.vm

import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordecho.app.AppContainer
import com.wordecho.app.data.Word
import com.wordecho.app.judge.DeepSeekJudge
import com.wordecho.app.judge.JudgeResult
import com.wordecho.app.judge.MeaningJudge
import com.wordecho.app.quiz.QuizMode
import com.wordecho.app.quiz.SessionType
import com.wordecho.app.settings.Settings
import com.wordecho.app.speech.VoiceCommand
import com.wordecho.app.speech.VoiceCommandParser
import com.wordecho.app.speech.VoskAsr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class QuizPhase { IDLE, LOADING, SPEAKING, LISTENING, JUDGING, RESULT, FINISHED }

/** 当前用的是哪套语音识别 */
enum class AsrKind { SYSTEM, VOSK, NONE }

data class QuizUiState(
    val phase: QuizPhase = QuizPhase.IDLE,
    val word: Word? = null,
    val mode: QuizMode = QuizMode.EN2CN,
    val heard: String = "",
    val partial: String = "",
    val result: JudgeResult? = null,
    val asked: Int = 0,
    val correct: Int = 0,
    val session: SessionType = SessionType.MIX,
    /** BATCH 模式下要复习的日期 */
    val batchDate: String = "",
    /** BATCH 模式下具体是第几次上传 */
    val batchId: Long = 0L,
    val tip: String = "",
    val asrReady: Boolean = false,
    /** 显示给用户看：系统识别 / 离线识别 / 键盘 */
    val asrLabel: String = "",
    val earpiece: Boolean = false,
    val keyboard: Boolean = false,
    val reveal: Boolean = false,
    /** 设备是否有可用的语音引擎；没有时提示看题作答 */
    val ttsOk: Boolean = true
)

/**
 * 背诵主流程状态机：
 *   SPEAKING(TTS 读题) → LISTENING(持续识别) → JUDGING(判分) → RESULT → 自动下一题
 *
 * 全语音、不用碰键盘。识别引擎优先用系统语音识别（零体积、中文更准），
 * 没有或用户指定离线时才用 Vosk。
 */
class QuizViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private var active = false
    private var advanceJob: Job? = null
    private var cfg = Settings()

    private var asrKind = AsrKind.NONE
    /** 本轮用的判分器：填了 DeepSeek Key 就走 AI，否则本地规则 */
    private var judge: MeaningJudge = c.judge
    private var voskLang = VoskAsr.LANG_CN
    private var listenRetries = 0
    /** 系统识别失败后是否已经降级到离线，避免来回切 */
    private var fellBackToVosk = false

    fun start(session: SessionType, batchDate: String = "", batchId: Long = 0L) {
        if (active) return
        viewModelScope.launch {
            // 等种子词库灌完再出题，否则首次启动会误报「词库为空」
            runCatching { c.seedJob.join() }
            cfg = c.settings.current()
            fellBackToVosk = false
            if (cfg.earpiece) c.audioRouter.enter() else c.audioRouter.exit()
            _state.value = QuizUiState(
                phase = QuizPhase.LOADING,
                mode = cfg.mode,
                session = session,
                batchDate = batchDate,
                batchId = batchId,
                earpiece = cfg.earpiece,
                ttsOk = c.tts.isReady()
            )
            judge = if (cfg.deepSeekKey.isNotBlank()) {
                DeepSeekJudge(cfg.deepSeekKey.trim(), c.judge)
            } else {
                c.judge
            }
            active = true
            prepareAsr(cfg.mode) { next() }
        }
    }

    /**
     * 选识别引擎：
     *   auto    → 系统可用就用系统，否则离线
     *   system  → 只用系统
     *   offline → 只用离线 Vosk
     * 都不可用才退键盘。
     */
    private fun prepareAsr(mode: QuizMode, onReady: () -> Unit) {
        voskLang = if (mode == QuizMode.EN2CN) VoskAsr.LANG_CN else VoskAsr.LANG_EN
        val systemOk = c.systemAsr.isAvailable()
        val voskOk = c.asr.isModelPresent(voskLang)

        val useSystem = when (cfg.asrMode) {
            "system" -> systemOk
            "offline" -> false
            else -> systemOk
        }
        if (useSystem) {
            asrKind = AsrKind.SYSTEM
            _state.update { it.copy(asrReady = true, asrLabel = "系统识别") }
            onReady()
            return
        }

        if (voskOk) {
            asrKind = AsrKind.VOSK
            // 首次用离线模型要把它从 APK 解到私有目录（60MB+），给个提示免得以为卡住
            _state.update { it.copy(tip = "正在准备离线语音模型，首次约 5 秒…") }
            c.asr.ensureModel(voskLang) { ok, msg ->
                _state.update {
                    it.copy(
                        asrReady = ok,
                        asrLabel = if (ok) "离线识别" else "",
                        keyboard = it.keyboard || !ok,
                        tip = if (ok) it.tip else "离线模型未就绪：$msg"
                    )
                }
                if (!ok && systemOk) {
                    // 指定了离线但模型坏了，退回系统
                    asrKind = AsrKind.SYSTEM
                    _state.update { it.copy(asrReady = true, asrLabel = "系统识别") }
                }
                viewModelScope.launch { onReady() }
            }
            return
        }

        // 指定 offline 但没模型 → 还能退系统
        if (systemOk) {
            asrKind = AsrKind.SYSTEM
            _state.update { it.copy(asrReady = true, asrLabel = "系统识别") }
            onReady()
            return
        }

        asrKind = AsrKind.NONE
        _state.update {
            it.copy(
                asrReady = false,
                asrLabel = "键盘",
                keyboard = true,
                tip = "没有可用的语音识别，已切到键盘作答"
            )
        }
        onReady()
    }

    // ---------------- 出题 ----------------

    private fun next() {
        advanceJob?.cancel()
        viewModelScope.launch {
            if (!active) return@launch
            listenRetries = 0
            val st = _state.value
            val w = c.quizEngine.pick(st.session, st.word?.id, st.batchDate, st.batchId)
            if (w == null) {
                val tip = when (st.session) {
                    SessionType.REVIEW -> "今天的复习都清完了，明天再来"
                    SessionType.WRONG_ONLY -> "错题本已经清空"
                    SessionType.MIX -> "词库为空，先去导入单词吧"
                    SessionType.BATCH -> "这个词库已经背完了"
                }
                _state.update { it.copy(phase = QuizPhase.FINISHED, tip = tip) }
                return@launch
            }
            _state.update {
                it.copy(
                    word = w,
                    phase = QuizPhase.SPEAKING,
                    heard = "",
                    partial = "",
                    result = null,
                    reveal = false,
                    tip = ""
                )
            }
            speak(w)
        }
    }

    private fun speak(w: Word) {
        val st = _state.value
        val text = if (st.mode == QuizMode.EN2CN) w.text else w.meaning
        val english = if (cfg.accent == "us") Locale.US else Locale.UK
        val lang = if (st.mode == QuizMode.EN2CN) english else Locale.CHINA
        _state.update { it.copy(phase = QuizPhase.SPEAKING) }
        c.tts.speak(text, lang, cfg.speechRate, cfg.earpiece, cfg.offlineOnly) {
            viewModelScope.launch {
                delay(280)
                if (active) startListening()
            }
        }
    }

    private fun startListening() {
        if (!active) return
        val st = _state.value
        // 键盘模式：不占麦克风，安静等用户打字
        if (st.keyboard) {
            _state.update { it.copy(phase = QuizPhase.LISTENING, partial = "") }
            return
        }
        if (asrKind == AsrKind.NONE) {
            _state.update { it.copy(phase = QuizPhase.LISTENING, keyboard = true) }
            return
        }
        // 确保 TTS 已经彻底停了再开麦，否则会把自己的朗读声录进去
        runCatching { c.tts.stop() }
        _state.update { it.copy(phase = QuizPhase.LISTENING, partial = "") }

        val onPartial: (String) -> Unit = { p ->
            _state.update { if (it.phase == QuizPhase.LISTENING) it.copy(partial = p) else it }
        }
        val onFinal: (String) -> Unit = { text -> handleHeard(text) }
        val onError: (String) -> Unit = { msg -> handleListenError(msg) }

        when (asrKind) {
            AsrKind.SYSTEM -> {
                val tag = if (st.mode == QuizMode.EN2CN) "zh-CN" else "en-US"
                c.systemAsr.start(tag, onPartial, onFinal, onError)
            }
            AsrKind.VOSK -> {
                val source = if (cfg.earpiece) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.MIC
                }
                c.asr.start(source, onPartial, onFinal, onError)
            }
            AsrKind.NONE -> Unit
        }

        // 一直没听到任何内容时给个明确提示，别让用户对着屏幕干等
        viewModelScope.launch {
            delay(7000)
            val s = _state.value
            if (active && s.phase == QuizPhase.LISTENING && s.partial.isBlank()) {
                _state.update {
                    it.copy(
                        tip = "没听到声音：检查录音权限，或到设置里把识别方式改成「离线 Vosk」"
                    )
                }
            }
        }
    }

    /** 没听清就自动再听一次，最多两次，避免死循环 */
    private fun handleListenError(msg: String) {
        if (!active) return
        if (_state.value.phase != QuizPhase.LISTENING) return

        // 系统识别挂了（国产 ROM 很常见）→ 自动切到离线 Vosk 再试，而不是直接退键盘
        if (asrKind == AsrKind.SYSTEM && !fellBackToVosk && c.asr.isModelPresent(voskLang)) {
            fellBackToVosk = true
            asrKind = AsrKind.VOSK
            _state.update {
                it.copy(tip = "系统识别不可用（$msg），已切到离线识别", asrLabel = "离线识别")
            }
            c.asr.ensureModel(voskLang) { ok, m ->
                if (ok) {
                    _state.update { it.copy(asrReady = true, keyboard = false) }
                    viewModelScope.launch {
                        delay(300)
                        if (active) startListening()
                    }
                } else {
                    _state.update {
                        it.copy(asrReady = false, keyboard = true, tip = "离线模型未就绪：$m")
                    }
                }
            }
            return
        }

        // 没听到声音：先原样再听一次（可能是用户慢了半拍）
        if (msg == "麦克风没有声音" && listenRetries < 1) {
            listenRetries++
            _state.update { it.copy(tip = "没听到声音，再试一次…", partial = "") }
            viewModelScope.launch {
                delay(600)
                if (active) startListening()
            }
            return
        }

        if (msg == "没听清" && listenRetries < 2) {
            listenRetries++
            _state.update { it.copy(tip = "没听清，再说一次", partial = "") }
            viewModelScope.launch {
                delay(400)
                if (active) startListening()
            }
            return
        }

        _state.update { it.copy(tip = msg, keyboard = true) }
    }

    private fun handleHeard(text: String) {
        viewModelScope.launch {
            val st = _state.value
            if (st.phase != QuizPhase.LISTENING && st.phase != QuizPhase.JUDGING) return@launch
            val cmd = VoiceCommandParser.parse(text)
            if (cmd != VoiceCommand.NONE && cmd != VoiceCommand.START) {
                handleCommand(cmd)
                return@launch
            }
            judgeAnswer(text)
        }
    }

    private suspend fun judgeAnswer(text: String) {
        val st = _state.value
        val w = st.word ?: return
        _state.update { it.copy(phase = QuizPhase.JUDGING, partial = "") }
        stopListening()
        val r = withContext(Dispatchers.Default) { judge.judge(text, w, st.mode) }
        if (r.correct) c.repo.markCorrect(w) else c.repo.markWrong(w)
        _state.update {
            it.copy(
                phase = QuizPhase.RESULT,
                result = r,
                heard = text,
                reveal = true,
                asked = it.asked + 1,
                correct = it.correct + if (r.correct) 1 else 0
            )
        }
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(if (r.correct) 1000 else 2400)
            if (active) next()
        }
    }

    private fun stopListening() {
        runCatching { c.systemAsr.stop() }
        runCatching { c.asr.stop() }
    }

    // ---------------- 交互 ----------------

    private fun handleCommand(cmd: VoiceCommand) {
        when (cmd) {
            VoiceCommand.REPEAT -> {
                stopListening()
                repeatWord()
            }
            VoiceCommand.NEXT, VoiceCommand.SKIP -> skip()
            VoiceCommand.STOP -> finish()
            else -> Unit
        }
    }

    fun repeatWord() {
        val w = _state.value.word ?: return
        advanceJob?.cancel()
        stopListening()
        listenRetries = 0
        _state.update { it.copy(result = null, heard = "", partial = "", reveal = false) }
        speak(w)
    }

    fun skip() {
        val w = _state.value.word
        advanceJob?.cancel()
        stopListening()
        viewModelScope.launch {
            if (w != null) c.repo.touch(w)
            _state.update { it.copy(asked = it.asked + 1) }
            next()
        }
    }

    fun submitText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { judgeAnswer(text.trim()) }
    }

    /**
     * 语音 ⇄ 键盘 相互切换。
     * 切到键盘要立刻停麦（否则一边打字一边被识别结果打断）；
     * 切回语音要重新开麦（否则一直干等）。
     */
    fun toggleKeyboard() {
        val toKeyboard = !_state.value.keyboard
        _state.update { it.copy(keyboard = toKeyboard, partial = "") }

        if (toKeyboard) {
            stopListening()
            _state.update { it.copy(tip = "") }
        } else {
            if (asrKind == AsrKind.NONE) {
                _state.update {
                    it.copy(keyboard = true, tip = "这台设备没有可用的语音识别")
                }
                return
            }
            if (_state.value.phase == QuizPhase.LISTENING ||
                _state.value.phase == QuizPhase.SPEAKING
            ) {
                _state.update { it.copy(tip = "") }
                startListening()
            }
        }
    }
    fun revealAnswer() = _state.update { it.copy(reveal = true) }

    fun finish() {
        active = false
        advanceJob?.cancel()
        stopListening()
        c.tts.stop()
        c.audioRouter.exit()
        _state.update { it.copy(phase = QuizPhase.FINISHED, partial = "") }
    }

    fun restart() {
        val session = _state.value.session
        val batch = _state.value.batchDate
        val batchNo = _state.value.batchId
        active = false
        stopListening()
        c.tts.stop()
        _state.value = QuizUiState()
        start(session, batch, batchNo)
    }

    override fun onCleared() {
        active = false
        advanceJob?.cancel()
        stopListening()
        runCatching { c.tts.stop() }
        runCatching { c.audioRouter.exit() }
        super.onCleared()
    }
}
