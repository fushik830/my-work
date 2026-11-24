# 哦鲸鲸喊你学习 · 完整源码合集

> 工程内全部 Kotlin 源码、AndroidManifest 与 app_name，方便阅读/复制。实际工程以同目录 Gradle 项目为准。

## `app/src/main/java/com/example/stusy/ai/Ai.kt`

```kotlin
package com.example.stusy.ai

import com.example.stusy.data.AppStore
import com.example.stusy.data.DaySummary
import com.example.stusy.data.HUNGRY_HOUR
import com.example.stusy.data.PetMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class AiException(message: String) : Exception(message)

private const val MAX_LINE_CHARS = 25

private val aiHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

private val PERSONA = """
你是「鲸鲸」，一只蓝发鲸鱼女仆，和主人签下了“白米饭契约”：
主人每完成一项今日任务，就等于喂你一口白米饭；任务没完成，你就会饿肚子、委屈，但你永远不会责骂主人。
说话风格：软萌、黏人、有点小吃醋，句尾常用“呢/啦/喔”，可以带 1 个颜文字和 1 个 emoji，称呼用户“主人”。
硬性要求：只输出一句中文台词，不超过 25 个字（标点、emoji、颜文字都算），不要换行，不要引号，不要解释，不要写“鲸鲸：”。
""".trim()

class AiService(private val store: AppStore) {

    suspend fun dayLine(summary: DaySummary, tapped: Boolean = false): String {
        val user = buildString {
            appendLine(summary.promptBlock())
            appendLine()
            if (tapped) {
                append("主人刚刚戳了戳你。")
                if (summary.allDone) {
                    append("请用一句话夸夸主人，表达你吃得很饱、很幸福。")
                } else {
                    append("请先看一眼进度，再给一句具体、有行动感的鼓励，不要说空话。")
                }
            } else if (summary.allDone) {
                append("主人今天三项全部完成，等于给鲸鲸喂了满满一碗白米饭。请用一句话夸奖主人，可以撒娇。")
            } else {
                append("主人还有这些没完成：${summary.pendingTasks.joinToString("、")}。")
                append("请结合具体项目，用一句自然、口语化的话提醒或鼓励主人；不要罗列清单，不要说教。")
            }
            if (summary.missStreak > 0) {
                append("主人已经 ${summary.missStreak} 天没喂你白米饭了，可以带一点委屈。")
            }
        }
        return clampLine(
            chat(system = PERSONA, user = user, maxTokens = 80),
            MAX_LINE_CHARS,
        )
    }

    suspend fun notificationLine(summary: DaySummary): String {
        val user = buildString {
            appendLine(summary.promptBlock())
            appendLine("现在是晚上九点以后，主人还没有完成全部任务。")
            append("请写一条会直接显示在手机通知里的提醒，25 字以内，软萌、催主人去吃饭（完成任务），不要出现“通知”两个字。")
        }
        return clampLine(
            chat(system = PERSONA, user = user, maxTokens = 70),
            MAX_LINE_CHARS,
        )
    }

    suspend fun generateChallenges(summary: DaySummary): List<String> {
        val existing = store.challenges.joinToString("；") { it.text }
        val text = chat(
            system = "你是大学生的学习规划助手。只输出 5 行，每行一条挑战，不要编号，不要额外解释。每条 20 字以内，具体、今天能完成。",
            user = "大学目标：${summary.goal}。请给 5 个今天可以完成的学习小挑战。" +
                if (existing.isBlank()) "" else "不要和这些重复：$existing",
            maxTokens = 220,
        )
        val items = LinkedHashSet<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
                .removePrefix("-")
                .removePrefix("•")
                .trim()
                .replace(Regex("^\\d+[.、)）]\\s*"), "")
                .trim()
            if (line.isNotBlank()) items.add(clampLine(line, 30))
        }
        fallbackChallengeIdeas.forEach { idea ->
            if (items.size < 5) items.add(idea)
        }
        if (items.isEmpty()) error("没有拿到挑战")
        return items.take(5)
    }

    suspend fun polishChallenge(text: String): String {
        val result = chat(
            system = "你是学习规划助手。把用户写的任务改写成一条清晰、具体、今天能完成的中文挑战。" +
                "只输出改写后的这一条，20 字以内，不要编号、引号或解释。",
            user = text.trim(),
            maxTokens = 90,
        )
        val cleaned = clampLine(result, 30)
        return cleaned.ifBlank { text.trim() }
    }

    suspend fun testConnection(): String {
        val result = chat(
            system = "你是接口连通性测试助手。只回复四个字：连接成功。",
            user = "测试连接",
            maxTokens = 12,
        )
        if (result.isBlank()) throw AiException("接口返回为空")
        return "连接成功"
    }

    private suspend fun chat(system: String, user: String, maxTokens: Int): String =
        withContext(Dispatchers.IO) {
            val config = store.apiConfig()
            if (!config.configured) {
                throw AiException("还没有导入 API Key，去「设置 → AI 接口」填一下吧")
            }
            val payload = JSONObject()
                .put("model", config.model)
                .put("max_tokens", maxTokens)
                .put("temperature", 1.1)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", system))
                        .put(JSONObject().put("role", "user").put("content", user)),
                )
            val request = Request.Builder()
                .url(config.endpoint)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                aiHttpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = runCatching {
                            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                        }.getOrDefault("")
                        val reason = when (response.code) {
                            401 -> "API Key 无效或已过期"
                            402 -> "账户余额不足"
                            429 -> "请求太频繁，稍后再试"
                            in 500..599 -> "AI 服务暂时不可用"
                            else -> "请求失败（${response.code}）"
                        }
                        throw AiException(
                            if (detail.isBlank()) {
                                reason
                            } else {
                                "$reason：${detail.take(48)}"
                            },
                        )
                    }
                    val content = JSONObject(raw)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        .orEmpty()
                        .trim()
                    if (content.isBlank()) throw AiException("AI 返回了空白内容")
                    content
                }
            } catch (e: AiException) {
                throw e
            } catch (e: IOException) {
                throw AiException("网络连接失败，检查网络或接口地址")
            } catch (e: Exception) {
                throw AiException("AI 请求出错：${e.message?.take(40) ?: "未知错误"}")
            }
        }
}

fun fallbackLine(summary: DaySummary, tapped: Boolean = false): String {
    val pending = summary.pendingTasks
    if (tapped) {
        val tappedText = when {
            summary.allDone -> "主人戳我啦，饭也吃饱了，抱抱 (≧▽≦)♡"
            pending.size == 1 -> "收到戳戳！只差「${pending.first()}」，拿下它嘛 ♡"
            else -> "收到戳戳！还差${pending.size}项，鲸鲸陪你喔 ♡"
        }
        return clampLine(tappedText, MAX_LINE_CHARS)
    }
    val text = when {
        summary.allDone -> "主人今天全部完成啦，我吃得饱饱的 (≧▽≦)✨"
        summary.missStreak > 0 -> "主人好久没喂我吃饭了…我有点难过 (×_×)"
        summary.hour >= HUNGRY_HOUR -> "九点啦，还有${pending.size}项没做，我饿得咕咕叫 (´；ω；`)"
        pending.size == 1 -> "只差「${pending.first()}」了，主人再坚持一下 (｡•̀ᴗ-)✧"
        else -> "主人还差${pending.size}项，我在这里等你 (｡•̀ᴗ-)✧"
    }
    return clampLine(text, MAX_LINE_CHARS)
}

fun fallbackNotification(): String =
    clampLine("九点啦，主人还没喂我吃饭… (´；ω；`)", MAX_LINE_CHARS)

private val fallbackChallengeIdeas = listOf(
    "用 25 分钟整理今天最重要的一节课笔记",
    "读 10 页专业书，写下一句话总结",
    "向 AI 提一个你真正好奇的问题",
    "出门走 15 分钟，顺便晒晒太阳",
    "睡前复盘，写下明天最想完成的一件事",
)

/**
 * 去掉换行、引号、前缀，并把长度压到 maxChars 以内（按 Unicode code point 计算）。
 */
fun clampLine(raw: String, maxChars: Int = MAX_LINE_CHARS): String {
    val first = raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    var cleaned = first
        .replace("```", "")
        .replace("**", "")
        .replace(Regex("^#+\\s*"), "")
        .replace(Regex("^(鲸鲸|小鲸|女仆|助手)\\s*[:：]\\s*"), "")
        .trim()
        .trim('"', '“', '”', '\'', '`', '「', '」')
        .trim()
        .replace(Regex("\\s+"), " ")
    if (cleaned.isBlank()) return ""
    val codePoints = cleaned.codePoints().toArray()
    if (codePoints.size > maxChars) {
        val keep = (maxChars - 1).coerceAtLeast(1)
        cleaned = String(codePoints, 0, keep) + "…"
    }
    return cleaned
}

