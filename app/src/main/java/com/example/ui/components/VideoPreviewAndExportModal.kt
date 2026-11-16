package com.example.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.domain.model.AspectRatio
import com.example.domain.model.RenderProgressState
import com.example.domain.model.SceneMediaType
import com.example.domain.model.StudioProject
import com.example.domain.model.VerticalPosition
import com.example.engine.audio.ArabicTtsEngine
import com.example.engine.video.ProceduralSceneRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun InteractiveStoryboardPreviewPlayer(
    project: StudioProject,
    activeSceneIndex: Int,
    onActiveSceneChanged: (Int) -> Unit,
    ttsEngine: ArabicTtsEngine
) {
    val context = LocalContext.current
    val scenes = project.scenes
    if (scenes.isEmpty()) return

    val safeIndex = activeSceneIndex.coerceIn(0, scenes.lastIndex)
    val currentScene = scenes[safeIndex]
    val renderer = remember(context) { ProceduralSceneRenderer(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var sceneProgress by remember { mutableFloatStateOf(0f) }
    var teleprompterManualPlaying by remember { mutableStateOf(true) }

    // Load active scene bitmaps safely off the main thread
    var activeBitmaps by remember { mutableStateOf<List<Bitmap?>>(emptyList()) }
    var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(project.videoConfig.logoUri) {
        logoBitmap = withContext(Dispatchers.IO) {
            project.videoConfig.logoUri?.let { uriStr ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
        }
    }

    LaunchedEffect(currentScene.id, currentScene.assets) {
        activeBitmaps = withContext(Dispatchers.IO) {
            currentScene.assets.map { asset ->
                when (asset.type) {
                    SceneMediaType.USER_IMAGE -> asset.uriString?.let { uriStr ->
                        runCatching {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use {
                                BitmapFactory.decodeStream(it, null, opts)
                            }
                        }.getOrNull()
                    }
                    SceneMediaType.USER_VIDEO -> asset.uriString?.let { uriStr ->
                        runCatching {
                            val ret = MediaMetadataRetriever()
                            ret.setDataSource(context, Uri.parse(uriStr))
                            val bmp = ret.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ret.release()
                            bmp
                        }.getOrNull()
                    }
                    SceneMediaType.PROCEDURAL -> null
                }
            }
        }
    }

    // Play TTS audio for active scene when playing
    LaunchedEffect(isPlaying, safeIndex) {
        if (isPlaying) {
            ttsEngine.previewVoice(currentScene.narrationText, project.voiceConfig)
        } else {
            ttsEngine.stopPreview()
        }
    }

    DisposableEffect(Unit) {
        onDispose { ttsEngine.stopPreview() }
    }

    // 25 FPS animation loop when playing
    LaunchedEffect(isPlaying, safeIndex, currentScene.effectiveDurationSec) {
        if (!isPlaying) return@LaunchedEffect
        val stepMs = 40L
        val totalMs = (currentScene.effectiveDurationSec * 1000f).coerceAtLeast(1500f)
        var elapsed = sceneProgress * totalMs
        while (isPlaying) {
            delay(stepMs)
            elapsed += stepMs
            if (elapsed >= totalMs) {
                if (safeIndex < scenes.lastIndex) {
                    sceneProgress = 0f
                    onActiveSceneChanged(safeIndex + 1)
                    break
                } else {
                    sceneProgress = 0f
                    isPlaying = false
                    break
                }
            } else {
                sceneProgress = (elapsed / totalMs).coerceIn(0f, 1f)
            }
        }
    }

    val previewRatio = when (project.videoConfig.aspectRatio) {
        AspectRatio.RATIO_16_9 -> 16f / 9f
        AspectRatio.RATIO_9_16 -> 9f / 16f
        AspectRatio.RATIO_1_1 -> 1f
        AspectRatio.RATIO_4_5 -> 4f / 5f
    }

    val allSentences = remember(scenes) { scenes.map { it.originalSegment } }
    val globalProgress = (safeIndex + sceneProgress) / scenes.size.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF05070C), RoundedCornerShape(14.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (previewRatio < 1f) 0.56f else 1f)
                        .aspectRatio(previewRatio)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width.toInt().coerceAtLeast(1)
                        val h = size.height.toInt().coerceAtLeast(1)
                        renderer.renderFrame(
                            canvas = drawContext.canvas.nativeCanvas,
                            width = w,
                            height = h,
                            scene = currentScene,
                            sceneProgress = sceneProgress,
                            globalProgress = globalProgress,
                            assetBitmaps = activeBitmaps,
                            logoBitmap = logoBitmap,
                            videoConfig = project.videoConfig,
                            teleprompterConfig = project.teleprompterConfig,
                            allScriptSentences = allSentences,
                            activeSentenceIndex = safeIndex,
                            isExportRender = false
                        )
                    }

                    // Live Teleprompter Overlay in Preview Player when enabled
                    if (project.teleprompterConfig.enabled) {
                        val align = when (project.teleprompterConfig.position) {
                            VerticalPosition.TOP -> Alignment.TopCenter
                            VerticalPosition.CENTER -> Alignment.Center
                            VerticalPosition.BOTTOM -> Alignment.BottomCenter
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = align
                        ) {
                            LiveTeleprompterOverlay(
                                config = project.teleprompterConfig,
                                sentences = allSentences,
                                activeSentenceIndex = safeIndex,
                                isPlaying = isPlaying && teleprompterManualPlaying,
                                onTogglePlay = { teleprompterManualPlaying = !teleprompterManualPlaying },
                                onSelectSentence = { idx ->
                                    sceneProgress = 0f
                                    onActiveSceneChanged(idx)
                                },
                                modifier = Modifier.fillMaxWidth(
                                    project.teleprompterConfig.widthFraction.coerceIn(0.5f, 0.96f)
                                )
                            )
                        }
                    }
                }
            }

            // Scrub & Transport Controls
            Slider(
                value = sceneProgress,
                onValueChange = { sceneProgress = it },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المشهد ${safeIndex + 1} من ${scenes.size} • ${String.format("%.1f", currentScene.effectiveDurationSec)} ث",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = {
                            if (safeIndex > 0) {
                                sceneProgress = 0f
                                onActiveSceneChanged(safeIndex - 1)
                            }
                        },
                        enabled = safeIndex > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "المشهد السابق")
                    }

                    Button(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.testTag("preview_play_pause_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "إيقاف" else "تشغيل"
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isPlaying) "إيقاف" else "تشغيل المعاينة")
                    }

                    IconButton(
                        onClick = {
                            if (safeIndex < scenes.lastIndex) {
                                sceneProgress = 0f
                                onActiveSceneChanged(safeIndex + 1)
                            }
                        },
                        enabled = safeIndex < scenes.lastIndex
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "المشهد التالي")
                    }
                }
            }
        }
    }
}

