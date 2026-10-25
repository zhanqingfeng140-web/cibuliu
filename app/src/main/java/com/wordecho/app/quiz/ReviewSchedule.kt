package com.wordecho.app.quiz

/**
 * 复习排期（按用户指定的节奏）。
 *
 * 新词当天背完，之后在这几个时间点复习：
 *
 *   第 1 次：当天晚上，背完约 1 小时
 *   第 2 次：第 2 天
 *   第 3 次：第 4 天
 *   第 4 次：第 7 天
 *   第 5 次：第 15 天
 *   第 6 次：第 30 天
 *
 * `reviewStage` 表示「已经答对过几次」：
 *
 *   stage 1 → 1 小时后（当天晚上的那次）
 *   stage 2 → 1 天后（第 2 天）
 *   stage 3 → 2 天后（第 4 天）
 *   stage 4 → 3 天后（第 7 天）
 *   stage 5 → 8 天后（第 15 天）
 *   stage 6 → 15 天后（第 30 天）
 *   stage 7 → 毕业，不再排期
 *
 * 答错：打回 0 级，10 分钟后重来 —— 立刻重刷只会烦人，
 * 但也不能等一天，10 分钟是「还记得刚才错过」的窗口。
 */
object ReviewSchedule {

    private const val MINUTE = 60_000L

    /** 下标 = reviewStage，值 = 该等级后要等多少分钟 */
    private val waitMinutes = longArrayOf(
        0,                  // 0 级：还没答对过，不排期
        60,                 // 1 级 → 1 小时后（当天晚上）
        24 * 60,            // 2 级 → 第 2 天
        2 * 24 * 60,        // 3 级 → 第 4 天
        3 * 24 * 60,        // 4 级 → 第 7 天
        8 * 24 * 60,        // 5 级 → 第 15 天
        15 * 24 * 60,       // 6 级 → 第 30 天
        0                   // 7 级：毕业
    )

    /** 答对 7 次就算掌握了 */
    const val MAX_STAGE = 7

    /** 答错后的重来间隔 */
    private const val RETRY_MINUTES = 10L

    fun intervalMinutes(stage: Int): Long =
        waitMinutes[stage.coerceIn(0, MAX_STAGE)]

    /** 答对：升一级，按新等级排下次复习；到顶则视为毕业（不再排期） */
    fun onCorrect(currentStage: Int, now: Long): Pair<Int, Long> {
        val stage = (currentStage + 1).coerceAtMost(MAX_STAGE)
        val wait = intervalMinutes(stage)
        return if (wait <= 0L) stage to 0L else stage to now + wait * MINUTE
    }

    /** 答错：打回 0 级，10 分钟后重新出现 */
    fun onWrong(now: Long): Pair<Int, Long> = 0 to now + RETRY_MINUTES * MINUTE

    /** 人类可读的「下次复习」文案 */
    fun describe(nextReviewAt: Long, now: Long = System.currentTimeMillis()): String {
        if (nextReviewAt <= 0L) return "未学过"
        val diff = nextReviewAt - now
        if (diff <= 0L) return "该复习了"
        val minutes = diff / MINUTE
        return when {
            minutes < 60 -> "${minutes} 分钟后"
            minutes < 24 * 60 -> "${minutes / 60} 小时后"
            minutes < 30 * 24 * 60 -> "${minutes / (24 * 60)} 天后"
            else -> "${minutes / (30 * 24 * 60)} 个月后"
        }
    }

    /** 第几次复习的说明，给界面用 */
    fun stageLabel(stage: Int): String = when {
        stage <= 0 -> "新词"
        stage >= MAX_STAGE -> "已掌握"
        else -> "第 $stage 次复习后"
    }
}
