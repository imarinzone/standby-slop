package com.example.ui.standby

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.view.KeyEvent
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    
    // Independent vertical pager states for Left & Right sides
    val leftPagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val rightPagerState = rememberPagerState(initialPage = 2, pageCount = { 5 })

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT HALF PANELS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            VerticalPager(
                state = leftPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                WidgetContainer(
                    pageIndex = page,
                    isLeft = true
                ) {
                    RenderDuoWidget(
                        pageIndex = page,
                        viewModel = viewModel,
                        calendarViewModel = calendarViewModel,
                        weatherViewModel = weatherViewModel
                    )
                }
            }

            // Left Pager vertical dots indicator (as in clock screen representing screen count)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 0 until 5) {
                    val isSelected = i == leftPagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 4.2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.25f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    leftPagerState.animateScrollToPage(i)
                                }
                            }
                    )
                }
            }
        }

        // Soft, ultra-subtle vertical space instead of borders per user request
        Spacer(modifier = Modifier.width(4.dp))

        // RIGHT HALF PANELS
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            VerticalPager(
                state = rightPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                WidgetContainer(
                    pageIndex = page,
                    isLeft = false
                ) {
                    RenderDuoWidget(
                        pageIndex = page,
                        viewModel = viewModel,
                        calendarViewModel = calendarViewModel,
                        weatherViewModel = weatherViewModel
                    )
                }
            }

            // Right Pager vertical dots indicator (as in clock screen representing screen count)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 0 until 5) {
                    val isSelected = i == rightPagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 4.2.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.25f)
                            )
                            .clickable {
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

@Composable
fun WidgetContainer(
    pageIndex: Int,
    isLeft: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // AMOLED True Black Background
            .padding(
                start = if (isLeft) 6.dp else 2.dp,
                end = if (isLeft) 2.dp else 6.dp,
                top = 6.dp,
                bottom = 6.dp
            )
    ) {
        // High-contrast subtle ambient glow for premium AMOLED vibes
        val ambientBrush = when (pageIndex) {
            0 -> Brush.radialGradient(colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.04f), Color.Transparent)) // Clock blue
            1 -> Brush.radialGradient(colors = listOf(Color(0xFFEF4444).copy(alpha = 0.04f), Color.Transparent)) // Alarm Red
            2 -> Brush.radialGradient(colors = listOf(Color(0xFF10B981).copy(alpha = 0.04f), Color.Transparent)) // Calendar Teal
            3 -> Brush.radialGradient(colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.04f), Color.Transparent)) // Music Violet
            else -> Brush.radialGradient(colors = listOf(Color(0xFFF97316).copy(alpha = 0.04f), Color.Transparent)) // Timer Amber
        }
        
        Box(modifier = Modifier.fillMaxSize().background(ambientBrush))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black), // True AMOLED Black for maximum power saving
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
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

// 0. Duo Clock face: Glowing digital dynamic clock with local weather metadata
@Composable
fun DuoClockWidget(
    viewModel: StandbyViewModel,
    weatherViewModel: WeatherViewModel
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val weather by weatherViewModel.weather.collectAsState()
    val showWeatherVal by viewModel.showWeatherPage0.collectAsState()
    val selectedColorIdx by viewModel.colorPage0.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val secFormat = SimpleDateFormat("ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

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
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1.5).sp
            )
            Text(
                text = ":" + secFormat.format(currentTime.time),
                color = accentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = dateFormat.format(currentTime.time).uppercase(Locale.getDefault()),
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )

        // Show weather widget if enabled
        if (showWeatherVal && weather != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${weather?.temperature_2m}°C • ${getWeatherCodeDesc(weather?.weather_code ?: 0)}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
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

// 1. Duo Alarm widget: Large high-contrast neon list card of next alarm
@Composable
fun DuoAlarmWidget(viewModel: StandbyViewModel) {
    val nextSystemAlarm by viewModel.nextSystemAlarm.collectAsState()
    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first

    val hasAlarm = nextSystemAlarm != null && nextSystemAlarm != "No Alarm"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = if (hasAlarm) accentColor else Color.Gray.copy(alpha = 0.4f),
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "UPCOMING ALARM",
            color = Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = nextSystemAlarm ?: "No Active Alarm",
            color = if (hasAlarm) Color.White else Color.DarkGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

// 2. Duo Calendar: Agenda items scrollable widget list
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuoCalendarWidget(
    calendarViewModel: CalendarViewModel,
    standbyViewModel: StandbyViewModel
) {
    val events by calendarViewModel.events.collectAsState()
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
            startOnMonday = false,
            highlightDay = currentCal.get(Calendar.DAY_OF_MONTH),
            showEventDots = true,
            events = events,
            accentColor = accentColor,
            customFont = FontFamily.Default,
            monthLabel = monthName
        )
    }
}

