package com.example.ui.standby

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.roundToInt

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

    val selectedLeft by viewModel.duoLeftWidgetIndex.collectAsState()
    val selectedRight by viewModel.duoRightWidgetIndex.collectAsState()
    val totalDuoWidgets = viewModel.duoWidgetTitles.size

    val leftPagerState = rememberPagerState(
        initialPage = selectedLeft.coerceIn(0, totalDuoWidgets - 1),
        pageCount = { totalDuoWidgets }
    )
    val rightPagerState = rememberPagerState(
        initialPage = selectedRight.coerceIn(0, totalDuoWidgets - 1),
        pageCount = { totalDuoWidgets }
    )

    var hasInitLeft by remember { mutableStateOf(false) }
    LaunchedEffect(leftPagerState.currentPage) {
        if (!hasInitLeft) {
            hasInitLeft = true
        } else {
            haptics.tick()
            viewModel.setDuoLeftWidgetIndex(leftPagerState.currentPage)
        }
    }

    LaunchedEffect(selectedLeft) {
        val target = selectedLeft.coerceIn(0, totalDuoWidgets - 1)
        if (leftPagerState.currentPage != target) {
            leftPagerState.scrollToPage(target)
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

    LaunchedEffect(selectedRight) {
        val target = selectedRight.coerceIn(0, totalDuoWidgets - 1)
        if (rightPagerState.currentPage != target) {
            rightPagerState.scrollToPage(target)
        }
    }

    val bgUri by viewModel.getBgUriFlow(5).collectAsState()
    val locMode by viewModel.locationMode.collectAsState()
    val customLoc by viewModel.customLocationName.collectAsState()
    val clockTz by viewModel.clockTimeZoneId.collectAsState()
    val leftTz by viewModel.duoLeftTimeZoneId.collectAsState()
    val rightTz by viewModel.duoRightTimeZoneId.collectAsState()
    val leftLoc by viewModel.duoLeftLocationName.collectAsState()
    val rightLoc by viewModel.duoRightLocationName.collectAsState()
    val gpsLoc by viewModel.gpsLocationName.collectAsState()
    val gpsLat by viewModel.gpsLatitude.collectAsState()
    val gpsLon by viewModel.gpsLongitude.collectAsState()

    val leftRegionInfo = remember(locMode, customLoc, clockTz, leftTz, leftLoc, gpsLoc, gpsLat, gpsLon) {
        viewModel.resolvePaneLocationInfo(1)
    }
    val rightRegionInfo = remember(locMode, customLoc, clockTz, rightTz, rightLoc, gpsLoc, gpsLat, gpsLon) {
        viewModel.resolvePaneLocationInfo(2)
    }

    var activeTimezonePickerPane by remember { mutableIntStateOf(0) } // 0 = hidden, 1 = left, 2 = right

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bgUri != null) {
                coil.compose.AsyncImage(
                    model = bgUri,
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)))
            }

            // Full-Screen Edge-to-Edge Split Row so every screen in Duo takes up the whole screen
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // LEFT FULL-SCREEN HALF PANEL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    VerticalPager(
                        state = leftPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        WidgetContainer(pageIndex = page) {
                            RenderDuoWidget(
                                pageIndex = page,
                                paneIndex = 1,
                                viewModel = viewModel,
                                calendarViewModel = calendarViewModel,
                                weatherViewModel = weatherViewModel
                            )
                        }
                    }

                    // Left Pane TimeZone & Location Quick-Switch Chip
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 8.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                activeTimezonePickerPane = 1
                            },
                        shape = CircleShape,
                        color = Color(0xFF14161C).copy(alpha = 0.78f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Left Pane TimeZone",
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${leftRegionInfo.airportCode} • ${leftRegionInfo.standardTimeShort}",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Left Pager Compact Vertical Indicator
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 4.dp),
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (i in 0 until totalDuoWidgets) {
                                val isSelected = i == leftPagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = if (isSelected) 8.dp else 3.dp)
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

                // RIGHT FULL-SCREEN HALF PANEL
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    VerticalPager(
                        state = rightPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        WidgetContainer(pageIndex = page) {
                            RenderDuoWidget(
                                pageIndex = page,
                                paneIndex = 2,
                                viewModel = viewModel,
                                calendarViewModel = calendarViewModel,
                                weatherViewModel = weatherViewModel
                            )
                        }
                    }

                    // Right Pane TimeZone & Location Quick-Switch Chip
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 10.dp, top = 8.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptics.tick()
                                activeTimezonePickerPane = 2
                            },
                        shape = CircleShape,
                        color = Color(0xFF14161C).copy(alpha = 0.78f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Right Pane TimeZone",
                                tint = Color(0xFF81C995),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${rightRegionInfo.airportCode} • ${rightRegionInfo.standardTimeShort}",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Right Pager Compact Vertical Indicator
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp),
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (i in 0 until totalDuoWidgets) {
                                val isSelected = i == rightPagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(width = 4.dp, height = if (isSelected) 8.dp else 3.dp)
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

            if (activeTimezonePickerPane != 0) {
                DuoPaneTimeZonePickerDialog(
                    paneIndex = activeTimezonePickerPane,
                    viewModel = viewModel,
                    onDismiss = { activeTimezonePickerPane = 0 }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuoPaneTimeZonePickerDialog(
    paneIndex: Int,
    viewModel: StandbyViewModel,
    onDismiss: () -> Unit
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val isLeft = paneIndex == 1
    val activeTz by (if (isLeft) viewModel.duoLeftTimeZoneId else viewModel.duoRightTimeZoneId).collectAsState()
    val activeLoc by (if (isLeft) viewModel.duoLeftLocationName else viewModel.duoRightLocationName).collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLeft) "Left Duo Pane Time Zone & Location" else "Right Duo Pane Time Zone & Location",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select a Time Zone or City for the ${if (isLeft) "Left" else "Right"} Duo screen:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    viewModel.selectableTimeZones.forEach { (tzId, label) ->
                        val isSelected = activeTz.equals(tzId, ignoreCase = true) && activeLoc.isBlank()
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.tick()
                                if (isLeft) {
                                    viewModel.setDuoLeftLocationName("")
                                    viewModel.setDuoLeftTimeZoneId(tzId)
                                } else {
                                    viewModel.setDuoRightLocationName("")
                                    viewModel.setDuoRightTimeZoneId(tzId)
                                }
                                onDismiss()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    text = "Or Select Airport Terminal / City Region:",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    viewModel.selectableLocations.forEach { preset ->
                        val isSelected = activeLoc.equals(preset, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics.tick()
                                if (isLeft) {
                                    viewModel.setDuoLeftLocationName(preset)
                                } else {
                                    viewModel.setDuoRightLocationName(preset)
                                }
                                onDismiss()
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun WidgetContainer(
    pageIndex: Int,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun RenderDuoWidget(
    pageIndex: Int,
    paneIndex: Int = 1,
    viewModel: StandbyViewModel,
    calendarViewModel: CalendarViewModel,
    weatherViewModel: WeatherViewModel
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val selectedColorIdx by viewModel.colorPage5.collectAsState()
    val selectedFontIdx by viewModel.fontPage5.collectAsState()
    val accentColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color(0xFF81C995)
    val customFont = viewModel.fonts.getOrNull(selectedFontIdx)?.first ?: FontFamily.Default
    val use24Hour by viewModel.use24HourFormat.collectAsState()
    val showAmPm by viewModel.showAmPm.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val animsEnabled by viewModel.animPage5.collectAsState()
    val glowVal by viewModel.textGlowPage5.collectAsState()
    val outlineVal by viewModel.textOutlineOnlyPage5.collectAsState()
    val weather by weatherViewModel.weather.collectAsState()
    val nextAlarm by viewModel.nextSystemAlarm.collectAsState()

    val locMode by viewModel.locationMode.collectAsState()
    val customLoc by viewModel.customLocationName.collectAsState()
    val clockTz by viewModel.clockTimeZoneId.collectAsState()
    val leftTz by viewModel.duoLeftTimeZoneId.collectAsState()
    val rightTz by viewModel.duoRightTimeZoneId.collectAsState()
    val leftLoc by viewModel.duoLeftLocationName.collectAsState()
    val rightLoc by viewModel.duoRightLocationName.collectAsState()
    val gpsLoc by viewModel.gpsLocationName.collectAsState()
    val gpsLat by viewModel.gpsLatitude.collectAsState()
    val gpsLon by viewModel.gpsLongitude.collectAsState()

    val paneRegionInfo = remember(
        paneIndex, locMode, customLoc, clockTz,
        leftTz, rightTz, leftLoc, rightLoc,
        gpsLoc, gpsLat, gpsLon
    ) {
        viewModel.resolvePaneLocationInfo(paneIndex)
    }

    val zonedTime = remember(currentTime, paneRegionInfo.timeZoneId) {
        GoogleDeviceAndHomeMonitor.calendarForTimeZone(currentTime, paneRegionInfo.timeZoneId)
    }

    LaunchedEffect(pageIndex) {
        while (true) {
            delay(1000)
            currentTime = Calendar.getInstance()
        }
    }

    val total = viewModel.duoWidgetTitles.size.coerceAtLeast(1)
    when (pageIndex.mod(total)) {
        0 -> DuoClockWidget(viewModel, weatherViewModel, paneIndex = paneIndex, externalCalendar = zonedTime, regionInfo = paneRegionInfo)
        1 -> DuoAlarmWidget(viewModel)
        2 -> DuoCalendarWidget(calendarViewModel, viewModel)
        3 -> DuoMusicWidget(viewModel)
        4 -> DuoTimerWidget(viewModel)
        5 -> RetroFlip(
            calendar = zonedTime,
            color = accentColor,
            fontFamily = customFont,
            scale = 1.0f,
            use24Hour = use24Hour,
            showAmPm = showAmPm,
            showSeconds = showSeconds,
            isDuoCompact = true,
            standbyViewModel = viewModel,
            paneIndex = paneIndex
        )
        6 -> SquircleTickClockWidget(zonedTime, accentColor, scale = 1.0f, use24Hour = use24Hour)
        7 -> StandbyNumberedAnalogWidget(zonedTime, accentColor, scale = 1.0f)
        8 -> OverlappingFloatClockFace(zonedTime, accentColor, scale = 1.0f, use24Hour = use24Hour, isDuoCompact = true)
        9 -> StandbySageComplicationClockFace(
            calendar = zonedTime,
            accentColor = accentColor,
            customFont = customFont,
            scale = 1.0f,
            use24Hour = use24Hour,
            weatherTempCelsius = weather?.temperature_2m,
            nextAlarmLabel = nextAlarm,
            isDuoCompact = true
        )
        10 -> WorldTimeZoneMapClockFace(
            calendar = zonedTime,
            accentColor = accentColor,
            customFont = customFont,
            scale = 1.0f,
            use24Hour = use24Hour,
            nextAlarmLabel = nextAlarm,
            isDuoCompact = true,
            standbyViewModel = viewModel,
            paneIndex = paneIndex
        )
        11 -> BatteryChargingRingsWidget()
        12 -> SmartHomeControlGridWidget(standbyViewModel = viewModel)
        13 -> BigDateHeroWidget(zonedTime, accentColor)
        14 -> StandbyRedCalendarGridWidget(zonedTime, accentColor)
        15 -> StandbyNowPlayingCardWidget(viewModel)
        16 -> StandbyIncomingCallCardWidget()
        17 -> DigitalMinimal(zonedTime, accentColor, customFont, 0.85f, animsEnabled, use24Hour, showAmPm, showSeconds, glowVal, outlineVal, null)
        18 -> AnalogClassic(zonedTime, accentColor, 0.90f, animsEnabled, true)
        19 -> BinaryStyle(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        20 -> ModernBoldTextClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        21 -> ContrastingSplitClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        22 -> AnalogDashboard(zonedTime, accentColor, customFont, 0.85f, animsEnabled, true)
        23 -> BubblePastelClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        24 -> AmbientGradientClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        25 -> NixieTubeClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
    }
}

@Composable
fun DuoClockWidget(
    viewModel: StandbyViewModel,
    weatherViewModel: WeatherViewModel,
    paneIndex: Int = 1,
    externalCalendar: Calendar? = null,
    regionInfo: UserTimeZoneRegionInfo? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    var internalTime by remember { mutableStateOf(Calendar.getInstance()) }
    val resolvedRegion = regionInfo ?: viewModel.resolvePaneLocationInfo(paneIndex)
    val currentTime = externalCalendar ?: GoogleDeviceAndHomeMonitor.calendarForTimeZone(internalTime, resolvedRegion.timeZoneId)
    val activeTz = currentTime.timeZone ?: TimeZone.getDefault()

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
            internalTime = now
            if (now.get(Calendar.SECOND) % 15 == 0) {
                viewModel.updateNextAlarm(now)
            }
            delay(1000)
        }
    }

    val timeFormat = remember(use24Hour, activeTz.id) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "hh:mm", Locale.getDefault()).apply {
            timeZone = activeTz
        }
    }
    val secFormat = remember(activeTz.id) {
        SimpleDateFormat("ss", Locale.getDefault()).apply {
            timeZone = activeTz
        }
    }
    val dateFormat = remember(activeTz.id) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
            timeZone = activeTz
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = animatedBrightness }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val mainFontSize = (minOf(maxWidth.value * 0.28f, maxHeight.value * 0.36f)).coerceIn(42f, 96f).sp
        val secFontSize = (mainFontSize.value * 0.38f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${resolvedRegion.regionName.uppercase(Locale.getDefault())} • ${resolvedRegion.standardTimeShort} (${resolvedRegion.utcOffsetLabel})",
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = timeFormat.format(currentTime.time),
                    color = Color.White,
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
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = CircleShape,
                color = colorScheme.secondaryContainer
            ) {
                Text(
                    text = dateFormat.format(currentTime.time).uppercase(Locale.getDefault()),
                    color = colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (showWeatherVal && weather != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${weather?.temperature_2m}°C • ${getWeatherCodeDesc(weather?.weather_code ?: 0)}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val iconCircleSize = (minOf(maxWidth, maxHeight) * 0.30f).coerceIn(52.dp, 88.dp)
        val iconSize = iconCircleSize * 0.50f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.size(iconCircleSize),
                shape = CircleShape,
                color = if (hasAlarm) accentColor.copy(alpha = 0.22f) else colorScheme.surfaceContainerHigh
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = if (hasAlarm) accentColor else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "UPCOMING ALARM",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = nextSystemAlarm ?: "No Active Alarm",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
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
            .background(Color.Black)
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                .background(Color.Black)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Media Sync Permission",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                .background(Color.Black)
        ) {
            val isCompact = maxHeight < 210.dp || maxWidth < 210.dp
            val sideBtnSize = if (isCompact) 44.dp else 54.dp
            val mainBtnSize = if (isCompact) 56.dp else 68.dp

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
                            Brush.radialGradient(listOf(accentColor.copy(alpha = 0.25f), Color.Black))
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = trackTitle ?: "No Track Playing",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = artistName ?: "System Media",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
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
                            modifier = Modifier.size(24.dp)
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
                        shape = RoundedCornerShape(18.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accentColor,
                            contentColor = if (accentColor == Color.White) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(30.dp)
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
                            modifier = Modifier.size(24.dp)
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        val dialSize = (minOf(maxWidth, maxHeight) * 0.62f).coerceIn(92.dp, 190.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.size(dialSize),
                contentAlignment = Alignment.Center
            ) {
                val totalSecs = (focusMinutes * 60).coerceAtLeast(1)
                val ratio = secondsLeft.toFloat() / totalSecs.toFloat()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF232528),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * ratio,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Text(
                    text = formatted,
                    color = Color.White,
                    fontSize = (dialSize.value * 0.24f).coerceIn(22f, 44f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.confirm()
                        isRunning = !isRunning
                    },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(18.dp)
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
