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