fun clampCount(text: String): Int = text.codePointCount(0, text.length)

fun aiCharCountLabel(text: String): String =
    String.format(Locale.CHINA, "%d/%d 字", clampCount(text), MAX_LINE_CHARS)

fun moodDisplayName(mood: PetMood): String = when (mood) {
    PetMood.NORMAL -> "开心"
    PetMood.WAITING -> "期待"
    PetMood.HUNGRY -> "饿肚子"
    PetMood.DEAD -> "破防"
    PetMood.THINKING -> "思考中"
}
```

## `app/src/main/java/com/example/stusy/data/AppStore.kt`

```kotlin
package com.example.stusy.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.stusy.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

const val FOCUS_GOAL_SECONDS: Long = 30L * 60L
const val CHALLENGE_TARGET: Int = 5
const val HUNGRY_HOUR: Int = 21
const val DEAD_MISS_DAYS: Int = 1
const val DEFAULT_GOAL: String = "我的大学目标：绩点3.8，拿下国奖"

private const val PREFS_NAME = "stusy_prefs"
private const val KEY_TASK_DATE = "task_date"
private const val KEY_LAST_CHECK_IN = "last_check_in_date"
private const val KEY_HISTORY = "check_in_history"
private const val KEY_MISS_STREAK = "miss_streak"
private const val KEY_GOAL = "university_goal"
private const val KEY_CHALLENGES = "challenges_json"
private const val KEY_CHALLENGE_GENERATED_DATE = "challenge_generated_date"
private const val KEY_SESSIONS = "focus_sessions_json"
private const val KEY_API_KEY = "deepseek_api_key"
private const val KEY_API_BASE = "deepseek_api_base"
private const val KEY_API_MODEL = "deepseek_api_model"
private const val KEY_NOTIFY_ENABLED = "notify_enabled"
private const val KEY_NOTIFIED_DATE = "notified_date"

const val KEY_READ_SECONDS = "read_seconds"
const val KEY_READ_RUNNING = "read_running"
const val KEY_AI_SECONDS = "ai_seconds"
const val KEY_AI_RUNNING = "ai_running"

private const val DEFAULT_API_BASE = "https://api.deepseek.com"
private const val DEFAULT_API_MODEL = "deepseek-chat"

enum class PetMood {
    NORMAL,
    WAITING,
    HUNGRY,
    DEAD,
    THINKING,
}

fun PetMood.drawableRes(): Int = when (this) {
    PetMood.NORMAL -> R.drawable.pet_normal
    PetMood.WAITING -> R.drawable.pet_waiting
    PetMood.HUNGRY -> R.drawable.pet_hungry
    PetMood.DEAD -> R.drawable.pet_dead
    PetMood.THINKING -> R.drawable.pet_thinking
}

/**
 * 状态优先级严格按需求：思考 > 破防 > 全部完成 > 晚上九点后饿了 > 期待。
 * 其中 missStreak 表示“已经整整漏掉的天数”，所以 1 表示昨天没有完成任何任务。
 */
fun resolveMood(
    thinking: Boolean,
    allDone: Boolean,
    hour: Int,
    missStreak: Int,
): PetMood {
    if (thinking) return PetMood.THINKING
    if (missStreak >= DEAD_MISS_DAYS) return PetMood.DEAD
    if (allDone) return PetMood.NORMAL
    if (hour >= HUNGRY_HOUR) return PetMood.HUNGRY
    return PetMood.WAITING
}

@Immutable
data class FocusTask(
    val id: String,
    val title: String,
    val subtitle: String,
    val secondsKey: String,
    val runningKey: String,
)

val READ_TASK = FocusTask(
    id = "read",
    title = "阅读信息源",
    subtitle = "读书、文章或课程，专注 30 分钟",
    secondsKey = KEY_READ_SECONDS,
    runningKey = KEY_READ_RUNNING,
)

val AI_TASK = FocusTask(
    id = "ai",
    title = "找AI对话",
    subtitle = "带着问题去聊，专注 30 分钟",
    secondsKey = KEY_AI_SECONDS,
    runningKey = KEY_AI_RUNNING,
)

@Immutable
data class Challenge(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val source: String = "ai",
)

@Immutable
data class FocusSession(
    val taskId: String,
    val date: String,
    val seconds: Long,
    val endedAt: Long,
)

@Immutable
data class ApiConfig(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_API_BASE,
    val model: String = DEFAULT_API_MODEL,
) {
    val configured: Boolean
        get() = apiKey.isNotBlank()

    val endpoint: String
        get() {
            val base = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_API_BASE }
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }
}

fun prefs(context: Context): SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

fun dateString(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

fun todayString(): String = dateString(System.currentTimeMillis())

fun parseDate(date: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date)?.time
} catch (_: Exception) {
    null
}

fun daysBetween(start: String, end: String): Int {
    val from = parseDate(start) ?: return 0
    val to = parseDate(end) ?: return 0
    return Math.round((to - from) / 86_400_000.0).toInt()
}

fun shiftDate(date: String, delta: Int): String {
    val parsed = parseDate(date) ?: return date
    val calendar = Calendar.getInstance().apply {
        timeInMillis = parsed
        add(Calendar.DAY_OF_MONTH, delta)
    }
    return dateString(calendar.timeInMillis)
}

fun dayOfWeek(date: String): Int {
    val parsed = parseDate(date) ?: System.currentTimeMillis()
    return Calendar.getInstance().apply { timeInMillis = parsed }.get(Calendar.DAY_OF_WEEK)
}

fun hourOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)

fun minuteOf(millis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.MINUTE)

fun isWeekend(date: String = todayString()): Boolean {
    val day = dayOfWeek(date)
    return day == Calendar.SATURDAY || day == Calendar.SUNDAY
}

fun daysInMonth(year: Int, month0: Int): Int {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month0, 1)
    }
    return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
}

fun firstWeekdayOffset(year: Int, month0: Int): Int {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(year, month0, 1)
    }
    // Calendar.SUNDAY = 1 ... Calendar.SATURDAY = 7；日历从周一开始，所以周日排最后。
    return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

class FocusTimerStore(
    private val prefs: SharedPreferences,
    val task: FocusTask,
    initialSeconds: Long,
    initialRunning: Boolean,
    private val onCompleted: (Long) -> Unit = {},
) {
    var accumulatedSeconds by mutableLongStateOf(
        initialSeconds.coerceIn(0L, FOCUS_GOAL_SECONDS),
    )
        private set

    var running by mutableStateOf(initialRunning && initialSeconds < FOCUS_GOAL_SECONDS)
        private set

    private val lastTickKey = "${task.secondsKey}_last_tick"
    private var lastTickMillis: Long = prefs.getLong(lastTickKey, 0L)

    val completed: Boolean
        get() = accumulatedSeconds >= FOCUS_GOAL_SECONDS

    val progress: Float
        get() = (accumulatedSeconds.toFloat() / FOCUS_GOAL_SECONDS.toFloat()).coerceIn(0f, 1f)

    val focusedMinutes: Long
        get() = accumulatedSeconds / 60L

    fun onStartPauseClick() {
        if (completed) return
        if (running) {
            addElapsed()
            running = false
            lastTickMillis = 0L
        } else {
            running = true
            lastTickMillis = System.currentTimeMillis()
        }
        persist(flush = true)
    }

    fun pause() {
        if (!running) return
        addElapsed()
        running = false
        lastTickMillis = 0L
        persist(flush = true)
    }

    /**
     * 用时间戳补齐经过的秒数。
     *
     * 阅读/找AI 不再占用前台服务通知，所以 App 在后台时不会每秒执行到这里；
     * 但开始时间会持久化，回到 App（或进程被杀后重新打开）时会把后台的整段
     * 时间一次性补回来，不会像固定 +1 秒那样丢掉。
     */
    fun tick() {
        if (!running || completed) return
        val now = System.currentTimeMillis()
        if (lastTickMillis <= 0L) {
            lastTickMillis = now
            persist(flush = false)
            return
        }
        val last = lastTickMillis
        val deltaSeconds = ((now - last) / 1_000L).coerceAtLeast(0L)
        if (deltaSeconds <= 0L) return
        addElapsed(deltaSeconds, now)
        persist(flush = false)
    }

    private fun addElapsed(deltaSeconds: Long = 0L, now: Long = System.currentTimeMillis()) {
        val delta = if (deltaSeconds > 0L) {
            deltaSeconds
        } else if (lastTickMillis > 0L) {
            ((now - lastTickMillis) / 1_000L).coerceAtLeast(0L)
        } else {
            0L
        }
        if (delta <= 0L) return
        accumulatedSeconds = (accumulatedSeconds + delta)
            .coerceAtMost(FOCUS_GOAL_SECONDS)
        lastTickMillis = now
        if (accumulatedSeconds >= FOCUS_GOAL_SECONDS) {
            accumulatedSeconds = FOCUS_GOAL_SECONDS
            running = false
            onCompleted(FOCUS_GOAL_SECONDS)
        }
    }

    fun reset() {
        accumulatedSeconds = 0L
        running = false
        lastTickMillis = 0L
        persist(flush = true)
    }

    fun reload() {
        accumulatedSeconds = prefs.getLong(task.secondsKey, 0L)
            .coerceIn(0L, FOCUS_GOAL_SECONDS)
        running = prefs.getBoolean(task.runningKey, false) && !completed
        lastTickMillis = prefs.getLong(lastTickKey, 0L)
        if (running && lastTickMillis <= 0L) {
            lastTickMillis = System.currentTimeMillis()
        }
    }

    private fun persist(flush: Boolean) {
        val editor = prefs.edit()
            .putLong(task.secondsKey, accumulatedSeconds)
            .putBoolean(task.runningKey, running && !completed)
            .putLong(lastTickKey, lastTickMillis)
        if (flush) editor.commit() else editor.apply()
    }
}

