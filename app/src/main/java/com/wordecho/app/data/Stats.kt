package com.wordecho.app.data

/** 按天聚合的答题量（SQLite 直接 group by 出来的一行） */
data class DayAgg(
    /** 本地日期，形如 2026-10-21 */
    val day: String,
    val total: Int,
    val correct: Int
)

/** 某一天的答题情况，用于画柱状图 */
data class DayStat(
    val key: String,
    val label: String,
    val total: Int,
    val correct: Int,
    val isToday: Boolean
) {
    val wrong: Int get() = (total - correct).coerceAtLeast(0)
    val accuracy: Int get() = if (total > 0) correct * 100 / total else 0
}

/** 某一天里的一次上传 */
data class BatchRow(
    val batchDate: String,
    val batchId: Long,
    val total: Int,
    val wrong: Int,
    val correct: Int,
    val firstId: Long = 0L
)

/** 首页统计卡片要用的全部数据 */
data class WeeklyStats(
    val days: List<DayStat> = emptyList(),
    /** 7 天内答题总数 / 答对数 */
    val weekTotal: Int = 0,
    val weekCorrect: Int = 0,
    /** 累计答题数（全部历史） */
    val allAnswered: Int = 0,
    /** 已掌握：复习等级 >= 6 */
    val mastered: Int = 0
) {
    val weekAccuracy: Int get() = if (weekTotal > 0) weekCorrect * 100 / weekTotal else 0
}
