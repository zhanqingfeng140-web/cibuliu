package com.wordecho.app

import android.content.Context
import com.wordecho.app.data.AppDatabase
import com.wordecho.app.data.ModelImporter
import com.wordecho.app.data.WordRepository
import com.wordecho.app.judge.FuzzyJudge
import com.wordecho.app.judge.LlmJudge
import com.wordecho.app.judge.MeaningJudge
import com.wordecho.app.judge.NoopLocalLlm
import com.wordecho.app.ocr.CloudDict
import com.wordecho.app.quiz.QuizEngine
import com.wordecho.app.settings.SettingsStore
import com.wordecho.app.speech.AudioRouter
import com.wordecho.app.speech.CloudAsr
import com.wordecho.app.speech.SystemAsr
import com.wordecho.app.speech.TtsSpeaker
import com.wordecho.app.speech.VoskAsr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手写依赖容器，不引 Hilt，保持工程轻量 */
class AppContainer(private val context: Context) {

    val appContext: Context get() = context

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase by lazy { AppDatabase.get(context) }
    val repo: WordRepository by lazy { WordRepository(db.wordDao(), db.logDao(), db.batchWordDao()) }
    val settings: SettingsStore by lazy { SettingsStore(context) }

    val tts: TtsSpeaker by lazy { TtsSpeaker(context) }
    val asr: VoskAsr by lazy { VoskAsr(context) }
    val systemAsr: SystemAsr by lazy { SystemAsr(context) }
    val audioRouter: AudioRouter by lazy { AudioRouter(context) }
    val cloudDict = CloudDict()
    val cloudAsr: CloudAsr by lazy { CloudAsr(context) }
    val modelImporter: ModelImporter by lazy { ModelImporter(context) }

    val quizEngine: QuizEngine by lazy { QuizEngine(repo) }
    val judge: MeaningJudge by lazy { LlmJudge(FuzzyJudge(), NoopLocalLlm) }

    /** 首次启动灌种子词库的任务，背诵前会 join 它，避免「刚装完就说词库为空」 */
    val seedJob = appScope.launch { runCatching { repo.seedIfEmpty(context) } }
}
