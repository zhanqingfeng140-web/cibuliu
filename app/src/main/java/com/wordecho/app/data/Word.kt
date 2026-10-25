package com.wordecho.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条单词记录。
 * - isCore：是否来自考研核心词库（影响抽题权重）
 * - isCustom：手写识别后人工补录的生词
 * - wrongCount / correctCount：用于错题本与降权
 * - lastAskedAt：上次被抽到的时间戳，用于「久未复习优先」
 */
@Entity(tableName = "words", indices = [Index(value = ["text"], unique = true)])
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val meaning: String,
    val isCore: Boolean = true,
    val isCustom: Boolean = false,
    val wrongCount: Int = 0,
    val correctCount: Int = 0,
    val lastAskedAt: Long = 0L,
    val weight: Int = 100,
    /** 艾宾浩斯等级 0~8，答对升一级、答错打回 0 */
    val reviewStage: Int = 0,
    /** 下次该复习的时间戳，0 表示还没学过 */
    val nextReviewAt: Long = 0L,
    /** 个人笔记 / 记忆口诀 */
    val note: String = "",
    /** 形近词 / 易混词，逗号分隔 */
    val confusables: String = "",
    /** 所属日期，形如 2026-10-23；空字符串表示内置基础词库 */
    val batchDate: String = "",
    /** 同一天里第几次上传（时间戳），0 表示老数据没记录 */
    val batchId: Long = 0L
)

/** 每一次作答的流水，用于统计今日数量 / 正确率 */
@Entity(tableName = "answer_log")
data class AnswerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val wordText: String,
    val correct: Boolean,
    val answeredAt: Long = System.currentTimeMillis()
)
