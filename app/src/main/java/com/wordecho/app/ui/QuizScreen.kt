package com.wordecho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordecho.app.R
import com.wordecho.app.quiz.QuizMode
import com.wordecho.app.quiz.SessionType
import com.wordecho.app.vm.QuizPhase
import com.wordecho.app.vm.QuizUiState

@Composable
fun QuizScreen(
    state: QuizUiState,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onFinish: () -> Unit,
    onRepeat: () -> Unit,
    onSkip: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onSubmitText: (String) -> Unit,
    onReveal: () -> Unit,
    onRestart: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    LaunchedEffect(state.word?.id) { input = "" }

    ScreenScaffold(
        title = if (state.session == SessionType.BATCH && state.batchDate.isNotBlank()) {
            state.batchDate + " 的词库"
        } else {
            state.session.title
        },
        subtitle = "已答 ${state.asked} 题 · 答对 ${state.correct}",
        onBack = onFinish
    ) {
        if (state.phase == QuizPhase.FINISHED) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("本轮结束", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                Spacer(Modifier.height(8.dp))
                val rate = if (state.asked > 0) state.correct * 100 / state.asked else 0
                Text(
                    "背了 ${state.asked} 个词，答对 ${state.correct} 个（$rate%）",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant
                )
                if (state.tip.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.tip, fontSize = 12.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(26.dp))
                if (state.session != SessionType.REVIEW) {
                    Button(
                        onClick = onRestart,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("再来一轮") }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("返回首页") }
            }
            return@ScreenScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            if (!micGranted) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "需要麦克风权限",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "没有录音权限，语音识别无法工作，只能键盘作答。",
                            fontSize = 11.sp,
                            color = cs.onErrorContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onRequestMic,
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(11.dp)
                        ) { Text("授权麦克风", fontSize = 13.sp) }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 上下各一个 weight，让题面在「标题」和「按钮」之间垂直居中，
            // 否则大屏手机上中间会留一大片空白
            Spacer(Modifier.weight(1f))

            // ---- 题面 ----
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val word = state.word
                    if (word == null) {
                        Text("加载中…", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    } else {
                        if (state.mode == QuizMode.EN2CN) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    word.text,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.width(10.dp))
                                IconButton(onClick = onRepeat) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_speaker),
                                        contentDescription = "再读一遍",
                                        tint = cs.primary,
                                        modifier = Modifier.width(24.dp).height(24.dp)
                                    )
                                }
                            }
                            if (state.reveal) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    word.meaning,
                                    fontSize = 17.sp,
                                    color = cs.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Text(
                                word.meaning,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (state.reveal) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    word.text,
                                    fontSize = 22.sp,
                                    color = cs.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (word.wrongCount > 0) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "历史错 ${word.wrongCount} 次",
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant
                            )
                        }
                        if (state.reveal && word.note.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(cs.surfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        "笔记",
                                        fontSize = 10.sp,
                                        color = cs.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(word.note, fontSize = 13.sp, color = cs.onSurface)
                                    if (word.confusables.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "形近词：${word.confusables}",
                                            fontSize = 11.sp,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- 状态 / 判分 ----
            when (state.phase) {
                QuizPhase.RESULT -> ResultBanner(state)
                QuizPhase.LISTENING -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            state.keyboard && state.mode == QuizMode.EN2CN -> "输入中文意思"
                            state.keyboard -> "输入英文单词"
                            state.mode == QuizMode.EN2CN -> "请说出中文意思"
                            else -> "请说出英文单词"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface
                    )
                    if (state.partial.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "「${state.partial}」",
                            fontSize = 16.sp,
                            color = cs.primary,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (state.keyboard) "打完字点右边的「提交」" else "说完自动判分，不用点任何按钮",
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                QuizPhase.SPEAKING -> Text(
                    "正在朗读…",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                QuizPhase.JUDGING -> Text(
                    "判分中…",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                else -> Text(
                    "准备中…",
                    fontSize = 14.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            if (state.tip.isNotBlank() && state.phase != QuizPhase.RESULT) {
                Spacer(Modifier.height(10.dp))
                Text(
                    state.tip,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!state.ttsOk) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "本机没有可用的语音引擎，直接看词作答即可",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.asrLabel.isNotBlank() && state.phase != QuizPhase.FINISHED && !state.keyboard) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "识别方式：${state.asrLabel}",
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.weight(1f))

            // ---- 键盘输入 ----
            if (state.keyboard) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (state.mode == QuizMode.EN2CN) "输入中文释义" else "输入英文单词",
                                fontSize = 13.sp
                            )
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (input.isNotBlank()) {
                                onSubmitText(input)
                                input = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("提交") }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- 操作 ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRepeat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("再说一遍", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onReveal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("看答案", fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("跳过", fontSize = 13.sp) }
            }

            Spacer(Modifier.height(10.dp))

            // 语音 / 键盘 随时互切，每个词都能自己选
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputModeChip("语音作答", !state.keyboard, Modifier.weight(1f)) {
                    if (state.keyboard) onToggleKeyboard()
                }
                InputModeChip("键盘作答", state.keyboard, Modifier.weight(1f)) {
                    if (!state.keyboard) onToggleKeyboard()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResultBanner(state: QuizUiState) {
    val cs = MaterialTheme.colorScheme
    val r = state.result ?: return
    val ok = r.correct
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                if (ok) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                if (ok) "正确" else "答错",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (ok) SuccessGreen else ErrorRed
            )
            Spacer(Modifier.height(6.dp))
            if (state.heard.isNotBlank()) {
                Text("你说：${state.heard}", fontSize = 13.sp, color = cs.onSurface)
                Spacer(Modifier.height(4.dp))
            }
            val word = state.word
            if (!ok && word != null) {
                Text("标准释义：${word.meaning}", fontSize = 13.sp, color = cs.onSurface)
            }
            if (r.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(r.note, fontSize = 12.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InputModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            shape = RoundedCornerShape(11.dp)
        ) { Text(text, fontSize = 12.sp, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(38.dp),
            shape = RoundedCornerShape(11.dp)
        ) { Text(text, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1) }
    }
}
