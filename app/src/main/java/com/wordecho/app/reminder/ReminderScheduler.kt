package com.wordecho.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每日背单词提醒。
 *
 * 用 AlarmManager 而不是 WorkManager：WorkManager 的周期任务最小间隔 15 分钟、
 * 且执行时间会被系统打散，做不到「每天 21:00 准点提醒」。
 *
 * Android 12+ 精确闹钟需要 SCHEDULE_EXACT_ALARM 授权，没授权时降级成
 * setAndAllowWhileIdle（可能晚几分钟），对学习提醒来说完全够用。
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 1001

    fun schedule(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, hour, minute)

        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            }
        }.onFailure {
            // 某些 ROM 会拦精确闹钟，退一步用普通闹钟
            runCatching {
                am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(pendingIntent(context, 0, 0)) }
    }

    private fun pendingIntent(context: Context, hour: Int, minute: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
