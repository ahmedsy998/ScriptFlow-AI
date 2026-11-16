package com.example.domain.model

import com.squareup.moshi.JsonClass
import java.util.UUID

enum class ArabicDialect(
    val titleAr: String,
    val bcp47Tag: String,
    val countryCode: String,
    val samplePhrase: String
) {
    FUSHA("العربية الفصحى", "ar", "SA", "مرحباً بكم في استوديو تحويل النصوص إلى فيديو احترافي."),
    SYRIAN("اللهجة السورية", "ar-SY", "SY", "أهلاً وسهلاً فيكم، اليوم رح نحكي قصة مميزة كتير."),
    LEVANTINE("اللهجة الشامية", "ar-LB", "LB", "كيفكم اليوم؟ خلونا نبلش سوا بصناعة فيديو رائع."),
    EGYPTIAN("اللهجة المصرية", "ar-EG", "EG", "أهلاً بيكم، النهاردة هنعمل فيديو احترافي بكل سهولة."),
    GULF("اللهجة الخليجية", "ar-SA", "SA", "حياكم الله، اليوم بنستعرض معاكم فكرة جديدة ومميزة."),
    IRAQI("اللهجة العراقية", "ar-IQ", "IQ", "هلا بيكم، اليوم رح نسوي فيديو كلش حلو ومرتب.")
}

enum class VoiceGender(val titleAr: String) {
    MALE("أصوات رجالية"),
    FEMALE("أصوات نسائية")
}

enum class VoiceProfile(
    val id: String,
    val titleAr: String,
    val descriptionAr: String,
    val gender: VoiceGender,
    val basePitch: Float,
    val baseRateMultiplier: Float,
    val preferredVoiceTokens: List<String>
) {
    // MALE VOICES
    DEEP_CINEMATIC(
        "deep_cinematic",
        "صوت سينمائي عميق",
        "نبرة رخيمة وعميقة تناسب المقدمات الدرامية والإعلانات",
        VoiceGender.MALE, 0.78f, 0.90f, listOf("male", "m0", "m1", "erm", "ard", "wavenet-b", "wavenet-d")
    ),
    WARM_FRIENDLY(
        "warm_friendly",
        "صوت دافئ وودود",
        "أداء قريب ومريح للمحتوى اليومي والقصص التفاعلية",
        VoiceGender.MALE, 0.96f, 1.0f, listOf("male", "m1", "standard-b")
    ),
    PRO_DOCUMENTARY_M(
        "pro_documentary_m",
        "راوي وثائقي محترف",
        "إلقاء متزن وواضح مثالي للأفلام الوثائقية والتقارير",
        VoiceGender.MALE, 0.85f, 0.94f, listOf("male", "m2", "wavenet-d")
    ),
    YOUNG_ENERGETIC_M(
        "young_energetic_m",
        "صوت شبابي حماسي",
        "إيقاع سريع وحيوي مناسب لمقاطع السوشيال ميديا",
        VoiceGender.MALE, 1.06f, 1.12f, listOf("male", "m3")
    ),
    CALM_STORYTELLER_M(
        "calm_storyteller_m",
        "راوي قصص هادئ",
        "سرد سلس وهادئ يعزز التأمل والتركيز",
        VoiceGender.MALE, 0.90f, 0.88f, listOf("male", "m4")
    ),
    NEWS_PRESENTER_M(
        "news_presenter_m",
        "مذيع أخبار رسمي",
        "نطق دقيق وحازم للنشرات والتحليلات الإخبارية",
        VoiceGender.MALE, 0.92f, 1.04f, listOf("male", "m5")
    ),

    // FEMALE VOICES
    WARM_NARRATOR_F(
        "warm_narrator_f",
        "راوية دافئة",
        "نبرة أنثوية دافئة ومحببة للشروحات والقصص",
        VoiceGender.FEMALE, 1.14f, 0.98f, listOf("female", "f0", "f1", "wavenet-a", "wavenet-c")
    ),
    PRO_DOCUMENTARY_F(
        "pro_documentary_f",
        "صوت وثائقي نسائي احترافي",
        "أداء رصين ومتماسك للتقارير والمعرفة",
        VoiceGender.FEMALE, 1.08f, 0.95f, listOf("female", "f2", "standard-a")
    ),
    SOFT_STORYTELLER_F(
        "soft_storyteller_f",
        "راوية قصص ناعمة",
        "إلقاء شاعري هادئ للمحتوى العاطفي والأدبي",
        VoiceGender.FEMALE, 1.18f, 0.88f, listOf("female", "f3")
    ),
    YOUNG_ENERGETIC_F(
        "young_energetic_f",
        "صوت شابة حيوي",
        "أداء متجدد وسريع الإيقاع لمحتوى الريلز واليوتيوب",
        VoiceGender.FEMALE, 1.24f, 1.10f, listOf("female", "f4")
    ),
    CALM_EDUCATIONAL_F(
        "calm_educational_f",
        "صوت تعليمي هادئ",
        "وضوح عالٍ وسرعة متوازنة للشروحات والدروس",
        VoiceGender.FEMALE, 1.10f, 0.93f, listOf("female", "f5")
    ),
    NEWS_PRESENTER_F(
        "news_presenter_f",
        "مذيعة أخبار محترفة",
        "نبرة إعلامية رصينة وواضحة المخارج",
        VoiceGender.FEMALE, 1.12f, 1.03f, listOf("female", "f6")
    );

    companion object {
        fun fromId(id: String): VoiceProfile = entries.firstOrNull { it.id == id } ?: PRO_DOCUMENTARY_M
    }
}

