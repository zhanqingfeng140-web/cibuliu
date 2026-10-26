package com.wordecho.app.speech

import android.content.Context

/**
 * 离线音素查询表。
 *
 * Kokoro 只吃音素、不吃文字，而且 Android 上没有现成的英文 G2P
 * （misaki 的 G2P 是 Python、espeak 要 NDK 编译，两者都被刻意避开）。
 * 所以「任意单词能不能读」就取决于这张表收没收录——18 万词，
 * 内置词库 430 词 100% 命中，用户自己导的生僻词命中不了就退回系统 TTS。
 *
 * 存储刻意没用 HashMap<String, String>：18 万条会占 25–35MB 堆，
 * 低端机容易 OOM。改成「一整块字节数组 + 行偏移索引 + 二分查找」，
 * 常驻约 5MB，查一次 17 次比较。
 */
class KokoroLexicon private constructor(
    private val buf: ByteArray,
    private val offsets: IntArray,
    private val vocab: Map<Char, Int>
) {

    /** 音素字符 → 模型词表 id */
    fun idOf(c: Char): Int? = vocab[c]

    /** 单词 → 音素串；查不到返回 null（调用方据此降级） */
    fun phonemes(word: String): String? {
        val q = normalize(word).toByteArray(Charsets.UTF_8)
        if (q.isEmpty()) return null
        // offsets 的最后一格是哨兵（= 文件末尾），真实行下标只到 size-2
        if (offsets.size < 2) return null
        var lo = 0
        var hi = offsets.size - 2
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareAt(mid, q)
            when {
                c == 0 -> return phoneAt(mid)
                c < 0 -> lo = mid + 1
                else -> hi = mid - 1
            }
        }
        return null
    }

    fun size(): Int = offsets.size

    private fun normalize(w: String): String {
        val t = w.trim().lowercase()
        // 「don't」这类撇号在词典里是 ASCII 撇号，输入法常给全角
        return t.replace('\u2019', '\'').replace('\u2018', '\'')
    }

    /** 把 mid 行 tab 前的单词与 q 做无符号字节比较 */
    private fun compareAt(mid: Int, q: ByteArray): Int {
        var i = offsets[mid]
        var j = 0
        val end = offsets[mid + 1]
        while (i < end && buf[i] != TAB) {
            if (j >= q.size) return 1
            val a = buf[i].toInt() and 0xFF
            val b = q[j].toInt() and 0xFF
            if (a != b) return if (a < b) -1 else 1
            i++
            j++
        }
        return if (j == q.size) 0 else -1
    }

    private fun phoneAt(mid: Int): String? {
        val start = offsets[mid]
        val end = offsets[mid + 1]
        var i = start
        while (i < end && buf[i] != TAB) i++
        if (i >= end) return null
        // 必须停在行尾的 \n 上，否则音素串会末尾带换行，
        // 换行在 vocab 里没有 id，合成时会被当成非法字符
        var j = i + 1
        while (j < end && buf[j] != NL) j++
        val len = j - i - 1
        if (len <= 0) return null
        return String(buf, i + 1, len, Charsets.UTF_8)
    }

    companion object {
        private const val TAB: Byte = 9
        private const val NL: Byte = 10

        /**
         * 一次性读进内存。lex.txt 约 4.3MB，读 + 建索引实测在 100ms 量级，
         * 必须放在 IO 线程调（不要在 Composable 里直接调）。
         */
        fun load(context: Context): KokoroLexicon {
            val vocab = HashMap<Char, Int>(192)
            context.assets.open("kokoro/vocab.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    vocab[line[0]] = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEach
                }
            }

            val buf = context.assets.open("kokoro/lex.txt").use { it.readBytes() }

            // 第一遍数行数，第二遍填偏移。多一次遍历换掉 ArrayList 的装箱开销。
            var lines = 0
            for (b in buf) if (b == '\n'.code.toByte()) lines++
            if (buf.isNotEmpty() && buf[buf.size - 1] != '\n'.code.toByte()) lines++

            val offs = IntArray(lines + 1)
            var k = 0
            offs[k++] = 0
            for (i in buf.indices) {
                if (buf[i] == NL && i + 1 < buf.size) offs[k++] = i + 1
            }
            // 末尾可能因换行符少算一格，补齐
            while (k < offs.size) offs[k++] = buf.size

            return KokoroLexicon(buf, offs, vocab)
        }
    }
}
