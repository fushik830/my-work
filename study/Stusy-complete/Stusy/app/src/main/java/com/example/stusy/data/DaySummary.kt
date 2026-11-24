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
