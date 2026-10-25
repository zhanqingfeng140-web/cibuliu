package com.wordecho.app.quiz

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wordecho.app.data.AppDatabase
import com.wordecho.app.data.WordRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回归测试：小词库里出题老是重复。
 *
 * 原来 `weighted` 只排除「上一个词」，所以 8 个词的词库里第 3 题
 * 就可能和第 1 题重复。现在传入本轮已问集合，整轮不重复，
 * 一轮问完才重新开始。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuizEngineRepeatTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WordRepository
    private lateinit var engine: QuizEngine

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WordRepository(db.wordDao(), db.logDao(), db.batchWordDao())
        engine = QuizEngine(repo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `小词库里一整轮不会出现重复`() = runBlocking {
        val stamp = 1_000L
        val words = listOf("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh")
        words.forEach { repo.addCustom(it, "释义", "2026-10-25", stamp) }

        val asked = mutableSetOf<Long>()
        var prev: Long? = null
        repeat(words.size) {
            val w = engine.pick(SessionType.BATCH, prev, "2026-10-25", stamp, asked.toSet())
                ?: error("第 ${it + 1} 题应该能取到词")
            asked += w.id
            prev = w.id
        }

        // 8 题下来 8 个不同的词，一个都没重复
        assertEquals(words.size, asked.size)
    }

    @Test
    fun `一轮问完以后允许继续出题`() = runBlocking {
        val stamp = 2_000L
        val words = listOf("aa", "bb", "cc", "dd", "ee", "ff", "gg", "hh")
        words.forEach { repo.addCustom(it, "释义", "2026-10-25", stamp) }

        // 先把整轮问完
        val asked = mutableSetOf<Long>()
        var prev: Long? = null
        repeat(words.size) {
            val w = engine.pick(SessionType.BATCH, prev, "2026-10-25", stamp, asked.toSet())
                ?: error("整轮内应该能取到词")
            asked += w.id
            prev = w.id
        }

        // 再问 3 题，仍然能出词（不会因为全部问过就卡住）
        repeat(3) {
            val w = engine.pick(SessionType.BATCH, prev, "2026-10-25", stamp, asked.toSet())
                ?: error("一轮问完后仍应能继续出题")
            prev = w.id
        }
    }

    @Test
    fun `同一次上传的两个词会连着出现不同的`() = runBlocking {
        val stamp = 3_000L
        repo.addCustom("aa", "释义1", "2026-10-25", stamp)
        repo.addCustom("bb", "释义2", "2026-10-25", stamp)

        val asked = mutableSetOf<Long>()
        var prev: Long? = null
        val seen = mutableListOf<String>()
        repeat(2) {
            val w = engine.pick(SessionType.BATCH, prev, "2026-10-25", stamp, asked.toSet())
                ?: error("应该能取到词")
            asked += w.id
            prev = w.id
            seen += w.text
        }
        // 只有 2 个词时，前 2 题也必须不同
        assertEquals(2, seen.distinct().size)
    }
}
