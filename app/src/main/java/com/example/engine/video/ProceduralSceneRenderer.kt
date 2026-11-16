package com.example.engine.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.FontChoice
import com.example.domain.model.KenBurnsMode
import com.example.domain.model.StoryboardScene
import com.example.domain.model.SubtitleBackgroundStyle
import com.example.domain.model.SubtitleLanguage
import com.example.domain.model.TeleprompterConfig
import com.example.domain.model.TeleprompterExportMode
import com.example.domain.model.TeleprompterTextAlign
import com.example.domain.model.TransitionType
import com.example.domain.model.VerticalPosition
import com.example.domain.model.VideoConfig
import com.example.domain.model.VisualStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ProceduralSceneRenderer(private val context: Context) {

    private val cairoTypeface: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.cairo) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }
    private val ibmPlexTypeface: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_sans_arabic) }.getOrNull() ?: Typeface.DEFAULT
    }
    private val amiriTypeface: Typeface by lazy {
        runCatching { ResourcesCompat.getFont(context, R.font.amiri) }.getOrNull() ?: Typeface.SERIF
    }

    fun getTypeface(choice: FontChoice): Typeface = when (choice) {
        FontChoice.CAIRO -> cairoTypeface
        FontChoice.IBM_PLEX -> ibmPlexTypeface
        FontChoice.AMIRI -> amiriTypeface
    }

    fun renderFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: StoryboardScene,
        sceneProgress: Float, // 0f..1f inside this scene
        globalProgress: Float, // 0f..1f across whole video
        assetBitmaps: List<Bitmap?>,
        logoBitmap: Bitmap?,
        videoConfig: VideoConfig,
        teleprompterConfig: TeleprompterConfig,
        allScriptSentences: List<String>,
        activeSentenceIndex: Int,
        isExportRender: Boolean
    ) {
        val style = scene.visualStyle
        val clampedProgress = sceneProgress.coerceIn(0f, 1f)

        // Base dark canvas
        canvas.drawColor(Color.BLACK)
        canvas.save()

        // Apply scene transition transform on first ~0.6s (approx 12% of scene)
        applyTransitionPreTransform(canvas, width, height, scene.transition, clampedProgress)

        // 1. Background / Media Layer
        val validBitmaps = assetBitmaps.filterNotNull()
        if (validBitmaps.isNotEmpty()) {
            drawMultiAssetMediaLayer(
                canvas = canvas,
                width = width,
                height = height,
                bitmaps = validBitmaps,
                progress = clampedProgress,
                kenBurnsMode = scene.kenBurnsMode,
                style = style
            )
        } else {
            drawProceduralStyleBackground(
                canvas = canvas,
                width = width,
                height = height,
                scene = scene,
                progress = clampedProgress
            )
        }

        // 2. Style-specific atmospheric overlays (letterbox bars, news ticker, luxury border, etc.)
        drawStyleOverlays(canvas, width, height, scene, clampedProgress, globalProgress)

        // 3. Optional User Logo Watermark
        if (logoBitmap != null && !logoBitmap.isRecycled) {
            drawLogoWatermark(canvas, width, height, logoBitmap)
        }

        // 4. Subtitles Layer (respects Arabic BiDi shaping via StaticLayout)
        if (videoConfig.subtitleLanguage != SubtitleLanguage.NONE && scene.subtitleText.isNotBlank()) {
            drawSubtitle(canvas, width, height, scene.subtitleText, videoConfig)
        }

        // 5. Optional Teleprompter Layer during export if burn-in is selected
        if (isExportRender &&
            teleprompterConfig.enabled &&
            teleprompterConfig.exportMode == TeleprompterExportMode.BURN_IN_VIDEO
        ) {
            drawTeleprompterBurnIn(
                canvas = canvas,
                width = width,
                height = height,
                config = teleprompterConfig,
                sentences = allScriptSentences,
                activeIndex = activeSentenceIndex,
                globalProgress = globalProgress
            )
        }

        canvas.restore()

        // Post-transition fade overlay
        if (scene.transition == TransitionType.FADE && clampedProgress < 0.14f) {
            val alpha = ((1f - (clampedProgress / 0.14f)) * 255).toInt().coerceIn(0, 255)
            canvas.drawColor(Color.argb(alpha, 0, 0, 0))
        }
    }

    private fun applyTransitionPreTransform(
        canvas: Canvas,
        width: Int,
        height: Int,
        transition: TransitionType,
        progress: Float
    ) {
        val tWindow = 0.16f
        if (progress >= tWindow) return
        val t = (progress / tWindow).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) // EaseOutQuad

        when (transition) {
            TransitionType.SLIDE_RTL -> {
                val dx = -(1f - eased) * width * 0.35f
                canvas.translate(dx, 0f)
            }
            TransitionType.ZOOM_IN -> {
                val scale = 0.90f + 0.10f * eased
                canvas.scale(scale, scale, width / 2f, height / 2f)
            }
            TransitionType.WIPE -> {
                canvas.clipRect(0f, 0f, width * eased, height.toFloat())
            }
            TransitionType.DISSOLVE, TransitionType.FADE, TransitionType.NONE -> Unit
        }
    }

    private fun drawMultiAssetMediaLayer(
        canvas: Canvas,
        width: Int,
        height: Int,
        bitmaps: List<Bitmap>,
        progress: Float,
        kenBurnsMode: KenBurnsMode,
        style: VisualStyle
    ) {
        val count = bitmaps.size
        val scaledIndex = (progress * count).coerceAtMost(count - 0.0001f)
        val idx = scaledIndex.toInt().coerceIn(0, count - 1)
        val localProgress = scaledIndex - idx

        val currentBmp = bitmaps[idx]
        drawBitmapWithKenBurns(canvas, width, height, currentBmp, progress, kenBurnsMode, 255)

        // Cross-fade into next asset within the same scene if multiple assets exist
        if (idx < count - 1 && localProgress > 0.78f) {
            val fadeAlpha = (((localProgress - 0.78f) / 0.22f) * 255).toInt().coerceIn(0, 255)
            drawBitmapWithKenBurns(canvas, width, height, bitmaps[idx + 1], progress, kenBurnsMode, fadeAlpha)
        }

        // Subtle style color grading scrim so subtitles stay crisp
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(
                    Color.argb(65, 0, 0, 0),
                    Color.argb(25, 0, 0, 0),
                    Color.argb(155, Color.red(style.primaryHex.toInt()), Color.green(style.primaryHex.toInt()), Color.blue(style.primaryHex.toInt()))
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    private fun drawBitmapWithKenBurns(
        canvas: Canvas,
        width: Int,
        height: Int,
        bitmap: Bitmap,
        progress: Float,
        mode: KenBurnsMode,
        alpha: Int
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

        val baseScale = max(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val zoom = when (mode) {
            KenBurnsMode.ZOOM_IN -> 1.0f + 0.14f * progress
            KenBurnsMode.ZOOM_OUT -> 1.14f - 0.14f * progress
            KenBurnsMode.PAN_RIGHT, KenBurnsMode.PAN_LEFT -> 1.12f
            KenBurnsMode.STATIC -> 1.0f
        }
        val finalScale = baseScale * zoom
        val drawnW = bitmap.width * finalScale
        val drawnH = bitmap.height * finalScale

        val extraX = (drawnW - width) / 2f
        val extraY = (drawnH - height) / 2f
        val offsetX = when (mode) {
            KenBurnsMode.PAN_RIGHT -> -extraX + (extraX * 2f * progress)
            KenBurnsMode.PAN_LEFT -> extraX - (extraX * 2f * progress)
            else -> 0f
        }

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate((width - drawnW) / 2f + offsetX, (height - drawnH) / 2f)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    private fun drawProceduralStyleBackground(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: StoryboardScene,
        progress: Float
    ) {
        val style = scene.visualStyle
        val c1 = style.primaryHex.toInt()
        val c2 = style.secondaryHex.toInt()
        val accent = style.accentHex.toInt()

        // Animated gradient backdrop
        val shift = sin(progress * PI).toFloat() * width * 0.25f
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                shift, 0f,
                width - shift, height.toFloat(),
                c1, c2, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Soft glowing radial aura
        val glowX = width * (0.25f + 0.5f * progress)
        val glowY = height * (0.35f + 0.15f * cos(progress * PI * 2).toFloat())
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                glowX, glowY,
                max(width, height) * 0.65f,
                intArrayOf(
                    Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent))
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

        // Animated geometric waves / particles depending on style
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent))
            this.style = Paint.Style.STROKE
            strokeWidth = max(2f, width * 0.003f)
        }

        when (style) {
            VisualStyle.CARTOON -> {
                val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
                for (i in 0..6) {
                    val bx = width * ((i * 0.17f + progress * 0.15f) % 1.0f)
                    val by = height * ((0.2f + i * 0.12f - progress * 0.1f + 1f) % 1.0f)
                    canvas.drawCircle(bx, by, width * (0.04f + (i % 3) * 0.025f), bubblePaint)
                }
            }
            VisualStyle.DOCUMENTARY, VisualStyle.EDUCATIONAL -> {
                // Subtle architectural studio grid
                val gridPaint = Paint().apply {
                    color = Color.argb(26, 255, 255, 255)
                    strokeWidth = 1.5f
                }
                val step = max(48f, width / 14f)
                var x = (progress * step) % step
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                    x += step
                }
                var y = 0f
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                    y += step
                }
            }
            else -> {
                // Flowing soundwave ribbon across background
                for (layer in 0..2) {
                    val path = Path()
                    val baseY = height * (0.42f + layer * 0.08f)
                    val amp = height * (0.04f + layer * 0.018f)
                    val phase = progress * PI.toFloat() * 2f + layer * 1.1f
                    var x = 0f
                    path.moveTo(0f, baseY)
                    while (x <= width) {
                        val y = baseY + sin((x / width) * PI.toFloat() * 3f + phase) * amp
                        path.lineTo(x, y)
                        x += 18f
                    }
                    canvas.drawPath(path, wavePaint)
                }
            }
        }

        // Draw focal keyword / topic badge in upper-center canvas for procedural richness
        if (scene.keywords.isNotEmpty() && style != VisualStyle.MINIMALIST) {
            drawProceduralSceneTopicCard(canvas, width, height, scene, progress)
        }
    }

    private fun drawProceduralSceneTopicCard(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: StoryboardScene,
        progress: Float
    ) {
        val accent = scene.visualStyle.accentHex.toInt()
        val keywordsText = scene.keywords.take(3).joinToString("  •  ")
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairoTypeface
            textSize = (min(width, height) * 0.034f).coerceIn(18f, 44f)
            color = Color.WHITE
            alpha = ((sin(min(1f, progress * 3f) * PI / 2f)) * 230).toInt().coerceIn(0, 255)
        }
        val textWidth = textPaint.measureText(keywordsText)
        val padH = width * 0.03f
        val padV = height * 0.015f
        val centerX = width / 2f
        val centerY = height * 0.24f
        val box = RectF(
            centerX - textWidth / 2f - padH,
            centerY - textPaint.textSize / 2f - padV,
            centerX + textWidth / 2f + padH,
            centerY + textPaint.textSize / 2f + padV
        )
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 10, 14, 24)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, Color.red(accent), Color.green(accent), Color.blue(accent))
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        val radius = height * 0.025f
        canvas.drawRoundRect(box, radius, radius, boxPaint)
        canvas.drawRoundRect(box, radius, radius, borderPaint)

        val fm = textPaint.fontMetrics
        val textY = centerY - (fm.ascent + fm.descent) / 2f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(keywordsText, centerX, textY, textPaint)
    }

    private fun drawStyleOverlays(
        canvas: Canvas,
        width: Int,
        height: Int,
        scene: StoryboardScene,
        sceneProgress: Float,
        globalProgress: Float
    ) {
        val style = scene.visualStyle
        val accent = style.accentHex.toInt()

        when (style) {
            VisualStyle.CINEMATIC -> {
                // Classic 2.39:1 cinema letterbox bars on 16:9 or subtle vignette
                val barH = height * 0.075f
                val barPaint = Paint().apply { color = Color.argb(230, 5, 7, 12) }
                canvas.drawRect(0f, 0f, width.toFloat(), barH, barPaint)
                canvas.drawRect(0f, height - barH, width.toFloat(), height.toFloat(), barPaint)
            }
            VisualStyle.LUXURY, VisualStyle.CLASSIC -> {
                val inset = min(width, height) * 0.028f
                val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))
                    this.style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                canvas.drawRect(inset, inset, width - inset, height - inset, framePaint)
            }
            VisualStyle.NEWS -> {
                // Top-right LIVE / News broadcast tag & bottom progress ticker line
                val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(220, 38, 38)
                }
                val tagW = width * 0.16f
                val tagH = height * 0.055f
                val margin = width * 0.04f
                val tagRect = RectF(width - margin - tagW, margin, width - margin, margin + tagH)
                canvas.drawRoundRect(tagRect, 8f, 8f, tagPaint)

                val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    typeface = cairoTypeface
                    textSize = tagH * 0.55f
                    textAlign = Paint.Align.CENTER
                }
                val fm = labelPaint.fontMetrics
                canvas.drawText(
                    "تقرير خاص",
                    tagRect.centerX(),
                    tagRect.centerY() - (fm.ascent + fm.descent) / 2f,
                    labelPaint
                )
            }
            VisualStyle.SOCIAL_MEDIA -> {
                // Sleek top progress bar
                val trackPaint = Paint().apply { color = Color.argb(70, 255, 255, 255) }
                val fillPaint = Paint().apply { color = accent }
                val barH = max(5f, height * 0.008f)
                canvas.drawRect(0f, 0f, width.toFloat(), barH, trackPaint)
                canvas.drawRect(0f, 0f, width * globalProgress.coerceIn(0f, 1f), barH, fillPaint)
            }
            else -> {
                // Subtle scene progress indicator dot/bar at very top
                val fillPaint = Paint().apply {
                    color = Color.argb(140, Color.red(accent), Color.green(accent), Color.blue(accent))
                }
                canvas.drawRect(0f, 0f, width * sceneProgress, max(3f, height * 0.004f), fillPaint)
            }
        }
    }

    private fun drawLogoWatermark(canvas: Canvas, width: Int, height: Int, logo: Bitmap) {
        val maxLogoSize = min(width, height) * 0.11f
        val scale = min(maxLogoSize / logo.width, maxLogoSize / logo.height)
        val dstW = logo.width * scale
        val dstH = logo.height * scale
        val margin = min(width, height) * 0.04f
        val dst = RectF(margin, margin, margin + dstW, margin + dstH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            alpha = 220
        }
        canvas.drawBitmap(logo, null, dst, paint)
    }

    private fun drawSubtitle(
        canvas: Canvas,
        width: Int,
        height: Int,
        text: String,
        config: VideoConfig
    ) {
        val scaleFactor = min(width, height) / 720f
        val fontSizePx = (config.subtitleSizeSp * 1.45f * scaleFactor).coerceIn(18f, 72f)
        val maxTextWidth = (width * 0.84f).toInt().coerceAtLeast(100)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getTypeface(config.subtitleFont)
            textSize = fontSizePx
            color = config.subtitleColorHex.toInt()
            if (config.subtitleBgStyle == SubtitleBackgroundStyle.SHADOW_ONLY ||
                config.subtitleBgStyle == SubtitleBackgroundStyle.NONE
            ) {
                setShadowLayer(6f * scaleFactor, 0f, 2f * scaleFactor, Color.argb(230, 0, 0, 0))
            }
        }

        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(4f * scaleFactor, 1.15f)
            .setIncludePad(true)
            .build()

        val marginEdge = height * 0.09f
        val topY = when (config.subtitlePosition) {
            VerticalPosition.TOP -> marginEdge
            VerticalPosition.CENTER -> (height - layout.height) / 2f
            VerticalPosition.BOTTOM -> height - marginEdge - layout.height
        }
        val leftX = (width - maxTextWidth) / 2f

        val padH = 24f * scaleFactor
        val padV = 14f * scaleFactor

        when (config.subtitleBgStyle) {
            SubtitleBackgroundStyle.PILL_DARK -> {
                val bgRect = RectF(
                    leftX - padH,
                    topY - padV,
                    leftX + maxTextWidth + padH,
                    topY + layout.height + padV
                )
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(190, 10, 14, 24)
                }
                canvas.drawRoundRect(bgRect, 18f * scaleFactor, 18f * scaleFactor, bgPaint)
            }
            SubtitleBackgroundStyle.FULL_BAR -> {
                val bgRect = RectF(0f, topY - padV, width.toFloat(), topY + layout.height + padV)
                val bgPaint = Paint().apply { color = Color.argb(185, 8, 11, 18) }
                canvas.drawRect(bgRect, bgPaint)
            }
            SubtitleBackgroundStyle.SHADOW_ONLY, SubtitleBackgroundStyle.NONE -> Unit
        }

        canvas.save()
        canvas.translate(leftX, topY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawTeleprompterBurnIn(
        canvas: Canvas,
        width: Int,
        height: Int,
        config: TeleprompterConfig,
        sentences: List<String>,
        activeIndex: Int,
        globalProgress: Float
    ) {
        if (sentences.isEmpty()) return
        val scaleFactor = min(width, height) / 720f
        val boxWidth = (width * config.widthFraction.coerceIn(0.45f, 0.96f)).toInt()
        val boxHeight = (height * 0.28f).toInt()
        val leftX = (width - boxWidth) / 2f
        val topY = when (config.position) {
            VerticalPosition.TOP -> height * 0.06f
            VerticalPosition.CENTER -> (height - boxHeight) / 2f
            VerticalPosition.BOTTOM -> height * 0.66f
        }

        val boxRect = RectF(leftX, topY, leftX + boxWidth, topY + boxHeight)

        if (!config.transparentBackground) {
            val bgInt = config.backgroundColorHex.toInt()
            val alphaInt = (config.backgroundOpacity.coerceIn(0.05f, 1f) * 255).toInt()
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alphaInt, Color.red(bgInt), Color.green(bgInt), Color.blue(bgInt))
            }
            canvas.drawRoundRect(boxRect, 20f * scaleFactor, 20f * scaleFactor, bgPaint)
        }

        val textAlign = when (config.textAlign) {
            TeleprompterTextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            TeleprompterTextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TeleprompterTextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
        }

        val currentLine = sentences.getOrElse(activeIndex.coerceIn(0, sentences.lastIndex)) { "" }
        val nextLine = sentences.getOrNull(activeIndex + 1) ?: ""
        val displayStr = if (nextLine.isNotBlank()) "$currentLine\n$nextLine" else currentLine

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = getTypeface(config.fontStyle)
            textSize = (config.textSizeSp * 1.25f * scaleFactor).coerceIn(16f, 56f)
            color = config.textColorHex.toInt()
            setShadowLayer(4f, 0f, 1f, Color.argb(180, 0, 0, 0))
        }

        val innerPad = (20f * scaleFactor).toInt()
        val layout = StaticLayout.Builder.obtain(
            displayStr, 0, displayStr.length, textPaint, (boxWidth - innerPad * 2).coerceAtLeast(80)
        )
            .setAlignment(textAlign)
            .setLineSpacing(6f * scaleFactor, 1.2f)
            .build()

        canvas.save()
        canvas.clipRect(boxRect)
        val scrollOffset = if (config.autoScroll) -(globalProgress * 12f * config.scrollSpeed) else 0f
        canvas.translate(leftX + innerPad, topY + innerPad + scrollOffset)
        layout.draw(canvas)
        canvas.restore()
    }
}
