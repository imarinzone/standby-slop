package com.example.ui.standby

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DuoPage(
    viewModel: StandbyViewModel,
    modifier: Modifier = Modifier,
    calendarViewModel: CalendarViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

    val initialLeft = remember { viewModel.duoLeftWidgetIndex.value }
    val initialRight = remember { viewModel.duoRightWidgetIndex.value }

    val leftPagerState = rememberPagerState(initialPage = initialLeft, pageCount = { 5 })
    val rightPagerState = rememberPagerState(initialPage = initialRight, pageCount = { 5 })

    var hasInitLeft by remember { mutableStateOf(false) }
    LaunchedEffect(leftPagerState.currentPage) {
        if (!hasInitLeft) {
            hasInitLeft = true
        } else {
            haptics.tick()
            viewModel.setDuoLeftWidgetIndex(leftPagerState.currentPage)
        }
    }

    var hasInitRight by remember { mutableStateOf(false) }
    LaunchedEffect(rightPagerState.currentPage) {
        if (!hasInitRight) {
            hasInitRight = true
        } else {
            haptics.tick()
            viewModel.setDuoRightWidgetIndex(rightPagerState.currentPage)
        }
    }

    val bgUri by viewModel.getBgUriFlow(5).collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgUri != null) {
                coil.compose.AsyncImage(
                    model = bgUri,
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(colorScheme.surface.copy(alpha = 0.72f)))
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactHeight = maxHeight < 380.dp
                val isCompactWidth = maxWidth < 620.dp
                val outerHPad = if (isCompactWidth) 14.dp else 20.dp
                val outerVPad = if (isCompactHeight) 10.dp else 16.dp
                val paneGap = if (isCompactWidth) 12.dp else 16.dp

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = outerHPad, vertical = outerVPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(paneGap)
                ) {
                    // LEFT HALF PANEL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        VerticalPager(
                            state = leftPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            WidgetContainer(pageIndex = page, isLeftPane = true) {
                                RenderDuoWidget(
                                    pageIndex = page,
                                    viewModel = viewModel,
                                    calendarViewModel = calendarViewModel,
                                    weatherViewModel = weatherViewModel
                                )
                            }
                        }

                        // Left Pager M3 Vertical Indicator
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp),
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 0 until 5) {
                                    val isSelected = i == leftPagerState.currentPage
                                    Box(
                                        modifier = Modifier
                                            .size(width = 6.dp, height = if (isSelected) 13.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) colorScheme.primary
                                                else colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            )
                                            .clickable {
                                                haptics.tick()
                                                viewModel.setDuoLeftWidgetIndex(i)
                                                coroutineScope.launch {
                                                    leftPagerState.animateScrollToPage(i)
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // RIGHT HALF PANEL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        VerticalPager(
                            state = rightPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            WidgetContainer(pageIndex = page, isLeftPane = false) {
                                RenderDuoWidget(
                                    pageIndex = page,
                                    viewModel = viewModel,
                                    calendarViewModel = calendarViewModel,
                                    weatherViewModel = weatherViewModel
                                )
                            }
                        }

                        // Right Pager M3 Vertical Indicator
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp),
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 0 until 5) {
                                    val isSelected = i == rightPagerState.currentPage
                                    Box(
                                        modifier = Modifier
                                            .size(width = 6.dp, height = if (isSelected) 13.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) colorScheme.primary
                                                else colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            )
                                            .clickable {
                                                haptics.tick()
                                                viewModel.setDuoRightWidgetIndex(i)
                                                coroutineScope.launch {
                                                    rightPagerState.animateScrollToPage(i)
                                                }
                                            }
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

@Composable
fun WidgetContainer(
    pageIndex: Int,
    isLeftPane: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isLeftPane) 26.dp else 12.dp,
                    end = if (isLeftPane) 12.dp else 26.dp,
                    top = 12.dp,
                    bottom = 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun RenderDuoWidget(
    pageIndex: Int,
    viewModel: StandbyViewModel,
    calendarViewModel: CalendarViewModel,
    weatherViewModel: WeatherViewModel
) {
    when (pageIndex) {
        0 -> DuoClockWidget(viewModel, weatherViewModel)
        1 -> DuoAlarmWidget(viewModel)
        2 -> DuoCalendarWidget(calendarViewModel, viewModel)
        3 -> DuoMusicWidget(viewModel)
        4 -> DuoTimerWidget(viewModel)
    }
}

@Composable
fun DuoClockWidget(
    viewModel: StandbyViewModel,
    weatherViewModel: WeatherViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val weather by weatherViewModel.weather.collectAsState()
    val showWeatherVal by viewModel.showWeatherPage0.collectAsState()
    val selectedColorIdx by viewModel.colorPage5.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first
    val use24Hour by viewModel.use24HourFormat.collectAsState()

    val targetBrightness by viewModel.clockBrightnessFactor.collectAsState()
    val isPreAlarmDimming by viewModel.isPreAlarmDimmingActive.collectAsState()
    val animatedBrightness by animateFloatAsState(
        targetValue = targetBrightness.coerceIn(0.10f, 1.0f),
        animationSpec = tween(durationMillis = 600),
        label = "duoClockPreAlarmBrightness"
    )

    LaunchedEffect(Unit) {
        viewModel.updateNextAlarm()
        while (true) {
            val now = Calendar.getInstance()
            currentTime = now
            if (now.get(Calendar.SECOND) % 15 == 0) {
                viewModel.updateNextAlarm(now)
            }
            delay(1000)
        }
    }

    val timeFormat = remember(use24Hour) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "hh:mm", Locale.getDefault())
    }
    val secFormat = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = animatedBrightness },
        contentAlignment = Alignment.Center
    ) {
        val mainFontSize = (minOf(maxWidth.value * 0.22f, maxHeight.value * 0.28f)).coerceIn(34f, 54f).sp
        val secFontSize = (mainFontSize.value * 0.37f).sp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = timeFormat.format(currentTime.time),
                    color = colorScheme.onSurface,
                    fontSize = mainFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-1.5).sp,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = ":" + secFormat.format(currentTime.time),
                    color = accentColor,
                    fontSize = secFontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = CircleShape,
                color = colorScheme.secondaryContainer
            ) {
                Text(
                    text = dateFormat.format(currentTime.time).uppercase(Locale.getDefault()),
                    color = colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            if (showWeatherVal && weather != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${weather?.temperature_2m}°C • ${getWeatherCodeDesc(weather?.weather_code ?: 0)}",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isPreAlarmDimming) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pre-alarm dim • ${(animatedBrightness * 100).roundToInt()}%",
                    color = accentColor.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

private fun getWeatherCodeDesc(code: Int): String {
    return when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57, 66, 67 -> "Freezing Rain"
        61, 63, 65 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        85, 86 -> "Snow Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }
}

@Composable
fun DuoAlarmWidget(viewModel: StandbyViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val nextSystemAlarm by viewModel.nextSystemAlarm.collectAsState()
    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first

    val hasAlarm = nextSystemAlarm != null && nextSystemAlarm != "No Alarm"

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxHeight < 220.dp
        val iconCircleSize = if (isCompact) 44.dp else 56.dp
        val iconSize = if (isCompact) 22.dp else 28.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.size(iconCircleSize),
                shape = CircleShape,
                color = if (hasAlarm) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (hasAlarm) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))
            Text(
                text = "UPCOMING ALARM",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nextSystemAlarm ?: "No Active Alarm",
                color = if (hasAlarm) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuoCalendarWidget(
    calendarViewModel: CalendarViewModel,
    standbyViewModel: StandbyViewModel
) {
    val events by calendarViewModel.events.collectAsState()
    val startOnMon by calendarViewModel.startWeekOnMonday.collectAsState()
    val showDots by calendarViewModel.showEventDots.collectAsState()
    val context = LocalContext.current
    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first

    val calendarPermissionState = rememberPermissionState(
        android.Manifest.permission.READ_CALENDAR
    )

    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(calendarPermissionState.status.isGranted) {
        hasCalendarPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED || calendarPermissionState.status.isGranted
        if (hasCalendarPermission) {
            calendarViewModel.loadLocalEvents(context)
        }
    }

    LaunchedEffect(Unit) {
        hasCalendarPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCalendarPermission) {
            calendarViewModel.loadLocalEvents(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        val currentCal = remember { Calendar.getInstance() }
        val monthName = remember(currentCal) {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCal.time)
        }
        MonthlyGrid(
            startOnMonday = startOnMon,
            highlightDay = currentCal.get(Calendar.DAY_OF_MONTH),
            showEventDots = showDots,
            events = events,
            accentColor = accentColor,
            customFont = FontFamily.Default,
            monthLabel = monthName
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuoMusicWidget(standbyViewModel: StandbyViewModel) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val trackTitle by MediaStateHolder.trackTitle.collectAsState()
    val artistName by MediaStateHolder.artistName.collectAsState()
    val isPlayingFlow by MediaStateHolder.isPlaying.collectAsState()
    val albumArt by MediaStateHolder.albumArt.collectAsState()

    var isMusicActive by remember {
        mutableStateOf(
            try {
                audioManager?.isMusicActive == true
            } catch (e: Exception) {
                false
            }
        )
    }

    val selectedColorIdx by standbyViewModel.colorPage3.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    LaunchedEffect(notificationPermissionState?.status?.isGranted) {
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) ||
                (notificationPermissionState?.status?.isGranted == true)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isMusicActive = try {
                audioManager?.isMusicActive == true
            } catch (e: Exception) {
                false
            }
            delay(1000)
        }
    }

    val isCurrentlyPlaying = isPlayingFlow || isMusicActive

    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Media Sync Permission",
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
                onClick = {
                    haptics.confirm()
                    notificationPermissionState?.launchPermissionRequest()
                }
            ) {
                Text("Allow")
            }
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
        ) {
            val isCompact = maxHeight < 210.dp || maxWidth < 210.dp
            val sideBtnSize = if (isCompact) 38.dp else 44.dp
            val mainBtnSize = if (isCompact) 48.dp else 56.dp

            if (albumArt != null) {
                Image(
                    bitmap = albumArt!!.asImageBitmap(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(listOf(accentColor.copy(alpha = 0.2f), Color.Transparent))
                        )
                )
            }

            val fgColor = if (albumArt != null) Color.White else colorScheme.onSurface
            val subColor = if (albumArt != null) Color.White.copy(alpha = 0.75f) else colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompact) 10.dp else 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = trackTitle ?: "No Track Playing",
                        color = fgColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = artistName ?: "System Media",
                        color = subColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.tick()
                            triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        },
                        modifier = Modifier.size(sideBtnSize)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            haptics.confirm()
                            triggerMediaKey(
                                context,
                                if (isCurrentlyPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
                            )
                        },
                        modifier = Modifier.size(mainBtnSize),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accentColor,
                            contentColor = if (accentColor == Color.White) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(if (isCompact) 24.dp else 28.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            haptics.tick()
                            triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                        },
                        modifier = Modifier.size(sideBtnSize)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(if (isCompact) 18.dp else 22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuoTimerWidget(viewModel: StandbyViewModel) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first
    val focusMinutes by viewModel.pomodoroFocusMinutes.collectAsState()

    var isRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember(focusMinutes) { mutableIntStateOf(focusMinutes * 60) }

    LaunchedEffect(isRunning, secondsLeft) {
        if (isRunning && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
            if (secondsLeft == 0) {
                haptics.confirm()
                isRunning = false
            }
        }
    }

    val min = secondsLeft / 60
    val sec = secondsLeft % 60
    val formatted = String.format("%02d:%02d", min, sec)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val isCompact = maxHeight < 210.dp
        val dialSize = (minOf(maxWidth, maxHeight) * 0.48f).coerceIn(72.dp, 98.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.size(dialSize),
                contentAlignment = Alignment.Center
            ) {
                val totalSecs = (focusMinutes * 60).coerceAtLeast(1)
                val ratio = secondsLeft.toFloat() / totalSecs.toFloat()
                val trackColor = colorScheme.surfaceContainerHighest
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = trackColor,
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * ratio,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = formatted,
                    color = colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.confirm()
                        isRunning = !isRunning
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRunning) "Pause" else "Start",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        haptics.tick()
                        isRunning = false
                        secondsLeft = focusMinutes * 60
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun triggerMediaKey(context: Context, keyCode: Int) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    } catch (e: Exception) {
        android.util.Log.e("DuoPage", "Media dispatch failed", e)
    }
}
