package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.FontChoice
import com.example.domain.model.TeleprompterConfig
import com.example.domain.model.TeleprompterExportMode
import com.example.domain.model.TeleprompterTextAlign
import com.example.domain.model.VerticalPosition
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.IbmPlexArabicFontFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeleprompterConfigSection(
    config: TeleprompterConfig,
    scriptSample: String,
    onConfigChange: ((TeleprompterConfig) -> TeleprompterConfig) -> Unit
) {
    var previewPlaying by remember { mutableStateOf(true) }
    val previewSentences = remember(scriptSample) {
        val parts = scriptSample.trim().split(Regex("""(?<=[.!؟?\n])\s+""")).filter { it.isNotBlank() }
        if (parts.isNotEmpty()) parts else listOf(
            "مرحباً بكم في نظام التلقين التلقائي الذكي.",
            "يمكنك التحكم في حجم الخط واللون وسرعة التمرير بحرية تامة.",
            "كما يمكنك اختيار إظهاره في المعاينة فقط أو تضمينه داخل الفيديو."
        )
    }
    var activeSentenceIndex by remember { mutableStateOf(0) }

    LaunchedEffect(config.enabled, config.autoScroll, previewPlaying, config.scrollSpeed, previewSentences) {
        if (config.enabled && config.autoScroll && previewPlaying && previewSentences.size > 1) {
            while (true) {
                val intervalMs = (2400L / config.scrollSpeed.coerceIn(0.4f, 2.5f)).toLong()
                delay(intervalMs)
                activeSentenceIndex = (activeSentenceIndex + 1) % previewSentences.size
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column {
                        Text(
                            text = "إظهار التلقين التلقائي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ملقّن احترافي عائم مستقل عن الترجمة السفلية",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled -> onConfigChange { it.copy(enabled = enabled) } },
                    modifier = Modifier.testTag("teleprompter_toggle")
                )
            }

            AnimatedVisibility(visible = config.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Live Interactive Teleprompter Preview Box
                    Text(
                        text = "معاينة حية للملقّن (يمكنك التمرير يدوياً أو الإيقاف المؤقت)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF060911))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .padding(10.dp)
                    ) {
                        val boxAlignment = when (config.position) {
                            VerticalPosition.TOP -> Alignment.TopCenter
                            VerticalPosition.CENTER -> Alignment.Center
                            VerticalPosition.BOTTOM -> Alignment.BottomCenter
                        }
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = boxAlignment) {
                            LiveTeleprompterOverlay(
                                config = config,
                                sentences = previewSentences,
                                activeSentenceIndex = activeSentenceIndex,
                                isPlaying = previewPlaying,
                                onTogglePlay = { previewPlaying = !previewPlaying },
                                onSelectSentence = { activeSentenceIndex = it },
                                modifier = Modifier.fillMaxWidth(config.widthFraction.coerceIn(0.5f, 1f))
                            )
                        }
                    }

                    // Export Mode: Preview Only vs Burn-in
                    Text("وضع الظهور في الفيديو:", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeleprompterExportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = config.exportMode == mode,
                                onClick = { onConfigChange { it.copy(exportMode = mode) } },
                                label = { Text(mode.titleAr) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Text Size Slider
                    Column {
                        Text(
                            text = "حجم نص التلقين: ${config.textSizeSp.roundToInt()} sp",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = config.textSizeSp,
                            onValueChange = { v -> onConfigChange { it.copy(textSizeSp = v) } },
                            valueRange = 14f..42f
                        )
                    }

                    // Font Selection
                    Text("خط التلقين:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontChoice.entries.forEach { font ->
                            FilterChip(
                                selected = config.fontStyle == font,
                                onClick = { onConfigChange { it.copy(fontStyle = font) } },
                                label = { Text(font.titleAr) }
                            )
                        }
                    }

                    // Position & Alignment
                    Text("موضع الملقّن:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VerticalPosition.entries.forEach { pos ->
                            FilterChip(
                                selected = config.position == pos,
                                onClick = { onConfigChange { it.copy(position = pos) } },
                                label = { Text(pos.titleAr) }
                            )
                        }
                    }

                    Text("محاذاة النص:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeleprompterTextAlign.entries.forEach { align ->
                            FilterChip(
                                selected = config.textAlign == align,
                                onClick = { onConfigChange { it.copy(textAlign = align) } },
                                label = { Text(align.titleAr) }
                            )
                        }
                    }

                    // Width & Scroll Speed
                    Column {
                        Text(
                            text = "عرض نافذة التلقين: ${(config.widthFraction * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = config.widthFraction,
                            onValueChange = { v -> onConfigChange { it.copy(widthFraction = v) } },
                            valueRange = 0.50f..1.0f
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تمرير تلقائي مع تمييز الجملة الحالية", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = config.autoScroll,
                            onCheckedChange = { a -> onConfigChange { it.copy(autoScroll = a) } }
                        )
                    }

                    if (config.autoScroll) {
                        Column {
                            Text(
                                text = "سرعة التمرير: ${String.format("%.1f", config.scrollSpeed)}x",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = config.scrollSpeed,
                                onValueChange = { v -> onConfigChange { it.copy(scrollSpeed = v) } },
                                valueRange = 0.4f..2.4f
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تمييز الجملة النشطة", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = config.highlightCurrentSentence,
                            onCheckedChange = { h -> onConfigChange { it.copy(highlightCurrentSentence = h) } }
                        )
                    }

                    // Transparent Background & Opacity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("خلفية شفافة بالكامل", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = config.transparentBackground,
                            onCheckedChange = { t -> onConfigChange { it.copy(transparentBackground = t) } }
                        )
                    }

                    if (!config.transparentBackground) {
                        Column {
                            Text(
                                text = "شفافية الخلفية: ${(config.backgroundOpacity * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = config.backgroundOpacity,
                                onValueChange = { v -> onConfigChange { it.copy(backgroundOpacity = v) } },
                                valueRange = 0.15f..0.98f
                            )
                        }

                        Text("لون الخلفية:", style = MaterialTheme.typography.labelLarge)
                        ColorSwatchRow(
                            colors = listOf(0xFF090D16, 0xFF1E293B, 0xFF1E1B4B, 0xFF000000, 0xFF27272A),
                            selectedHex = config.backgroundColorHex,
                            onSelect = { hex -> onConfigChange { it.copy(backgroundColorHex = hex) } }
                        )
                    }

                    Text("لون نص التلقين:", style = MaterialTheme.typography.labelLarge)
                    ColorSwatchRow(
                        colors = listOf(0xFFFFFFFF, 0xFFF5B742, 0xFF38BDF8, 0xFF4ADE80, 0xFFFDE047),
                        selectedHex = config.textColorHex,
                        onSelect = { hex -> onConfigChange { it.copy(textColorHex = hex) } }
                    )
                }
            }
        }
    }
}

