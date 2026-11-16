package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.domain.engine.ScriptAnalysisEngine
import com.example.domain.model.AspectRatio
import com.example.domain.model.EmotionalTone
import com.example.domain.model.StudioProject
import com.example.domain.model.TeleprompterConfig
import com.example.domain.model.TeleprompterExportMode
import com.example.domain.model.VideoConfig
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScriptFlowStudioTest {

    private val defaultVoice = VoiceConfig()
    private val defaultVideo = VideoConfig()

    @Test
    fun `short Arabic script produces accurate metrics and storyboard`() {
        val script = "مرحباً بكم في عالم الذكاء الاصطناعي لصناعة الفيديو العربي."
        val metrics = ScriptAnalysisEngine.analyzeMetrics(script, defaultVoice, defaultVideo)
        assertEquals(9, metrics.wordCount)
        assertEquals(1, metrics.paragraphCount)
        assertTrue(metrics.estimatedDurationSec >= 2.5f)

        val scenes = ScriptAnalysisEngine.generateStoryboard(
            script, VisualStyle.CINEMATIC, defaultVoice, defaultVideo
        )
        assertEquals(1, scenes.size)
        assertEquals(script, scenes[0].originalSegment)
    }

    @Test
    fun `very long Arabic script has no arbitrary duration limit and splits cleanly`() {
        val paragraph = "في هذه الرحلة الطويلة عبر التاريخ والعلوم، نستكشف كيف تطورت الفنون والمعارف الإنسانية عبر القرون المتعاقبة."
        val longScript = (1..40).joinToString("\n\n") { "الفقرة رقم $it: $paragraph" }

        val metrics = ScriptAnalysisEngine.analyzeMetrics(longScript, defaultVoice, defaultVideo)
        assertTrue("Expected > 200 seconds for 40 paragraphs", metrics.estimatedDurationSec > 200f)

        val scenes = ScriptAnalysisEngine.generateStoryboard(
            longScript, VisualStyle.DOCUMENTARY, defaultVoice, defaultVideo
        )
        assertEquals(40, scenes.size)
        val totalDuration = scenes.sumOf { it.effectiveDurationSec.toDouble() }
        assertTrue("No 5-minute cap should restrict long scripts", totalDuration > 300.0)
    }

    @Test
    fun `English and mixed Arabic-English scripts with punctuation and numbers preserve content`() {
        val mixedScript = """
            Welcome to ScriptFlow AI 2026! Let's explore how neural networks work.

            في عام 2026، أصبح الذكاء الاصطناعي قادراً على معالجة النصوص العربية والإنجليزية بنسبة دقة تصل إلى 99.5%.
        """.trimIndent()

        val metrics = ScriptAnalysisEngine.analyzeMetrics(mixedScript, defaultVoice, defaultVideo)
        assertEquals(2, metrics.paragraphCount)
        assertTrue(metrics.wordCount > 20)

        val scenes = ScriptAnalysisEngine.generateStoryboard(
            mixedScript, VisualStyle.EDUCATIONAL, defaultVoice, defaultVideo
        )
        assertEquals(2, scenes.size)
        assertTrue(scenes[0].originalSegment.contains("ScriptFlow AI 2026"))
        assertTrue(scenes[1].originalSegment.contains("99.5%"))
    }

    @Test
    fun `dialogue script detects speakers and dialogue tone`() {
        val dialogueScript = """
            الراوي: كان الجو هادئاً في الاستوديو قبل بدء البث المباشر.
            سارة: هل تأكدت من ضبط مستوى الصوت والترجمة؟
            أحمد: نعم، كل المشاهد جاهزة للتصدير الآن.
        """.trimIndent()

        val metrics = ScriptAnalysisEngine.analyzeMetrics(dialogueScript, defaultVoice, defaultVideo)
        assertEquals(3, metrics.dialogueCount)

        val scenes = ScriptAnalysisEngine.generateStoryboard(
            dialogueScript, VisualStyle.STORYTELLING, defaultVoice, defaultVideo
        )
        assertEquals(3, scenes.size)
        assertTrue(scenes.all { it.isDialogue && it.emotionalTone == EmotionalTone.DIALOGUE })
        assertEquals("سارة", scenes[1].speakerName)
    }

    @Test
    fun `aspect ratios produce encoder-safe multiples of 16`() {
        AspectRatio.entries.forEach { ratio ->
            val (w720, h720) = ratio.dimensions(com.example.domain.model.VideoResolution.HD_720P)
            val (w1080, h1080) = ratio.dimensions(com.example.domain.model.VideoResolution.FHD_1080P)
            assertEquals(0, w720 % 16)
            assertEquals(0, h720 % 16)
            assertEquals(0, w1080 % 16)
            assertEquals(0, h1080 % 16)
        }
    }

    @Test
    fun `project repository saves restores and round-trips JSON cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ProjectRepository(db.studioDao())

        val script = "مشهد تجريبي لحفظ المشروع واستعادته بعد إعادة التشغيل."
        val scenes = ScriptAnalysisEngine.generateStoryboard(
            script, VisualStyle.LUXURY, defaultVoice, defaultVideo
        )
        val project = StudioProject(
            title = "اختبار الحفظ",
            scriptText = script,
            visualStyle = VisualStyle.LUXURY,
            teleprompterConfig = TeleprompterConfig(
                enabled = true,
                exportMode = TeleprompterExportMode.BURN_IN_VIDEO
            ),
            scenes = scenes,
            isDraft = false
        )

        repo.saveProject(project)
        val loaded = repo.getProject(project.id)
        assertNotNull(loaded)
        assertEquals("اختبار الحفظ", loaded?.title)
        assertEquals(VisualStyle.LUXURY, loaded?.visualStyle)
        assertTrue(loaded?.teleprompterConfig?.enabled == true)
        assertEquals(1, loaded?.scenes?.size)

        val json = repo.exportProjectToJson(project)
        val imported = repo.importProjectFromJson(json)
        assertTrue(imported.isSuccess)
        assertFalse(imported.getOrThrow().id == project.id)
        assertTrue(imported.getOrThrow().title.contains("اختبار الحفظ"))

        db.close()
    }
}
