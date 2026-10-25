package com.wordecho.app.speech

import android.content.Context
import android.media.AudioManager

/**
 * 听筒模式路由。
 *
 * 进入 MODE_IN_COMMUNICATION 后：
 *  - TTS 走 STREAM_VOICE_CALL 的声音从听筒出来
 *  - AudioRecord 用 VOICE_COMMUNICATION 采集，回声消除更接近通话效果
 * 这样在打电话（或在嘈杂环境想小音量背词）时也能用。
 */
class AudioRouter(context: Context) {

    private val am = context.applicationContext
        .getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var savedMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeaker: Boolean = false
    private var active = false

    fun enter() {
        if (active) return
        savedMode = am.mode
        savedSpeaker = am.isSpeakerphoneOn
        runCatching {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false
        }
        active = true
    }

    fun exit() {
        if (!active) return
        runCatching {
            am.isSpeakerphoneOn = savedSpeaker
            am.mode = savedMode
        }
        active = false
    }

    fun isActive(): Boolean = active
}
