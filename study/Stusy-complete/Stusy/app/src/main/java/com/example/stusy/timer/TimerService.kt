package com.example.stusy.timer

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.stusy.MainActivity
import com.example.stusy.R
import com.example.stusy.data.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 只负责 8-4-4 时间管理的前台服务。
 *
 * 通知栏只显示当前 8-4-4 时段的进度，不再显示「阅读信息源 / 找AI对话」
 * 的今日任务进度。阅读/找AI 回到 App 前台计时。
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private var lastTickElapsed = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var store: AppStore

    override fun onCreate() {
        super.onCreate()
        store = AppStore.get(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        syncState()
        return START_STICKY
    }

    private fun syncState() {
        val running = store.timeManager.hasRunning() && !store.timeManager.isRestDay()
        if (!running) {
            stopTicker()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 每次 sync 都重建通知，重置/切换时段后进度条立即刷新。
        startInForeground()
        acquireWakeLock()
        startTicker()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        lastTickElapsed = SystemClock.elapsedRealtime()
        ticker = scope.launch {
            while (isActive) {
                delay(1_000L)
                val now = SystemClock.elapsedRealtime()
                val deltaSeconds = ((now - lastTickElapsed) / 1_000L).coerceAtLeast(1L)
                lastTickElapsed = now

                val timeManager = store.timeManager
                timeManager.tick(deltaSeconds)

                if (!timeManager.hasRunning()) {
                    // 先把最后一秒（或完成状态）写进通知，再退场。
                    updateNotification()
                    releaseWakeLock()
                    stopSelf()
                    break
                }
                updateNotification()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun updateNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val timeManager = store.timeManager
        val timer = timeManager.runningSlot()
        val label = timer?.slot?.label ?: "专注"
        val seconds = timer?.seconds ?: 0L
        val target = timer?.slot?.targetSeconds ?: 1L
        val remaining = timer?.remainingSeconds ?: 0L
        val progress = (seconds.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        val progressValue = (progress * 1000f).roundToInt().coerceIn(0, 1000)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("8-4-4 · $label")
            .setContentText(
                "已用 ${durationHms(seconds)} / ${durationHms(target)} · " +
                    "剩余 ${durationHms(remaining)}",
            )
            .setSubText("鲸鲸在后台陪你计时")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "已用 ${durationHms(seconds)} / ${durationHms(target)}\n" +
                        "剩余 ${durationHms(remaining)}",
                ),
            )
            .setProgress(1000, progressValue.coerceAtLeast(1), false)
            .setGroup("stusy_844")
            .setSortKey("01_stusy_844")
            .setWhen(0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openTimePendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun openTimePendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_TAB, "time")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "8-4-4 计时",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在通知栏显示 8-4-4 当前时段的计时进度"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Stusy::Time844",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopTicker()
        scope.cancel()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun durationHms(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0L)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    }

    companion object {
        private const val EXTRA_OPEN_TAB = "open_tab"
        private const val CHANNEL_ID = "timer_844"
        private const val NOTIFICATION_ID = 8440
        private const val REQUEST_OPEN = 8400

        fun sync(context: Context, store: AppStore) {
            val running = store.timeManager.hasRunning() && !store.timeManager.isRestDay()
            val intent = Intent(context, TimerService::class.java)
            if (running) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
