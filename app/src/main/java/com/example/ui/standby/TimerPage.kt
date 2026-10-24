package com.example.ui.standby

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimerPage(
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val initialMode = remember { viewModel.timerModeIndex.value }
    val pagerState = rememberPagerState(initialPage = initialMode, pageCount = { 2 })
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
        modifier = modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FocusPomodoroTimer(viewModel)
                    1 -> HighTechStopwatch(viewModel)
                }
            }

            // Material 3 Vertical Mode Indicator Pill on the Right Edge
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                shape = CircleShape,
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 0 until 2) {
                        val isSelected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .size(width = 7.dp, height = if (isSelected) 16.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) colorScheme.primary
                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    haptics.tick()
                                    viewModel.setTimerModeIndex(i)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

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
            0 -> "Focus Session 1"
            1 -> "Short Break"
            2 -> "Focus Session 2"
            3 -> "Short Break"
            4 -> "Focus Session 3"
            5 -> "Short Break"
            6 -> "Focus Session 4"
            7 -> "Long Recovery Break"
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
            .background(colorScheme.surface)
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val isCompactWidth = maxWidth < 640.dp
        val outerHPad = if (isCompactWidth) 20.dp else 36.dp
        val outerVPad = if (isCompactHeight) 12.dp else 20.dp
        val paneSpacing = if (isCompactWidth) 14.dp else 24.dp

        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(colorScheme.surface.copy(alpha = 0.72f)))
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 12.dp, top = outerVPad, bottom = outerVPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(paneSpacing)
        ) {
            // Left Pane: Material 3 Circular Countdown Dial
            ElevatedCard(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = colorScheme.surfaceContainerLow
                )
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompactHeight) 10.dp else 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val dialSize = (minOf(maxWidth, maxHeight) * 0.94f).coerceIn(160.dp, 280.dp)
                    val isSmallDial = dialSize < 230.dp
                    val timerFontSize = (dialSize.value * 0.205f).coerceIn(32f, 56f).sp
                    val iconBtnSize = if (isSmallDial) 36.dp else 44.dp
                    val iconSize = if (isSmallDial) 16.dp else 20.dp
                    val strokeWidthDp = if (isSmallDial) 8.dp else 10.dp

                    Box(
                        modifier = Modifier.size(dialSize),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = if (initialSeconds > 0) secondsLeft.toFloat() / initialSeconds.toFloat() else 1f
                        val trackColor = colorScheme.surfaceContainerHighest

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokePx = strokeWidthDp.toPx()
                            drawCircle(
                                color = trackColor,
                                radius = (size.minDimension - strokePx) / 2f,
                                style = Stroke(width = strokePx)
                            )
                            drawArc(
                                color = accentColor,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = strokeWidthDp + 8.dp)
                        ) {
                            val minutes = secondsLeft / 60
                            val secs = secondsLeft % 60
                            val timerString = String.format("%02d:%02d", minutes, secs)

                            val pomodoroTextStyle = androidx.compose.ui.text.TextStyle(
                                color = if (gradientColors == null && !outlineVal) colorScheme.onSurface else Color.Unspecified,
                                fontSize = timerFontSize,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                drawStyle = if (outlineVal) Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                                shadow = if (glowVal && !outlineVal) {
                                    androidx.compose.ui.graphics.Shadow(
                                        color = accentColor.copy(alpha = 0.55f),
                                        offset = Offset(0f, 0f),
                                        blurRadius = 18f
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

                            Spacer(modifier = Modifier.height(if (isSmallDial) 8.dp else 14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(if (isSmallDial) 6.dp else 10.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.tick()
                                        showConfigDialog = true
                                    },
                                    modifier = Modifier.size(iconBtnSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Set Focus Duration",
                                        modifier = Modifier.size(iconSize)
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptics.confirm()
                                        isRunning = !isRunning
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accentColor,
                                        contentColor = if (accentColor == Color.White) Color.Black else Color.White
                                    ),
                                    contentPadding = PaddingValues(
                                        horizontal = if (isSmallDial) 12.dp else 20.dp,
                                        vertical = if (isSmallDial) 6.dp else 10.dp
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
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isSmallDial) 12.sp else 14.sp,
                                        maxLines = 1
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.tick()
                                        isRunning = false
                                        secondsLeft = initialSeconds
                                    },
                                    modifier = Modifier.size(iconBtnSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Timer",
                                        modifier = Modifier.size(iconSize)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Pane: Material 3 Focus Timeline & Phase Card
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = colorScheme.surfaceContainerLow
                )
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompactHeight) 16.dp else 24.dp),
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
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "POMODORO MATRIX",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                        Text(
                            text = getSegmentLabel(currentSegmentIndex),
                            style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontFamily = customFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val focusCompletedCount = currentSegmentIndex / 2
                        Text(
                            text = "$focusCompletedCount of 4 focus intervals completed • ${focusMinutes}m per round",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = if (isCompactHeight) 12.dp else 18.dp)
                        )

                        Surface(
                            color = colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                                    Icons.Default.DirectionsRun to "L"
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
                                            isCompleted -> colorScheme.secondaryContainer
                                            else -> Color.Transparent
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label,
                                                tint = when {
                                                    isCurrent -> if (accentColor == Color.White) Color.Black else Color.White
                                                    isCompleted -> colorScheme.onSecondaryContainer
                                                    else -> colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
            containerColor = colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Focus Session Length",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface,
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
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSel) colorScheme.primaryContainer else colorScheme.surfaceContainer,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSel) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
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
                                    color = if (isSel) colorScheme.onPrimaryContainer else colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
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
                TextButton(onClick = { showConfigDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

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
    val customFont = viewModel.fonts[selectedFontIdx].first
    val gradientColors = if (gradientIdx > 0 && gradientIdx < viewModel.gradients.size) {
        viewModel.gradients[gradientIdx].second
    } else null

    var isRunning by remember { mutableStateOf(false) }
    var timeMillis by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }
    var lastRecordedTime by remember { mutableLongStateOf(0L) }

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

    fun formatDisplayTime(millis: Long): Pair<String, String> {
        val secondsTotal = millis / 1000
        val min = secondsTotal / 60
        val sec = secondsTotal % 60
        val centis = (millis % 1000) / 10
        val mainText = String.format("%02d:%02d", min, sec)
        val centisText = String.format(".%02d", centis)
        return Pair(mainText, centisText)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val outerHPad = if (maxWidth < 640.dp) 20.dp else 36.dp
        val outerVPad = if (isCompactHeight) 12.dp else 20.dp

        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(colorScheme.surface.copy(alpha = 0.72f)))
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = outerHPad, end = outerHPad + 12.dp, top = outerVPad, bottom = outerVPad),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerLow
            )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompactHeight) 16.dp else 24.dp)
            ) {
                val mainFontSize = (minOf(maxWidth.value * 0.14f, maxHeight.value * 0.25f)).coerceIn(44f, 72f).sp
                val centisFontSize = (mainFontSize.value * 0.52f).sp

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PRECISION STOPWATCH",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

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
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont,
                        drawStyle = if (outlineVal) Stroke(width = 3f) else androidx.compose.ui.graphics.drawscope.Fill,
                        shadow = if (glowVal && !outlineVal) {
                            androidx.compose.ui.graphics.Shadow(
                                color = accentColor.copy(alpha = 0.5f),
                                offset = Offset(0f, 0f),
                                blurRadius = 18f
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

                    val count = laps.size
                    val lapTextValue = if (count > 0) {
                        val activeLapProgress = timeMillis - lastRecordedTime
                        val activeFormatted = formatDisplayTime(activeLapProgress)
                        "Lap ${count + 1} • ${activeFormatted.first}${activeFormatted.second}"
                    } else {
                        "Tap Split while running to record lap intervals"
                    }

                    Surface(
                        color = colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = if (isCompactHeight) 14.dp else 20.dp,
                                vertical = if (isCompactHeight) 6.dp else 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Lap Indicator",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lapTextValue,
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val sideBtnSize = if (isCompactHeight) 48.dp else 56.dp
                    val mainBtnWidth = if (isCompactHeight) 76.dp else 88.dp
                    val mainBtnHeight = if (isCompactHeight) 52.dp else 64.dp

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 18.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                isRunning = false
                                timeMillis = 0L
                                laps.clear()
                                lastRecordedTime = 0L
                            },
                            modifier = Modifier.size(sideBtnSize),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colorScheme.errorContainer,
                                contentColor = colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Stopwatch",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                haptics.confirm()
                                isRunning = !isRunning
                            },
                            modifier = Modifier.size(width = mainBtnWidth, height = mainBtnHeight),
                            shape = RoundedCornerShape(22.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor,
                                contentColor = if (accentColor == Color.White) Color.Black else Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Stopwatch",
                                modifier = Modifier.size(if (isCompactHeight) 28.dp else 32.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                if (isRunning) {
                                    haptics.tick()
                                    val nextLap = timeMillis
                                    laps.add(nextLap - lastRecordedTime)
                                    lastRecordedTime = nextLap
                                }
                            },
                            enabled = isRunning,
                            modifier = Modifier.size(sideBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Record Lap Split",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
