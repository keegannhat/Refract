package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object FlacEncoderHelper {

    /**
     * Encodes a mono or stereo WAV (PCM) file into a compressed FLAC file.
     * Uses Android's native MediaCodec audio/flac encoder.
     */
    fun encodeWavToFlac(
        wavFile: File,
        flacFile: File,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android Q (API 29) introduces reliable FLAC encoders, though earlier might have it.
            // If API level is too low, we gracefully return false and fallback to WAV.
            return false
        }

        var codec: MediaCodec? = null
        var fis: FileInputStream? = null
        var fos: BufferedOutputStream? = null

        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channelCount)
            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5) // Medium compression
            
            // Query encoder support
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            fis = FileInputStream(wavFile)
            fis.skip(44) // Skip the WAV header to read pure PCM data
            val totalBytes = wavFile.length() - 44
            var processedBytes = 0L

            fos = BufferedOutputStream(FileOutputStream(flacFile))

            val inputBuffers = codec.inputBuffers
            val outputBuffers = codec.outputBuffers
            val bufferInfo = MediaCodec.BufferInfo()
            
            val pcmFrameSize = channelCount * (bitsPerSample / 8)
            val chunkBuffer = ByteArray(4096 * pcmFrameSize) // chunk of PCM frames
            
            var isInputEof = false
            var isOutputEof = false

            while (!isOutputEof) {
                // Feed input buffers
                if (!isInputEof) {
                    val inputBufIndex = codec.dequeueInputBuffer(10000) // 10ms timeout
                    if (inputBufIndex >= 0) {
                        val inputBuffer = inputBuffers[inputBufIndex]
                        inputBuffer.clear()
                        
                        val bytesToRead = minOf(chunkBuffer.size, inputBuffer.capacity())
                        val bytesRead = fis.read(chunkBuffer, 0, bytesToRead)
                        
                        if (bytesRead == -1) {
                            codec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEof = true
                        } else {
                            inputBuffer.put(chunkBuffer, 0, bytesRead)
                            codec.queueInputBuffer(
                                inputBufIndex,
                                0,
                                bytesRead,
                                0L,
                                0
                            )
                            processedBytes += bytesRead
                            onProgress(processedBytes.toFloat() / totalBytes)
                        }
                    }
                }

                // Retrieve output buffers
                val outputBufIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufIndex >= 0) {
                    val outputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        codec.getOutputBuffer(outputBufIndex)
                    } else {
                        outputBuffers[outputBufIndex]
                    }

                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        
                        val outData = ByteArray(bufferInfo.size)
                        outputBuffer.get(outData)
                        fos.write(outData)
                    }

                    codec.releaseOutputBuffer(outputBufIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEof = true
                    }
                } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format changed
                }
            }

            fos.flush()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { fis?.close() } catch (e: Exception) {}
            try { fos?.close() } catch (e: Exception) {}
            try { 
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {}
        }
    }
}
