package com.example.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Memory-efficient streaming PCM mixer & AAC (.m4a) encoder.
 * Unified internal stream format: 44,100 Hz, Mono, 16-bit Signed Little-Endian PCM.
 */
object AudioMixerEngine {

    const val TARGET_SAMPLE_RATE = 44_100
    const val TARGET_CHANNELS = 1
    const val AAC_BITRATE = 128_000

    data class SceneAudioInput(
        val durationSec: Float,
        val narrationWavFile: File?,
        val customNarrationUri: String?
    )

    data class MixedAudioOutput(
        val aacFile: File,
        val totalDurationSec: Float,
        val hasAnyAudio: Boolean
    )

    suspend fun mixAndEncodeAudioTrack(
        context: Context,
        workDir: File,
        scenes: List<SceneAudioInput>,
        fullNarrationUri: String?,
        backgroundMusicUri: String?,
        narrationVolume: Float,
        musicVolume: Float,
        fadeInSec: Float,
        fadeOutSec: Float
    ): MixedAudioOutput = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val mixedPcmFile = File(workDir, "mixed_master.pcm")
        val outAacFile = File(workDir, "mixed_master.m4a")
        if (mixedPcmFile.exists()) mixedPcmFile.delete()
        if (outAacFile.exists()) outAacFile.delete()

        val musicPcmFile = backgroundMusicUri?.let { uriStr ->
            File(workDir, "bg_music.pcm").also { target ->
                decodeUriToMonoPcm(context, Uri.parse(uriStr), target)
            }.takeIf { it.exists() && it.length() > 0L }
        }

        val fullNarrPcmFile = fullNarrationUri?.let { uriStr ->
            File(workDir, "full_narr.pcm").also { target ->
                decodeUriToMonoPcm(context, Uri.parse(uriStr), target)
            }.takeIf { it.exists() && it.length() > 0L }
        }

        val totalSamples = scenes.sumOf { (it.durationSec * TARGET_SAMPLE_RATE).roundToInt().toLong() }
            .coerceAtLeast(TARGET_SAMPLE_RATE.toLong())
        val fadeInSamples = (fadeInSec * TARGET_SAMPLE_RATE).roundToInt().coerceAtLeast(0)
        val fadeOutSamples = (fadeOutSec * TARGET_SAMPLE_RATE).roundToInt().coerceAtLeast(0)

        var musicStream = musicPcmFile?.let { DataInputStream(BufferedInputStream(FileInputStream(it))) }
        val fullNarrStream = fullNarrPcmFile?.let { DataInputStream(BufferedInputStream(FileInputStream(it))) }

        var hasAnyAudio = musicPcmFile != null || fullNarrPcmFile != null
        var globalSamplePos = 0L

        DataOutputStream(BufferedOutputStream(FileOutputStream(mixedPcmFile))).use { pcmOut ->
            val chunkShorts = ShortArray(4096)
            val chunkBytes = ByteArray(4096 * 2)

            for ((index, scene) in scenes.withIndex()) {
                coroutineContext.ensureActive()
                val sceneSamples = (scene.durationSec * TARGET_SAMPLE_RATE).roundToInt().coerceAtLeast(1)

                val scenePcmFile: File? = when {
                    !scene.customNarrationUri.isNullOrBlank() -> {
                        val decoded = File(workDir, "scene_custom_$index.pcm")
                        decodeUriToMonoPcm(context, Uri.parse(scene.customNarrationUri), decoded)
                        decoded.takeIf { it.exists() && it.length() > 0L }
                    }
                    scene.narrationWavFile != null && scene.narrationWavFile.exists() -> {
                        val decoded = File(workDir, "scene_tts_$index.pcm")
                        decodeWavToMonoPcm(scene.narrationWavFile, decoded)
                        decoded.takeIf { it.exists() && it.length() > 0L }
                    }
                    else -> null
                }

                if (scenePcmFile != null) hasAnyAudio = true
                val sceneNarrStream = scenePcmFile?.let {
                    DataInputStream(BufferedInputStream(FileInputStream(it)))
                }

                var remaining = sceneSamples
                while (remaining > 0) {
                    coroutineContext.ensureActive()
                    val count = min(remaining, chunkShorts.size)
                    for (i in 0 until count) {
                        val narrSample: Int = when {
                            sceneNarrStream != null -> readNextShortOrZero(sceneNarrStream)
                            fullNarrStream != null -> readNextShortOrZero(fullNarrStream)
                            else -> 0
                        }

                        var musicSample = 0
                        if (musicPcmFile != null && musicStream != null) {
                            musicSample = readLoopingShort(musicStream!!, musicPcmFile) { reopened ->
                                musicStream = reopened
                            }
                        }

                        // Auto-duck music gently when narration is speaking
                        val duckFactor = if (kotlin.math.abs(narrSample) > 250) 0.45f else 1.0f
                        var env = 1.0f
                        if (fadeInSamples > 0 && globalSamplePos < fadeInSamples) {
                            env *= (globalSamplePos.toFloat() / fadeInSamples.toFloat())
                        }
                        val samplesFromEnd = totalSamples - globalSamplePos
                        if (fadeOutSamples > 0 && samplesFromEnd < fadeOutSamples) {
                            env *= (samplesFromEnd.coerceAtLeast(0L).toFloat() / fadeOutSamples.toFloat())
                        }

                        val mixed = (narrSample * narrationVolume) +
                            (musicSample * musicVolume * duckFactor * env)
                        chunkShorts[i] = mixed.roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            .toShort()
                        globalSamplePos++
                    }

                    ByteBuffer.wrap(chunkBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        .put(chunkShorts, 0, count)
                    pcmOut.write(chunkBytes, 0, count * 2)
                    remaining -= count
                }

                sceneNarrStream?.close()
                scenePcmFile?.delete()
            }
        }

        musicStream?.close()
        fullNarrStream?.close()
        musicPcmFile?.delete()
        fullNarrPcmFile?.delete()

        encodePcmToAacM4a(mixedPcmFile, outAacFile)
        mixedPcmFile.delete()

        MixedAudioOutput(
            aacFile = outAacFile,
            totalDurationSec = totalSamples.toFloat() / TARGET_SAMPLE_RATE.toFloat(),
            hasAnyAudio = hasAnyAudio
        )
    }

