package com.calmdrive.visualtimer.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.calmdrive.visualtimer.model.NumberMode
import com.calmdrive.visualtimer.model.PaletteTheme
import com.calmdrive.visualtimer.model.VisualStyle
import kotlin.math.cos
import kotlin.math.sin

data class RenderState(
    val totalSec: Int,
    val remainingSec: Int,
    val isPaused: Boolean,
    val visualStyle: VisualStyle,
    val numberMode: NumberMode,
    val paletteTheme: PaletteTheme,
    val isFirstStep: Boolean,
    val currentTitle: String,
    val currentIcon: String,
    val formattedTime: String,
    val thenTitle: String?,
    val thenIcon: String?
)

object TimerArtworkRenderer {

    private const val SIZE = 512
    private val cachedBitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(cachedBitmap)

    fun render(state: RenderState): Bitmap {
        val w = SIZE.toFloat()
        val h = SIZE.toFloat()
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val ratio = if (state.totalSec > 0) {
            (state.remainingSec.toFloat() / state.totalSec.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val (stageColor, stageDot) = when {
            state.remainingSec <= 0 -> Pair(state.paletteTheme.doneColor, "🎉")
            ratio <= 0.18f -> Pair(state.paletteTheme.lowColor, state.paletteTheme.lowDot)
            ratio <= 0.45f -> Pair(state.paletteTheme.midColor, state.paletteTheme.midDot)
            else -> Pair(state.paletteTheme.highColor, state.paletteTheme.highDot)
        }

        // 1. High-Contrast Deep Card Background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.45f, w * 0.75f,
                intArrayOf(0xFF192132.toInt(), 0xFF0C1018.toInt()),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle ambient ring glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stageColor
            alpha = 40
            style = Paint.Style.FILL
        }
        canvas.drawCircle(w * 0.5f, h * 0.44f, w * 0.36f, glowPaint)

        // 2. Render Selected Style
        if (state.remainingSec <= 0) {
            drawCelebration(canvas, w, h, stageColor)
        } else {
            when (state.visualStyle) {
                VisualStyle.DISC -> drawShrinkingDisc(canvas, w, h, state, ratio, stageColor)
                VisualStyle.JOURNEY -> drawCarJourney(canvas, w, h, state, ratio, stageColor)
                VisualStyle.BLOCKS -> drawCalmBlocks(canvas, w, h, state, ratio, stageColor)
            }
        }

        return cachedBitmap
    }

    private fun drawShrinkingDisc(
        c: Canvas,
        w: Float,
        h: Float,
        state: RenderState,
        ratio: Float,
        stageColor: Int
    ) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val outerR = w * 0.43f
        val innerR = w * 0.22f

        // 12 Clock Tick Marks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 12) {
            val angle = (i.toFloat() / 12f) * Math.PI.toFloat() * 2f - Math.PI.toFloat() / 2f
            val isQuarter = i % 3 == 0
            val r1 = outerR + 6f
            val r2 = outerR + (if (isQuarter) 24f else 16f)
            tickPaint.color = if (isQuarter) 0x99FFFFFF.toInt() else 0x44FFFFFF
            tickPaint.strokeWidth = if (isQuarter) 5f else 3f
            c.drawLine(
                cx + cos(angle) * r1, cy + sin(angle) * r1,
                cx + cos(angle) * r2, cy + sin(angle) * r2,
                tickPaint
            )
        }

        // Empty track ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, outerR, trackPaint)

