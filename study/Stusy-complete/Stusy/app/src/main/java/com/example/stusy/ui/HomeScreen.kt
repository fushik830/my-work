package com.example.stusy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.stusy.ai.AiService
import com.example.stusy.ai.fallbackLine
import com.example.stusy.data.AppStore
import com.example.stusy.data.Challenge
import com.example.stusy.data.FocusTask
import com.example.stusy.data.FocusTimerStore
import com.example.stusy.data.PetMood
import com.example.stusy.data.hourOf
import com.example.stusy.data.resolveMood
import com.example.stusy.notify.ReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    store: AppStore,
    onOpenTab: (StusyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember(store) { AiService(store) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var goal by remember { mutableStateOf(store.goal()) }
    var goalDraft by remember { mutableStateOf(goal) }
    var showGoalDialog by remember { mutableStateOf(false) }

    var bubble by remember { mutableStateOf(fallbackLine(store.summary())) }
    var bubbleLoading by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }

    var showChallenges by remember { mutableStateOf(false) }
    var challengeLoading by remember { mutableStateOf(false) }
    var challengeError by remember { mutableStateOf<String?>(null) }
    var polishingId by remember { mutableStateOf<String?>(null) }
    var challengeDoneCount by remember { mutableIntStateOf(store.challengeDoneCount()) }

    val readTimer = store.readTimer
    val aiTimer = store.aiTimer
    val readDone = readTimer.completed
    val aiDone = aiTimer.completed
    val dailyDone = store.allChallengesDone()
    val allDone = readDone && aiDone && dailyDone
    val summary = store.summary(nowMillis, thinking)
    val baseMood = resolveMood(
        thinking = false,
        allDone = allDone,
        hour = hourOf(nowMillis),
        missStreak = summary.missStreak,
    )
    val mood = if (thinking) PetMood.THINKING else baseMood
    val apiConfigured = store.apiConfig().configured

    // 阅读/找AI 不占用通知栏；前台每秒刷新，后台/锁屏的时间会由 FocusTimerStore
    // 的时间戳在回到 App 时一次性补回。8-4-4 的进度单独由 TimerService 负责。
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(1_000)
                if (readTimer.running) readTimer.tick()
                if (aiTimer.running) aiTimer.tick()
                challengeDoneCount = store.challengeDoneCount()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            nowMillis = System.currentTimeMillis()
            store.rolloverIfNeeded()
        }
    }

    LaunchedEffect(readDone, aiDone, dailyDone) {
        if (readDone || aiDone || dailyDone) store.markCheckIn()
    }

    LaunchedEffect(baseMood, readDone, aiDone, dailyDone, challengeDoneCount, goal) {
        val current = store.summary(nowMillis)
        bubble = fallbackLine(current)
        delay(450)
        bubble = try {
            ai.dayLine(current)
        } catch (_: Exception) {
            fallbackLine(store.summary(nowMillis))
        }
    }

    LaunchedEffect(Unit) {
        delay(1_400)
        ReminderScheduler.ensureChannel(context)
        ReminderScheduler.schedule(context)
        val line = ReminderScheduler.remindNowIfNeeded(context, store, ai)
        if (line != null) bubble = line
    }

    fun regenerateChallenges() {
        if (challengeLoading) return
        challengeLoading = true
        thinking = true
        challengeError = null
        scope.launch {
            try {
                val items = ai.generateChallenges(store.summary(nowMillis))
                store.replaceChallenges(items)
                challengeDoneCount = store.challengeDoneCount()
            } catch (e: Exception) {
                challengeError = e.message ?: "挑战生成失败，请稍后再试"
            } finally {
                challengeLoading = false
                thinking = false
            }
        }
    }

    fun openChallenges() {
        challengeError = null
        showChallenges = true
        val hasProgress = store.challenges.any { it.done } ||
            store.challenges.any { it.source == "user" }
        if (!store.challengeGeneratedToday() && !hasProgress) {
            regenerateChallenges()
        }
    }

    fun polishChallenge(item: Challenge, text: String) {
        if (polishingId != null || challengeLoading) return
        polishingId = item.id
        challengeError = null
        scope.launch {
            try {
                val polished = ai.polishChallenge(text)
                store.updateChallengeText(item.id, polished)
            } catch (e: Exception) {
                challengeError = e.message ?: "润色失败，请检查 API 设置"
            } finally {
                polishingId = null
            }
        }
    }

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = LocalStusyColors.current.surface,
            titleContentColor = LocalStusyColors.current.text,
            textContentColor = LocalStusyColors.current.text,
            title = { Text(text = "编辑目标", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = goalDraft,
                    onValueChange = { goalDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text(text = "目标") },
                    colors = stusyFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val saved = goalDraft.trim().ifBlank { goal }
                        store.saveGoal(saved)
                        goal = store.goal()
                        showGoalDialog = false
                    },
                ) {
                    Text(text = "保存", color = LocalStusyColors.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(text = "关闭", color = LocalStusyColors.current.textSoft)
                }
            },
        )
    }

    if (showChallenges) {
        ChallengesDialog(
            challenges = store.challenges,
            loading = challengeLoading,
            error = challengeError,
            polishingId = polishingId,
            onDismiss = { showChallenges = false },
            onToggle = { item ->
                store.setChallengeDone(item.id, !item.done)
                challengeDoneCount = store.challengeDoneCount()
            },
            onPolish = { item, text -> polishChallenge(item, text) },
            onSaveText = { item, text -> store.updateChallengeText(item.id, text) },
            onAdd = { text ->
                val added = store.addChallenge(text)
                if (added) challengeDoneCount = store.challengeDoneCount()
                added
            },
            onRegenerate = { regenerateChallenges() },
        )
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
                                text = "🐋 ${greetingForHour(hourOf(nowMillis))}",
                                color = LocalStusyColors.current.text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${dateLabel(nowMillis)} ${weekdayLabel(nowMillis)} · 白米饭契约进行中",
                                color = LocalStusyColors.current.textSoft,
                                fontSize = 10.5.sp,
                            )
                        }
                        ThemeBadge()
                    }
                }

                item {
                    WhaleCard(
                        onClick = {
                            goalDraft = goal
                            showGoalDialog = true
                        },
                        contentPadding = 16.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                text = "目标",
                                foreground = LocalStusyColors.current.accent,
                                background = LocalStusyColors.current.accentSoft,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "✎ 点击编辑",
                                color = LocalStusyColors.current.textFaint,
                                fontSize = 9.5.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = goal,
                            color = LocalStusyColors.current.text,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SpeechBubble(
                            text = when {
                                thinking -> "让我想想今天的挑战… (｡•́‿•̀｡)"
                                bubbleLoading -> "唔…让我看看主人今天做到哪了"
                                else -> bubble
                            },
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PetPortrait(
                            mood = mood,
                            onClick = {
                                if (!bubbleLoading) {
                                    bubbleLoading = true
                                    scope.launch {
                                        val current = store.summary(System.currentTimeMillis())
                                        bubble = try {
                                            ai.dayLine(current, tapped = true)
                                        } catch (_: Exception) {
                                            fallbackLine(current, tapped = true)
                                        }
                                        bubbleLoading = false
                                    }
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill(
                                text = moodLabel(mood),
                                foreground = moodColor(mood),
                                background = moodColor(mood).copy(alpha = 0.14f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (bubbleLoading) "鲸鲸正在组织语言…" else "戳一戳鲸鲸",
                                color = LocalStusyColors.current.textFaint,
                                fontSize = 9.5.sp,
                            )
                        }
                        if (!apiConfigured) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "还没有导入 API · 点这里去设置",
                                color = LocalStusyColors.current.accent,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalStusyColors.current.accentSoft.copy(alpha = 0.7f))
                                    .clickable { onOpenTab(StusyTab.Settings) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                item {
                    val doneCount = (if (readDone) 1 else 0) +
                        (if (aiDone) 1 else 0) +
                        (if (dailyDone) 1 else 0)
                    SectionTitle(
                        title = "今日任务",
                        trailing = {
                            Text(
                                text = "$doneCount/3 完成",
                                color = LocalStusyColors.current.textSoft,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    )
                }

                item {
                    FocusTaskCard(
                        task = com.example.stusy.data.READ_TASK,
                        state = readTimer,
                        accent = LocalStusyColors.current.accent,
                        onToggle = {
                            readTimer.onStartPauseClick()
                        },
                    )
                }
                item {
                    FocusTaskCard(
                        task = com.example.stusy.data.AI_TASK,
                        state = aiTimer,
                        accent = LocalStusyColors.current.mint,
                        onToggle = {
                            aiTimer.onStartPauseClick()
                        },
                    )
                }
                item {
                    WhaleCard(
                        onClick = { openChallenges() },
                        contentPadding = 16.dp,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(checked = dailyDone)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "每日挑战",
                                    color = LocalStusyColors.current.text,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        thinking -> "鲸鲸正在想 5 条新挑战…"
                                        dailyDone -> "5 条全部完成，鲸鲸吃饱啦"
                                        else -> "完成 ${store.challengeDoneCount()}/5 · 点开自己加或让 AI 润色"
                                    },
                                    color = if (dailyDone) {
                                        LocalStusyColors.current.mint
                                    } else {
                                        LocalStusyColors.current.textSoft
                                    },
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "›",
                                color = LocalStusyColors.current.textFaint,
                                fontSize = 20.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${store.challengeDoneCount()}/5",
                                color = LocalStusyColors.current.warm,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            WhaleProgressBar(
                                progress = store.challengeProgress(),
                                color = LocalStusyColors.current.warm,
                                modifier = Modifier.weight(1f),
                                height = 6.dp,
                            )
                        }
                    }
                }
            }
            StusyBottomBar(
                selected = StusyTab.Today,
                onSelect = onOpenTab,
            )
        }
    }
}

@Composable
private fun FocusTaskCard(
    task: FocusTask,
    state: FocusTimerStore,
    accent: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    val c = LocalStusyColors.current
    WhaleCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckCircle(checked = state.completed)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        state.completed -> "已完成，这碗白米饭鲸鲸收下啦"
                        state.running -> "正在专注 · 计时中"
                        else -> task.subtitle
                    },
                    color = if (state.running) accent else c.textSoft,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StusyButton(
                text = when {
                    state.completed -> "已完成"
                    state.running -> "暂停"
                    else -> "开始"
                },
                onClick = onToggle,
                enabled = !state.completed,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.focusedMinutes}/30 分钟",
                color = c.textSoft,
                fontSize = 10.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(state.progress * 100).roundToInt()}%",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        WhaleProgressBar(
            progress = state.progress,
            color = accent,
            height = 6.dp,
        )
    }
}
