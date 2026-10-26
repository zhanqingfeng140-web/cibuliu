package com.wordecho.app.speech

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 验证打包进 APK 的音素表。
 *
 * 这三件事任何一件错了，用户听到的就是错的音或者干脆没声音：
 *  1. 二分查找能在 18 万行里找到词
 *  2. 查出来的音素每个字符都得在 vocab 里有 id（否则合成时会被静默跳过）
 *  3. 多音词取的是名词重音（背单词默认按名词理解）
 */
@RunWith(RobolectricTestRunner::class)
class KokoroLexiconTest {

    private fun lex(): KokoroLexicon = cached ?: run {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        KokoroLexicon.load(ctx).also { cached = it }
    }

    @Test
    fun `内置词库里的词都能查到音素`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val l = lex()
        val words = ctx.assets.open("words_seed.tsv").bufferedReader(Charsets.UTF_8)
            .readLines()
            .map { it.substringBefore('\t').trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertTrue("内置词库不该是空的", words.size > 400)
        val missing = words.filter { l.phonemes(it) == null }
        assertEquals("这些词查不到音素：$missing", 0, missing.size)
    }

    @Test
    fun `大小写和首尾空格不影响查找`() {
        val l = lex()
        val a = l.phonemes("abandon")
        assertNotNull(a)
        assertEquals(a, l.phonemes("Abandon"))
        assertEquals(a, l.phonemes("ABANDON"))
        assertEquals(a, l.phonemes("  abandon  "))
    }

    @Test
    fun `多音词取名词重音`() {
        val l = lex()
        // 名词重音在前：[ˈæbstrækt]；动词在后：[əbˈstrækt]
        assertEquals("ˈæbstɹˌækt", l.phonemes("abstract"))
        assertEquals("kˈɑndˌʌkt", l.phonemes("conduct"))
        assertEquals("kˈɑnflˌɪkt", l.phonemes("conflict"))
    }

    @Test
    fun `查不到的词返回空而不是乱猜`() {
        val l = lex()
        assertNull(l.phonemes("zzzqqqxxxvvv"))
        assertNull(l.phonemes(""))
        assertNull(l.phonemes("   "))
    }

    @Test
    fun `音素字符全都能映射到模型 id`() {
        val l = lex()
        // 抽一批长度和音节差异大的词，覆盖绝大多数音素
        val sample = listOf(
            "abandon", "phenomenon", "entrepreneur", "sophisticated",
            "nevertheless", "ubiquitous", "criterion", "thorough",
            "rhythm", "strengths", "queue", "xylophone", "judge", "vision"
        )
        for (w in sample) {
            val ph = l.phonemes(w)
            assertNotNull("$w 应该有音素", ph)
            for (c in ph!!) {
                assertNotNull("$w 的音素里 ${c} 没有对应 id", l.idOf(c))
            }
        }
    }

    @Test
    fun `自定义导入的词大多也能读`() {
        val l = lex()
        // 用户自己从别处粘进来的词，只要在常用词范围就该命中
        val custom = listOf("resilient", "ambiguous", "paradigm", "substitute", "incentive")
        assertTrue(custom.all { l.phonemes(it) != null })
    }

    companion object {
        /** 4.3MB 的词典加载一次就够了，别让每个用例都读一遍 */
        @Volatile
        private var cached: KokoroLexicon? = null
    }
}
