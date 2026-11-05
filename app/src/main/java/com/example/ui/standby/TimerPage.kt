package com.example.ui.standby

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val OledCanvasBg = Color(0xFF030407)
private val OledCardBg = Color(0xFF0B0E15)
private val OledCardElevated = Color(0xFF121722)
private val OledBorderSubtle = Color(0xFF222938)

@Composable
fun TimerPage(
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStandbyHaptics()
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF38BDF8)

    val toolItems = remember {
        listOf(
            Triple("Focus", "POMODORO", Icons.Default.Bolt),
            Triple("Chrono", "STOPWATCH", Icons.Default.Timer),
            Triple("Gallery", "SLIDESHOW", Icons.Default.Collections),
            Triple("Charge", "CONNECTED DEVICES", Icons.Default.BatteryChargingFull),
            Triple("Smart Home", "GOOGLE HOME", Icons.Default.Home),
            Triple("Ambient Light", "FLASH & LEVEL", Icons.Default.FlashlightOn)
        )
    }

    val initialMode = remember { viewModel.timerModeIndex.value.coerceIn(0, toolItems.lastIndex) }
    val pagerState = rememberPagerState(initialPage = initialMode, pageCount = { toolItems.size })
    val coroutineScope = rememberCoroutineScope()

    var hasInitPager by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        if (!hasInitPager) {
            hasInitPager = true
        } else {
            haptics.tick()
            viewModel.setTimerModeIndex(pagerState.currentPage)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_hub_page"),
        color = OledCanvasBg
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FocusPomodoroTimer(viewModel)
                    1 -> HighTechStopwatch(viewModel)
                    2 -> StandbyGallerySlideshowTool(viewModel)
                    3 -> StandbyConnectedDevicesChargeTool(viewModel)
                    4 -> StandbySmartDevicesControlTool(viewModel)
                    5 -> StandbyDockLabTool(viewModel)
                }
            }

            // Right-Edge Vertical Mode Indicator Dots (preserves timer_mode_dot_$i testTags)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                shape = CircleShape,
                color = OledCardElevated.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, OledBorderSubtle)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in toolItems.indices) {
                        val isSelected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .size(width = 7.dp, height = if (isSelected) 16.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) accentColor
                                    else Color.White.copy(alpha = 0.28f)
                                )
                                .clickable {
                                    haptics.tick()
                                    viewModel.setTimerModeIndex(i)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                }
                                .testTag("timer_mode_dot_$i")
                        )
                    }
                }
            }
        }
    }
}

/**
 * TOOL 01: OLED StandBy Focus Pomodoro Matrix
 */
