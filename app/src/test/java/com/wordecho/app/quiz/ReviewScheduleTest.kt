package com.wordecho.app.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复习节奏按用户指定的时间表：
 * 当天晚上(1h) → 第2天 → 第4天 → 第7天 → 第15天 → 第30天
 */
class ReviewScheduleTest {

    private val minute = 60_000L
    private val day = 24 * 60 * minute
    private val now = 1_700_000_000_000L

    @Test
    fun `答对一次排到1小时后`() {
        val (stage, next) = ReviewSchedule.onCorrect(0, now)
        assertEquals(1, stage)
        assertEquals(now + 60 * minute, next)
    }

    @Test
    fun `六次复习的时间点对得上用户给的表`() {
        // 第1次复习（1 小时后）答对 → 第2天
        val r2 = ReviewSchedule.onCorrect(1, now)
        assertEquals(2, r2.first)
        assertEquals(now + day, r2.second)

        // 第2天答对 → 第4天（+2 天）
        val r3 = ReviewSchedule.onCorrect(2, now)
        assertEquals(3, r3.first)
        assertEquals(now + 2 * day, r3.second)

        // 第4天答对 → 第7天（+3 天）
        val r4 = ReviewSchedule.onCorrect(3, now)
        assertEquals(4, r4.first)
        assertEquals(now + 3 * day, r4.second)

        // 第7天答对 → 第15天（+8 天）
        val r5 = ReviewSchedule.onCorrect(4, now)
        assertEquals(5, r5.first)
        assertEquals(now + 8 * day, r5.second)

        // 第15天答对 → 第30天（+15 天）
        val r6 = ReviewSchedule.onCorrect(5, now)
        assertEquals(6, r6.first)
        assertEquals(now + 15 * day, r6.second)

        // 第30天答对 → 毕业，不再排期
        val r7 = ReviewSchedule.onCorrect(6, now)
        assertEquals(7, r7.first)
        assertEquals(0L, r7.second)
    }

    @Test
    fun `连对到底会毕业不会溢出`() {
        var stage = 0
        repeat(20) { stage = ReviewSchedule.onCorrect(stage, now).first }
        assertEquals(ReviewSchedule.MAX_STAGE, stage)
        assertEquals(0L, ReviewSchedule.onCorrect(stage, now).second)
    }

    @Test
    fun `答错打回0级并10分钟后重来`() {
        val (stage, next) = ReviewSchedule.onWrong(now)
        assertEquals(0, stage)
        assertEquals(now + 10 * minute, next)
    }

    @Test
    fun `各级等待时长`() {
        assertEquals(60L, ReviewSchedule.intervalMinutes(1))
        assertEquals(24 * 60L, ReviewSchedule.intervalMinutes(2))
        assertEquals(2 * 24 * 60L, ReviewSchedule.intervalMinutes(3))
        assertEquals(3 * 24 * 60L, ReviewSchedule.intervalMinutes(4))
        assertEquals(8 * 24 * 60L, ReviewSchedule.intervalMinutes(5))
        assertEquals(15 * 24 * 60L, ReviewSchedule.intervalMinutes(6))
        assertEquals(0L, ReviewSchedule.intervalMinutes(7))
    }

    @Test
    fun `越界等级会被夹住`() {
        assertEquals(0L, ReviewSchedule.intervalMinutes(-5))
        assertEquals(0L, ReviewSchedule.intervalMinutes(99))
    }

    @Test
    fun `文案`() {
        assertEquals("未学过", ReviewSchedule.describe(0L))
        assertEquals("该复习了", ReviewSchedule.describe(now - 1000, now))
        assertTrue(ReviewSchedule.describe(now + 30 * minute, now).contains("分钟"))
        assertTrue(ReviewSchedule.describe(now + 3 * 60 * minute, now).contains("小时"))
        assertTrue(ReviewSchedule.describe(now + 2 * day, now).contains("天"))
    }
}
