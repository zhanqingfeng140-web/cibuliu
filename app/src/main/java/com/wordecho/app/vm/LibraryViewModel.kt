package com.wordecho.app.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordecho.app.AppContainer
import com.wordecho.app.data.BatchRow
import com.wordecho.app.data.WeeklyStats
import com.wordecho.app.data.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val c: AppContainer) : ViewModel() {

    val words: StateFlow<List<Word>> = c.repo.words()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    val wrongWords: StateFlow<List<Word>> = c.repo.wrongWords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(3_000), emptyList())

    private val _today = MutableStateFlow(0 to 0)
    val today: StateFlow<Pair<Int, Int>> = _today.asStateFlow()

    private val _total = MutableStateFlow(0)
    val total: StateFlow<Int> = _total.asStateFlow()

    private val _due = MutableStateFlow(0)
    /** 艾宾浩斯到期待复习数量 */
    val due: StateFlow<Int> = _due.asStateFlow()

    private val _sessionWords = MutableStateFlow<List<Word>>(emptyList())
    /** 当前查看的某一批上传里的词 */
    val sessionWords: StateFlow<List<Word>> = _sessionWords.asStateFlow()

    private val _batches = MutableStateFlow<List<BatchRow>>(emptyList())
    /** 每日导入的词库批次 */
    val batches: StateFlow<List<BatchRow>> = _batches.asStateFlow()

    private val _weekly = MutableStateFlow(WeeklyStats())
    /** 近 7 天曲线 + 累计统计 */
    val weekly: StateFlow<WeeklyStats> = _weekly.asStateFlow()

    private val _toast = MutableStateFlow("")
    val toast: StateFlow<String> = _toast.asStateFlow()

    private val _modelBusy = MutableStateFlow(false)
    val modelBusy: StateFlow<Boolean> = _modelBusy.asStateFlow()

    private val _modelMsg = MutableStateFlow("")
    val modelMsg: StateFlow<String> = _modelMsg.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _today.value = runCatching { c.repo.statsToday() }.getOrDefault(0 to 0)
            _total.value = runCatching { c.repo.totalCount() }.getOrDefault(0)
            _due.value = runCatching { c.repo.dueCount() }.getOrDefault(0)
            _weekly.value = runCatching { c.repo.weeklyStats() }.getOrDefault(WeeklyStats())
            _batches.value = runCatching { c.repo.batches() }.getOrDefault(emptyList())
        }
    }

    fun loadSession(date: String, batchId: Long) {
        viewModelScope.launch {
            _sessionWords.value = runCatching { c.repo.sessionPool(date, batchId) }
                .getOrDefault(emptyList())
        }
    }

    fun deleteSession(date: String, batchId: Long) {
        viewModelScope.launch {
            c.repo.deleteSession(date, batchId)
            _toast.value = "已删除这一批"
            refresh()
        }
    }

    fun delete(word: Word) {
        viewModelScope.launch {
            c.repo.delete(word)
            refresh()
        }
    }

    /** 从 txt / csv 文件导入词库 */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    c.appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                _toast.value = "文件读取失败"
                return@launch
            }
            val n = c.repo.importText(text)
            _toast.value = if (n > 0) "导入 $n 条单词" else "没有解析到有效行（格式：单词<Tab>释义）"
            refresh()
        }
    }

    fun exportWrong(onText: (String) -> Unit) {
        viewModelScope.launch {
            val text = c.repo.exportWrong()
            onText(text)
        }
    }

    fun clearCustom() {
        viewModelScope.launch {
            c.repo.clearCustom()
            _toast.value = "已清空自定义生词"
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            c.repo.clearAll()
            _toast.value = "已清空全部数据"
            refresh()
        }
    }

    fun clearToast() {
        _toast.value = ""
    }

    // ---------------- 笔记 / 形近词 ----------------

    fun saveNote(word: Word, note: String, confusables: String) {
        viewModelScope.launch {
            c.repo.saveNote(word, note, confusables)
            _toast.value = "已保存「${word.text}」的笔记"
        }
    }

    // ---------------- 离线模型导入 ----------------

    fun importVoskModel(uri: Uri, lang: String) {
        viewModelScope.launch {
            _modelBusy.value = true
            _modelMsg.value = "正在解压语音模型，42MB 大概需要十几秒…"
            val r = c.modelImporter.importVoskModel(uri, lang)
            if (r.ok) runCatching { c.asr.release() }
            _modelMsg.value = r.message
            _modelBusy.value = false
        }
    }

    fun clearModelMsg() {
        _modelMsg.value = ""
    }
}
