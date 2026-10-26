package com.wordecho.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归测试：之前「上传后词会变少」。
 *
 * 原因是 words 表对 text 建了唯一索引，已存在的词直接被跳过。
 * 现在拆成 words + batch_words 多对多，同一个词可以出现在多次上传里。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepositoryBatchTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WordRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WordRepository(db.wordDao(), db.logDao(), db.batchWordDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `同一个词第二次上传仍能进新批次`() = runBlocking {
        // 第一次上传
        assertTrue(repo.addCustom("abandon", "v. 放弃", "2026-10-25", 1001L))
        // 第二次上传（同一天不同批次）—— 以前这里会被去重掉
        assertTrue(repo.addCustom("abandon", "v. 放弃", "2026-10-25", 1002L))

        val sessions = repo.batches()
        assertEquals(2, sessions.size)
        assertEquals(1, sessions[0].total)
        assertEquals(1, sessions[1].total)
    }

    @Test
    fun `同一批次内重复的词不会重复计数`() = runBlocking {
        assertTrue(repo.addCustom("vague", "adj. 模糊的", "2026-10-25", 2001L))
        assertFalse(repo.addCustom("vague", "adj. 模糊的", "2026-10-25", 2001L))

        val sessions = repo.batches()
        assertEquals(1, sessions.size)
        assertEquals(1, sessions[0].total)
    }

    @Test
    fun `上传多少就是多少`() = runBlocking {
        val words = listOf("abandon", "abundant", "vague", "resilient", "ambiguous")
        words.forEach { repo.addCustom(it, "释义", "2026-10-25", 3001L) }

        val sessions = repo.batches()
        assertEquals(1, sessions.size)
        assertEquals(words.size, sessions[0].total)

        val pool = repo.sessionPool("2026-10-25", 3001L)
        assertEquals(words.size, pool.size)
    }

    @Test
    fun `内置词库已有的词也能加入当天批次`() = runBlocking {
        // 模拟一个已存在的内置词
        db.wordDao().upsert(Word(text = "abandon", meaning = "v. 放弃", isCore = true))
        val before = db.wordDao().count()

        assertTrue(repo.addCustom("abandon", "", "2026-10-25", 4001L))

        // 词表没多一条（复用已有记录），但批次里有了
        assertEquals(before, db.wordDao().count())
        assertEquals(1, repo.sessionPool("2026-10-25", 4001L).size)
    }

    @Test
    fun `删除某一次上传只影响那一批`() = runBlocking {
        repo.addCustom("abandon", "放弃", "2026-10-25", 5001L)
        repo.addCustom("vague", "模糊", "2026-10-25", 5002L)

        repo.deleteSession("2026-10-25", 5001L)

        val sessions = repo.batches()
        assertEquals(1, sessions.size)
        assertEquals(5002L, sessions[0].batchId)
        // abandon 没有被别的批次引用，作为孤儿自建词被清掉
        assertEquals(null, db.wordDao().findByText("abandon"))
        // vague 还在
        assertEquals("vague", db.wordDao().findByText("vague")?.text)
    }

    // ---------------- 改日期（补传） ----------------

    @Test
    fun `整批改日期后落到新的一天`() = runBlocking {
        repo.addCustom("abandon", "放弃", "2026-10-25", 6001L)
        repo.addCustom("vague", "模糊", "2026-10-25", 6001L)

        repo.moveSession("2026-10-25", 6001L, "2026-10-20")

        val sessions = repo.batches()
        assertEquals(1, sessions.size)
        assertEquals("2026-10-20", sessions[0].batchDate)
        assertEquals(2, sessions[0].total)
        // 新日期查得到，旧日期空了
        assertEquals(2, repo.sessionPool("2026-10-20", 6001L).size)
        assertEquals(0, repo.sessionPool("2026-10-25", 6001L).size)
        // words 表上的冗余日期也跟着走
        assertEquals(0, repo.batchPool("2026-10-25").size)
    }

    @Test
    fun `改日期只动选中的那一批`() = runBlocking {
        repo.addCustom("abandon", "放弃", "2026-10-25", 7001L)
        repo.addCustom("vague", "模糊", "2026-10-25", 7002L)

        repo.moveSession("2026-10-25", 7001L, "2026-10-20")

        val sessions = repo.batches()
        assertEquals(2, sessions.size)
        // 按日期倒序：10-25 那批留在原地，10-20 是刚挪过去的
        assertEquals("2026-10-25", sessions[0].batchDate)
        assertEquals(7002L, sessions[0].batchId)
        assertEquals("2026-10-20", sessions[1].batchDate)
        assertEquals(7001L, sessions[1].batchId)
    }

    @Test
    fun `改日期不影响错题和复习进度`() = runBlocking {
        repo.addCustom("abandon", "放弃", "2026-10-25", 8001L)
        val w = db.wordDao().findByText("abandon")!!
        repo.markWrong(w)

        repo.moveSession("2026-10-25", 8001L, "2026-10-20")

        val after = db.wordDao().findByText("abandon")!!
        assertEquals(1, after.wrongCount)
        assertEquals(0, after.reviewStage)
        assertTrue(after.nextReviewAt > 0)
    }

    @Test
    fun `老数据批次号为零时不与其他批次合并`() = runBlocking {
        // 早期导入没有批次号：两天各有一条 batchId = 0 的记录
        repo.addCustom("abandon", "放弃", "2026-10-25", 0L)
        repo.addCustom("vague", "模糊", "2026-10-20", 0L)

        repo.moveSession("2026-10-25", 0L, "2026-10-20")

        val sessions = repo.batches()
        // 两批都在 10-20，但还是两批（主键没撞，也没被合成一批）
        assertEquals(2, sessions.size)
        assertEquals(setOf("2026-10-20"), sessions.map { it.batchDate }.toSet())
        assertEquals(setOf(1), sessions.map { it.total }.toSet())
    }
}
