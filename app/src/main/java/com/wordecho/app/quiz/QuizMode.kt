package com.wordecho.app.quiz

/** 背诵模式：英→中（主模式）/ 中→英（备选） */
enum class QuizMode {
    EN2CN,
    CN2EN;

    val label: String
        get() = when (this) {
            EN2CN -> "听英文 · 说中文"
            CN2EN -> "看中文 · 说英文"
        }
}

/** 一轮背诵的来源 */
enum class SessionType {
    /** 混合：错题 + 高频 + 新词 */
    MIX,
    /** 只出错题本里的词 */
    WRONG_ONLY,
    /** 只出艾宾浩斯到期的词 */
    REVIEW,
    /** 只出某一天导入的那批词 */
    BATCH;

    val title: String
        get() = when (this) {
            MIX -> "背诵中"
            WRONG_ONLY -> "错题专项"
            REVIEW -> "复习到期"
            BATCH -> "今日词库"
        }
}
