package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ArabicDialect
import com.example.domain.model.AspectRatio
import com.example.domain.model.FontChoice
import com.example.domain.model.SubtitleBackgroundStyle
import com.example.domain.model.SubtitleLanguage
import com.example.domain.model.TransitionType
import com.example.domain.model.VerticalPosition
import com.example.domain.model.VideoConfig
import com.example.domain.model.VideoResolution
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceConfig
import com.example.domain.model.VoiceGender
import com.example.domain.model.VoiceProfile
import com.example.engine.audio.TtsEngineStatus
import com.example.engine.video.DeviceVideoCapabilities
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSelectionPanel(
    voiceConfig: VoiceConfig,
    ttsStatus: TtsEngineStatus,
    isPreviewPlaying: Boolean,
    onVoiceConfigChange: ((VoiceConfig) -> VoiceConfig) -> Unit,
    onTogglePreview: () -> Unit,
    onInstallTtsData: () -> Unit
) {
    var selectedGenderTab by remember {
        mutableIntStateOf(
            if (VoiceProfile.fromId(voiceConfig.voiceId).gender == VoiceGender.FEMALE) 1 else 0
        )
    }
    var showAdvancedVoice by remember { mutableStateOf(false) }

    val customNarrationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onVoiceConfigChange { cfg -> cfg.copy(customFullNarrationUri = it.toString()) } }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("اختيار الصوت واللهجة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTogglePreview,
                    modifier = Modifier.testTag("voice_preview_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isPreviewPlaying) "إيقاف المعاينة" else "معاينة الصوت")
                }
            }

            // Honest TTS Engine Capability Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (ttsStatus.arabicAvailable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = ttsStatus.statusMessageAr,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!ttsStatus.arabicAvailable) {
                    TextButton(onClick = onInstallTtsData) {
                        Text("تثبيت الصوت", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Dialects Selector
            Text("اللغة واللهجة العربية:", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ArabicDialect.entries.forEach { dialect ->
                    FilterChip(
                        selected = voiceConfig.dialect == dialect,
                        onClick = { onVoiceConfigChange { it.copy(dialect = dialect) } },
                        label = { Text(dialect.titleAr) }
                    )
                }
            }

            // Male / Female Tabs
            TabRow(
                selectedTabIndex = selectedGenderTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedGenderTab == 0,
                    onClick = { selectedGenderTab = 0 },
                    text = { Text("أصوات رجالية (6)") }
                )
                Tab(
                    selected = selectedGenderTab == 1,
                    onClick = { selectedGenderTab = 1 },
                    text = { Text("أصوات نسائية (6)") }
                )
            }

            val visibleGender = if (selectedGenderTab == 0) VoiceGender.MALE else VoiceGender.FEMALE
            val profiles = remember(visibleGender) {
                VoiceProfile.entries.filter { it.gender == visibleGender }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val selected = voiceConfig.voiceId == profile.id
                    Card(
                        modifier = Modifier
                            .width(188.dp)
                            .clickable { onVoiceConfigChange { it.copy(voiceId = profile.id) } }
                            .testTag("voice_card_${profile.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SettingsVoice,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = profile.titleAr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = profile.descriptionAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Expandable Advanced Voice Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showAdvancedVoice = !showAdvancedVoice }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ضبط السرعة والنبرة والوقفات", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Icon(
                    imageVector = if (showAdvancedVoice) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(visible = showAdvancedVoice) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column {
                        Text("سرعة الإلقاء: ${String.format("%.2f", voiceConfig.speed)}x", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = voiceConfig.speed,
                            onValueChange = { v -> onVoiceConfigChange { it.copy(speed = v) } },
                            valueRange = 0.65f..1.65f
                        )
                    }
                    Column {
                        Text("حدة الصوت (Pitch): ${String.format("%.2f", voiceConfig.pitch)}x", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = voiceConfig.pitch,
                            onValueChange = { v -> onVoiceConfigChange { it.copy(pitch = v) } },
                            valueRange = 0.70f..1.40f
                        )
                    }
                    Column {
                        Text("مستوى صوت التعليق: ${(voiceConfig.narrationVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = voiceConfig.narrationVolume,
                            onValueChange = { v -> onVoiceConfigChange { it.copy(narrationVolume = v) } },
                            valueRange = 0.2f..1.5f
                        )
                    }
                    Column {
                        Text("وقفة طبيعية بين الفقرات: ${voiceConfig.paragraphPauseMs} مللي ثانية", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = voiceConfig.paragraphPauseMs.toFloat(),
                            onValueChange = { v -> onVoiceConfigChange { it.copy(paragraphPauseMs = v.roundToInt()) } },
                            valueRange = 150f..1500f
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("معالجة النطق العربي الذكي وإزالة التطويل", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = voiceConfig.arabicPronunciationCleanup,
                            onCheckedChange = { c -> onVoiceConfigChange { it.copy(arabicPronunciationCleanup = c) } }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { customNarrationLauncher.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (voiceConfig.customFullNarrationUri == null) "رفع تعليق صوتي جاهز"
                                else "تم إرفاق تعليق صوتي"
                            )
                        }
                        if (voiceConfig.customFullNarrationUri != null) {
                            TextButton(onClick = { onVoiceConfigChange { it.copy(customFullNarrationUri = null) } }) {
                                Text("إزالة")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisualStyleSelectorPanel(
    selectedStyle: VisualStyle,
    onSelectStyle: (VisualStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("النمط البصري للفيديو (10 أساليب)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(VisualStyle.entries, key = { it.id }) { style ->
                    val selected = style == selectedStyle
                    Card(
                        modifier = Modifier
                            .width(164.dp)
                            .clickable { onSelectStyle(style) }
                            .testTag("style_card_${style.id}"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(74.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(style.primaryHex), Color(style.secondaryHex))
                                        )
                                    )
                                    .padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(style.accentHex).copy(alpha = 0.28f))
                                        .border(1.dp, Color(style.accentHex), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = style.titleAr,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "محدد",
                                        tint = Color(style.accentHex),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                    )
                                }
                            }
                            Text(
                                text = style.subtitleAr,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoConfigurationPanel(
    videoConfig: VideoConfig,
    deviceCapabilities: DeviceVideoCapabilities,
    onConfigChange: ((VideoConfig) -> VideoConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val logoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onConfigChange { cfg -> cfg.copy(logoUri = it.toString()) } }
    }

    val musicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onConfigChange { cfg -> cfg.copy(backgroundMusicUri = it.toString()) } }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("video_config_toggle"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("إعدادات الفيديو والترجمة والموسيقى", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${videoConfig.aspectRatio.id} • ${videoConfig.resolution.id} • ${videoConfig.defaultTransition.titleAr}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            // Quick Aspect Ratio Chips always visible
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatio.entries.forEach { ratio ->
                    FilterChip(
                        selected = videoConfig.aspectRatio == ratio,
                        onClick = { onConfigChange { it.copy(aspectRatio = ratio) } },
                        label = { Text(ratio.labelAr) }
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("دقة التصدير:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoResolution.entries.forEach { res ->
                            val enabled = res == VideoResolution.HD_720P || deviceCapabilities.supports1080p
                            FilterChip(
                                selected = videoConfig.resolution == res,
                                onClick = { if (enabled) onConfigChange { it.copy(resolution = res) } },
                                enabled = enabled,
                                label = {
                                    Text(if (!enabled) "${res.labelAr} (غير مدعوم بالجهاز)" else res.labelAr)
                                }
                            )
                        }
                    }

                    Text("لغة الترجمة (Subtitles):", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtitleLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = videoConfig.subtitleLanguage == lang,
                                onClick = { onConfigChange { it.copy(subtitleLanguage = lang) } },
                                label = { Text(lang.titleAr) }
                            )
                        }
                    }

                    if (videoConfig.subtitleLanguage != SubtitleLanguage.NONE) {
                        Text("خط الترجمة:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FontChoice.entries.forEach { f ->
                                FilterChip(
                                    selected = videoConfig.subtitleFont == f,
                                    onClick = { onConfigChange { it.copy(subtitleFont = f) } },
                                    label = { Text(f.titleAr) }
                                )
                            }
                        }

                        Column {
                            Text("حجم خط الترجمة: ${videoConfig.subtitleSizeSp.roundToInt()} sp", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = videoConfig.subtitleSizeSp,
                                onValueChange = { v -> onConfigChange { it.copy(subtitleSizeSp = v) } },
                                valueRange = 14f..38f
                            )
                        }

                        Text("لون خط الترجمة:", style = MaterialTheme.typography.labelLarge)
                        ColorSwatchRow(
                            colors = listOf(0xFFFFFFFF, 0xFFF5B742, 0xFF38BDF8, 0xFFFDE047, 0xFF4ADE80),
                            selectedHex = videoConfig.subtitleColorHex,
                            onSelect = { hex -> onConfigChange { it.copy(subtitleColorHex = hex) } }
                        )

                        Text("خلفية الترجمة:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubtitleBackgroundStyle.entries.forEach { bg ->
                                FilterChip(
                                    selected = videoConfig.subtitleBgStyle == bg,
                                    onClick = { onConfigChange { it.copy(subtitleBgStyle = bg) } },
                                    label = { Text(bg.titleAr) }
                                )
                            }
                        }

                        Text("موضع الترجمة:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VerticalPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = videoConfig.subtitlePosition == pos,
                                    onClick = { onConfigChange { it.copy(subtitlePosition = pos) } },
                                    label = { Text(pos.titleAr) }
                                )
                            }
                        }
                    }

                    Text("أسلوب الانتقال بين المشاهد:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransitionType.entries.forEach { tr ->
                            FilterChip(
                                selected = videoConfig.defaultTransition == tr,
                                onClick = { onConfigChange { it.copy(defaultTransition = tr) } },
                                label = { Text(tr.titleAr) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تنوع الانتقالات تلقائياً", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = videoConfig.autoTransitions,
                            onCheckedChange = { a -> onConfigChange { it.copy(autoTransitions = a) } }
                        )
                    }

                    Column {
                        Text("تعديل مدة المشاهد: ${String.format("%.2f", videoConfig.sceneDurationMultiplier)}x", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = videoConfig.sceneDurationMultiplier,
                            onValueChange = { v -> onConfigChange { it.copy(sceneDurationMultiplier = v) } },
                            valueRange = 0.7f..1.8f
                        )
                    }

                    // Logo & Background Music pickers (Photo Picker & SAF)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (videoConfig.logoUri == null) "إضافة شعار (Logo)" else "تغيير الشعار")
                        }
                        if (videoConfig.logoUri != null) {
                            AssistChip(
                                onClick = { onConfigChange { it.copy(logoUri = null) } },
                                label = { Text("إزالة الشعار") }
                            )
                        }

                        OutlinedButton(onClick = { musicPicker.launch(arrayOf("audio/*")) }) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (videoConfig.backgroundMusicUri == null) "إضافة موسيقى خلفية" else "تغيير الموسيقى")
                        }
                        if (videoConfig.backgroundMusicUri != null) {
                            AssistChip(
                                onClick = { onConfigChange { it.copy(backgroundMusicUri = null) } },
                                label = { Text("إزالة الموسيقى") }
                            )
                        }
                    }

                    Column {
                        Text("مستوى صوت الموسيقى الخلفية: ${(videoConfig.musicVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = videoConfig.musicVolume,
                            onValueChange = { v -> onConfigChange { it.copy(musicVolume = v) } },
                            valueRange = 0f..1f
                        )
                    }

                    Column {
                        Text("مستوى صوت السرد العام: ${(videoConfig.narrationVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = videoConfig.narrationVolume,
                            onValueChange = { v -> onConfigChange { it.copy(narrationVolume = v) } },
                            valueRange = 0.2f..1.5f
                        )
                    }
                }
            }
        }
    }
}