// 3. Duo Music Widget: Spinning vinyl disc and compact playback controllers
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuoMusicWidget(standbyViewModel: StandbyViewModel) {
    val context = LocalContext.current
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

    // Notification permission setup
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
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Periodic check to keep system alarm status accurate on view resume/active
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

    // Vinyl Rotation angle
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isCurrentlyPlaying) {
        if (isCurrentlyPlaying) {
            rotationAngle.animateTo(
                targetValue = rotationAngle.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotationAngle.stop()
        }
    }

    if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Notifications Setup",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Permit playback status sync",
                color = Color.Gray,
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { notificationPermissionState?.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    "GRANT",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (accentColor == Color.White) Color.Black else Color.White
                )
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background album art similar to main music screen
            if (albumArt != null) {
                Image(
                    bitmap = albumArt!!.asImageBitmap(),
                    contentDescription = "Background Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(listOf(accentColor.copy(alpha = 0.35f), Color.Transparent))
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Media Title Metadata
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = trackTitle ?: "No Track Playing",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artistName ?: "Unknown Artist",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Action Deck buttons upscaled
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    IconButton(
                        onClick = { triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Prev",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable { 
                                triggerMediaKey(context, if (isCurrentlyPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = accentColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle",
                                tint = if (accentColor == Color.White) Color.Black else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

// 4. Duo Timer: Interactive countdown Focus block and split stopwatch
@Composable
fun DuoTimerWidget(viewModel: StandbyViewModel) {
    val selectedColorIdx by viewModel.colorPage4.collectAsState()
    val accentColor = viewModel.colors[selectedColorIdx].first

    var isRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(1500) } // Standard 25 mins

    LaunchedEffect(isRunning, secondsLeft) {
        if (isRunning && secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
            if (secondsLeft == 0) {
                isRunning = false
            }
        }
    }

    val min = secondsLeft / 60
    val sec = secondsLeft % 60
    val formatted = String.format("%02d:%02d", min, sec)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            val ratio = secondsLeft.toFloat() / 1500f
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            Text(
                text = formatted,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Actions Container
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (isRunning) Color(0xFF5C1C1C) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clickable { isRunning = !isRunning }
            ) {
                Text(
                    text = if (isRunning) "PAUSE" else "START",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = CircleShape,
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        isRunning = false
                        secondsLeft = 1500
                    },
                contentColor = Color.White
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

// Media key dispatcher helper
private fun triggerMediaKey(context: Context, keyCode: Int) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    } catch (e: Exception) {
        android.util.Log.e("DuoPage", "Media dispatch failed", e)
    }
}

// Title & icon loaders for dynamic configuration listings
private fun getWidgetTitle(pageIndex: Int): String {
    return when (pageIndex) {
        0 -> "AMBIENT CLOCK"
        1 -> "ALARM"
        2 -> "LOCAL AGENDA"
        3 -> "MUSIC PLAYER"
        else -> "FOCUS COUNTER"
    }
}

private fun getWidgetIcon(pageIndex: Int): ImageVector {
    return when (pageIndex) {
        0 -> Icons.Default.Schedule
        1 -> Icons.Default.Alarm
        2 -> Icons.Default.Event
        3 -> Icons.Default.MusicNote
        else -> Icons.Default.HourglassEmpty
    }
}
