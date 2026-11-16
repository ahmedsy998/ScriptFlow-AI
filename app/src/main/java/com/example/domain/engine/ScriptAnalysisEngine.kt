package com.example.domain.engine

import com.example.domain.model.EmotionalTone
import com.example.domain.model.KenBurnsMode
import com.example.domain.model.SceneAsset
import com.example.domain.model.SceneMediaType
import com.example.domain.model.ScriptMetrics
import com.example.domain.model.StoryboardScene
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoConfig
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceConfig
import com.example.domain.model.VoiceProfile
import kotlin.math.max

object ScriptAnalysisEngine {

    private val sceneMarkerRegex = Regex(
        """^(?:\[?(?:مشهد|المشهد|انتقال|scene|cut|fade)\]?\s*\d*\s*[:\-–—]?|-{3,}|\*{3,}|#{1,3}\s+.+)$""",
        setOf(RegexOption.IGNORE_CASE)
    )

    private val dialoguePrefixRegex = Regex(
        """^([\p{L}\p{N}_ ]{2,24})\s*[:：]\s+(.+)$"""
    )

    private val quoteDialogueRegex = Regex("""^[«"“—\-]\s*.+""")

    private val sentenceSplitRegex = Regex("""(?<=[.!؟?۔؛\n])\s+""")

    private val arabicStopWords = setOf(
        "في", "من", "على", "إلى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
        "الذين", "ما", "لا", "نعم", "قد", "كان", "كانت", "يكون", "أن", "إن", "أو", "ثم",
        "حتى", "بين", "كل", "بعد", "قبل", "عند", "كما", "لكن", "هو", "هي", "هم", "نحن",
        "أنت", "أنا", "فيه", "فيها", "عليه", "عليها", "منها", "منه", "إليه", "بها", "به",
        "the", "and", "is", "are", "was", "were", "in", "on", "at", "to", "for", "of",
        "with", "a", "an", "that", "this", "it", "as", "by", "from", "or", "but"
    )

    private val dramaticWords = setOf(
        "عاصفة", "صراع", "خطر", "مصير", "انفجار", "صدمة", "سر", "ظلام", "معركة", "انهيار",
        "حاسمة", "dramatic", "crisis", "dark", "storm", "battle", "shock", "secret"
    )
    private val urgentWords = setOf(
        "عاجل", "الآن", "اليوم", "أعلن", "بيان", "تقرير", "مباشر", "أخبار", "ارتفاع", "تحذير",
        "breaking", "urgent", "today", "report", "news", "alert"
    )
    private val educationalWords = setOf(
        "كيف", "لماذا", "أولاً", "ثانياً", "ثالثاً", "خطوة", "شرح", "تعلّم", "تعلم", "معلومة",
        "السبب", "الطريقة", "قاعدة", "مثال", "how", "why", "step", "learn", "guide", "explain"
    )
    private val inspiringWords = setOf(
        "نجاح", "أمل", "حلم", "مستقبل", "إبداع", "طريق", "إنجاز", "قوة", "ابتكار", "انطلق",
        "success", "dream", "future", "hope", "inspire", "create", "power"
    )
    private val calmWords = setOf(
        "هدوء", "سكينة", "تأمل", "ليل", "بحر", "نسيم", "سلام", "راحة", "صباح", "صمت",
        "calm", "peace", "quiet", "morning", "ocean", "gentle", "soft"
    )

    fun analyzeMetrics(
        scriptText: String,
        voiceConfig: VoiceConfig,
        videoConfig: VideoConfig
    ): ScriptMetrics {
        val trimmed = scriptText.trim()
        if (trimmed.isEmpty()) return ScriptMetrics()

        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val paragraphs = splitParagraphs(trimmed)
        val sentences = trimmed.split(sentenceSplitRegex).filter { it.isNotBlank() }

        var dialogueCount = 0
        var transitionMarkers = 0
        paragraphs.forEach { para ->
            val lines = para.lines().map { it.trim() }.filter { it.isNotEmpty() }
            lines.forEach { line ->
                if (sceneMarkerRegex.matches(line)) {
                    transitionMarkers++
                } else if (dialoguePrefixRegex.matches(line) || quoteDialogueRegex.matches(line)) {
                    dialogueCount++
                }
            }
        }

        val voiceProfile = VoiceProfile.fromId(voiceConfig.voiceId)
        val effectiveSpeed = (voiceConfig.speed * voiceProfile.baseRateMultiplier).coerceIn(0.5f, 2.2f)
        val baseWordsPerSecond = 2.15f * effectiveSpeed // ~129 Arabic WPM at 1.0x
        val speechSeconds = words.size / baseWordsPerSecond
        val pauseSeconds = max(0, paragraphs.size - 1) * (voiceConfig.paragraphPauseMs / 1000f)
        val totalSeconds = ((speechSeconds + pauseSeconds) * videoConfig.sceneDurationMultiplier)
            .coerceAtLeast(if (words.isNotEmpty()) 2.5f else 0f)

        return ScriptMetrics(
            wordCount = words.size,
            charCount = trimmed.length,
            paragraphCount = paragraphs.size,
            sentenceCount = max(1, sentences.size),
            dialogueCount = dialogueCount,
            transitionMarkerCount = transitionMarkers,
            estimatedDurationSec = totalSeconds
        )
    }

