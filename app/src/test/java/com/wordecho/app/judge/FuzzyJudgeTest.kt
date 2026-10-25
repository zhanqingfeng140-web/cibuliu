package com.wordecho.app.judge

import com.wordecho.app.data.Word
import com.wordecho.app.quiz.QuizMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 判分是这款 App 的核心，也是最容易「看起来对、实际判错」的地方。
 * 这里把用户真实会说的话都跑一遍。
 */
class FuzzyJudgeTest {

    private val judge = FuzzyJudge()

    private fun word(text: String, meaning: String) = Word(text = text, meaning = meaning)

    private fun ask(answer: String, w: Word, mode: QuizMode = QuizMode.EN2CN) =
        judge.judge(answer, w, mode)

    // ---------- 英 → 中 ----------

    @Test
    fun `完全相同的释义判对`() {
        val r = ask("放弃", word("abandon", "v. 放弃；抛弃；遗弃"))
        assertTrue(r.correct)
    }

    @Test
    fun `命中第二个义项也判对`() {
        val r = ask("遗弃", word("abandon", "v. 放弃；抛弃；遗弃"))
        assertTrue(r.correct)
    }

    @Test
    fun `同义词替换判对`() {
        // 词库写「放弃」，用户说「抛弃」——同义词表应该兜住
        val r = ask("抛弃", word("abandon", "v. 放弃"))
        assertTrue(r.correct)
    }

    @Test
    fun `带口语句式也能判对`() {
        val r = ask("它的意思是放弃", word("abandon", "v. 放弃"))
        assertTrue(r.correct)
    }

    @Test
    fun `完全不相干的答案判错`() {
        val r = ask("苹果", word("abandon", "v. 放弃；抛弃"))
        assertFalse(r.correct)
    }

    @Test
    fun `空回答判错`() {
        val r = ask("", word("abandon", "v. 放弃"))
        assertFalse(r.correct)
    }

    @Test
    fun `词性标记不应该影响判分`() {
        // 词库里是 "adj. 抽象的；n. 摘要"，用户只说「抽象的」
        val r = ask("抽象的", word("abstract", "adj. 抽象的；n. 摘要"))
        assertTrue(r.correct)
    }

    @Test
    fun `中文里的空格和标点要被忽略`() {
        val r = ask("放 弃。", word("abandon", "v. 放弃"))
        assertTrue(r.correct)
    }

    @Test
    fun `多义项只说一个就算对`() {
        val r = ask("预算", word("budget", "n. 预算；开支计划"))
        assertTrue(r.correct)
    }

    @Test
    fun `沾边的答案判错但给出接近提示`() {
        // 「放肆」和「放弃」共用一个字，相似度落在 0.42~0.62 区间
        val r = ask("放肆", word("abandon", "v. 放弃"))
        assertFalse(r.correct)
        assertTrue("应该提示很接近", r.note.contains("接近"))
    }

    // ---------- 中 → 英 ----------

    @Test
    fun `英文拼写完全正确判对`() {
        val r = ask("abandon", word("abandon", "v. 放弃"), QuizMode.CN2EN)
        assertTrue(r.correct)
    }

    @Test
    fun `英文拼写差一个字母算对`() {
        val r = ask("abandan", word("abandon", "v. 放弃"), QuizMode.CN2EN)
        assertTrue(r.correct)
    }

    @Test
    fun `英文拼写差太多判错`() {
        val r = ask("banana", word("abandon", "v. 放弃"), QuizMode.CN2EN)
        assertFalse(r.correct)
    }

    @Test
    fun `英文大小写不影响`() {
        val r = ask("Abandon", word("abandon", "v. 放弃"), QuizMode.CN2EN)
        assertTrue(r.correct)
    }
}
