package com.wordecho.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.wordecho.app.data.BatchRow
import com.wordecho.app.data.DayStat
import com.wordecho.app.data.WeeklyStats
import com.wordecho.app.data.Word
import com.wordecho.app.judge.JudgeResult
import com.wordecho.app.quiz.QuizMode
import com.wordecho.app.quiz.SessionType
import com.wordecho.app.settings.Settings
import com.wordecho.app.vm.ImportUiState
import com.wordecho.app.vm.OcrItem
import com.wordecho.app.vm.QuizPhase
import com.wordecho.app.vm.QuizUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 没有模拟器时，用 Robolectric 在 JVM 上把真实 Compose 界面渲染成 PNG。
 *
 * 做法：起一个 ComponentActivity → setContent → 量好 DecorView → 直接 draw 到 Bitmap。
 * 比 captureToImage 稳，因为它不依赖「等一帧重绘完成」。
 *
 * 能验证：布局会不会崩、文字是否溢出、配色与间距是否正常。
 * 验证不了：触摸交互、音频、真实设备字体。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenRenderTest {

    private fun render(name: String, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { WordEchoTheme(dark = false) { content() } }

        val dm = activity.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val decor = activity.window.decorView

        shadowOf(Looper.getMainLooper()).idle()
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        decor.layout(0, 0, w, h)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))

        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun demoWeek(): WeeklyStats {
        val base = listOf(12 to 9, 0 to 0, 18 to 14, 6 to 3, 25 to 20, 14 to 9, 24 to 18)
        val days = base.mapIndexed { i, (t, c) ->
            DayStat(
                key = "2026-10-${15 + i}",
                label = if (i == 6) "今天" else "10/${15 + i}",
                total = t,
                correct = c,
                isToday = i == 6
            )
        }
        return WeeklyStats(
            days = days,
            weekTotal = days.sumOf { it.total },
            weekCorrect = days.sumOf { it.correct },
            allAnswered = 214,
            mastered = 96
        )
    }

    @Test
    fun renderHome() {
        render("01_home") {
            HomeScreen(
                todayAsked = 24,
                todayCorrect = 18,
                total = 431,
                wrongCount = 7,
                dueCount = 5,
                weekly = demoWeek(),
                modeLabel = "听英文 · 说中文",
                onStart = {},
                onReview = {},
                onPaste = {},
                onWrongBook = {},
                onWords = {},
                onBatches = {},
                onSettings = {}
            )
        }
    }

    @Test
    fun renderQuizListening() {
        val word = Word(
            id = 1,
            text = "abandon",
            meaning = "v. 放弃；抛弃；遗弃",
            wrongCount = 2,
            reviewStage = 3
        )
        render("02_quiz_listening") {
            QuizScreen(
                state = QuizUiState(
                    phase = QuizPhase.LISTENING,
                    word = word,
                    mode = QuizMode.EN2CN,
                    session = SessionType.MIX,
                    asked = 12,
                    correct = 9,
                    partial = "放弃",
                    asrReady = true
                ),
                micGranted = true,
                onRequestMic = {},
                onFinish = {},
                onRepeat = {},
                onSkip = {},
                onToggleKeyboard = {},
                onSubmitText = {},
                onReveal = {},
                onRestart = {}
            )
        }
    }

    @Test
    fun renderSessionWords() {
        render("11_session") {
            SessionWordsScreen(
                title = "10 月 23 日",
                subtitle = "共 4 个词",
                words = listOf(
                    Word(id = 1, text = "abandon", meaning = "v. 放弃；抛弃"),
                    Word(id = 2, text = "abundant", meaning = "adj. 丰富的；充裕的"),
                    Word(id = 3, text = "vague", meaning = "adj. 模糊的；含糊的"),
                    Word(id = 4, text = "resilient", meaning = "")
                ),
                onBack = {},
                onSpeak = {},
                onStartReview = {}
            )
        }
    }

    @Test
    fun renderWords() {
        render("10_words") {
            WordsScreen(
                words = listOf(
                    Word(id = 1, text = "abandon", meaning = "v. 放弃；抛弃", wrongCount = 2, batchDate = "2026-10-23"),
                    Word(id = 2, text = "abundant", meaning = "adj. 丰富的", batchDate = "2026-10-23"),
                    Word(id = 3, text = "vague", meaning = "adj. 模糊的", note = "记不清就是模糊", batchDate = "2026-10-22")
                ),
                onBack = {},
                onImportUri = {},
                onDelete = {},
                onSaveNote = { _, _, _ -> },
                onSpeak = {}
            )
        }
    }

    @Test
    fun renderQuizKeyboardMode() {
        val word = Word(id = 3, text = "abandon", meaning = "v. 放弃；抛弃；遗弃")
        render("09_quiz_keyboard") {
            QuizScreen(
                state = QuizUiState(
                    phase = QuizPhase.LISTENING,
                    word = word,
                    mode = QuizMode.EN2CN,
                    session = SessionType.MIX,
                    asked = 5,
                    correct = 4,
                    keyboard = true,
                    asrLabel = "系统识别"
                ),
                micGranted = true,
                onRequestMic = {},
                onFinish = {},
                onRepeat = {},
                onSkip = {},
                onToggleKeyboard = {},
                onSubmitText = {},
                onReveal = {},
                onRestart = {}
            )
        }
    }

    @Test
    fun renderQuizWrongAnswer() {
        val word = Word(
            id = 2,
            text = "ambiguous",
            meaning = "adj. 模糊的；有歧义的",
            wrongCount = 4,
            note = "ambi = 两边，两边都能解释 → 模糊的"
        )
        render("03_quiz_wrong") {
            QuizScreen(
                state = QuizUiState(
                    phase = QuizPhase.RESULT,
                    word = word,
                    mode = QuizMode.EN2CN,
                    session = SessionType.MIX,
                    asked = 13,
                    correct = 9,
                    heard = "模糊",
                    reveal = true,
                    result = JudgeResult(false, 0.5, "很接近（你说的是「模糊」，标准释义「模糊的」）")
                ),
                micGranted = true,
                onRequestMic = {},
                onFinish = {},
                onRepeat = {},
                onSkip = {},
                onToggleKeyboard = {},
                onSubmitText = {},
                onReveal = {},
                onRestart = {}
            )
        }
    }

    @Test
    fun renderPasteImport() {
        render("08_paste") {
            PasteImportScreen(
                state = ImportUiState(
                    busy = false,
                    message = "解析出 4 个词",
                    date = "2026-10-23",
                    items = listOf(
                        OcrItem(0, "abandon", 100, "v. 放弃；抛弃", true),
                        OcrItem(1, "abundant", 100, "adj. 丰富的；充裕的", true),
                        OcrItem(2, "vague", 100, "adj. 模糊的", true),
                        OcrItem(3, "resilient", 100, "", false)
                    )
                ),
                onBack = {},
                onParse = {},
                onDate = {},
                onUpdateText = { _, _ -> },
                onUpdateMeaning = { _, _ -> },
                onRemove = {},
                onSave = {}
            )
        }
    }

    @Test
    fun renderBatchList() {
        render("07_batches") {
            BatchScreen(
                batches = listOf(
                    BatchRow("2026-10-23", 1730000000000L, 12, 3, 18),
                    BatchRow("2026-10-23", 1729000000000L, 8, 0, 11),
                    BatchRow("2026-10-22", 1728000000000L, 20, 0, 41),
                    BatchRow("2026-10-20", 0L, 8, 5, 6)
                ),
                todayKey = "2026-10-23",
                totalWords = 431,
                onBack = {},
                onOpenSession = { _, _ -> },
                onMoveSession = { _, _, _ -> },
                onDeleteSession = { _, _ -> },
                onAllWords = {}
            )
        }
    }

    @Test
    fun renderSettings() {
        render("05_settings") {
            SettingsScreen(
                settings = Settings(speechRate = 0.85f, mode = QuizMode.EN2CN),
                asrCnReady = true,
                asrEnReady = false,
                modelBusy = false,
                modelMsg = "语音模型导入完成（42 MB）",
                onBack = {},
                onRate = {},
                onAccent = {},
                onReminder = { _, _, _ -> },
                onMode = {},
                onEarpiece = {},
                onOffline = {},
                onAsrMode = {},
                onDeepSeekKey = {},
                onCloudKey = {},
                onCloudBase = {},
                onCloudOcrModel = {},
                onTheme = {},
                onFontScale = {},
                onImportVoskCn = {},
                onImportVoskEn = {},
                onClearCustom = {},
                onClearAll = {}
            )
        }
    }
}
