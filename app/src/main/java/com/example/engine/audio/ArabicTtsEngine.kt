package com.example.engine.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.domain.model.ArabicDialect
import com.example.domain.model.VoiceConfig
import com.example.domain.model.VoiceProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

data class TtsEngineStatus(
    val isReady: Boolean = false,
    val arabicAvailable: Boolean = false,
    val englishAvailable: Boolean = false,
    val installedArabicVoicesCount: Int = 0,
    val installedVoiceNames: List<String> = emptyList(),
    val statusMessageAr: String = "جارٍ تهيئة محرك النطق..."
)

data class SynthesizedAudioResult(
    val wavFile: File,
    val durationSec: Float,
    val waveformPeaks: List<Float>
)

class ArabicTtsEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val initSignal = CompletableDeferred<Boolean>()

    private val _status = MutableStateFlow(TtsEngineStatus())
    val status: StateFlow<TtsEngineStatus> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val pendingUtterances = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private var previewDoneCallback: (() -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { initStatus ->
                if (initStatus == TextToSpeech.SUCCESS) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId?.startsWith("preview_") == true) {
                                _isSpeaking.value = true
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId?.startsWith("preview_") == true) {
                                _isSpeaking.value = false
                                previewDoneCallback?.invoke()
                            }
                            utteranceId?.let { pendingUtterances.remove(it)?.complete(true) }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId?.startsWith("preview_") == true) {
                                _isSpeaking.value = false
                                previewDoneCallback?.invoke()
                            }
                            utteranceId?.let { pendingUtterances.remove(it)?.complete(false) }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId?.startsWith("preview_") == true) {
                                _isSpeaking.value = false
                                previewDoneCallback?.invoke()
                            }
                            utteranceId?.let { pendingUtterances.remove(it)?.complete(false) }
                        }
                    })
                    refreshCapabilities()
                    if (!initSignal.isCompleted) initSignal.complete(true)
                } else {
                    _status.value = TtsEngineStatus(
                        isReady = false,
                        statusMessageAr = "تعذر تشغيل محرك النطق على هذا الجهاز. يمكنك رفع ملف تعليق صوتي جاهز."
                    )
                    if (!initSignal.isCompleted) initSignal.complete(false)
                }
            }
        } catch (_: Throwable) {
            _status.value = TtsEngineStatus(
                isReady = false,
                statusMessageAr = "محرك النطق غير متوفر في هذه البيئة."
            )
            if (!initSignal.isCompleted) initSignal.complete(false)
        }
    }

    fun refreshCapabilities() {
        val engine = tts ?: return
        val arAvail = runCatching {
            val res = engine.isLanguageAvailable(Locale.forLanguageTag("ar"))
            res >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)

        val enAvail = runCatching {
            val res = engine.isLanguageAvailable(Locale.US)
            res >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)

        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val arVoices = voices.filter { it.locale.language.equals("ar", ignoreCase = true) }

        val message = when {
            arAvail && arVoices.isNotEmpty() ->
                "محرك النطق جاهز (${arVoices.size} صوت عربي متاح على الجهاز)"
            arAvail ->
                "اللغة العربية مدعومة عبر محرك النطق بالنظام"
            enAvail ->
                "حزمة الصوت العربي غير مثبتة حالياً؛ الإنجليزية متاحة ويمكنك تثبيت الصوت العربي مجاناً من إعدادات النظام."
            else ->
                "لا توجد أصوات نطق مثبتة حالياً. يمكنك تثبيت حزمة الصوت من الإعدادات أو رفع تسجيلك الصوتي."
        }

        _status.value = TtsEngineStatus(
            isReady = true,
            arabicAvailable = arAvail,
            englishAvailable = enAvail,
            installedArabicVoicesCount = arVoices.size,
            installedVoiceNames = arVoices.map { it.name }.take(8),
            statusMessageAr = message
        )
    }

    suspend fun previewVoice(
        text: String,
        voiceConfig: VoiceConfig,
        onFinished: () -> Unit = {}
    ): Boolean {
        val ready = withTimeoutOrNull(3000L) { initSignal.await() } ?: false
        val engine = tts
        if (!ready || engine == null) {
            onFinished()
            return false
        }

        stopPreview()
        previewDoneCallback = onFinished
        applyVoiceConfig(engine, text, voiceConfig)

        val utteranceId = "preview_${UUID.randomUUID()}"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, voiceConfig.narrationVolume.coerceIn(0f, 1f))
        }
        val spoken = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        return spoken == TextToSpeech.SUCCESS
    }

    fun stopPreview() {
        runCatching { tts?.stop() }
        _isSpeaking.value = false
    }

    suspend fun synthesizeSceneToWav(
        text: String,
        voiceConfig: VoiceConfig,
        outputWavFile: File
    ): SynthesizedAudioResult? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val ready = withTimeoutOrNull(4000L) { initSignal.await() } ?: false
        val engine = tts
        if (!ready || engine == null) return@withContext null

        outputWavFile.parentFile?.mkdirs()
        if (outputWavFile.exists()) outputWavFile.delete()

        val hasArabicChars = text.any { it in '\u0600'..'\u06FF' }
        if (hasArabicChars && !_status.value.arabicAvailable) {
            return@withContext null
        }

        applyVoiceConfig(engine, text, voiceConfig)

        val utteranceId = "synth_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Boolean>()
        pendingUtterances[utteranceId] = deferred

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.synthesizeToFile(text, params, outputWavFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            pendingUtterances.remove(utteranceId)
            return@withContext null
        }

        val timeoutMs = max(15_000L, text.length * 140L)
        val ok = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        pendingUtterances.remove(utteranceId)

        if (!ok || !outputWavFile.exists() || outputWavFile.length() <= 44L) {
            return@withContext null
        }

        val (durationSec, peaks) = inspectWavFile(outputWavFile)
        if (durationSec <= 0.05f) return@withContext null

        SynthesizedAudioResult(
            wavFile = outputWavFile,
            durationSec = durationSec,
            waveformPeaks = peaks
        )
    }

    private fun applyVoiceConfig(engine: TextToSpeech, text: String, config: VoiceConfig) {
        val profile = VoiceProfile.fromId(config.voiceId)
        val hasArabic = text.any { it in '\u0600'..'\u06FF' }
        val targetLocale = if (hasArabic) {
            resolveBestArabicLocale(engine, config.dialect)
        } else {
            Locale.US
        }

        runCatching { engine.language = targetLocale }

        // Match real installed Voice object if available on device
        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val matchingVoice = selectBestDeviceVoice(voices, targetLocale, profile)
        if (matchingVoice != null) {
            runCatching { engine.voice = matchingVoice }
        }

        val effectivePitch = (profile.basePitch * config.pitch).coerceIn(0.55f, 1.65f)
        val effectiveRate = (profile.baseRateMultiplier * config.speed).coerceIn(0.5f, 2.2f)
        engine.setPitch(effectivePitch)
        engine.setSpeechRate(effectiveRate)
    }

    private fun resolveBestArabicLocale(engine: TextToSpeech, dialect: ArabicDialect): Locale {
        val preferred = Locale.forLanguageTag(dialect.bcp47Tag)
        val prefAvail = runCatching { engine.isLanguageAvailable(preferred) }.getOrDefault(-1)
        if (prefAvail >= TextToSpeech.LANG_AVAILABLE) return preferred

        val fallback = Locale.forLanguageTag("ar")
        val arAvail = runCatching { engine.isLanguageAvailable(fallback) }.getOrDefault(-1)
        if (arAvail >= TextToSpeech.LANG_AVAILABLE) return fallback

        return Locale("ar", "SA")
    }

    private fun selectBestDeviceVoice(
        voices: List<Voice>,
        targetLocale: Locale,
        profile: VoiceProfile
    ): Voice? {
        val langMatches = voices.filter {
            it.locale.language.equals(targetLocale.language, ignoreCase = true)
        }
        if (langMatches.isEmpty()) return null

        val countryMatches = langMatches.filter {
            it.locale.country.equals(targetLocale.country, ignoreCase = true)
        }.ifEmpty { langMatches }

        // Prefer offline / non-network-required voices when exporting, or matching gender token
        val tokenMatched = countryMatches.firstOrNull { voice ->
            val lowerName = voice.name.lowercase()
            profile.preferredVoiceTokens.any { token -> lowerName.contains(token) }
        }
        return tokenMatched ?: countryMatches.firstOrNull { !it.isNetworkConnectionRequired }
            ?: countryMatches.firstOrNull()
    }

    fun createInstallTtsDataIntent(): Intent {
        return Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun inspectWavFile(file: File, peakCount: Int = 32): Pair<Float, List<Float>> {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return 0f to emptyList()
                val header = ByteArray(44)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt().coerceAtLeast(1)
                val sampleRate = bb.getInt(24).coerceAtLeast(8000)
                val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(8)
                val dataBytes = (raf.length() - 44L).coerceAtLeast(0L)
                val bytesPerSample = (bitsPerSample / 8) * channels
                val totalSamples = dataBytes / bytesPerSample.coerceAtLeast(1)
                val durationSec = totalSamples.toFloat() / sampleRate.toFloat()

                val peaks = MutableList(peakCount) { 0.1f }
                if (bitsPerSample == 16 && totalSamples > 0) {
                    val stepSamples = max(1L, totalSamples / peakCount)
                    val buf = ByteArray(2)
                    for (i in 0 until peakCount) {
                        var maxAmp = 0
                        val startSample = i * stepSamples
                        val probeCount = minOf(32L, stepSamples)
                        val innerStep = max(1L, stepSamples / probeCount)
                        var s = 0L
                        while (s < stepSamples) {
                            val offset = 44L + (startSample + s) * bytesPerSample
                            if (offset + 2 <= raf.length()) {
                                raf.seek(offset)
                                raf.readFully(buf)
                                val sample = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort().toInt()
                                maxAmp = max(maxAmp, abs(sample))
                            }
                            s += innerStep
                        }
                        peaks[i] = (maxAmp / 32768f).coerceIn(0.08f, 1.0f)
                    }
                }
                durationSec to peaks
            }
        }.getOrElse { 0f to emptyList() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
