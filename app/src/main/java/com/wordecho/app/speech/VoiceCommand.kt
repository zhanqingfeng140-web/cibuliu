package com.wordecho.app.speech

enum class VoiceCommand { START, NEXT, SKIP, REPEAT, STOP, NONE }

/**
 * 语音指令解析。只在「等待作答」阶段启用 NEXT / SKIP / REPEAT / STOP，
 * 且要求整句较短，避免把正常释义误判成指令。
 */
object VoiceCommandParser {

    private val startWords = listOf("开始背", "开始学习", "继续背", "开始吧")
    private val nextWords = listOf("下一个", "下一题", "下一词", "换一个", "下一个单词")
    private val skipWords = listOf("跳过", "不会", "不知道", "想不起来")
    private val repeatWords = listOf("再说一遍", "重复", "再读一遍", "没听清", "再说一次")
    private val stopWords = listOf("结束背诵", "结束", "停止", "不背了", "退出")

    fun parse(raw: String): VoiceCommand {
        val t = raw.replace(Regex("[\\s，。,.!！?？、~～]"), "")
        if (t.isEmpty()) return VoiceCommand.NONE
        fun hit(list: List<String>) = list.any { t == it || (t.length <= 10 && t.contains(it)) }
        return when {
            hit(repeatWords) -> VoiceCommand.REPEAT
            hit(skipWords) -> VoiceCommand.SKIP
            hit(nextWords) -> VoiceCommand.NEXT
            hit(stopWords) -> VoiceCommand.STOP
            hit(startWords) -> VoiceCommand.START
            else -> VoiceCommand.NONE
        }
    }
}
