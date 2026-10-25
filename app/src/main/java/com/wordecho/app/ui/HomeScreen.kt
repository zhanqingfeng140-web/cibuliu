package com.wordecho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.data.DayStat
import com.wordecho.app.data.WeeklyStats

@Composable
fun HomeScreen(
    todayAsked: Int,
    todayCorrect: Int,
    total: Int,
    wrongCount: Int,
    dueCount: Int,
    weekly: WeeklyStats,
    modeLabel: String,
    onStart: () -> Unit,
    onReview: () -> Unit,
    onPaste: () -> Unit,
    onWrongBook: () -> Unit,
    onWords: () -> Unit,
    onBatches: () -> Unit,
    onSettings: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val rate = if (todayAsked > 0) "${todayCorrect * 100 / todayAsked}%" else "—"

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(26.dp))
        Text("词不溜", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
        Text(
            "听英文 · 说中文 · 自动判分",
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(22.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock("今日背词", "$todayAsked")
                    StatBlock("正确率", rate)
                    StatBlock("词库", "$total")
                    StatBlock("错题", "$wrongCount")
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    Text("开始背诵", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "当前模式：$modeLabel",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (dueCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onReview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "复习到期的 $dueCount 个词",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (weekly.weekTotal > 0) {
            Spacer(Modifier.height(12.dp))
            WeekCard(weekly)
        }

        Spacer(Modifier.height(14.dp))

        RowOfActions(
            listOf(
                Triple("导入", "JSON / 文本，零误差", onPaste),
                Triple("每日词库", "按日期分开复习", onBatches)
            )
        )

        Spacer(Modifier.height(10.dp))

        RowOfActions(
            listOf(
                Triple("错题本", "$wrongCount 个待攻克", onWrongBook),
                Triple("设置", "语速 · 模式 · 听筒", onSettings)
            )
        )

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun WeekCard(weekly: WeeklyStats) {
    val cs = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "近 7 天",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    "${weekly.weekTotal} 题 · 正确率 ${weekly.weekAccuracy}%",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            WeekChart(weekly.days)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "蓝=答对　红=答错",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Text(
                    "累计 ${weekly.allAnswered} 题 · 已掌握 ${weekly.mastered} 词",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekChart(days: List<DayStat>) {
    val cs = MaterialTheme.colorScheme
    if (days.isEmpty()) return
    val max = days.maxOf { it.total }.coerceAtLeast(1)
    val barMax = 62f

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { d ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (d.total > 0) "${d.total}" else "",
                    fontSize = 9.sp,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .height(barMax.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        Modifier.width(14.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (d.total == 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(cs.outline, RoundedCornerShape(1.dp))
                            )
                        } else {
                            if (d.correct > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((barMax * d.correct / max).dp)
                                        .background(
                                            AccentBlue,
                                            RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                        )
                                )
                            }
                            if (d.wrong > 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height((barMax * d.wrong / max).dp)
                                        .background(ErrorRed.copy(alpha = 0.45f))
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    d.label,
                    fontSize = 9.sp,
                    color = if (d.isToday) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (d.isToday) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}
