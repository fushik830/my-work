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