@Composable
fun FocusPomodoroTimer(viewModel: StandbyViewModel) {
    val haptics = rememberStandbyHaptics()

    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()
    val focusMinutes by viewModel.pomodoroFocusMinutes.collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    val gradientColors = if (gradientIdx > 0 && gradientIdx < viewModel.gradients.size) {
        viewModel.gradients[gradientIdx].second
    } else null

    var currentSegmentIndex by remember { mutableIntStateOf(0) }

    fun getDurationForSegment(index: Int, focusMins: Int): Int {
        return when (index) {
            0, 2, 4, 6 -> focusMins * 60
            1, 3, 5 -> 5 * 60
            7 -> 15 * 60
            else -> focusMins * 60
        }
    }

    fun getSegmentLabel(index: Int): String {
        return when (index) {
            0 -> "Focus Session 01"
            1 -> "Short Recovery 01"
            2 -> "Focus Session 02"
            3 -> "Short Recovery 02"
            4 -> "Focus Session 03"
            5 -> "Short Recovery 03"
            6 -> "Focus Session 04"
            7 -> "Deep Recovery Break"
            else -> ""
        }
    }

    var initialSeconds by remember(focusMinutes) {
        mutableIntStateOf(getDurationForSegment(currentSegmentIndex, focusMinutes))
    }
    var secondsLeft by remember(focusMinutes) {
        mutableIntStateOf(getDurationForSegment(currentSegmentIndex, focusMinutes))
    }
    var isRunning by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, secondsLeft) {
        if (isRunning && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
            if (secondsLeft == 0) {
                haptics.confirm()
                isRunning = false
                currentSegmentIndex = (currentSegmentIndex + 1) % 8
                val nextDur = getDurationForSegment(currentSegmentIndex, focusMinutes)
                initialSeconds = nextDur
                secondsLeft = nextDur
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val isCompactWidth = maxWidth < 640.dp
        val outerHPad = if (isCompactWidth) 16.dp else 28.dp
        val outerVPad = if (isCompactHeight) 12.dp else 18.dp
        val paneSpacing = if (isCompactWidth) 12.dp else 18.dp

        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f)))
        }

        // Unified Single Full-Screen Pomodoro Card (no 2-pane split)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 14.dp, top = outerVPad, bottom = outerVPad),
            shape = RoundedCornerShape(28.dp),
            color = OledCardBg,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                val dialSize = (maxHeight * 0.62f).coerceIn(135.dp, 220.dp)
                val isSmallDial = dialSize < 175.dp
                val timerFontSize = (dialSize.value * 0.22f).coerceIn(28f, 48f).sp
                val strokeWidthDp = if (isSmallDial) 7.dp else 9.dp

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Bar: Visual Duration Chips + Config Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(15, 25, 45, 60).forEach { mins ->
                                val isSel = focusMinutes == mins
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        viewModel.setPomodoroFocusMinutes(mins)
                                        isRunning = false
                                        val nextDur = getDurationForSegment(currentSegmentIndex, mins)
                                        initialSeconds = nextDur
                                        secondsLeft = nextDur
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSel) accentColor.copy(alpha = 0.24f) else OledCardElevated,
                                    border = BorderStroke(1.dp, if (isSel) accentColor else OledBorderSubtle)
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSel) accentColor else Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                haptics.tick()
                                showConfigDialog = true
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(OledCardElevated)
                                .border(1.dp, OledBorderSubtle, CircleShape)
                                .testTag("pomodoro_config_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Set Focus Duration",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Center: 60-Tick StandBy Countdown Dial + Start / Reset Controls
                    Box(
                        modifier = Modifier.size(dialSize),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = if (initialSeconds > 0) secondsLeft.toFloat() / initialSeconds.toFloat() else 1f
                        val activeTickBoundary = (progress * 60).roundToInt()

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = strokeWidthDp.toPx()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val outerRadius = (size.minDimension / 2f) - 2.dp.toPx()
                            val arcRadius = outerRadius - 9.dp.toPx()

                            for (i in 0 until 60) {
                                val angleRad = ((i * 6f - 90f) * PI / 180.0).toFloat()
                                val isActiveTick = i <= activeTickBoundary
                                val tickLen = if (i % 5 == 0) 6.dp.toPx() else 3.5.dp.toPx()
                                drawLine(
                                    color = if (isActiveTick) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f),
                                    start = Offset(cx + cos(angleRad) * (outerRadius - tickLen), cy + sin(angleRad) * (outerRadius - tickLen)),
                                    end = Offset(cx + cos(angleRad) * outerRadius, cy + sin(angleRad) * outerRadius),
                                    strokeWidth = if (i % 5 == 0) 2.5f else 1.4f,
                                    cap = StrokeCap.Round
                                )
                            }

                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = arcRadius,
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                color = accentColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(cx - arcRadius, cy - arcRadius),
                                size = Size(arcRadius * 2f, arcRadius * 2f),
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val minutes = secondsLeft / 60
                            val secs = secondsLeft % 60
                            val timerString = String.format(Locale.US, "%02d:%02d", minutes, secs)

                            val pomodoroTextStyle = androidx.compose.ui.text.TextStyle(
                                color = if (gradientColors == null && !outlineVal) Color.White else Color.Unspecified,
                                fontSize = timerFontSize,
                                fontWeight = FontWeight.Black,
                                fontFamily = customFont,
                                drawStyle = if (outlineVal) Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                                shadow = if (glowVal && !outlineVal) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = accentColor.copy(alpha = 0.65f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 20f
                                    )
                                } else null
                            )
                            val textGradientModifier = if (gradientColors != null) {
                                Modifier
                                    .graphicsLayer(alpha = 0.99f)
                                    .drawWithCache {
                                        val brush = Brush.linearGradient(gradientColors)
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                                        }
                                    }
                            } else Modifier

                            Text(
                                text = timerString,
                                style = pomodoroTextStyle,
                                maxLines = 1,
                                softWrap = false,
                                modifier = textGradientModifier
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        haptics.confirm()
                                        isRunning = !isRunning
                                    },
                                    modifier = Modifier.testTag("pomodoro_start_pause_btn"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = Color.Black
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isRunning) "Pause" else "Start",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        isRunning = false
                                        secondsLeft = initialSeconds
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(OledCardElevated)
                                        .border(1.dp, OledBorderSubtle, CircleShape)
                                        .testTag("pomodoro_reset_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Timer",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Bar: 8-Stage Visual Cycle Indicators
                    Surface(
                        color = OledCardElevated,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, OledBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Icons.Default.Adjust to "P1",
                                Icons.Default.Coffee to "B1",
                                Icons.Default.Adjust to "P2",
                                Icons.Default.Coffee to "B2",
                                Icons.Default.Adjust to "P3",
                                Icons.Default.Coffee to "B3",
                                Icons.Default.Adjust to "P4",
                                Icons.AutoMirrored.Filled.DirectionsRun to "L"
                            ).forEachIndexed { index, (icon, label) ->
                                val isCompleted = index < currentSegmentIndex
                                val isCurrent = index == currentSegmentIndex

                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        isRunning = false
                                        currentSegmentIndex = index
                                        val duration = getDurationForSegment(index, focusMinutes)
                                        initialSeconds = duration
                                        secondsLeft = duration
                                    },
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = when {
                                        isCurrent -> accentColor
                                        isCompleted -> accentColor.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = when {
                                                isCurrent -> Color.Black
                                                isCompleted -> accentColor
                                                else -> Color.White.copy(alpha = 0.40f)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = OledCardElevated,
            title = {
                Text(
                    text = "Focus Session Length",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose your preferred Pomodoro duration:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSel = focusMinutes == mins
                        Surface(
                            onClick = {
                                haptics.confirm()
                                viewModel.setPomodoroFocusMinutes(mins)
                                isRunning = false
                                val nextDur = getDurationForSegment(currentSegmentIndex, mins)
                                initialSeconds = nextDur
                                secondsLeft = nextDur
                                showConfigDialog = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) accentColor.copy(alpha = 0.22f) else OledCardBg,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSel) accentColor else OledBorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pomodoro_preset_$mins")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$mins Minutes",
                                    color = if (isSel) accentColor else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Close", color = accentColor)
                }
            }
        )
    }
}

