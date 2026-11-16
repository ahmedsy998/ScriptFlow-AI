package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppPreferences
import com.example.domain.model.ArabicDialect
import com.example.domain.model.VideoResolution
import com.example.engine.audio.TtsEngineStatus
import com.example.engine.video.DeviceVideoCapabilities

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    ttsStatus: TtsEngineStatus,
    deviceCapabilities: DeviceVideoCapabilities,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAutoSave: (Boolean) -> Unit,
    onSelectDefaultResolution: (VideoResolution) -> Unit,
    onSelectDefaultDialect: (ArabicDialect) -> Unit,
    onInstallTtsData: () -> Unit,
    onRefreshTts: () -> Unit
) {
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
                    .widthIn(max = 840.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("الإعدادات والتفضيلات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                // Appearance & Persistence
                Card(
                    shape = RoundedCornerShape(18.dp),
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
                                Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("المظهر الليلي الاحترافي (Dark Mode)", style = MaterialTheme.typography.titleMedium)
                                    Text("الوضع الافتراضي المريح للاستوديو — يمكنك التبديل للوضع الفاتح", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = preferences.isDarkTheme,
                                onCheckedChange = onToggleDarkTheme,
                                modifier = Modifier.testTag("settings_dark_theme_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الحفظ التلقائي للمسودات والمشاريع", style = MaterialTheme.typography.titleMedium)
                                Text("يحفظ السكربت وكافة الإعدادات محلياً عبر Room و DataStore", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = preferences.autoSaveEnabled,
                                onCheckedChange = onToggleAutoSave
                            )
                        }
                    }
                }

                // Defaults
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("الدقة الافتراضية للتصدير:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = preferences.defaultResolution == res,
                                    onClick = { onSelectDefaultResolution(res) },
                                    label = { Text(res.labelAr) }
                                )
                            }
                        }

                        Text("اللهجة الافتراضية:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArabicDialect.entries.forEach { d ->
                                FilterChip(
                                    selected = preferences.defaultDialect == d,
                                    onClick = { onSelectDefaultDialect(d) },
                                    label = { Text(d.titleAr) }
                                )
                            }
                        }
                    }
                }

                // Speech Engine Status
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("محرك النطق والتعليق الصوتي المحلي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(ttsStatus.statusMessageAr, style = MaterialTheme.typography.bodyMedium)
                        if (ttsStatus.installedVoiceNames.isNotEmpty()) {
                            Text(
                                text = "الأصوات المكتشفة: ${ttsStatus.installedVoiceNames.joinToString("، ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onInstallTtsData) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("تثبيت حزم الصوت العربي")
                            }
                            OutlinedButton(onClick = onRefreshTts) {
                                Text("تحديث الحالة")
                            }
                        }
                    }
                }

                // Hardware & Architecture Transparency
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("قدرات الجهاز والمعالجة المجانية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("• مشفّر الفيديو: ${deviceCapabilities.encoderName}", style = MaterialTheme.typography.bodyMedium)
                        Text("• أقصى أبعاد مدعومة: ${deviceCapabilities.maxEncoderWidth}x${deviceCapabilities.maxEncoderHeight}", style = MaterialTheme.typography.bodyMedium)
                        Text("• المساحة الحرة المتوفرة: ${deviceCapabilities.freeStorageMb} MB", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(
                                "يعمل ScriptFlow AI محلياً بالكامل على جهازك بصفر اشتراكات أو واجهات مدفوعة.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
