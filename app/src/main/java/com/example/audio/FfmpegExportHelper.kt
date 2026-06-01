package com.example.audio

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FfmpegExportHelper {
    private const val TAG = "FfmpegExportHelper"

    private fun run(cmd: String): Boolean {
        val session = FFmpegKit.execute(cmd)
        val ok = ReturnCode.isSuccess(session.returnCode)
        if (!ok) Log.e(TAG, "ffmpeg failed: ${session.output}")
        return ok
    }

    fun splitChannels(
        inputFile: File,
        outputDir: File,
        baseName: String,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int,
        asFlac: Boolean,
        onChannelDone: (Int, Int) -> Unit = { _, _ -> }
    ): List<File> {
        // We will output stereo pairs
        // The Pair contains the left and right channel indices from the original file
        val defs = when (channelCount) {
            1  -> listOf("Mono" to Pair(0, 0))
            2  -> listOf("Front" to Pair(0, 1))
            6  -> listOf("Front" to Pair(0, 1),
                         "Center" to Pair(2, 2), 
                         "LFE" to Pair(3, 3),
                         "Surround" to Pair(4, 5))
            8  -> listOf("Front" to Pair(0, 1),
                         "Center" to Pair(2, 2), 
                         "LFE" to Pair(3, 3),
                         "Surround" to Pair(4, 5),
                         "Rear_Surround" to Pair(6, 7))
            10 -> listOf("Front" to Pair(0, 1),
                         "Center" to Pair(2, 2),
                         "LFE" to Pair(3, 3),
                         "Surround" to Pair(4, 5),
                         "Rear_Surround" to Pair(6, 7),
                         "Top_Front" to Pair(8, 9))
            12 -> listOf("Front" to Pair(0, 1),
                         "Center" to Pair(2, 2), 
                         "LFE" to Pair(3, 3),
                         "Surround" to Pair(4, 5),
                         "Rear_Surround" to Pair(6, 7),
                         "Top_Front" to Pair(8, 9),
                         "Top_Mid" to Pair(10, 11))
            16 -> listOf("Front" to Pair(0, 1),
                         "Center" to Pair(2, 2), 
                         "LFE" to Pair(3, 3),
                         "Surround" to Pair(4, 5),
                         "Rear_Surround" to Pair(6, 7),
                         "Top_Front" to Pair(8, 9),
                         "Top_Mid" to Pair(10, 11),
                         "Top_Rear" to Pair(12, 13),
                         "Wide" to Pair(14, 15))
            else -> {
                val list = mutableListOf<Pair<String, Pair<Int, Int>>>()
                var ch = 0
                while (ch < channelCount) {
                    if (ch + 1 < channelCount) {
                        list.add("Ch_${ch+1}_${ch+2}" to Pair(ch, ch + 1))
                        ch += 2
                    } else {
                        list.add("Ch_${ch+1}" to Pair(ch, ch))
                        ch++
                    }
                }
                list
            }
        }
        if (!outputDir.exists()) outputDir.mkdirs()
        val ext = if (asFlac) "flac" else "wav"
        val codec = if (asFlac) "flac -compression_level 8"
                    else "pcm_s${bitsPerSample}le"
        val out = mutableListOf<File>()
        defs.forEachIndexed { idx, (name, channels) ->
            val f = File(outputDir, "${baseName}_${name}.$ext")
            val cL = channels.first
            val cR = channels.second
            val ok = run(
                "-y -i \"${inputFile.absolutePath}\" " +
                "-filter_complex \"[0:a]pan=stereo|c0=c${cL}|c1=c${cR}[o]\" " +
                "-map \"[o]\" -ar $sampleRate -c:a $codec " +
                "\"${f.absolutePath}\""
            )
            if (ok) out.add(f)
            onChannelDone(idx + 1, defs.size)
        }
        return out
    }

    fun stereoDownmix(
        inputFile: File,
        outputFile: File,
        sampleRate: Int,
        bitsPerSample: Int,
        asFlac: Boolean
    ): Boolean {
        val codec = if (asFlac) "flac -compression_level 8"
                    else "pcm_s${bitsPerSample}le"
        return run(
            "-y -i \"${inputFile.absolutePath}\" " +
            "-ac 2 -ar $sampleRate -c:a $codec " +
            "\"${outputFile.absolutePath}\""
        )
    }

    fun zipFiles(files: List<File>, zipFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { f ->
                    zos.putNextEntry(ZipEntry(f.name))
                    FileInputStream(f).use { it.copyTo(zos) }
                    zos.closeEntry()
                    f.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "ZIP failed: ${e.message}")
            false
        }
    }
}