enum class VisualStyle(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val accentHex: Long,
    val defaultTransition: TransitionType
) {
    CINEMATIC("cinematic", "سينمائي", "إضاءة درامية وتباين سينمائي عميق", 0xFF0A0E17, 0xFF1E293B, 0xFFF59E0B, TransitionType.FADE),
    CLASSIC("classic", "كلاسيكي", "أناقة خالدة وتكوين نظيف ومتوازن", 0xFF1C1917, 0xFF44403C, 0xFFE7E5E4, TransitionType.DISSOLVE),
    DOCUMENTARY("documentary", "وثائقي", "عرض واقعي وشرائط معلوماتية رصينة", 0xFF0F172A, 0xFF1E3A5F, 0xFF38BDF8, TransitionType.SLIDE_RTL),
    EDUCATIONAL("educational", "تعليمي", "توضيحات بصرية نظيفة وبطاقات معرفية", 0xFF0F1F38, 0xFF164E63, 0xFF2DD4BF, TransitionType.SLIDE_RTL),
    STORYTELLING("storytelling", "قصصي", "أجواء عاطفية دافئة وإضاءة حالمة", 0xFF1E142B, 0xFF4C1D95, 0xFFF472B6, TransitionType.DISSOLVE),
    CARTOON("cartoon", "كرتوني", "ألوان زاهية ورسوم متحركة مرحة", 0xFF1E3A8A, 0xFF7C3AED, 0xFFFACC15, TransitionType.ZOOM_IN),
    NEWS("news", "إخباري", "تصميم بث احترافي وشريط عناوين حي", 0xFF090D16, 0xFF991B1B, 0xFFEF4444, TransitionType.WIPE),
    SOCIAL_MEDIA("social_media", "سوشيال ميديا", "إيقاع سريع وخطوط بارزة وجذابة", 0xFF18181B, 0xFFBE185D, 0xFF22D3EE, TransitionType.ZOOM_IN),
    MINIMALIST("minimalist", "بسيط", "تكوينات هادئة ومساحات نقية مريحة", 0xFF111827, 0xFF374151, 0xFFE5E7EB, TransitionType.FADE),
    LUXURY("luxury", "فاخر", "لمسات ذهبية راقية وخلفية مخملية", 0xFF0B0A08, 0xFF292214, 0xFFD4AF37, TransitionType.FADE);

    companion object {
        fun fromId(id: String): VisualStyle = entries.firstOrNull { it.id == id } ?: CINEMATIC
    }
}

enum class AspectRatio(
    val id: String,
    val labelAr: String,
    val width720: Int,
    val height720: Int,
    val width1080: Int,
    val height1080: Int
) {
    // All dimensions are multiples of 16 for universal MediaCodec H.264 encoder compatibility
    RATIO_16_9("16:9", "16:9 (أفقي يوتيوب)", 1280, 720, 1920, 1088),
    RATIO_9_16("9:16", "9:16 (عمودي ريلز)", 720, 1280, 1088, 1920),
    RATIO_1_1("1:1", "1:1 (مربع)", 720, 720, 1088, 1088),
    RATIO_4_5("4:5", "4:5 (منشور)", 720, 896, 864, 1088);

    fun dimensions(resolution: VideoResolution): Pair<Int, Int> = when (resolution) {
        VideoResolution.HD_720P -> width720 to height720
        VideoResolution.FHD_1080P -> width1080 to height1080
    }
}