enum class TimeSlot(val label: String, val targetSeconds: Long) {
    FOCUS("专注", 8L * 3600L),
    IDLE("无脑玩", 4L * 3600L),
    TEMPER("节制玩", 4L * 3600L),
}

class SlotTimer internal constructor(
    val slot: TimeSlot,
    initialSeconds: Long,
    initialRunning: Boolean,
) {
    var seconds by mutableLongStateOf(initialSeconds.coerceIn(0L, slot.targetSeconds))
        internal set

    var running by mutableStateOf(initialRunning && initialSeconds < slot.targetSeconds)
        internal set

    val progress: Float
        get() = (seconds.toFloat() / slot.targetSeconds.toFloat()).coerceIn(0f, 1f)

    val completed: Boolean
        get() = seconds >= slot.targetSeconds

    val remainingSeconds: Long
        get() = (slot.targetSeconds - seconds).coerceAtLeast(0L)
}

class TimeManager(context: Context) {
    private val prefs = prefs(context)
    private var day: String = prefs.getString(KEY_TIME_DAY, todayString()) ?: todayString()

    val slots: List<SlotTimer> = TimeSlot.entries.map { slot ->
        SlotTimer(
            slot = slot,
            initialSeconds = prefs.getLong(slot.secondsKey(), 0L),
            initialRunning = prefs.getBoolean(slot.runningKey(), false),
        )
    }

    init {
        if (!prefs.contains(KEY_TIME_DAY)) {
            prefs.edit().putString(KEY_TIME_DAY, day).apply()
        }
        ensureDay()
        if (isRestDay()) pauseAll()
    }

    fun slot(timer: TimeSlot): SlotTimer = slots.first { it.slot == timer }

    fun hasRunning(): Boolean = slots.any { it.running }

    fun runningSlot(): SlotTimer? = slots.firstOrNull { it.running }

    fun isRestDay(): Boolean = isWeekend(todayString())

    fun ensureDay() {
        val today = todayString()
        if (day == today) return
        day = today
        slots.forEach {
            it.seconds = 0L
            it.running = false
        }
        persistAll(flush = true)
    }

    fun toggle(timer: TimeSlot) {
        ensureDay()
        if (isRestDay()) return
        val target = slot(timer)
        if (target.running) {
            target.running = false
            persist(target, flush = true)
        } else {
            if (target.completed) return
            val editor = prefs.edit().putString(KEY_TIME_DAY, day)
            slots.forEach { item ->
                item.running = item === target
                editor.putBoolean(item.slot.runningKey(), item.running)
            }
            editor.apply()
        }
    }

    fun pauseAll() {
        if (slots.none { it.running }) return
        val editor = prefs.edit().putString(KEY_TIME_DAY, day)
        slots.forEach {
            it.running = false
            editor.putBoolean(it.slot.runningKey(), false)
        }
        editor.commit()
    }

    fun reset(timer: TimeSlot) {
        ensureDay()
        val target = slot(timer)
        target.seconds = 0L
        target.running = false
        persist(target, flush = true)
    }

    fun tick(deltaSeconds: Long = 1L) {
        ensureDay()
        if (isRestDay()) {
            pauseAll()
            return
        }
        val delta = deltaSeconds.coerceAtLeast(1L)
        var changed = false
        slots.forEach { item ->
            if (item.running) {
                if (item.seconds < item.slot.targetSeconds) {
                    item.seconds = (item.seconds + delta)
                        .coerceAtMost(item.slot.targetSeconds)
                    changed = true
                }
                if (item.seconds >= item.slot.targetSeconds) {
                    item.seconds = item.slot.targetSeconds
                    item.running = false
                }
            }
        }
        if (changed) persistAll(flush = false)
    }

    private fun persist(timer: SlotTimer, flush: Boolean) {
        val editor = prefs.edit()
            .putString(KEY_TIME_DAY, day)
            .putLong(timer.slot.secondsKey(), timer.seconds)
            .putBoolean(timer.slot.runningKey(), timer.running)
        if (flush) editor.commit() else editor.apply()
    }

    private fun persistAll(flush: Boolean) {
        val editor = prefs.edit().putString(KEY_TIME_DAY, day)
        slots.forEach { item ->
            editor.putLong(item.slot.secondsKey(), item.seconds)
            editor.putBoolean(item.slot.runningKey(), item.running)
        }
        if (flush) editor.commit() else editor.apply()
    }

    private fun TimeSlot.secondsKey(): String = "time_${name.lowercase(Locale.US)}_seconds"
    private fun TimeSlot.runningKey(): String = "time_${name.lowercase(Locale.US)}_running"

    private companion object {
        const val KEY_TIME_DAY = "time_manager_day"
    }
}

class AppStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = prefs(appContext)

    val readTimer: FocusTimerStore
    val aiTimer: FocusTimerStore
    val challenges: SnapshotStateList<Challenge>
    val timeManager = TimeManager(appContext)

    init {
        rolloverStorage(todayString())
        readTimer = FocusTimerStore(
            prefs = prefs,
            task = READ_TASK,
            initialSeconds = prefs.getLong(READ_TASK.secondsKey, 0L),
            initialRunning = prefs.getBoolean(READ_TASK.runningKey, false),
            onCompleted = { seconds -> recordSession(READ_TASK.id, seconds) },
        )
        aiTimer = FocusTimerStore(
            prefs = prefs,
            task = AI_TASK,
            initialSeconds = prefs.getLong(AI_TASK.secondsKey, 0L),
            initialRunning = prefs.getBoolean(AI_TASK.runningKey, false),
            onCompleted = { seconds -> recordSession(AI_TASK.id, seconds) },
        )
        challenges = mutableStateListOf<Challenge>().apply {
            addAll(loadChallenges())
        }
    }

    // ---------- 跨天 ----------

    /** 返回 true 表示刚跨天并且已经清空昨日进度。 */
    fun rolloverIfNeeded(): Boolean {
        val changed = rolloverStorage(todayString())
        if (changed) {
            readTimer.reload()
            aiTimer.reload()
            reloadChallenges()
        }
        return changed
    }

    private fun rolloverStorage(today: String): Boolean {
        val store = prefs
        val storedDate = store.getString(KEY_TASK_DATE, null)
        if (storedDate == null) {
            store.edit()
                .putString(KEY_TASK_DATE, today)
                .putInt(KEY_MISS_STREAK, 0)
                .apply()
            return false
        }
        if (storedDate == today) return false

        val readDone = store.getLong(READ_TASK.secondsKey, 0L) >= FOCUS_GOAL_SECONDS
        val aiDone = store.getLong(AI_TASK.secondsKey, 0L) >= FOCUS_GOAL_SECONDS
        val dailyDone = decodeChallenges(store.getString(KEY_CHALLENGES, null))
            .count { it.done } >= CHALLENGE_TARGET
        if (readDone || aiDone || dailyDone) {
            addHistory(storedDate)
        }

        val gap = daysBetween(storedDate, today).coerceAtLeast(1)
        val oldStreak = store.getInt(KEY_MISS_STREAK, 0)
        val newStreak = if (readDone || aiDone || dailyDone) {
            (gap - 1).coerceAtLeast(0)
        } else {
            oldStreak + gap
        }
        store.edit()
            .putString(KEY_TASK_DATE, today)
            .putInt(KEY_MISS_STREAK, newStreak)
            .putLong(READ_TASK.secondsKey, 0L)
            .putBoolean(READ_TASK.runningKey, false)
            .putLong(AI_TASK.secondsKey, 0L)
            .putBoolean(AI_TASK.runningKey, false)
            .remove(KEY_CHALLENGES)
            .commit()
        return true
    }

    // ---------- 今日状态 ----------

    fun readDone(): Boolean = readTimer.completed

    fun aiDone(): Boolean = aiTimer.completed

    fun dailyDone(): Boolean = allChallengesDone()

    fun allDone(): Boolean = readDone() && aiDone() && dailyDone()

    fun anyDone(): Boolean = readDone() || aiDone() || dailyDone()

    fun summary(
        now: Long = System.currentTimeMillis(),
        thinking: Boolean = false,
    ): DaySummary {
        val miss = missStreak()
        val done = allDone()
        return DaySummary(
            readDone = readDone(),
            aiDone = aiDone(),
            dailyDone = dailyDone(),
            challengeDone = challengeDoneCount(),
            challengeTotal = CHALLENGE_TARGET,
            readMinutes = readTimer.focusedMinutes,
            aiMinutes = aiTimer.focusedMinutes,
            missStreak = miss,
            mood = resolveMood(
                thinking = thinking,
                allDone = done,
                hour = hourOf(now),
                missStreak = miss,
            ),
            goal = goal(),
            hour = hourOf(now),
        )
    }

    fun markCheckIn(date: String = todayString()) {
        addHistory(date)
        val last = prefs.getString(KEY_LAST_CHECK_IN, null)
        val editor = prefs.edit().putInt(KEY_MISS_STREAK, 0)
        if (last == null || date > last) {
            editor.putString(KEY_LAST_CHECK_IN, date)
        }
        editor.commit()
    }

    private fun addHistory(date: String) {
        val set = historyDates().toMutableSet()
        if (!set.add(date)) return
        prefs.edit().putStringSet(KEY_HISTORY, set).commit()
    }

    fun historyDates(): Set<String> =
        prefs.getStringSet(KEY_HISTORY, emptySet())?.toSet() ?: emptySet()

    /** 一次性算出所有打卡日期，供日历批量渲染，避免每天重复解析 JSON。 */
    fun checkInDates(): Set<String> {
        val dates = historyDates().toMutableSet()
        completedSessions().forEach { dates.add(it.date) }
        if (anyDone()) dates.add(todayString())
        return dates
    }

    fun checkInOn(date: String): Boolean {
        if (date == todayString()) return anyDone()
        if (historyDates().contains(date)) return true
        return completedSessions().any { it.date == date && it.seconds >= FOCUS_GOAL_SECONDS }
    }

    fun lastCheckInDate(): String? {
        val today = todayString()
        val dates = historyDates().toMutableSet()
        completedSessions().forEach { dates.add(it.date) }
        if (anyDone()) dates.add(today)
        return dates.filter { it <= today }.maxOrNull()
    }

    /**
     * 已完整漏掉的天数。今天没打卡不算“破防”，昨天也没打卡才算 1。
     * 如果用户从未打卡，则回退到跨天时累计的 miss_streak。
     */
    fun missStreak(): Int {
        val last = lastCheckInDate() ?: return prefs.getInt(KEY_MISS_STREAK, 0)
        val gap = daysBetween(last, todayString())
        return (gap - 1).coerceAtLeast(0)
    }

    // ---------- 专注记录 ----------

    fun completedSessions(): List<FocusSession> = decodeSessions(
        prefs.getString(KEY_SESSIONS, null),
    )

    private fun recordSession(taskId: String, seconds: Long) {
        val date = todayString()
        val sessions = completedSessions().toMutableList()
        sessions.add(
            FocusSession(
                taskId = taskId,
                date = date,
                seconds = seconds,
                endedAt = System.currentTimeMillis(),
            ),
        )
        val trimmed = if (sessions.size > 400) sessions.takeLast(400) else sessions
        val array = JSONArray()
        trimmed.forEach { session ->
            array.put(
                JSONObject()
                    .put("taskId", session.taskId)
                    .put("date", session.date)
                    .put("seconds", session.seconds)
                    .put("endedAt", session.endedAt),
            )
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).commit()
        markCheckIn(date)
    }

    fun sessionsOn(date: String): List<FocusSession> =
        completedSessions().filter { it.date == date }

    // ---------- 每日挑战 ----------

    fun allChallengesDone(): Boolean =
        challenges.size >= CHALLENGE_TARGET && challenges.all { it.done }

    fun challengeDoneCount(): Int = challenges.count { it.done }

    fun challengeProgress(): Float =
        (challengeDoneCount().toFloat() / CHALLENGE_TARGET.toFloat()).coerceIn(0f, 1f)

    fun setChallengeDone(id: String, done: Boolean) {
        val index = challenges.indexOfFirst { it.id == id }
        if (index < 0) return
        challenges[index] = challenges[index].copy(done = done)
        saveChallenges()
        if (done) markCheckIn()
    }

    fun updateChallengeText(id: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val index = challenges.indexOfFirst { it.id == id }
        if (index < 0) return
        challenges[index] = challenges[index].copy(text = clean)
        saveChallenges()
    }

    fun addChallenge(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank() || challenges.size >= CHALLENGE_TARGET) return false
        challenges.add(
            Challenge(
                id = UUID.randomUUID().toString(),
                text = clean,
                done = false,
                source = "user",
            ),
        )
        saveChallenges()
        return true
    }

    fun replaceChallenges(items: List<String>) {
        val clean = items.map { it.trim() }.filter { it.isNotBlank() }.take(CHALLENGE_TARGET)
        if (clean.size < CHALLENGE_TARGET) return
        challenges.clear()
        clean.forEach { text ->
            challenges.add(
                Challenge(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    done = false,
                    source = "ai",
                ),
            )
        }
        saveChallenges()
        markChallengeGeneratedToday()
    }

    fun challengeGeneratedToday(): Boolean =
        prefs.getString(KEY_CHALLENGE_GENERATED_DATE, null) == todayString()

    fun markChallengeGeneratedToday() {
        prefs.edit()
            .putString(KEY_CHALLENGE_GENERATED_DATE, todayString())
            .commit()
    }

    private fun saveChallenges() {
        val array = JSONArray()
        challenges.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("done", item.done)
                    .put("source", item.source),
            )
        }
        prefs.edit().putString(KEY_CHALLENGES, array.toString()).apply()
    }

    private fun reloadChallenges() {
        challenges.clear()
        challenges.addAll(loadChallenges())
    }

    private fun loadChallenges(): List<Challenge> {
        // 不预填模板：第一条可以由用户自己写，也可以让 AI 生成 5 条。
        return decodeChallenges(prefs.getString(KEY_CHALLENGES, null))
            .take(CHALLENGE_TARGET)
    }

    // ---------- 目标 ----------

    fun goal(): String {
        val saved = prefs.getString(KEY_GOAL, null)?.trim().orEmpty()
        return saved.ifBlank { DEFAULT_GOAL }
    }

    fun saveGoal(goal: String) {
        val value = goal.trim().ifBlank { DEFAULT_GOAL }
        prefs.edit().putString(KEY_GOAL, value).commit()
    }

    // ---------- API ----------

    fun apiConfig(): ApiConfig = ApiConfig(
        apiKey = prefs.getString(KEY_API_KEY, null).orEmpty(),
        baseUrl = prefs.getString(KEY_API_BASE, null).orEmpty()
            .ifBlank { DEFAULT_API_BASE },
        model = prefs.getString(KEY_API_MODEL, null).orEmpty()
            .ifBlank { DEFAULT_API_MODEL },
    )

    fun saveApiConfig(apiKey: String, baseUrl: String, model: String) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(
                KEY_API_BASE,
                baseUrl.trim().ifBlank { DEFAULT_API_BASE },
            )
            .putString(
                KEY_API_MODEL,
                model.trim().ifBlank { DEFAULT_API_MODEL },
            )
            .commit()
    }

    fun clearApiKey() {
        prefs.edit().putString(KEY_API_KEY, "").commit()
    }

    // ---------- 通知 ----------

    fun notificationEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_ENABLED, true)

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ENABLED, enabled).commit()
    }

    fun wasNotifiedToday(): Boolean =
        prefs.getString(KEY_NOTIFIED_DATE, null) == todayString()

    fun markNotifiedToday() {
        prefs.edit().putString(KEY_NOTIFIED_DATE, todayString()).commit()
    }

    fun resetNotificationFlag() {
        prefs.edit().remove(KEY_NOTIFIED_DATE).apply()
    }

    private fun decodeChallenges(raw: String?): List<Challenge> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isBlank()) continue
                    add(
                        Challenge(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            done = item.optBoolean("done", false),
                            source = item.optString("source", "ai"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeSessions(raw: String?): List<FocusSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        FocusSession(
                            taskId = item.optString("taskId"),
                            date = item.optString("date"),
                            seconds = item.optLong("seconds", 0L),
                            endedAt = item.optLong("endedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var shared: AppStore? = null

        fun get(context: Context): AppStore {
            return shared ?: synchronized(this) {
                shared ?: AppStore(context.applicationContext).also { shared = it }
            }
        }
    }
}
```

## `app/src/main/java/com/example/stusy/data/DaySummary.kt`

```kotlin
package com.example.stusy.data

import androidx.compose.runtime.Immutable

@Immutable
data class DaySummary(
    val readDone: Boolean,
    val aiDone: Boolean,
    val dailyDone: Boolean,
    val challengeDone: Int,
    val challengeTotal: Int,
    val readMinutes: Long,
    val aiMinutes: Long,
    val missStreak: Int,
    val mood: PetMood,
    val goal: String,
    val hour: Int,
) {
    val allDone: Boolean
        get() = readDone && aiDone && dailyDone

    val completedTasks: List<String>
        get() = buildList {
            if (readDone) add("阅读信息源")
            if (aiDone) add("找AI对话")
            if (dailyDone) add("每日挑战")
        }

    val pendingTasks: List<String>
        get() = buildList {
            if (!readDone) add("阅读信息源")
            if (!aiDone) add("找AI对话")
            if (!dailyDone) add("每日挑战")
        }

    fun promptBlock(): String {
        val readState = if (readDone) "已完成" else "未完成"
        val aiState = if (aiDone) "已完成" else "未完成"
        val dailyState = if (dailyDone) "已完成" else "未完成"
        return buildString {
            appendLine("现在是 ${hour} 点。")
            appendLine("大学目标：$goal")
            appendLine("阅读信息源：$readState（已专注 $readMinutes 分钟，目标 30 分钟）")
            appendLine("找AI对话：$aiState（已专注 $aiMinutes 分钟，目标 30 分钟）")
            appendLine("每日挑战：$dailyState（完成 $challengeDone/$challengeTotal）")
            appendLine("已经完整漏掉的天数：$missStreak")
            append("当前状态：${moodWord()}")
        }
    }

    fun moodWord(): String = when (mood) {
        PetMood.NORMAL -> "开心，今天吃饱了"
        PetMood.WAITING -> "期待，等主人投喂"
        PetMood.HUNGRY -> "饿肚子，晚上九点后还没开饭"
        PetMood.DEAD -> "破防，已经很久没吃饭"
        PetMood.THINKING -> "正在想今天的挑战"
    }
}
```

## `app/src/main/java/com/example/stusy/MainActivity.kt`

```kotlin
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
```

## `app/src/main/java/com/example/stusy/notify/Reminder.kt`

```kotlin
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
```

## `app/src/main/java/com/example/stusy/timer/TimerService.kt`

```kotlin
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
```

## `app/src/main/java/com/example/stusy/ui/CalendarScreen.kt`

```kotlin
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
```

## `app/src/main/java/com/example/stusy/ui/ChallengesDialog.kt`

```kotlin
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.stusy.data.CHALLENGE_TARGET
import com.example.stusy.data.Challenge

@Composable
internal fun ChallengesDialog(
    challenges: List<Challenge>,
    loading: Boolean,
    error: String?,
    polishingId: String?,
    onDismiss: () -> Unit,
    onToggle: (Challenge) -> Unit,
    onPolish: (Challenge, String) -> Unit,
    onSaveText: (Challenge, String) -> Unit,
    onAdd: (String) -> Boolean,
    onRegenerate: () -> Unit,
) {
    val c = LocalStusyColors.current
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    val doneCount = challenges.count { it.done }
    val anyDone = doneCount > 0

    LaunchedEffect(editingId, challenges) {
        val editing = challenges.firstOrNull { it.id == editingId }
        if (editing != null) editingText = editing.text
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .shadow(26.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(26.dp))
                    .imePadding()
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🍚 今日挑战",
                            color = c.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "完成 5 条，鲸鲸今天就能吃饱",
                            color = c.textSoft,
                            fontSize = 11.5.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(c.surfaceAlt)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "✕", color = c.textSoft, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WhaleProgressBar(
                        progress = doneCount.toFloat() / CHALLENGE_TARGET.toFloat(),
                        color = c.warm,
                        modifier = Modifier.weight(1f),
                        height = 7.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$doneCount/$CHALLENGE_TARGET",
                        color = c.textSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (loading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.accentSoft.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = c.accent,
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Text(
                            text = "鲸鲸正在想新的 5 条挑战…",
                            color = c.accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = c.danger,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.danger.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (challenges.isEmpty() && !loading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🍚",
                                fontSize = 34.sp,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "今天还没有挑战",
                                color = c.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "在下面写第一条，或让鲸鲸生成 5 条\n自己的任务永远比模板更合胃口",
                                color = c.textFaint,
                                fontSize = 10.sp,
                                lineHeight = 15.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(challenges, key = { it.id }) { item ->
                            ChallengeRow(
                                item = item,
                                editing = editingId == item.id,
                                editingText = editingText,
                                polishing = polishingId == item.id,
                                busy = loading || polishingId != null,
                                onEditTextChange = { editingText = it },
                                onToggle = { onToggle(item) },
                                onStartEdit = {
                                    editingId = item.id
                                    editingText = item.text
                                },
                                onCancelEdit = { editingId = null },
                                onSaveEdit = {
                                    onSaveText(item, editingText)
                                    editingId = null
                                },
                                onPolish = {
                                    val text = if (editingId == item.id) editingText else item.text
                                    onPolish(item, text)
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (challenges.size < CHALLENGE_TARGET) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newText,
                            onValueChange = {
                                newText = it
                                addError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(text = "自己加一条今天想做的…", fontSize = 12.sp)
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(14.dp),
                            colors = stusyFieldColors(),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StusyButton(
                            text = "添加",
                            onClick = {
                                if (onAdd(newText)) {
                                    newText = ""
                                    addError = null
                                } else {
                                    addError = "最多只能有 5 条挑战喔"
                                }
                            },
                            enabled = newText.isNotBlank() && !loading,
                        )
                    }
                    if (addError != null) {
                        Text(
                            text = addError.orEmpty(),
                            color = c.danger,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                } else {
                    Text(
                        text = "今日 5 条已满 · 可以点「AI 润色」把描述变清楚",
                        color = c.textFaint,
                        fontSize = 10.5.sp,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StusyTextButton(
                        text = if (anyDone) "已有打卡，明天再换" else "AI 重新生成 5 条",
                        onClick = onRegenerate,
                        enabled = !loading && polishingId == null && !anyDone,
                        color = c.accent,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    StusyButton(
                        text = "收下挑战",
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeRow(
    item: Challenge,
    editing: Boolean,
    editingText: String,
    polishing: Boolean,
    busy: Boolean,
    onEditTextChange: (String) -> Unit,
    onToggle: () -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onPolish: () -> Unit,
) {
    val c = LocalStusyColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceAlt.copy(alpha = if (c.isDay) 0.55f else 0.7f))
            .border(1.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CheckCircle(
                checked = item.done,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .clickable(onClick = onToggle),
                size = 21.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (editing) {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = onEditTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 1,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = stusyFieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StusyTextButton(
                            text = "保存",
                            onClick = onSaveEdit,
                            enabled = editingText.isNotBlank() && !busy,
                        )
                        StusyTextButton(
                            text = "取消",
                            onClick = onCancelEdit,
                            color = c.textSoft,
                        )
                        StusyTextButton(
                            text = if (polishing) "润色中…" else "AI 润色",
                            onClick = onPolish,
                            enabled = !busy && editingText.isNotBlank(),
                        )
                    }
                } else {
                    Text(
                        text = item.text,
                        color = if (item.done) c.textFaint else c.text,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (item.source) {
                                "user" -> "自定"
                                "ai" -> "AI"
                                else -> "模板"
                            },
                            color = c.textFaint,
                            fontSize = 9.sp,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (polishing) {
                            Text(text = "鲸鲸正在润色…", color = c.accent, fontSize = 10.sp)
                        } else {
                            StusyTextButton(
                                text = "编辑",
                                onClick = onStartEdit,
                                enabled = !busy,
                            )
                            StusyTextButton(
                                text = "AI 润色",
                                onClick = onPolish,
                                enabled = !busy,
                            )
                        }
                    }
                }
            }
        }
    }
}
```

## `app/src/main/java/com/example/stusy/ui/Common.kt`

```kotlin
package com.example.stusy.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stusy.data.PetMood
import com.example.stusy.data.drawableRes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class StusyTab(val label: String) {
    Today("今天"),
    Calendar("日历"),
    Time("时间"),
    Settings("设置"),
}

@Composable
fun WhaleCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalStusyColors.current
    val shape = RoundedCornerShape(22.dp)
    var m = modifier.fillMaxWidth()
    if (c.isDay) {
        m = m.shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = c.shadow,
            spotColor = c.shadow,
        )
    } else {
        m = m.shadow(
            elevation = 16.dp,
            shape = shape,
            ambientColor = c.accent.copy(alpha = 0.16f),
            spotColor = c.accent.copy(alpha = 0.12f),
        )
    }
    m = m
        .clip(shape)
        .background(if (c.isDay) c.surface else c.surface.copy(alpha = 0.9f))
        .border(1.dp, c.border, shape)
    if (onClick != null) {
        m = m.clickable(onClick = onClick)
    }
    Column(modifier = m.padding(contentPadding), content = content)
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = LocalStusyColors.current.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun WhaleProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val c = LocalStusyColors.current
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "whaleProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(c.track),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.62f), color),
                        ),
                    ),
            )
        }
    }
}

@Composable
fun RingProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = LocalStusyColors.current.track,
    content: @Composable BoxScope.() -> Unit,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "ringProgress",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (animated > 0.001f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            color.copy(alpha = 0.45f),
                            color,
                            color.copy(alpha = 0.75f),
                        ),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    val c = LocalStusyColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 328.dp)
                .shadow(
                    elevation = if (c.isDay) 8.dp else 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = if (c.isDay) c.shadow else c.accent.copy(alpha = 0.22f),
                    spotColor = if (c.isDay) c.shadow else c.accent.copy(alpha = 0.18f),
                )
                .clip(RoundedCornerShape(20.dp))
                .background(c.bubble)
                .border(1.dp, c.bubbleBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 5 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "speech",
            ) { value ->
                Text(
                    text = value,
                    color = c.text,
                    fontSize = 13.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Canvas(modifier = Modifier.size(width = 18.dp, height = 9.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path = path, color = c.bubble)
        }
    }
}

@Composable
fun PetPortrait(
    mood: PetMood,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    val breath = rememberInfiniteTransition(label = "petBreath")
    val scale by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "petScale",
    )
    val clickable = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(role = Role.Button, onClick = onClick)

    if (c.isDay) {
        Box(
            modifier = clickable.size(186.dp).rotate(-1.4f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = c.shadow,
                        spotColor = c.shadow,
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFFFE4B4), RoundedCornerShape(24.dp)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    ) {
                        Crossfade(
                            targetState = mood,
                            animationSpec = tween(420),
                            label = "petDay",
                        ) { value ->
                            Image(
                                painter = painterResource(value.drawableRes()),
                                contentDescription = "鲸鲸",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    Text(
                        text = "鲸鲸 · 白米饭契约",
                        color = Color(0xFF7890A8),
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(bottom = 9.dp, top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-9).dp)
                    .rotate(3.4f)
                    .size(width = 58.dp, height = 16.dp)
                    .background(Color(0xE6FFE7B8), RoundedCornerShape(2.dp)),
            )
        }
    } else {
        Box(
            modifier = clickable.size(176.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            c.accent.copy(alpha = 0.30f),
                            c.accent.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension / 2f,
                    ),
                    radius = size.minDimension / 2f,
                    center = center,
                )
                drawCircle(
                    color = c.accent.copy(alpha = 0.34f),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier
                    .size(154.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = c.accent.copy(alpha = 0.5f),
                        spotColor = c.accent.copy(alpha = 0.4f),
                    )
                    .clip(CircleShape)
                    .border(1.5.dp, c.accent.copy(alpha = 0.6f), CircleShape)
                    .padding(7.dp),
            ) {
                Crossfade(
                    targetState = mood,
                    animationSpec = tween(420),
                    label = "petNight",
                ) { value ->
                    Image(
                        painter = painterResource(value.drawableRes()),
                        contentDescription = "鲸鲸",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 23.dp) {
    val c = LocalStusyColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) c.accent else Color.Transparent)
            .border(1.5.dp, if (checked) c.accent else c.textFaint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                text = "✓",
                color = c.onAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StusyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = LocalStusyColors.current
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.track,
            disabledContentColor = c.textFaint,
        ),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StusyTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = LocalStusyColors.current.accent,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) color else LocalStusyColors.current.textFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StusyDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalStusyColors.current.border.copy(alpha = 0.7f)),
    )
}

@Composable
fun stusyFieldColors(): TextFieldColors {
    val c = LocalStusyColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        disabledTextColor = c.textFaint,
        cursorColor = c.accent,
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.border,
        disabledBorderColor = c.border,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSoft,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedPlaceholderColor = c.textFaint,
        unfocusedPlaceholderColor = c.textFaint,
        focusedTrailingIconColor = c.accent,
        unfocusedTrailingIconColor = c.textSoft,
    )
}

@Composable
fun ThemeBadge(modifier: Modifier = Modifier) {
    val c = LocalStusyColors.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (c.isDay) Color.White.copy(alpha = 0.72f) else c.surfaceAlt)
            .border(1.dp, c.border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (c.isDay) "☀" else "☾",
            color = if (c.isDay) c.warm else c.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (c.isDay) "白昼" else "夜间",
            color = c.textSoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun StusyBottomBar(
    selected: StusyTab,
    onSelect: (StusyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (c.isDay) 16.dp else 22.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = if (c.isDay) c.shadow else c.accent.copy(alpha = 0.16f),
                    spotColor = if (c.isDay) c.shadow else c.accent.copy(alpha = 0.12f),
                )
                .clip(RoundedCornerShape(26.dp))
                .background(c.navBg)
                .border(1.dp, c.border, RoundedCornerShape(26.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StusyTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(19.dp))
                        .background(
                            if (isSelected) {
                                c.accentSoft.copy(alpha = if (c.isDay) 0.9f else 0.62f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TabGlyph(
                            tab = tab,
                            color = if (isSelected) c.accent else c.textFaint,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.label,
                            color = if (isSelected) c.accent else c.textFaint,
                            fontSize = 9.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabGlyph(tab: StusyTab, color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.6.dp.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        when (tab) {
            StusyTab.Today -> {
                val roof = Path().apply {
                    moveTo(w * 0.14f, h * 0.47f)
                    lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.47f)
                }
                drawPath(roof, color, style = stroke)
                val body = Path().apply {
                    moveTo(w * 0.23f, h * 0.44f)
                    lineTo(w * 0.23f, h * 0.84f)
                    lineTo(w * 0.77f, h * 0.84f)
                    lineTo(w * 0.77f, h * 0.44f)
                }
                drawPath(body, color, style = stroke)
                drawLine(
                    color = color,
                    start = Offset(w * 0.43f, h * 0.84f),
                    end = Offset(w * 0.43f, h * 0.63f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.43f, h * 0.63f),
                    end = Offset(w * 0.57f, h * 0.63f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.57f, h * 0.63f),
                    end = Offset(w * 0.57f, h * 0.84f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            StusyTab.Calendar -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.14f, h * 0.22f),
                    size = Size(w * 0.72f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.12f),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.14f, h * 0.4f),
                    end = Offset(w * 0.86f, h * 0.4f),
                    strokeWidth = strokeWidth,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.32f, h * 0.12f),
                    end = Offset(w * 0.32f, h * 0.28f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.68f, h * 0.12f),
                    end = Offset(w * 0.68f, h * 0.28f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                listOf(
                    Offset(w * 0.33f, h * 0.57f),
                    Offset(w * 0.5f, h * 0.57f),
                    Offset(w * 0.67f, h * 0.57f),
                    Offset(w * 0.33f, h * 0.72f),
                    Offset(w * 0.5f, h * 0.72f),
                ).forEach { point ->
                    drawCircle(color = color, radius = w * 0.035f, center = point)
                }
            }

            StusyTab.Time -> {
                drawCircle(
                    color = color,
                    radius = w * 0.35f,
                    center = Offset(w * 0.5f, h * 0.55f),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.55f),
                    end = Offset(w * 0.5f, h * 0.34f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.55f),
                    end = Offset(w * 0.63f, h * 0.62f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.42f, h * 0.13f),
                    end = Offset(w * 0.58f, h * 0.13f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.13f),
                    end = Offset(w * 0.5f, h * 0.2f),
                    strokeWidth = strokeWidth,
                )
            }

            StusyTab.Settings -> {
                drawCircle(
                    color = color,
                    radius = w * 0.13f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = stroke,
                )
                repeat(8) { index ->
                    val angle = index * PI / 4.0
                    val inner = w * 0.25f
                    val outer = w * 0.38f
                    drawLine(
                        color = color,
                        start = Offset(
                            (w * 0.5f + cos(angle).toFloat() * inner),
                            (h * 0.5f + sin(angle).toFloat() * inner),
                        ),
                        end = Offset(
                            (w * 0.5f + cos(angle).toFloat() * outer),
                            (h * 0.5f + sin(angle).toFloat() * outer),
                        ),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

fun moodLabel(mood: PetMood): String = when (mood) {
    PetMood.NORMAL -> "开心 · 吃饱啦"
    PetMood.WAITING -> "期待 · 等投喂"
    PetMood.HUNGRY -> "饿了 · 还没开饭"
    PetMood.DEAD -> "破防 · 饿扁了"
    PetMood.THINKING -> "思考中 · 想挑战"
}

@Composable
fun moodColor(mood: PetMood): Color {
    val c = LocalStusyColors.current
    return when (mood) {
        PetMood.NORMAL -> c.mint
        PetMood.WAITING -> c.accent
        PetMood.HUNGRY -> c.warm
        PetMood.DEAD -> c.danger
        PetMood.THINKING -> c.accentDeep
    }
}

fun greetingForHour(hour: Int): String = when (hour) {
    in 5..10 -> "早上好"
    in 11..12 -> "中午好"
    in 13..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

fun formatClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFullClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%d:%02d:%02d",
        safe / 3600L,
        (safe % 3600L) / 60L,
        safe % 60L,
    )
}

fun dateLabel(millis: Long): String =
    SimpleDateFormat("M月d日", Locale.CHINA).format(Date(millis))

fun weekdayLabel(millis: Long): String {
    val week = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
    val index = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
    return week[(index - 1).coerceIn(0, 6)]
}

fun monthLabel(millis: Long): String =
    SimpleDateFormat("yyyy年M月", Locale.CHINA).format(Date(millis))
```

## `app/src/main/java/com/example/stusy/ui/HomeScreen.kt`

```kotlin
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
```

## `app/src/main/java/com/example/stusy/ui/SettingsScreen.kt`

```kotlin
package com.example.stusy.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.stusy.ai.AiService
import com.example.stusy.data.ApiConfig
import com.example.stusy.data.AppStore
import com.example.stusy.notify.ReminderScheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    store: AppStore,
    onOpenTab: (StusyTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalStusyColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember { store.apiConfig() }

    var draftKey by remember { mutableStateOf(config.apiKey) }
    var draftBase by remember { mutableStateOf(config.baseUrl) }
    var draftModel by remember { mutableStateOf(config.model) }
    var keyVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(config.configured) }

    var notifyEnabled by remember { mutableStateOf(store.notificationEnabled()) }
    var notifyStatus by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted && notifyEnabled) {
            ReminderScheduler.setEnabled(context, true)
            notifyStatus = "通知权限已开启，今晚九点鲸鲸会来敲门"
        }
    }

    val draftEndpoint = remember(draftBase, draftModel) {
        ApiConfig(apiKey = "", baseUrl = draftBase, model = draftModel).endpoint
    }

    fun validate(): String? {
        if (draftKey.isBlank()) return "先填入你的 API Key"
        if (!draftBase.trim().startsWith("http")) return "接口地址需要以 http(s):// 开头"
        if (draftModel.isBlank()) return "模型名不能为空"
        return null
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
                                text = "设置",
                                color = c.text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "鲸鲸的投喂手册 · 接口、提醒与契约",
                                color = c.textSoft,
                                fontSize = 10.5.sp,
                            )
                        }
                        ThemeBadge()
                    }
                }

                item {
                    SectionTitle(title = "AI 接口")
                }

                item {
                    WhaleCard(contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(c.accentSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "AI",
                                    color = c.accentDeep,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "接入你自己的模型",
                                    color = c.text,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "兼容 OpenAI Chat Completions 协议",
                                    color = c.textFaint,
                                    fontSize = 9.5.sp,
                                )
                            }
                            Pill(
                                text = if (configured) "已配置" else "未配置",
                                foreground = if (configured) c.mint else c.danger,
                                background = if (configured) {
                                    c.mint.copy(alpha = 0.13f)
                                } else {
                                    c.danger.copy(alpha = 0.11f)
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = draftKey,
                            onValueChange = { draftKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = "API Key") },
                            placeholder = { Text(text = "sk-...") },
                            visualTransformation = if (keyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = stusyFieldColors(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "只保存在本机，不会上传到鲸鲸的服务器",
                                color = c.textFaint,
                                fontSize = 8.5.sp,
                                modifier = Modifier.weight(1f),
                            )
                            StusyTextButton(
                                text = if (keyVisible) "隐藏" else "显示",
                                onClick = { keyVisible = !keyVisible },
                                color = c.textSoft,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))

                        OutlinedTextField(
                            value = draftBase,
                            onValueChange = { draftBase = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = "接口地址 Base URL") },
                            placeholder = { Text(text = "https://api.deepseek.com") },
                            shape = RoundedCornerShape(14.dp),
                            colors = stusyFieldColors(),
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                        OutlinedTextField(
                            value = draftModel,
                            onValueChange = { draftModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(text = "模型名") },
                            placeholder = { Text(text = "deepseek-chat") },
                            shape = RoundedCornerShape(14.dp),
                            colors = stusyFieldColors(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "快捷填入",
                                color = c.textFaint,
                                fontSize = 8.5.sp,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            StusyTextButton(
                                text = "DeepSeek",
                                onClick = {
                                    draftBase = "https://api.deepseek.com"
                                    draftModel = "deepseek-chat"
                                },
                            )
                            StusyTextButton(
                                text = "OpenAI",
                                onClick = {
                                    draftBase = "https://api.openai.com/v1"
                                    draftModel = "gpt-4o-mini"
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "请求地址：$draftEndpoint",
                            color = c.textFaint,
                            fontSize = 8.sp,
                            lineHeight = 12.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            StusyButton(
                                text = if (testing) "测试中…" else "保存",
                                onClick = {
                                    val error = validate()
                                    if (error != null) {
                                        status = error
                                        statusError = true
                                        return@StusyButton
                                    }
                                    store.saveApiConfig(draftKey, draftBase, draftModel)
                                    configured = true
                                    status = "已保存，下次生成台词就会用这套接口"
                                    statusError = false
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !testing,
                            )
                            StusyButton(
                                text = if (testing) "连接中…" else "测试连接",
                                onClick = {
                                    val error = validate()
                                    if (error != null) {
                                        status = error
                                        statusError = true
                                        return@StusyButton
                                    }
                                    store.saveApiConfig(draftKey, draftBase, draftModel)
                                    configured = true
                                    scope.launch {
                                        testing = true
                                        status = null
                                        statusError = false
                                        try {
                                            AiService(store).testConnection()
                                            status = "连接成功，鲸鲸能吃到饭了"
                                            statusError = false
                                        } catch (e: Exception) {
                                            status = e.message ?: "连接失败，检查接口和网络"
                                            statusError = true
                                        } finally {
                                            testing = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !testing,
                            )
                        }

                        if (status != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = status.orEmpty(),
                                color = if (statusError) c.danger else c.mint,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        StusyTextButton(
                            text = "清除已保存的 Key",
                            onClick = {
                                draftKey = ""
                                store.clearApiKey()
                                configured = false
                                status = "Key 已从本机清除"
                                statusError = false
                            },
                            enabled = !testing,
                            color = c.textFaint,
                        )
                    }
                }

                item {
                    SectionTitle(title = "饿肚子提醒")
                }

                item {
                    WhaleCard(contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(c.warm.copy(alpha = if (c.isDay) 0.15f else 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "21:00",
                                        color = c.warm,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "鲸鲸饿",
                                        color = c.textSoft,
                                        fontSize = 7.sp,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "晚上九点的敲门声",
                                    color = c.text,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "超过 21:00 三项还没全完成时，发一条 AI 生成的催促通知（不超过 25 字）。",
                                    color = c.textSoft,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp,
                                )
                            }
                            Switch(
                                checked = notifyEnabled,
                                onCheckedChange = { checked ->
                                    notifyEnabled = checked
                                    if (checked) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            !permissionGranted
                                        ) {
                                            permissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        }
                                        ReminderScheduler.setEnabled(context, true)
                                        notifyStatus = "提醒已开启，今晚九点见"
                                    } else {
                                        ReminderScheduler.setEnabled(context, false)
                                        notifyStatus = "提醒已关闭，鲸鲸今晚不敲门"
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = c.onAccent,
                                    checkedTrackColor = c.accent,
                                    uncheckedThumbColor = c.textFaint,
                                    uncheckedTrackColor = c.track,
                                    uncheckedBorderColor = c.border,
                                ),
                            )
                        }

                        if (notifyEnabled && !permissionGranted) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.danger.copy(alpha = 0.10f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "系统通知权限还没开，鲸鲸敲不到门。",
                                    color = c.danger,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                StusyTextButton(
                                    text = "去开启",
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        }
                                    },
                                    color = c.danger,
                                )
                            }
                        }

                        if (notifyStatus != null) {
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = notifyStatus.orEmpty(),
                                color = c.mint,
                                fontSize = 9.5.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(7.dp))
                        Text(
                            text = "开启后，鲸鲸每天只会敲一次门；如果当天已经完成，她会安静地等你。",
                            color = c.textFaint,
                            fontSize = 8.5.sp,
                            lineHeight = 12.sp,
                        )
                    }
                }

                item {
                    SectionTitle(title = "今日契约")
                }

                item {
                    WhaleCard(contentPadding = 16.dp) {
                        Text(
                            text = store.goal(),
                            color = c.text,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "想改的话，回首页点一下最上面的目标卡。",
                            color = c.textFaint,
                            fontSize = 9.sp,
                        )
                    }
                }

                item {
                    SectionTitle(title = "关于")
                }

                item {
                    WhaleCard(contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🐳",
                                fontSize = 24.sp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stusy 2.0 · 白米饭契约",
                                    color = c.text,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "专注、打卡、被一只蓝发鲸鱼女仆惦记着。",
                                    color = c.textFaint,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(9.dp))
                        StusyDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "所有打卡、计时、挑战与 API 配置都只保存在这台手机本地。" +
                                "AI 请求会直接发往你填写的接口地址；换手机不会自动同步。",
                            color = c.textSoft,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "如果这个工程曾经把 Key 写进代码并分享过，请去控制台作废并重新生成。",
                            color = c.warm,
                            fontSize = 8.5.sp,
                            lineHeight = 12.sp,
                        )
                    }
                }
            }
            StusyBottomBar(
                selected = StusyTab.Settings,
                onSelect = onOpenTab,
            )
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
```

## `app/src/main/java/com/example/stusy/ui/Theme.kt`

```kotlin
package com.example.stusy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.random.Random

/**
 * 两套配色：
 * - 白昼（06:00–18:00）：晨光奶油 + 海水蓝，像一张摊开的鲸鲸手账。
 * - 夜间（18:00–06:00）：深海蓝 + 月光青，像鲸鲸房间里的夜灯。
 */
@Immutable
data class StusyColors(
    val isDay: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val textSoft: Color,
    val textFaint: Color,
    val accent: Color,
    val accentDeep: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val warm: Color,
    val mint: Color,
    val danger: Color,
    val bubble: Color,
    val bubbleBorder: Color,
    val navBg: Color,
    val track: Color,
    val shadow: Color,
)

val DayPalette = StusyColors(
    isDay = true,
    bgTop = Color(0xFFFFF9F0),
    bgBottom = Color(0xFFE7F2FF),
    glow = Color(0xFFFFE1A8),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF3F8FF),
    border = Color(0xFFDCE8F6),
    text = Color(0xFF16324C),
    textSoft = Color(0xFF71869D),
    textFaint = Color(0xFFA3B3C4),
    accent = Color(0xFF3D7FD6),
    accentDeep = Color(0xFF245CAB),
    accentSoft = Color(0xFFE1EEFF),
    onAccent = Color(0xFFFFFFFF),
    warm = Color(0xFFF2B45C),
    mint = Color(0xFF4FBFA0),
    danger = Color(0xFFE27D7D),
    bubble = Color(0xFFFFFFFF),
    bubbleBorder = Color(0xFFCFE2F8),
    navBg = Color(0xF7FFFFFF),
    track = Color(0xFFE8F1FC),
    shadow = Color(0x1A2B5C96),
)

val NightPalette = StusyColors(
    isDay = false,
    bgTop = Color(0xFF050D19),
    bgBottom = Color(0xFF0B2033),
    glow = Color(0xFF2A6FA8),
    surface = Color(0xFF0E2237),
    surfaceAlt = Color(0xFF132B43),
    border = Color(0xFF23445E),
    text = Color(0xFFEAF3FF),
    textSoft = Color(0xFF9AB2CA),
    textFaint = Color(0xFF64809B),
    accent = Color(0xFF7CC8FF),
    accentDeep = Color(0xFF4A9FE0),
    accentSoft = Color(0xFF173A58),
    onAccent = Color(0xFF04121F),
    warm = Color(0xFFFFD58A),
    mint = Color(0xFF6FE0B8),
    danger = Color(0xFFFF8E9E),
    bubble = Color(0xFF122A42),
    bubbleBorder = Color(0xFF2B5475),
    navBg = Color(0xF20D2136),
    track = Color(0xFF18334C),
    shadow = Color(0x00000000),
)

val LocalStusyColors = staticCompositionLocalOf { DayPalette }

fun isDaytime(millis: Long): Boolean {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return hour in 6..17
}

private val StusyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun StusyTheme(isDay: Boolean, content: @Composable () -> Unit) {
    val c = if (isDay) DayPalette else NightPalette
    val scheme = if (isDay) {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.accentDeep,
            secondary = c.warm,
            onSecondary = Color.White,
            background = c.bgTop,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.textSoft,
            outline = c.border,
            error = c.danger,
        )
    } else {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            primaryContainer = c.accentSoft,
            onPrimaryContainer = c.accent,
            secondary = c.warm,
            onSecondary = c.onAccent,
            background = c.bgTop,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.textSoft,
            outline = c.border,
            error = c.danger,
        )
    }
    CompositionLocalProvider(LocalStusyColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = StusyTypography,
            content = content,
        )
    }
}

@Composable
fun StusyBackground(modifier: Modifier = Modifier) {
    val c = LocalStusyColors.current
    val stars = remember {
        val random = Random(20261119)
        List(54) {
            Triple(
                random.nextFloat(),
                random.nextFloat() * 0.72f,
                0.6f + random.nextFloat() * 1.6f,
            )
        }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(c.bgTop, c.bgBottom),
                startY = 0f,
                endY = size.height,
            ),
        )

        if (c.isDay) {
            val sun = Offset(size.width * 0.87f, size.height * 0.055f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.glow.copy(alpha = 0.55f), Color.Transparent),
                    center = sun,
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = sun,
            )
            drawCircle(
                color = Color(0xFFFFF3D2).copy(alpha = 0.9f),
                radius = size.width * 0.085f,
                center = sun,
            )
            // 云朵 / 泡沫
            drawCircle(
                color = Color.White.copy(alpha = 0.52f),
                radius = size.width * 0.16f,
                center = Offset(size.width * 0.06f, size.height * 0.22f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.38f),
                radius = size.width * 0.1f,
                center = Offset(size.width * 0.23f, size.height * 0.17f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = size.width * 0.13f,
                center = Offset(size.width * 0.92f, size.height * 0.36f),
            )
            drawCircle(
                color = c.accent.copy(alpha = 0.06f),
                radius = size.width * 0.3f,
                center = Offset(size.width * 0.05f, size.height * 0.82f),
            )
            drawCircle(
                color = c.warm.copy(alpha = 0.08f),
                radius = size.width * 0.26f,
                center = Offset(size.width * 0.94f, size.height * 0.74f),
            )
        } else {
            val moon = Offset(size.width * 0.84f, size.height * 0.1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.glow.copy(alpha = 0.42f), Color.Transparent),
                    center = moon,
                    radius = size.width * 0.5f,
                ),
                radius = size.width * 0.5f,
                center = moon,
            )
            drawCircle(
                color = Color(0xFFF4EFD8).copy(alpha = 0.92f),
                radius = size.width * 0.075f,
                center = moon,
            )
            drawCircle(
                color = c.bgTop.copy(alpha = 0.92f),
                radius = size.width * 0.063f,
                center = moon + Offset(size.width * 0.032f, -size.width * 0.018f),
            )
            stars.forEach { (x, y, r) ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f + r * 0.22f),
                    radius = r,
                    center = Offset(size.width * x, size.height * y),
                )
            }
            // 远处的海面微光
            drawCircle(
                color = c.accent.copy(alpha = 0.07f),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.15f, size.height * 1.02f),
            )
            drawCircle(
                color = c.accentDeep.copy(alpha = 0.08f),
                radius = size.width * 0.42f,
                center = Offset(size.width * 0.92f, size.height * 0.9f),
            )
        }
    }
}
```

## `app/src/main/java/com/example/stusy/ui/TimeScreen.kt`

```kotlin
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
```

## `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="false"
        android:icon="@drawable/app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Stusy">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".notify.ReminderReceiver"
            android:exported="false" />

        <receiver
            android:name=".notify.ReminderRestoreReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>


        <service
            android:name=".timer.TimerService"
            android:exported="false"
            android:stopWithTask="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="study_timer" />
        </service>
    </application>

</manifest>
```

## `app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">哦鲸鲸喊你学习</string>
</resources>
```
