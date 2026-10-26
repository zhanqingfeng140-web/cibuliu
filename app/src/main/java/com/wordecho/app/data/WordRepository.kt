package com.wordecho.app.data

import android.content.Context
import com.wordecho.app.quiz.ReviewSchedule
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class WordRepository(
    private val wordDao: WordDao,
    private val logDao: AnswerLogDao,
    private val batchDao: BatchWordDao
) {

    fun words(): Flow<List<Word>> = wordDao.observeAll()
    fun wrongWords(): Flow<List<Word>> = wordDao.observeWrong()

    // ---------------- 词库装载 ----------------

    /** 首次启动时把 assets 里的种子词库灌进数据库 */
    suspend fun seedIfEmpty(context: Context) {
        if (wordDao.count() > 0) return
        val list = runCatching {
            context.assets.open("words_seed.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { parseLine(it) }.toList()
            }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) wordDao.insertAll(list)
    }

    /**
     * 一行一条：`word \t 中文释义`，也兼容逗号分隔。
     * 空行、# 开头的注释行会被忽略。
     */
    fun parseLine(raw: String): Word? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return null
        val parts = if (line.contains('\t')) line.split('\t') else line.split(',', limit = 2)
        if (parts.size < 2) return null
        val text = parts[0].trim().lowercase()
        val meaning = parts[1].trim()
        if (text.isEmpty() || meaning.isEmpty()) return null
        if (!text.any { it.isLetter() }) return null
        return Word(text = text, meaning = meaning)
    }

    /** 用户从文件导入完整 5500 词库，返回成功解析条数 */
    suspend fun importText(text: String): Int {
        val list = text.lineSequence().mapNotNull { parseLine(it) }.toList()
        if (list.isNotEmpty()) wordDao.insertAll(list)
        return list.size
    }

    suspend fun findByText(text: String): Word? = wordDao.findByText(text.trim().lowercase())

    /** 手写识别后补录生词 */
    /** 今天作为批次键，形如 2026-10-23 */
    fun todayKey(): String = keyOf(java.util.Date())

    /** 任意时间 → 批次键（yyyy-MM-dd） */
    fun keyOf(date: java.util.Date): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(date)

    /** 批次键 → 当天中午 12:00 的时间戳，给老数据补一个像样的 batchId */
    fun noonStamp(key: String): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            .parse("$key 12:00")?.time
    }.getOrNull() ?: System.currentTimeMillis()

    suspend fun batches(): List<BatchRow> = batchDao.sessions()

    suspend fun batchPool(date: String): List<Word> = wordDao.batchPool(date)

    suspend fun sessionPool(date: String, batchId: Long): List<Word> =
        batchDao.pool(date, batchId)

    suspend fun deleteBatch(date: String) = wordDao.deleteBatch(date)

    suspend fun deleteSession(date: String, batchId: Long) {
        batchDao.clear(date, batchId)
        wordDao.deleteOrphanCustom()
    }

    /**
     * 把「某一天的第 N 次上传」整批挪到另一天。
     *
     * 场景：10 月 25 号忘了传，26 号才补上传，想让它回到 25 号那天的词库里。
     * 词的错题、复习等级都不动，只改归属日期，所以挪完进度还在。
     *
     * 老数据的 batchId = 0：如果直接挪，目标日期里已有的 0 号批次会被合并成一批
     * （主键是 batchDate + batchId + wordId），所以要给它补一个目标日期中午的时间戳，
     * 「上传于 HH:mm」的显示逻辑照旧。
     */
    suspend fun moveSession(fromDate: String, batchId: Long, toDate: String) {
        if (fromDate == toDate) return
        val toBatchId = if (batchId > 0L) batchId else noonStamp(toDate)
        batchDao.moveToDate(fromDate, batchId, toDate, toBatchId)
        batchDao.clear(fromDate, batchId)
        wordDao.moveBatchDate(fromDate, batchId, toDate, toBatchId)
    }

    /**
     * 把单词加入某一次上传。
     * @return true 表示这次真的加进去了（同一个词重复上传也能再次加入，但同一批内不会重复）
     */
    suspend fun addCustom(
        text: String,
        meaning: String,
        batchDate: String = todayKey(),
        batchId: Long = 0L
    ): Boolean {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return false

        var w = wordDao.findByText(t)
        if (w == null) {
            wordDao.upsert(
                Word(
                    text = t,
                    meaning = meaning.trim(),
                    isCore = false,
                    isCustom = true,
                    batchDate = batchDate,
                    batchId = batchId
                )
            )
            w = wordDao.findByText(t) ?: return false
        } else if (w.meaning.isBlank() && meaning.isNotBlank()) {
            wordDao.update(w.copy(meaning = meaning.trim()))
        }

        return batchDao.add(
            BatchWord(batchDate = batchDate, batchId = batchId, wordId = w.id)
        ) != -1L
    }

    // ---------------- 答题 ----------------

    suspend fun pool(): List<Word> = wordDao.allForPick()
    suspend fun wrongPool(): List<Word> = wordDao.wrongForPick()
    suspend fun duePool(now: Long = System.currentTimeMillis()): List<Word> =
        wordDao.dueForPick(now)

    suspend fun dueCount(now: Long = System.currentTimeMillis()): Int = wordDao.dueCount(now)

    suspend fun markWrong(w: Word) {
        val now = System.currentTimeMillis()
        val (stage, next) = ReviewSchedule.onWrong(now)
        wordDao.markWrong(w.id, now, stage, next)
        logDao.insert(AnswerLog(wordId = w.id, wordText = w.text, correct = false, answeredAt = now))
    }

    suspend fun markCorrect(w: Word) {
        val now = System.currentTimeMillis()
        val (stage, next) = ReviewSchedule.onCorrect(w.reviewStage, now)
        wordDao.markCorrect(w.id, now, stage, next)
        logDao.insert(AnswerLog(wordId = w.id, wordText = w.text, correct = true, answeredAt = now))
    }

    /** 跳过 / 未作答：只更新时间戳，不计入对错 */
    suspend fun touch(w: Word) {
        wordDao.update(w.copy(lastAskedAt = System.currentTimeMillis()))
    }

    suspend fun delete(w: Word) = wordDao.deleteById(w.id)

    /** 保存个人笔记 / 形近词 */
    suspend fun saveNote(w: Word, note: String, confusables: String) {
        wordDao.updateNote(w.id, note.trim(), confusables.trim())
    }

    // ---------------- 统计 / 导出 ----------------

    suspend fun statsToday(): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val since = cal.timeInMillis
        return logDao.countSince(since) to logDao.correctSince(since)
    }

    /** 近 7 天答题曲线 + 累计数据，首页那张卡片用它 */
    suspend fun weeklyStats(): WeeklyStats {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }

        val rows = runCatching { logDao.dailySince(start.timeInMillis) }
            .getOrDefault(emptyList())
            .associateBy { it.day }

        val keyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val labelFmt = java.text.SimpleDateFormat("M/d", java.util.Locale.CHINA)

        val days = (0..6).map { i ->
            val c = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i - 6) }
            val key = keyFmt.format(c.time)
            val r = rows[key]
            DayStat(
                key = key,
                label = if (i == 6) "今天" else labelFmt.format(c.time),
                total = r?.total ?: 0,
                correct = r?.correct ?: 0,
                isToday = i == 6
            )
        }

        return WeeklyStats(
            days = days,
            weekTotal = days.sumOf { it.total },
            weekCorrect = days.sumOf { it.correct },
            allAnswered = runCatching { logDao.totalCount() }.getOrDefault(0),
            mastered = runCatching { wordDao.countMastered(com.wordecho.app.quiz.ReviewSchedule.MAX_STAGE) }.getOrDefault(0)
        )
    }

    suspend fun exportWrong(): String {
        val list = wordDao.wrongForPick()
        if (list.isEmpty()) return "（错题本是空的）"
        return buildString {
            appendLine("# 错题导出  " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                .format(java.util.Date()))
            appendLine("# 共 ${list.size} 个")
            list.forEach { w ->
                appendLine("${w.text}\t${w.meaning}\t错 ${w.wrongCount} 次")
            }
        }
    }

    suspend fun clearCustom() = wordDao.clearCustom()

    suspend fun clearAll() {
        wordDao.clearAll()
        logDao.clear()
    }

    suspend fun totalCount(): Int = wordDao.count()
}