enum class VideoResolution(val id: String, val labelAr: String, val bitrateMbps: Int) {
    HD_720P("720p", "720p HD", 4_500_000),
    FHD_1080P("1080p", "1080p Full HD", 8_000_000)
}

enum class TransitionType(val id: String, val titleAr: String) {
    FADE("fade", "تلاشي ناعم"),
    SLIDE_RTL("slide", "انزلاق"),
    ZOOM_IN("zoom", "تقريب ديناميكي"),
    WIPE("wipe", "مسح سينمائي"),
    DISSOLVE("dissolve", "ذوبان متدرج"),
    NONE("none", "بدون انتقال")
}

enum class SubtitleLanguage(val id: String, val titleAr: String) {
    AUTO("auto", "تلقائي حسب النص"),
    ARABIC("ar", "العربية"),
    ENGLISH("en", "الإنجليزية"),
    NONE("none", "إيقاف الترجمة")
}

enum class FontChoice(val id: String, val titleAr: String) {
    CAIRO("cairo", "خط القاهرة (Cairo)"),
    IBM_PLEX("ibm_plex", "خط IBM Plex العربي"),
    AMIRI("amiri", "خط أميري الكلاسيكي")
}

enum class SubtitleBackgroundStyle(val id: String, val titleAr: String) {
    PILL_DARK("pill_dark", "خلفية داكنة مستديرة"),
    FULL_BAR("full_bar", "شريط عرض كامل"),
    SHADOW_ONLY("shadow_only", "ظل للنص فقط"),
    NONE("none", "بدون خلفية")
}

enum class VerticalPosition(val id: String, val titleAr: String) {
    TOP("top", "أعلى"),
    CENTER("center", "وسط"),
    BOTTOM("bottom", "أسفل")
}

enum class TeleprompterTextAlign(val id: String, val titleAr: String) {
    RIGHT("right", "يمين"),
    CENTER("center", "توسيط"),
    LEFT("left", "يسار")
}

enum class TeleprompterExportMode(val id: String, val titleAr: String) {
    PREVIEW_ONLY("preview_only", "في معاينة الإنتاج فقط"),
    BURN_IN_VIDEO("burn_in_video", "تضمينه كطبقة مرئية في الفيديو المُصدّر")
}

enum class EmotionalTone(val titleAr: String) {
    DRAMATIC("درامي"),
    CALM("هادئ"),
    INSPIRING("ملهم"),
    URGENT("عاجل / إخباري"),
    EDUCATIONAL("تعليمي"),
    NARRATIVE("سردي"),
    DIALOGUE("حوار")
}

enum class SceneMediaType {
    PROCEDURAL,
    USER_IMAGE,
    USER_VIDEO
}

enum class KenBurnsMode(val titleAr: String) {
    ZOOM_IN("تقريب تدريجي"),
    ZOOM_OUT("تبعيد تدريجي"),
    PAN_RIGHT("تحريك لليمين"),
    PAN_LEFT("تحريك لليسار"),
    STATIC("ثابت")
}

@JsonClass(generateAdapter = true)
data class SceneAsset(
    val id: String = UUID.randomUUID().toString(),
    val type: SceneMediaType = SceneMediaType.PROCEDURAL,
    val uriString: String? = null,
    val label: String = "خلفية بصرية ذكية"
)

@JsonClass(generateAdapter = true)
data class StoryboardScene(
    val id: String = UUID.randomUUID().toString(),
    val sceneNumber: Int,
    val originalSegment: String,
    val narrationText: String,
    val visualDescription: String,
    val keywords: List<String> = emptyList(),
    val emotionalTone: EmotionalTone = EmotionalTone.NARRATIVE,
    val isDialogue: Boolean = false,
    val speakerName: String? = null,
    val estimatedDurationSec: Float = 5f,
    val customDurationSec: Float? = null,
    val subtitleText: String = narrationText,
    val visualStyle: VisualStyle = VisualStyle.CINEMATIC,
    val transition: TransitionType = TransitionType.FADE,
    val assets: List<SceneAsset> = listOf(SceneAsset()),
    val customNarrationUri: String? = null,
    val synthesizedWavPath: String? = null,
    val waveformPeaks: List<Float> = emptyList(),
    val kenBurnsMode: KenBurnsMode = KenBurnsMode.ZOOM_IN
) {
    val effectiveDurationSec: Float
        get() = (customDurationSec ?: estimatedDurationSec).coerceAtLeast(1.5f)
}

