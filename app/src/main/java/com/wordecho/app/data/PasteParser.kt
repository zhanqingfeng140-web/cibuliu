package com.wordecho.app.data

/**
 * 粘贴词表解析。
 *
 * 用户从单词书 / Excel / 网页复制过来的格式千奇百怪，这里尽量都吃下：
 *
 *   abandon
 *   abundant
 *
 *   abandon, abundant, vague
 *
 *   abandon	放弃；抛弃
 *   abundant 丰富的
 *
 *   abandon 放弃
 *
 * 规则：一行里如果出现中文或 Tab，就当成「单词 + 释义」；
 * 纯英文的一行按空白拆成多个单词。
 */
object PasteParser {

    private val fences = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```")

    /** 去掉 LLM 常见的 ```json 代码块包裹 */
    private fun stripFences(raw: String): String {
        val m = fences.find(raw)
        return (m?.groupValues?.get(1) ?: raw).trim()
    }

    /**
     * 解析 JSON 词表。支持四种常见形状：
     *   ["abandon", "abundant"]
     *   [{"word":"abandon","meaning":"v. 放弃"}]
     *   {"words":[{"word":"abandon","meaning":"放弃"}]}
     *   {"abandon":"放弃","abundant":"丰富的"}
     */
    private fun parseJson(text: String): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()

        fun addWord(raw: String?, rawMeaning: String?) {
            val w = (raw ?: "").trim().lowercase()
                .replace(Regex("[^a-z'\\-]"), "")
            if (w.length < 2) return
            out.putIfAbsent(w, (rawMeaning ?: "").trim())
        }

        fun takeObject(o: org.json.JSONObject) {
            val word = listOf("word", "text", "en", "english", "name", "单词", "英文")
                .firstNotNullOfOrNull { k -> o.optString(k, "").takeIf { it.isNotBlank() } }
            val meaning = listOf("meaning", "zh", "cn", "chinese", "translation", "def", "释义", "中文")
                .firstNotNullOfOrNull { k -> o.optString(k, "").takeIf { it.isNotBlank() } }
            addWord(word, meaning)
        }

        fun takeArray(a: org.json.JSONArray) {
            for (i in 0 until a.length()) {
                when (val el = a.opt(i)) {
                    is org.json.JSONObject -> takeObject(el)
                    is String -> addWord(el, null)
                }
            }
        }

        val root: Any = if (text.startsWith("[")) {
            org.json.JSONArray(text)
        } else {
            org.json.JSONObject(text)
        }

        when (root) {
            is org.json.JSONArray -> takeArray(root)
            is org.json.JSONObject -> {
                val inner = root.optJSONArray("words") ?: root.optJSONArray("list")
                if (inner != null) {
                    takeArray(inner)
                } else {
                    // 当成 {单词: 释义} 的映射
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = root.opt(k)
                        if (v is String) addWord(k, v)
                    }
                }
            }
        }
        return out.map { it.key to it.value }
    }

    private val wordOnly = Regex("^[A-Za-z'\\- ]+$")
    private val leadingWord = Regex("^([A-Za-z][A-Za-z'\\-]*)[\\s\\t:：,，]+(.*)$")

    fun parse(raw: String): List<Pair<String, String>> {
        val cleaned = stripFences(raw)
        if (cleaned.startsWith("[") || cleaned.startsWith("{")) {
            val json = runCatching { parseJson(cleaned) }.getOrNull()
            if (!json.isNullOrEmpty()) return json
        }
        return parsePlain(cleaned)
    }

    private fun parsePlain(raw: String): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()

        raw.lineSequence().forEach { lineRaw ->
            val line = lineRaw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            // 带中文 / Tab / 冒号 → 单词 + 释义
            val hasCjk = line.any { it.code in 0x4E00..0x9FFF }
            if (hasCjk || line.contains('\t')) {
                val m = leadingWord.find(line)
                if (m != null) {
                    val w = m.groupValues[1].lowercase()
                    val meaning = m.groupValues[2].trim().trim(',', '，', ';', '；')
                    if (w.length >= 2) out.putIfAbsent(w, meaning)
                }
                return@forEach
            }

            // 纯英文：可能一行多个词
            if (wordOnly.matches(line)) {
                line.split(Regex("[\\s,，;；、/|]+"))
                    .map { it.trim() }
                    .filter { it.length >= 2 }
                    .forEach { out.putIfAbsent(it.lowercase(), "") }
                return@forEach
            }

            // 混合：逐个 token 取英文部分
            line.split(Regex("[\\s,，;；、/|]+"))
                .map { it.replace(Regex("[^A-Za-z'\\-]"), "") }
                .filter { it.length >= 2 }
                .forEach { out.putIfAbsent(it.lowercase(), "") }
        }

        return out.map { it.key to it.value }
    }
}
