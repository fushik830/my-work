package com.example.stusy

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.stusy.data.AppStore
import com.example.stusy.notify.ReminderScheduler
import com.example.stusy.timer.TimerService
import com.example.stusy.ui.CalendarScreen
import com.example.stusy.ui.HomeScreen
import com.example.stusy.ui.SettingsScreen
import com.example.stusy.ui.StusyTab
import com.example.stusy.ui.StusyTheme
import com.example.stusy.ui.TimeScreen
import com.example.stusy.ui.isDaytime
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 结果会在设置页与下次启动时重新检查 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        clearLegacyFocusNotifications()
        ReminderScheduler.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences("stusy_prefs", MODE_PRIVATE)
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted && !prefs.getBoolean("notify_permission_asked", false)) {
                prefs.edit().putBoolean("notify_permission_asked", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val initialTab = if (intent.getStringExtra("open_tab") == "time") {
            StusyTab.Time
        } else {
            StusyTab.Today
        }
        setContent {
            StusyApp(initialTab = initialTab)
        }
    }

    /** 清掉旧版本留下的「今日任务」通知与 channel，只保留 8-4-4 计时通知。 */
    private fun clearLegacyFocusNotifications() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(4100)
        manager.cancel(4101)
        manager.cancel(4102)
        manager.cancel(8440)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.deleteNotificationChannel("focus_today")
            manager.deleteNotificationChannel("focus_read")
            manager.deleteNotificationChannel("focus_ai")
        }
    }
}

@Composable
private fun StusyApp(initialTab: StusyTab = StusyTab.Today) {
    val context = LocalContext.current
    val store = remember(context) { AppStore.get(context) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    // 只有 8-4-4 使用前台服务；阅读/找AI 回到 App 前台计时。
    LaunchedEffect(store) {
        TimerService.sync(context, store)
    }

    LaunchedEffect(store) {
        if (store.notificationEnabled()) {
            ReminderScheduler.ensureChannel(context)
            ReminderScheduler.schedule(context)
        }
    }

    val isDay = isDaytime(nowMillis)
    SideEffect {
        val window = (context as? ComponentActivity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isDay
            isAppearanceLightNavigationBars = isDay
        }
    }

    StusyTheme(isDay = isDay) {
        BackHandler(enabled = tab != StusyTab.Today) {
            tab = StusyTab.Today
        }
        when (tab) {
            StusyTab.Today -> HomeScreen(
                store = store,
                onOpenTab = { tab = it },
                modifier = Modifier.fillMaxSize(),
            )

            StusyTab.Calendar -> CalendarScreen(
                store = store,
                onOpenTab = { tab = it },
                modifier = Modifier.fillMaxSize(),
            )

            StusyTab.Time -> TimeScreen(
                store = store,
                onOpenTab = { tab = it },
                modifier = Modifier.fillMaxSize(),
            )

            StusyTab.Settings -> SettingsScreen(
                store = store,
                onOpenTab = { tab = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
