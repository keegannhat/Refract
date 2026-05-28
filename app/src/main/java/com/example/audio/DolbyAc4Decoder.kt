package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object DolbyAc4Decoder {

    data class DecodedMetadata(
        val mimeType: String,
        val channelCount: Int,
        val sampleRate: Int,
        val durationUs: Long,
        val profile: String,
        val isSimulated: Boolean = false
    )

    /**
     * Inspects a file to retrieve its audio tracks and profile format.
     */
    fun extractMetadata(context: Context, fileUri: Uri): DecodedMetadata {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, fileUri, null)
            val trackCount = extractor.trackCount
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                
                if (mime.contains("ac4", ignoreCase = true) || mime.contains("dolby-ac4", ignoreCase = true)) {
                    val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else {
                        2
                    }
                    val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } else {
                        48000
                    }
                    val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION)
                    } else {
                        0L
                    }
                    
                    val profile = if (channels == 2) {
                        "IMS (Immersive Stereo / Binaural)"
                    } else {
                        "L4 (Multichannel Surround, ${channels}ch)"
                    }

                    return DecodedMetadata(
                        mimeType = mime,
                        channelCount = channels,
                        sampleRate = sampleRate,
                        durationUs = durationUs,
                        profile = profile
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            extractor.release()
        }

        // Default or fallback (simulate parsing standard media file or generate a mock for simulation)
        return DecodedMetadata(
            mimeType = "audio/ac4",
            channelCount = 6,
            sampleRate = 48000,
            durationUs = 10_000_000L, // 10 seconds
            profile = "L4 (Multichannel Surround, 6ch) - [SIMULATION FALLBACK]",
            isSimulated = true
        )
    }

    /**
     * Checks if the device has a native decoder capable of decoding Dolby AC-4 format.
     */
    fun checkAc4Support(): DecoderSupportInfo {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val ac4Decoders = mutableListOf<String>()
        val allAudioCodecs = mutableListOf<CodecDetail>()

        for (info in codecList.codecInfos) {
            val supportedTypes = info.supportedTypes
            for (type in supportedTypes) {
                if (type.startsWith("audio/", ignoreCase = true)) {
                    var maxChannels = 0
                    var sampleRates = emptyList<Int>()
                    try {
                        val caps = info.getCapabilitiesForType(type)
                        val audioCaps = caps.audioCapabilities
                        if (audioCaps != null) {
                            maxChannels = audioCaps.maxInputChannelCount
                            sampleRates = audioCaps.supportedSampleRates?.toList() ?: emptyList()
                        }
                    } catch (e: Exception) {}

                    allAudioCodecs.add(
                        CodecDetail(
                            name = info.name,
                            mimeType = type,
                            isEncoder = info.isEncoder,
                            maxChannels = maxChannels,
                            supportedSampleRates = sampleRates
                        )
                    )
                }

                if (!info.isEncoder && (type.equals("audio/ac4", ignoreCase = true) || 
                    type.equals("audio/dolby-ac4", ignoreCase = true))) {
                    ac4Decoders.add(info.name)
                }
            }
        }

        return DecoderSupportInfo(
            hasAc4Decoder = ac4Decoders.isNotEmpty(),
            ac4DecoderNames = ac4Decoders,
            availableCodecs = allAudioCodecs.distinctBy { it.name }
        )
    }

    /**
     * Decodes Dolby AC-4 using MediaCodec if supported, or falls back to synthetic high-fidelity 
     * multichannel audio simulation.
     */
    suspend fun decode(
        context: Context,
        inputUri: Uri,
        outputPcmFile: File,
        isSimulationEnabled: Boolean,
        onProgress: (Float) -> Unit,
        onStatusUpdate: (String) -> Unit
    ): DecodedMetadata = withContext(Dispatchers.IO) {
        val supportInfo = checkAc4Support()
        val usesSimulation = isSimulationEnabled || !supportInfo.hasAc4Decoder

        if (usesSimulation) {
            onStatusUpdate("Initializing high-fidelity simulation engine...")
            val simulatedMetadata = extractMetadata(context, inputUri)
            simulateMultichannelPcm(outputPcmFile, simulatedMetadata.channelCount, simulatedMetadata.sampleRate, onProgress, onStatusUpdate)
            return@withContext simulatedMetadata.copy(isSimulated = true)
        }

        // REAL DECODING PATH
        onStatusUpdate("Extracting Dolby AC-4 container...")
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var bos: BufferedOutputStream? = null
        
        try {
            extractor.setDataSource(context, inputUri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            var mime: String? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.contains("ac4", ignoreCase = true) || trackMime.contains("dolby-ac4", ignoreCase = true)) {
                    trackIndex = i
                    format = trackFormat
                    mime = trackMime
                    break
                }
            }

            if (trackIndex == -1 || format == null || mime == null) {
                throw IOException("No Dolby AC-4 audio tracks patterns found in file.")
            }

            extractor.selectTrack(trackIndex)
            
            onStatusUpdate("Configuring native Dolby AC-4 decoder...")
            // Create decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            bos = BufferedOutputStream(FileOutputStream(outputPcmFile))
            // Placeholder WAV header (will update later since size is unknown)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            
            WavHelper.writeWavHeader(channelCount, sampleRate, 16, 0, bos)

            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()
            
            var isInputEos = false
            var isOutputEos = false
            var totalDataBytes = 0L
            
            onStatusUpdate("Decoding Audio Frame Buffer...")

            while (!isOutputEos && coroutineContext.isActive) {
                if (!isInputEos) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = inputBuffers[inputBufferIndex]
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                presentationTimeUs,
                                0
                            )
                            extractor.advance()
                            
                            val progress = if (durationUs > 0) presentationTimeUs.toFloat() / durationUs else 0f
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        codec.getOutputBuffer(outputBufferIndex)
                    } else {
                        outputBuffers[outputBufferIndex]
                    }

                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        
                        val pcmChunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmChunk)
                        bos.write(pcmChunk)
                        totalDataBytes += bufferInfo.size
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEos = true
                    }
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Decoder channel/sample rate might change
                }
            }

            bos.flush()
            bos.close()
            bos = null

            // Write actual file size into WAV header
            onStatusUpdate("Finalizing exported structure...")
            WavHelper.updateWavHeaderSizes(outputPcmFile, totalDataBytes)

            val profile = if (channelCount == 2) {
                "IMS (Immersive Stereo / Binaural)"
            } else {
                "L4 (Multichannel Surround, ${channelCount}ch)"
            }

            return@withContext DecodedMetadata(
                mimeType = mime,
                channelCount = channelCount,
                sampleRate = sampleRate,
                durationUs = durationUs,
                profile = profile,
                isSimulated = false
            )

        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try { codec?.stop(); codec?.release() } catch (e: Exception) {}
            try { bos?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Simulation method: Synthesizes high-fidelity 5.1 multichannel audio with separate panning frequencies
     * to verify the splitting and downmixing tools work beautifully.
     */
    private suspend fun simulateMultichannelPcm(
        outputFile: File,
        channelCount: Int,
        sampleRate: Int,
        onProgress: (Float) -> Unit,
        onStatusUpdate: (String) -> Unit
    ) {
        val durationSeconds = 10
        val totalSamples = sampleRate * durationSeconds
        val bytesPerSample = 2 // 16-bit PCM
        val frameSize = channelCount * bytesPerSample
        val totalDataSize = totalSamples.toLong() * frameSize

        onStatusUpdate("Synthesizing multi-channel sound stage components...")

        FileOutputStream(outputFile).use { fos ->
            // Write Wav Header
            WavHelper.writeWavHeader(channelCount, sampleRate, 16, totalDataSize, fos)

            // Split into buffer iterations
            val bufferFrames = 1024
            val buffer = ByteBuffer.allocate(bufferFrames * frameSize).order(ByteOrder.LITTLE_ENDIAN)

            // Define individual panning frequencies for distinct channels
            // 5.1 sequence frequencies: L=440Hz, R=554Hz, C=659Hz, LFE=50Hz, Ls=880Hz, Rs=1108Hz
            // Stereo sequence frequencies: L=440Hz, R=880Hz
            val freqs = when (channelCount) {
                1 -> doubleArrayOf(440.0)
                2 -> doubleArrayOf(440.0, 880.0)
                6 -> doubleArrayOf(440.0, 554.0, 659.0, 50.0, 880.0, 1108.0)
                else -> DoubleArray(channelCount) { 440.0 * (1.0 + (it * 0.2)) }
            }

            var frameCount = 0
            while (frameCount < totalSamples) {
                buffer.clear()
                val chunkFrames = minOf(bufferFrames, totalSamples - frameCount)
                
                for (f in 0 until chunkFrames) {
                    val sampleIndex = frameCount + f
                    val t = sampleIndex.toDouble() / sampleRate
                    
                    for (c in 0 until channelCount) {
                        val freq = freqs[c]
                        // Generate sine amplitude wave
                        var amp = sin(2.0 * Math.PI * freq * t)
                        
                        // LFE is a sub-bass LFE LFE sweep
                        if (c == 3 && channelCount == 6) {
                            amp = sin(2.0 * Math.PI * (50.5 + 20 * sin(t)) * t)
                        }
                        
                        // Scale amplitude
                        val scaled = (amp * 16384.0).toInt().coerceIn(-32768, 32767).toShort()
                        buffer.putShort(scaled)
                    }
                }
                
                fos.write(buffer.array(), 0, chunkFrames * frameSize)
                frameCount += chunkFrames
                onProgress(frameCount.toFloat() / totalSamples)
                
                if (frameCount % (sampleRate * 2) == 0) {
                    onStatusUpdate("Rendering immersive frame chunk ${frameCount / sampleRate}s...")
                }
            }
        }
    }
}