    fun generateStoryboard(
        scriptText: String,
        visualStyle: VisualStyle,
        voiceConfig: VoiceConfig,
        videoConfig: VideoConfig
    ): List<StoryboardScene> {
        val trimmed = scriptText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawSegments = buildLogicalSegments(trimmed)
        val voiceProfile = VoiceProfile.fromId(voiceConfig.voiceId)
        val effectiveSpeed = (voiceConfig.speed * voiceProfile.baseRateMultiplier).coerceIn(0.5f, 2.2f)

        val kenBurnsModes = listOf(
            KenBurnsMode.ZOOM_IN,
            KenBurnsMode.PAN_RIGHT,
            KenBurnsMode.ZOOM_OUT,
            KenBurnsMode.PAN_LEFT
        )
        val autoTransitions = listOf(
            visualStyle.defaultTransition,
            TransitionType.FADE,
            TransitionType.SLIDE_RTL,
            TransitionType.DISSOLVE,
            TransitionType.ZOOM_IN
        )

        return rawSegments.mapIndexed { index, segment ->
            val cleanSegment = segment.trim()
            val dialogueMatch = dialoguePrefixRegex.matchEntire(cleanSegment)
            val isDialogue = dialogueMatch != null || quoteDialogueRegex.matches(cleanSegment)
            val speakerName = dialogueMatch?.groupValues?.getOrNull(1)?.trim()

            // Preserve original script segment completely; only adjust TTS-friendly pronunciation text when enabled
            val narrationForSpeech = if (voiceConfig.arabicPronunciationCleanup) {
                prepareArabicPronunciation(cleanSegment)
            } else {
                cleanSegment
            }

            val words = cleanSegment.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val tone = detectEmotionalTone(cleanSegment, isDialogue)
            val keywords = extractKeywords(cleanSegment)

            val pauseSec = voiceConfig.paragraphPauseMs / 1000f
            val baseDuration = ((words.size / (2.15f * effectiveSpeed)) + pauseSec) *
                videoConfig.sceneDurationMultiplier
            val clampedDuration = baseDuration.coerceAtLeast(2.5f)

            val visualDesc = buildVisualSuggestion(
                segment = cleanSegment,
                keywords = keywords,
                tone = tone,
                style = visualStyle,
                isDialogue = isDialogue,
                speakerName = speakerName
            )

            val transition = if (!videoConfig.autoTransitions) {
                videoConfig.defaultTransition
            } else if (index == 0) {
                TransitionType.FADE
            } else {
                autoTransitions[index % autoTransitions.size]
            }

            StoryboardScene(
                sceneNumber = index + 1,
                originalSegment = cleanSegment,
                narrationText = narrationForSpeech,
                visualDescription = visualDesc,
                keywords = keywords,
                emotionalTone = tone,
                isDialogue = isDialogue,
                speakerName = speakerName,
                estimatedDurationSec = clampedDuration,
                subtitleText = cleanSegment,
                visualStyle = visualStyle,
                transition = transition,
                assets = listOf(
                    SceneAsset(
                        type = SceneMediaType.PROCEDURAL,
                        label = "${visualStyle.titleAr} • ${tone.titleAr}"
                    )
                ),
                waveformPeaks = generateIndicativeWaveform(cleanSegment),
                kenBurnsMode = kenBurnsModes[index % kenBurnsModes.size]
            )
        }
    }

    fun prepareArabicPronunciation(text: String): String {
        var result = text
            // Remove decorative tatweel so TTS pronounces words cleanly
            .replace("\u0640", "")
            // Normalize multiple spaces
            .replace(Regex("""[ \t]+"""), " ")
            // Ensure pauses around colons and dashes for natural breathing
            .replace("：", ": ")
            .replace(":", "، ")
            .replace("—", "، ")
            .trim()
        // Strip markdown-like scene markers if present at start of a line while keeping meaningful title
        if (result.startsWith("#")) {
            result = result.trimStart('#', ' ')
        }
        return result
    }

