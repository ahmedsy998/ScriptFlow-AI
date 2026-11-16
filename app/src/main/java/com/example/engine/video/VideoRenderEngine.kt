package com.example.engine.video

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.domain.model.RenderProgressState
import com.example.domain.model.SceneMediaType
import com.example.domain.model.StoryboardScene
import com.example.domain.model.StudioProject
import com.example.domain.model.VideoResolution
import com.example.engine.audio.ArabicTtsEngine
import com.example.engine.audio.AudioMixerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DeviceVideoCapabilities(
    val supports1080p: Boolean,
    val maxEncoderWidth: Int,
    val maxEncoderHeight: Int,
    val encoderName: String,
    val freeStorageMb: Long
)

class VideoRenderEngine(
    private val context: Context,
    private val ttsEngine: ArabicTtsEngine
) {
    private val renderer = ProceduralSceneRenderer(context)

    fun inspectDeviceCapabilities(): DeviceVideoCapabilities {
        var maxW = 1280
        var maxH = 720
        var encoderName = "H.264 AVC"

        runCatching {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val avcEncoder = codecList.codecInfos.firstOrNull { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
            }
            if (avcEncoder != null) {
                encoderName = avcEncoder.name
                val caps = avcEncoder.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                if (caps != null) {
                    maxW = caps.supportedWidths.upper
                    maxH = caps.supportedHeights.upper
                }
            }
        }

        val freeBytes = runCatching { context.filesDir.usableSpace }.getOrDefault(0L)
        return DeviceVideoCapabilities(
            supports1080p = maxW >= 1920 && maxH >= 1080,
            maxEncoderWidth = maxW,
            maxEncoderHeight = maxH,
            encoderName = encoderName,
            freeStorageMb = freeBytes / (1024L * 1024L)
        )
    }

    fun renderProjectToMp4(project: StudioProject): Flow<RenderProgressState> = flow {
        val scenes = project.scenes
        if (scenes.isEmpty()) {
            emit(RenderProgressState.Error("لا توجد مشاهد للتحويل. أضف نصاً وأنشئ لوحة المشاهد أولاً."))
            return@flow
        }

        val workDir = File(context.cacheDir, "render_${project.id}_${System.currentTimeMillis()}")
        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val tempVideoOnly = File(workDir, "video_track.mp4")
        val finalMp4 = File(exportsDir, "ScriptFlow_${project.id.take(8)}_${System.currentTimeMillis()}.mp4")
        val warnings = mutableListOf<String>()

        try {
            workDir.mkdirs()

            // 1. Check resolution capabilities honestly
            val caps = inspectDeviceCapabilities()
            val requestedRes = project.videoConfig.resolution
            val effectiveRes = if (requestedRes == VideoResolution.FHD_1080P && !caps.supports1080p) {
                warnings.add("تم التصدير بدقة 720p لأن مشفّر الفيديو في الجهاز يدعم حتى ${caps.maxEncoderWidth}x${caps.maxEncoderHeight}.")
                VideoResolution.HD_720P
            } else {
                requestedRes
            }

            val (rawW, rawH) = project.videoConfig.aspectRatio.dimensions(effectiveRes)
            val width = (rawW / 16) * 16
            val height = (rawH / 16) * 16

            // 2. Sequential TTS synthesis & audio preparation per scene
            val preparedScenes = mutableListOf<StoryboardScene>()
            val sceneAudioInputs = mutableListOf<AudioMixerEngine.SceneAudioInput>()
            var ttsFailureCount = 0

            for ((index, scene) in scenes.withIndex()) {
                currentCoroutineContext().ensureActive()
                emit(
                    RenderProgressState.PreparingAudio(
                        currentScene = index + 1,
                        totalScenes = scenes.size,
                        message = "جارٍ تجهيز الصوت للمشهد ${index + 1} من ${scenes.size}..."
                    )
                )

                var wavFile: File? = null
                var finalDuration = scene.effectiveDurationSec

                if (scene.customNarrationUri.isNullOrBlank() &&
                    project.voiceConfig.customFullNarrationUri.isNullOrBlank() &&
                    scene.narrationText.isNotBlank()
                ) {
                    val outWav = File(workDir, "tts_scene_$index.wav")
                    val synth = ttsEngine.synthesizeSceneToWav(scene.narrationText, project.voiceConfig, outWav)
                    if (synth != null) {
                        wavFile = synth.wavFile
                        val pauseSec = project.voiceConfig.paragraphPauseMs / 1000f
                        // Expand scene duration if actual spoken audio is longer so subtitles never cut off
                        finalDuration = max(
                            scene.effectiveDurationSec,
                            synth.durationSec + pauseSec
                        )
                    } else {
                        ttsFailureCount++
                    }
                }

                preparedScenes.add(
                    scene.copy(
                        customDurationSec = finalDuration,
                        synthesizedWavPath = wavFile?.absolutePath
                    )
                )
                sceneAudioInputs.add(
                    AudioMixerEngine.SceneAudioInput(
                        durationSec = finalDuration,
                        narrationWavFile = wavFile,
                        customNarrationUri = scene.customNarrationUri
                    )
                )
            }

            if (ttsFailureCount == scenes.size &&
                project.voiceConfig.customFullNarrationUri.isNullOrBlank() &&
                project.videoConfig.backgroundMusicUri.isNullOrBlank()
            ) {
                warnings.add("لم يتم توليد ملف صوتي لأن حزمة النطق غير متاحة للتصدير على الجهاز؛ تم تصدير الفيديو مع الترجمة المتزامنة.")
            }

            val mixedAudio = AudioMixerEngine.mixAndEncodeAudioTrack(
                context = context,
                workDir = workDir,
                scenes = sceneAudioInputs,
                fullNarrationUri = project.voiceConfig.customFullNarrationUri,
                backgroundMusicUri = project.videoConfig.backgroundMusicUri,
                narrationVolume = project.videoConfig.narrationVolume * project.voiceConfig.narrationVolume,
                musicVolume = project.videoConfig.musicVolume,
                fadeInSec = project.videoConfig.musicFadeInSec,
                fadeOutSec = project.videoConfig.musicFadeOutSec
            )

            // 3. Encode Video Track sequentially scene-by-scene
            val fps = 24
            val totalDurationSec = preparedScenes.sumOf { it.effectiveDurationSec.toDouble() }.toFloat()
            val totalFrames = max(1, (totalDurationSec * fps).roundToInt())
            val logoBitmap = project.videoConfig.logoUri?.let { loadScaledBitmap(Uri.parse(it), width / 4, height / 4) }
            val allSentences = preparedScenes.map { it.originalSegment }

            val renderStartMs = System.currentTimeMillis()
            encodeVideoTrack(
                width = width,
                height = height,
                fps = fps,
                bitrate = effectiveRes.bitrateMbps,
                scenes = preparedScenes,
                totalFrames = totalFrames,
                logoBitmap = logoBitmap,
                project = project,
                allSentences = allSentences,
                outVideoFile = tempVideoOnly,
                onFrameProgress = { sceneIndex, frameIndex ->
                    val fraction = (frameIndex.toFloat() / totalFrames.toFloat()).coerceIn(0f, 0.98f)
                    val elapsedSec = (System.currentTimeMillis() - renderStartMs) / 1000f
                    val eta = if (fraction > 0.04f) {
                        ((elapsedSec / fraction) * (1f - fraction)).roundToInt().coerceAtLeast(1)
                    } else null
                    emit(
                        RenderProgressState.RenderingVideo(
                            currentScene = sceneIndex + 1,
                            totalScenes = preparedScenes.size,
                            progress = fraction,
                            etaSeconds = eta,
                            message = "جارٍ رندر المشهد ${sceneIndex + 1} من ${preparedScenes.size} (${(fraction * 100).roundToInt()}%)"
                        )
                    )
                }
            )
            logoBitmap?.recycle()

            // 4. Mux video + audio tracks into final MP4
            emit(
                RenderProgressState.RenderingVideo(
                    currentScene = preparedScenes.size,
                    totalScenes = preparedScenes.size,
                    progress = 0.99f,
                    etaSeconds = 1,
                    message = "جارٍ دمج الصوت والصورة وإخراج ملف MP4 النهائي..."
                )
            )

            muxVideoAndAudio(
                videoMp4 = tempVideoOnly,
                audioM4a = mixedAudio.aacFile.takeIf { mixedAudio.hasAnyAudio && it.exists() },
                outputMp4 = finalMp4
            )

            emit(
                RenderProgressState.Completed(
                    outputPath = finalMp4.absolutePath,
                    durationSec = totalDurationSec,
                    fileSizeBytes = finalMp4.length(),
                    warnings = warnings
                )
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            runCatching { finalMp4.delete() }
            emit(RenderProgressState.Cancelled)
            throw ce
        } catch (t: Throwable) {
            runCatching { finalMp4.delete() }
            emit(RenderProgressState.Error(t.localizedMessage ?: "حدث خطأ أثناء تصدير الفيديو"))
        } finally {
            runCatching { workDir.deleteRecursively() }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun encodeVideoTrack(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        scenes: List<StoryboardScene>,
        totalFrames: Int,
        logoBitmap: Bitmap?,
        project: StudioProject,
        allSentences: List<String>,
        outVideoFile: File,
        onFrameProgress: suspend (sceneIndex: Int, frameIndex: Int) -> Unit
    ) {
        val (encoder, colorFormat) = createConfiguredAvcEncoder(width, height, fps, bitrate)
        val muxer = MediaMuxer(outVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        val argbPixels = IntArray(width * height)
        val yuvBuffer = ByteArray(width * height * 3 / 2)
        val bufferInfo = MediaCodec.BufferInfo()

        var globalFrameIndex = 0

        try {
            encoder.start()

            for ((sceneIndex, scene) in scenes.withIndex()) {
                currentCoroutineContext().ensureActive()

                val sceneFrames = max(1, (scene.effectiveDurationSec * fps).roundToInt())
                // Load only current scene bitmaps / video retriever, release immediately after scene!
                val imageBitmaps = mutableListOf<Bitmap?>()
                val videoRetrievers = mutableListOf<Pair<Int, MediaMetadataRetriever>>()

                scene.assets.forEachIndexed { assetIdx, asset ->
                    when (asset.type) {
                        SceneMediaType.USER_IMAGE -> {
                            val bmp = asset.uriString?.let { loadScaledBitmap(Uri.parse(it), width, height) }
                            imageBitmaps.add(bmp)
                        }
                        SceneMediaType.USER_VIDEO -> {
                            val retriever = runCatching {
                                MediaMetadataRetriever().apply {
                                    setDataSource(context, Uri.parse(asset.uriString))
                                }
                            }.getOrNull()
                            if (retriever != null) {
                                videoRetrievers.add(assetIdx to retriever)
                            }
                            imageBitmaps.add(null)
                        }
                        SceneMediaType.PROCEDURAL -> imageBitmaps.add(null)
                    }
                }

                try {
                    for (localFrame in 0 until sceneFrames) {
                        currentCoroutineContext().ensureActive()
                        val sceneProgress = localFrame.toFloat() / sceneFrames.toFloat()
                        val globalProgress = globalFrameIndex.toFloat() / totalFrames.toFloat()

                        // Extract live frame for any user-uploaded video assets at this timestamp
                        val frameBitmaps = imageBitmaps.toMutableList()
                        for ((assetIdx, retriever) in videoRetrievers) {
                            val durationMs = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION
                            )?.toLongOrNull() ?: 4000L
                            val sampleTimeUs = (((localFrame.toFloat() / fps) * 1000f) % durationMs.coerceAtLeast(500L)).toLong() * 1000L
                            val videoFrame = runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    retriever.getScaledFrameAtTime(
                                        sampleTimeUs,
                                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        width,
                                        height
                                    )
                                } else {
                                    retriever.getFrameAtTime(sampleTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                }
                            }.getOrNull()
                            frameBitmaps[assetIdx] = videoFrame
                        }

                        renderer.renderFrame(
                            canvas = canvas,
                            width = width,
                            height = height,
                            scene = scene,
                            sceneProgress = sceneProgress,
                            globalProgress = globalProgress,
                            assetBitmaps = frameBitmaps,
                            logoBitmap = logoBitmap,
                            videoConfig = project.videoConfig,
                            teleprompterConfig = project.teleprompterConfig,
                            allScriptSentences = allSentences,
                            activeSentenceIndex = sceneIndex,
                            isExportRender = true
                        )

                        // Recycle transient extracted video frame bitmaps immediately
                        for ((assetIdx, _) in videoRetrievers) {
                            frameBitmaps[assetIdx]?.recycle()
                        }

                        // Convert ARGB -> YUV420 and queue to MediaCodec
                        frameBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                        val isSemiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                        encodeArgbToYuv420(argbPixels, yuvBuffer, width, height, isSemiPlanar)

                        val inIdx = encoder.dequeueInputBuffer(20_000)
                        if (inIdx >= 0) {
                            val inBuf = encoder.getInputBuffer(inIdx)!!
                            inBuf.clear()
                            inBuf.put(yuvBuffer, 0, min(yuvBuffer.size, inBuf.capacity()))
                            val ptsUs = (globalFrameIndex.toLong() * 1_000_000L) / fps
                            encoder.queueInputBuffer(inIdx, 0, yuvBuffer.size, ptsUs, 0)
                        }

                        drainEncoder(encoder, bufferInfo, muxer, onFormatChanged = { fmt ->
                            trackIndex = muxer.addTrack(fmt)
                            muxer.start()
                            muxerStarted = true
                        }, getTrackIndex = { trackIndex }, isMuxerStarted = { muxerStarted })

                        if (localFrame % 6 == 0) {
                            onFrameProgress(sceneIndex, globalFrameIndex)
                        }
                        globalFrameIndex++
                    }
                } finally {
                    imageBitmaps.forEach { it?.recycle() }
                    videoRetrievers.forEach { (_, ret) -> runCatching { ret.release() } }
                }
            }

            // Signal EOS
            val eosIdx = encoder.dequeueInputBuffer(20_000)
            if (eosIdx >= 0) {
                val ptsUs = (globalFrameIndex.toLong() * 1_000_000L) / fps
                encoder.queueInputBuffer(eosIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }

            var eosReached = false
            while (!eosReached) {
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 20_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            eosReached = true
                        }
                    }
                }
            }
        } finally {
            frameBitmap.recycle()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        onFormatChanged: (MediaFormat) -> Unit,
        getTrackIndex: () -> Int,
        isMuxerStarted: () -> Boolean
    ) {
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!isMuxerStarted()) onFormatChanged(encoder.outputFormat)
                }
                outIdx >= 0 -> {
                    val outBuf = encoder.getOutputBuffer(outIdx)!!
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && isMuxerStarted()) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(getTrackIndex(), outBuf, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                }
            }
        }
    }

    private fun createConfiguredAvcEncoder(
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int
    ): Pair<MediaCodec, Int> {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val avcInfo = codecList.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
        }
        val caps = avcInfo?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val supportedColors = caps?.colorFormats?.toSet().orEmpty()

        val colorFormat = when {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supportedColors ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supportedColors ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = if (avcInfo != null) {
            MediaCodec.createByCodecName(avcInfo.name)
        } else {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return encoder to colorFormat
    }

    private fun encodeArgbToYuv420(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int,
        semiPlanar: Boolean
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        val uOffset = frameSize
        val vOffset = frameSize + frameSize / 4

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yuv[yIndex++] = y.toByte()

                if ((j and 1) == 0 && (i and 1) == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                    if (semiPlanar) {
                        yuv[uvIndex++] = u.toByte()
                        yuv[uvIndex++] = v.toByte()
                    } else {
                        val uvPos = (j / 2) * (width / 2) + (i / 2)
                        yuv[uOffset + uvPos] = u.toByte()
                        yuv[vOffset + uvPos] = v.toByte()
                    }
                }
            }
        }
    }

    private fun muxVideoAndAudio(videoMp4: File, audioM4a: File?, outputMp4: File) {
        if (audioM4a == null || !audioM4a.exists() || audioM4a.length() < 128L) {
            videoMp4.copyTo(outputMp4, overwrite = true)
            return
        }

        val vExtractor = MediaExtractor()
        val aExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            vExtractor.setDataSource(videoMp4.absolutePath)
            vExtractor.selectTrack(0)
            val vOutTrack = muxer.addTrack(vExtractor.getTrackFormat(0))

            aExtractor.setDataSource(audioM4a.absolutePath)
            aExtractor.selectTrack(0)
            val aOutTrack = muxer.addTrack(aExtractor.getTrackFormat(0))

            muxer.start()

            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val size = vExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, vExtractor.sampleTime, vExtractor.sampleFlags)
                muxer.writeSampleData(vOutTrack, buf, info)
                vExtractor.advance()
            }

            while (true) {
                val size = aExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.set(0, size, aExtractor.sampleTime, aExtractor.sampleFlags)
                muxer.writeSampleData(aOutTrack, buf, info)
                aExtractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { vExtractor.release() }
            runCatching { aExtractor.release() }
        }
    }

    private fun loadScaledBitmap(uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > targetW * 2 &&
                boundsOpts.outHeight / sampleSize > targetH * 2
            ) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            }
        }.getOrNull()
    }

    suspend fun saveMp4ToDeviceGallery(mp4File: File, displayTitle: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = displayTitle.replace(Regex("""[^\p{L}\p{N}_\- ]"""), "").ifBlank { "ScriptFlow" }
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "${safeName}_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ScriptFlowAI")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("تعذر إنشاء ملف في معرض الفيديو")

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(mp4File).use { input ->
                        input.copyTo(out)
                    }
                } ?: throw IllegalStateException("تعذر الكتابة إلى معرض الفيديو")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            }
        }
}