@Composable
fun ExportProgressAndPlayerDialog(
    renderState: RenderProgressState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onSaveToGallery: (String) -> Unit,
    onRetry: () -> Unit
) {
    if (renderState is RenderProgressState.Idle) return
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (renderState is RenderProgressState.Completed ||
                renderState is RenderProgressState.Error ||
                renderState is RenderProgressState.Cancelled
            ) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (renderState) {
                    is RenderProgressState.PreparingAudio -> {
                        Text("تجهيز المسار الصوتي...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(renderState.message, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = {
                                (renderState.currentScene.toFloat() / renderState.totalScenes.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
                        )
                        OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                            Text("إلغاء")
                        }
                    }

                    is RenderProgressState.RenderingVideo -> {
                        Text("جارٍ إنتاج الفيديو MP4...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(renderState.message, style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { renderState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "التقدم: ${(renderState.progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = renderState.etaSeconds?.let { "الوقت المتبقي التقريبي: $it ثانية" } ?: "جارٍ الحساب...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                            Text("إلغاء التصدير")
                        }
                    }

                    is RenderProgressState.Completed -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Text("اكتمل تصدير الفيديو!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق")
                            }
                        }

                        // Real Native MP4 Video Player
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        val controller = MediaController(ctx)
                                        controller.setAnchorView(this)
                                        setMediaController(controller)
                                        setVideoPath(renderState.outputPath)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        val sizeMb = renderState.fileSizeBytes / (1024f * 1024f)
                        Text(
                            text = "المدة: ${String.format("%.1f", renderState.durationSec)} ثانية  •  الحجم: ${String.format("%.2f", sizeMb)} MB  •  صيغة MP4 (H.264/AAC)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        renderState.warnings.forEach { warning ->
                            Text(
                                text = "ملاحظة: $warning",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSaveToGallery(renderState.outputPath) },
                                modifier = Modifier.weight(1f).testTag("save_gallery_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("حفظ بالمعرض")
                            }
                            OutlinedButton(
                                onClick = {
                                    val file = File(renderState.outputPath)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة الفيديو"))
                                },
                                modifier = Modifier.weight(1f).testTag("share_mp4_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة MP4")
                            }
                        }
                    }

                    is RenderProgressState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("تعذر إتمام التصدير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(renderState.message, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                            OutlinedButton(onClick = onDismiss) { Text("إغلاق") }
                            Button(onClick = onRetry) { Text("إعادة المحاولة") }
                        }
                    }

                    RenderProgressState.Cancelled -> {
                        Text("تم إلغاء التصدير", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("تم تحرير الملفات المؤقتة بنجاح. يمكنك التعديل وإعادة التصدير في أي وقت.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("حسناً") }
                    }

                    RenderProgressState.Idle -> Unit
                }
            }
        }
    }
}
