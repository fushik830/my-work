package com.example.stusy.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.stusy.MainActivity
import com.example.stusy.R
import com.example.stusy.ai.AiService
import com.example.stusy.ai.fallbackNotification
import com.example.stusy.data.AppStore
import com.example.stusy.data.hourOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

private const val CHANNEL_ID = "whale_reminder"
private const val CHANNEL_NAME = "鲸鲸的投喂提醒"
private const val NOTIFICATION_ID = 2100
private const val ALARM_REQUEST_CODE = 2100
private const val HUNGRY_HOUR_LOCAL = 21

object ReminderScheduler {

    fun setEnabled(context: Context, enabled: Boolean) {
        val store = AppStore.get(context)
        store.setNotificationEnabled(enabled)
        if (enabled) {
            store.resetNotificationFlag()
            schedule(context)
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        val store = AppStore.get(context)
        if (!store.notificationEnabled()) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmPendingIntent(context)
        val triggerAt = nextNinePm()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            // 某些系统会限制后台闹钟；App 打开时还有一次兜底检查。
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        manager.cancel(alarmPendingIntent(context))
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "晚上九点还没喂鲸鲸吃饭时，她会来敲门。"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showNotification(context: Context, line: String): Boolean {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_reminder", true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            ALARM_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("鲸鲸饿了 · 白米饭契约")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF3D7FD6.toInt())
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * App 在前台时的兜底：用户 21 点后打开 App，若还没完成就立刻提醒一次。
     * 返回真正展示的文案，方便首页同步到中央气泡。
     */
    suspend fun remindNowIfNeeded(
        context: Context,
        store: AppStore,
        ai: AiService,
    ): String? {
        if (!store.notificationEnabled()) return null
        if (hourOf(System.currentTimeMillis()) < HUNGRY_HOUR_LOCAL) return null
        if (store.allDone() || store.wasNotifiedToday()) return null
        val line = withTimeoutOrNull(6_000) {
            runCatching { ai.notificationLine(store.summary()) }.getOrNull()
        } ?: fallbackNotification()
        val shown = withContext(Dispatchers.Main) {
            showNotification(context.applicationContext, line)
        }
        if (shown) store.markNotifiedToday()
        return if (shown) line else null
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextNinePm(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HUNGRY_HOUR_LOCAL)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1)
        return target.timeInMillis
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val store = AppStore.get(appContext)
        if (!store.notificationEnabled()) return
        if (store.allDone() || store.wasNotifiedToday()) {
            ReminderScheduler.schedule(appContext)
            return
        }
        val pending = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val summary = store.summary()
                val line = withTimeoutOrNull(8_000) {
                    runCatching { AiService(store).notificationLine(summary) }.getOrNull()
                } ?: fallbackNotification()
                val shown = withContext(Dispatchers.Main) {
                    ReminderScheduler.showNotification(appContext, line)
                }
                if (shown) store.markNotifiedToday()
            } catch (_: Exception) {
                // 闹钟失败不打扰用户，下一次打开 App 还有兜底。
            } finally {
                ReminderScheduler.schedule(appContext)
                pending.finish()
            }
        }
    }
}

class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderScheduler.schedule(context.applicationContext)
        }
    }
}
