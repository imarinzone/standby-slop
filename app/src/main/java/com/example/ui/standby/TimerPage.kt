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

@Composable
fun ToolScreenRenderer(
    toolIndex: Int,
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        when (toolIndex.coerceIn(0, 5)) {
            0 -> FocusPomodoroTimer(viewModel)
            1 -> HighTechStopwatch(viewModel)
            2 -> StandbyGallerySlideshowTool(viewModel)
            3 -> StandbyConnectedDevicesChargeTool(viewModel)
            4 -> StandbySmartDevicesControlTool(viewModel)
            5 -> StandbyDockLabTool(viewModel)
        }
    }
}

@Composable
fun TimerPage(
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: colorScheme.primary

    val enabledTools by viewModel.enabledToolScreens.collectAsState()
    val activeToolIndices = remember(enabledTools) {
        enabledTools.sorted().ifEmpty { listOf(0) }
    }
    val currentTimerMode by viewModel.timerModeIndex.collectAsState()

    val initialPage = remember {
        val pos = activeToolIndices.indexOf(viewModel.timerModeIndex.value)
        if (pos >= 0) pos else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (activeToolIndices.size - 1).coerceAtLeast(0)),
        pageCount = { activeToolIndices.size }
    )
    val coroutineScope = rememberCoroutineScope()

    var hasInitPager by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage, activeToolIndices) {
        if (!hasInitPager) {
            hasInitPager = true
        } else {
            val safePage = pagerState.currentPage.coerceIn(0, (activeToolIndices.size - 1).coerceAtLeast(0))
            val toolIdx = activeToolIndices.getOrElse(safePage) { 0 }
            if (viewModel.timerModeIndex.value != toolIdx) {
                haptics.tick()
                viewModel.setTimerModeIndex(toolIdx)
            }
        }
    }

    LaunchedEffect(currentTimerMode, activeToolIndices) {
        val targetPos = activeToolIndices.indexOf(currentTimerMode)
        if (targetPos >= 0 && pagerState.currentPage != targetPos && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(targetPos)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_hub_page"),
        color = colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val toolIdx = activeToolIndices.getOrElse(page) { 0 }
                ToolScreenRenderer(toolIndex = toolIdx, viewModel = viewModel)
            }

            // Right-Edge Vertical Mode Indicator Dots (preserves timer_mode_dot_$toolIdx testTags)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                shape = CircleShape,
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                tonalElevation = if (com.example.ui.theme.LocalPureAmoled.current) 0.dp else 4.dp,
                border = expressiveSurfaceBorder()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    activeToolIndices.forEachIndexed { pagePos, toolIdx ->
                        val isSelected = pagerState.currentPage == pagePos
                        val dotHeight by animateDpAsState(
                            targetValue = if (isSelected) 18.dp else 7.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "timerDotHeight"
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 7.dp, height = dotHeight)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) accentColor
                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                )
                                .clickable {
                                    haptics.tick()
                                    viewModel.setTimerModeIndex(toolIdx)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagePos)
                                    }
                                }
                                .testTag("timer_mode_dot_$toolIdx")
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
    val colorScheme = MaterialTheme.colorScheme

    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()
    val focusMinutes by viewModel.pomodoroFocusMinutes.collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    val accentContentFg = if (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f > 0.55f) Color.Black else Color.White
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
            .background(colorScheme.surface)
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val isCompactWidth = maxWidth < 640.dp
        val outerHPad = if (isCompactWidth) 16.dp else 28.dp
        val outerVPad = if (isCompactHeight) 12.dp else 18.dp

        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(colorScheme.surface.copy(alpha = 0.74f)))
        }

        // Unified Single Full-Screen Pomodoro Card (Sculpted Material 3 Expressive Surface)
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 14.dp, top = outerVPad, bottom = outerVPad),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surfaceContainerLow,
            border = expressiveSurfaceBorder(highlighted = isRunning, accentColor = accentColor.copy(alpha = 0.65f))
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
                    // Top Bar: Expressive Morphing Duration Chips + Config Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(15, 25, 45, 60).forEach { mins ->
                                val isSel = focusMinutes == mins
                                val pillCorner by animateDpAsState(
                                    targetValue = if (isSel) 20.dp else 12.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "pomodoroPillCorner"
                                )
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        viewModel.setPomodoroFocusMinutes(mins)
                                        isRunning = false
                                        val nextDur = getDurationForSegment(currentSegmentIndex, mins)
                                        initialSeconds = nextDur
                                        secondsLeft = nextDur
                                    },
                                    shape = RoundedCornerShape(pillCorner),
                                    color = if (isSel) accentColor.copy(alpha = 0.22f) else colorScheme.surfaceContainerHigh,
                                    border = expressiveSurfaceBorder(highlighted = isSel, accentColor = accentColor)
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        color = if (isSel) accentColor else colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.tick()
                                showConfigDialog = true
                            },
                            icon = Icons.Default.Tune,
                            contentDescription = "Set Focus Duration",
                            size = 38.dp,
                            iconSize = 18.dp,
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("pomodoro_config_btn")
                        )
                    }

                    // Center: 60-Tick StandBy Countdown Dial + Start / Reset Controls
                    Box(
                        modifier = Modifier.size(dialSize),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = if (initialSeconds > 0) secondsLeft.toFloat() / initialSeconds.toFloat() else 1f
                        val activeTickBoundary = (progress * 60).roundToInt()
                        val inactiveTickColor = colorScheme.onSurface.copy(alpha = 0.16f)
                        val trackCircleColor = colorScheme.onSurface.copy(alpha = 0.08f)

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
                                    color = if (isActiveTick) accentColor.copy(alpha = 0.85f) else inactiveTickColor,
                                    start = Offset(cx + cos(angleRad) * (outerRadius - tickLen), cy + sin(angleRad) * (outerRadius - tickLen)),
                                    end = Offset(cx + cos(angleRad) * outerRadius, cy + sin(angleRad) * outerRadius),
                                    strokeWidth = if (i % 5 == 0) 2.5f else 1.4f,
                                    cap = StrokeCap.Round
                                )
                            }

                            drawCircle(
                                color = trackCircleColor,
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
                                color = if (gradientColors == null && !outlineVal) colorScheme.onSurface else Color.Unspecified,
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
                                ExpressiveMorphButton(
                                    onClick = {
                                        haptics.confirm()
                                        isRunning = !isRunning
                                    },
                                    selected = isRunning,
                                    idleCorner = 16.dp,
                                    activeCorner = 12.dp,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = accentContentFg
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("pomodoro_start_pause_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isRunning) "Pause" else "Start",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                ExpressiveIconActionButton(
                                    onClick = {
                                        haptics.tick()
                                        isRunning = false
                                        secondsLeft = initialSeconds
                                    },
                                    icon = Icons.Default.Refresh,
                                    contentDescription = "Reset Timer",
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurface,
                                    modifier = Modifier.testTag("pomodoro_reset_btn")
                                )
                            }
                        }
                    }

                    // Bottom Bar: 8-Stage Visual Cycle Indicators
                    Surface(
                        color = colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        border = expressiveSurfaceBorder()
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
                                    shape = if (isCurrent) CircleShape else RoundedCornerShape(10.dp),
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
                                                isCurrent -> accentContentFg
                                                isCompleted -> accentColor
                                                else -> colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
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
            shape = RoundedCornerShape(30.dp),
            containerColor = colorScheme.surfaceContainerHigh,
            title = {
                KeepDialogImmersiveFullScreen()
                Text(
                    text = "Focus Session Length",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
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
                        color = colorScheme.onSurfaceVariant
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
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSel) colorScheme.primaryContainer else colorScheme.surfaceContainer,
                            border = expressiveSurfaceBorder(highlighted = isSel, accentColor = accentColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pomodoro_preset_$mins")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$mins Minutes",
                                    color = if (isSel) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isSel) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ExpressiveMorphButton(
                    onClick = { showConfigDialog = false },
                    selected = false
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
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
    val colorScheme = MaterialTheme.colorScheme

    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val accentContentFg = if (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f > 0.55f) Color.Black else Color.White
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
            .background(colorScheme.surface),
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
            Box(modifier = Modifier.fillMaxSize().background(colorScheme.surface.copy(alpha = 0.74f)))
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 14.dp, top = outerVPad, bottom = outerVPad + 32.dp),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surfaceContainerLow,
            border = expressiveSurfaceBorder(highlighted = isRunning, accentColor = accentColor.copy(alpha = 0.65f))
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
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
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
                            color = if (isRunning) Color(0xFF34D399) else colorScheme.onSurfaceVariant,
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
                        color = if (gradientColors == null && !outlineVal) colorScheme.onSurface else Color.Unspecified,
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
                            color = colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                            border = expressiveSurfaceBorder(highlighted = !isRunning, accentColor = accentColor),
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
                                    color = colorScheme.onSurface,
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
                    val mainBtnWidth = if (isCompactHeight) 82.dp else 94.dp
                    val mainBtnHeight = if (isCompactHeight) 50.dp else 58.dp
                    val mainCorner by animateDpAsState(
                        targetValue = if (isRunning) 29.dp else 18.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "chronoStartCorner"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 18.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.tick()
                                isRunning = false
                                timeMillis = 0L
                                laps.clear()
                                lastRecordedTime = 0L
                                showLapDialog = false
                            },
                            icon = Icons.Default.Refresh,
                            contentDescription = "Reset Stopwatch",
                            size = sideBtnSize,
                            iconSize = 22.dp,
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                            modifier = Modifier.testTag("stopwatch_reset_btn")
                        )

                        Surface(
                            onClick = {
                                haptics.confirm()
                                isRunning = !isRunning
                            },
                            modifier = Modifier
                                .size(width = mainBtnWidth, height = mainBtnHeight)
                                .testTag("stopwatch_start_pause_btn"),
                            shape = RoundedCornerShape(mainCorner),
                            color = accentColor,
                            contentColor = accentContentFg,
                            border = expressiveSurfaceBorder(highlighted = isRunning, accentColor = accentColor)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Toggle Stopwatch",
                                    modifier = Modifier.size(if (isCompactHeight) 26.dp else 30.dp)
                                )
                            }
                        }

                        val canLap = isRunning || laps.isNotEmpty() || timeMillis > 0L
                        Surface(
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
                            enabled = canLap,
                            shape = if (canLap) CircleShape else RoundedCornerShape(16.dp),
                            color = if (canLap) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
                            border = expressiveSurfaceBorder(highlighted = canLap, accentColor = accentColor),
                            modifier = Modifier
                                .size(sideBtnSize)
                                .testTag("stopwatch_lap_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = if (isRunning) "Record Lap Split" else "View Lap Timings",
                                    tint = if (canLap) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
            shape = RoundedCornerShape(30.dp),
            containerColor = colorScheme.surfaceContainerHigh,
            title = {
                KeepDialogImmersiveFullScreen()
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
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer
                    ) {
                        val totalFmt = formatDisplayTime(timeMillis)
                        Text(
                            text = "Total ${totalFmt.first}${totalFmt.second}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onPrimaryContainer,
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
                                shape = RoundedCornerShape(18.dp),
                                color = colorScheme.surfaceContainerLow,
                                border = BorderStroke(
                                    width = if (isFastest || isSlowest) 1.5.dp else 1.dp,
                                    color = when {
                                        isFastest -> Color(0xFF10B981).copy(alpha = 0.7f)
                                        isSlowest -> Color(0xFFF43F5E).copy(alpha = 0.7f)
                                        else -> colorScheme.outlineVariant.copy(alpha = 0.55f)
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
                                            color = colorScheme.onSurface
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
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ExpressiveMorphButton(
                    onClick = { showLapDialog = false },
                    selected = true
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
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

    val colorScheme = MaterialTheme.colorScheme
    val accentContentFg = if (accentColor.red * 0.299f + accentColor.green * 0.587f + accentColor.blue * 0.114f > 0.55f) Color.Black else Color.White

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .testTag("gallery_slideshow_tool")
    ) {
        if (!isPermissionGranted) {
            // STEP 1: Minimal Expressive Permission Card before showing Gallery / Slideshow
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
                    .testTag("gallery_permission_card"),
                shape = RoundedCornerShape(30.dp),
                color = colorScheme.surfaceContainerLow,
                border = expressiveSurfaceBorder()
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
                    Surface(
                        modifier = Modifier.size(84.dp),
                        shape = RoundedCornerShape(26.dp),
                        color = colorScheme.primaryContainer,
                        border = expressiveSurfaceBorder(highlighted = true, accentColor = accentColor)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = "Gallery Permission",
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveMorphButton(
                            onClick = {
                                haptics.confirm()
                                viewModel.setGalleryPermissionGranted(true)
                            },
                            selected = true,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = accentContentFg
                            ),
                            modifier = Modifier.testTag("gallery_grant_permission_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Slideshow,
                                contentDescription = "Start Slideshow",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        ExpressiveIconActionButton(
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
                            icon = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Add Photos",
                            size = 48.dp,
                            iconSize = 22.dp,
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
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
                // GALLERY GRID VIEW MODE (Visual-only photo grid with Material 3 Expressive framing)
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = colorScheme.surfaceContainerLow,
                    border = expressiveSurfaceBorder()
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
                                ExpressiveIconActionButton(
                                    onClick = {
                                        haptics.tick()
                                        try {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    icon = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Add Photos",
                                    size = 44.dp,
                                    iconSize = 20.dp,
                                    containerColor = colorScheme.secondaryContainer,
                                    contentColor = colorScheme.onSecondaryContainer
                                )

                                ExpressiveIconActionButton(
                                    onClick = {
                                        haptics.tick()
                                        isGridViewMode = false
                                    },
                                    icon = Icons.Default.Slideshow,
                                    contentDescription = "Full Screen Slideshow",
                                    selected = true,
                                    size = 44.dp,
                                    iconSize = 20.dp,
                                    containerColor = accentColor,
                                    contentColor = accentContentFg
                                )
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
                                    shape = RoundedCornerShape(20.dp),
                                    color = colorScheme.surfaceContainerHigh,
                                    border = expressiveSurfaceBorder(highlighted = isCurrent, accentColor = accentColor)
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
    val colorScheme = MaterialTheme.colorScheme
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: colorScheme.primary

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("connected_devices_charge_tool")
    ) {
        var showBtInfo by remember { mutableStateOf(false) }

        // Unified Single Full-Screen Charge Card (Material 3 Expressive Surface)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surfaceContainerLow,
            border = expressiveSurfaceBorder()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = "Connected Devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showBtInfo = !showBtInfo },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (showBtInfo) accentColor else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    ExpressiveMorphButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        },
                        selected = false,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pair", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }

                AnimatedVisibility(visible = showBtInfo) {
                    Text(
                        text = "Pair your Pixel Buds, Wear OS watch, or Bluetooth headphones in Android Settings to monitor live battery telemetry here.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
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
    val colorScheme = MaterialTheme.colorScheme
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: colorScheme.primary
    val homeDevices by viewModel.googleHomeDevices.collectAsState()
    var selectedDeviceId by remember { mutableStateOf(homeDevices.firstOrNull()?.id ?: "") }
    val activeDevice = homeDevices.firstOrNull { it.id == selectedDeviceId } ?: homeDevices.firstOrNull()
    var showAddDeviceDialog by remember { mutableStateOf(false) }

    var showSmartInfo by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("smart_devices_control_tool")
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surfaceContainerLow,
            border = expressiveSurfaceBorder()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(7.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = "Smart Home",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(
                            onClick = { showSmartInfo = !showSmartInfo },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = if (showSmartInfo) accentColor else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveMorphButton(
                            onClick = {
                                haptics.tick()
                                showAddDeviceDialog = true
                            },
                            selected = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        ExpressiveMorphButton(
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
                            selected = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Controls", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                        ExpressiveMorphButton(
                            onClick = {
                                haptics.tick()
                                GoogleDeviceAndHomeMonitor.openGoogleHomeApp(context)
                            },
                            selected = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Google Home", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                AnimatedVisibility(visible = showSmartInfo) {
                    Text(
                        text = "Manage your real smart lights, thermostat, and plugs through Android's system Device Controls or add custom tiles here.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
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
                                    shape = CircleShape,
                                    label = { Text(item.name, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }

                        ExpressiveMorphButton(
                            onClick = {
                                haptics.tick()
                                val step = if (activeDevice.type == "thermostat") -1 else -10
                                viewModel.adjustGoogleHomeDeviceValue(activeDevice.id, step)
                            },
                            selected = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (activeDevice.type == "thermostat") "-1°F" else "-10%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        ExpressiveMorphButton(
                            onClick = {
                                haptics.tick()
                                val step = if (activeDevice.type == "thermostat") 1 else 10
                                viewModel.adjustGoogleHomeDeviceValue(activeDevice.id, step)
                            },
                            selected = false,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (activeDevice.type == "thermostat") "+1°F" else "+10%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(30.dp),
                containerColor = colorScheme.surfaceContainerHigh,
                title = {
                    KeepDialogImmersiveFullScreen()
                    Text("Add Smart Device Controller", fontWeight = FontWeight.ExtraBold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Add a controller for your real smart appliances:",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Device Name (e.g. Desk Lamp)") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newRoom,
                            onValueChange = { newRoom = it },
                            label = { Text("Room (e.g. Bedroom, Office)") },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("light" to "Light", "thermostat" to "Thermostat", "plug" to "Plug", "air" to "Air").forEach { (typeKey, typeLabel) ->
                                FilterChip(
                                    selected = newType == typeKey,
                                    onClick = { newType = typeKey },
                                    shape = CircleShape,
                                    label = { Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    ExpressiveMorphButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.addUserSmartDevice(newName, newRoom, newType)
                                showAddDeviceDialog = false
                            }
                        },
                        selected = true
                    ) {
                        Text("Add Device", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    ExpressiveMorphButton(
                        onClick = { showAddDeviceDialog = false },
                        selected = false
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
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

    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 14.dp)
            .testTag("dock_lab_tool")
    ) {
        // Unified Single Full-Screen Ambient Light Studio (Material 3 Expressive Surface)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = colorScheme.surfaceContainerLow,
            border = expressiveSurfaceBorder(
                highlighted = flashAmbientMode != 0 || activeNightlightColor != Color.Transparent,
                accentColor = if (flashAmbientMode != 0) Color(0xFFFFD54F) else activeNightlightColor
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
                    // Header with Expressive Morphing Hero Flash Toggle Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (flashAmbientMode != 0) Color(0xFFFFD54F).copy(alpha = 0.22f) else colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbIncandescent,
                                    contentDescription = null,
                                    tint = if (flashAmbientMode != 0) Color(0xFFFFD54F) else colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(20.dp)
                                )
                            }
                            Text(
                                text = "Ambient Light",
                                style = MaterialTheme.typography.titleLarge,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.confirm()
                                flashAmbientMode = if (flashAmbientMode == 0) 1 else 0
                            },
                            icon = if (flashAmbientMode != 0) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Toggle Phone Flash Ambient Light",
                            selected = flashAmbientMode != 0,
                            size = 50.dp,
                            iconSize = 24.dp,
                            containerColor = if (flashAmbientMode != 0) Color(0xFFFFD54F) else colorScheme.surfaceContainerHigh,
                            contentColor = if (flashAmbientMode != 0) Color.Black else colorScheme.onSurface,
                            modifier = Modifier.testTag("camera_flash_ambient_toggle_btn")
                        )
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
                            val chipCorner by animateDpAsState(
                                targetValue = if (isSel) 22.dp else 14.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "flashModeCorner"
                            )
                            Surface(
                                onClick = {
                                    haptics.tick()
                                    flashAmbientMode = modeIdx
                                },
                                shape = RoundedCornerShape(chipCorner),
                                color = if (isSel) Color(0xFFFFD54F).copy(alpha = 0.24f) else colorScheme.surfaceContainerHigh,
                                border = expressiveSurfaceBorder(highlighted = isSel, accentColor = Color(0xFFFFD54F)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("flash_ambient_mode_$modeIdx")
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color(0xFFFFD54F) else colorScheme.onSurfaceVariant,
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

                    // Bottom Row: Expressive OLED Halo Swatches + Ambient Soundscape Chips
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
                                val swatchRadius by animateDpAsState(
                                    targetValue = if (isSel) 18.dp else 10.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "haloSwatchCorner"
                                )
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        nightlightWarmthIdx = idx
                                    },
                                    shape = RoundedCornerShape(swatchRadius),
                                    color = if (glowCol == Color.Transparent) colorScheme.surfaceContainerHigh else glowCol,
                                    border = BorderStroke(
                                        width = if (isSel) 2.5.dp else 1.dp,
                                        color = if (isSel) colorScheme.onSurface else colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (glowCol == Color.Transparent) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = label,
                                                tint = colorScheme.onSurfaceVariant,
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
                                val soundCorner by animateDpAsState(
                                    targetValue = if (isSel) 20.dp else 12.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "soundChipCorner"
                                )
                                Surface(
                                    onClick = {
                                        haptics.tick()
                                        activeSoundMode = modeIdx
                                    },
                                    shape = RoundedCornerShape(soundCorner),
                                    color = if (isSel) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh,
                                    border = expressiveSurfaceBorder(highlighted = isSel, accentColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = title,
                                        color = if (isSel) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
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