    private fun splitParagraphs(text: String): List<String> {
        return text.split(Regex("""\n\s*\n+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun buildLogicalSegments(text: String): List<String> {
        val result = mutableListOf<String>()
        val blocks = splitParagraphs(text)

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val currentBuffer = StringBuilder()

            fun flushBuffer() {
                val content = currentBuffer.toString().trim()
                if (content.isNotEmpty()) {
                    splitLongSegmentBySentences(content, result)
                    currentBuffer.clear()
                }
            }

            for (line in lines) {
                if (sceneMarkerRegex.matches(line)) {
                    flushBuffer()
                    // Preserve explicit scene headings if they carry meaningful text (e.g., "# المقدمة")
                    val cleanedHeader = line.trim('#', '-', '*', '[', ']', ' ')
                    if (cleanedHeader.length > 3 && !cleanedHeader.equals("مشهد", true) &&
                        !cleanedHeader.equals("scene", true)
                    ) {
                        result.add(line)
                    }
                } else if (dialoguePrefixRegex.matches(line) || quoteDialogueRegex.matches(line)) {
                    flushBuffer()
                    result.add(line)
                } else {
                    if (currentBuffer.isNotEmpty()) currentBuffer.append(' ')
                    currentBuffer.append(line)
                }
            }
            flushBuffer()
        }
        return result.ifEmpty { listOf(text.trim()) }
    }

    private fun splitLongSegmentBySentences(segment: String, out: MutableList<String>) {
        val words = segment.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size <= 32) {
            out.add(segment)
            return
        }
        val sentences = segment.split(sentenceSplitRegex).map { it.trim() }.filter { it.isNotEmpty() }
        val chunk = StringBuilder()
        var chunkWordCount = 0

        for (sentence in sentences) {
            val sWords = sentence.split(Regex("""\s+""")).size
            if (chunkWordCount > 0 && chunkWordCount + sWords > 28) {
                out.add(chunk.toString().trim())
                chunk.clear()
                chunkWordCount = 0
            }
            if (chunk.isNotEmpty()) chunk.append(' ')
            chunk.append(sentence)
            chunkWordCount += sWords
        }
        if (chunk.isNotEmpty()) {
            out.add(chunk.toString().trim())
        }
    }

    fun detectEmotionalTone(text: String, isDialogue: Boolean): EmotionalTone {
        if (isDialogue) return EmotionalTone.DIALOGUE
        val lower = text.lowercase()
        return when {
            urgentWords.any { lower.contains(it) } -> EmotionalTone.URGENT
            dramaticWords.any { lower.contains(it) } -> EmotionalTone.DRAMATIC
            educationalWords.any { lower.contains(it) } -> EmotionalTone.EDUCATIONAL
            inspiringWords.any { lower.contains(it) } -> EmotionalTone.INSPIRING
            calmWords.any { lower.contains(it) } -> EmotionalTone.CALM
            else -> EmotionalTone.NARRATIVE
        }
    }

    fun extractKeywords(text: String, maxKeywords: Int = 4): List<String> {
        return text
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 3 && it.lowercase() !in arabicStopWords }
            .distinctBy { it.lowercase() }
            .take(maxKeywords)
    }

    private fun buildVisualSuggestion(
        segment: String,
        keywords: List<String>,
        tone: EmotionalTone,
        style: VisualStyle,
        isDialogue: Boolean,
        speakerName: String?
    ): String {
        val kw = if (keywords.isNotEmpty()) keywords.joinToString(" • ") else "مشهد تعبيري"
        return when {
            isDialogue && !speakerName.isNullOrBlank() ->
                "مشهد حواري ($speakerName) بطابع ${style.titleAr} — تركيز بصري على: $kw"
            tone == EmotionalTone.EDUCATIONAL ->
                "بطاقة توضيحية بأسلوب ${style.titleAr} مع إبراز المفاهيم: $kw"
            tone == EmotionalTone.URGENT ->
                "لقطة إخبارية ديناميكية بطابع ${style.titleAr} — محاور المشهد: $kw"
            else ->
                "تكوين بصري ${style.titleAr} بنبرة ${tone.titleAr} يبرز: $kw"
        }
    }

    fun generateIndicativeWaveform(text: String, bars: Int = 28): List<Float> {
        if (text.isBlank()) return List(bars) { 0.15f }
        return List(bars) { idx ->
            val ch = text[idx % text.length].code
            val norm = ((ch * 31 + idx * 17) % 75) / 100f
            (0.22f + norm).coerceIn(0.15f, 0.96f)
        }
    }
}
