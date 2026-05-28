package com.example

import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    var showDiagnostics by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectFile(uri)
        }
    }

    // High frequency gradient background mapping
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SlateGrayBg,
                        Color(0xFF070B14)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Title Bar
            HeaderBar()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Codec Diagnostics status
                item {
                    CodecDiagnosticsCard(
                        supportInfo = supportInfo,
                        isSimulationActive = isSimulationEnabled,
                        showDiagnostics = showDiagnostics,
                        onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                        onToggleSimulation = { viewModel.setSimulationEnabled(it) }
                    )
                }

                // Core interaction stage
                item {
                    when (val state = uiState) {
                        is AudioDecoderViewModel.UIState.Idle -> {
                            FileDropzoneSelector(
                                onSelectClick = { filePickerLauncher.launch("*/*") }
                            )
                        }

                        is AudioDecoderViewModel.UIState.FileSelected -> {
                            FileSelectedCard(
                                name = state.name,
                                info = state.metadata,
                                selectedMode = exportMode,
                                onModeSelect = { viewModel.setExportMode(it) },
                                onCancel = { viewModel.resetToIdle() },
                                onProcess = { viewModel.startDecoding() }
                            )
                        }

                        is AudioDecoderViewModel.UIState.Processing -> {
                            ProcessingCard(
                                fileName = state.originalName,
                                progress = state.progress,
                                statusMsg = state.status
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

                // Saved History List
                if (historyFiles.isNotEmpty()) {
                    item {
                        Text(
                            text = "PREVIOUS DECODED ASSETS",
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
                
                // Add vertical safety space
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Continuous Mini Audio Player Overlay
            if (playingFile != null) {
                MiniPlayerOverlay(
                    playingFileName = playingFile?.name ?: "",
                    isPlaying = isPlaying,
                    onPlayPauseToggle = { playingFile?.let { viewModel.playAudio(it) } },
                    onStop = { viewModel.onStopAudio() }
                )
            }
        }
    }
}

@Composable
fun HeaderBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Styled glowing logo container
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
                text = "Dolby AC-4 Studio",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = IceWhite,
                letterSpacing = 1.sp
            )
            Text(
                text = "Binaural IMS / Multichannel L4 Local Decoder",
                fontSize = 11.sp,
                color = CoolGrayText,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CodecDiagnosticsCard(
    supportInfo: DecoderSupportInfo?,
    isSimulationActive: Boolean,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit,
    onToggleSimulation: (Boolean) -> Unit
) {
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
                            .background(if (supportInfo?.hasAc4Decoder == true) AcidGreen else RedAlert)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System AC-4 Decoder",
                        fontWeight = FontWeight.Bold,
                        color = IceWhite,
                        fontSize = 14.sp
                    )
                }

                Text(
                    text = if (supportInfo?.hasAc4Decoder == true) "SUPPORTED" else "UNSUPPORTED",
                    color = if (supportInfo?.hasAc4Decoder == true) AcidGreen else RedAlert,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (supportInfo?.hasAc4Decoder == true) {
                Text(
                    text = "Hardware/Software decoder registered: ${supportInfo.ac4DecoderNames.joinToString()}",
                    color = CoolGrayText,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "Your device lacks licensing support for Dolby AC-4. Enable simulation engine below to synthesize professional multi-channel PCM sweeps and test the splitting/FLAC configurations.",
                    color = CoolGrayText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // High fidelity simulator toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E2536))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "sim active",
                            tint = PurpleGlow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Synthetic Simulation Engine",
                            color = IceWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable full system codec registry list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDiagnostics() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showDiagnostics) "Hide Codec Registry" else "Show Full Android Audio Codecs",
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
                Divider(color = SurfaceBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = codec.name,
                                    color = IceWhite,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
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
            .height(180.dp)
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
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "SELECT DOLBY AC-4 BITSTREAM",
                    color = IceWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Supports local IMS (stereo binaural) and L4 (multichannel)",
                    color = CoolGrayText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun FileSelectedCard(
    name: String,
    info: DolbyAc4Decoder.DecodedMetadata,
    selectedMode: AudioDecoderViewModel.ExportMode,
    onModeSelect: (AudioDecoderViewModel.ExportMode) -> Unit,
    onCancel: () -> Unit,
    onProcess: () -> Unit
) {
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
            // File descriptor header
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
                        contentDescription = "file info",
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
                        contentDescription = "cancel icon",
                        tint = RedAlert,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // File Specifications Card
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
                        Text("Detected Profile:", color = CoolGrayText, fontSize = 11.sp)
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
                        Text("Channels Layout:", color = CoolGrayText, fontSize = 11.sp)
                        Text("${info.channelCount} Channels", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sampling Frequency:", color = CoolGrayText, fontSize = 11.sp)
                        Text("${info.sampleRate / 1000f} kHz", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duration:", color = CoolGrayText, fontSize = 11.sp)
                        val secs = info.durationUs / 1_000_000L
                        Text(String.format(Locale.getDefault(), "%02d:%02d", secs / 60, secs % 60), color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Export Configuration Selection
            Text(
                text = "EXPORT CONFIGURATION",
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
                    desc = "Downmixes sound channels into spatialized head-coupled binaural stereo. Highly recommended for headphones.",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.StereoBinauralWav,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.StereoBinauralWav) }
                )

                ExportModeOptionTile(
                    title = "Unified Multichannel (WAV)",
                    desc = "Generates a single high-fidelity uncompressed container preserving all discrete channels (5.1/7.1 PCM).",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.WaveMultichannel,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.WaveMultichannel) }
                )

                ExportModeOptionTile(
                    title = "Split Mono Channels (WAV)",
                    desc = "Splits and renders each audio channel element into individual uncompressed WAV files (e.g. Center, Left Surround, etc.)",
                    selected = selectedMode == AudioDecoderViewModel.ExportMode.MonoWavCustomSplit,
                    onClick = { onModeSelect(AudioDecoderViewModel.ExportMode.MonoWavCustomSplit) }
                )

                ExportModeOptionTile(
                    title = "Split Mono Lossless (FLAC)",
                    desc = "Compresses and locks decoded discrete elements natively into individual highly compatible .flac files.",
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
                    text = "START DECODING & EXPORT",
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
    statusMsg: String
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
                // Animated sound waves
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
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .testTag("progress_indicator"),
                color = Brush.horizontalGradient(listOf(CyberCyan, PurpleGlow)) as? Color ?: CyberCyan,
                trackColor = SurfaceBorder
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = String.format(Locale.getDefault(), "%.0f%%", progress * 100f),
                color = IceWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
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
                    text = "DECODING COMPLETED!",
                    color = AcidGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = SurfaceBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "EXPORTED ARTIFACTS (${files.size}):",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateGrayBg)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (file.extension.lowercase(Locale.getDefault()) == "flac") Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                contentDescription = "wav icon",
                                tint = if (file.extension.lowercase(Locale.getDefault()) == "flac") PurpleGlow else CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = file.name,
                                color = IceWhite,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row {
                            IconButton(onClick = { onPlayClick(file) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = if (playingFile == file && isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = "play file",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
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
                    text = "DECODE ANOTHER BITSTREAM",
                    color = IceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
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

            Text("DECODING CONFLICT DETECTED", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                message,
                color = IceWhite,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
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
                        .background(if (file.extension == "flac") PurpleGlow.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (file.extension == "flac") Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = "music logo",
                        tint = if (file.extension == "flac") PurpleGlow else CyberCyan,
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
                        text = "${String.format(Locale.getDefault(), "%.1f", file.length() / (1024f * 1024f))} MB • ${file.extension.uppercase(Locale.getDefault())}",
                        color = CoolGrayText,
                        fontSize = 10.sp
                    )
                }
            }

            // Quick Actions
            Row {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "play/pause",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
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
                contentDescription = "empty",
                tint = CoolGrayText.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No decoded sound files found",
                color = CoolGrayText,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                "Items will appear once you load and decode Dolby AC-4 streams.",
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
                        text = "Playing: $playingFileName",
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
                            contentDescription = "close player",
                            tint = RedAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
