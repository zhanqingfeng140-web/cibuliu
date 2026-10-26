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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
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
 *
 * 解析出的每条都能直接改：单词拼写、释义都能点开输入框编辑，
 * 改完单词会自动去词库比对、补上已知释义（不影响你手填的释义）。
 *
 * 顶部还能改「存入哪一天」：10 月 25 号忘了传，26 号补上时把日期调回 25 号，
 * 这批词就直接落在 25 号的每日词库里。
 */
@Composable
fun PasteImportScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onParse: (String) -> Unit,
    onDate: (String) -> Unit,
    onUpdateText: (Long, String) -> Unit,
    onUpdateMeaning: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onSave: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var raw by remember { mutableStateOf("") }
    val todayKey = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(java.util.Date())
    }

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

            UploadDateRow(
                dateKey = state.date,
                isToday = state.date == todayKey,
                onChange = onDate
            )

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

            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "下面每条都能直接改：单词拼写或释义不对，点开输入框就能编辑。",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(state.items, key = { _, it -> it.key }) { idx, item ->
                    Card(
                        shape = RoundedCornerShape(11.dp),
                        colors = CardDefaults.cardColors(containerColor = cs.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${idx + 1}.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(Modifier.padding(start = 8.dp))
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { onUpdateText(item.key, it) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("单词", fontSize = 11.sp) },
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                                )
                                TextButton(
                                    onClick = { onRemove(item.key) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp
                                    )
                                ) {
                                    Text("删除", fontSize = 11.sp, color = cs.onSurfaceVariant)
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = item.meaning,
                                onValueChange = { onUpdateMeaning(item.key, it) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 1,
                                maxLines = 3,
                                label = { Text("释义", fontSize = 11.sp) },
                                placeholder = { Text("还没填释义", fontSize = 11.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                            )
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
                    Text(
                        if (state.date == todayKey) {
                            "存入今天的词库（${state.items.size} 个）"
                        } else {
                            "存入「${prettyDate(state.date)}」的词库（${state.items.size} 个）"
                        },
                        fontSize = 15.sp
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * 上传日期。
 *
 * 默认今天；忘了传的那天可以调回去补，词就落在正确的那一天的每日词库里。
 * 不限制只能选过去——想把今天这批排到明天再背也行。
 */
@Composable
private fun UploadDateRow(
    dateKey: String,
    isToday: Boolean,
    onChange: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("存入哪一天", fontSize = 11.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        prettyDate(dateKey),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        if (isToday) "今天" else "补传",
                        fontSize = 10.sp,
                        color = cs.primary
                    )
                }
            }
            TextButton(onClick = {
                val cal = java.util.Calendar.getInstance()
                runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                        .parse(dateKey)?.let { cal.time = it }
                }
                android.app.DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        onChange(
                            String.format(java.util.Locale.CHINA, "%04d-%02d-%02d", y, m + 1, d)
                        )
                    },
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                ).show()
            }) { Text("改日期", fontSize = 12.sp) }
        }
    }
}
