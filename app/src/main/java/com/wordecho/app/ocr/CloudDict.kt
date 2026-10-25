package com.wordecho.app.ocr

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 批量查释义（云端 LLM）。
 *
 * 用户的原话：「识别出来的单词，他要自己去找意思，我就不给他上传意思了」。
 * 所以 OCR 之后要自动补释义：
 *   1. 先查本地词库（快、免费）
 *   2. 本地没有的，攒成一批问云端，一次请求拿回十几个词的释义
 *
 * 一次请求而不是逐词请求，是因为逐词会慢十倍且更贵。
 */
class CloudDict {

    fun lookup(
        words: List<String>,
        baseUrl: String,
        apiKey: String,
        model: String
    ): Map<String, String> {
        if (words.isEmpty()) return emptyMap()

        val prompt = buildString {
            appendLine("给出下面每个英文单词在考研英语里最常用的中文释义。")
            appendLine("格式要求：每行「单词<Tab>词性. 释义」，多个义项用；分隔，不要编号，不要解释，不要例句。")
            appendLine("如果某个不是英文单词，直接跳过不要输出。")
            appendLine()
            append("单词：")
            append(words.joinToString("、"))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("max_tokens", 1200)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 40000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return emptyMap()

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val reply = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")

            val wanted = words.map { it.lowercase() }.toSet()
            val out = mutableMapOf<String, String>()
            reply.lineSequence().forEach { line ->
                val clean = line.replace(Regex("^[\\s\\d.、)\\]]+"), "").trim()
                if (clean.isBlank()) return@forEach
                val sep = clean.indexOfFirst { it == '\t' || it == '：' || it == ':' || it == '|' }
                if (sep <= 0) return@forEach
                val word = clean.substring(0, sep)
                    .replace(Regex("[^A-Za-z'\\-]"), "")
                    .lowercase()
                val meaning = clean.substring(sep + 1).trim()
                if (word in wanted && meaning.isNotBlank()) {
                    out[word] = meaning
                }
            }
            out
        } catch (e: Throwable) {
            emptyMap()
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
