package com.reminds.characters.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * タップするとメモ入力が開くマスコット「リマ」。
 * 画像アセット不要のCanvas描画（タップでぷにっと弾む）。
 */
@Composable
fun CharacterMascot(onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        finishedListener = { pressed = false },
        label = "mascot-bounce",
    )

    val body = Color(0xFFFFC94D)
    val bodyShadow = Color(0xFFF0A830)
    val cheek = Color(0xFFFF8A80)

    Canvas(
        modifier = Modifier
            .size(160.dp)
            .padding(8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                pressed = true
                onTap()
            },
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = w * 0.42f

        // 体（まる）
        drawCircle(color = body, radius = radius, center = center)
        drawCircle(
            color = bodyShadow,
            radius = radius,
            center = center,
            style = Stroke(width = w * 0.02f),
        )

        // 目
        val eyeY = center.y - radius * 0.15f
        val eyeDx = radius * 0.35f
        drawCircle(Color(0xFF4E342E), radius = w * 0.035f, center = Offset(center.x - eyeDx, eyeY))
        drawCircle(Color(0xFF4E342E), radius = w * 0.035f, center = Offset(center.x + eyeDx, eyeY))
        // 目のハイライト
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(center.x - eyeDx + w * 0.012f, eyeY - w * 0.012f))
        drawCircle(Color.White, radius = w * 0.012f, center = Offset(center.x + eyeDx + w * 0.012f, eyeY - w * 0.012f))

        // ほっぺ
        val cheekY = center.y + radius * 0.12f
        drawCircle(cheek.copy(alpha = 0.6f), radius = w * 0.045f, center = Offset(center.x - eyeDx - w * 0.02f, cheekY))
        drawCircle(cheek.copy(alpha = 0.6f), radius = w * 0.045f, center = Offset(center.x + eyeDx + w * 0.02f, cheekY))

        // くち（にっこり）
        drawArc(
            color = Color(0xFF4E342E),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - w * 0.06f, center.y - w * 0.02f),
            size = androidx.compose.ui.geometry.Size(w * 0.12f, w * 0.10f),
            style = Stroke(width = w * 0.016f),
        )

        // あたまの葉っぱ
        val leafBase = Offset(center.x, center.y - radius)
        drawArc(
            color = Color(0xFF7CB342),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = true,
            topLeft = Offset(leafBase.x - w * 0.10f, leafBase.y - w * 0.14f),
            size = androidx.compose.ui.geometry.Size(w * 0.20f, w * 0.20f),
        )
    }
}
