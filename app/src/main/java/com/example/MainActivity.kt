package com.example

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audio.CodecDetail
import com.example.audio.DecoderSupportInfo
import com.example.audio.DolbyAc4Decoder
import com.example.ui.AudioDecoderViewModel
import com.example.ui.theme.*
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_screen"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    DecoderAppScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecoderAppScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioDecoderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val supportInfo by viewModel.supportInfo.collectAsState()
    val exportMode by viewModel.exportMode.collectAsState()
    val isSimulationEnabled by viewModel.isSimulationEnabled.collectAsState()
    val historyFiles by viewModel.historyFiles.collectAsState()
    val playingFile by viewModel.playingFile.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Advanced State Collectors
    val recentDocsList by viewModel.recentDocsList.collectAsState()
    val availablePresentations by viewModel.availablePresentations.collectAsState()
    val selectedPresentationIndex by viewModel.selectedPresentationIndex.collectAsState()
    val waveformMode by viewModel.waveformMode.collectAsState()
    val defaultBitDepth by viewModel.defaultBitDepth.collectAsState()
    val defaultSampleRate by viewModel.defaultSampleRate.collectAsState()
    val exportLocationLabel by viewModel.exportLocationLabel.collectAsState()
    val meterLevels by viewModel.meterLevels.collectAsState()
    val lkfsValue by viewModel.lkfsValue.collectAsState()
    val truePeakValue by viewModel.truePeakValue.collectAsState()
    val hasClipWarning by viewModel.hasClipWarning.collectAsState()
    val speakerConfig by viewModel.speakerConfig.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val loopPlayback by viewModel.loopPlayback.collectAsState()
    val playbackElapsedMs by viewModel.playbackElapsedMs.collectAsState()
    val playbackTotalMs by viewModel.playbackTotalMs.collectAsState()

    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHardwareInfoSheet by remember { mutableStateOf(false) }

    // File picker launcher supporting .ec3, .ac4, .mp4, .m4a, and .ts containers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFile(uri)
        }
    }

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SlateGrayBg,
                        Color(0xFF060912)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Title Bar with Settings triggers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(PurpleGlow.copy(alpha = 0.4f), Color.Transparent)))
                            .border(2.dp, CyberCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Decoder logo",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "REFRACT",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = IceWhite,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Professional Dolby Atmos & AC-4 Studio",
                            fontSize = 10.sp,
                            color = CoolGrayText,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showHardwareInfoSheet = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Hardware Info Help",
                            tint = CyberCyan
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System Settings",
                            tint = IceWhite
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Persistent Hardware Capabilities status card
                item {
                    CapabilitiesHardwareCard(
                        supportInfo = supportInfo,
                        isSimulationActive = isSimulationEnabled,
                        showDiagnostics = showDiagnostics,
                        onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                        onToggleSimulation = { viewModel.setSimulationEnabled(it) },
                        onHelpClick = { showHardwareInfoSheet = true }
                    )
                }

                // Core Interactive Stage
                item {
                    when (val state = uiState) {
                        is AudioDecoderViewModel.UIState.Idle -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FileDropzoneSelector(
                                    onSelectClick = { filePickerLauncher.launch("*/*") }
                                )

                                // Real recent documents library
                                if (recentDocsList.isNotEmpty()) {
                                    Text(
                                        text = "RECENT DOCUMENTS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                                    )
                                    recentDocsList.forEach { record ->
                                        RecentFileItem(
                                            record = record,
                                            onLoadClick = {
                                                viewModel.selectFile(Uri.parse(record.uriString))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        is AudioDecoderViewModel.UIState.FileSelected -> {
                            FileSelectedCard(
                                name = state.name,
                                info = state.metadata,
                                selectedMode = exportMode,
                                onModeSelect = { viewModel.setExportMode(it) },
                                onCancel = { viewModel.resetToIdle() },
                                onProcess = { viewModel.startDecoding() },
                                availablePresentations = availablePresentations,
                                selectedPresentationIndex = selectedPresentationIndex,
                                onSelectPresentation = { viewModel.switchPresentation(it) },
                                speakerConfig = speakerConfig,
                                onSpeakerConfigChange = { viewModel.setSpeakerConfig(it) }
                            )
                        }

                        is AudioDecoderViewModel.UIState.Processing -> {
                            ProcessingCard(
                                fileName = state.originalName,
                                progress = state.progress,
                                statusMsg = state.status,
                                estSecondsRemaining = state.estSecondsRemaining
                            )
                        }

                        is AudioDecoderViewModel.UIState.Success -> {
                            SuccessCard(
                                metadata = state.metadata,
                                files = state.exportedFiles,
                                playingFile = playingFile,
                                isPlaying = isPlaying,
                                onPlayClick = { viewModel.playAudio(it) },
                                onShareClick = { viewModel.shareExportedFile(context, it) },
                                onDeleteClick = { viewModel.deleteFile(it) },
                                onReset = { viewModel.resetToIdle() }
                            )
                        }

                        is AudioDecoderViewModel.UIState.Error -> {
                            ErrorDisplayCard(
                                message = state.message,
                                onDismiss = { viewModel.resetToIdle() }
                            )
                        }
                    }
                }

                // Playback Analytics suite with waveform, scrubber, speakers and live peak level meters
                if (playingFile != null) {
                    item {
                        ActiveAnalyzerAtmosPanel(
                            playingName = playingFile?.name ?: "",
                            isPlaying = isPlaying,
                            elapsedMs = playbackElapsedMs,
                            totalMs = playbackTotalMs,
                            waveformMode = waveformMode,
                            speakerConfig = speakerConfig,
                            meterLevels = meterLevels,
                            lkfsValue = lkfsValue,
                            truePeakValue = truePeakValue,
                            hasClipWarning = hasClipWarning,
                            masterVolume = masterVolume,
                            loopPlayback = loopPlayback,
                            onScrub = { viewModel.setPlaybackPosition(it) },
                            onVolumeChange = { viewModel.setMasterVolume(it) },
                            onLoopToggle = { viewModel.setLoopPlayback(!loopPlayback) },
                            onResetClip = { viewModel.resetTruePeakHold() }
                        )
                    }
                }

                // Saved Decoded Exports History List
                if (historyFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = "DECODED ARTIFACT HISTORY",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(historyFiles, key = { it.absolutePath }) { file ->
                        HistoryFileItem(
                            file = file,
                            isPlaying = isPlaying && playingFile == file,
                            onPlayPause = { viewModel.playAudio(file) },
                            onShare = { viewModel.shareExportedFile(context, file) },
                            onDelete = { viewModel.deleteFile(file) }
                        )
                    }
                } else {
                    item {
                        EmptyHistoryCard()
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Real-time Mini Player Overlay on bottom
            if (playingFile != null) {
                MiniPlayerOverlay(
                    playingFileName = playingFile?.name ?: "",
                    isPlaying = isPlaying,
                    onPlayPauseToggle = { playingFile?.let { viewModel.playAudio(it) } },
                    onStop = { viewModel.onStopAudio() }
                )
            }
        }

        // Settings Preferences Dialog
        if (showSettingsDialog) {
            SystemSettingsDialog(
                waveformMode = waveformMode,
                defaultBitDepth = defaultBitDepth,
                defaultSampleRate = defaultSampleRate,
                exportLocation = exportLocationLabel,
                onToggleWaveform = { viewModel.setWaveformMode(it) },
                onSelectBitDepth = { viewModel.setDefaultBitDepth(it) },
                onSelectSampleRate = { viewModel.setDefaultSampleRate(it) },
                onClearHistory = {
                    viewModel.clearHistory()
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Help Information Overlay Toolsheet
        if (showHardwareInfoSheet) {
            HardwareInfoDialog(
                onDismiss = { showHardwareInfoSheet = false }
            )
        }
    }
}

@Composable
fun CapabilitiesHardwareCard(
    supportInfo: DecoderSupportInfo?,
    isSimulationActive: Boolean,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit,
    onHelpClick: () -> Unit
) {
    // Dynamic media capability checking elements
    var hasEac3 by remember { mutableStateOf(false) }
    var hasAc4 by remember { mutableStateOf(false) }
    var eac3DecoderName by remember { mutableStateOf("Not Found") }
    var ac4DecoderName by remember { mutableStateOf("Not Found") }

    LaunchedEffect(supportInfo) {
        if (supportInfo != null) {
            hasAc4 = supportInfo.hasAc4Decoder
            ac4DecoderName = if (hasAc4) supportInfo.ac4DecoderNames.first() else "Not Found"
            
            val eac3Codec = supportInfo.availableCodecs.find {
                it.mimeType.contains("eac3", ignoreCase = true) && !it.isEncoder
            }
            hasEac3 = eac3Codec != null
            eac3DecoderName = eac3Codec?.name ?: "MpegAudioSoftwareFallback"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, SurfaceBorder), RoundedCornerShape(16.dp))
            .testTag("diagnostics_card"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (hasAc4 || isSimulationActive) AcidGreen else RedAlert)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Decoder Diagnostics Suite",
                        fontWeight = FontWeight.Bold,
                        color = IceWhite,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = if (isSimulationActive) "VIRTUAL DIRECT" else "HARDWARE ENFORCED",
                    color = if (isSimulationActive) PurpleGlow else AcidGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Badge Matrix Block
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // E-AC3 JOC status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "E-AC3-JOC (Dolby Atmos Objects)",
                        color = CoolGrayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val isEac3Hw = hasEac3 && !eac3DecoderName.contains("google", ignoreCase = true)
                    Text(
                        text = if (hasEac3) (if (isEac3Hw) "✅ Hardware ($eac3DecoderName)" else "✅ Software Fallback ($eac3DecoderName)") else "⚠️ Simulated Rendering",
                        color = if (hasEac3) (if (isEac3Hw) AcidGreen else CyberCyan) else PurpleGlow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                    )
                }

                // AC-4 IMS status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AC-4 IMS Binaural",
                        color = CoolGrayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasAc4) "✅ Native ($ac4DecoderName)" else "✅ Software fallback active",
                        color = if (hasAc4) AcidGreen else CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // AC-4 L4 status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AC-4 L4 Multichannel Support",
                        color = CoolGrayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (Build.VERSION.SDK_INT >= 35) {
                                if (hasAc4) "✅ Android 15+ Native support" else "⚠️ API 35 (Codecs absent)"
                            } else {
                                "⚠️ SDK 35 required (You are on API ${Build.VERSION.SDK_INT})"
                            },
                            color = if (Build.VERSION.SDK_INT >= 35 && hasAc4) AcidGreen else RedAlert,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Synthetic Simulation Toggle when local hardware lacks licenses
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF131A26))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sim Mode active",
                        tint = PurpleGlow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Synthetic Simulation Stage",
                            color = IceWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Simulates spatial panning vectors for missing licenses",
                            color = CoolGrayText,
                            fontSize = 9.sp
                        )
                    }
                }

                Switch(
                    checked = isSimulationActive,
                    onCheckedChange = onToggleSimulation,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = CyberCyan.copy(alpha = 0.4f),
                        uncheckedThumbColor = CoolGrayText,
                        uncheckedTrackColor = SurfaceBorder
                    ),
                    modifier = Modifier
                        .scale(0.8f)
                        .testTag("simulation_toggle")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable full system codec registry list trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDiagnostics() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showDiagnostics) "Hide Codec Registry" else "Show Full Android MediaCodec Registry",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showDiagnostics) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "arrow",
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (showDiagnostics && supportInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val codecsToDisplay = supportInfo.availableCodecs.sortedBy { it.name }
                        items(codecsToDisplay) { codec ->
                            val isAtmosCodec = codec.name.lowercase(Locale.getDefault()).contains("dolby") ||
                                    codec.name.lowercase(Locale.getDefault()).contains("ac4") ||
                                    codec.name.lowercase(Locale.getDefault()).contains("eac3")
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isAtmosCodec) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (isAtmosCodec) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(CyberCyan)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = codec.name,
                                        color = if (isAtmosCodec) CyberCyan else IceWhite,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = codec.mimeType.replace("audio/", ""),
                                    color = CoolGrayText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileDropzoneSelector(
    onSelectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(2.dp, Brush.sweepGradient(listOf(CyberCyan, PurpleGlow, CyberCyan))),
                RoundedCornerShape(16.dp)
            )
            .clickable { onSelectClick() }
            .testTag("select_file_button"),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Upload Icon",
                    tint = CyberCyan,
                    modifier = Modifier.size(44.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "SELECT SOUND STREAM PORT",
                    color = IceWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Supports .ac4, .ec3, .mp4, .m4a, and .ts containers",
                    color = CoolGrayText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun RecentFileItem(
    record: AudioDecoderViewModel.RecentFileRecord,
    onLoadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLoadClick() }
            .border(BorderStroke(1.dp, SurfaceBorder), RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Recent document",
                    tint = CoolGrayText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = record.name,
                        color = IceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Loaded ${record.dateAdded} • ${record.format}",
                        color = CoolGrayText,
                        fontSize = 9.sp
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Load File",
                tint = CyberCyan,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileSelectedCard(
    name: String,
    info: DolbyAc4Decoder.DecodedMetadata,
    selectedMode: AudioDecoderViewModel.ExportMode,
    onModeSelect: (AudioDecoderViewModel.ExportMode) -> Unit,
    onCancel: () -> Unit,
    onProcess: () -> Unit,
    availablePresentations: List<DolbyAc4Decoder.PresentationInfo>,
    selectedPresentationIndex: Int,
    onSelectPresentation: (Int) -> Unit,
    speakerConfig: String,
    onSpeakerConfigChange: (String) -> Unit
) {
    val isBinauralSource = info.channelCount == 2 && name.contains(".ac4", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .testTag("selected_file_card"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "file details",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        fontWeight = FontWeight.ExtraBold,
                        color = IceWhite,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "cancel selected",
                        tint = RedAlert,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Spec grid container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateGrayBg)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Profile Standard:", color = CoolGrayText, fontSize = 11.sp)
                        Text(info.profile, color = PurpleGlow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stream Mime-Type:", color = CoolGrayText, fontSize = 11.sp)
                        Text(info.mimeType, color = IceWhite, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Decoded Bit Depth:", color = CoolGrayText, fontSize = 11.sp)
                        Text("${info.bitDepth}-bit uncompressed PCM", color = IceWhite, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bitstream Channel layout:", color = CoolGrayText, fontSize = 11.sp)
                        Text("${info.channelCount} Channels (Atmos Matrix)", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Atmos Master Standard:", color = CoolGrayText, fontSize = 11.sp)
                        Text(info.jocVersion, color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Audio Duration:", color = CoolGrayText, fontSize = 11.sp)
                        val secs = info.durationUs / 1_000_000L
                        Text(String.format(Locale.getDefault(), "%02d:%02d.%03d", secs / 60, secs % 60, (info.durationUs % 1_000_000L) / 1000), color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // Dolby Presentation Selector Block (Only shown for AC-4 stream files loaded)
            if (info.mimeType.contains("ac4", ignoreCase = true) && availablePresentations.size > 1) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceBorder)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "DOLBY AC-4 PROGRAM LIST (${availablePresentations.size})",
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                availablePresentations.forEachIndexed { idx, p ->
                    val isChosen = idx == selectedPresentationIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isChosen) CyberCyan.copy(alpha = 0.08f) else Color(0xFF131A26))
                            .border(1.dp, if (isChosen) CyberCyan else SurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectPresentation(idx) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.label, color = IceWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (p.isImmersive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(PurpleGlow.copy(alpha = 0.3f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("ATMOS OBJECTS", color = PurpleGlow, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text("Language: ${p.language.uppercase()} • Layout Profile: ${p.channelConfig} • Dialogue Limit: ${p.dialogueLevelDb} dBFS", color = CoolGrayText, fontSize = 9.sp)
                        }
                        RadioButton(
                            selected = isChosen,
                            onClick = { onSelectPresentation(idx) },
                            colors = RadioButtonDefaults.colors(selectedColor = CyberCyan, unselectedColor = CoolGrayText)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Speaker Channel config mappings with coordinate matrix drawing preview
            Text(
                text = "SURROUND / IMMERSIVE OUT-STAGE LAYOUT",
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Config buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val layouts = listOf("Mono", "Stereo", "5.1", "7.1", "7.1.4", "9.1.6")
                layouts.forEach { layout ->
                    val isLayoutActive = speakerConfig == layout
                    val tooHighForBinaural = isBinauralSource && layout != "Mono" && layout != "Stereo" && layout != "5.1"
                    
                    Button(
                        onClick = { if (!tooHighForBinaural) onSpeakerConfigChange(layout) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLayoutActive) CyberCyan else (if (tooHighForBinaural) Color.Transparent else Color(0xFF131A26)),
                            contentColor = if (isLayoutActive) SlateGrayBg else (if (tooHighForBinaural) CoolGrayText.copy(alpha = 0.4f) else IceWhite)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = if (tooHighForBinaural) BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f)) else null
                    ) {
                        Text(layout, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isBinauralSource) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Binaural limit warning",
                        tint = PurpleGlow,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Dolby Binaural AC-4 IMS is hardcapped to 5.1 bed elements in dynamic export models.",
                        color = PurpleGlow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Coordinate layout map Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B14))
                    .border(1.dp, SurfaceBorder)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2
                    val cy = h / 2
                    val radius = minOf(cx, cy) - 24f

                    // Draw head silhouette central node
                    drawCircle(Color(0xFF1E293B), radius = 20f, center = Offset(cx, cy))
                    drawCircle(CyberCyan.copy(alpha = 0.3f), radius = 22f, center = Offset(cx, cy), style = Stroke(width = 2f))
                    // Nose point showing direction
                    drawRect(Color(0xFF1E293B), topLeft = Offset(cx - 3f, cy - 28f), size = Size(6f, 10f))

                    // Node mappings based on speaker config layouts
                    val speakerJoints = mutableListOf<Pair<String, Offset>>()
                    when (speakerConfig) {
                        "Mono" -> {
                            speakerJoints.add("C" to Offset(cx, cy - radius))
                        }
                        "Stereo" -> {
                            speakerJoints.add("L" to Offset(cx - radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("R" to Offset(cx + radius * 0.707f, cy - radius * 0.707f))
                        }
                        "5.1" -> {
                            speakerJoints.add("L" to Offset(cx - radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("R" to Offset(cx + radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("C" to Offset(cx, cy - radius * 0.95f))
                            speakerJoints.add("LFE" to Offset(cx / 1.5f, cy - radius * 0.4f))
                            speakerJoints.add("Ls" to Offset(cx - radius * 0.95f, cy + radius * 0.2f))
                            speakerJoints.add("Rs" to Offset(cx + radius * 0.95f, cy + radius * 0.2f))
                        }
                        "7.1" -> {
                            speakerJoints.add("L" to Offset(cx - radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("R" to Offset(cx + radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("C" to Offset(cx, cy - radius * 0.95f))
                            speakerJoints.add("LFE" to Offset(cx / 1.5f, cy - radius * 0.4f))
                            speakerJoints.add("Ls" to Offset(cx - radius * 0.98f, cy))
                            speakerJoints.add("Rs" to Offset(cx + radius * 0.98f, cy))
                            speakerJoints.add("Lbs" to Offset(cx - radius * 0.5f, cy + radius * 0.8f))
                            speakerJoints.add("Rbs" to Offset(cx + radius * 0.5f, cy + radius * 0.8f))
                        }
                        "7.1.4", "9.1.6" -> {
                            speakerJoints.add("L" to Offset(cx - radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("R" to Offset(cx + radius * 0.707f, cy - radius * 0.707f))
                            speakerJoints.add("C" to Offset(cx, cy - radius * 0.95f))
                            speakerJoints.add("LFE" to Offset(cx / 1.5f, cy - radius * 0.4f))
                            speakerJoints.add("Ls" to Offset(cx - radius * 0.98f, cy))
                            speakerJoints.add("Rs" to Offset(cx + radius * 0.98f, cy))
                            speakerJoints.add("Lbs" to Offset(cx - radius * 0.5f, cy + radius * 0.8f))
                            speakerJoints.add("Rbs" to Offset(cx + radius * 0.5f, cy + radius * 0.8f))
                            // Heights (Neon Magenta dots)
                            speakerJoints.add("Ltf" to Offset(cx - radius * 0.4f, cy - radius * 0.4f))
                            speakerJoints.add("Rtf" to Offset(cx + radius * 0.4f, cy - radius * 0.4f))
                            speakerJoints.add("Ltr" to Offset(cx - radius * 0.4f, cy + radius * 0.4f))
                            speakerJoints.add("Rtr" to Offset(cx + radius * 0.4f, cy + radius * 0.4f))
                        }
                    }

                    // Draw grid layout boundaries
                    drawCircle(Color(0xFF1E293B).copy(alpha = 0.4f), radius = radius, center = Offset(cx, cy), style = Stroke(width = 1f))

                    speakerJoints.forEach { pair ->
                        val label = pair.first
                        val pos = pair.second
                        val isHeight = label.startsWith("Lt") || label.startsWith("Rt")

                        // glowing matrix rings
                        drawCircle(if (isHeight) PurpleGlow else CyberCyan, radius = 5f, center = pos)
                        drawCircle(if (isHeight) PurpleGlow.copy(alpha = 0.3f) else CyberCyan.copy(alpha = 0.3f), radius = 10f, center = pos, style = Stroke(width = 1.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            // Target Export Configuration Selection
            Text(
                text = "CONTAINER EXPORT MODAL OPTIONS",
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportModeOptionTile(
                    title = "Stereo Downmix (Binaural Mode)",
                    desc = "Downmixes discrete sound coordinates into spatialized binaural stereo WAV (Recommended for regular headphones).",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.StereoBinauralWav,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.StereoBinauralWav) }
                )

                ExportModeOptionTile(
                    title = "Unified Multichannel (Interleaved WAV)",
                    desc = "Generates a single multichannel uncompressed WAV preserving layout mapping coordinates (Ideal for DAW editing).",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.WaveMultichannel,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.WaveMultichannel) }
                )

                ExportModeOptionTile(
                    title = "Split Mono Channels (Uncompressed WAV)",
                    desc = "Splits each audio track coordinate into individual discrete mono WAV files.",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.MonoWavCustomSplit,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.MonoWavCustomSplit) }
                )

                ExportModeOptionTile(
                    title = "Split Mono Lossless Zip (FLAC Archive)",
                    desc = "Compresses and encapsulates split channel flacs natively into a single manageable .zip archive.",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.MonoFlacCustomSplit,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.MonoFlacCustomSplit) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onProcess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("action_decode_button"),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "START EXTRACTION PIPELINE",
                    color = SlateGrayBg,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun ExportModeOptionTile(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) CyberCyan else SurfaceBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = CyberCyan,
                unselectedColor = CoolGrayText
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) CyberCyan else IceWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                desc,
                color = CoolGrayText,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ProcessingCard(
    fileName: String,
    progress: Float,
    statusMsg: String,
    estSecondsRemaining: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("processing_card"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundVisualizerBars()
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Processing $fileName",
                fontWeight = FontWeight.Bold,
                color = IceWhite,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusMsg,
                color = CyberCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .testTag("progress_indicator"),
                color = CyberCyan,
                trackColor = SurfaceBorder
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "Progress: %.0f%%", progress * 100f),
                    color = IceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                
                Text(
                    text = if (estSecondsRemaining > 0) "Est. remaining: ${estSecondsRemaining}s" else "Finalizing...",
                    color = PurpleGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SuccessCard(
    metadata: DolbyAc4Decoder.DecodedMetadata,
    files: List<File>,
    playingFile: File?,
    isPlaying: Boolean,
    onPlayClick: (File) -> Unit,
    onShareClick: (File) -> Unit,
    onDeleteClick: (File) -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, AcidGreen.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .testTag("success_card"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AcidGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        tint = AcidGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DSP CONVERSION SUCCESSFUL!",
                    color = AcidGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "GENERATED WORKSPACE ARTIFACTS (${files.size}):",
                color = CoolGrayText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                files.forEach { file ->
                    val isReport = file.extension == "txt"
                    val isZip = file.extension == "zip"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateGrayBg)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isReport) Icons.Default.Info else (if (isZip) Icons.Default.CheckCircle else Icons.Default.PlayArrow),
                                contentDescription = "file icon",
                                tint = if (isReport) PurpleGlow else (if (isZip) AcidGreen else CyberCyan),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = file.name,
                                    color = IceWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isReport) "Refract Loudness summary report" else "${String.format(Locale.getDefault(), "%.2f", file.length() / (1024f * 1024f))} MB • ${file.extension.uppercase()}",
                                    color = CoolGrayText,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Row {
                            if (!isReport && !isZip) {
                                IconButton(onClick = { onPlayClick(file) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        imageVector = if (playingFile == file && isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = "play file",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(onClick = { onShareClick(file) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "share file",
                                    tint = IceWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(onClick = { onDeleteClick(file) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "delete file",
                                    tint = RedAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("success_dismiss_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "DECODE OTHER IMMERSIVE BITSTREAM",
                    color = IceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ActiveAnalyzerAtmosPanel(
    playingName: String,
    isPlaying: Boolean,
    elapsedMs: Long,
    totalMs: Long,
    waveformMode: Boolean,
    speakerConfig: String,
    meterLevels: FloatArray,
    lkfsValue: Double,
    truePeakValue: Double,
    hasClipWarning: Boolean,
    masterVolume: Float,
    loopPlayback: Boolean,
    onScrub: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLoopToggle: () -> Unit,
    onResetClip: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Panel Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "REAL-TIME ATMOS METERING & DSP ANALYZER",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hasClipWarning) RedAlert.copy(alpha = 0.2f) else SurfaceBorder)
                        .border(1.dp, if (hasClipWarning) RedAlert else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { onResetClip() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (hasClipWarning) "OVERLOAD CLIPPED (TAP RESET)" else "PEAK OK",
                        color = if (hasClipWarning) RedAlert else AcidGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multichannel Meter Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Determine layout names
                val meterCount = when (speakerConfig) {
                    "Mono" -> 1
                    "Stereo" -> 2
                    "5.1" -> 6
                    "7.1" -> 8
                    "7.1.4" -> 12
                    "9.1.6" -> 12
                    else -> 6
                }
                val channelLabels = when (speakerConfig) {
                    "Mono" -> listOf("M")
                    "Stereo" -> listOf("L", "R")
                    "5.1" -> listOf("L", "R", "C", "LFE", "Ls", "Rs")
                    "7.1" -> listOf("L", "R", "C", "LFE", "Ls", "Rs", "Lbs", "Rbs")
                    "7.1.4" -> listOf("L", "R", "C", "LFE", "Ls", "Rs", "Lbs", "Rbs", "Ltf", "Rtf", "Ltr", "Rtr")
                    "9.1.6" -> listOf("L", "R", "C", "LFE", "Ls", "Rs", "Lbs", "Rbs", "Ltf", "Rtf", "Ltr", "Rtr")
                    else -> listOf("L", "R", "C", "LFE", "Ls", "Rs")
                }

                (0 until meterCount).forEach { index ->
                    val rawLevel = meterLevels.getOrElse(index) { 0.0f }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Level bar container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF0C101B)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Active level fill
                            val heightFract = rawLevel.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightFract)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                if (hasClipWarning) RedAlert else PurpleGlow,
                                                CyberCyan
                                            )
                                        )
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = channelLabels.getOrElse(index) { "Ch" },
                            color = CoolGrayText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Loudness & True Peak Diagnostics display rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LOUDNESS:", color = CoolGrayText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f LKFS", lkfsValue),
                        color = if (lkfsValue > -18.0) RedAlert else CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TRUE PEAK:", color = CoolGrayText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f dBTP", truePeakValue),
                        color = if (truePeakValue >= -1.0) RedAlert else IceWhite,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(14.dp))

            // Waveform and seek scrubber block
            val elapsedSecs = elapsedMs / 1000
            val totalSecs = totalMs / 1000
            
            val elapsedFmt = String.format(Locale.getDefault(), "%02d:%02d.%03d", elapsedSecs / 60, elapsedSecs % 60, elapsedMs % 1000)
            val totalFmt = String.format(Locale.getDefault(), "%02d:%02d.%03d", totalSecs / 60, totalSecs % 60, totalMs % 1000)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(elapsedFmt, color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(totalFmt, color = CoolGrayText, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (waveformMode) {
                // Interactive Waveform peak Canvas with drag/scrub gestures
                var zoomFactor by remember { mutableFloatStateOf(1f) }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D16))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val scrubFract = (change.position.x / size.width).coerceIn(0f, 1f)
                                onScrub(scrubFract)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val scrubFract = (offset.x / size.width).coerceIn(0f, 1f)
                                onScrub(scrubFract)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2
                        
                        val activeProgress = if (totalMs > 0) elapsedMs.toFloat() / totalMs else 0f
                        val peakCount = 80
                        val rawBars = List(peakCount) { idx ->
                            val freq = 0.12f
                            val val1 = sin(idx * freq) / 2f + 0.5f
                            val val2 = cos(idx * freq * 0.45f) / 2f + 0.5f
                            (val1 * val2 * 0.85f).coerceIn(0.1f, 1f)
                        }

                        val barWidth = w / peakCount
                        rawBars.forEachIndexed { idx, pk ->
                            val pkHeight = pk * h * 0.8f
                            val x = idx * barWidth
                            val isLeftOfPlayhead = (idx.toFloat() / peakCount) < activeProgress
                            
                            val col = if (isLeftOfPlayhead) CyberCyan else SurfaceBorder.copy(alpha = 0.8f)
                            
                            drawRect(
                                color = col,
                                topLeft = Offset(x + 2f, midY - pkHeight / 2),
                                size = Size(barWidth - 4f, pkHeight)
                            )
                        }

                        // Playhead vertical line
                        val playheadX = activeProgress * w
                        drawLine(
                            color = PurpleGlow,
                            start = Offset(playheadX, 0f),
                            end = Offset(playheadX, h),
                            strokeWidth = 3f
                        )
                    }
                }
            } else {
                // Simple material slider fallback
                val currentSliderValue = if (totalMs > 0) elapsedMs.toFloat() / totalMs else 0f
                Slider(
                    value = currentSliderValue,
                    onValueChange = onScrub,
                    colors = SliderDefaults.colors(
                        activeTrackColor = CyberCyan,
                        inactiveTrackColor = SurfaceBorder,
                        thumbColor = CyberCyan
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub Controller Volume Slider + Loop Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Volume slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (masterVolume > 0.5f) Icons.Default.PlayArrow else Icons.Default.Close,
                        contentDescription = "volume icon",
                        tint = CoolGrayText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Slider(
                        value = masterVolume,
                        onValueChange = onVolumeChange,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = CyberCyan,
                            thumbColor = IceWhite
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Loop Toggle
                IconButton(
                    onClick = onLoopToggle,
                    modifier = Modifier
                        .background(if (loopPlayback) CyberCyan.copy(alpha = 0.1f) else Color.Transparent, CircleShape)
                        .border(1.dp, if (loopPlayback) CyberCyan else SurfaceBorder, CircleShape)
                        .size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "loop clip",
                        tint = if (loopPlayback) CyberCyan else CoolGrayText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SystemSettingsDialog(
    waveformMode: Boolean,
    defaultBitDepth: Int,
    defaultSampleRate: Int,
    exportLocation: String,
    onToggleWaveform: (Boolean) -> Unit,
    onSelectBitDepth: (Int) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE SETTINGS", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                "REFRACT LABORATORY LAWS",
                color = IceWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Waveform toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interactive Waveform Peak-Renderer", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Draws high density visual packet nodes (Pinch / drag active)", color = CoolGrayText, fontSize = 9.sp)
                    }
                    Switch(
                        checked = waveformMode,
                        onCheckedChange = onToggleWaveform,
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.4f))
                    )
                }

                HorizontalDivider(color = SurfaceBorder)

                // Select quantization depth resolution
                Column {
                    Text("Default PCM Quantization Bit-Depth", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(16, 24, 32).forEach { depth ->
                            val isChosen = defaultBitDepth == depth
                            Button(
                                onClick = { onSelectBitDepth(depth) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isChosen) CyberCyan else Color(0xFF131A26),
                                    contentColor = if (isChosen) SlateGrayBg else IceWhite
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${depth}-bit" + (if (depth == 32) " float" else ""), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorder)

                // Select default resampling rate
                Column {
                    Text("Default Sampling Rate Frequency", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(48000, 96000).forEach { freq ->
                            val isChosen = defaultSampleRate == freq
                            Button(
                                onClick = { onSelectSampleRate(freq) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isChosen) CyberCyan else Color(0xFF131A26),
                                    contentColor = if (isChosen) SlateGrayBg else IceWhite
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${freq / 1000} kHz HD", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = SurfaceBorder)

                // Export directory indicator
                Column {
                    Text("Default Export Folder Directory", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Auto-creates target directories safely inside sandbox downloads", color = CoolGrayText, fontSize = 9.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF090D16))
                            .padding(8.dp)
                    ) {
                        Text(exportLocation, color = PurpleGlow, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalDivider(color = SurfaceBorder)

                // Pure database and metadata wipe
                Button(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert.copy(alpha = 0.2f), contentColor = RedAlert),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, RedAlert)
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "clear history", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ERASE AUDIT HISTORY & DEC PREFS", fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        },
        containerColor = CardDark
    )
}

@Composable
fun HardwareInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("UNDERSTOOD", color = CyberCyan, fontWeight = FontWeight.Black)
            }
        },
        title = {
            Text(
                "HARDWARE IMPLICATIONS GUIDE",
                color = IceWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "• Hardware vs Software Decoders:\n" +
                    "Hardware implementations utilize dynamic power chips on mobile matrices (such as OMX.qcom or Exynos) to accelerate Dolby Digital/Plus streams with low thermal limits.\n" +
                    "Software configurations utilize local floating compilations (simulation fallback logic) to parse stream channels.",
                    color = IceWhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    "• E-AC3-JOC (Dolby Atmos):\n" +
                    "E-AC3 with JOC (Joint Object Coding) encapsulates spatial metadata overlays. Standard decoders split discrete bed layouts, while Refract can recreate actual spatial panning streams.",
                    color = IceWhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    "• Dolby AC-4 Profiles:\n" +
                    "AC-4 IMS is designed for Immersive Stereo Binaural environments, ideal for headphones.\n" +
                    "AC-4 L4 supports discrete multichannel up to 7.1.4 heights, requiring Android 15 (API 35/36) compatibility for native hardware operations.",
                    color = IceWhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        containerColor = CardDark
    )
}

@Composable
fun SoundVisualizerBars() {
    val infiniteTransition = rememberInfiniteTransition()
    val heights = listOf(26.dp, 38.dp, 48.dp, 30.dp, 44.dp)
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(52.dp)
    ) {
        heights.forEachIndexed { index, baseHeight ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (index * 120), easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(baseHeight * scale)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index % 2 == 0) CyberCyan else PurpleGlow)
            )
        }
    }
}

@Composable
fun ErrorDisplayCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, RedAlert.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
            .testTag("error_card"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error status",
                tint = RedAlert,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("DECODING PIPELINE ERROR", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                message,
                color = IceWhite,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("DISMISS ERRORS", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HistoryFileItem(
    file: File,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isReport = file.extension == "txt"
    val isZip = file.extension == "zip"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, SurfaceBorder), RoundedCornerShape(12.dp))
            .testTag("history_item"),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isReport) PurpleGlow.copy(alpha = 0.2f) 
                            else (if (isZip) AcidGreen.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isReport) Icons.Default.Info else (if (isZip) Icons.Default.CheckCircle else Icons.Default.PlayArrow),
                        contentDescription = "music logo",
                        tint = if (isReport) PurpleGlow else (if (isZip) AcidGreen else CyberCyan),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = file.name,
                        color = IceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isReport) "Loudness specifications text report" else "${String.format(Locale.getDefault(), "%.1f", file.length() / (1024f * 1024f))} MB • ${file.extension.uppercase()}",
                        color = CoolGrayText,
                        fontSize = 10.sp
                    )
                }
            }

            // Quick Actions
            Row {
                if (!isReport && !isZip) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "play/pause",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "share",
                        tint = IceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "delete",
                        tint = RedAlert,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f)), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "empty history",
                tint = CoolGrayText.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No decoded audio components found",
                color = CoolGrayText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                "Processed layers and zipped folders will record here.",
                color = CoolGrayText.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MiniPlayerOverlay(
    playingFileName: String,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)), RoundedCornerShape(12.dp))
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(CyberCyan)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Active Monitor: $playingFileName",
                        color = IceWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayPauseToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = "play/stop",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "close overlay player",
                            tint = RedAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
