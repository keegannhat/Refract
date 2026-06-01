package com.example.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SoftwareDecoderHelper {

    fun copyUriToTemp(context: Context, uri: Uri, suffix: String): File {
        val tempFile = File(context.cacheDir, "sw_decode_input_$suffix")
        if (tempFile.exists()) tempFile.delete()
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("Cannot open input stream for $uri")
        return tempFile
    }

    suspend fun detectFormatKeyRobust(context: Context, uri: Uri, fileName: String, mimeType: String?): String = withContext(Dispatchers.IO) {
        var key = detectFormatKey(fileName, mimeType)
        if (key == "unknown" || key == "ac4") {
            try {
                val tempFile = copyUriToTemp(context, uri, "probe_detect.tmp")
                val probeSession = FFprobeKit.execute("-v quiet -print_format json -show_streams \"${tempFile.absolutePath}\"")
                val out = probeSession.output ?: ""
                if (out.contains("\"codec_name\": \"truehd\"", ignoreCase = true)) key = "truehd"
                else if (out.contains("\"codec_name\": \"eac3\"", ignoreCase = true)) key = "eac3"
                else if (out.contains("\"codec_name\": \"ac4\"", ignoreCase = true)) key = "ac4"
                
                if (tempFile.exists()) tempFile.delete()
            } catch (e: Exception) {}
        }
        if (key == "unknown") "ac4" else key
    }

    fun detectFormatKey(fileName: String, mimeType: String?): String {
        val lower = fileName.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return when {
            mimeType?.contains("truehd", ignoreCase = true) == true || mimeType?.contains("true-hd", ignoreCase = true) == true -> "truehd"
            mimeType?.contains("eac3", ignoreCase = true) == true -> "eac3"
            mimeType?.contains("ac4", ignoreCase = true) == true -> "ac4"
            ext in setOf("mlp") -> "truehd"
            ext in setOf("ec3", "eac3") -> "eac3"
            ext == "ac4" -> "ac4"
            ext in setOf("mkv", "mka", "m4a") -> "unknown"
            else -> "unknown"
        }
    }
}
