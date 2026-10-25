package com.wordecho.app.data

import androidx.room.Entity

/**
 * 单词与「某一次上传」的归属关系。
 *
 * 为什么单独一张表：一个单词可能被多次上传（今天传一遍、下周又传一遍），
 * 但它应该只有一条学习记录（错题、复习等级共用）。
 * 如果直接在 words 表上挂 batchId，就变成「一个词只能属于一批」，
 * 重复上传会被去重掉——这正是之前「上传后词变少」的原因。
 *
 * 所以拆成多对多：words 保持唯一，batch_words 记录每次上传包含哪些词。
 */
@Entity(
    tableName = "batch_words",
    primaryKeys = ["batchDate", "batchId", "wordId"]
)
data class BatchWord(
    val batchDate: String,
    val batchId: Long,
    val wordId: Long,
    val addedAt: Long = 0L
)
