package com.wordecho.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.wordecho.app.WordEchoApp

/**
 * 手机重启、App 更新后，闹钟会被系统清掉，这里重新排一次。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val settings = (context.applicationContext as? WordEchoApp)?.container?.settings
            ?: return

        // 接收器里不能 suspend，这里短暂阻塞读一下 DataStore（毫秒级）
        val cfg = runBlocking { runCatching { settings.current() }.getOrNull() } ?: return
        if (cfg.reminderEnabled) {
            ReminderScheduler.schedule(context, cfg.reminderHour, cfg.reminderMinute)
        }
    }
}