    private fun readNextShortOrZero(stream: DataInputStream): Int {
        return try {
            val lo = stream.readByte().toInt() and 0xFF
            val hi = stream.readByte().toInt()
            ((hi shl 8) or lo).toShort().toInt()
        } catch (_: EOFException) {
            0
        }
    }

    private fun readLoopingShort(
        current: DataInputStream,
        sourceFile: File,
        onReopen: (DataInputStream) -> Unit
    ): Int {
        return try {
            val lo = current.readByte().toInt() and 0xFF
            val hi = current.readByte().toInt()
            ((hi shl 8) or lo).toShort().toInt()
        } catch (_: EOFException) {
            runCatching { current.close() }
            if (sourceFile.length() < 2L) return 0
            val next = DataInputStream(BufferedInputStream(FileInputStream(sourceFile)))
            onReopen(next)
            readNextShortOrZero(next)
        }
    }

    fun decodeWavToMonoPcm(wavFile: File, outPcmFile: File) {
        runCatching {
            FileInputStream(wavFile).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) < 44) return
                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = bb.getShort(22).toInt().coerceAtLeast(1)
                val sampleRate = bb.getInt(24).coerceAtLeast(8000)
                val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(8)
                if (bitsPerSample != 16) return

                val rawBytes = fis.readBytes()
                val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val frameCount = shortBuf.remaining() / channels
                val mono = ShortArray(frameCount)
                for (i in 0 until frameCount) {
                    var sum = 0
                    for (c in 0 until channels) {
                        sum += shortBuf.get(i * channels + c).toInt()
                    }
                    mono[i] = (sum / channels).toShort()
                }
                val resampled = resampleMono(mono, sampleRate, TARGET_SAMPLE_RATE)
                writeShortsToPcm(resampled, outPcmFile)
            }
        }
    }

    fun decodeUriToMonoPcm(context: Context, uri: Uri, outPcmFile: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) return

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            DataOutputStream(BufferedOutputStream(FileOutputStream(outPcmFile))).use { pcmOut ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFmt = codec.outputFormat
                            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val sb = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val totalShorts = sb.remaining()
                                val frames = totalShorts / channels.coerceAtLeast(1)
                                val mono = ShortArray(frames)
                                for (f in 0 until frames) {
                                    var sum = 0
                                    for (c in 0 until channels) {
                                        sum += sb.get(f * channels + c).toInt()
                                    }
                                    mono[f] = (sum / channels.coerceAtLeast(1)).toShort()
                                }
                                val resampled = resampleMono(mono, sampleRate, TARGET_SAMPLE_RATE)
                                val bytes = ByteArray(resampled.size * 2)
                                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer().put(resampled)
                                pcmOut.write(bytes)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Gracefully skip corrupt or unsupported audio URIs
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun resampleMono(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate || srcRate <= 0) return input
        val outLen = ((input.size.toLong() * dstRate) / srcRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outLen)
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, input.lastIndex)
            val frac = srcPos - idx0
            val sample = input[idx0] * (1.0 - frac) + input[idx1] * frac
            out[i] = sample.roundToInt().toShort()
        }
        return out
    }

    private fun writeShortsToPcm(samples: ShortArray, file: File) {
        val bytes = ByteArray(samples.size * 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        FileOutputStream(file).use { it.write(bytes) }
    }

    private fun encodePcmToAacM4a(pcmFile: File, outM4aFile: File) {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            TARGET_SAMPLE_RATE,
            TARGET_CHANNELS
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = android.media.MediaMuxer(
            outM4aFile.absolutePath,
            android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val readBuf = ByteArray(4096)
        var totalSamplesRead = 0L
        var inputDone = false
        var outputDone = false

        BufferedInputStream(FileInputStream(pcmFile)).use { pcmIn ->
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val maxRead = min(readBuf.size, inBuf.remaining())
                        val read = pcmIn.read(readBuf, 0, maxRead)
                        if (read <= 0) {
                            encoder.queueInputBuffer(
                                inIdx, 0, 0,
                                (totalSamplesRead * 1_000_000L) / TARGET_SAMPLE_RATE,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            inBuf.put(readBuf, 0, read)
                            val ptsUs = (totalSamplesRead * 1_000_000L) / TARGET_SAMPLE_RATE
                            totalSamplesRead += (read / 2)
                            encoder.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIdx >= 0 -> {
                        val encodedBuf = encoder.getOutputBuffer(outIdx)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encodedBuf.position(bufferInfo.offset)
                            encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encodedBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        }

        runCatching { encoder.stop() }
        runCatching { encoder.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}
