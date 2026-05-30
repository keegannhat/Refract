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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var vulkanAvailable: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        vulkanAvailable = packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1
        )
        
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
                        vulkanAvailable = vulkanAvailable,
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
    vulkanAvailable: Boolean,
    modifier: Modifier = Modifier,
    viewModel: AudioDecoderViewModel = viewModel()
) {
    val context = LocalContext.current
    val isReducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    var logoVisible by remember { mutableStateOf(isReducedMotion) }
    var subtitleVisible by remember { mutableStateOf(isReducedMotion) }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.95f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, easing = LinearOutSlowInEasing),
        label = "logoScale"
    )
    val logoOpacity by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, easing = LinearOutSlowInEasing),
        label = "logoOpacity"
    )
    val subtitleOpacity by animateFloatAsState(
        targetValue = if (subtitleVisible) 1f else 0f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, easing = LinearOutSlowInEasing),
        label = "subtitleOpacity"
    )

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            logoVisible = true
            delay(200)
            subtitleVisible = true
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val supportInfo by viewModel.supportInfo.collectAsState()
    val hardwareEnforcementLevel by viewModel.hardwareEnforcementLevel.collectAsState()
    val exportMode by viewModel.exportMode.collectAsState()
    val showAtmosFallbackBanner by viewModel.showAtmosFallbackBanner.collectAsState()
    val hasSeenOnboarding by viewModel.hasSeenOnboarding.collectAsState()
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
    val isLoudnessReportEnabled by viewModel.isLoudnessReportEnabled.collectAsState()
    val meterLevels by viewModel.meterLevels.collectAsState()
    val lkfsValue by viewModel.lkfsValue.collectAsState()
    val truePeakValue by viewModel.truePeakValue.collectAsState()
    val hasClipWarning by viewModel.hasClipWarning.collectAsState()
    val speakerConfig by viewModel.speakerConfig.collectAsState()
    val masterVolume by viewModel.masterVolume.collectAsState()
    val loopPlayback by viewModel.loopPlayback.collectAsState()
    val playbackElapsedMs by viewModel.playbackElapsedMs.collectAsState()
    val playbackTotalMs by viewModel.playbackTotalMs.collectAsState()
    val isAc4Ims by viewModel.isAc4Ims.collectAsState()

    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHardwareInfoSheet by remember { mutableStateOf(false) }
    var selectedCodecDetail by remember { mutableStateOf<com.example.audio.CodecDetail?>(null) }

    // File picker launcher supporting .ec3, .ac4, .mp4, .m4a, and .ts containers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.selectFile(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.getExportsDir()
    }

    LaunchedEffect(Unit) {
        viewModel.getExportsDir()
    }

    if (!hasSeenOnboarding) {
        com.example.ui.OnboardingScreen(
            supportInfo = supportInfo,
            onComplete = { viewModel.completeOnboarding() }
        )
        return
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
                            .scale(logoScale)
                            .alpha(logoOpacity)
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
                    Column(
                        modifier = Modifier
                            .alpha(subtitleOpacity)
                    ) {
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
                        hardwareEnforcementLevel = hardwareEnforcementLevel,
                        showDiagnostics = showDiagnostics,
                        onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                        onHelpClick = { showHardwareInfoSheet = true },
                        onShowCodecDetails = { selectedCodecDetail = it }
                    )
                }

                if (showAtmosFallbackBanner) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A1F11))
                                .border(BorderStroke(1.dp, Color(0xFFD3A233).copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning",
                                            tint = Color(0xFFD3A233),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Fallback Notification",
                                            color = Color(0xFFD3A233),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissFallbackBanner() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = CoolGrayText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Atmos object rendering unavailable on this device. Playing the Dolby Digital Plus 5.1 core instead.",
                                    color = CoolGrayText,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Core Interactive Stage
                item {
                    when (val state = uiState) {
                        is AudioDecoderViewModel.UIState.Idle -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FileDropzoneSelector(
                                    onSelectClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                                )

                                // Real recent documents library
                                if (recentDocsList.isNotEmpty()) {
                                    Text(
                                        text = "RECENT FILES",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                                    )
                                    recentDocsList.forEachIndexed { index, record ->
                                        RecentFileItem(
                                            record = record,
                                            index = index,
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
                                onSpeakerConfigChange = { viewModel.setSpeakerConfig(it) },
                                isIms = isAc4Ims
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
                                onDismiss = { viewModel.resetToIdle() },
                                onAction = state.onAction,
                                actionLabel = state.actionLabel
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
                isLoudnessReportEnabled = isLoudnessReportEnabled,
                vulkanAvailable = vulkanAvailable,
                onToggleWaveform = { viewModel.setWaveformMode(it) },
                onSelectBitDepth = { viewModel.setDefaultBitDepth(it) },
                onSelectSampleRate = { viewModel.setDefaultSampleRate(it) },
                onToggleLoudnessReport = { viewModel.setLoudnessReportEnabled(it) },
                onClearHistory = {
                    viewModel.clearHistory()
                    showSettingsDialog = false
                },
                onDismiss = { showSettingsDialog = false }
            )
        }

        selectedCodecDetail?.let { codec ->
            CodecDetailDialog(
                codec = codec,
                onDismiss = { selectedCodecDetail = null }
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
fun CodecDetailDialog(codec: CodecDetail, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Codec Registry Metadata", color = IceWhite, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = codec.name,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Type: ${if (codec.isEncoder) "Encoder" else "Decoder"} • ${if (codec.name.lowercase().contains("google")) "Software" else "Hardware Acceleration"}",
                    color = CoolGrayText,
                    fontSize = 12.sp
                )
                
                HorizontalDivider(color = SurfaceBorder)
                
                Column {
                    Text("Supported Mime Type", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(codec.mimeType, color = CoolGrayText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                if (codec.maxChannels > 0) {
                    Column {
                        Text("Maximum Target Channels", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(codec.maxChannels.toString(), color = CoolGrayText, fontSize = 12.sp)
                    }
                }

                if (codec.supportedSampleRates.isNotEmpty()) {
                    Column {
                        Text("Supported Frequencies Setup", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(codec.supportedSampleRates.joinToString(" Hz, ") + " Hz", color = CoolGrayText, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(
                        "Codec Details", 
                        "Name: ${codec.name}\nMime: ${codec.mimeType}\nChannels: ${codec.maxChannels}\nSample Rates: ${codec.supportedSampleRates.joinToString()}"
                    )
                    clipboard.setPrimaryClip(clip)
                    copied = true
                }
            ) {
                Text(if (copied) "COPIED" else "COPY TO CLIPBOARD", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = CoolGrayText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CardDark,
        titleContentColor = IceWhite,
        textContentColor = CoolGrayText
    )
}

@Composable
fun AnimatedCodecRow(
    title: String,
    statusText: String,
    statusColor: Color,
    isReducedMotion: Boolean,
    delayMs: Int,
    onClick: () -> Unit
) {
    var visible by remember { mutableStateOf(isReducedMotion) }
    
    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else -20f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, delayMillis = delayMs, easing = LinearOutSlowInEasing),
        label = "offsetX"
    )
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, delayMillis = delayMs, easing = LinearOutSlowInEasing),
        label = "opacity"
    )

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            visible = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .graphicsLayer {
                translationX = offsetX
                alpha = opacity
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = CoolGrayText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
        )
    }
}

@Composable
fun CapabilitiesHardwareCard(
    supportInfo: DecoderSupportInfo?,
    hardwareEnforcementLevel: AudioDecoderViewModel.HardwareEnforcementLevel,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit,
    onHelpClick: () -> Unit,
    onShowCodecDetails: (CodecDetail) -> Unit
) {
    val context = LocalContext.current
    val isReducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

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

    val enforcementColor = when (hardwareEnforcementLevel) {
        AudioDecoderViewModel.HardwareEnforcementLevel.FULL -> AcidGreen
        AudioDecoderViewModel.HardwareEnforcementLevel.PARTIAL -> CyberCyan
        AudioDecoderViewModel.HardwareEnforcementLevel.SOFTWARE -> PurpleGlow
        AudioDecoderViewModel.HardwareEnforcementLevel.NONE -> RedAlert
    }

    val enforcementText = when (hardwareEnforcementLevel) {
        AudioDecoderViewModel.HardwareEnforcementLevel.FULL -> "HARDWARE ENFORCED"
        AudioDecoderViewModel.HardwareEnforcementLevel.PARTIAL -> "PARTIAL HARDWARE"
        AudioDecoderViewModel.HardwareEnforcementLevel.SOFTWARE -> "SOFTWARE MODE"
        AudioDecoderViewModel.HardwareEnforcementLevel.NONE -> "NO DOLBY DECODERS"
    }

    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

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
                            .alpha(if (isReducedMotion) 1f else pulseAlpha)
                            .background(enforcementColor)
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
                    text = enforcementText,
                    color = enforcementColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dual Badge Matrix Block
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                
                val eac3Codec = supportInfo?.availableCodecs?.find {
                    it.mimeType.contains("eac3", ignoreCase = true) && !it.isEncoder
                }
                val isEac3Hw = hasEac3 && !eac3DecoderName.contains("google", ignoreCase = true)
                AnimatedCodecRow(
                    title = "E-AC3-JOC (Dolby Atmos Objects)",
                    statusText = if (hasEac3) (if (isEac3Hw) "✅ Hardware ($eac3DecoderName)" else "✅ Software Fallback ($eac3DecoderName)") else "⚠️ Emulated (no hardware decoder)",
                    statusColor = if (hasEac3) (if (isEac3Hw) AcidGreen else CyberCyan) else PurpleGlow,
                    isReducedMotion = isReducedMotion,
                    delayMs = 0,
                    onClick = { 
                        eac3Codec?.let { onShowCodecDetails(it) } 
                    }
                )

                val ac4Codec = supportInfo?.availableCodecs?.find {
                    it.name == ac4DecoderName
                } ?: CodecDetail(ac4DecoderName, "audio/ac4", false, 0)
                AnimatedCodecRow(
                    title = "AC-4 IMS Binaural",
                    statusText = if (hasAc4) "✅ Native ($ac4DecoderName)" else "✅ Software fallback",
                    statusColor = if (hasAc4) AcidGreen else CyberCyan,
                    isReducedMotion = isReducedMotion,
                    delayMs = 80,
                    onClick = { onShowCodecDetails(ac4Codec) }
                )

                AnimatedCodecRow(
                    title = "AC-4 L4 Multichannel Support",
                    statusText = if (Build.VERSION.SDK_INT >= 36) {
                        if (hasAc4) "✅ Native hardware" else "⚠️ Requires Android 16 · No decoder found"
                    } else {
                        "⚠️ Requires Android 16 (you are on Android ${Build.VERSION.SDK_INT})"
                    },
                    statusColor = if (Build.VERSION.SDK_INT >= 36 && hasAc4) AcidGreen else RedAlert,
                    isReducedMotion = isReducedMotion,
                    delayMs = 160,
                    onClick = { onShowCodecDetails(ac4Codec) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

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
    val context = LocalContext.current
    val isReducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.97f else 1f,
        animationSpec = tween(80),
        label = "pressScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(2.dp, Brush.sweepGradient(listOf(CyberCyan, PurpleGlow, CyberCyan))),
                RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current) { onSelectClick() }
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
                    text = "Open Audio File",
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
    index: Int,
    onLoadClick: () -> Unit
) {
    val context = LocalContext.current
    val isReducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    var visible by remember { mutableStateOf(isReducedMotion) }
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, delayMillis = index * 60, easing = LinearOutSlowInEasing),
        label = "offsetY"
    )
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (isReducedMotion) 0 else 300, delayMillis = index * 60, easing = LinearOutSlowInEasing),
        label = "opacity"
    )

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            visible = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                alpha = opacity
            }
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
    onSpeakerConfigChange: (String) -> Unit,
    isIms: Boolean = false
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
                val layoutOptions = listOf(
                    "Mono"  to false,
                    "Stereo" to false,
                    "5.1"   to isIms,   // disabled when IMS
                    "7.1"   to isIms,
                    "7.1.4" to isIms,
                    "9.1.6" to isIms
                )
                layoutOptions.forEach { (layout, disabled) ->
                    val isLayoutActive = speakerConfig == layout
                    val tooHighForBinaural = isBinauralSource && layout != "Mono" && layout != "Stereo" && layout != "5.1"
                    val isButtonEnabled = !disabled
                    
                    Button(
                        onClick = { if (isButtonEnabled && !tooHighForBinaural) onSpeakerConfigChange(layout) },
                        enabled = isButtonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLayoutActive) CyberCyan else (if (tooHighForBinaural) Color.Transparent else Color(0xFF131A26)),
                            contentColor = if (isLayoutActive) SlateGrayBg else (if (tooHighForBinaural) CoolGrayText.copy(alpha = 0.4f) else IceWhite),
                            disabledContainerColor = Color(0xFF131A26).copy(alpha = 0.5f),
                            disabledContentColor = IceWhite.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .then(if (disabled) Modifier.alpha(0.35f) else Modifier),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = if (tooHighForBinaural && !disabled) BorderStroke(1.dp, SurfaceBorder.copy(alpha = 0.5f)) else null
                    ) {
                        Text(layout, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isIms) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "IMS limit warning",
                        tint = PurpleGlow,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Not available for AC-4 IMS (2.0 max)",
                        color = PurpleGlow,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (isBinauralSource) {
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
                        text = "Dolby Binaural AC-4 IMS is hardcapped to 5.1 bed elements in dynamic export models.",
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
                text = "Export Format",
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
                    title = "Stereo Downmix",
                    desc = "Downmixes discrete sound coordinates into spatialized binaural stereo WAV (Recommended for regular headphones).",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.StereoBinauralWav,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.StereoBinauralWav) }
                )

                ExportModeOptionTile(
                    title = "Multichannel WAV",
                    desc = "Generates a single multichannel uncompressed WAV preserving layout mapping coordinates (Ideal for DAW editing).",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.WaveMultichannel,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.WaveMultichannel) }
                )

                val allowSplit = info.channelCount > 2
                val tooltipMsg = if (allowSplit) null else "AC-4 IMS is 2.0 — split export not applicable."

                ExportModeOptionTile(
                    title = "Split WAV (per channel)",
                    desc = "Splits each audio track coordinate into individual discrete mono WAV files.",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.MonoWavCustomSplit,
                    enabled = allowSplit,
                    tooltip = tooltipMsg,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.MonoWavCustomSplit) }
                )

                ExportModeOptionTile(
                    title = "Split FLAC (zipped)",
                    desc = "Compresses and encapsulates split channel flacs natively into a single manageable .zip archive.",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.MonoFlacCustomSplit,
                    enabled = allowSplit,
                    tooltip = tooltipMsg,
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
                    text = "Export",
                    color = SlateGrayBg,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportModeOptionTile(
    title: String,
    desc: String,
    selected: Boolean,
    enabled: Boolean = true,
    tooltip: String? = null,
    onClick: () -> Unit
) {
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!enabled && tooltip != null) {
                androidx.compose.material3.PlainTooltip {
                    Text(tooltip)
                }
            }
        },
        state = androidx.compose.material3.rememberTooltipState()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .alpha(if (enabled) 1f else 0.38f)
                .background(if (selected) CyberCyan.copy(alpha = 0.08f) else Color.Transparent)
                .border(
                    1.dp,
                    if (selected) CyberCyan else SurfaceBorder,
                    RoundedCornerShape(8.dp)
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = if (enabled) onClick else null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = CyberCyan,
                    unselectedColor = CoolGrayText,
                    disabledSelectedColor = CyberCyan.copy(alpha = 0.38f),
                    disabledUnselectedColor = CoolGrayText.copy(alpha = 0.38f)
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
                    lineHeight = 15.sp
                )
            }
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
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(200, easing = LinearOutSlowInEasing),
        label = "progress"
    )

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
                progress = { animatedProgress },
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

            val reportFile = files.find { it.extension == "txt" }
            if (reportFile != null && reportFile.exists()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = SurfaceBorder)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "LOUDNESS REPORT SPECIFICATIONS:",
                    color = PurpleGlow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                var reportContent by remember { mutableStateOf("") }
                LaunchedEffect(reportFile) {
                    try {
                        reportContent = reportFile.readText()
                    } catch (e: Exception) {
                        reportContent = "Failed to load loudness details specs: ${e.message}"
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090D16))
                        .border(BorderStroke(1.dp, SurfaceBorder), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = reportContent,
                        color = CoolGrayText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
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
    isLoudnessReportEnabled: Boolean,
    vulkanAvailable: Boolean,
    onToggleWaveform: (Boolean) -> Unit,
    onSelectBitDepth: (Int) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onToggleLoudnessReport: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                "SETTINGS",
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
                        Text("Waveform Display", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Show audio waveform with pinch-to-zoom and tap-to-seek", color = CoolGrayText, fontSize = 9.sp)
                    }
                    Switch(
                        checked = waveformMode,
                        onCheckedChange = onToggleWaveform,
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.4f))
                    )
                }

                HorizontalDivider(color = SurfaceBorder)

                // Loudness Report toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Export Loudness Report", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Saves a BS.1770-4 loudness summary alongside exported audio", color = CoolGrayText, fontSize = 9.sp)
                    }
                    Switch(
                        checked = isLoudnessReportEnabled,
                        onCheckedChange = onToggleLoudnessReport,
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.4f))
                    )
                }

                HorizontalDivider(color = SurfaceBorder)

                // Select quantization depth resolution
                Column {
                    Text("Bit Depth", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Sample Rate", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Export Folder", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Files are saved here after export", color = CoolGrayText, fontSize = 9.sp)
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

                // Graphics API
                Column {
                    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val glVersion = am.deviceConfigurationInfo.glEsVersion
                    
                    Text("Graphics API", color = IceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (vulkanAvailable) "Vulkan Enabled" else "OpenGL ES (version $glVersion fallback)", color = AcidGreen, fontSize = 11.sp)
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
                    Text("CLEAR HISTORY & RESET SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Black)
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
                Text("OK", color = CyberCyan, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                "DOLBY DECODER GUIDE",
                color = IceWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "• Hardware vs Software Decoders:\n" +
                    "Hardware decoders use the device's dedicated audio processors to decode Dolby formats efficiently. Software decoders use the CPU to process the audio stream, which can use more battery but serves as a reliable fallback.",
                    color = IceWhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    "• E-AC3-JOC (Dolby Atmos):\n" +
                    "E-AC3 with JOC (Joint Object Coding) delivers immersive spatial audio. Devices with hardware support can decode full spatial mixes, while software decoders will fallback to standard 5.1 multichannel audio.",
                    color = IceWhite,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    "• Dolby AC-4 Profiles:\n" +
                    "AC-4 IMS (Immersive Stereo) decodes spatial audio downmixed for standard headphones. AC-4 L4 supports discrete multichannel layouts (such as 5.1.4 or 7.1.4) which require native hardware support available in Android 16 (API 36).",
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
    onDismiss: () -> Unit,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    val context = LocalContext.current
    val isReducedMotion = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f

    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!isReducedMotion) {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.keyframes {
                    durationMillis = 400
                    0f at 0
                    -8f at 80
                    8f at 160
                    -4f at 240
                    4f at 320
                    0f at 400
                }
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = offsetX.value }
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

            Text("Export Failed", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (message.contains("Permission Denial", ignoreCase = true) || message.contains("SecurityException", ignoreCase = true)) {
                    "File access was denied. Please re-select the file from the file picker."
                } else {
                    message
                },
                color = IceWhite,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (onAction != null && actionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("DISMISS", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            onDismiss()
                            onAction()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AcidGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = actionLabel.uppercase(java.util.Locale.getDefault()),
                            color = CardDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (message.contains("Permission Denial", ignoreCase = true) || message.contains("SecurityException", ignoreCase = true)) "Re-open File" else "DISMISS ERRORS",
                        color = IceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
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
                "No exported files yet",
                color = CoolGrayText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                "Your decoded and exported audio files will appear here.",
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
