package com.wordecho.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.wordecho.app.quiz.QuizMode
import com.wordecho.app.settings.Settings
import com.wordecho.app.speech.KokoroModelStore

@Composable
fun SettingsScreen(
    settings: Settings,
    asrCnReady: Boolean,
    asrEnReady: Boolean,
    modelBusy: Boolean,
    modelMsg: String,
    onBack: () -> Unit,
    onRate: (Float) -> Unit,
    onAccent: (String) -> Unit,
    onTtsEngine: (String) -> Unit,
    kokoroState: KokoroModelStore.State,
    systemVoiceOk: Boolean,
    onDownloadKokoro: () -> Unit,
    onDeleteKokoro: () -> Unit,
    onTestKokoro: () -> Unit,
    onReminder: (Boolean, Int, Int) -> Unit,
    onMode: (QuizMode) -> Unit,
    onEarpiece: (Boolean) -> Unit,
    onOffline: (Boolean) -> Unit,
    onAsrMode: (String) -> Unit,
    onDeepSeekKey: (String) -> Unit,
    onCloudKey: (String) -> Unit,
    onCloudBase: (String) -> Unit,
    onCloudOcrModel: (String) -> Unit,
    onTheme: (String) -> Unit,
    onFontScale: (Float) -> Unit,
    onImportVoskCn: (Uri) -> Unit,
    onImportVoskEn: (Uri) -> Unit,
    onClearCustom: () -> Unit,
    onClearAll: () -> Unit
) {
    val cs = MaterialTheme.colorScheme

    val pickVoskCn = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) onImportVoskCn(uri) }

    val pickVoskEn = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) onImportVoskEn(uri) }

    ScreenScaffold(title = "设置", subtitle = "全部本地运行，随时可改", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingCard("背诵模式") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("看英文说中文", settings.mode == QuizMode.EN2CN, Modifier.weight(1f)) {
                        onMode(QuizMode.EN2CN)
                    }
                    ModeChip("看中文说英文", settings.mode == QuizMode.CN2EN, Modifier.weight(1f)) {
                        onMode(QuizMode.CN2EN)
                    }
                }
            }

            SettingCard("每日复习提醒") {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val hhmm = String.format("%02d:%02d", settings.reminderHour, settings.reminderMinute)
                SwitchRow(
                    title = "每天提醒我复习",
                    desc = if (settings.reminderEnabled) "每天 $hhmm 通知你该背哪些词" else "按 1小时/第2天/第4天/第7天/第15天/第30天 排期",
                    checked = settings.reminderEnabled,
                    onChange = { onReminder(it, settings.reminderHour, settings.reminderMinute) }
                )
                if (settings.reminderEnabled) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            android.app.TimePickerDialog(
                                ctx,
                                { _, h, m -> onReminder(true, h, m) },
                                settings.reminderHour,
                                settings.reminderMinute,
                                true
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(11.dp)
                    ) { Text("提醒时间：$hhmm", fontSize = 13.sp) }
                }
                Spacer(Modifier.height(8.dp))
                TipText("通知里会直接列出当天该复习的单词。手机开了省电模式可能会延迟几分钟。")
            }

            SettingCard("英文发音") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("英式", settings.accent != "us", Modifier.weight(1f)) { onAccent("uk") }
                    ModeChip("美式", settings.accent == "us", Modifier.weight(1f)) { onAccent("us") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("系统英文语音包", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (systemVoiceOk) "已安装" else "未检测到",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (systemVoiceOk) SuccessGreen else cs.error
                    )
                }
                if (!systemVoiceOk) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "手机里没有英文语音包，系统朗读会用中文引擎硬念英文，这就是口音怪的原因。",
                        fontSize = 11.5.sp,
                        color = cs.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                TipText("英式用 en-GB 语音包。手机没装英式语音包时会自动退回美式，不会没声音。")
            }

            SettingCard("发音引擎") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("系统朗读", settings.ttsEngine != "kokoro", Modifier.weight(1f)) {
                        onTtsEngine("system")
                    }
                    ModeChip("Kokoro 神经网络", settings.ttsEngine == "kokoro", Modifier.weight(1f)) {
                        onTtsEngine("kokoro")
                    }
                }
                Spacer(Modifier.height(10.dp))
                when (val st = kokoroState) {
                    is KokoroModelStore.State.Downloading -> {
                        val pct = if (st.total > 0) (st.done * 100 / st.total).toInt() else 0
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在下载 $pct%　${sizeText(st.done)} / ${sizeText(st.total)}",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        TipText("可以退出这页，下载会继续。中途断了下次从断点接着下，不用从头来。")
                    }

                    is KokoroModelStore.State.Ready -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onTestKokoro,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp)
                            ) { Text("试听", fontSize = 13.sp) }
                            OutlinedButton(
                                onClick = onDeleteKokoro,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(11.dp)
                            ) { Text("删除模型", fontSize = 13.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        TipText(
                            "已就绪。第一次听某个词要等 0.5–2 秒（手机在算），" +
                                "同一个词再听是直接读缓存、瞬间出声。"
                        )
                    }

                    is KokoroModelStore.State.Failed -> {
                        Text("下载失败：${st.message}", fontSize = 12.sp, color = cs.error)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onDownloadKokoro,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        ) { Text("重试", fontSize = 13.sp) }
                    }

                    else -> {
                        Button(
                            onClick = onDownloadKokoro,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(11.dp)
                        ) { Text("下载发音模型（82MB）", fontSize = 13.sp) }
                        Spacer(Modifier.height(8.dp))
                        TipText(
                            "内置 18 万个常见词的音素，你自己导入的词也大多能读；" +
                                "查不到的词自动退回系统朗读，不会变哑。模型不放进安装包，所以 APK 不涨体积。"
                        )
                    }
                }
            }

            SettingCard("语音识别") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("自动", settings.asrMode == "auto", Modifier.weight(1f)) { onAsrMode("auto") }
                    ModeChip("系统识别", settings.asrMode == "system", Modifier.weight(1f)) { onAsrMode("system") }
                    ModeChip("离线 Vosk", settings.asrMode == "offline", Modifier.weight(1f)) { onAsrMode("offline") }
                }
                Spacer(Modifier.height(8.dp))
                TipText("自动：系统识别可用就用（免下载、中文更准），否则退回离线。系统识别可能走厂商云端，介意隐私请选「离线 Vosk」。")
            }

            SettingCard("朗读语速") {
                Text(
                    "当前 " + String.format("%.2f", settings.speechRate) + "x",
                    fontSize = 13.sp,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.speechRate,
                    onValueChange = onRate,
                    valueRange = 0.5f..1.3f,
                    steps = 15
                )
                TipText("考试听力大约 1.0x，刚开始练建议 0.8x 左右。")
            }

            SettingCard("声音走听筒") {
                SwitchRow(
                    title = "像打电话一样贴在耳边",
                    desc = "开启后从听筒出声，公共场所不容易漏音；麦克风也会切到通话通道",
                    checked = settings.earpiece,
                    onChange = onEarpiece
                )
            }

            SettingCard("离线优先") {
                SwitchRow(
                    title = "禁止联网合成语音",
                    desc = "语音合成只走本地引擎。识别是否联网取决于上面的引擎选择。",
                    checked = settings.offlineOnly,
                    onChange = onOffline
                )
            }

            SettingCard("离线语音模型") {
                StatusLine("中文语音识别", asrCnReady)
                StatusLine("英文语音识别", asrEnReady)

                Spacer(Modifier.height(10.dp))
                TipText("中文模型已内置。英文模型需要自己下载 zip，传到手机后点下面按钮导入。")

                if (modelBusy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("处理中…", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(10.dp))
                ImportRow("导入中文语音模型 .zip", !modelBusy) {
                    pickVoskCn.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                Spacer(Modifier.height(8.dp))
                ImportRow("导入英文语音模型 .zip", !modelBusy) {
                    pickVoskEn.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }

                if (modelMsg.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(modelMsg, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }

            SettingCard("云端识别（OpenAI 兼容）") {
                var key by remember(settings.cloudApiKey) { mutableStateOf(settings.cloudApiKey) }
                var base by remember(settings.cloudBaseUrl) { mutableStateOf(settings.cloudBaseUrl) }
                var model by remember(settings.cloudOcrModel) { mutableStateOf(settings.cloudOcrModel) }

                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("API Key", fontSize = 11.sp) },
                    placeholder = { Text("sk-…", fontSize = 12.sp) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = base,
                    onValueChange = { base = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Base URL", fontSize = 11.sp) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("文本模型", fontSize = 11.sp) }
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onCloudKey(key.trim())
                        onCloudBase(base.trim())
                        onCloudOcrModel(model.trim())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("保存云端配置", fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                TipText("默认硅基流动（api.siliconflow.cn/v1）。填了 Key 之后，导入时本地词库查不到的单词会自动向云端要释义。")
            }

            SettingCard("AI 判分（可选）") {
                var keyInput by remember(settings.deepSeekKey) { mutableStateOf(settings.deepSeekKey) }
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("DeepSeek API Key", fontSize = 11.sp) },
                    placeholder = { Text("sk-… 留空则只用本地规则", fontSize = 12.sp) }
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onDeepSeekKey(keyInput.trim()) },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("保存", fontSize = 13.sp) }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (settings.deepSeekKey.isNotBlank()) "已启用 AI 判分" else "未启用",
                        fontSize = 11.sp,
                        color = if (settings.deepSeekKey.isNotBlank()) SuccessGreen else cs.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                TipText("只影响判分，不影响识别。填了之后，本地规则拿不准的答案会交给 DeepSeek 判断。")
            }

            SettingCard("外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("跟随系统", settings.theme == "system", Modifier.weight(1f)) { onTheme("system") }
                    ModeChip("浅色", settings.theme == "light", Modifier.weight(1f)) { onTheme("light") }
                    ModeChip("深色", settings.theme == "dark", Modifier.weight(1f)) { onTheme("dark") }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "字号 " + String.format("%.1f", settings.fontScale) + "x",
                    fontSize = 13.sp,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = settings.fontScale,
                    onValueChange = onFontScale,
                    valueRange = 0.85f..1.4f,
                    steps = 10
                )
            }

            SettingCard("数据") {
                OutlinedButton(
                    onClick = onClearCustom,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp)
                ) { Text("清除所有自建单词", fontSize = 13.sp) }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp)
                ) { Text("清空全部数据并重新灌词库", fontSize = 13.sp) }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------------------------------------------------------- 小组件

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            shape = RoundedCornerShape(11.dp)
        ) {
            Text(text, fontSize = 12.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            shape = RoundedCornerShape(11.dp)
        ) {
            Text(text, fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = cs.onSurface, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            if (ok) "已就绪" else "未就绪",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (ok) SuccessGreen else cs.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

/** 字节数转成人话：82MB 比 86033585 好懂 */
private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / 1073741824.0)
    bytes >= 1024L * 1024 -> String.format("%.0f MB", bytes / 1048576.0)
    else -> String.format("%.0f KB", bytes / 1024.0)
}
