package com.wordecho.app.ui

import com.wordecho.app.data.BatchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 每日词库「查找日期」的筛选逻辑。
 *
 * 纯函数，不碰数据库也不用渲染界面。
 */
class BatchFilterTest {

    private fun row(date: String, id: Long, total: Int = 10) = BatchRow(
        batchDate = date,
        batchId = id,
        total = total,
        wrong = 0,
        correct = 0,
        firstId = id
    )

    private val all = listOf(
        row("2026-10-26", 3001L, 8),
        row("2026-10-26", 3002L, 12),
        row("2026-10-25", 2001L, 20),
        row("2026-10-20", 1001L, 5)
    )

    @Test
    fun `不选日期时按日期分组全部返回`() {
        val g = groupBatches(all)
        assertEquals(listOf("2026-10-26", "2026-10-25", "2026-10-20"), g.keys.toList())
        assertEquals(2, g["2026-10-26"]?.size)
        assertEquals(4, g.values.sumOf { it.size })
    }

    @Test
    fun `选了一天只剩那天的上传`() {
        val g = groupBatches(all, "2026-10-25")
        assertEquals(setOf("2026-10-25"), g.keys)
        assertEquals(2001L, g["2026-10-25"]?.first()?.batchId)
    }

    @Test
    fun `同一天的多次上传都保留`() {
        val g = groupBatches(all, "2026-10-26")
        assertEquals(listOf(3001L, 3002L), g["2026-10-26"]?.map { it.batchId })
        assertEquals(20, g["2026-10-26"]?.sumOf { it.total })
    }

    @Test
    fun `那天没传过时结果为空`() {
        assertTrue(groupBatches(all, "2026-10-24").isEmpty())
    }

    @Test
    fun `日期写法不完整不会误命中`() {
        assertTrue(groupBatches(all, "2026-10-2").isEmpty())
        assertTrue(groupBatches(all, "2026-1-2").isEmpty())
        assertTrue(groupBatches(all, "").isEmpty())
    }
}
