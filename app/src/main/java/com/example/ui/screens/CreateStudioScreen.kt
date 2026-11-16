package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ScriptMetrics
import com.example.domain.model.StudioProject
import com.example.domain.model.TeleprompterConfig
import com.example.domain.model.VideoConfig
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceConfig
import com.example.engine.audio.TtsEngineStatus
import com.example.engine.video.DeviceVideoCapabilities
import com.example.ui.components.TeleprompterConfigSection
import com.example.ui.components.VideoConfigurationPanel
import com.example.ui.components.VisualStyleSelectorPanel
import com.example.ui.components.VoiceSelectionPanel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateStudioScreen(
    project: StudioProject,
    metrics: ScriptMetrics,
    latestDraft: StudioProject?,
    ttsStatus: TtsEngineStatus,
    isVoicePreviewPlaying: Boolean,
    deviceCapabilities: DeviceVideoCapabilities,
    onTitleChange: (String) -> Unit,
    onScriptChange: (String) -> Unit,
    onVoiceConfigChange: ((VoiceConfig) -> VoiceConfig) -> Unit,
    onSelectVisualStyle: (VisualStyle) -> Unit,
    onVideoConfigChange: ((VideoConfig) -> VideoConfig) -> Unit,
    onTeleprompterConfigChange: ((TeleprompterConfig) -> TeleprompterConfig) -> Unit,
    onToggleVoicePreview: () -> Unit,
    onInstallTtsData: () -> Unit,
    onRestoreDraft: (StudioProject) -> Unit,
    onCreateVideoClicked: () -> Unit,
    onOpenExistingStoryboard: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Studio Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_scriptflow_icon),
                            contentDescription = "ScriptFlow AI",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text("استوديو إنشاء الفيديو", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("أدخل السكربت، اضبط الصوت والنمط، وأنشئ الفيديو في خطوة واحدة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (project.scenes.isNotEmpty()) {
                        OutlinedButton(onClick = onOpenExistingStoryboard) {
                            Icon(Icons.Default.VideoSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("لوحة المشاهد (${project.scenes.size})")
                        }
                    }
                }

                // A. Script Editor Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("محرر السكربت الذكي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp))
                                Text("حفظ تلقائي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }

                        OutlinedTextField(
                            value = project.title,
                            onValueChange = onTitleChange,
                            label = { Text("عنوان المشروع") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("project_title_input"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = project.scriptText,
                            onValueChange = onScriptChange,
                            placeholder = { Text("اكتب أو الصق السكربت هنا...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 190.dp, max = 380.dp)
                                .testTag("script_editor_input"),
                            shape = RoundedCornerShape(16.dp),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        // Quick Editor Toolbar
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                                    if (!clipText.isNullOrBlank()) {
                                        val combined = if (project.scriptText.isBlank()) clipText else "${project.scriptText}\n\n$clipText"
                                        onScriptChange(combined)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("لصق من الحافظة") }
                            )
                            AssistChip(
                                onClick = { onScriptChange("") },
                                leadingIcon = { Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("مسح النص") }
                            )
                            if (latestDraft != null && latestDraft.id != project.id && latestDraft.scriptText.isNotBlank()) {
                                AssistChip(
                                    onClick = { onRestoreDraft(latestDraft) },
                                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("استعادة مسودة سابقة") }
                                )
                            }
                        }

                        // Live Analysis Metrics Strip
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MetricPill("الكلمات", "${metrics.wordCount}")
                            MetricPill("الأحرف", "${metrics.charCount}")
                            MetricPill("الفقرات", "${metrics.paragraphCount}")
                            MetricPill("المدة التقديرية", formatDurationAr(metrics.estimatedDurationSec))
                            if (metrics.dialogueCount > 0) {
                                MetricPill("حوارات مكتشفة", "${metrics.dialogueCount}")
                            }
                            if (metrics.transitionMarkerCount > 0) {
                                MetricPill("فواصل مشاهد", "${metrics.transitionMarkerCount}")
                            }
                        }
                    }
                }

                // B. Voice Selection Panel
                VoiceSelectionPanel(
                    voiceConfig = project.voiceConfig,
                    ttsStatus = ttsStatus,
                    isPreviewPlaying = isVoicePreviewPlaying,
                    onVoiceConfigChange = onVoiceConfigChange,
                    onTogglePreview = onToggleVoicePreview,
                    onInstallTtsData = onInstallTtsData
                )

                // C. Visual Style Selector
                VisualStyleSelectorPanel(
                    selectedStyle = project.visualStyle,
                    onSelectStyle = onSelectVisualStyle
                )

                // D. Expandable Video Configuration
                VideoConfigurationPanel(
                    videoConfig = project.videoConfig,
                    deviceCapabilities = deviceCapabilities,
                    onConfigChange = onVideoConfigChange
                )

                // E. Optional Teleprompter System
                TeleprompterConfigSection(
                    config = project.teleprompterConfig,
                    scriptSample = project.scriptText,
                    onConfigChange = onTeleprompterConfigChange
                )

                // F. Prominent Create Video CTA
                Button(
                    onClick = onCreateVideoClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("create_video_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "إنشاء الفيديو",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

fun formatDurationAr(seconds: Float): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    val mins = total / 60
    val secs = total % 60
    return if (mins > 0) "${mins}د ${secs}ث" else "${secs} ثانية"
}
