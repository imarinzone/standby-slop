package com.example.ui.standby

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// Page index 4: Standby Timer & Stopwatch Screen
@Composable
fun TimerPage(
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Vertical Pager supporting dragging/swiping up and down
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> FocusPomodoroTimer(viewModel)
                1 -> HighTechStopwatch(viewModel)
            }
        }

        // Minimalist vertical dots indicator on the right edge of the screen
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until 2) {
                val isSelected = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.35f))
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        }
                )
            }
        }
    }
}

// Sub-Tab 0: Focus Pomodoro Timer
@Composable
fun FocusPomodoroTimer(viewModel: StandbyViewModel) {
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    val gradientColors = if (gradientIdx > 0 && gradientIdx < viewModel.gradients.size) viewModel.gradients[gradientIdx].second else null

    var focusMinutes by remember { mutableStateOf(25) }
    var currentSegmentIndex by remember { mutableStateOf(0) }
    
    fun getDurationForSegment(index: Int, focusMins: Int): Int {
        return when (index) {
            0, 2, 4, 6 -> focusMins * 60 // customizable focus
            1, 3, 5 -> 5 * 60            // 5 minutes short break
            7 -> 15 * 60                // 15 minutes long break
            else -> focusMins * 60
        }
    }
    
    fun getSegmentLabel(index: Int): String {
        return when (index) {
            0 -> "Focus Period 1"
            1 -> "Short Break"
            2 -> "Focus Period 2"
            3 -> "Short Break"
            4 -> "Focus Period 3"
            5 -> "Short Break"
            6 -> "Focus Period 4"
            7 -> "Long Break (Hike)"
            else -> ""
        }
    }

    var initialSeconds by remember { mutableStateOf(1500) } // Default of 25 minutes
    var secondsLeft by remember { mutableStateOf(1500) }
    var isRunning by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    
    // Timer ticker loop
    LaunchedEffect(isRunning, secondsLeft) {
        if (isRunning && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
            if (secondsLeft == 0) {
                isRunning = false
                currentSegmentIndex = (currentSegmentIndex + 1) % 8
                val nextDur = getDurationForSegment(currentSegmentIndex, focusMinutes)
                initialSeconds = nextDur
                secondsLeft = nextDur
            }
        }
    }

    val backgroundBrush = remember(accentColor, gradientColors) {
        if (gradientColors != null) {
            Brush.linearGradient(gradientColors)
        } else {
            Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.45f),
                    accentColor,
                    Color.Black
                )
            )
        }
    }

    // Gorgeous Gradient Background (styled according to theme index)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (bgUri != null) Modifier else Modifier.background(brush = backgroundBrush)
            )
            .padding(horizontal = 48.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Add a dark overlay just to make sure timer remains readable
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Side: Ring indicator and Countdown face
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // Large outer circle matching alignment in mockup
                Box(
                    modifier = Modifier.size(310.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val progress = if (initialSeconds > 0) secondsLeft.toFloat() / initialSeconds.toFloat() else 1f
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Soft overlay outline/track ring
                        drawCircle(
                            color = Color.White.copy(alpha = 0.15f),
                            radius = size.minDimension / 2f,
                            style = Stroke(width = 8.dp.toPx())
                        )
                        // Active countdown sweep
                        drawArc(
                            color = accentColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (1f - progress),
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Content inside the ring: Timer values and Action Pills
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val minutes = secondsLeft / 60
                        val secs = secondsLeft % 60
                        val timerString = String.format("%02d:%02d", minutes, secs)
                        
                        val pomodoroTextStyle = androidx.compose.ui.text.TextStyle(
                            color = if (gradientColors == null && !outlineVal) Color.White else Color.Unspecified,
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            drawStyle = if (outlineVal) androidx.compose.ui.graphics.drawscope.Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                            shadow = if (glowVal && !outlineVal) androidx.compose.ui.graphics.Shadow(color = accentColor, offset = Offset(0f, 0f), blurRadius = 20f) else null
                        )
                        val textGradientModifier = if (gradientColors != null) {
                            Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
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
                            modifier = textGradientModifier
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls container inside the ring
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Configuration Gear Wheel Settings
                            IconButton(
                                onClick = { showConfigDialog = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Set Timer Intervals",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Primary Play/Pause/Reset rounded box (as shown in mockup image)
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .clickable { isRunning = !isRunning }
                                    .size(width = 110.dp, height = 54.dp),
                                shadowElevation = 4.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = if (isRunning) "Pause" else "Start",
                                        color = accentColor,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Quick Reset
                            IconButton(
                                onClick = {
                                    isRunning = false
                                    secondsLeft = initialSeconds
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Timer",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Right Side: Focus track list & completed Pomodoros indicators (from layout 1)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "FOCUS TIME",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Row of Pomodoro / Break intervals (Mirroring layout exactly!)
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 4 Pomodoro icons interleaved with 3 break Coffee Cups and a final Hiker
                        listOf(
                            Icons.Default.Adjust to "P1", // Pomodoro 1
                            Icons.Default.Coffee to "B1", // Break 1
                            Icons.Default.Adjust to "P2", // Pomodoro 2
                            Icons.Default.Coffee to "B2", // Break 2
                            Icons.Default.Adjust to "P3", // Pomodoro 3
                            Icons.Default.Coffee to "B3", // Break 3
                            Icons.Default.Adjust to "P4", // Pomodoro 4
                            Icons.Default.DirectionsRun to "L" // Long break/Hiking trip!
                        ).forEachIndexed { index, (icon, label) ->
                            val isCompleted = index < currentSegmentIndex
                            val isCurrent = index == currentSegmentIndex
                            
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) Color.White.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .then(
                                        if (isCurrent) Modifier.border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        isRunning = false
                                        currentSegmentIndex = index
                                        val duration = getDurationForSegment(index, focusMinutes)
                                        initialSeconds = duration
                                        secondsLeft = duration
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isCurrent) {
                                        Color.White
                                    } else if (isCompleted) {
                                        Color.White.copy(alpha = 0.7f)
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Dynamic active phase label
                Text(
                    text = getSegmentLabel(currentSegmentIndex).uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Interactive Counter indicator string (as shown: 0/4 POMODOROS)
                val focusCompletedCount = currentSegmentIndex / 2
                Text(
                    text = "$focusCompletedCount/4 POMODOROS DONE",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    // Interval Config Dialog to adjust values easily
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Configure Focus Length", color = Color.White) },
            containerColor = Color.Black,
            textContentColor = Color.White,
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Select a focus duration for sessions:", color = Color.LightGray, fontSize = 13.sp)
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSel = focusMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) Color(0xFFE11D48) else Color.Black,
                            border = if (isSel) null else androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    focusMinutes = mins
                                    isRunning = false
                                    val nextDur = getDurationForSegment(currentSegmentIndex, mins)
                                    initialSeconds = nextDur
                                    secondsLeft = nextDur
                                    showConfigDialog = false
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "$mins Minutes Focus",
                                color = Color.White,
                                modifier = Modifier.padding(14.dp),
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

// Sub-Tab 1: High Tech Stopwatch View
@Composable
fun HighTechStopwatch(viewModel: StandbyViewModel) {
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val selectedFontIdx by viewModel.fontPage4.collectAsState()
    val glowVal by viewModel.getGlowFlow(4).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(4).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(4).collectAsState()
    val bgUri by viewModel.getBgUriFlow(4).collectAsState()

    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    val gradientColors = if (gradientIdx > 0 && gradientIdx < viewModel.gradients.size) viewModel.gradients[gradientIdx].second else null

    var isRunning by remember { mutableStateOf(false) }
    var timeMillis by remember { mutableStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }
    var lastRecordedTime by remember { mutableStateOf(0L) }

    // High fidelity running state updater
    LaunchedEffect(isRunning) {
        if (isRunning) {
            var lastTime = System.currentTimeMillis()
            while (isRunning) {
                delay(10)
                val now = System.currentTimeMillis()
                timeMillis += (now - lastTime)
                lastTime = now
            }
        }
    }

    // Format millisecond timer precisely as 00:00.00
    fun formatDisplayTime(millis: Long): Pair<String, String> {
        val secondsTotal = millis / 1000
        val min = secondsTotal / 60
        val sec = secondsTotal % 60
        val centis = (millis % 1000) / 10
        val mainText = String.format("%02d:%02d", min, sec)
        val centisText = String.format(".%02d", centis)
        return Pair(mainText, centisText)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // True AMOLED Black for maximum power saving
            .padding(horizontal = 48.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Add a dark overlay just to make sure stopwatch remains readable
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }
        // Ticking radial lines around the top/bottom as in image 2
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val colorMajor = accentColor.copy(alpha = 0.28f)
            val colorMinor = accentColor.copy(alpha = 0.12f)
            val strokeMajor = 2.dp.toPx()
            val strokeMinor = 1.dp.toPx()

            // Draw angled radiating dashes at the top
            for (i in -15..15) {
                val valX = w / 2f + (i * 24.dp.toPx())
                val isMajor = i % 5 == 0
                val angleCoeff = i * 0.05f

                // Angled line parameters matching layout perfectly
                val startY = 12.dp.toPx()
                val len = if (isMajor) 18.dp.toPx() else 10.dp.toPx()
                drawLine(
                    color = if (isMajor) colorMajor else colorMinor,
                    start = Offset(valX, startY),
                    end = Offset(valX + (len * angleCoeff), startY + len),
                    strokeWidth = if (isMajor) strokeMajor else strokeMinor,
                    cap = StrokeCap.Round
                )
            }

            // Draw angled radiating dashes at the bottom
            for (i in -15..15) {
                val valX = w / 2f + (i * 24.dp.toPx())
                val isMajor = i % 5 == 0
                val angleCoeff = i * 0.05f

                val startY = h - 12.dp.toPx()
                val len = if (isMajor) 18.dp.toPx() else 10.dp.toPx()
                drawLine(
                    color = if (isMajor) colorMajor else colorMinor,
                    start = Offset(valX, startY),
                    end = Offset(valX - (len * angleCoeff), startY - len),
                    strokeWidth = if (isMajor) strokeMajor else strokeMinor,
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp)) // clear top bars

            // Huge Display: minutes:seconds & centiseconds (offset sizes exactly as layout 2)
            val stopwatchGradientModifier = if (gradientColors != null) {
                Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                    val brush = androidx.compose.ui.graphics.Brush.linearGradient(gradientColors)
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                    }
                }
            } else Modifier

            val mainTextStyle = androidx.compose.ui.text.TextStyle(
                color = if (gradientColors == null && !outlineVal) accentColor else Color.Unspecified,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                drawStyle = if (outlineVal) androidx.compose.ui.graphics.drawscope.Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                shadow = if (glowVal && !outlineVal) androidx.compose.ui.graphics.Shadow(color = accentColor, offset = Offset(0f, 0f), blurRadius = 20f) else null
            )
            val centisTextStyle = androidx.compose.ui.text.TextStyle(
                color = if (gradientColors == null && !outlineVal) accentColor.copy(alpha = 0.7f) else Color.Unspecified,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                drawStyle = if (outlineVal) androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f) else androidx.compose.ui.graphics.drawscope.Fill,
                shadow = if (glowVal && !outlineVal) androidx.compose.ui.graphics.Shadow(color = accentColor, offset = Offset(0f, 0f), blurRadius = 15f) else null
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .then(stopwatchGradientModifier)
            ) {
                val formatted = formatDisplayTime(timeMillis)
                Text(
                    text = formatted.first,
                    style = mainTextStyle
                )
                Text(
                    text = formatted.second,
                    style = centisTextStyle,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Middle Capsule: Last recorded / active Lap log or statistics
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val count = laps.size
                val lapTextValue = if (count > 0) {
                    val activeLapProgress = timeMillis - lastRecordedTime
                    val activeFormatted = formatDisplayTime(activeLapProgress)
                    "Lap ${count + 1}   ${activeFormatted.first}${activeFormatted.second}"
                } else {
                    "Tap Split to start recording laps"
                }

                Surface(
                    color = Color(0xFF1B2028),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .widthIn(min = 280.dp)
                        .padding(horizontal = 24.dp, vertical = 2.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Active lap indicator",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lapTextValue,
                            color = Color(0xFF9CA9BA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Chevron",
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Controls Dock Layer (Reset, Play/Pause toggle, Split/Lap indicator)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 36.dp, end = 36.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Button 1: RESET / Clear laps (Red-tinted backplate) Match image 2!
                IconButton(
                    onClick = {
                        isRunning = false
                        timeMillis = 0L
                        laps.clear()
                        lastRecordedTime = 0L
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF5C1C1C)) // Dark rich red matching mockup
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Stopwatch",
                        tint = Color(0xFFFF9E9E),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Button 2: PRIMARY PLAY / PAUSE (Large rounded purple button) Match image 2!
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accentColor.copy(alpha = 0.15f), // Matching active accent color backplate
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clickable { isRunning = !isRunning }
                        .size(width = 84.dp, height = 72.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Stopwatch",
                            tint = accentColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Button 3: SPLIT / LAP recorder button (Slate-blue backplate) Match image 2!
                IconButton(
                    onClick = {
                        if (isRunning) {
                            val nextLap = timeMillis
                            laps.add(nextLap - lastRecordedTime)
                            lastRecordedTime = nextLap
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accentColor.copy(alpha = 0.1f))
                        .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Record Lap Split",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
