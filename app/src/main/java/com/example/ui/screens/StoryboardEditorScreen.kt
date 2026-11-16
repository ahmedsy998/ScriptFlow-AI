package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.KenBurnsMode
import com.example.domain.model.SceneAsset
import com.example.domain.model.SceneMediaType
import com.example.domain.model.StoryboardScene
import com.example.domain.model.StudioProject
import com.example.domain.model.TransitionType
import com.example.engine.audio.ArabicTtsEngine
import com.example.ui.components.InteractiveStoryboardPreviewPlayer
import java.util.UUID

@Composable
fun StoryboardEditorScreen(
    project: StudioProject,
    canUndo: Boolean,
    canRedo: Boolean,
    ttsEngine: ArabicTtsEngine,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onMoveScene: (Int, Int) -> Unit,
    onDuplicateScene: (String) -> Unit,
    onDeleteScene: (String) -> Unit,
    onRegenerateScene: (String) -> Unit,
    onUpdateScene: (String, (StoryboardScene) -> StoryboardScene) -> Unit,
    onAddAsset: (String, SceneAsset) -> Unit,
    onRemoveAsset: (String, String) -> Unit,
    onPreviewSceneAudio: (StoryboardScene) -> Unit,
    onExportMp4: () -> Unit
) {
    BackHandler { onBack() }

    val scenes = project.scenes
    var activeSceneIndex by remember(scenes.size) { mutableIntStateOf(0) }
    var mediaTargetSceneId by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val sceneId = mediaTargetSceneId
        if (uri != null && sceneId != null) {
            onAddAsset(
                sceneId,
                SceneAsset(
                    id = UUID.randomUUID().toString(),
                    type = SceneMediaType.USER_IMAGE,
                    uriString = uri.toString(),
                    label = "صورة مرفوعة"
                )
            )
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val sceneId = mediaTargetSceneId
        if (uri != null && sceneId != null) {
            onAddAsset(
                sceneId,
                SceneAsset(
                    id = UUID.randomUUID().toString(),
                    type = SceneMediaType.USER_VIDEO,
                    uriString = uri.toString(),
                    label = "مقطع فيديو مرفوع"
                )
            )
        }
    }

    val sceneAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val sceneId = mediaTargetSceneId
        if (uri != null && sceneId != null) {
            onUpdateScene(sceneId) { it.copy(customNarrationUri = uri.toString()) }
        }
    }

    val totalDurationSec = scenes.sumOf { it.effectiveDurationSec.toDouble() }.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Action Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع للسكربت")
                        }
                        Column {
                            Text("محرر الفيديو والمشاهد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${scenes.size} مشاهد • المدة الكلية ${formatDurationAr(totalDurationSec)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onUndo, enabled = canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
                        }
                        IconButton(onClick = onRedo, enabled = canRedo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "إعادة")
                        }
                        Button(
                            onClick = onExportMp4,
                            modifier = Modifier.testTag("export_mp4_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تصدير MP4")
                        }
                    }
                }

                // Live Interactive Preview Player
                InteractiveStoryboardPreviewPlayer(
                    project = project,
                    activeSceneIndex = activeSceneIndex,
                    onActiveSceneChanged = { activeSceneIndex = it },
                    ttsEngine = ttsEngine
                )

                // Scene Timeline Strip with Thumbnails & Waveforms
                Text("الخط الزمني للمشاهد والمسار الصوتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(scenes, key = { _, s -> s.id }) { index, scene ->
                        val isSelected = index == activeSceneIndex
                        Card(
                            modifier = Modifier
                                .width(156.dp)
                                .clickable { activeSceneIndex = index }
                                .testTag("timeline_scene_${index + 1}"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(scene.visualStyle.primaryHex),
                                                    Color(scene.visualStyle.secondaryHex)
                                                )
                                            )
                                        )
                                ) {
                                    val firstMedia = scene.assets.firstOrNull { it.uriString != null }
                                    if (firstMedia != null) {
                                        AsyncImage(
                                            model = firstMedia.uriString,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Text(
                                        text = "#${scene.sceneNumber} • ${String.format("%.1f", scene.effectiveDurationSec)}ث",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .background(Color.Black.copy(alpha = 0.65f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Audio Waveform Strip
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    scene.waveformPeaks.take(20).forEach { amp ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height((amp * 16f).coerceAtLeast(3f).dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                                    RoundedCornerShape(2.dp)
                                                )
                                        )
                                    }
                                }

                                Text(
                                    text = scene.subtitleText,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Editable Scene Cards List
        itemsIndexed(scenes, key = { _, scene -> scene.id }) { index, scene ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
            ) {
                SceneEditorCard(
                    scene = scene,
                    index = index,
                    totalCount = scenes.size,
                    isActive = index == activeSceneIndex,
                    onSelect = { activeSceneIndex = index },
                    onMoveUp = { onMoveScene(index, index - 1) },
                    onMoveDown = { onMoveScene(index, index + 1) },
                    onDuplicate = { onDuplicateScene(scene.id) },
                    onDelete = { onDeleteScene(scene.id) },
                    onRegenerate = { onRegenerateScene(scene.id) },
                    onPreviewAudio = { onPreviewSceneAudio(scene) },
                    onAddImage = {
                        mediaTargetSceneId = scene.id
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onAddVideo = {
                        mediaTargetSceneId = scene.id
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onPickCustomAudio = {
                        mediaTargetSceneId = scene.id
                        sceneAudioPicker.launch(arrayOf("audio/*"))
                    },
                    onRemoveAsset = { assetId -> onRemoveAsset(scene.id, assetId) },
                    onUpdateScene = { updater -> onUpdateScene(scene.id, updater) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SceneEditorCard(
    scene: StoryboardScene,
    index: Int,
    totalCount: Int,
    isActive: Boolean,
    onSelect: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onPreviewAudio: () -> Unit,
    onAddImage: () -> Unit,
    onAddVideo: () -> Unit,
    onPickCustomAudio: () -> Unit,
    onRemoveAsset: (String) -> Unit,
    onUpdateScene: ((StoryboardScene) -> StoryboardScene) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "المشهد ${scene.sceneNumber} • ${scene.emotionalTone.titleAr}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scene.visualDescription,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "تحريك لأعلى")
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "تحريك لأسفل")
                    }
                    IconButton(onClick = onRegenerate) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "إعادة توليد المشهد")
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "تكرار المشهد")
                    }
                    IconButton(onClick = onDelete, enabled = totalCount > 1) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف المشهد")
                    }
                }
            }

            // Edit Narration & Subtitle text
            OutlinedTextField(
                value = scene.narrationText,
                onValueChange = { txt -> onUpdateScene { it.copy(narrationText = txt) } },
                label = { Text("نص التعليق الصوتي") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = scene.subtitleText,
                onValueChange = { txt -> onUpdateScene { it.copy(subtitleText = txt) } },
                label = { Text("نص الترجمة على الشاشة") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            // Scene Assets (Multi-asset support: images, video clips, procedural)
            Text("الوسائط البصرية للمشهد (${scene.assets.size}):", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                scene.assets.forEach { asset ->
                    AssistChip(
                        onClick = {
                            if (asset.type != SceneMediaType.PROCEDURAL) onRemoveAsset(asset.id)
                        },
                        label = { Text(asset.label) },
                        trailingIcon = if (asset.type != SceneMediaType.PROCEDURAL) {
                            { Icon(Icons.Default.Close, contentDescription = "إزالة", modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddImage) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة صورة")
                }
                OutlinedButton(onClick = onAddVideo) {
                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إضافة مقطع فيديو")
                }
                OutlinedButton(onClick = onPreviewAudio) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("سماع المشهد")
                }
                OutlinedButton(onClick = onPickCustomAudio) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (scene.customNarrationUri == null) "استبدال الصوت" else "تم إرفاق صوت خاص")
                }
            }

            // Duration Slider
            Column {
                Text(
                    text = "مدة المشهد: ${String.format("%.1f", scene.effectiveDurationSec)} ثانية",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = scene.effectiveDurationSec,
                    onValueChange = { d -> onUpdateScene { it.copy(customDurationSec = d) } },
                    valueRange = 2f..35f
                )
            }

            // Transition & Ken Burns Motion
            Text("انتقال المشهد وحركة الكاميرا:", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TransitionType.entries.forEach { tr ->
                    FilterChip(
                        selected = scene.transition == tr,
                        onClick = { onUpdateScene { it.copy(transition = tr) } },
                        label = { Text(tr.titleAr) }
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KenBurnsMode.entries.forEach { kb ->
                    FilterChip(
                        selected = scene.kenBurnsMode == kb,
                        onClick = { onUpdateScene { it.copy(kenBurnsMode = kb) } },
                        label = { Text(kb.titleAr) }
                    )
                }
            }
        }
    }
}
