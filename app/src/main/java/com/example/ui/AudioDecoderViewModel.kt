package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DecoderSupportInfo
import com.example.audio.DolbyAc4Decoder
import com.example.audio.FlacEncoderHelper
import com.example.audio.WavHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioDecoderViewModel(application: Application) : AndroidViewModel(application) {

    enum class ExportMode {
        WaveMultichannel,
        MonoWavCustomSplit,
        MonoFlacCustomSplit,
        StereoBinauralWav
    }

    sealed interface UIState {
        object Idle : UIState
        data class FileSelected(
            val uri: Uri,
            val name: String,
            val metadata: DolbyAc4Decoder.DecodedMetadata
        ) : UIState
        data class Processing(
            val originalName: String,
            val progress: Float,
            val status: String
        ) : UIState
        data class Success(
            val metadata: DolbyAc4Decoder.DecodedMetadata,
            val exportedFiles: List<File>
        ) : UIState
        data class Error(val message: String) : UIState
    }

    private val _uiState = MutableStateFlow<UIState>(UIState.Idle)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    private val _supportInfo = MutableStateFlow<DecoderSupportInfo?>(null)
    val supportInfo: StateFlow<DecoderSupportInfo?> = _supportInfo.asStateFlow()

    private val _exportMode = MutableStateFlow(ExportMode.StereoBinauralWav)
    val exportMode: StateFlow<ExportMode> = _exportMode.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private val _historyFiles = MutableStateFlow<List<File>>(emptyList())
    val historyFiles: StateFlow<List<File>> = _historyFiles.asStateFlow()

    // Playback state
    private var mediaPlayer: MediaPlayer? = null
    private val _playingFile = MutableStateFlow<File?>(null)
    val playingFile: StateFlow<File?> = _playingFile.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        checkDecoderSupport()
        loadHistory()
    }

    fun setExportMode(mode: ExportMode) {
        _exportMode.value = mode
    }

    fun setSimulationEnabled(enabled: Boolean) {
        _isSimulationEnabled.value = enabled
    }

    private fun checkDecoderSupport() {
        val info = DolbyAc4Decoder.checkAc4Support()
        _supportInfo.value = info
        // Automatically enable simulation if physical AC-4 codec is missing on emulator
        if (!info.hasAc4Decoder) {
            _isSimulationEnabled.value = true
        }
    }

    /**
     * Scans exports directory and lists existing decoded audio files.
     */
    fun loadHistory() {
        val context = getApplication<Application>()
        val exportsDir = File(context.filesDir, "exports")
        if (exportsDir.exists()) {
            val files = exportsDir.listFiles { file ->
                file.isFile && (file.extension.equals("wav", ignoreCase = true) || 
                                file.extension.equals("flac", ignoreCase = true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            _historyFiles.value = files
        } else {
            _historyFiles.value = emptyList()
        }
    }

    fun selectFile(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val name = getFileNameFromUri(context, uri) ?: "unknown_audio_file"
                onStopAudio() // Stop any active playback if new file is selected
                
                _uiState.value = UIState.Processing(name, 0f, "Analyzing bitstream contents...")
                val metadata = DolbyAc4Decoder.extractMetadata(context, uri)
                
                _uiState.value = UIState.FileSelected(uri, name, metadata)
                
            } catch (e: Exception) {
                _uiState.value = UIState.Error("Failed to parse file metadata: ${e.localizedMessage}")
            }
        }
    }

    fun startDecoding() {
        val state = _uiState.value
        if (state !is UIState.FileSelected) return

        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.value = UIState.Processing(state.name, 0f, "Preparing decoders...")
            
            try {
                val cachePcmFile = File(context.cacheDir, "temp_full_decoded.wav")
                if (cachePcmFile.exists()) cachePcmFile.delete()

                val activeMetadata = DolbyAc4Decoder.decode(
                    context = context,
                    inputUri = state.uri,
                    outputPcmFile = cachePcmFile,
                    isSimulationEnabled = _isSimulationEnabled.value,
                    onProgress = { progress ->
                        val currentState = _uiState.value
                        if (currentState is UIState.Processing) {
                            _uiState.value = currentState.copy(progress = progress)
                        }
                    },
                    onStatusUpdate = { status ->
                        val currentState = _uiState.value
                        if (currentState is UIState.Processing) {
                            _uiState.value = currentState.copy(status = status)
                        }
                    }
                )

                // EXPORT PROCESSING
                val mode = _exportMode.value
                val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
                val baseFilename = state.name.replace(".ac4", "").replace(".mp4", "").replace(".m4a", "")
                
                val finalFiles = mutableListOf<File>()

                when (mode) {
                    ExportMode.WaveMultichannel -> {
                        val destFile = File(exportsDir, "${baseFilename}_multichannel.wav")
                        cachePcmFile.copyTo(destFile, overwrite = true)
                        finalFiles.add(destFile)
                    }
                    ExportMode.StereoBinauralWav -> {
                        val destFile = File(exportsDir, "${baseFilename}_binaural_stereo.wav")
                        WavHelper.downmixToStereWav(
                            inputFile = cachePcmFile,
                            outputFile = destFile,
                            sourceChannelCount = activeMetadata.channelCount,
                            sampleRate = activeMetadata.sampleRate,
                            bitsPerSample = 16
                        )
                        finalFiles.add(destFile)
                    }
                    ExportMode.MonoWavCustomSplit -> {
                        val splitWavs = WavHelper.splitMultichannelWav(
                            inputFile = cachePcmFile,
                            outputDir = exportsDir,
                            baseName = baseFilename,
                            channelCount = activeMetadata.channelCount,
                            sampleRate = activeMetadata.sampleRate,
                            bitsPerSample = 16
                        )
                        finalFiles.addAll(splitWavs)
                    }
                    ExportMode.MonoFlacCustomSplit -> {
                        // Temp split in cache
                        val tempSplitDir = File(context.cacheDir, "splits").apply { mkdirs() }
                        val splitWavs = WavHelper.splitMultichannelWav(
                            inputFile = cachePcmFile,
                            outputDir = tempSplitDir,
                            baseName = baseFilename,
                            channelCount = activeMetadata.channelCount,
                            sampleRate = activeMetadata.sampleRate,
                            bitsPerSample = 16
                        )
                        
                        splitWavs.forEach { wav ->
                            val flacFile = File(exportsDir, "${wav.nameWithoutExtension}.flac")
                            val success = FlacEncoderHelper.encodeWavToFlac(
                                wavFile = wav,
                                flacFile = flacFile,
                                channelCount = 1,
                                sampleRate = activeMetadata.sampleRate,
                                bitsPerSample = 16
                            )
                            if (success) {
                                finalFiles.add(flacFile)
                            } else {
                                // Fallback to WAV if compressor fails
                                val fallbackFlac = File(exportsDir, "${wav.nameWithoutExtension}_split.wav")
                                wav.copyTo(fallbackFlac, overwrite = true)
                                finalFiles.add(fallbackFlac)
                            }
                            wav.delete() // Cleanup caching SPLIT WAV
                        }
                    }
                }

                // Delete workspace temp file
                if (cachePcmFile.exists()) cachePcmFile.delete()

                _uiState.value = UIState.Success(activeMetadata, finalFiles)
                loadHistory()

            } catch (e: Exception) {
                _uiState.value = UIState.Error("Processing pipeline failed: ${e.localizedMessage}")
            }
        }
    }

    fun resetToIdle() {
        _uiState.value = UIState.Idle
    }

    fun playAudio(file: File) {
        if (_playingFile.value == file) {
            if (_isPlaying.value) {
                mediaPlayer?.pause()
                _isPlaying.value = false
            } else {
                mediaPlayer?.start()
                _isPlaying.value = true
            }
            return
        }

        onStopAudio()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playingFile.value = null
                }
            }
            _playingFile.value = file
            _isPlaying.value = true
        } catch (e: Exception) {
            _uiState.value = UIState.Error("Could not play audio file: ${e.localizedMessage}")
        }
    }

    fun onStopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playingFile.value = null
        _isPlaying.value = false
    }

    fun deleteFile(file: File) {
        if (_playingFile.value == file) {
            onStopAudio()
        }
        if (file.exists()) {
            file.delete()
        }
        loadHistory()
    }

    fun shareExportedFile(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Share Exported Audio")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            _uiState.value = UIState.Error("Sharing failed: ${e.localizedMessage}")
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    override fun onCleared() {
        super.onCleared()
        onStopAudio()
    }
}
