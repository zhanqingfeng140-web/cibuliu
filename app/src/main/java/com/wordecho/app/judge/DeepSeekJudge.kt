package com.wordecho.app.judge

import com.wordecho.app.data.Word
import com.wordecho.app.quiz.QuizMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 用 DeepSeek 做「裁判」。
 *
 * 重要：DeepSeek 是文本大模型，**做不了语音识别**。
 * 它能解决的是「听对了但判错了」——比如你说「就是不要了」，
 * 本地规则判错，而模型能理解这等于「放弃」。
 *
 * 省钱策略：先跑本地规则，
 *   - 明确判对（置信度 ≥ 0.9）→ 直接用，不调 API
 *   - 完全不着边（≤ 0.15）→ 直接用，不调 API
 *   - 落在模糊区间 → 才问模型
 * 这样绝大多数答题不花 token。
 */
class DeepSeekJudge(
    private val apiKey: String,
    private val fallback: MeaningJudge = FuzzyJudge()
) : MeaningJudge {

    companion object {
        private const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        private const val MODEL = "deepseek-chat"
        private const val CONNECT_TIMEOUT = 6000
        private const val READ_TIMEOUT = 12000
    }

    override fun judge(answer: String, word: Word, mode: QuizMode): JudgeResult {
        val local = fallback.judge(answer, word, mode)
        if (apiKey.isBlank()) return local
        if (local.correct && local.confidence >= 0.9) return local
        if (local.confidence <= 0.15) return local

        val verdict = ask(answer, word, mode) ?: return local
        return if (verdict) {
            JudgeResult(true, 0.85, "AI 判定：意思对得上（你说的是「$answer」）")
        } else {
            JudgeResult(false, local.confidence, "标准释义：${word.meaning}")
        }
    }

    /** 返回 true=对 / false=错 / null=调用失败（回退本地结果） */
    private fun ask(answer: String, word: Word, mode: QuizMode): Boolean? {
        val question = if (mode == QuizMode.EN2CN) {
            "标准释义：${word.meaning}\n学生说的：$answer"
        } else {
            "标准单词：${word.text}\n学生说的：$answer"
        }
        val prompt = """
            你在批改英语单词默写。判断学生的答案和标准答案意思是否一致，
            只要意思对就算对（同义词、口语化说法都算对），错别字只要不影响理解也算对。
            $question

            只回答一个字：对 或 错。
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0)
            put("max_tokens", 4)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            conn.outputStream.use { out: OutputStream ->
                out.write(body.toByteArray(Charsets.UTF_8))
            }
            if (conn.responseCode !in 200..299) return null
            val text = BufferedReader(
                InputStreamReader(conn.inputStream, Charsets.UTF_8)
            ).use { it.readText() }

            val content = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
                .trim()

            when {
                content.contains("对") && !content.contains("错") -> true
                content.contains("错") -> false
                else -> null
            }
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
