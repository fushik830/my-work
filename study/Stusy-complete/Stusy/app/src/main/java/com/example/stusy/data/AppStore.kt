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
