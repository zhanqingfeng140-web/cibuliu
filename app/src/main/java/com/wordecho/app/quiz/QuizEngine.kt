package com.wordecho.app.quiz

import com.wordecho.app.data.Word
import com.wordecho.app.data.WordRepository
import kotlin.random.Random

/**
 * 出题策略。
 *
 * MIX / WRONG_ONLY 用权重抽：
 *   权重 = 1
 *        + 错答次数 × 6          // 错题优先
 *        + 核心词加成 ×1.2
 *        + 久未复习加成（最多 +2.1）
 *        + 从未抽到过 ×1.4
 *   答对多于答错的词再 ×0.75，形成「降权 → 少抽」。
 *
 * REVIEW 用艾宾浩斯队列：只取 `nextReviewAt <= now` 的词，按到期先后出。
 */
class QuizEngine(private val repo: WordRepository) {

    /**
     * @param avoid 本轮已经问过的词（小词库里要整轮不重复）
     */
    suspend fun pick(
        session: SessionType,
        excludeId: Long?,
        batchDate: String? = null,
        batchId: Long = 0L,
        avoid: Set<Long> = emptySet()
    ): Word? = when (session) {
        SessionType.BATCH -> {
            val pool = if (batchDate.isNullOrBlank()) {
                emptyList()
            } else {
                repo.sessionPool(batchDate, batchId)
            }
            weighted(pool, excludeId, avoid)
        }
        SessionType.REVIEW -> pickDue(excludeId)
        SessionType.WRONG_ONLY -> weighted(repo.wrongPool(), excludeId, avoid)
        SessionType.MIX -> weighted(repo.pool(), excludeId, avoid)
    }

    /** 复习模式：到期的先来，没到期就不出题（返回 null 让 UI 显示「清完了」） */
    private suspend fun pickDue(excludeId: Long?): Word? {
        val due = repo.duePool()
        if (due.isEmpty()) return null
        return due.firstOrNull { it.id != excludeId } ?: due.first()
    }

    private fun weighted(pool: List<Word>, excludeId: Long?, avoid: Set<Long>): Word? {
        if (pool.isEmpty()) return null

        val notLast = pool.filter { it.id != excludeId }.ifEmpty { pool }
        // 优先出这一轮还没问过的词；整轮都问过了才允许重复
        val candidates = notLast.filter { it.id !in avoid }.ifEmpty { notLast }
        val now = System.currentTimeMillis()

        var total = 0.0
        val weights = DoubleArray(candidates.size)
        for (i in candidates.indices) {
            val w = candidates[i]
            var s = 1.0
            s += w.wrongCount * 6.0
            if (w.isCore) s *= 1.2
            if (w.lastAskedAt == 0L) {
                s *= 1.4
            } else {
                val idleDays = ((now - w.lastAskedAt) / 86_400_000.0).coerceIn(0.0, 7.0)
                s *= 1.0 + idleDays * 0.3
            }
            // 已经到复习期的词再提一档
            if (w.nextReviewAt in 1..now) s *= 1.5
            // 连续答对的词进一步降权
            if (w.correctCount > w.wrongCount) s *= 0.75
            weights[i] = s
            total += s
        }

        if (total <= 0.0) return candidates.random()
        var r = Random.nextDouble() * total
        for (i in candidates.indices) {
            r -= weights[i]
            if (r <= 0.0) return candidates[i]
        }
        return candidates.last()
    }
}
