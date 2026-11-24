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
