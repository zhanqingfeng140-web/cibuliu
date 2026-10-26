package com.wordecho.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {

    @Query("SELECT COUNT(*) FROM words")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<Word>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: Word): Long

    @Update
    suspend fun update(word: Word)

    @Query("SELECT * FROM words WHERE text = :text LIMIT 1")
    suspend fun findByText(text: String): Word?

    @Query("SELECT * FROM words ORDER BY text ASC")
    fun observeAll(): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE wrongCount > 0 ORDER BY wrongCount DESC, text ASC")
    fun observeWrong(): Flow<List<Word>>

    @Query("SELECT * FROM words ORDER BY wrongCount DESC")
    suspend fun allForPick(): List<Word>

    @Query("SELECT * FROM words WHERE wrongCount > 0 ORDER BY wrongCount DESC")
    suspend fun wrongForPick(): List<Word>

    @Query("SELECT * FROM words WHERE nextReviewAt > 0 AND nextReviewAt <= :now ORDER BY nextReviewAt ASC")
    suspend fun dueForPick(now: Long): List<Word>

    @Query("SELECT COUNT(*) FROM words WHERE nextReviewAt > 0 AND nextReviewAt <= :now")
    suspend fun dueCount(now: Long): Int

    @Query("UPDATE words SET wrongCount = wrongCount + 1, lastAskedAt = :now, reviewStage = :stage, nextReviewAt = :next WHERE id = :id")
    suspend fun markWrong(id: Long, now: Long, stage: Int, next: Long)

    @Query("UPDATE words SET correctCount = correctCount + 1, lastAskedAt = :now, reviewStage = :stage, nextReviewAt = :next WHERE id = :id")
    suspend fun markCorrect(id: Long, now: Long, stage: Int, next: Long)

    @Query("UPDATE words SET note = :note, confusables = :confusables WHERE id = :id")
    suspend fun updateNote(id: Long, note: String, confusables: String)

    @Query("DELETE FROM words WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM words WHERE reviewStage >= :stage")
    suspend fun countMastered(stage: Int): Int

    /** 按「日期 + 每次上传」聚合 */
    @Query(
        "SELECT batchDate AS batchDate, batchId AS batchId, COUNT(*) AS total, " +
            "SUM(CASE WHEN wrongCount > 0 THEN 1 ELSE 0 END) AS wrong, " +
            "SUM(correctCount) AS correct, MIN(id) AS firstId " +
            "FROM words WHERE batchDate != '' " +
            "GROUP BY batchDate, batchId " +
            "ORDER BY batchDate DESC, batchId DESC, firstId ASC"
    )
    suspend fun batches(): List<BatchRow>

    @Query("SELECT * FROM words WHERE batchId = :batchId ORDER BY id ASC")
    suspend fun byBatchId(batchId: Long): List<Word>

    /** 删除没有归属任何批次的自建词（清空批次后收尾） */
    @Query(
        "DELETE FROM words WHERE isCustom = 1 AND id NOT IN (SELECT wordId FROM batch_words)"
    )
    suspend fun deleteOrphanCustom()

    @Query("SELECT * FROM words WHERE batchDate = :date ORDER BY id ASC")
    suspend fun batchPool(date: String): List<Word>

    @Query("DELETE FROM words WHERE batchDate = :date")
    suspend fun deleteBatch(date: String)

    /**
     * 整批改日期时同步 words 表上的冗余字段（每日词库的统计、BATCH 模式的池子都靠它）。
     * batchId 也可能被换掉（老数据是 0），所以一起更新。
     */
    @Query(
        "UPDATE words SET batchDate = :toDate, batchId = :toBatchId " +
            "WHERE batchDate = :fromDate AND batchId = :fromBatchId"
    )
    suspend fun moveBatchDate(
        fromDate: String,
        fromBatchId: Long,
        toDate: String,
        toBatchId: Long
    )

    @Query("DELETE FROM words WHERE batchDate = :date AND batchId = :batchId")
    suspend fun deleteSession(date: String, batchId: Long)

    @Query("DELETE FROM words WHERE isCustom = 1")
    suspend fun clearCustom()

    @Query("DELETE FROM words")
    suspend fun clearAll()
}

@Dao
interface BatchWordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(row: BatchWord): Long

    /** 每次上传的统计 */
    @Query(
        "SELECT b.batchDate AS batchDate, b.batchId AS batchId, COUNT(*) AS total, " +
            "SUM(CASE WHEN w.wrongCount > 0 THEN 1 ELSE 0 END) AS wrong, " +
            "SUM(w.correctCount) AS correct, MIN(w.id) AS firstId " +
            "FROM batch_words b JOIN words w ON w.id = b.wordId " +
            "GROUP BY b.batchDate, b.batchId " +
            "ORDER BY b.batchDate DESC, b.batchId DESC, firstId ASC"
    )
    suspend fun sessions(): List<BatchRow>

    /** 某一次上传包含的词 */
    @Query(
        "SELECT w.* FROM words w JOIN batch_words b ON b.wordId = w.id " +
            "WHERE b.batchDate = :date AND b.batchId = :batchId ORDER BY w.id ASC"
    )
    suspend fun pool(date: String, batchId: Long): List<Word>

    @Query("DELETE FROM batch_words WHERE batchDate = :date AND batchId = :batchId")
    suspend fun clear(date: String, batchId: Long)

    /**
     * 把某一次上传整体搬到另一天。
     *
     * 用 INSERT OR REPLACE 而不是 UPDATE 是因为主键包含 batchDate：
     * 直接改主键字段在 SQLite 里容易撞键，先复制到新日期再删旧的更稳。
     */
    @Query(
        "INSERT OR REPLACE INTO batch_words(batchDate, batchId, wordId, addedAt) " +
            "SELECT :toDate, :toBatchId, wordId, addedAt FROM batch_words " +
            "WHERE batchDate = :fromDate AND batchId = :fromBatchId"
    )
    suspend fun moveToDate(
        fromDate: String,
        fromBatchId: Long,
        toDate: String,
        toBatchId: Long
    )
}

@Dao
interface AnswerLogDao {

    @Insert
    suspend fun insert(log: AnswerLog)

    @Query("SELECT COUNT(*) FROM answer_log WHERE answeredAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM answer_log WHERE answeredAt >= :since AND correct = 1")
    suspend fun correctSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM answer_log")
    suspend fun totalCount(): Int

    /**
     * 按「本地日期」聚合。用 SQLite 的 localtime 修饰符，
     * 避免直接拿毫秒除以 86400000 导致跨时区日期错位。
     */
    @Query(
        "SELECT date(answeredAt / 1000, 'unixepoch', 'localtime') AS day, " +
            "COUNT(*) AS total, " +
            "SUM(CASE WHEN correct = 1 THEN 1 ELSE 0 END) AS correct " +
            "FROM answer_log WHERE answeredAt >= :since GROUP BY day ORDER BY day"
    )
    suspend fun dailySince(since: Long): List<DayAgg>

    @Query("DELETE FROM answer_log")
    suspend fun clear()
}
