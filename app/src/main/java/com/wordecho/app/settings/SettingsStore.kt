package com.wordecho.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordecho.app.quiz.QuizMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("wordecho_settings")

data class Settings(
    val speechRate: Float = 0.85f,
    val mode: QuizMode = QuizMode.EN2CN,
    val earpiece: Boolean = false,
    val offlineOnly: Boolean = true,
    /** system / light / dark */
    val theme: String = "system",
    val fontScale: Float = 1.0f,
    /** auto（系统优先，没有再用离线）/ system（强制系统）/ offline（强制 Vosk） */
    val asrMode: String = "auto",
    /** 每日提醒 */
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    /** 英文口音：uk（英式，默认）/ us（美式） */
    val accent: String = "uk",
    /** 发音引擎：system（系统 TTS）/ kokoro（端侧神经网络，需下载 82MB 模型） */
    val ttsEngine: String = "system",
    /** DeepSeek API Key，留空则只用本地规则判分 */
    val deepSeekKey: String = "",
    /** 云端识别（OpenAI 兼容网关） */
    val cloudApiKey: String = "",
    val cloudBaseUrl: String = "https://api.siliconflow.cn/v1",
    val cloudOcrModel: String = "Qwen/Qwen2.5-VL-32B-Instruct",
    val cloudAsrModel: String = "FunAudioLLM/SenseVoiceSmall"
)

class SettingsStore(private val context: Context) {

    private val keyRate = floatPreferencesKey("tts_rate")
    private val keyMode = stringPreferencesKey("mode")
    private val keyEarpiece = booleanPreferencesKey("earpiece")
    private val keyOffline = booleanPreferencesKey("offline_only")
    private val keyTheme = stringPreferencesKey("theme")
    private val keyFont = floatPreferencesKey("font_scale")
    private val keyAsrMode = stringPreferencesKey("asr_mode")
    private val keyAccent = stringPreferencesKey("accent")
    private val keyTtsEngine = stringPreferencesKey("tts_engine")
    private val keyReminderOn = booleanPreferencesKey("reminder_on")
    private val keyReminderHour = intPreferencesKey("reminder_hour")
    private val keyReminderMinute = intPreferencesKey("reminder_min")
    private val keyDeepSeek = stringPreferencesKey("deepseek_key")
    private val keyCloudKey = stringPreferencesKey("cloud_api_key")
    private val keyCloudBase = stringPreferencesKey("cloud_base_url")
    private val keyCloudOcrModel = stringPreferencesKey("cloud_ocr_model")
    private val keyCloudAsrModel = stringPreferencesKey("cloud_asr_model")

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            speechRate = p[keyRate] ?: 0.85f,
            mode = runCatching { QuizMode.valueOf(p[keyMode] ?: "EN2CN") }.getOrDefault(QuizMode.EN2CN),
            earpiece = p[keyEarpiece] ?: false,
            offlineOnly = p[keyOffline] ?: true,
            theme = p[keyTheme] ?: "system",
            fontScale = p[keyFont] ?: 1.0f,
            asrMode = p[keyAsrMode] ?: "auto",
            reminderEnabled = p[keyReminderOn] ?: false,
            reminderHour = p[keyReminderHour] ?: 21,
            reminderMinute = p[keyReminderMinute] ?: 0,
            accent = p[keyAccent] ?: "uk",
            ttsEngine = p[keyTtsEngine] ?: "system",
            deepSeekKey = p[keyDeepSeek] ?: "",
            cloudApiKey = p[keyCloudKey] ?: "",
            cloudBaseUrl = p[keyCloudBase] ?: "https://api.siliconflow.cn/v1",
            cloudOcrModel = p[keyCloudOcrModel] ?: "Qwen/Qwen2.5-VL-32B-Instruct",
            cloudAsrModel = p[keyCloudAsrModel] ?: "FunAudioLLM/SenseVoiceSmall"
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun setRate(v: Float) = context.dataStore.edit { it[keyRate] = v }
    suspend fun setMode(v: QuizMode) = context.dataStore.edit { it[keyMode] = v.name }
    suspend fun setEarpiece(v: Boolean) = context.dataStore.edit { it[keyEarpiece] = v }
    suspend fun setOffline(v: Boolean) = context.dataStore.edit { it[keyOffline] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[keyTheme] = v }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[keyFont] = v }
    suspend fun setAsrMode(v: String) = context.dataStore.edit { it[keyAsrMode] = v }
    suspend fun setDeepSeekKey(v: String) = context.dataStore.edit { it[keyDeepSeek] = v }
    suspend fun setAccent(v: String) = context.dataStore.edit { it[keyAccent] = v }
    suspend fun setTtsEngine(v: String) = context.dataStore.edit { it[keyTtsEngine] = v }
    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) =
        context.dataStore.edit {
            it[keyReminderOn] = enabled
            it[keyReminderHour] = hour
            it[keyReminderMinute] = minute
        }
    suspend fun setCloudApiKey(v: String) = context.dataStore.edit { it[keyCloudKey] = v }
    suspend fun setCloudBaseUrl(v: String) = context.dataStore.edit { it[keyCloudBase] = v }
    suspend fun setCloudOcrModel(v: String) = context.dataStore.edit { it[keyCloudOcrModel] = v }
    suspend fun setCloudAsrModel(v: String) = context.dataStore.edit { it[keyCloudAsrModel] = v }
}
