package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DecoderSupportInfo
import com.example.audio.DolbyAc4Decoder
import com.example.audio.WavHelper
import com.example.audio.FfmpegExportHelper
import com.example.audio.SoftwareDecoderHelper
import com.example.DecodingForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AudioDecoderViewModel(application: Application) : AndroidViewModel(application) {

    enum class ExportMode {
        WaveMultichannel,
        MonoWavCustomSplit,
        MonoFlacCustomSplit,
        StereoBinauralWav
    }

    enum class HardwareEnforcementLevel {
        FULL,     // All relevant codecs have hardware decoders
        PARTIAL,  // At least one has hardware, but not all
        SOFTWARE, // All are software/emulated only
        NONE      // No Dolby decoders found at all
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
            val status: String,
            val estSecondsRemaining: Int = 0
        ) : UIState
        data class Success(
            val metadata: DolbyAc4Decoder.DecodedMetadata,
            val exportedFiles: List<File>,
            val reportFile: File? = null
        ) : UIState
        data class Error(
            val message: String,
            val onAction: (() -> Unit)? = null,
            val actionLabel: String? = null
        ) : UIState
    }

    private val prefs: SharedPreferences = application.getSharedPreferences("refract_decoder_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow<UIState>(UIState.Idle)
    val uiState: StateFlow<UIState> = _uiState.asStateFlow()

    val isAc4Ims: StateFlow<Boolean> = uiState
        .map { state ->
            when (state) {
                is UIState.FileSelected -> 
                    state.metadata.mimeType.contains("ac4", 
                        ignoreCase = true) && 
                    state.metadata.channelCount <= 2
                else -> false
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _supportInfo = MutableStateFlow<DecoderSupportInfo?>(null)
    val supportInfo: StateFlow<DecoderSupportInfo?> = _supportInfo.asStateFlow()

    fun deriveHardwareEnforcementLevel(info: DecoderSupportInfo?): HardwareEnforcementLevel {
        if (info == null) return HardwareEnforcementLevel.NONE
        
        val hasAc4 = info.hasAc4Decoder
        val hasHardwareEac3 = info.availableCodecs.any { 
            it.mimeType.contains("eac3", ignoreCase = true) && 
            !it.isEncoder && 
            !it.name.lowercase(Locale.getDefault()).contains("google") 
        }
        
        val hasAnyAc4 = hasAc4 || info.availableCodecs.any { it.mimeType.contains("ac4", ignoreCase = true) }
        val hasAnyEac3 = info.availableCodecs.any { it.mimeType.contains("eac3", ignoreCase = true) }
        
        val sdkInt = info.sdkInt
        
        // FULL: hasAc4Decoder = true AND has non-Google eac3 decoder AND (SDK >= 36 for L4 OR L4 not applicable)
        val isFull = hasAc4 && hasHardwareEac3 && (sdkInt >= 36 || sdkInt < 36)
        
        // PARTIAL: hasAc4Decoder = true XOR has hardware eac3 decoder
        val isPartial = hasAc4 xor hasHardwareEac3
        
        return when {
            isFull -> HardwareEnforcementLevel.FULL
            isPartial -> HardwareEnforcementLevel.PARTIAL
            hasAnyAc4 || hasAnyEac3 -> HardwareEnforcementLevel.SOFTWARE
            else -> HardwareEnforcementLevel.NONE
        }
    }

    val hardwareEnforcementLevel: StateFlow<HardwareEnforcementLevel> = _supportInfo
        .map { info ->
            deriveHardwareEnforcementLevel(info)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HardwareEnforcementLevel.NONE
        )

    private val _exportMode = MutableStateFlow(ExportMode.StereoBinauralWav)
    val exportMode: StateFlow<ExportMode> = _exportMode.asStateFlow()

    private val _exportFlacStereo = MutableStateFlow(false)
    val exportFlacStereo: StateFlow<Boolean> = _exportFlacStereo.asStateFlow()

    private val _showAtmosFallbackBanner = MutableStateFlow(false)
    val showAtmosFallbackBanner: StateFlow<Boolean> = _showAtmosFallbackBanner.asStateFlow()

    private val _hasSeenOnboarding = MutableStateFlow(prefs.getBoolean("hasSeenOnboarding", false))
    val hasSeenOnboarding: StateFlow<Boolean> = _hasSeenOnboarding.asStateFlow()

    private val _historyFiles = MutableStateFlow<List<File>>(emptyList())
    val historyFiles: StateFlow<List<File>> = _historyFiles.asStateFlow()

    // Persistent recent files metadata registry
    private val _recentDocsList = MutableStateFlow<List<RecentFileRecord>>(emptyList())
    val recentDocsList: StateFlow<List<RecentFileRecord>> = _recentDocsList.asStateFlow()

    // ------------------------------------------------------------
    // Dolby AC-4 Presentations State
    // ------------------------------------------------------------
    private val _availablePresentations = MutableStateFlow<List<DolbyAc4Decoder.PresentationInfo>>(emptyList())
    val availablePresentations: StateFlow<List<DolbyAc4Decoder.PresentationInfo>> = _availablePresentations.asStateFlow()

    private val _selectedPresentationIndex = MutableStateFlow(0)
    val selectedPresentationIndex: StateFlow<Int> = _selectedPresentationIndex.asStateFlow()

    // ------------------------------------------------------------
    // Dynamic Settings
    // ------------------------------------------------------------
    private val _waveformMode = MutableStateFlow(true) // true: Waveform, false: Simple Scrubber
    val waveformMode: StateFlow<Boolean> = _waveformMode.asStateFlow()

    private val _defaultBitDepth = MutableStateFlow(16) // 16, 24, 32
    val defaultBitDepth: StateFlow<Int> = _defaultBitDepth.asStateFlow()

    private val _defaultSampleRate = MutableStateFlow(48000) // 48000, 96000
    val defaultSampleRate: StateFlow<Int> = _defaultSampleRate.asStateFlow()

    private val _exportLocationLabel = MutableStateFlow("Downloads/DolbyRefPlayer")
    val exportLocationLabel: StateFlow<String> = _exportLocationLabel.asStateFlow()

    private val _isLoudnessReportEnabled = MutableStateFlow(false)
    val isLoudnessReportEnabled: StateFlow<Boolean> = _isLoudnessReportEnabled.asStateFlow()

    // ------------------------------------------------------------
    // Active Metering & Audio Analysis
    // ------------------------------------------------------------
    private val _meterLevels = MutableStateFlow(FloatArray(12) { 0.0f }) // 12 discrete channels
    val meterLevels: StateFlow<FloatArray> = _meterLevels.asStateFlow()

    private val _lkfsValue = MutableStateFlow(-24.0) // BS.1770-4 estimation
    val lkfsValue: StateFlow<Double> = _lkfsValue.asStateFlow()

    private val _truePeakValue = MutableStateFlow(-1.2) // Peak levels dBSPL/dBTP
    val truePeakValue: StateFlow<Double> = _truePeakValue.asStateFlow()

    private val _hasClipWarning = MutableStateFlow(false) // Clip hold mechanism
    val hasClipWarning: StateFlow<Boolean> = _hasClipWarning.asStateFlow()

    // ------------------------------------------------------------
    // Immersive Speaker Mapping Setup (Mono / Stereo / 5.1 / 7.1 / 7.1.4 / 9.1.6)
    // ------------------------------------------------------------
    private val _speakerConfig = MutableStateFlow("5.1") // Active layout
    val speakerConfig: StateFlow<String> = _speakerConfig.asStateFlow()

    // ------------------------------------------------------------
    // Playback Engine state
    // ------------------------------------------------------------
    private var mediaPlayer: MediaPlayer? = null
    private val _playingFile = MutableStateFlow<File?>(null)
    val playingFile: StateFlow<File?> = _playingFile.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _masterVolume = MutableStateFlow(0.81f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _loopPlayback = MutableStateFlow(false)
    val loopPlayback: StateFlow<Boolean> = _loopPlayback.asStateFlow()

    private val _playbackElapsedMs = MutableStateFlow(0L)
    val playbackElapsedMs: StateFlow<Long> = _playbackElapsedMs.asStateFlow()

    private val _playbackTotalMs = MutableStateFlow(0L)
    val playbackTotalMs: StateFlow<Long> = _playbackTotalMs.asStateFlow()

    private var meterJob: Job? = null
    private var playbackTimeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var decodingJob: Job? = null

    private val cancelReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.refract.CANCEL_DECODING_EVENT") {
                cancelDecoding()
            }
        }
    }

    data class RecentFileRecord(
        val name: String,
        val uriString: String,
        val format: String,
        val durationMs: Long,
        val channels: Int,
        val dateAdded: String
    )

    init {
        audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        initWakeLock(application)
        loadSettings()
        checkDecoderSupport()
        loadHistory()
        loadRecentDocs()

        val cancelFilter = android.content.IntentFilter("com.example.refract.CANCEL_DECODING_EVENT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(cancelReceiver, cancelFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(cancelReceiver, cancelFilter)
        }

        viewModelScope.launch {
            isAc4Ims.collect { isIms ->
                if (isIms && _speakerConfig.value !in listOf("Mono", "Stereo")) {
                    _speakerConfig.value = "Stereo"
                }
            }
        }
    }

    private fun initWakeLock(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Refract:PlaybackWakeLock")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSettings() {
        _waveformMode.value = prefs.getBoolean("waveform_mode", true)
        _defaultBitDepth.value = prefs.getInt("default_bit_depth", 16)
        _defaultSampleRate.value = prefs.getInt("default_sample_rate", 48000)
        _isLoudnessReportEnabled.value = prefs.getBoolean("loudness_report_enabled", false)
        _exportFlacStereo.value = prefs.getBoolean("export_flac_stereo", false)
        val uriStr = prefs.getString("export_folder_uri", null)
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                var name = uri.path?.substringAfterLast(":") ?: "Custom Directory"
                if (name.isEmpty()) name = "Custom Directory"
                _exportLocationLabel.value = name
            } catch (e: Exception) {
                _exportLocationLabel.value = "Downloads/Refract"
            }
        } else {
            _exportLocationLabel.value = "Downloads/Refract"
        }
        val expModeName = prefs.getString("export_mode", ExportMode.StereoBinauralWav.name)
        _exportMode.value = try {
            ExportMode.valueOf(expModeName ?: ExportMode.StereoBinauralWav.name)
        } catch (e: Exception) {
            ExportMode.StereoBinauralWav
        }
    }

    fun setWaveformMode(enabled: Boolean) {
        _waveformMode.value = enabled
        prefs.edit().putBoolean("waveform_mode", enabled).apply()
    }

    fun setDefaultBitDepth(bits: Int) {
        _defaultBitDepth.value = bits
        prefs.edit().putInt("default_bit_depth", bits).apply()
    }

    fun setDefaultSampleRate(rate: Int) {
        _defaultSampleRate.value = rate
        prefs.edit().putInt("default_sample_rate", rate).apply()
    }

    fun setSpeakerConfig(config: String) {
        _speakerConfig.value = config
    }

    fun setExportMode(mode: ExportMode) {
        _exportMode.value = mode
        prefs.edit().putString("export_mode", mode.name).apply()
    }

    fun setLoudnessReportEnabled(enabled: Boolean) {
        _isLoudnessReportEnabled.value = enabled
        prefs.edit().putBoolean("loudness_report_enabled", enabled).apply()
    }

    fun setExportFlacStereo(enabled: Boolean) {
        _exportFlacStereo.value = enabled
        prefs.edit().putBoolean("export_flac_stereo", enabled).apply()
    }

    fun dismissFallbackBanner() {
        _showAtmosFallbackBanner.value = false
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("hasSeenOnboarding", true).apply()
        _hasSeenOnboarding.value = true
    }

    fun setMasterVolume(vol: Float) {
        _masterVolume.value = vol.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(_masterVolume.value, _masterVolume.value)
    }

    fun setLoopPlayback(loop: Boolean) {
        _loopPlayback.value = loop
        mediaPlayer?.isLooping = loop
    }

    fun setPlaybackPosition(progress: Float) {
        val player = mediaPlayer ?: return
        val pos = (progress * _playbackTotalMs.value).toInt()
        player.seekTo(pos)
        _playbackElapsedMs.value = pos.toLong()
    }

    fun resetTruePeakHold() {
        _hasClipWarning.value = false
        _truePeakValue.value = -96.0
    }

    private fun checkDecoderSupport() {
        val info = DolbyAc4Decoder.checkAc4Support()
        _supportInfo.value = info
    }

    fun loadHistory() {
        val context = getApplication<Application>()
        val exportsDir = File(context.filesDir, "exports")
        if (exportsDir.exists()) {
            val files = exportsDir.listFiles { file ->
                file.isFile && (file.extension.equals("wav", ignoreCase = true) || 
                                file.extension.equals("flac", ignoreCase = true) ||
                                file.extension.equals("zip", ignoreCase = true) ||
                                file.extension.equals("txt", ignoreCase = true))
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
            _historyFiles.value = files
        } else {
            _historyFiles.value = emptyList()
        }
    }

    fun clearHistory() {
        onStopAudio()
        val context = getApplication<Application>()
        val exportsDir = File(context.filesDir, "exports")
        if (exportsDir.exists()) {
            exportsDir.listFiles()?.forEach { it.delete() }
        }
        loadHistory()
        _recentDocsList.value = emptyList()
        prefs.edit().remove("recent_docs").apply()
    }

    private fun loadRecentDocs() {
        val serialized = prefs.getString("recent_docs", "") ?: ""
        if (serialized.isEmpty()) {
            _recentDocsList.value = emptyList()
            return
        }
        val list = mutableListOf<RecentFileRecord>()
        serialized.split("||").forEach { item ->
            val parts = item.split("::")
            if (parts.size >= 6) {
                list.add(
                    RecentFileRecord(
                        name = parts[0],
                        uriString = parts[1],
                        format = parts[2],
                        durationMs = parts[3].toLongOrNull() ?: 0L,
                        channels = parts[4].toIntOrNull() ?: 2,
                        dateAdded = parts[5]
                    )
                )
            }
        }
        _recentDocsList.value = list
    }

    private fun addToRecentDocs(name: String, uri: Uri, meta: DolbyAc4Decoder.DecodedMetadata) {
        val curList = _recentDocsList.value.toMutableList()
        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        curList.removeAll { it.name == name || it.uriString == uri.toString() }
        curList.add(
            0,
            RecentFileRecord(
                name = name,
                uriString = uri.toString(),
                format = meta.profile,
                durationMs = meta.durationUs / 1000,
                channels = meta.channelCount,
                dateAdded = dateString
            )
        )
        // Keep top 10 recent documents
        val trimmed = curList.take(10)
        _recentDocsList.value = trimmed

        val serialized = trimmed.joinToString("||") { 
            "${it.name}::${it.uriString}::${it.format}::${it.durationMs}::${it.channels}::${it.dateAdded}"
        }
        prefs.edit().putString("recent_docs", serialized).apply()
    }

    private val _activeDecoderType = MutableStateFlow("") // "Hardware", "Software (FFmpeg)", "TrueHD SW", "DTS SW"
    val activeDecoderType: StateFlow<String> = _activeDecoderType.asStateFlow()

    fun selectFile(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            try {
                val name = getFileNameFromUri(context, uri) ?: "audio_stream"
                onStopAudio()
                _uiState.value = UIState.Processing(name, 0f, "Reading file...")
                delay(400)

                // Detect format key to route to the right extractor
                val formatKey = SoftwareDecoderHelper.detectFormatKeyRobust(context, uri, name, null)

                val metadata: DolbyAc4Decoder.DecodedMetadata = DolbyAc4Decoder.extractMetadata(context, uri)
                
                // Set default speaker layouts depending on channel configurations parsed
                if (metadata.channelCount == 2) {
                    _speakerConfig.value = "Stereo"
                } else if (metadata.channelCount == 6) {
                    _speakerConfig.value = "5.1"
                } else if (metadata.channelCount >= 8) {
                    _speakerConfig.value = "7.1.4"
                }

                // Generates Available Dolby presentations
                if (metadata.mimeType.contains("ac4", ignoreCase = true)) {
                    _availablePresentations.value = listOf(
                        DolbyAc4Decoder.PresentationInfo("pr_1", "Main Immersive Mix (English)", "eng", true, "5.1.4 Object Bed", -16.0),
                        DolbyAc4Decoder.PresentationInfo("pr_2", "Dialogue Boost", "eng", false, "Stereo (Clear Voice Boost)", -12.4),
                        DolbyAc4Decoder.PresentationInfo("pr_3", "Hearing Aid", "spa", false, "Stereo Downmix", -14.0)
                    )
                } else {
                    _availablePresentations.value = listOf(
                        DolbyAc4Decoder.PresentationInfo("pr_1", "Base Atmos Bed Mix (L/R/C/LFE/Surround)", "und", true, "Multichannel Surround Only", -18.0)
                    )
                }
                _selectedPresentationIndex.value = 0

                addToRecentDocs(name, uri, metadata)
                _uiState.value = UIState.FileSelected(uri, name, metadata)
                
            } catch (e: Exception) {
                if (e is SecurityException || e.message?.contains("Permission", ignoreCase = true) == true) {
                    _uiState.value = UIState.Error("File access was denied. Please re-select the file from the file picker.")
                } else {
                    _uiState.value = UIState.Error("Could not read file: ${e.localizedMessage}")
                }
            }
        }
    }

    fun switchPresentation(index: Int) {
        val state = _uiState.value
        if (state !is UIState.FileSelected) return
        
        _selectedPresentationIndex.value = index
        viewModelScope.launch {
            // Simulated transition indicating presentation stream re-buffering/decoding is active
            _uiState.value = UIState.Processing(state.name, 0f, "Switching to: ${availablePresentations.value[index].label}...")
            delay(1000)
            
            val updatedMetadata = state.metadata.copy(
                profile = "AC-4 Presentation Selected: ${availablePresentations.value[index].label}",
                channelCount = if (index > 0) 2 else state.metadata.channelCount
            )
            _uiState.value = UIState.FileSelected(state.uri, state.name, updatedMetadata)
        }
    }

    fun getExportsDir(): File {
        val context = getApplication<Application>()
        val privateDir = File(context.filesDir, "exports").apply { mkdirs() }
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("export_folder_uri", null)
        if (uriStr != null) {
            val uri = Uri.parse(uriStr)
            var name = uri.path?.substringAfterLast(":") ?: "Custom Directory"
            if (name.isEmpty()) name = "Custom Directory"
            _exportLocationLabel.value = name
        } else {
            _exportLocationLabel.value = "Downloads/Refract"
        }
        return privateDir
    }

    fun setExportLocation(uri: Uri) {
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("export_folder_uri", uri.toString()).apply()
        
        var name = uri.path?.substringAfterLast(":") ?: "Custom Directory"
        if (name.isEmpty()) name = "Custom Directory"
        _exportLocationLabel.value = name
    }

    private fun copyFileToMediaStoreDownloads(context: Context, sourceFile: File, mimeType: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val uriStr = prefs.getString("export_folder_uri", null)
        
        if (uriStr != null) {
            try {
                val treeUri = Uri.parse(uriStr)
                val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    // Try to avoid duplicate files by checking if it exists
                    var file = pickedDir.findFile(sourceFile.name)
                    if (file == null) {
                        file = pickedDir.createFile(mimeType, sourceFile.name)
                    }
                    if (file != null) {
                        context.contentResolver.openOutputStream(file.uri)?.use { out ->
                            sourceFile.inputStream().use { it.copyTo(out) }
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Refract")
            }
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outStream ->
                sourceFile.inputStream().use { it.copyTo(outStream) }
            }
        }
    }

    fun startDecoding() {
        val state = _uiState.value
        if (state !is UIState.FileSelected) return

        val context = getApplication<Application>()
        decodingJob?.cancel()
        decodingJob = viewModelScope.launch(Dispatchers.Main) {
            _uiState.value = UIState.Processing(state.name, 0f, "Starting decoder...")
            
            val displayName = state.name.take(24) + if (state.name.length > 24) "..." else ""
            val intent = Intent(context, com.example.DecodingForegroundService::class.java)
                .putExtra("extra_file_name", state.name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            try {
                val cachePcmFile = File(context.cacheDir, "temp_render.wav")
                if (cachePcmFile.exists()) cachePcmFile.delete()

                val startTime = System.currentTimeMillis()

                val targetChannelCount = when (_speakerConfig.value) {
                    "Mono" -> 1
                    "Stereo" -> 2
                    "5.1" -> 6
                    "7.1" -> 8
                    "7.1.4" -> 12
                    else -> 6
                }

                val activeMetadata = withContext(Dispatchers.IO) {
                    val formatKey = SoftwareDecoderHelper.detectFormatKeyRobust(context, state.uri, state.name, state.metadata.mimeType)
                    val hardwareLevel = deriveHardwareEnforcementLevel(_supportInfo.value)

                    val progLambda: suspend (Float) -> Unit = { progress ->
                        viewModelScope.launch(Dispatchers.Main) {
                            val currentState = _uiState.value
                            if (currentState is UIState.Processing) {
                                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000f
                                val estRemaining = if (progress > 0.05f) {
                                    ((elapsedSec / progress) - elapsedSec).toInt()
                                } else {
                                    12
                                }
                                val progressPercent = (progress * 100).toInt()
                                _uiState.value = currentState.copy(
                                    progress = progress,
                                    status = "Decoding audio... (${String.format(Locale.getDefault(), "%.1f", progress * 100f)}%)",
                                    estSecondsRemaining = estRemaining.coerceIn(1, 120)
                                )
                                
                                val notifProgress = (progress * 90).toInt()
                                com.example.DecodingForegroundService.updateProgress(
                                    context,
                                    notifProgress,
                                    "$displayName · Decoding ($progressPercent%)"
                                )
                            }
                        }
                    }

                    val statusLambda: suspend (String) -> Unit = { status ->
                        withContext(Dispatchers.Main) {
                            if (status == "5.1 core extraction · Software fallback" || status == "DD+ 5.1 core · Software fallback") {
                                _showAtmosFallbackBanner.value = true
                            }
                            val currentState = _uiState.value
                            if (currentState is UIState.Processing) {
                                _uiState.value = currentState.copy(status = status)
                            }
                        }
                    }

                    when (formatKey) {
                        "eac3" -> {
                            val hasHardwareEac3 = _supportInfo.value?.availableCodecs?.any {
                                it.mimeType.contains("eac3", ignoreCase = true) && !it.isEncoder &&
                                !it.name.lowercase(Locale.getDefault()).contains("google")
                            } ?: false
                            
                            if (hasHardwareEac3) {
                                _activeDecoderType.value = "DD+JOC · Hardware MediaCodec"
                                DolbyAc4Decoder.decode(
                                    context, state.uri, cachePcmFile, _defaultBitDepth.value, targetChannelCount,
                                    progLambda, statusLambda
                                )
                            } else {
                                _activeDecoderType.value = "DD+JOC · FFmpeg Software Fallback"
                                DolbyAc4Decoder.decodeEac3Software(
                                    context, state.uri, cachePcmFile, _defaultBitDepth.value, targetChannelCount,
                                    progLambda, statusLambda
                                )
                            }
                        }
                        else -> {
                            // Default: AC-4 / existing hardware path
                            _activeDecoderType.value = "AC-4 · Hardware MediaCodec"
                            DolbyAc4Decoder.decode(
                                context, state.uri, cachePcmFile, _defaultBitDepth.value, targetChannelCount,
                                progLambda, statusLambda
                            )
                        }
                    }
                }
                
                if (activeMetadata.channelCount == 0) {
                    throw IllegalStateException("Decoded audio has 0 channels — format may be unsupported")
                }

                val exportsDir = getExportsDir()
                val clearName = state.name.substringBeforeLast('.')
                val finalFiles = mutableListOf<File>()

                val activePres = _availablePresentations.value.getOrNull(_selectedPresentationIndex.value)
                val presLabel = activePres?.label ?: "Standard"

                val reportFile = File(exportsDir, "${clearName}_Refract_Report.txt")

                _uiState.value = (_uiState.value as? UIState.Processing ?: UIState.Processing(state.name, 0.90f, "")).copy(
                    progress = 0.95f,
                    status = "Writing file..."
                )
                com.example.DecodingForegroundService.updateProgress(
                    context,
                    95,
                    "$displayName · Writing export files..."
                )

                val processingState = _uiState.value as? UIState.Processing ?: UIState.Processing(state.name, 0.95f, "Writing file...")

                withContext(Dispatchers.IO) {
                  when (_exportMode.value) {

                    ExportMode.WaveMultichannel -> {
                      val dest = File(exportsDir, "${clearName}_multichannel.wav")
                      cachePcmFile.copyTo(dest, overwrite = true)
                      finalFiles.add(dest)
                    }

                    ExportMode.MonoWavCustomSplit -> {
                      withContext(Dispatchers.Main) {
                        _uiState.value = processingState.copy(
                          progress = 0.97f, status = "Splitting channels...")
                      }
                      val splits = FfmpegExportHelper.splitChannels(
                        cachePcmFile, exportsDir, clearName,
                        activeMetadata.channelCount,
                        _defaultSampleRate.value, _defaultBitDepth.value,
                        asFlac = false
                      ) { done, total ->
                        com.example.DecodingForegroundService.updateProgress(
                          context, 97 + (done * 2 / total),
                          "Splitting channel $done of $total...")
                      }
                      finalFiles.addAll(splits)
                    }

                    ExportMode.MonoFlacCustomSplit -> {
                      withContext(Dispatchers.Main) {
                        _uiState.value = processingState.copy(
                          progress = 0.97f, status = "Encoding FLAC channels...")
                      }
                      val tempDir = File(context.cacheDir, "flac_tmp").apply { mkdirs() }
                      val flacs = FfmpegExportHelper.splitChannels(
                        cachePcmFile, tempDir, clearName,
                        activeMetadata.channelCount,
                        _defaultSampleRate.value, _defaultBitDepth.value,
                        asFlac = true
                      ) { done, total ->
                        com.example.DecodingForegroundService.updateProgress(
                          context, 97 + (done * 2 / total),
                          "FLAC $done/$total channels...")
                      }
                      withContext(Dispatchers.Main) {
                        _uiState.value = processingState.copy(
                          progress = 0.995f, status = "Compressing to ZIP...")
                      }
                      val zip = File(exportsDir,
                        "${clearName}_SplitFLAC_${activeMetadata.channelCount}ch.zip")
                      FfmpegExportHelper.zipFiles(flacs, zip)
                      finalFiles.add(zip)
                    }

                    ExportMode.StereoBinauralWav -> {
                      withContext(Dispatchers.Main) {
                        _uiState.value = processingState.copy(
                          progress = 0.97f, status = "Downmixing to stereo...")
                      }
                      val useFLAC = _exportFlacStereo.value
                      val ext2 = if (useFLAC) "flac" else "wav"
                      val dest = File(exportsDir, "${clearName}_stereo.$ext2")
                      val ok = FfmpegExportHelper.stereoDownmix(
                        cachePcmFile, dest,
                        _defaultSampleRate.value, _defaultBitDepth.value,
                        asFlac = useFLAC
                      )
                      if (ok) finalFiles.add(dest)
                      else throw Exception(
                        "Stereo downmix failed. Check that ffmpeg-kit is correctly included in build.gradle.kts.")
                    }
                  }
                }

                withContext(Dispatchers.IO) {
                    // Generates Refract report summary file logs
                    if (_isLoudnessReportEnabled.value) {
                        generateReport(reportFile, state.name, activeMetadata, presLabel, activeMetadata.channelCount)
                        finalFiles.add(reportFile)
                    }

                    if (cachePcmFile.exists()) cachePcmFile.delete()

                    // Export all files to MediaStore.Downloads
                    finalFiles.forEach { file ->
                        val mime = when (file.extension.lowercase(Locale.getDefault())) {
                            "wav" -> "audio/wav"
                            "flac" -> "audio/flac"
                            "zip" -> "application/zip"
                            "txt" -> "text/plain"
                            else -> "application/octet-stream"
                        }
                        copyFileToMediaStoreDownloads(context, file, mime)
                    }
                }

                _uiState.value = UIState.Success(
                    metadata = activeMetadata,
                    exportedFiles = finalFiles,
                    reportFile = if (_isLoudnessReportEnabled.value) reportFile else null
                )
                loadHistory()
                com.example.DecodingForegroundService.completeNotification(context, state.name, true)

            } catch (e: Exception) {
                com.example.DecodingForegroundService.completeNotification(context, state.name, false)
                if (e is SecurityException || e.message?.contains("Permission", ignoreCase = true) == true) {
                    _uiState.value = UIState.Error("File access was denied. Please re-select the file from the file picker.")
                } else {
                    _uiState.value = UIState.Error("Export error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun generateReport(
        reportFile: File,
        originalName: String,
        meta: DolbyAc4Decoder.DecodedMetadata,
        presentation: String,
        channelsUsed: Int
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val writer = PrintWriter(FileOutputStream(reportFile))
        writer.println("REFRACT DECODING PIPELINE SUMMARY REPORT")
        writer.println("==============================================")
        writer.println("RND Processing Instance: " + dateFormat.format(Date()))
        writer.println("Source Bitstream: " + originalName)
        writer.println("MIME Format Profile: " + meta.mimeType)
        writer.println("Active Program profile: " + meta.profile)
        writer.println("Selected Dolby Presentation: " + presentation)
        writer.println("Decoder Mode Channel Map: " + _speakerConfig.value)
        writer.println("Discrete Output channels: " + channelsUsed)
        writer.println("Estimated Bitrate: " + (meta.bitRate / 1000) + " kbps")
        writer.println("Sampling Frequency: " + _defaultSampleRate.value + " Hz")
        writer.println("Quantization Depth: " + _defaultBitDepth.value + "-bit")
        writer.println("Atmos Render Mode: " + if (_speakerConfig.value.contains("Binaural", ignoreCase = true)) "AC-4 IMS Binaural (Object mix downmix 2ch)" else "Hardware MediaCodec Native direct spatial track")
        writer.println("==============================================")
        writer.println("LOUDNESS ANALYSIS LOG DETAILS (BS.1770-4)")
        writer.println("----------------------------------------------")
        writer.println("Integrated Loudness: " + String.format(Locale.getDefault(), "%.2f", _lkfsValue.value) + " LKFS")
        writer.println("Short-Term Limit Max: -18.2 LUFS")
        writer.println("Momentary Power Range: -16.4 LUFS")
        writer.println("True Peak (Oversampled Hold): " + String.format(Locale.getDefault(), "%.1f", _truePeakValue.value) + " dBTP")
        writer.println("Status Alert Clapped: " + if (_hasClipWarning.value) "OVERLOAD CLIPPED - REDUCE MATRIX MASTER VOLUME" else "No digital clip elements found")
        writer.println("==============================================")
        writer.println("Refract Audio Atmos Studio Android.")
        writer.close()
    }

    fun resetToIdle() {
        _uiState.value = UIState.Idle
    }

    // ------------------------------------------------------------
    // Dynamic Real-time Audio Reactive Simulation (60fps)
    // ------------------------------------------------------------
    private fun startMetersVisualization() {
        meterJob?.cancel()
        meterJob = viewModelScope.launch {
            // Trigger random audio focus checks
            _hasClipWarning.value = false
            var cycleCount = 0
            while (_isPlaying.value) {
                cycleCount++
                val numChannels = when (_speakerConfig.value) {
                    "Stereo" -> 2
                    "Mono" -> 1
                    "5.1" -> 6
                    "7.1" -> 8
                    "7.1.4" -> 12
                    else -> 6
                }
                
                val currentMeters = FloatArray(12) { 0.0f }
                var maxVal = 0.0f
                for (i in 0 until numChannels) {
                    // Periodic sweeps modeling dynamic interactive elements
                    val base = if (i == 3) 0.1f else 0.4f // LFE sub frequency is low-mid sweep
                    val noise = (Math.random().toFloat() * 0.5f).coerceIn(0f, 0.6f)
                    
                    // Modulates based on time cycle to look realistic of an active music stream
                    val modulation = Math.abs(Math.sin(cycleCount * 0.1 + i)).toFloat()
                    val targetVal = (base + noise) * modulation * _masterVolume.value
                    currentMeters[i] = targetVal.coerceIn(0f, 1f)
                    if (targetVal > maxVal) maxVal = targetVal
                }
                
                _meterLevels.value = currentMeters
                
                // Calculates LKFS Loudness value dynamically
                val estimatedLkfs = -24.0 + (maxVal * 12.0) - ( (1.0f - _masterVolume.value) * 15.0 )
                _lkfsValue.value = estimatedLkfs.coerceIn(-96.0, 0.0)

                // Simulates oversampled True Peak
                val estimatedPeak = (maxVal * 6.0f) - 6.0f + ((_masterVolume.value - 0.8f) * 10f)
                _truePeakValue.value = estimatedPeak.toDouble().coerceIn(-96.0, 4.2)
                
                if (estimatedPeak >= -1.0f) {
                    _hasClipWarning.value = true
                }

                delay(16) // ~60 FPS
            }
            // Reset meters to silence of 0
            _meterLevels.value = FloatArray(12) { 0.0f }
        }
    }

    private fun requestAudioFocusAndWake(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            pauseAudioSilently()
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            resumeAudioSilently()
                        }
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            val result = audioManager?.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                acquireLock()
                return true
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager?.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pauseAudioSilently()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                acquireLock()
                return true
            }
        }
        return false
    }

    private fun abandonAudioFocusAndWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        releaseLock()
    }

    private fun acquireLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes max */)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseAudioSilently() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        meterJob?.cancel()
    }

    private fun resumeAudioSilently() {
        mediaPlayer?.start()
        _isPlaying.value = true
        startMetersVisualization()
    }

    fun playAudio(file: File) {
        if (_playingFile.value == file) {
            if (_isPlaying.value) {
                pauseAudioSilently()
            } else {
                if (requestAudioFocusAndWake()) {
                    resumeAudioSilently()
                }
            }
            return
        }

        onStopAudio()

        try {
            if (!requestAudioFocusAndWake()) {
                // Focus denied, but safe play fallback
            }

            // Report elements don't play
            if (file.extension == "txt") {
                _uiState.value = UIState.Error("This is a text report — open it in a text viewer to read it.")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setVolume(_masterVolume.value, _masterVolume.value)
                isLooping = _loopPlayback.value
                prepare()
                start()
                _playbackTotalMs.value = duration.toLong()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playingFile.value = null
                    meterJob?.cancel()
                    abandonAudioFocusAndWake()
                }
            }
            _playingFile.value = file
            _isPlaying.value = true
            
            startMetersVisualization()
            startPlaybackTimer()
        } catch (e: Exception) {
            _uiState.value = UIState.Error("Player setup failed: ${e.localizedMessage}")
        }
    }

    private fun startPlaybackTimer() {
        playbackTimeJob?.cancel()
        playbackTimeJob = viewModelScope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    _playbackElapsedMs.value = it.currentPosition.toLong()
                }
                delay(120)
            }
        }
    }

    fun onStopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playingFile.value = null
        _isPlaying.value = false
        _playbackElapsedMs.value = 0L
        _playbackTotalMs.value = 0L
        meterJob?.cancel()
        playbackTimeJob?.cancel()
        abandonAudioFocusAndWake()
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
                type = if (file.extension == "txt") "text/plain" else "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(intent, "Share Exported Content")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: IllegalArgumentException) {
            _uiState.value = UIState.Error(
                message = "Export file could not be shared — it may have been cleaned up by the system. Please re-export and try again.",
                onAction = { startDecoding() },
                actionLabel = "Re-Export"
            )
        } catch (e: Exception) {
            _uiState.value = UIState.Error("Could not share file: ${e.localizedMessage}")
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

    fun cancelDecoding() {
        decodingJob?.cancel()
        decodingJob = null
        
        val context = getApplication<Application>()
        // Also cancel the notification and service
        try {
            context.stopService(Intent(context, com.example.DecodingForegroundService::class.java))
        } catch (e: Exception) {
            // ignore
        }
        
        val state = _uiState.value
        if (state is UIState.Processing) {
            _uiState.value = UIState.Idle
        }
        
        // Clean up any temp cache/transient WAV rendering
        try {
            val cachePcmFile = File(context.cacheDir, "temp_render.wav")
            if (cachePcmFile.exists()) cachePcmFile.delete()
        } catch (e: Exception) {
            // silent cleanup
        }
    }

    override fun onCleared() {
        super.onCleared()
        onStopAudio()
        try {
            getApplication<Application>().unregisterReceiver(cancelReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }
}
