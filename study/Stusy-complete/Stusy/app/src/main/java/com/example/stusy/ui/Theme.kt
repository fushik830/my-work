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
