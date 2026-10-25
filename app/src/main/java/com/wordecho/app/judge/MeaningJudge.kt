package com.wordecho.app.judge

import com.wordecho.app.data.Word
import com.wordecho.app.quiz.QuizMode

data class JudgeResult(
    val correct: Boolean,
    /** 0.0 ~ 1.0，用于「接近答案」的提示 */
    val confidence: Double,
    val note: String = ""
)

/**
 * 判分抽象。默认用 [FuzzyJudge]（纯本地规则，零算力）；
 * 后续接入 3B 小模型时只需再实现一个类替换即可。
 */
interface MeaningJudge {
    fun judge(answer: String, word: Word, mode: QuizMode): JudgeResult
}