        // Shrinking Wedge Arc (Time Timer)
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stageColor
            style = Paint.Style.FILL
        }
        val arcRect = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        c.drawArc(arcRect, -90f, ratio * 360f, true, discPaint)

        // Center Hub Circle
        val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0F1523.toInt()
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, innerR, hubPaint)

        val hubStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x55FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        c.drawCircle(cx, cy, innerR, hubStrokePaint)

        // Activity Icon in Center Hub
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (state.numberMode == NumberMode.NONE) w * 0.18f else w * 0.14f
            textAlign = Paint.Align.CENTER
        }
        val iconY = if (state.numberMode == NumberMode.NONE) cy + (w * 0.06f) else cy - (w * 0.01f)
        c.drawText(state.currentIcon, cx, iconY, iconPaint)

        // Bold Number Readout below icon
        if (state.numberMode != NumberMode.NONE) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = w * 0.075f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c.drawText(state.formattedTime, cx, cy + (w * 0.11f), textPaint)
        }
    }

    private fun drawCarJourney(
        c: Canvas,
        w: Float,
        h: Float,
        state: RenderState,
        ratio: Float,
        stageColor: Int
    ) {
        val progress = (1f - ratio).coerceIn(0f, 1f)
        val padX = w * 0.13f
        val trackW = w - padX * 2f
        val trackY = h * 0.46f

        // Top Header Text
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.072f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val topLabel = if (state.numberMode == NumberMode.NONE) {
            "${state.currentIcon} ${state.currentTitle}"
        } else {
            "${state.formattedTime} • ${state.currentIcon}"
        }
        c.drawText(topLabel, w * 0.5f, h * 0.20f, headerPaint)

        // Road Base Track
        val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 32f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(padX, trackY, padX + trackW, trackY, roadPaint)

        // Dashed Center Divider
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 14f), 0f)
        }
        c.drawLine(padX + 16f, trackY, padX + trackW - 16f, trackY, dashPaint)

        // Completed Route Fill Line
        val currentX = padX + trackW * progress
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stageColor
            style = Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(padX, trackY, (padX + 4f).coerceAtLeast(currentX), trackY, fillPaint)

        // Checkpoints (Start, Halfway, Goal)
        val checkPoints = listOf(
            Triple(padX, "🏁", "Start"),
            Triple(padX + trackW * 0.5f, if (progress >= 0.5f) "⭐" else "⚪", "Half"),
            Triple(padX + trackW, state.currentIcon, "Goal")
        )
        val cpBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF141A29.toInt()
            style = Paint.Style.FILL
        }
        val cpStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val cpTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            textAlign = Paint.Align.CENTER
        }

        checkPoints.forEach { (x, icon, _) ->
            c.drawCircle(x, trackY - 54f, 25f, cpBubblePaint)
            c.drawCircle(x, trackY - 54f, 25f, cpStrokePaint)
            c.drawText(icon, x, trackY - 46f, cpTextPaint)
        }

        // Moving Car Bubble
        val carBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val carStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stageColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        c.drawCircle(currentX, trackY, 27f, carBubblePaint)
        c.drawCircle(currentX, trackY, 27f, carStrokePaint)

        val carEmojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("🚗", currentX, trackY + 9f, carEmojiPaint)

        // 5 Countdown Dots Below Track
        val totalDots = 5
        val activeDots = kotlin.math.ceil(ratio * totalDots).toInt()
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (i in 0 until totalDots) {
            val dx = w * 0.34f + i * (w * 0.08f)
            val dy = trackY + 66f
            dotPaint.color = if (i < activeDots) stageColor else 0x22FFFFFF
            c.drawCircle(dx, dy, 8f, dotPaint)
        }
    }

    private fun drawCalmBlocks(
        c: Canvas,
        w: Float,
        h: Float,
        state: RenderState,
        ratio: Float,
        stageColor: Int
    ) {
        val totalBlocks = 10
        val litBlocks = kotlin.math.ceil(ratio * totalBlocks).toInt()

        // Top Header
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.072f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerText = if (state.numberMode == NumberMode.NONE) {
            "${state.currentIcon} $litBlocks steps left"
        } else {
            "${state.currentIcon}  ${state.formattedTime}"
        }
        c.drawText(headerText, w * 0.5f, h * 0.20f, headerPaint)

        val cols = 5
        val rows = 2
        val gap = 14f
        val gridW = w * 0.78f
        val gridH = h * 0.38f
        val startX = (w - gridW) / 2f
        val startY = h * 0.28f
        val cellW = (gridW - gap * (cols - 1)) / cols
        val cellH = (gridH - gap * (rows - 1)) / rows

        val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (i in 0 until totalBlocks) {
            val r = i / cols
            val col = i % cols
            val x = startX + col * (cellW + gap)
            val y = startY + r * (cellH + gap)
            val isLit = i < litBlocks

            blockPaint.color = if (isLit) stageColor else 0x18FFFFFF
            c.drawRoundRect(RectF(x, y, x + cellW, y + cellH), 16f, 16f, blockPaint)

            numPaint.color = if (isLit) 0xCC000000.toInt() else 0x33FFFFFF
            c.drawText("${i + 1}", x + cellW / 2f, y + cellH / 2f + 8f, numPaint)
        }
    }

    private fun drawCelebration(c: Canvas, w: Float, h: Float, stageColor: Int) {
        val cx = w * 0.5f
        val cy = h * 0.5f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stageColor
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, w * 0.36f, circlePaint)

        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 110f
            textAlign = Paint.Align.CENTER
        }
        c.drawText("🎉", cx, cy + 10f, iconPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText("All Done!", cx, cy + 85f, textPaint)
    }
}
