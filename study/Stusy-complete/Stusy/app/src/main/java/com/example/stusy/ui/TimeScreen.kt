package com.example.stusy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stusy.data.AppStore
import com.example.stusy.data.SlotTimer
import com.example.stusy.data.TimeSlot
import com.example.stusy.timer.TimerService
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TimeScreen(
    store: AppStore,
    onOpenTab: (StusyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val restDay = store.timeManager.isRestDay()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        StusyBackground(modifier = Modifier.matchParentSize())
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每日时间管理",
                                color = c.text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${dateLabel(now)} · ${weekdayLabel(now)} · 8-4-4 节奏",
                                color = c.textSoft,
                                fontSize = 10.5.sp,
                            )
                        }
                        ThemeBadge()
                    }
                }

                item {
                    RestOrWorkCard(restDay = restDay)
                }

                item {
                    SectionTitle(
                        title = "今天的三条时间线",
                        trailing = {
                            Pill(
                                text = "一次只走一条",
                                foreground = c.textSoft,
                                background = c.surfaceAlt,
                            )
                        },
                    )
                }

                item {
                    TimerCard(
                        timer = store.timeManager.slot(TimeSlot.FOCUS),
                        restDay = restDay,
                        onToggle = {
                            store.timeManager.toggle(TimeSlot.FOCUS)
                            TimerService.sync(context, store)
                        },
                        onReset = {
                            store.timeManager.reset(TimeSlot.FOCUS)
                            TimerService.sync(context, store)
                        },
                    )
                }
                item {
                    TimerCard(
                        timer = store.timeManager.slot(TimeSlot.IDLE),
                        restDay = restDay,
                        onToggle = {
                            store.timeManager.toggle(TimeSlot.IDLE)
                            TimerService.sync(context, store)
                        },
                        onReset = {
                            store.timeManager.reset(TimeSlot.IDLE)
                            TimerService.sync(context, store)
                        },
                    )
                }
                item {
                    TimerCard(
                        timer = store.timeManager.slot(TimeSlot.TEMPER),
                        restDay = restDay,
                        onToggle = {
                            store.timeManager.toggle(TimeSlot.TEMPER)
                            TimerService.sync(context, store)
                        },
                        onReset = {
                            store.timeManager.reset(TimeSlot.TEMPER)
                            TimerService.sync(context, store)
                        },
                    )
                }

                item {
                    WhaleCard(contentPadding = 16.dp) {
                        Text(
                            text = "8-4-4 是什么？",
                            color = c.text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "把一天切成三块：8 小时专注、4 小时无脑玩、4 小时节制玩。" +
                                "专注时不摸鱼，玩的时候不愧疚；一次只走一条计时器，切走页面也会继续。",
                            color = c.textSoft,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (restDay) {
                                "今天是休息日：8-4-4 暂停，不计时也算完成。"
                            } else {
                                "今天是工作日：三条线都可随时开始或暂停，数据会自动存在本机。"
                            },
                            color = if (restDay) c.warm else c.mint,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
            StusyBottomBar(
                selected = StusyTab.Time,
                onSelect = onOpenTab,
            )
        }
    }
}

@Composable
private fun RestOrWorkCard(restDay: Boolean) {
    val c = LocalStusyColors.current
    WhaleCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                    .background(if (restDay) c.warm.copy(alpha = 0.16f) else c.mint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (restDay) "☁" else "◎",
                    color = if (restDay) c.warm else c.mint,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (restDay) "今天可以躺平" else "今天是工作日",
                        color = c.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill(
                        text = if (restDay) "休息日" else "8-4-4 生效中",
                        foreground = if (restDay) c.warm else c.mint,
                        background = if (restDay) {
                            c.warm.copy(alpha = 0.14f)
                        } else {
                            c.mint.copy(alpha = 0.12f)
                        },
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (restDay) {
                        "不用践行 8-4-4，去玩、去睡、去发呆。鲸鲸今天不催你。"
                    } else {
                        "8 小时专注 · 4 小时无脑玩 · 4 小时节制玩。先选一条，开始就好。"
                    },
                    color = c.textSoft,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun TimerCard(
    timer: SlotTimer,
    restDay: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    val c = LocalStusyColors.current
    val color = when (timer.slot) {
        TimeSlot.FOCUS -> c.accent
        TimeSlot.IDLE -> c.warm
        TimeSlot.TEMPER -> c.mint
    }
    val subtitle = when (timer.slot) {
        TimeSlot.FOCUS -> "上课、写代码、读信息源"
        TimeSlot.IDLE -> "游戏、短视频、放空大脑"
        TimeSlot.TEMPER -> "有节制的娱乐，到点就收"
    }
    val timeText = if (timer.completed) "已达成" else formatFullClock(timer.remainingSeconds)
    val timeCaption = when {
        timer.completed -> "目标完成"
        timer.running -> "剩余 · 计时中"
        else -> "剩余"
    }

    WhaleCard(contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingProgress(
                progress = timer.progress,
                color = color,
                modifier = Modifier.size(62.dp),
                strokeWidth = 6.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(timer.progress * 100).roundToInt()}%",
                        color = color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "完成",
                        color = c.textFaint,
                        fontSize = 7.5.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timer.slot.label,
                        color = c.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Pill(
                        text = "${timer.slot.targetSeconds / 3600}h",
                        foreground = color,
                        background = color.copy(alpha = if (c.isDay) 0.12f else 0.18f),
                    )
                }
                Text(
                    text = subtitle,
                    color = c.textFaint,
                    fontSize = 9.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeText,
                        color = if (timer.completed) c.mint else c.text,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = timeCaption,
                        color = c.textSoft,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StusyButton(
                    text = when {
                        restDay -> "休息日"
                        timer.completed -> "已完成"
                        timer.running -> "暂停"
                        else -> "开始"
                    },
                    onClick = onToggle,
                    modifier = Modifier.width(76.dp),
                    enabled = !restDay && !timer.completed,
                )
                Spacer(modifier = Modifier.height(3.dp))
                StusyTextButton(
                    text = "重置",
                    onClick = onReset,
                    enabled = !restDay && timer.seconds > 0L,
                    color = c.textFaint,
                )
            }
        }
        Spacer(modifier = Modifier.height(9.dp))
        WhaleProgressBar(
            progress = timer.progress,
            color = color,
            height = 6.dp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "已用 ${formatFullClock(timer.seconds)}",
                color = c.textFaint,
                fontSize = 8.5.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (timer.completed) "这条线今天走完了" else "目标 ${formatFullClock(timer.slot.targetSeconds)}",
                color = c.textFaint,
                fontSize = 8.5.sp,
            )
        }
    }
}
