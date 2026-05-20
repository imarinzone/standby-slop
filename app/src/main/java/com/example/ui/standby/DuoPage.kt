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
                    title = getWidgetTitle(page),
                    icon = getWidgetIcon(page),
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

            // Up-Down visual helper indicator
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Slide Up",
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 24.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Slide Down",
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Soft, ultra-subtle vertical separator in between to keep a clean, modern aesthetic
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.04f))
        )

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
                    title = getWidgetTitle(page),
                    icon = getWidgetIcon(page),
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

            // Up-Down visual helper indicator
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Slide Up",
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 24.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Slide Down",
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun WidgetContainer(
    title: String,
    icon: ImageVector,
    pageIndex: Int,
    isLeft: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // AMOLED True Black Background
            .padding(
                start = if (isLeft) 12.dp else 4.dp,
                end = if (isLeft) 4.dp else 12.dp,
                top = 12.dp,
                bottom = 12.dp
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF080A0D)), // Ultra-deep slate near-black
            modifier = Modifier
                .fillMaxSize()
                .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar showing current active module
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = when (pageIndex) {
                                0 -> Color(0xFF60A5FA)
                                1 -> Color(0xFFFB7185)
                                2 -> Color(0xFF2DD4BF)
                                3 -> Color(0xFFC084FC)
                                else -> Color(0xFFFDBA74)
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = title,
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                    
                    Text(
                        text = "WIDGET 0${pageIndex + 1}",
                        color = Color.White.copy(alpha = 0.15f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }

                // Main Widget Page content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    content()
                }
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
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Configure & customize in the full Alarms tab.",
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 9.sp,
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

    if (!hasCalendarPermission) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Calendar Locked",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Access is required",
                color = Color.Gray,
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { calendarPermissionState.launchPermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    "GRANT ACCESS",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (accentColor == Color.White) Color.Black else Color.White
                )
            }
        }
    } else if (events.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = null,
                tint = Color.DarkGray,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Agenda Empty",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "No upcoming events",
                color = Color.DarkGray,
                fontSize = 9.sp
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "UPCOMING SCHEDULE",
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            
            // Show top 2 calendar events for the narrow form factor
            events.take(2).forEach { event ->
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = event.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.dtStart))
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = timeStr,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

// 3. Duo Music Widget: Spinning vinyl disc and compact playback controllers
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuoMusicWidget(standbyViewModel: StandbyViewModel) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var isPlaying by remember {
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
            isPlaying = try {
                audioManager?.isMusicActive == true
            } catch (e: Exception) {
                false
            }
            delay(1000)
        }
    }

    // Vinyl Rotation angle
    val rotationAngle = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Spinning Classic Record Disk
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .rotate(rotationAngle.value),
                    contentAlignment = Alignment.Center
                ) {
                    // Draws circles representing ridges on record disk
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.DarkGray.copy(alpha = 0.8f), radius = size.minDimension / 2.3f, style = Stroke(width = 1.dp.toPx()))
                        drawCircle(color = Color.DarkGray.copy(alpha = 0.5f), radius = size.minDimension / 3.4f, style = Stroke(width = 1.dp.toPx()))
                        drawCircle(color = accentColor, radius = size.minDimension / 6f)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF151D2A), CircleShape)
                    )
                }

                // Media Title Metadata
                Column {
                    Text(
                        text = if (isPlaying) "Streaming Track..." else "Media Paused",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "System Deck Output",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Action Deck buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                    modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = { 
                        triggerMediaKey(context, if (isPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier.size(44.dp).background(accentColor, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle",
                        tint = if (accentColor == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { triggerMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                    modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.LightGray, modifier = Modifier.size(16.dp))
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
        1 -> "SYSTEM ALARM"
        2 -> "LOCAL AGENDA"
        3 -> "SYSTEM MUSIC"
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