@JsonClass(generateAdapter = true)
data class VoiceConfig(
    val voiceId: String = VoiceProfile.PRO_DOCUMENTARY_M.id,
    val dialect: ArabicDialect = ArabicDialect.FUSHA,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val narrationVolume: Float = 1.0f,
    val paragraphPauseMs: Int = 550,
    val arabicPronunciationCleanup: Boolean = true,
    val customFullNarrationUri: String? = null
)

@JsonClass(generateAdapter = true)
data class VideoConfig(
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val resolution: VideoResolution = VideoResolution.HD_720P,
    val subtitleLanguage: SubtitleLanguage = SubtitleLanguage.AUTO,
    val subtitleFont: FontChoice = FontChoice.CAIRO,
    val subtitleSizeSp: Float = 22f,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleBgStyle: SubtitleBackgroundStyle = SubtitleBackgroundStyle.PILL_DARK,
    val subtitlePosition: VerticalPosition = VerticalPosition.BOTTOM,
    val defaultTransition: TransitionType = TransitionType.FADE,
    val musicVolume: Float = 0.25f,
    val narrationVolume: Float = 1.0f,
    val sceneDurationMultiplier: Float = 1.0f,
    val autoTransitions: Boolean = true,
    val logoUri: String? = null,
    val backgroundMusicUri: String? = null,
    val musicFadeInSec: Float = 1.5f,
    val musicFadeOutSec: Float = 2.0f
)

@JsonClass(generateAdapter = true)
data class TeleprompterConfig(
    val enabled: Boolean = false,
    val textSizeSp: Float = 24f,
    val fontStyle: FontChoice = FontChoice.CAIRO,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0xFF090D16,
    val transparentBackground: Boolean = false,
    val backgroundOpacity: Float = 0.72f,
    val position: VerticalPosition = VerticalPosition.CENTER,
    val widthFraction: Float = 0.86f,
    val textAlign: TeleprompterTextAlign = TeleprompterTextAlign.CENTER,
    val autoScroll: Boolean = true,
    val scrollSpeed: Float = 1.0f,
    val highlightCurrentSentence: Boolean = true,
    val exportMode: TeleprompterExportMode = TeleprompterExportMode.PREVIEW_ONLY
)

@JsonClass(generateAdapter = true)
data class StudioProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "مشروع جديد",
    val scriptText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val voiceConfig: VoiceConfig = VoiceConfig(),
    val visualStyle: VisualStyle = VisualStyle.CINEMATIC,
    val videoConfig: VideoConfig = VideoConfig(),
    val teleprompterConfig: TeleprompterConfig = TeleprompterConfig(),
    val scenes: List<StoryboardScene> = emptyList(),
    val lastExportedMp4Path: String? = null,
    val isDraft: Boolean = true
)

data class ScriptMetrics(
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val paragraphCount: Int = 0,
    val sentenceCount: Int = 0,
    val dialogueCount: Int = 0,
    val transitionMarkerCount: Int = 0,
    val estimatedDurationSec: Float = 0f
)

data class ProjectTemplate(
    val id: String,
    val titleAr: String,
    val descriptionAr: String,
    val categoryAr: String,
    val visualStyle: VisualStyle,
    val aspectRatio: AspectRatio,
    val voiceProfile: VoiceProfile,
    val sampleScript: String
)

sealed interface RenderProgressState {
    data object Idle : RenderProgressState
    data class PreparingAudio(
        val currentScene: Int,
        val totalScenes: Int,
        val message: String
    ) : RenderProgressState
    data class RenderingVideo(
        val currentScene: Int,
        val totalScenes: Int,
        val progress: Float,
        val etaSeconds: Int?,
        val message: String
    ) : RenderProgressState
    data class Completed(
        val outputPath: String,
        val durationSec: Float,
        val fileSizeBytes: Long,
        val warnings: List<String> = emptyList()
    ) : RenderProgressState
    data class Error(val message: String) : RenderProgressState
    data object Cancelled : RenderProgressState
}
