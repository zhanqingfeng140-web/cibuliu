package com.wordecho.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.data.BatchRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 每日词库。
 *
 * 层级是「日期 → 当天第几次上传」：
 *   10 月 25 日
 *     上传于 18:30 · 8 个词
 *     上传于 09:12 · 12 个词
 * 点某一次只复习那一批，上传多少就是多少。
 *
 * 长按某一批可以改日期（忘了传的那天，事后把整批挪回正确日期）或者删除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BatchScreen(
    batches: List<BatchRow>,
    todayKey: String,
    totalWords: Int,
    onBack: () -> Unit,
    onOpenSession: (date: String, batchId: Long) -> Unit,
    onMoveSession: (date: String, batchId: Long, toDate: String) -> Unit,
    onDeleteSession: (date: String, batchId: Long) -> Unit,
    onAllWords: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val grouped = batches.groupBy { it.batchDate }
    // 长按弹出：改日期 / 删除
    var pending by remember { mutableStateOf<BatchRow?>(null) }
    // 删除前的二次确认
    var deleting by remember { mutableStateOf<BatchRow?>(null) }

    ScreenScaffold(
        title = "每日词库",
        subtitle = if (batches.isEmpty()) "还没有导入过单词" else "共 ${batches.size} 次上传",
        onBack = onBack
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

            // 全部词库入口，放最上面
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cs.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAllWords() }
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "全部词库",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onPrimaryContainer
                        )
                        Text(
                            "$totalWords 个词 · 含内置基础词库",
                            fontSize = 11.sp,
                            color = cs.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text("›", fontSize = 22.sp, color = cs.onPrimaryContainer)
                }
            }

            if (batches.isNotEmpty()) {
                Text(
                    "点进去看这一批的词，长按可改日期或删除",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            if (batches.isEmpty()) {
                Text(
                    "去「导入」把今天的单词存进来，会按日期和次数自动分组。",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grouped.forEach { (date, sessions) ->
                    item(key = "date-$date") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                prettyDate(date),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface
                            )
                            if (date == todayKey) {
                                Spacer(Modifier.width(6.dp))
                                Text("今天", fontSize = 10.sp, color = cs.primary)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${sessions.sumOf { it.total }} 个词",
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }

                    items(sessions, key = { "$date-${it.batchId}-${it.firstId}" }) { b ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = cs.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenSession(b.batchDate, b.batchId) },
                                    onLongClick = { pending = b }
                                )
                        ) {
                            Row(
                                Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sessionLabel(b.batchId),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = cs.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        buildString {
                                            append("${b.total} 个词")
                                            if (b.correct > 0) append(" · 答对 ${b.correct} 次")
                                            if (b.wrong > 0) append(" · ${b.wrong} 个错词")
                                        },
                                        fontSize = 11.sp,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                                Text("›", fontSize = 20.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pending?.let { target ->
        BatchActionDialog(
            target = target,
            onDismiss = { pending = null },
            onMove = { toDate ->
                onMoveSession(target.batchDate, target.batchId, toDate)
                pending = null
            },
            onDelete = {
                pending = null
                deleting = target
            }
        )
    }

    deleting?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onDismiss = { deleting = null },
            onConfirm = {
                onDeleteSession(target.batchDate, target.batchId)
                deleting = null
            }
        )
    }
}

/** 长按某一批：改日期 / 删除 */
@Composable
private fun BatchActionDialog(
    target: BatchRow,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${prettyDate(target.batchDate)} ${sessionLabel(target.batchId)}",
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                "共 ${target.total} 个词。\n\n" +
                    "改日期：整批挪到另一天（补传用），错题记录和复习进度都不动。\n" +
                    "删除：从这一批移除。",
                fontSize = 12.sp
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val cal = Calendar.getInstance()
                runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        .parse(target.batchDate)?.let { cal.time = it }
                }
                DatePickerDialog(
                    ctx,
                    { _, y, m, d ->
                        onMove(String.format(Locale.CHINA, "%04d-%02d-%02d", y, m + 1, d))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) { Text("改日期") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = cs.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    target: BatchRow,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这一批？") },
        text = {
            Text(
                "「${prettyDate(target.batchDate)} ${sessionLabel(target.batchId)}」" +
                    "共 ${target.total} 个词，会从这一批移除。",
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 2026-10-25 → 10 月 25 日 */
internal fun prettyDate(key: String): String {
    val parts = key.split("-")
    if (parts.size != 3) return key
    val m = parts[1].trimStart('0')
    val d = parts[2].trimStart('0')
    return "$m 月 $d 日"
}

/** 批次号是时间戳；0 表示老数据没有记录 */
private fun sessionLabel(batchId: Long): String {
    if (batchId <= 0L) return "早期导入"
    val fmt = SimpleDateFormat("HH:mm", Locale.CHINA)
    return "上传于 " + fmt.format(Date(batchId))
}
