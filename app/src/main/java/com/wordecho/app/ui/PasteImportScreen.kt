package com.wordecho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.vm.ImportUiState

private const val SAMPLE = """[
  {"word": "abandon", "meaning": "v. 放弃；抛弃"},
  {"word": "abundant", "meaning": "adj. 丰富的；充裕的"},
  {"word": "vague", "meaning": "adj. 模糊的；含糊的"}
]"""

/**
 * 导入。
 *
 * 拍照识别这条路试过三种引擎（Tesseract / ML Kit / 云端视觉），
 * 对手写都不够稳。JSON 粘贴则是**零误差**的：
 * 你可以让任意 AI 把照片转成 JSON，粘进来就行，格式确定、可校验、可预览。
 */
@Composable
fun PasteImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onParse: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var raw by remember { mutableStateOf("") }

    ScreenScaffold(
        title = "导入",
        subtitle = "JSON 或纯文本都行，不用拍照",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "支持这些格式",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "① 对象数组（推荐，可带释义）\n" +
                            "② 纯字符串数组  [\"abandon\", \"vague\"]\n" +
                            "③ 纯文本：一行一个，或逗号/空格分隔\n" +
                            "④ {单词: 释义} 映射",
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { raw = SAMPLE },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("填入示例格式", fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                placeholder = {
                    Text("把 JSON 或单词列表粘在这里…", fontSize = 12.sp)
                }
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onParse(raw) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text(if (state.busy) "解析中…" else "解析", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        raw = ""
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(11.dp)
                ) { Text("清空", fontSize = 13.sp) }
            }

            if (state.message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(state.message, fontSize = 11.sp, color = cs.onSurfaceVariant)
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.items, key = { it.key }) { item ->
                    Card(
                        shape = RoundedCornerShape(11.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurface
                                )
                                Text(
                                    item.meaning.ifBlank { "（还没有释义）" },
                                    fontSize = 11.sp,
                                    color = if (item.meaning.isBlank()) {
                                        cs.error
                                    } else {
                                        cs.onSurfaceVariant
                                    }
                                )
                            }
                            TextButton(onClick = { onRemove(item.key) }) {
                                Text("删除", fontSize = 11.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            if (state.items.isNotEmpty()) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("存入今天的词库（${state.items.size} 个）", fontSize = 15.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
