package com.wordecho.app

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordecho.app.quiz.QuizMode
import com.wordecho.app.quiz.SessionType
import com.wordecho.app.settings.Settings
import com.wordecho.app.speech.VoskAsr
import com.wordecho.app.ui.BatchScreen
import com.wordecho.app.ui.HomeScreen
import com.wordecho.app.ui.PasteImportScreen
import com.wordecho.app.ui.SessionWordsScreen
import com.wordecho.app.ui.QuizScreen
import com.wordecho.app.ui.SettingsScreen
import com.wordecho.app.ui.WordEchoTheme
import com.wordecho.app.ui.WordsScreen
import com.wordecho.app.ui.WrongBookScreen
import com.wordecho.app.vm.ImportViewModel
import com.wordecho.app.vm.LibraryViewModel
import com.wordecho.app.vm.QuizViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WordEchoApp).container

        setContent {
            val settings by container.settings.flow
                .collectAsStateWithLifecycle(initialValue = Settings())

            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            WordEchoTheme(dark = dark) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, settings.fontScale)
                ) {
                    AppRoot(container, settings)
                }
            }
        }
    }
}

private sealed class Screen {
    data object Home : Screen()
    data object Quiz : Screen()
    data object Wrong : Screen()
    data object Words : Screen()
    data object Batch : Screen()
    data object Paste : Screen()
    data object Session : Screen()
    data object Settings : Screen()
}

private class VMFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(QuizViewModel::class.java) -> QuizViewModel(c) as T
        modelClass.isAssignableFrom(ImportViewModel::class.java) -> ImportViewModel(c) as T
        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(c) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

