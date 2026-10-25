package com.wordecho.app

import android.app.Application
import com.wordecho.app.reminder.ReminderScheduler
import kotlinx.coroutines.launch

class WordEchoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 闹钟可能被系统清掉（清后台、省电策略），每次启动补排一次
        container.appScope.launch {
            val cfg = runCatching { container.settings.current() }.getOrNull() ?: return@launch
            if (cfg.reminderEnabled) {
                ReminderScheduler.schedule(this@WordEchoApp, cfg.reminderHour, cfg.reminderMinute)
            }
        }
    }
}
