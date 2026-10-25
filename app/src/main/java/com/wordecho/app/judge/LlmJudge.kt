package com.wordecho.app.judge

import com.wordecho.app.data.Word
import com.wordecho.app.quiz.QuizMode

/**
 * 端侧小模型判分的接入点（对应方案第 6 步：3B INT4）。
 *
 * 设计原则 —— 模型只做「裁判」，不做生成：
 *  - 先跑规则判分 [FuzzyJudge]
 *  - 只有落在模糊区间（0.15 ~ 0.9）时才问小模型
 *  - 模型只输出「算对 / 算错」，prompt 极短，token 消耗最低
 *
 * 目前 [LocalLlm] 默认是空实现，App 完全靠规则即可跑通。
 * 接 llama.cpp 的做法见 README「接入 3B 小模型」一节。
 */
interface LocalLlm {
    fun isLoaded(): Boolean
    /** 阻塞调用，请在后台线程执行。返回 true 表示模型认为答对，null 表示无法判断 */
    fun judgeMeaning(answer: String, meaning: String): Boolean?
}

object NoopLocalLlm : LocalLlm {
    override fun isLoaded(): Boolean = false
    override fun judgeMeaning(answer: String, meaning: String): Boolean? = null
}

class LlmJudge(
    private val fallback: MeaningJudge = FuzzyJudge(),
    private val llm: LocalLlm = NoopLocalLlm
) : MeaningJudge {

    override fun judge(answer: String, word: Word, mode: QuizMode): JudgeResult {
        val base = fallback.judge(answer, word, mode)
        if (!llm.isLoaded()) return base
        if (base.correct || base.confidence >= 0.9 || base.confidence <= 0.15) return base

        val verdict = runCatching { llm.judgeMeaning(answer, word.meaning) }.getOrNull()
            ?: return base
        return if (verdict) {
            JudgeResult(true, 0.8, "模型判定：意思对得上")
        } else {
            JudgeResult(false, base.confidence, "标准释义：${word.meaning}")
        }
    }
}