@Composable
private fun AppRoot(container: AppContainer, settings: Settings) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var quizSession by remember { mutableStateOf(SessionType.MIX) }
    var quizBatch by remember { mutableStateOf("") }
    var quizBatchId by remember { mutableStateOf(0L) }
    var viewDate by remember { mutableStateOf("") }
    var viewBatchId by remember { mutableStateOf(0L) }
    var confirmClearAll by remember { mutableStateOf(false) }

    val factory = remember(container) { VMFactory(container) }
    val libVm: LibraryViewModel = viewModel(factory = factory)
    // 提到顶层：返回键要能通知它收尾（停录音、停 TTS）
    val quizVm: QuizViewModel = viewModel(key = "quiz", factory = factory)

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> micGranted = ok }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (!micGranted) {
            permLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        libVm.refresh()
    }

    val today by libVm.today.collectAsStateWithLifecycle()
    val words by libVm.words.collectAsStateWithLifecycle()
    val due by libVm.due.collectAsStateWithLifecycle()
    val weekly by libVm.weekly.collectAsStateWithLifecycle()
    val modelBusy by libVm.modelBusy.collectAsStateWithLifecycle()
    val modelMsg by libVm.modelMsg.collectAsStateWithLifecycle()
    val wrong by libVm.wrongWords.collectAsStateWithLifecycle()
    val toast by libVm.toast.collectAsStateWithLifecycle()

    LaunchedEffect(toast) {
        if (toast.isNotBlank()) {
            Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
            libVm.clearToast()
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("确认清空全部数据？") },
            text = { Text("词库、错题、答题记录都会被删除，无法恢复。建议先导出错题。") },
            confirmButton = {
                TextButton(onClick = {
                    libVm.clearAll()
                    confirmClearAll = false
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("取消") }
            }
        )
    }

    // 系统返回（手势返回、返回键、三键导航）统一在这里处理。
    // 之前没接这个，所以在子页面按返回会直接退出 App。
    BackHandler(enabled = screen != Screen.Home) {
        when (screen) {
            Screen.Quiz -> {
                quizVm.finish()
                libVm.refresh()
                screen = Screen.Home
            }
            Screen.Session -> {
                libVm.refresh()
                screen = Screen.Batch
            }
            Screen.Paste, Screen.Batch, Screen.Words, Screen.Wrong, Screen.Settings ->
                screen = Screen.Home
            Screen.Home -> Unit
        }
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            todayAsked = today.first,
            todayCorrect = today.second,
            total = words.size,
            wrongCount = wrong.size,
            dueCount = due,
            weekly = weekly,
            modeLabel = settings.mode.label,
            onStart = {
                quizSession = SessionType.MIX
                quizBatch = ""
                quizBatchId = 0L
                screen = Screen.Quiz
            },
            onReview = {
                quizSession = SessionType.REVIEW
                quizBatch = ""
                quizBatchId = 0L
                screen = Screen.Quiz
            },
            onPaste = { screen = Screen.Paste },
            onWrongBook = { screen = Screen.Wrong },
            onWords = { screen = Screen.Words },
            onBatches = { screen = Screen.Batch },
            onSettings = { screen = Screen.Settings }
        )

        Screen.Quiz -> {
            val vm = quizVm
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.start(quizSession, quizBatch, quizBatchId) }
            QuizScreen(
                state = state,
                micGranted = micGranted,
                onRequestMic = { permLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onFinish = {
                    vm.finish()
                    screen = Screen.Home
                    libVm.refresh()
                },
                onRepeat = vm::repeatWord,
                onSkip = vm::skip,
                onToggleKeyboard = vm::toggleKeyboard,
                onSubmitText = vm::submitText,
                onReveal = vm::revealAnswer,
                onRestart = {
                    vm.restart()
                }
            )
        }

        Screen.Wrong -> WrongBookScreen(
            words = wrong,
            onBack = { screen = Screen.Home },
            onStartWrongQuiz = {
                quizSession = SessionType.WRONG_ONLY
                quizBatch = ""
                quizBatchId = 0L
                screen = Screen.Quiz
            },
            onExport = {
                val text = runBlocking { runCatching { container.repo.exportWrong() }.getOrDefault("") }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                ctx.startActivity(Intent.createChooser(intent, "导出错题"))
            }
        )

        Screen.Paste -> {
            val pvm: ImportViewModel = viewModel(key = "paste", factory = factory)
            val pstate by pvm.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { pvm.reset() }
            PasteImportScreen(
                state = pstate,
                onBack = { screen = Screen.Home },
                onParse = pvm::parsePaste,
                onDate = pvm::setDate,
                onUpdateText = pvm::updateText,
                onUpdateMeaning = pvm::updateMeaning,
                onRemove = pvm::remove,
                onSave = {
                    pvm.save()
                    libVm.refresh()
                }
            )
        }

        Screen.Session -> {
            val list by libVm.sessionWords.collectAsStateWithLifecycle()
            SessionWordsScreen(
                title = if (viewDate.isBlank()) "这一批" else com.wordecho.app.ui.prettyDate(viewDate),
                subtitle = buildString {
                    if (viewBatchId > 0L) {
                        append("上传于 ")
                        append(
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                                .format(java.util.Date(viewBatchId))
                        )
                        append(" · ")
                    }
                    append("共 ${list.size} 个词")
                },
                words = list,
                onBack = {
                    libVm.refresh()
                    screen = Screen.Batch
                },
                onSpeak = { text ->
                    container.tts.speak(
                        text = text,
                        lang = if (settings.accent == "us") java.util.Locale.US else java.util.Locale.UK,
                        rate = settings.speechRate,
                        earpiece = false,
                        offlineOnly = settings.offlineOnly
                    )
                },
                onStartReview = {
                    quizSession = SessionType.BATCH
                    quizBatch = viewDate
                    quizBatchId = viewBatchId
                    screen = Screen.Quiz
                }
            )
        }

        Screen.Batch -> BatchScreen(
            batches = libVm.batches.collectAsStateWithLifecycle().value,
            todayKey = container.repo.todayKey(),
            totalWords = words.size,
            onBack = { screen = Screen.Home },
            onOpenSession = { date, id ->
                viewDate = date
                viewBatchId = id
                libVm.loadSession(date, id)
                screen = Screen.Session
            },
            onDeleteSession = { date, id -> libVm.deleteSession(date, id) },
            onMoveSession = { date, id, toDate -> libVm.moveSession(date, id, toDate) },
            onAllWords = { screen = Screen.Words }
        )

        Screen.Words -> WordsScreen(
            words = words,
            onBack = { screen = Screen.Home },
            onImportUri = { uri -> libVm.importFrom(uri) },
            onDelete = { w -> libVm.delete(w) },
            onSaveNote = { w, note, conf -> libVm.saveNote(w, note, conf) },
            onSpeak = { text ->
                container.tts.speak(
                    text = text,
                    lang = if (settings.accent == "us") java.util.Locale.US else java.util.Locale.UK,
                    rate = settings.speechRate,
                    earpiece = false,
                    offlineOnly = settings.offlineOnly
                )
            }
        )

        Screen.Settings -> {
            SettingsScreen(
                settings = settings,
                asrCnReady = container.asr.isModelPresent(VoskAsr.LANG_CN),
                asrEnReady = container.asr.isModelPresent(VoskAsr.LANG_EN),
                    modelBusy = modelBusy,
                modelMsg = modelMsg,
                onBack = {
                    libVm.clearModelMsg()
                    screen = Screen.Home
                },
                onRate = { v -> container.appScope.launch { container.settings.setRate(v) } },
                onAccent = { a -> container.appScope.launch { container.settings.setAccent(a) } },
                onReminder = { on, h, m ->
                    container.appScope.launch { container.settings.setReminder(on, h, m) }
                    if (on) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                ctx, Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        com.wordecho.app.reminder.ReminderScheduler.schedule(ctx, h, m)
                    } else {
                        com.wordecho.app.reminder.ReminderScheduler.cancel(ctx)
                    }
                },
                onMode = { m: QuizMode -> container.appScope.launch { container.settings.setMode(m) } },
                onEarpiece = { b -> container.appScope.launch { container.settings.setEarpiece(b) } },
                onOffline = { b -> container.appScope.launch { container.settings.setOffline(b) } },
                onAsrMode = { m -> container.appScope.launch { container.settings.setAsrMode(m) } },
                onDeepSeekKey = { k -> container.appScope.launch { container.settings.setDeepSeekKey(k) } },
                onCloudKey = { v -> container.appScope.launch { container.settings.setCloudApiKey(v) } },
                onCloudBase = { v -> container.appScope.launch { container.settings.setCloudBaseUrl(v) } },
                onCloudOcrModel = { v -> container.appScope.launch { container.settings.setCloudOcrModel(v) } },
                onTheme = { t -> container.appScope.launch { container.settings.setTheme(t) } },
                onFontScale = { v -> container.appScope.launch { container.settings.setFontScale(v) } },
                onImportVoskCn = { uri -> libVm.importVoskModel(uri, VoskAsr.LANG_CN) },
                onImportVoskEn = { uri -> libVm.importVoskModel(uri, VoskAsr.LANG_EN) },
                onClearCustom = { libVm.clearCustom() },
                onClearAll = { confirmClearAll = true }
            )
        }
    }
}
