package com.wordecho.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wordecho.app.MainActivity
import com.wordecho.app.R
import com.wordecho.app.WordEchoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 每天定时弹通知，内容是「今天该复习哪些词」，然后自动排明天的。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", 21)
        val minute = intent.getIntExtra("minute", 0)

        // 查库是 suspend 的，用 goAsync 争取几秒钟把通知内容查出来
        val pending = goAsync()
        val app = context.applicationContext as? WordEchoApp

        CoroutineScope(Dispatchers.IO).launch {
            val due = runCatching { app?.container?.repo?.duePool() }.getOrNull().orEmpty()
            try {
                show(context, due.map { it.text })
            } finally {
                runCatching { pending.finish() }
                // 排下一次（明天同一时间）
                ReminderScheduler.schedule(context, hour, minute)
            }
        }
    }

    private fun show(context: Context, words: List<String>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "每日复习提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "每天按复习计划提醒你要背哪些词" }
            nm.createNotificationChannel(ch)
        }

        val open = PendingIntent.getActivity(
            context,
            2001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String

        if (words.isEmpty()) {
            title = "今天没有到期的词"
            text = "可以背几个新词，或者歇一天"
        } else {
            title = "今天要复习 ${words.size} 个词"
            text = words.take(6).joinToString("、") +
                if (words.size > 6) " 等" else ""
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_word)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { nm.notify(NOTIFY_ID, notification) }
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        private const val NOTIFY_ID = 3001
    }
}
