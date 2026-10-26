package com.wordecho.app.speech

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * 播放 Kokoro 生成的 wav。
 *
 * 用 MediaPlayer 而不是 AudioTrack：wav 是我们自己写的固定格式，但
 * MediaPlayer 自带播放完成回调，不用按采样率估算时长再 sleep —— 估错了
 * 会截掉尾音，或者让「正在朗读」多挂一秒。
 */
class WavPlayer {

    private var player: MediaPlayer? = null

    @Volatile
    private var token = 0

    fun play(file: File, earpiece: Boolean, onDone: () -> Unit) {
        stop()
        val mine = ++token
        val ok = runCatching {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        if (earpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION
                        else AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.setOnCompletionListener { finish(mine, onDone) }
            mp.setOnErrorListener { _, _, _ -> finish(mine, onDone); true }
            player = mp
            mp.start()
        }
        if (ok.isFailure) finish(mine, onDone)
    }

    fun stop() {
        token++
        val p = player
        player = null
        if (p != null) {
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
    }

    fun isPlaying(): Boolean = runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun finish(mine: Int, onDone: () -> Unit) {
        // 已经被新的播放顶掉就不要再回调，否则会提前进入下一题
        if (mine != token) return
        token++
        val p = player
        player = null
        if (p != null) runCatching { p.release() }
        onDone()
    }
}
