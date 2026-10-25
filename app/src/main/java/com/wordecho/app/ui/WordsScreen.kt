package com.wordecho.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.R
import com.wordecho.app.data.SimilarWords
import com.wordecho.app.data.Word
import com.wordecho.app.quiz.ReviewSchedule

@Composable
fun WordsScreen(
    words: List<Word>,
    onBack: () -> Unit,
    onImportUri: (Uri) -> Unit,
    onDelete: (Word) -> Unit,
    onSaveNote: (Word, String, String) -> Unit,
    onSpeak: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var draftNote by remember { mutableStateOf("") }
    var draftConf by remember { mutableStateOf("") }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) onImportUri(uri) }

    val filtered = remember(words, query) {
        if (query.isBlank()) words
        else words.filter {
            it.text.contains(query.trim(), ignoreCase = true) ||
                it.meaning.contains(query.trim())
        }
    }

    ScreenScaffold(
        title = "单词表",
        subtitle = "共 ${words.size} 个词 · 点一行记笔记",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜单词或中文释义", fontSize = 13.sp) }
            )

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { openDoc.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("导入词库文件（每行：单词 + Tab + 释义）", fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Text("没有匹配的单词", fontSize = 13.sp, color = cs.onSurfaceVariant)
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.id }) { w ->
                    WordRow(
                        word = w,
                        allWords = words,
                        expanded = expandedId == w.id,
                        draftNote = draftNote,
                        draftConf = draftConf,
                        onToggle = {
                            if (expandedId == w.id) {
                                expandedId = null
                            } else {
                                expandedId = w.id
                                draftNote = w.note
                                draftConf = w.confusables
                            }
                        },
                        onNoteChange = { draftNote = it },
                        onConfChange = { draftConf = it },
                        onSave = {
                            onSaveNote(w, draftNote, draftConf)
                            expandedId = null
                        },
                        onDelete = { onDelete(w) },
                        onSpeak = { onSpeak(w.text) },
                        onSearch = { q ->
                            query = q
                            expandedId = null
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun WordRow(
    word: Word,
    allWords: List<Word>,
    expanded: Boolean,
    draftNote: String,
    draftConf: String,
    onToggle: () -> Unit,
    onNoteChange: (String) -> Unit,
    onConfChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit,
    onSearch: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .clickable(onClick = onToggle)
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            word.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                        if (word.isCustom) {
                            Spacer(Modifier.width(6.dp))
                            Text("生词", fontSize = 10.sp, color = cs.primary)
                        }
                    }
                    Text(word.meaning, fontSize = 12.sp, color = cs.onSurfaceVariant)
                    if (word.nextReviewAt > 0L) {
                        Text(
                            "复习：" + ReviewSchedule.describe(word.nextReviewAt),
                            fontSize = 10.sp,
                            color = cs.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    if (word.note.isNotBlank()) {
                        Text(
                            "笔记：${word.note}",
                            fontSize = 10.sp,
                            color = cs.primary.copy(alpha = 0.85f)
                        )
                    }
                    if (word.confusables.isNotBlank()) {
                        Row(
                            Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "形近：",
                                fontSize = 10.sp,
                                color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            word.confusables
                                .split(',', '，', '、', ' ')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(4)
                                .forEach { c ->
                                    Text(
                                        c,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = cs.primary,
                                        modifier = Modifier
                                            .clickable { onSearch(c) }
                                            .padding(end = 8.dp)
                                    )
                                }
                        }
                    }
                }
                IconButton(onClick = onSpeak, modifier = Modifier.width(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_speaker),
                        contentDescription = "再读一遍",
                        tint = cs.primary,
                        modifier = Modifier.width(20.dp).height(20.dp)
                    )
                }
                TextButton(onClick = onDelete) {
                    Text("删除", fontSize = 11.sp, color = cs.onSurfaceVariant)
                }
            }

            if (expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    OutlinedTextField(
                        value = draftNote,
                        onValueChange = onNoteChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("笔记 / 记忆口诀", fontSize = 11.sp) },
                        placeholder = { Text("比如：a + band + on，一帮人散了 → 放弃", fontSize = 12.sp) },
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftConf,
                        onValueChange = onConfChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("形近词（逗号分隔）", fontSize = 11.sp) },
                        placeholder = { Text("abundant, abdomen", fontSize = 12.sp) }
                    )

                    // 形近词候选：按编辑距离从词库里找，点一下补上
                    val token = SimilarWords.currentToken(draftConf)
                    val already = draftConf.split(',', '，', ';', '；', ' ')
                        .map { it.trim().lowercase() }
                        .filter { it.isNotBlank() }
                        .toSet()
                    val suggestions = token.takeIf { it.length >= 2 }?.let {
                        SimilarWords.suggest(it, allWords, excludeText = word.text)
                            .filterNot { it.text.lowercase() in already }
                    }.orEmpty()

                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "形近词候选（点一下填入）",
                            fontSize = 10.sp,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { s ->
                                AssistChip(
                                    onClick = {
                                        onConfChange(
                                            SimilarWords.replaceToken(draftConf, s.text)
                                        )
                                    },
                                    label = { Text(s.text, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("保存", fontSize = 13.sp) }
                        OutlinedButton(
                            onClick = onToggle,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("收起", fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
