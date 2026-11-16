package com.example.domain.engine

import com.example.domain.model.AspectRatio
import com.example.domain.model.ProjectTemplate
import com.example.domain.model.VisualStyle
import com.example.domain.model.VoiceProfile

object TemplatesCatalog {
    val templates: List<ProjectTemplate> = listOf(
        ProjectTemplate(
            id = "doc_history",
            titleAr = "فيلم وثائقي معرفي",
            descriptionAr = "قالب متكامل للتقارير الوثائقية والسرد التاريخي والعلمي بصوت عميق.",
            categoryAr = "وثائقي",
            visualStyle = VisualStyle.DOCUMENTARY,
            aspectRatio = AspectRatio.RATIO_16_9,
            voiceProfile = VoiceProfile.PRO_DOCUMENTARY_M,
            sampleScript = """
                في قلب الصحراء العربية، حيث تلتقي الرمال الذهبية بسماء لا تعرف الحدود، بدأت حكاية حضارة صنعت من النجوم خرائطها.

                لم تكن الرحلة مجرد عبورٍ للمسافات، بل كانت بحثاً مستمراً عن المعرفة والابتكار في الفلك والعمارة والعلوم.

                واليوم، تعود هذه الحكايات لتلهم جيلاً جديداً يصنع مستقبله بأدوات العصر الحديث، محافظاً على أصالة الجذور وروح الاكتشاف.
            """.trimIndent()
        ),
        ProjectTemplate(
            id = "social_reel",
            titleAr = "ريلز سوشيال ميديا سريع",
            descriptionAr = "مقطع عمودي 9:16 بإيقاع سريع وترجمة بارزة لمنصات تيك توك وريلز.",
            categoryAr = "سوشيال ميديا",
            visualStyle = VisualStyle.SOCIAL_MEDIA,
            aspectRatio = AspectRatio.RATIO_9_16,
            voiceProfile = VoiceProfile.YOUNG_ENERGETIC_M,
            sampleScript = """
                هل تعلم أن ثلاث دقائق من التخطيط الصباحي قد تغيّر إنتاجية يومك بالكامل؟

                أولاً: حدد أهم هدف واحد فقط قبل فتح هاتفك.
                ثانياً: قسّم عملك إلى جلسات مركّزة لا تتجاوز ٢٥ دقيقة.

                ابدأ اليوم بخطوة بسيطة، وراقب الفرق الكبير خلال أسبوع واحد!
            """.trimIndent()
        ),
        ProjectTemplate(
            id = "storytelling_night",
            titleAr = "قصة أدبية ملهمة",
            descriptionAr = "سرد دافئ وهادئ مناسب للقصص الإنسانية والبودكاست المرئي.",
            categoryAr = "قصصي",
            visualStyle = VisualStyle.STORYTELLING,
            aspectRatio = AspectRatio.RATIO_16_9,
            voiceProfile = VoiceProfile.SOFT_STORYTELLER_F,
            sampleScript = """
                كان المقهى الصغير في آخر الشارع يحتفظ بكل أسرار المدينة القديمة، وبرائحة القهوة الممزوجة بأصوات المطر.

                الراوي: جلس المسافر قرب النافذة، وفتح دفتره القديم ليكتب السطر الأول.
                المسافر: "أحياناً نعود إلى الأماكن القديمة لا لنبحث عمّا مضى، بل لنفهم من أصبحنا اليوم."

                ومع انعكاس أضواء المساء على الزجاج، أدرك أن أجمل الفصول لم تُكتب بعد.
            """.trimIndent()
        ),
        ProjectTemplate(
            id = "educational_tech",
            titleAr = "شرح تعليمي مبسط",
            descriptionAr = "تقديم منظم للمفاهيم التقنية والعلمية مع وضوح عالٍ في النطق والترجمة.",
            categoryAr = "تعليمي",
            visualStyle = VisualStyle.EDUCATIONAL,
            aspectRatio = AspectRatio.RATIO_16_9,
            voiceProfile = VoiceProfile.CALM_EDUCATIONAL_F,
            sampleScript = """
                كيف تعمل الشبكات العصبية في معالجة اللغة العربية؟

                الخطوة الأولى هي تقسيم النص إلى وحدات صغيرة تفهم السياق والمعنى داخل الجملة.
                بعد ذلك، تحوّل النماذج هذه الكلمات إلى تمثيلات رياضية دقيقة تساعد في فهم النبرة والعاطفة.

                وبهذه الطريقة، يمكن تحويل أي نص مكتوب إلى تجربة بصرية وصوتية متكاملة.
            """.trimIndent()
        ),
        ProjectTemplate(
            id = "news_brief",
            titleAr = "نشرة أخبار وتقارير",
            descriptionAr = "تصميم بث إخباري احترافي مع شرائط عناوين واضحة وصوت إعلامي.",
            categoryAr = "إخباري",
            visualStyle = VisualStyle.NEWS,
            aspectRatio = AspectRatio.RATIO_16_9,
            voiceProfile = VoiceProfile.NEWS_PRESENTER_M,
            sampleScript = """
                تقرير خاص: تسارع ملحوظ في مشاريع التحول الرقمي وصناعة المحتوى الذكي في المنطقة العربية خلال العام الجاري.

                وتشير البيانات الحديثة إلى نمو الاعتماد على أدوات الإنتاج المحلي المستقلة لدعم المبدعين والمؤسسات التعليمية.

                نواصل معكم أبرز التفاصيل والتحليلات في هذا العرض الموجز.
            """.trimIndent()
        ),
        ProjectTemplate(
            id = "luxury_brand",
            titleAr = "إعلان فاخر وعلامات تجارية",
            descriptionAr = "هوية بصرية ذهبية راقية لعروض المنتجات الفاخرة والهويات البصرية.",
            categoryAr = "فاخر",
            visualStyle = VisualStyle.LUXURY,
            aspectRatio = AspectRatio.RATIO_4_5,
            voiceProfile = VoiceProfile.DEEP_CINEMATIC,
            sampleScript = """
                الفخامة الحقيقية لا تصنعها التفاصيل الصاخبة، بل الدقة المتناهية في كل لحظة.

                تصميم يجمع بين بساطة الخط العربي الأصيل، وجودة الحرفية التي تدوم لسنوات.

                اختر التميز الذي يعبّر عنك.
            """.trimIndent()
        )
    )
}