/**
 * TOOL 02: OLED StandBy Telemetry Chronograph Stopwatch
 */
@Composable
fun HighTechStopwatch(viewModel: StandbyViewModel) {
    val haptics = rememberStandbyHaptics()

    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    val gradientColors = if (gradientIdx > 0 && gradientIdx < viewModel.gradients.size) {
        viewModel.gradients[gradientIdx].second
    } else null

    var isRunning by remember { mutableStateOf(false) }
    var timeMillis by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }
    var lastRecordedTime by remember { mutableLongStateOf(0L) }
    var showLapDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastTime = System.currentTimeMillis()
            while (isRunning) {
                delay(16)
                val now = System.currentTimeMillis()
                timeMillis += (now - lastTime)
                lastTime = now
            }
        }
    }

    fun formatDisplayTime(millis: Long): Pair<String, String> {
        val secondsTotal = millis / 1000
        val min = secondsTotal / 60
        val sec = secondsTotal % 60
        val centis = (millis % 1000) / 10
        val mainText = String.format(Locale.US, "%02d:%02d", min, sec)
        val centisText = String.format(Locale.US, ".%02d", centis)
        return Pair(mainText, centisText)
    }

    val canShowLapTimings = !isRunning && (laps.isNotEmpty() || timeMillis > 0L)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val outerHPad = if (maxWidth < 640.dp) 16.dp else 28.dp
        val outerVPad = if (isCompactHeight) 12.dp else 18.dp

        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f)))
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 14.dp, top = outerVPad, bottom = outerVPad + 32.dp),
            shape = RoundedCornerShape(26.dp),
            color = OledCardBg,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                    .padding(if (isCompactHeight) 14.dp else 22.dp)
            ) {
                val mainFontSize = (minOf(maxWidth.value * 0.16f, maxHeight.value * 0.30f)).coerceIn(46f, 84f).sp
                val centisFontSize = (mainFontSize.value * 0.50f).sp

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Chrono Telemetry Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.42f))
                        ) {
                            Text(
                                text = "02 • PRECISION CHRONOGRAPH",
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = if (isRunning) "LIVE TELEMETRY • 60HZ" else "STANDBY READY",
                            color = if (isRunning) Color(0xFF34D399) else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Center Stopwatch Readout
                    val stopwatchGradientModifier = if (gradientColors != null) {
                        Modifier
                            .graphicsLayer(alpha = 0.99f)
                            .drawWithCache {
                                val brush = Brush.linearGradient(gradientColors)
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                                }
                            }
                    } else Modifier

                    val mainTextStyle = androidx.compose.ui.text.TextStyle(
                        color = if (gradientColors == null && !outlineVal) Color.White else Color.Unspecified,
                        fontSize = mainFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = customFont,
                        drawStyle = if (outlineVal) Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                        shadow = if (glowVal && !outlineVal) {
                            androidx.compose.ui.graphics.Shadow(
                                color = accentColor.copy(alpha = 0.55f),
                                offset = Offset(0f, 0f),
                                blurRadius = 20f
                            )
                        } else null
                    )
                    val centisTextStyle = androidx.compose.ui.text.TextStyle(
                        color = if (gradientColors == null && !outlineVal) accentColor else Color.Unspecified,
                        fontSize = centisFontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont,
                        drawStyle = if (outlineVal) Stroke(width = 2.5f) else androidx.compose.ui.graphics.drawscope.Fill
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = stopwatchGradientModifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = canShowLapTimings) {
                                haptics.tick()
                                showLapDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val formatted = formatDisplayTime(timeMillis)
                        Text(
                            text = formatted.first,
                            style = mainTextStyle,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = formatted.second,
                            style = centisTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(bottom = if (isCompactHeight) 4.dp else 8.dp)
                        )
                    }

                    if (laps.isNotEmpty() || (!isRunning && timeMillis > 0L)) {
                        val count = laps.size
                        val lapTextValue = when {
                            isRunning && count > 0 -> {
                                val activeLapProgress = (timeMillis - lastRecordedTime).coerceAtLeast(0L)
                                val activeFormatted = formatDisplayTime(activeLapProgress)
                                "Lap ${count + 1} • ${activeFormatted.first}${activeFormatted.second}"
                            }
                            !isRunning && count > 0 -> {
                                val lastLapFormatted = formatDisplayTime(laps.last())
                                "Lap $count • ${lastLapFormatted.first}${lastLapFormatted.second}"
                            }
                            else -> {
                                val stoppedFormatted = formatDisplayTime(timeMillis)
                                "Lap 1 • ${stoppedFormatted.first}${stoppedFormatted.second}"
                            }
                        }

                        Surface(
                            onClick = {
                                if (!isRunning) {
                                    haptics.tick()
                                    showLapDialog = true
                                }
                            },
                            enabled = !isRunning,
                            color = OledCardElevated,
                            shape = CircleShape,
                            border = BorderStroke(
                                1.dp,
                                if (!isRunning) accentColor.copy(alpha = 0.65f) else OledBorderSubtle
                            ),
                            modifier = Modifier.testTag("stopwatch_lap_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isCompactHeight) 14.dp else 20.dp,
                                    vertical = if (isCompactHeight) 6.dp else 9.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Lap Timings",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lapTextValue,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!isRunning) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Show Lap Timings",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    val sideBtnSize = if (isCompactHeight) 48.dp else 54.dp
                    val mainBtnWidth = if (isCompactHeight) 78.dp else 90.dp
                    val mainBtnHeight = if (isCompactHeight) 50.dp else 58.dp

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 18.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                isRunning = false
                                timeMillis = 0L
                                laps.clear()
                                lastRecordedTime = 0L
                                showLapDialog = false
                            },
                            modifier = Modifier
                                .size(sideBtnSize)
                                .clip(CircleShape)
                                .background(Color(0xFF2B1218))
                                .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.45f), CircleShape)
                                .testTag("stopwatch_reset_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Stopwatch",
                                tint = Color(0xFFFDA4AF),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                haptics.confirm()
                                isRunning = !isRunning
                            },
                            modifier = Modifier
                                .size(width = mainBtnWidth, height = mainBtnHeight)
                                .testTag("stopwatch_start_pause_btn"),
                            shape = RoundedCornerShape(20.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Stopwatch",
                                modifier = Modifier.size(if (isCompactHeight) 26.dp else 30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isRunning) {
                                    haptics.tick()
                                    val nextLap = timeMillis
                                    laps.add((nextLap - lastRecordedTime).coerceAtLeast(0L))
                                    lastRecordedTime = nextLap
                                } else if (laps.isNotEmpty() || timeMillis > 0L) {
                                    haptics.tick()
                                    showLapDialog = true
                                }
                            },
                            enabled = isRunning || laps.isNotEmpty() || timeMillis > 0L,
                            modifier = Modifier
                                .size(sideBtnSize)
                                .clip(CircleShape)
                                .background(OledCardElevated)
                                .border(1.dp, OledBorderSubtle, CircleShape)
                                .testTag("stopwatch_lap_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = if (isRunning) "Record Lap Split" else "View Lap Timings",
                                tint = if (isRunning || laps.isNotEmpty() || timeMillis > 0L) accentColor else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLapDialog && !isRunning) {
        val displayLaps = remember(laps.size, timeMillis, lastRecordedTime) {
            val result = laps.toMutableList()
            val unrecordedRemainder = timeMillis - lastRecordedTime
            if (result.isEmpty() && timeMillis > 0L) {
                result.add(timeMillis)
            } else if (unrecordedRemainder > 0L) {
                result.add(unrecordedRemainder)
            }
            result
        }
        val fastestIdx = if (displayLaps.size > 1) displayLaps.indices.minByOrNull { displayLaps[it] } else null
        val slowestIdx = if (displayLaps.size > 1) displayLaps.indices.maxByOrNull { displayLaps[it] } else null

        AlertDialog(
            onDismissRequest = { showLapDialog = false },
            modifier = Modifier.testTag("lap_timings_dialog"),
            shape = RoundedCornerShape(28.dp),
            containerColor = OledCardElevated,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Lap Split Telemetry",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.2f)
                    ) {
                        val totalFmt = formatDisplayTime(timeMillis)
                        Text(
                            text = "Total ${totalFmt.first}${totalFmt.second}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(displayLaps) { idx, lapDur ->
                            val isFastest = idx == fastestIdx
                            val isSlowest = idx == slowestIdx
                            val cumulativeMillis = displayLaps.take(idx + 1).sum()
                            val lapFmt = formatDisplayTime(lapDur)
                            val cumFmt = formatDisplayTime(cumulativeMillis)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = OledCardBg,
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        isFastest -> Color(0xFF10B981).copy(alpha = 0.6f)
                                        isSlowest -> Color(0xFFF43F5E).copy(alpha = 0.6f)
                                        else -> OledBorderSubtle
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "LAP %02d", idx + 1),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White
                                        )
                                        if (isFastest) {
                                            Text(
                                                text = "FASTEST",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF10B981)
                                            )
                                        } else if (isSlowest) {
                                            Text(
                                                text = "SLOWEST",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFF43F5E)
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "+${lapFmt.first}${lapFmt.second}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor
                                        )
                                        Text(
                                            text = "${cumFmt.first}${cumFmt.second}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLapDialog = false }) {
                    Text("Done", color = accentColor, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private data class CuratedStandbySlide(
    val id: String,
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val accent: Color
)

private val BuiltInStandbyArtworks = listOf(
    CuratedStandbySlide(
        id = "builtin_aurora",
        title = "Aurora Borealis Dock",
        subtitle = "SOLAR MAGNETOSPHERE • 68°N",
        colors = listOf(Color(0xFF041B2D), Color(0xFF064E3B), Color(0xFF020617)),
        accent = Color(0xFF34D399)
    ),
    CuratedStandbySlide(
        id = "builtin_eclipse",
        title = "Total Solar Horizon",
        subtitle = "CORONA TELEMETRY • 0000 UTC",
        colors = listOf(Color(0xFF1F1209), Color(0xFF431407), Color(0xFF030712)),
        accent = Color(0xFFFF9F0A)
    ),
    CuratedStandbySlide(
        id = "builtin_cyber",
        title = "Neo-Tokyo Midnight",
        subtitle = "SYNTHWAVE GRID • SHIBUYA",
        colors = listOf(Color(0xFF1E1B4B), Color(0xFF4C1D95), Color(0xFF090D16)),
        accent = Color(0xFF38BDF8)
    ),
    CuratedStandbySlide(
        id = "builtin_alpine",
        title = "Dolomites Starlight",
        subtitle = "ALPINE RIDGE • 3,343M",
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF020617)),
        accent = Color(0xFFA78BFA)
    )
)

/**
 * TOOL 03: Permission-Gated StandBy Photo Gallery & Ambient Ken Burns Slideshow
 */
@Composable
fun StandbyGallerySlideshowTool(viewModel: StandbyViewModel) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()

    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF38BDF8)
    val use24Hour by viewModel.use24HourFormat.collectAsState()

    val isPermissionGranted by viewModel.galleryPermissionGranted.collectAsState()
    val userPhotoUris by viewModel.galleryPhotoUris.collectAsState()
    val intervalSecs by viewModel.slideshowIntervalSeconds.collectAsState()

    // Zero-permission Android Photo Picker for importing user photos into the StandBy Gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                uri.toString()
            }
            viewModel.addGalleryPhotoUris(uriStrings)
            viewModel.setGalleryPermissionGranted(true)
        }
    }

    var isSlideshowPlaying by remember { mutableStateOf(true) }
    var isGridViewMode by remember { mutableStateOf(false) }
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    val totalSlidesCount = userPhotoUris.size + BuiltInStandbyArtworks.size

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = Calendar.getInstance()
        }
    }

    LaunchedEffect(isPermissionGranted, isSlideshowPlaying, isGridViewMode, intervalSecs, totalSlidesCount) {
        if (isPermissionGranted && isSlideshowPlaying && !isGridViewMode && totalSlidesCount > 1) {
            while (isActive) {
                delay(intervalSecs * 1000L)
                currentSlideIndex = (currentSlideIndex + 1) % totalSlidesCount
            }
        }
    }

    // Subtle Ken Burns zoom animation for active slide
    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenBurnsScale"
    )

    val showSlideshowTime by viewModel.showSlideshowTimeOverlay.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
            .testTag("gallery_slideshow_tool")
    ) {
        if (!isPermissionGranted) {
            // STEP 1: Minimal Visual Permission Card before showing Gallery / Slideshow
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
                    .testTag("gallery_permission_card"),
                shape = RoundedCornerShape(28.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                            )
                        )
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Gallery Permission",
                            tint = accentColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptics.confirm()
                                viewModel.setGalleryPermissionGranted(true)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gallery_grant_permission_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = "Start Slideshow",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                viewModel.setGalleryPermissionGranted(true)
                                try {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Photos",
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // STEP 2: Full-Screen Edge-to-Edge Immersive Slideshow (zero text distractions; optional time overlay from Settings)
            val safeSlideIdx = currentSlideIndex.mod(totalSlidesCount.coerceAtLeast(1))
            var showControlsOverlay by remember { mutableStateOf(false) }

            LaunchedEffect(showControlsOverlay) {
                if (showControlsOverlay) {
                    delay(3500L)
                    showControlsOverlay = false
                }
            }

            if (!isGridViewMode) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                haptics.tick()
                                showControlsOverlay = !showControlsOverlay
                            }
                    ) {
                        if (safeSlideIdx < userPhotoUris.size) {
                            val uriStr = userPhotoUris[safeSlideIdx]
                            coil.compose.AsyncImage(
                                model = uriStr,
                                contentDescription = "User Gallery Slide",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = if (isSlideshowPlaying) kenBurnsScale else 1f
                                        scaleY = if (isSlideshowPlaying) kenBurnsScale else 1f
                                    },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val art = BuiltInStandbyArtworks[(safeSlideIdx - userPhotoUris.size).mod(BuiltInStandbyArtworks.size)]
                            CuratedStandbyArtworkCanvas(
                                slide = art,
                                zoomScale = if (isSlideshowPlaying) kenBurnsScale else 1f
                            )
                        }
                    }

                    // Subtle Icon-Only Actions revealed only on tap (No Text Labels)
                    AnimatedVisibility(
                        visible = showControlsOverlay,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 16.dp, top = 14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    try {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .testTag("gallery_add_photos_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add Photos",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.tick()
                                    isGridViewMode = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .testTag("gallery_toggle_grid_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "Gallery Grid",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptics.confirm()
                                    isSlideshowPlaying = !isSlideshowPlaying
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .testTag("slideshow_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause Slideshow",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Optional Time-Only Overlay (Opt-On by Default, Controlled in Settings -> Tools -> Slideshow)
                    if (showSlideshowTime) {
                        val timeFmt = remember(use24Hour) {
                            SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault())
                        }
                        Text(
                            text = timeFmt.format(currentTime.time),
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 48.sp,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 24.dp, bottom = 20.dp)
                                .testTag("slideshow_time_overlay_text")
                        )
                    }
                }
            } else {
                // GALLERY GRID VIEW MODE (Visual-only photo grid)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = OledCardBg,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.tick()
                                        try {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } catch (_: Exception) {
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add Photos",
                                        tint = accentColor
                                    )
                                }

                                FilledIconButton(
                                    onClick = {
                                        haptics.tick()
                                        isGridViewMode = false
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = accentColor,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Slideshow,
                                        contentDescription = "Full Screen Slideshow"
                                    )
                                }
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(totalSlidesCount) { idx ->
                                val isCurrent = idx == safeSlideIdx
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        currentSlideIndex = idx
                                        isGridViewMode = false
                                    },
                                    modifier = Modifier
                                        .width(190.dp)
                                        .fillMaxHeight(),
                                    shape = RoundedCornerShape(18.dp),
                                    color = OledCardElevated,
                                    border = BorderStroke(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) accentColor else OledBorderSubtle
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (idx < userPhotoUris.size) {
                                            val u = userPhotoUris[idx]
                                            coil.compose.AsyncImage(
                                                model = u,
                                                contentDescription = "User Photo $idx",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = {
                                                    haptics.tick()
                                                    viewModel.removeGalleryPhotoUri(u)
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.7f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove Photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else {
                                            val art = BuiltInStandbyArtworks[(idx - userPhotoUris.size).mod(BuiltInStandbyArtworks.size)]
                                            CuratedStandbyArtworkCanvas(slide = art, zoomScale = 1f)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CuratedStandbyArtworkCanvas(
    slide: CuratedStandbySlide,
    zoomScale: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
            }
            .background(Brush.linearGradient(slide.colors))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Atmospheric glowing orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(slide.accent.copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * 0.68f, h * 0.36f),
                    radius = minOf(w, h) * 0.55f
                ),
                center = Offset(w * 0.68f, h * 0.36f),
                radius = minOf(w, h) * 0.55f
            )
            // Architectural horizon waves
            val ridgePath = Path().apply {
                moveTo(0f, h * 0.72f)
                cubicTo(w * 0.25f, h * 0.56f, w * 0.55f, h * 0.82f, w, h * 0.62f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = ridgePath,
                color = Color.Black.copy(alpha = 0.45f)
            )
            // Subtle dot-matrix starfield
            for (r in 0..8) {
                for (c in 0..16) {
                    if ((r * 7 + c * 3) % 5 == 0) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = 1.6f,
                            center = Offset(w * (c / 17f + 0.03f), h * (r / 14f + 0.04f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * TOOL 04: Connected Devices Charge Telemetry (Google Fast Pair / Bluetooth & System Battery)
 */
@Composable
fun StandbyConnectedDevicesChargeTool(viewModel: StandbyViewModel) {
    val context = LocalContext.current
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF34D399)
    val devices = rememberGoogleBatteryDevices()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("connected_devices_charge_tool")
    ) {
        var showBtInfo by remember { mutableStateOf(false) }

        // Unified Single Full-Screen Charge Card (no 2-pane split)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            color = OledCardBg,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Connected Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showBtInfo = !showBtInfo },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (showBtInfo) accentColor else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pair", fontSize = 11.sp)
                    }
                }

                AnimatedVisibility(visible = showBtInfo) {
                    Text(
                        text = "Pair your Pixel Buds, Wear OS watch, or Bluetooth headphones in Android Settings to monitor live battery telemetry here.",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BatteryChargingRingsWidget(scale = 1.0f)
                }
            }
        }
    }
}

/**
 * TOOL 05: Smart Devices Control Hub (Connected through Google Home & Android Device Controls)
 */
@Composable
fun StandbySmartDevicesControlTool(viewModel: StandbyViewModel) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF38BDF8)
    val homeDevices by viewModel.googleHomeDevices.collectAsState()
    var selectedDeviceId by remember { mutableStateOf(homeDevices.firstOrNull()?.id ?: "") }
    val activeDevice = homeDevices.firstOrNull { it.id == selectedDeviceId } ?: homeDevices.firstOrNull()
    var showAddDeviceDialog by remember { mutableStateOf(false) }

    var showSmartInfo by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("smart_devices_control_tool")
    ) {
        // Unified Single Full-Screen Smart Home Card (no 2-pane split)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            color = OledCardBg,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Smart Home",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showSmartInfo = !showSmartInfo },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (showSmartInfo) accentColor else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                showAddDeviceDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                haptics.confirm()
                                try {
                                    context.startActivity(Intent("android.settings.ACTION_DEVICE_CONTROLS_SETTINGS").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Controls", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                GoogleDeviceAndHomeMonitor.openGoogleHomeApp(context)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Google Home", fontSize = 11.sp)
                        }
                    }
                }

                AnimatedVisibility(visible = showSmartInfo) {
                    Text(
                        text = "Manage your real smart lights, thermostat, and plugs through Android's system Device Controls or add custom tiles here.",
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SmartHomeControlGridWidget(standbyViewModel = viewModel)
                }

                if (activeDevice != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            homeDevices.forEach { item ->
                                val isSel = item.id == activeDevice.id
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        haptics.tick()
                                        selectedDeviceId = item.id
                                    },
                                    label = { Text(item.name, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                val step = if (activeDevice.type == "thermostat") -1 else -10
                                viewModel.adjustGoogleHomeDeviceValue(activeDevice.id, step)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (activeDevice.type == "thermostat") "-1°F" else "-10%", color = Color.White, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                haptics.tick()
                                val step = if (activeDevice.type == "thermostat") 1 else 10
                                viewModel.adjustGoogleHomeDeviceValue(activeDevice.id, step)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (activeDevice.type == "thermostat") "+1°F" else "+10%", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        if (showAddDeviceDialog) {
            var newName by remember { mutableStateOf("") }
            var newRoom by remember { mutableStateOf("Living Room") }
            var newType by remember { mutableStateOf("light") }

            AlertDialog(
                onDismissRequest = { showAddDeviceDialog = false },
                title = { Text("Add Smart Device Controller", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add a controller for your real smart appliances:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Device Name (e.g. Desk Lamp)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newRoom,
                            onValueChange = { newRoom = it },
                            label = { Text("Room (e.g. Bedroom, Office)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("light" to "Light", "thermostat" to "Thermostat", "plug" to "Plug", "air" to "Air").forEach { (typeKey, typeLabel) ->
                                FilterChip(
                                    selected = newType == typeKey,
                                    onClick = { newType = typeKey },
                                    label = { Text(typeLabel, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.addUserSmartDevice(newName, newRoom, newType)
                                showAddDeviceDialog = false
                            }
                        }
                    ) {
                        Text("Add Device")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDeviceDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Helper to control the phone's rear LED Camera Flash / Torch as an indirect nightstand wall ambient light.
 * Uses Android `CameraManager.setTorchMode` (and `turnOnTorchWithStrengthLevel` on Android 13+ when supported).
 */
private fun setHardwareFlashlightAmbient(
    context: Context,
    enabled: Boolean,
    intensityFraction: Float = 1.0f
): Boolean {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
        val torchCamId = cm.cameraIdList.firstOrNull { id ->
            val chars = cm.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false

        if (!enabled) {
            cm.setTorchMode(torchCamId, false)
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val chars = cm.getCameraCharacteristics(torchCamId)
            val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            if (maxLevel > 1) {
                val targetLevel = (intensityFraction.coerceIn(0.1f, 1.0f) * maxLevel).roundToInt().coerceIn(1, maxLevel)
                cm.turnOnTorchWithStrengthLevel(torchCamId, targetLevel)
                return true
            }
        }
        cm.setTorchMode(torchCamId, true)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * TOOL 06: StandBy Full-Screen Ambient Light Studio
 * - Dedicated full-screen phone rear Camera Flash / Torch (`CameraManager.setTorchMode`) indirect
 *   wall-bounce ambient light (Steady Torch, Gentle Breathing Pulse, or Candlelight Flicker),
 *   OLED Screen Halo Diffuser, and Sleep Audio Synthesizer.
 */
@Composable
fun StandbyDockLabTool(viewModel: StandbyViewModel) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF38BDF8)

    // Camera Flash / Torch Ambient Light state:
    // 0 = Off, 1 = Steady Wall-Bounce Torch, 2 = Breathing Ambient Pulse, 3 = Candlelight Flicker
    var flashAmbientMode by remember { mutableIntStateOf(0) }
    var flashIntensity by remember { mutableFloatStateOf(0.65f) }
    var isHardwareFlashActive by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        onDispose {
            // Always turn off hardware camera flash when leaving the screen
            setHardwareFlashlightAmbient(context, enabled = false)
        }
    }

    // Drive the hardware Camera Flash / Torch in Steady, Breathing Pulse, or Candlelight mode
    LaunchedEffect(flashAmbientMode, flashIntensity) {
        if (flashAmbientMode == 0) {
            setHardwareFlashlightAmbient(context, enabled = false)
            isHardwareFlashActive = false
            return@LaunchedEffect
        }
        when (flashAmbientMode) {
            1 -> { // Steady Ambient Wall-Bounce Torch
                isHardwareFlashActive = setHardwareFlashlightAmbient(context, enabled = true, intensityFraction = flashIntensity)
            }
            2 -> { // Gentle Breathing Ambient Flash
                var step = 0
                while (isActive && flashAmbientMode == 2) {
                    val wave = ((sin(step * 0.35) + 1.0) / 2.0).toFloat().coerceIn(0.15f, 1.0f)
                    val level = (wave * flashIntensity).coerceIn(0.15f, 1.0f)
                    val onCycle = wave > 0.35f
                    isHardwareFlashActive = setHardwareFlashlightAmbient(context, enabled = onCycle, intensityFraction = level)
                    step++
                    delay(260L)
                }
            }
            3 -> { // Warm Candlelight Flicker Flash
                while (isActive && flashAmbientMode == 3) {
                    val flicker = Random.nextFloat().coerceIn(0.25f, 1.0f) * flashIntensity
                    val onCycle = flicker > 0.22f
                    isHardwareFlashActive = setHardwareFlashlightAmbient(context, enabled = onCycle, intensityFraction = flicker)
                    delay(Random.nextLong(120L, 320L))
                }
            }
        }
    }

    // Procedural Ambient Soundscape Synthesizer + OLED Screen Nightlight
    var activeSoundMode by remember { mutableIntStateOf(0) } // 0 = Off, 1 = Brown Noise, 2 = Night Rain, 3 = 432Hz Zen
    var nightlightWarmthIdx by remember { mutableIntStateOf(1) } // Default Warm Amber 2000K screen glow

    val nightlightColors = listOf(
        Color.Transparent to "Off",
        Color(0xFFFF9500) to "Amber",
        Color(0xFFFF3B30) to "Crimson",
        Color(0xFF38BDF8) to "Cyan"
    )
    val activeNightlightColor = nightlightColors[nightlightWarmthIdx].first

    LaunchedEffect(activeSoundMode) {
        if (activeSoundMode == 0) return@LaunchedEffect
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val sampleRate = 22050
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                null
            } ?: return@withContext

            val pcm = ShortArray(1024)
            var lastOut = 0.0
            var phase = 0.0
            try {
                track.play()
                while (isActive && activeSoundMode != 0) {
                    for (i in pcm.indices) {
                        val sample = when (activeSoundMode) {
                            1 -> {
                                val white = Random.nextDouble(-1.0, 1.0)
                                lastOut = (lastOut + (0.02 * white)) / 1.02
                                (lastOut * 3.2).coerceIn(-0.9, 0.9)
                            }
                            2 -> {
                                val white = Random.nextDouble(-1.0, 1.0)
                                lastOut = 0.85 * lastOut + 0.15 * white
                                (lastOut * 0.45).coerceIn(-0.9, 0.9)
                            }
                            else -> {
                                phase += (2.0 * PI * 216.0) / sampleRate
                                val s1 = sin(phase)
                                val s2 = sin(phase * 2.0) * 0.4
                                (s1 + s2) * 0.25
                            }
                        }
                        pcm[i] = (sample * Short.MAX_VALUE * 0.35).toInt().toShort()
                    }
                    track.write(pcm, 0, pcm.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("dock_lab_tool")
    ) {
        // Unified Single Full-Screen Ambient Light Studio (no 2-pane split, no 2D level)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            color = OledCardBg,
            border = BorderStroke(
                1.5.dp,
                if (flashAmbientMode != 0) Color(0xFFFFD54F).copy(alpha = 0.75f)
                else if (activeNightlightColor != Color.Transparent) activeNightlightColor.copy(alpha = 0.55f)
                else OledBorderSubtle
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (flashAmbientMode != 0) {
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD54F).copy(alpha = 0.38f), Color.Transparent)
                            )
                        } else if (activeNightlightColor != Color.Transparent) {
                            Brush.radialGradient(
                                colors = listOf(activeNightlightColor.copy(alpha = 0.32f), Color.Transparent)
                            )
                        } else {
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                            )
                        }
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header with Hero Flash Toggle Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbIncandescent,
                                contentDescription = null,
                                tint = if (flashAmbientMode != 0) Color(0xFFFFD54F) else accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Ambient Light",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.confirm()
                                flashAmbientMode = if (flashAmbientMode == 0) 1 else 0
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (flashAmbientMode != 0) Color(0xFFFFD54F) else OledCardElevated
                                )
                                .border(
                                    1.dp,
                                    if (flashAmbientMode != 0) Color(0xFFFFD54F) else OledBorderSubtle,
                                    CircleShape
                                )
                                .testTag("camera_flash_ambient_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (flashAmbientMode != 0) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Toggle Phone Flash Ambient Light",
                                tint = if (flashAmbientMode != 0) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Phone Rear Flash Mode Chips (Off / Steady / Breathe / Candle)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            0 to "Flash Off",
                            1 to "Steady Glow",
                            2 to "Breathe",
                            3 to "Candle"
                        ).forEach { (modeIdx, label) ->
                            val isSel = flashAmbientMode == modeIdx
                            Surface(
                                onClick = {
                                    haptics.tick()
                                    flashAmbientMode = modeIdx
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) Color(0xFFFFD54F).copy(alpha = 0.24f) else OledCardElevated,
                                border = BorderStroke(1.dp, if (isSel) Color(0xFFFFD54F) else OledBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flash_ambient_mode_$modeIdx")
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }

                    // Brightness Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = "Brightness",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = flashIntensity,
                            onValueChange = { flashIntensity = it },
                            valueRange = 0.15f..1.0f,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(flashIntensity * 100).roundToInt()}%",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Bottom Row: OLED Screen Halo Color Swatches + Ambient Soundscape Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Visual Color Swatches for OLED Halo
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            nightlightColors.forEachIndexed { idx, (glowCol, label) ->
                                val isSel = nightlightWarmthIdx == idx
                                val chipAccent = if (glowCol == Color.Transparent) Color.White.copy(alpha = 0.35f) else glowCol
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (glowCol == Color.Transparent) OledCardElevated else glowCol)
                                        .border(
                                            width = if (isSel) 2.5.dp else 1.dp,
                                            color = if (isSel) Color.White else chipAccent.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            haptics.tick()
                                            nightlightWarmthIdx = idx
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (glowCol == Color.Transparent) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = label,
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    } else if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = label,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Sleep Audio Synthesizer Chips
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "Mute",
                                1 to "Brown",
                                2 to "Rain",
                                3 to "432Hz"
                            ).forEach { (modeIdx, title) ->
                                val isSel = activeSoundMode == modeIdx
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        activeSoundMode = modeIdx
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) accentColor.copy(alpha = 0.22f) else OledCardElevated,
                                    border = BorderStroke(1.dp, if (isSel) accentColor else OledBorderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSel) accentColor else Color.White.copy(alpha = 0.78f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
