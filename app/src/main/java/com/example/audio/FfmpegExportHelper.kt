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
        val defs = when (channelCount) {
            1  -> listOf("Mono" to 0)
            2  -> listOf("Front_L" to 0, "Front_R" to 1)
            6  -> listOf("Front_L" to 0, "Front_R" to 1,
                         "Center" to 2, "LFE" to 3,
                         "Surround_L" to 4, "Surround_R" to 5)
            8  -> listOf("Front_L" to 0, "Front_R" to 1,
                         "Center" to 2, "LFE" to 3,
                         "Surround_L" to 4, "Surround_R" to 5,
                         "Rear_Surround_L" to 6, "Rear_Surround_R" to 7)
            12 -> listOf("Front_L" to 0, "Front_R" to 1,
                         "Center" to 2, "LFE" to 3,
                         "Surround_L" to 4, "Surround_R" to 5,
                         "Rear_Surround_L" to 6, "Rear_Surround_R" to 7,
                         "Top_Front_L" to 8, "Top_Front_R" to 9,
                         "Top_Mid_L" to 10, "Top_Mid_R" to 11)
            16 -> listOf("Front_L" to 0, "Front_R" to 1,
                         "Center" to 2, "LFE" to 3,
                         "Surround_L" to 4, "Surround_R" to 5,
                         "Rear_Surround_L" to 6, "Rear_Surround_R" to 7,
                         "Top_Front_L" to 8, "Top_Front_R" to 9,
                         "Top_Mid_L" to 10, "Top_Mid_R" to 11,
                         "Top_Rear_L" to 12, "Top_Rear_R" to 13,
                         "Wide_L" to 14, "Wide_R" to 15)
            else -> (0 until channelCount).map { "Ch_${it+1}" to it }
        }
        if (!outputDir.exists()) outputDir.mkdirs()
        val ext = if (asFlac) "flac" else "wav"
        val codec = if (asFlac) "flac -compression_level 8"
                    else "pcm_s${bitsPerSample}le"
        val out = mutableListOf<File>()
        defs.forEachIndexed { idx, (name, ch) ->
            val f = File(outputDir, "${baseName}_${name}.$ext")
            val ok = run(
                "-y -i \"${inputFile.absolutePath}\" " +
                "-filter_complex \"[0:a]pan=mono|c0=c${ch}[o]\" " +
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
