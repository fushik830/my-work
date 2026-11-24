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
