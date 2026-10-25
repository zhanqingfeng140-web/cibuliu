package com.wordecho.app.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 形近词推荐。
 *
 * 用户在「形近词」输入框里打字时，按编辑距离从词库里找拼写相近的词，
 * 做成候选，点一下就补上 —— 不用凭记忆拼，也不会打错。
 *
 * 阈值按长度放宽：短词差 1 个字母就够像了，长词差 3 个也容易看混。
 */
object SimilarWords {

    // 只按逗号/分号切，空格不算分隔符——否则替换时会多出一个空格
    private val separators = charArrayOf(',', '，', ';', '；')

    /** 正在输入的那个词（最后一个逗号之后的部分） */
    fun currentToken(text: String): String {
        val idx = text.lastIndexOfAny(separators)
        return text.substring(idx + 1).trim()
    }

    /** 用推荐的词替换掉正在输入的那个，保留前面已写好的部分 */
    fun replaceToken(text: String, newToken: String): String {
        val idx = text.lastIndexOfAny(separators)
        val prefix = if (idx >= 0) text.substring(0, idx + 1) + " " else ""
        return prefix + newToken + ", "
    }

    /**
     * 从词库里找形近词。
     *
     * @param excludeText 当前正在编辑的词本身，不推荐它自己
     */
    fun suggest(
        token: String,
        bank: List<Word>,
        excludeText: String? = null,
        limit: Int = 6
    ): List<Word> {
        val t = token.trim().lowercase()
        if (t.length < 2) return emptyList()

        val exclude = excludeText?.lowercase()
        val maxDist = when {
            t.length >= 8 -> 3
            t.length >= 5 -> 2
            else -> 1
        }

        return bank.asSequence()
            .map { it.text.lowercase() to it }
            .filter { (text, _) -> text != exclude && text.length >= 2 }
            .filter { (text, _) -> abs(text.length - t.length) <= maxDist }
            .map { (text, w) -> Triple(w, distance(t, text), text) }
            .filter { (_, d, _) -> d in 1..maxDist }        // 排除完全一样的
            .sortedWith(
                compareBy(
                    { it.second },                      // 距离小的优先
                    { !it.third.startsWith(t) },        // 同前缀的优先
                    { it.third.length }                 // 短的优先
                )
            )
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** Levenshtein 编辑距离，带早期退出 */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)

        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                rowMin = min(rowMin, cur[j])
            }
            // 整行最小值已经超过可能的最大距离就没必要算了
            if (rowMin > max(a.length, b.length)) return rowMin
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
