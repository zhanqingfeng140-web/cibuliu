package com.wordecho.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarWordsTest {

    private fun bank(vararg texts: String) =
        texts.mapIndexed { i, t -> Word(id = i.toLong() + 1, text = t, meaning = "释义") }

    @Test
    fun `编辑距离`() {
        assertEquals(0, SimilarWords.distance("abandon", "abandon"))
        assertEquals(1, SimilarWords.distance("abandon", "abandons"))
        assertEquals(3, SimilarWords.distance("kitten", "sitting"))
        assertEquals(1, SimilarWords.distance("abandn", "abandon"))
        assertEquals(0, SimilarWords.distance("", ""))
    }

    @Test
    fun `取正在输入的那个词`() {
        assertEquals("abdom", SimilarWords.currentToken("abundant, abdom"))
        assertEquals("abdom", SimilarWords.currentToken("abundant，abdom"))
        assertEquals("abdom", SimilarWords.currentToken("abdom"))
        assertEquals("", SimilarWords.currentToken("abundant, "))
    }

    @Test
    fun `替换正在输入的词并保留前面`() {
        assertEquals(
            "abundant, abdomen, ",
            SimilarWords.replaceToken("abundant, abdom", "abdomen")
        )
        assertEquals("abandon, ", SimilarWords.replaceToken("abandn", "abandon"))
    }

    @Test
    fun `拼错时能推荐出正确词`() {
        val bank = bank("abandon", "abundant", "abdomen", "ability", "abstract")
        val s = SimilarWords.suggest("abandn", bank).map { it.text }
        assertTrue("应该推荐出 abandon，实际：$s", s.contains("abandon"))
        assertEquals("abandon", s.first())
    }

    @Test
    fun `不推荐自己也不推荐已填过的`() {
        val bank = bank("abandon", "abundant", "abdomen")
        val s = SimilarWords.suggest("abandon", bank, excludeText = "abandon").map { it.text }
        assertTrue("不能推荐自己：$s", !s.contains("abandon"))
    }

    @Test
    fun `短词要求更严格`() {
        val bank = bank("abandon", "cat", "car", "cut")
        // cat 只差 1 个字母的词才会被推荐
        val s = SimilarWords.suggest("cst", bank).map { it.text }
        assertTrue("cat/cut 差 1，应该出现：$s", s.containsAll(listOf("cat", "cut")))
        assertTrue("abandon 差太远，不该出现：$s", !s.contains("abandon"))
    }

    @Test
    fun `同前缀的排在前面`() {
        val bank = bank("abandon", "rebanon")
        val s = SimilarWords.suggest("abandn", bank).map { it.text }
        assertEquals("abandon", s.first())
    }

    @Test
    fun `太短不推荐`() {
        assertTrue(SimilarWords.suggest("a", bank("abandon")).isEmpty())
        assertTrue(SimilarWords.suggest("", bank("abandon")).isEmpty())
    }
}
