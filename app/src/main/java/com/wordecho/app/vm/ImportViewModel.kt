package com.wordecho.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordecho.app.AppContainer
import com.wordecho.app.data.PasteParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OcrItem(
    val key: Long,
    val text: String,
    val confidence: Int,
    val meaning: String,
    val inBank: Boolean
) {
    /** 置信度低或词库查不到 → 标黄，需要人工过一眼 */
    val suspicious: Boolean get() = confidence < 70 || !inBank
}

data class ImportUiState(
    val busy: Boolean = false,
    val message: String = "",
    val items: List<OcrItem> = emptyList(),
    /** 这批词存到哪一天，形如 2026-10-25。默认今天，可以往前调用来补传 */
    val date: String = ""
)

class ImportViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState(date = c.repo.todayKey()))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** 换一个上传日期（补传用） */
    fun setDate(key: String) {
        _state.update { it.copy(date = key) }
    }

    /** 粘贴词表：解析 → 查词库 → 云端补释义。完全不依赖 OCR，零识别误差 */
    fun parsePaste(raw: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = "正在解析…", items = emptyList()) }
            val parsed = PasteParser.parse(raw)
            if (parsed.isEmpty()) {
                _state.update {
                    it.copy(busy = false, message = "没解析出英文单词，检查一下粘贴的内容")
                }
                return@launch
            }

            val cfg = c.settings.current()
            var items = parsed.mapIndexed { i, pair ->
                val (word, given) = pair
                val w = c.repo.findByText(word)
                OcrItem(
                    key = i.toLong(),
                    text = w?.text ?: word,
                    confidence = 100,
                    meaning = given.ifBlank { w?.meaning.orEmpty() },
                    inBank = w != null || given.isNotBlank()
                )
            }

            val missing = items.filter { it.meaning.isBlank() }.map { it.text }
            if (missing.isNotEmpty() && cfg.cloudApiKey.isNotBlank()) {
                _state.update { it.copy(message = "有 ${missing.size} 个词要查释义…") }
                val found = withContext(Dispatchers.IO) {
                    runCatching {
                        c.cloudDict.lookup(
                            missing, cfg.cloudBaseUrl, cfg.cloudApiKey, cfg.cloudOcrModel
                        )
                    }.getOrDefault(emptyMap())
                }
                items = items.map { item ->
                    if (item.meaning.isBlank()) {
                        item.copy(meaning = found[item.text.lowercase()].orEmpty())
                    } else {
                        item
                    }
                }
            }

            val noMeaning = items.count { it.meaning.isBlank() }
            _state.update {
                it.copy(
                    busy = false,
                    items = items,
                    message = "解析出 ${items.size} 个词" +
                        if (noMeaning > 0) "，$noMeaning 个还没查到释义" else ""
                )
            }
        }
    }

    fun updateText(key: Long, value: String) {
        val trimmed = value.trim()
        _state.update { s ->
            s.copy(items = s.items.map { if (it.key == key) it.copy(text = trimmed) else it })
        }
        // 改完拼写后重新查词库：只在释义还空着时自动补，避免覆盖你手填的释义
        viewModelScope.launch {
            val item = _state.value.items.firstOrNull { it.key == key } ?: return@launch
            if (item.text.isBlank()) return@launch
            val w = c.repo.findByText(item.text)
            if (w != null) {
                _state.update { s ->
                    s.copy(items = s.items.map {
                        if (it.key == key) {
                            it.copy(
                                meaning = if (it.meaning.isBlank()) w.meaning else it.meaning,
                                inBank = true
                            )
                        } else {
                            it
                        }
                    })
                }
            }
        }
    }

    fun updateMeaning(key: Long, value: String) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it.key == key) it.copy(meaning = value) else it })
        }
    }

    fun remove(key: Long) {
        _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }
    }

    fun save() {
        viewModelScope.launch {
            val items = _state.value.items.filter { it.text.isNotBlank() }
            if (items.isEmpty()) {
                _state.update { it.copy(message = "没有可保存的单词") }
                return@launch
            }
            val batch = _state.value.date.ifBlank { c.repo.todayKey() }
            // 这一次点「存入」的所有词共用一个批次号，同一天再传一次就是新的一组
            val stamp = System.currentTimeMillis()
            var added = 0
            var dup = 0
            var noMeaning = 0
            items.forEach { item ->
                val ok = c.repo.addCustom(item.text, item.meaning, batch, stamp)
                if (ok) added++ else dup++
                if (item.meaning.isBlank()) noMeaning++
            }
            _state.update {
                it.copy(
                    items = emptyList(),
                    message = buildString {
                        append("已存入「${prettyKey(batch)}」，共 $added 个")
                        if (dup > 0) append("（$dup 个重复已跳过）")
                        if (noMeaning > 0) append("，$noMeaning 个还没查到释义")
                    }
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = "") }

    fun reset() {
        _state.value = ImportUiState(date = c.repo.todayKey())
    }

    /** 2026-10-25 → 10 月 25 日 */
    private fun prettyKey(key: String): String {
        val p = key.split("-")
        if (p.size != 3) return key
        return "${p[1].trimStart('0')} 月 ${p[2].trimStart('0')} 日"
    }
}
