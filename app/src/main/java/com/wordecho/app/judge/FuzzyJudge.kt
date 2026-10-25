package com.wordecho.app.judge

import com.wordecho.app.data.Word
import com.wordecho.app.quiz.QuizMode

/**
 * 纯本地规则判分，零额外算力，冷启动即可用。
 *
 * 思路：
 *  1. 词库释义按 `；，、/` 切成多个义项
 *  2. 用户口述文本与每个义项比对：完全相等 > 包含 > 最长公共子序列相似度
 *  3. 常见同义词组做一次加权，避免「放弃 / 抛弃」这种被判错
 *  4. 相似度 >= 0.62 判对；0.42 ~ 0.62 判错但提示「很接近」
 */
class FuzzyJudge : MeaningJudge {

    companion object {
        private const val PASS = 0.62
        private const val NEAR = 0.42

        private val posRegex = Regex(
            "(?i)\\b(v|n|adj|adv|prep|conj|pron|num|art|vt|vi|aux|int|pl)\\.\\s*"
        )
        private val splitRegex = Regex("[；;，,、/|（）()【】\\[\\]\\s]+")
        private val fillerRegex = Regex("[\\s，。,.!！?？、；;：:\"'“”‘’（）()【】\\[\\]]")

        /** 考研常见同义替换，命中直接给 0.85 */
        private val synonymGroups: List<Set<String>> = listOf(
            setOf("放弃", "抛弃", "舍弃", "丢弃"),
            setOf("提高", "提升", "增加", "增强", "加强"),
            setOf("减少", "降低", "减弱", "削减", "下降"),
            setOf("重要", "关键", "重大", "要紧"),
            setOf("明显", "显著", "清楚", "显然"),
            setOf("困难", "艰难", "不容易", "棘手"),
            setOf("开始", "着手", "启动", "发起"),
            setOf("结束", "终止", "停止", "完结"),
            setOf("获得", "得到", "取得", "赢得"),
            setOf("认为", "觉得", "以为", "主张"),
            setOf("巨大", "庞大", "宏大", "极大"),
            setOf("谨慎", "小心", "慎重", "审慎"),
            setOf("愤怒", "生气", "恼火", "愤慨"),
            setOf("导致", "引起", "造成", "引发"),
            setOf("支持", "赞成", "拥护", "支援"),
            setOf("反对", "抵制", "抗拒", "不赞成"),
            setOf("利用", "使用", "运用", "采用"),
            setOf("改变", "转变", "更改", "变革"),
            setOf("限制", "约束", "制约", "限定"),
            setOf("假设", "假定", "推测", "设想")
        )
    }

    override fun judge(answer: String, word: Word, mode: QuizMode): JudgeResult =
        when (mode) {
            QuizMode.EN2CN -> judgeChinese(normalizeCn(answer), word)
            QuizMode.CN2EN -> judgeEnglish(normalizeEn(answer), word)
        }

    // ---------------- 英 → 中 ----------------

    private fun judgeChinese(answer: String, word: Word): JudgeResult {
        if (answer.isEmpty()) return JudgeResult(false, 0.0, "没听到回答")

        val senses = splitSenses(word.meaning)
        if (senses.isEmpty()) return JudgeResult(false, 0.0, "词库缺少释义")

        var best = 0.0
        var bestSense = senses.first()
        for (s in senses) {
            val sc = maxOf(score(answer, s), synonymScore(answer, s))
            if (sc > best) {
                best = sc
                bestSense = s
            }
        }

        return when {
            best >= PASS -> JudgeResult(true, best, "")
            best >= NEAR -> JudgeResult(
                false, best,
                "很接近（你说的是「$answer」，标准释义「$bestSense」）"
            )
            else -> JudgeResult(false, best, "标准释义：${word.meaning}")
        }
    }

    private fun splitSenses(meaning: String): List<String> =
        meaning.replace(posRegex, " ")
            .split(splitRegex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun score(answer: String, sense: String): Double {
        if (answer.isEmpty() || sense.isEmpty()) return 0.0
        if (answer == sense) return 1.0
        if (sense.length >= 2 && answer.contains(sense)) return 0.95
        if (answer.length >= 2 && sense.contains(answer)) {
            return (0.92 * answer.length.toDouble() / sense.length).coerceAtLeast(0.55)
        }
        if (sense.length == 1 || answer.length == 1) return 0.0
        val lcs = lcsLength(answer, sense)
        return 2.0 * lcs / (answer.length + sense.length)
    }

    private fun synonymScore(answer: String, sense: String): Double {
        for (g in synonymGroups) {
            val hitA = g.any { answer.contains(it) }
            val hitS = g.any { sense.contains(it) }
            if (hitA && hitS) return 0.85
        }
        return 0.0
    }

    private fun lcsLength(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val prev = IntArray(b.length + 1)
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else maxOf(prev[j], cur[j - 1])
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    private fun normalizeCn(raw: String): String {
        var s = raw.replace(fillerRegex, "")
        for (p in listOf("意思是", "意思就是", "就是", "表示", "它的意思是", "含义是", "嗯", "呃", "啊")) {
            s = s.removePrefix(p)
        }
        return s
    }

    // ---------------- 中 → 英 ----------------

    private fun judgeEnglish(answer: String, word: Word): JudgeResult {
        val target = word.text.lowercase().trim()
        if (answer.isEmpty()) return JudgeResult(false, 0.0, "没听到回答")
        if (answer == target) return JudgeResult(true, 1.0, "")

        val d = editDistance(answer, target)
        val tol = maxOf(1, target.length / 4)
        val sim = 1.0 - d.toDouble() / maxOf(answer.length, target.length)
        return if (d <= tol) {
            JudgeResult(true, sim, "拼写略有偏差（正确：$target）")
        } else {
            JudgeResult(false, sim, "正确拼写：$target")
        }
    }

    private fun normalizeEn(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z'\\-]"), "")

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
