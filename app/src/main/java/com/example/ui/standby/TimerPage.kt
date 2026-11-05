package com.example.ui.standby

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
            Triple("Dock Lab", "LEVEL & AMBIENT", Icons.Default.Explore)
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
                    3 -> StandbyDockLabTool(viewModel)
                }
            }

            // Sleek Bottom-Center OLED Tools Dock Switcher Pill
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 10.dp),
                shape = CircleShape,
                color = OledCardElevated.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    toolItems.forEachIndexed { idx, (label, _, icon) ->
                        val isSelected = pagerState.currentPage == idx
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, accentColor.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.tick()
                                    viewModel.setTimerModeIndex(idx)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(idx)
                                    }
                                }
                                .testTag("tools_tab_chip_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) accentColor else Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 14.dp, top = outerVPad, bottom = outerVPad + 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(paneSpacing)
        ) {
            // Left Pane: 60-Tick StandBy OLED Countdown Dial
            Surface(
                modifier = Modifier
                    .weight(1.06f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f))
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(if (isCompactHeight) 10.dp else 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val dialSize = (minOf(maxWidth, maxHeight) * 0.94f).coerceIn(150.dp, 270.dp)
                    val isSmallDial = dialSize < 220.dp
                    val timerFontSize = (dialSize.value * 0.21f).coerceIn(30f, 56f).sp
                    val iconBtnSize = if (isSmallDial) 36.dp else 44.dp
                    val iconSize = if (isSmallDial) 16.dp else 20.dp
                    val strokeWidthDp = if (isSmallDial) 7.dp else 9.dp

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
                            val arcRadius = outerRadius - 10.dp.toPx()

                            // Outer 60-tick StandBy ring
                            for (i in 0 until 60) {
                                val angleRad = ((i * 6f - 90f) * PI / 180.0).toFloat()
                                val isActiveTick = i <= activeTickBoundary
                                val tickLen = if (i % 5 == 0) 6.dp.toPx() else 3.5.dp.toPx()
                                drawLine(
                                    color = if (isActiveTick) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f),
                                    start = Offset(cx + cos(angleRad) * (outerRadius - tickLen), cy + sin(angleRad) * (outerRadius - tickLen)),
                                    end = Offset(cx + cos(angleRad) * outerRadius, cy + sin(angleRad) * outerRadius),
                                    strokeWidth = if (i % 5 == 0) 2.6f else 1.5f,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Inner smooth progress arc
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
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = strokeWidthDp + 10.dp)
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

                            Spacer(modifier = Modifier.height(if (isSmallDial) 8.dp else 12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallDial) 6.dp else 10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        showConfigDialog = true
                                    },
                                    modifier = Modifier
                                        .size(iconBtnSize)
                                        .clip(CircleShape)
                                        .background(OledCardElevated)
                                        .border(1.dp, OledBorderSubtle, CircleShape)
                                        .testTag("pomodoro_config_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Set Focus Duration",
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptics.confirm()
                                        isRunning = !isRunning
                                    },
                                    modifier = Modifier.testTag("pomodoro_start_pause_btn"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = if (accentColor == Color.White) Color.Black else Color.Black
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = if (isSmallDial) 12.dp else 18.dp,
                                        vertical = if (isSmallDial) 6.dp else 9.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isSmallDial) 15.dp else 18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isRunning) "Pause" else "Start",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = if (isSmallDial) 12.sp else 13.5.sp,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        isRunning = false
                                        secondsLeft = initialSeconds
                                    },
                                    modifier = Modifier
                                        .size(iconBtnSize)
                                        .clip(CircleShape)
                                        .background(OledCardElevated)
                                        .border(1.dp, OledBorderSubtle, CircleShape)
                                        .testTag("pomodoro_reset_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Timer",
                                        tint = Color.White,
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Pane: StandBy OLED Focus Matrix & Phase Telemetry Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, OledBorderSubtle)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompactHeight) 14.dp else 22.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val stepBtnSize = ((maxWidth - 20.dp) / 8.5f).coerceIn(22.dp, 36.dp)
                    val stepIconSize = (stepBtnSize * 0.5f).coerceIn(12.dp, 18.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "01 • FOCUS MATRIX",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = "${focusMinutes}M ROUNDS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                        Text(
                            text = getSegmentLabel(currentSegmentIndex),
                            style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontFamily = customFont,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val focusCompletedCount = currentSegmentIndex / 2
                        Text(
                            text = "$focusCompletedCount of 4 focus blocks completed • Tap any stage below",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.padding(top = 4.dp, bottom = if (isCompactHeight) 10.dp else 16.dp)
                        )

                        // Quick Duration Selector Chips right inside the matrix card
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
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
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSel) accentColor else OledBorderSubtle
                                    )
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

                        Surface(
                            color = OledCardElevated,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, OledBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
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
                                        modifier = Modifier.size(stepBtnSize),
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
                                                modifier = Modifier.size(stepIconSize)
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OledCanvasBg)
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 46.dp)
            .testTag("gallery_slideshow_tool")
    ) {
        if (!isPermissionGranted) {
            // STEP 1: Explicit User Permission Prompt Card before showing Gallery / Slideshow
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("gallery_permission_card"),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = "Gallery Permission",
                            tint = accentColor,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "03 • STANDBY PHOTO FRAME & GALLERY",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Allow Gallery & Slideshow Access",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant permission to view your photo gallery and run an ambient nightstand slideshow with live clock overlay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Allow Gallery Access",
                                    fontWeight = FontWeight.ExtraBold
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
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Device Photos", color = Color.White)
                            }
                        }
                    }
                }
            }
        } else {
            // STEP 2: Full StandBy Gallery View & Ambient Ken Burns Slideshow
            val safeSlideIdx = currentSlideIndex.mod(totalSlidesCount.coerceAtLeast(1))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isGridViewMode) {
                        // AMBIENT SLIDESHOW MODE
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
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

                            // Bottom & Top Vignette Scrim for legibility
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.45f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.72f)
                                            )
                                        )
                                    )
                            )

                            // Top Bar: Slide Info + Controls (Play/Pause, Interval, Add Photos, Grid Toggle)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = if (isSlideshowPlaying) "SLIDESHOW • ${intervalSecs}S" else "PAUSED",
                                            color = accentColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "${safeSlideIdx + 1} / $totalSlidesCount",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Cycle Interval Button (5s / 8s / 15s / 30s)
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            val nextInt = when (intervalSecs) {
                                                5 -> 8
                                                8 -> 15
                                                15 -> 30
                                                else -> 5
                                            }
                                            viewModel.setSlideshowIntervalSeconds(nextInt)
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "${intervalSecs}s",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }

                                    // Add Photos from Device Picker
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            try {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } catch (_: Exception) {
                                            }
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                                        modifier = Modifier.testTag("gallery_add_photos_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Add Photos",
                                                tint = accentColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Add",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Switch to Gallery Grid View
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            isGridViewMode = true
                                        },
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier.testTag("gallery_toggle_grid_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.GridView,
                                                contentDescription = "Gallery Grid",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Grid",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Bottom-Left StandBy Live Clock & Date Overlay on Slideshow
                            val timeFmt = remember(use24Hour) {
                                SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault())
                            }
                            val dateFmt = remember {
                                SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 18.dp, bottom = 14.dp)
                            ) {
                                Text(
                                    text = dateFmt.format(currentTime.time).uppercase(Locale.getDefault()),
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp
                                )
                                Text(
                                    text = timeFmt.format(currentTime.time),
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 44.sp
                                )
                            }

                            // Bottom-Right Slideshow Transport Controls (Prev / Play-Pause / Next)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 18.dp, bottom = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        currentSlideIndex = (currentSlideIndex - 1 + totalSlidesCount) % totalSlidesCount
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Photo",
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
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .testTag("slideshow_play_pause_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isSlideshowPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause Slideshow",
                                        tint = Color.Black,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptics.tick()
                                        currentSlideIndex = (currentSlideIndex + 1) % totalSlidesCount
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // GALLERY GRID VIEW MODE
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "StandBy Photo Gallery ($totalSlidesCount items)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Tap any photo to launch full-bleed Ken Burns slideshow",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.tick()
                                            try {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } catch (_: Exception) {
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Photos", color = Color.White, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            haptics.tick()
                                            isGridViewMode = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accentColor,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Slideshow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Slideshow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 52.dp)
        ) {
            Text(
                text = slide.subtitle,
                color = slide.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = slide.title,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * TOOL 04: StandBy Dock Lab — Real Hardware 2D Spirit Level, Compass & Bedside Soundscape / Nightlight
 */
@Composable
fun StandbyDockLabTool(viewModel: StandbyViewModel) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF38BDF8)

    // Real Android Accelerometer + Magnetometer sensor listener for Dock Spirit Level & Compass
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var rollDeg by remember { mutableFloatStateOf(0f) }
    var azimuthDeg by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        var hasGravity = false
        var hasGeomag = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                    val gx = gravity[0]
                    val gy = gravity[1]
                    val gz = gravity[2]
                    pitchDeg = (kotlin.math.atan2(gy.toDouble(), kotlin.math.sqrt((gx * gx + gz * gz).toDouble())) * 180.0 / PI).toFloat()
                    rollDeg = (kotlin.math.atan2(gx.toDouble(), gz.toDouble()) * 180.0 / PI).toFloat()
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomag, 0, 3)
                    hasGeomag = true
                }
                if (hasGravity && hasGeomag) {
                    val rMat = FloatArray(9)
                    val iMat = FloatArray(9)
                    if (SensorManager.getRotationMatrix(rMat, iMat, gravity, geomag)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(rMat, orientation)
                        val deg = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                        azimuthDeg = deg
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        try {
            if (accelSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }
            if (magSensor != null) {
                sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {
        }

        onDispose {
            try {
                sensorManager?.unregisterListener(listener)
            } catch (_: Exception) {
            }
        }
    }

    // Procedural Ambient Soundscape Synthesizer (Brown Noise / Soft Rain / 432Hz Zen Drone)
    var activeSoundMode by remember { mutableIntStateOf(0) } // 0 = Off, 1 = Brown Noise, 2 = Night Rain, 3 = 432Hz Zen
    var nightlightWarmthIdx by remember { mutableIntStateOf(0) } // 0 = Off, 1 = Amber 2000K, 2 = Crimson 1500K, 3 = Soft Cyan

    val nightlightColors = listOf(
        Color.Transparent to "Off",
        Color(0xFFFF9500) to "Amber 2000K",
        Color(0xFFFF3B30) to "Crimson 1500K",
        Color(0xFF38BDF8) to "Soft Cyan"
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
                            1 -> { // Soothing Brown Noise
                                val white = Random.nextDouble(-1.0, 1.0)
                                lastOut = (lastOut + (0.02 * white)) / 1.02
                                (lastOut * 3.2).coerceIn(-0.9, 0.9)
                            }
                            2 -> { // Soft Night Rain
                                val white = Random.nextDouble(-1.0, 1.0)
                                lastOut = 0.85 * lastOut + 0.15 * white
                                (lastOut * 0.45).coerceIn(-0.9, 0.9)
                            }
                            else -> { // 432Hz + 216Hz Warm Zen Chord
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
            .padding(start = 18.dp, end = 30.dp, top = 14.dp, bottom = 46.dp)
            .testTag("dock_lab_tool")
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT PANE: 2D Hardware Dock Spirit Level & Magnetic Compass
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "04 • DOCK SPIRIT LEVEL",
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        val cardinal = when (((azimuthDeg + 22.5f) / 45f).toInt() % 8) {
                            0 -> "N"
                            1 -> "NE"
                            2 -> "E"
                            3 -> "SE"
                            4 -> "S"
                            5 -> "SW"
                            6 -> "W"
                            else -> "NW"
                        }
                        Text(
                            text = "${azimuthDeg.roundToInt()}° $cardinal",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // 2D Crosshair Spirit Bubble Canvas
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        val isLevel = abs(pitchDeg) < 2.5f && abs(rollDeg) < 2.5f
                        val bubbleColor = if (isLevel) Color(0xFF34D399) else accentColor

                        Canvas(modifier = Modifier.size(136.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val maxR = size.minDimension / 2f - 6f

                            drawCircle(
                                color = OledCardElevated,
                                radius = maxR,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = bubbleColor.copy(alpha = 0.45f),
                                radius = maxR,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.16f),
                                radius = maxR * 0.42f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5f)
                            )
                            // Crosshairs
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(cx - maxR, cy),
                                end = Offset(cx + maxR, cy),
                                strokeWidth = 1.2f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(cx, cy - maxR),
                                end = Offset(cx, cy + maxR),
                                strokeWidth = 1.2f
                            )

                            // Bubble position mapped from pitch/roll
                            val normX = (pitchDeg / 45f).coerceIn(-0.75f, 0.75f)
                            val normY = (rollDeg / 45f).coerceIn(-0.75f, 0.75f)
                            val bx = cx + normX * maxR
                            val by = cy + normY * maxR

                            drawCircle(
                                color = bubbleColor.copy(alpha = 0.30f),
                                radius = 20f,
                                center = Offset(bx, by)
                            )
                            drawCircle(
                                color = bubbleColor,
                                radius = 11f,
                                center = Offset(bx, by)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "PITCH %+.1f°", pitchDeg),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "TILT %+.1f°", rollDeg),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // RIGHT PANE: Bedside Soundscape Synthesizer & Warm OLED Nightlight
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(26.dp),
                color = OledCardBg,
                border = BorderStroke(
                    1.dp,
                    if (activeNightlightColor != Color.Transparent) activeNightlightColor.copy(alpha = 0.65f) else OledBorderSubtle
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (activeNightlightColor != Color.Transparent) {
                                Brush.radialGradient(
                                    colors = listOf(activeNightlightColor.copy(alpha = 0.36f), Color.Transparent)
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                                )
                            }
                        )
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Bedside Soundscape & Nightlight",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Real-time synthesized sleep audio & warm OLED dock glow",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }

                        // Soundscape Generator Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "AMBIENT AUDIO SYNTHESIZER",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    0 to "Mute",
                                    1 to "Brown Noise",
                                    2 to "Soft Rain",
                                    3 to "432Hz Zen"
                                ).forEach { (modeIdx, title) ->
                                    val isSel = activeSoundMode == modeIdx
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            activeSoundMode = modeIdx
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) accentColor.copy(alpha = 0.24f) else OledCardElevated,
                                        border = BorderStroke(1.dp, if (isSel) accentColor else OledBorderSubtle),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = title,
                                            color = if (isSel) accentColor else Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Warm OLED Nightlight Glow Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "WARM OLED NIGHTSTAND LAMP",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                nightlightColors.forEachIndexed { idx, (glowCol, label) ->
                                    val isSel = nightlightWarmthIdx == idx
                                    val chipAccent = if (glowCol == Color.Transparent) accentColor else glowCol
                                    Surface(
                                        onClick = {
                                            haptics.tick()
                                            nightlightWarmthIdx = idx
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) chipAccent.copy(alpha = 0.25f) else OledCardElevated,
                                        border = BorderStroke(1.dp, if (isSel) chipAccent else OledBorderSubtle),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = label.substringBefore(" "),
                                            color = if (isSel) chipAccent else Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.5.sp,
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
}