@Composable
fun LiveTeleprompterOverlay(
    config: TeleprompterConfig,
    sentences: List<String>,
    activeSentenceIndex: Int,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSelectSentence: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val fontFamily = when (config.fontStyle) {
        FontChoice.CAIRO -> CairoFontFamily
        FontChoice.IBM_PLEX -> IbmPlexArabicFontFamily
        FontChoice.AMIRI -> AmiriFontFamily
    }
    val align = when (config.textAlign) {
        TeleprompterTextAlign.RIGHT -> TextAlign.Right
        TeleprompterTextAlign.CENTER -> TextAlign.Center
        TeleprompterTextAlign.LEFT -> TextAlign.Left
    }
    val bgColor = if (config.transparentBackground) {
        Color.Transparent
    } else {
        Color(config.backgroundColorHex).copy(alpha = config.backgroundOpacity.coerceIn(0.1f, 1f))
    }
    val scrollState = rememberScrollState()

    LaunchedEffect(activeSentenceIndex, config.autoScroll) {
        if (config.autoScroll && sentences.isNotEmpty()) {
            val target = (scrollState.maxValue * (activeSentenceIndex.toFloat() / sentences.size.coerceAtLeast(1)))
                .toInt()
            scrollState.animateScrollTo(target)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (config.transparentBackground) 0.dp else 1.dp,
                color = if (config.transparentBackground) Color.Transparent else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الملقّن التلقائي",
                style = MaterialTheme.typography.labelSmall,
                color = Color(config.textColorHex).copy(alpha = 0.7f)
            )
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "إيقاف التمرير" else "تشغيل التمرير",
                    tint = Color(config.textColorHex),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sentences.forEachIndexed { idx, line ->
                val isCurrent = idx == activeSentenceIndex
                val textColor = if (config.highlightCurrentSentence && isCurrent) {
                    Color(0xFFF5B742)
                } else {
                    Color(config.textColorHex).copy(alpha = if (config.highlightCurrentSentence && !isCurrent) 0.55f else 0.95f)
                }
                Text(
                    text = line,
                    fontFamily = fontFamily,
                    fontSize = (config.textSizeSp * 0.72f).coerceIn(12f, 28f).sp,
                    fontWeight = if (isCurrent && config.highlightCurrentSentence) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    textAlign = align,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSentence(idx) }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ColorSwatchRow(
    colors: List<Long>,
    selectedHex: Long,
    onSelect: (Long) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { hex ->
            val selected = hex == selectedHex
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(hex))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}
