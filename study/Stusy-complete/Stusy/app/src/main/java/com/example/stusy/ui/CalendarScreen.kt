package com.example.stusy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stusy.data.AI_TASK
import com.example.stusy.data.AppStore
import com.example.stusy.data.FocusSession
import com.example.stusy.data.READ_TASK
import com.example.stusy.data.dateString
import com.example.stusy.data.daysInMonth
import com.example.stusy.data.firstWeekdayOffset
import com.example.stusy.data.shiftDate
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun CalendarScreen(
    store: AppStore,
    onOpenTab: (StusyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val today = dateString(nowMillis)
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf<String?>(today) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    val checkInSet = remember(
        store.readTimer.completed,
        store.aiTimer.completed,
        store.challengeDoneCount(),
    ) {
        store.checkInDates()
    }

    val monthCalendar = remember(monthOffset) {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, monthOffset)
        }
    }
    val year = monthCalendar.get(Calendar.YEAR)
    val month0 = monthCalendar.get(Calendar.MONTH)
    val label = monthLabel(monthCalendar.timeInMillis)
    val days = daysInMonth(year, month0)
    val offset = firstWeekdayOffset(year, month0)
    val cells = buildList {
        repeat(offset) { add(0) }
        for (day in 1..days) add(day)
        while (size % 7 != 0) add(0)
    }

    LaunchedEffect(monthOffset) {
        selectedDate = if (monthOffset == 0) today else null
    }

    var checkedCount = 0
    var missedCount = 0
    for (day in 1..days) {
        val date = dateOf(year, month0, day)
        if (checkInSet.contains(date)) checkedCount++ else if (date < today) missedCount++
    }

    val streak = remember(checkInSet, today) {
        var cursor = if (checkInSet.contains(today)) today else shiftDate(today, -1)
        var count = 0
        while (count < 366 && checkInSet.contains(cursor)) {
            count++
            cursor = shiftDate(cursor, -1)
        }
        count
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "打卡日历",
                            color = c.text,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "鲸鲸记得每一次投喂，也记得每一次挨饿",
                            color = c.textSoft,
                            fontSize = 10.5.sp,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard(
                            value = "$checkedCount",
                            label = "本月打卡",
                            color = c.mint,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            value = "$missedCount",
                            label = "本月漏掉",
                            color = c.danger,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            value = "$streak",
                            label = "连续天数",
                            color = c.accent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    WhaleCard(contentPadding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NavArrow(
                                text = "‹",
                                enabled = true,
                                onClick = { monthOffset-- },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    color = c.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (monthOffset == 0) "这个月" else "回看",
                                    color = c.textFaint,
                                    fontSize = 9.sp,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            NavArrow(
                                text = "›",
                                enabled = monthOffset < 0,
                                onClick = { if (monthOffset < 0) monthOffset++ },
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                                Text(
                                    text = day,
                                    color = c.textFaint,
                                    fontSize = 9.5.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        cells.chunked(7).forEach { week ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    if (day == 0) {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val date = dateOf(year, month0, day)
                                        DayCell(
                                            day = day,
                                            date = date,
                                            today = today,
                                            checked = checkInSet.contains(date),
                                            selected = selectedDate == date,
                                            onClick = { selectedDate = date },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LegendDot(color = c.accent, label = "已打卡")
                            Spacer(modifier = Modifier.width(14.dp))
                            LegendDot(color = c.danger, label = "未打卡")
                            Spacer(modifier = Modifier.width(14.dp))
                            LegendDot(color = c.textFaint, label = "未来")
                        }
                    }
                }

                item {
                    val selected = selectedDate
                    if (selected != null) {
                        DayDetailCard(
                            store = store,
                            date = selected,
                            today = today,
                            checked = checkInSet.contains(selected),
                        )
                    } else {
                        WhaleCard(contentPadding = 16.dp) {
                            Text(
                                text = "点一个日期，看看那天喂了鲸鲸什么",
                                color = c.textSoft,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }
            }
            StusyBottomBar(
                selected = StusyTab.Calendar,
                onSelect = onOpenTab,
            )
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    WhaleCard(modifier = modifier, contentPadding = 12.dp) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = c.textSoft,
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun NavArrow(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalStusyColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) c.surfaceAlt else c.track.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) c.accent else c.textFaint.copy(alpha = 0.4f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    date: String,
    today: String,
    checked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    val isToday = date == today
    val isFuture = date > today
    val background = when {
        checked -> c.accent
        isFuture -> c.track.copy(alpha = 0.35f)
        isToday -> c.accentSoft
        else -> c.danger.copy(alpha = 0.11f)
    }
    val foreground = when {
        checked -> c.onAccent
        isFuture -> c.textFaint.copy(alpha = 0.55f)
        isToday -> c.accent
        else -> c.danger
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(background)
                .then(
                    if (selected || (isToday && !checked)) {
                        Modifier.border(1.5.dp, c.accent, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                color = foreground,
                fontSize = 11.sp,
                fontWeight = if (checked || isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = LocalStusyColors.current.textSoft,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun DayDetailCard(store: AppStore, date: String, today: String, checked: Boolean) {
    val c = LocalStusyColors.current
    val isFuture = date > today
    val isToday = date == today
    val sessions = store.sessionsOn(date)
    WhaleCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$date ${weekdayLabel(parseDateOrNow(date))}",
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        isFuture -> "还没到这一天"
                        checked -> "那天鲸鲸吃到了白米饭"
                        else -> "那天鲸鲸饿着肚子"
                    },
                    color = when {
                        isFuture -> c.textFaint
                        checked -> c.mint
                        else -> c.danger
                    },
                    fontSize = 10.5.sp,
                )
            }
            Pill(
                text = if (checked) "已打卡" else if (isFuture) "未来" else "未打卡",
                foreground = if (checked) c.mint else if (isFuture) c.textFaint else c.danger,
                background = if (checked) {
                    c.mint.copy(alpha = 0.14f)
                } else if (isFuture) {
                    c.track
                } else {
                    c.danger.copy(alpha = 0.12f)
                },
            )
        }
        if (sessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            StusyDivider()
            Spacer(modifier = Modifier.height(8.dp))
            sessions.take(6).forEach { session ->
                SessionRow(session = session)
            }
            if (sessions.size > 6) {
                Text(
                    text = "还有 ${sessions.size - 6} 条专注记录…",
                    color = c.textFaint,
                    fontSize = 9.5.sp,
                )
            }
        } else if (checked && !isFuture) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isToday) "今天的白米饭还在碗里，继续加油" else "那天完成了每日挑战，没有计时记录",
                color = c.textFaint,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    val c = LocalStusyColors.current
    val title = when (session.taskId) {
        READ_TASK.id -> READ_TASK.title
        AI_TASK.id -> AI_TASK.title
        else -> "专注"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(c.accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = c.textSoft,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${session.seconds / 60} 分钟",
            color = c.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun dateOf(year: Int, month0: Int, day: Int): String =
    String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day)

private fun parseDateOrNow(date: String): Long =
    com.example.stusy.data.parseDate(date) ?: System.currentTimeMillis()
