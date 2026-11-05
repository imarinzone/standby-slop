package com.example.ui.standby

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

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

            // Full-Screen Edge-to-Edge Split Row: each Duo pane shows the actual main screens scaled for the half-screen aspect ratio
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

@Composable
fun DuoPaneTimeZonePickerDialog(
    paneIndex: Int,
    viewModel: StandbyViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isLeft = paneIndex == 1
    val activeTz by (if (isLeft) viewModel.duoLeftTimeZoneId else viewModel.duoRightTimeZoneId).collectAsState()
    val activeLoc by (if (isLeft) viewModel.duoLeftLocationName else viewModel.duoRightLocationName).collectAsState()
    val resolved = remember(activeTz, activeLoc) { viewModel.resolvePaneLocationInfo(paneIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLeft) "Left Duo Pane Location & Time Zone" else "Right Duo Pane Location & Time Zone",
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
                    text = "Active: ${resolved.regionName} (${resolved.airportCode}) • ${resolved.standardTimeShort} (${resolved.utcOffsetLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                SearchableLocationTimeZoneField(
                    viewModel = viewModel,
                    paneIndex = paneIndex,
                    activeSummary = "${resolved.regionName} (${resolved.airportCode}) • ${resolved.standardTimeShort}",
                    label = "Search city, airport, or time zone...",
                    testTag = if (isLeft) "duo_left_dialog_search" else "duo_right_dialog_search",
                    onSelectionApplied = onDismiss
                )
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
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Scales full-screen main utility pages (`AlarmPage`, `CalendarPage`, `MediaPage`, `TimerPage`)
 * proportionally so the exact main screens fit cleanly inside the smaller Duo half-pane aspect ratio.
 */
@Composable
private fun DuoAspectScaledScreen(
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val virtualW = 680.dp
        val virtualH = 420.dp
        val scaleX = (maxWidth.value / virtualW.value).coerceIn(0.42f, 1.0f)
        val scaleY = (maxHeight.value / virtualH.value).coerceIn(0.42f, 1.0f)
        val uniformScale = minOf(scaleX, scaleY)

        Box(
            modifier = Modifier
                .requiredSize(virtualW, virtualH)
                .graphicsLayer {
                    this.scaleX = uniformScale
                    this.scaleY = uniformScale
                }
        ) {
            content()
        }
    }
}

/**
 * Renders the actual main screens we have already built (indices 0..12 = the 13 main Clock Faces;
 * indices 13..16 = AlarmPage, CalendarPage, MediaPage, TimerPage/Tools) adapted to the Duo pane aspect ratio.
 */
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
        // 0..12: The 13 Main Clock Faces (adapted for Duo half-screen aspect ratio)
        0 -> DigitalMinimal(zonedTime, accentColor, customFont, 0.85f, animsEnabled, use24Hour, showAmPm, showSeconds, glowVal, outlineVal, null)
        1 -> AnalogClassic(zonedTime, accentColor, 0.90f, animsEnabled, true)
        2 -> RetroFlip(
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
        3 -> BinaryStyle(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        4 -> ModernBoldTextClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        5 -> ContrastingSplitClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        6 -> AnalogDashboard(zonedTime, accentColor, customFont, 0.85f, animsEnabled, true)
        7 -> BubblePastelClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        8 -> AmbientGradientClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
        9 -> NixieTubeClock(zonedTime, accentColor, customFont, 0.80f, use24Hour, showAmPm, showSeconds)
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
        11 -> StandbySageComplicationClockFace(
            calendar = zonedTime,
            accentColor = accentColor,
            customFont = customFont,
            scale = 1.0f,
            use24Hour = use24Hour,
            weatherTempCelsius = weather?.temperature_2m,
            nextAlarmLabel = nextAlarm,
            isDuoCompact = true
        )
        12 -> OverlappingFloatClockFace(zonedTime, accentColor, scale = 1.0f, use24Hour = use24Hour, isDuoCompact = true)

        // 13..16: The 4 Main Utility Screens (Alarm, Calendar, Music, Tools) scaled for Duo aspect ratio
        13 -> DuoAspectScaledScreen { AlarmPage(viewModel = viewModel) }
        14 -> DuoAspectScaledScreen { CalendarPage(viewModel = calendarViewModel, standbyViewModel = viewModel) }
        15 -> DuoAspectScaledScreen { MediaPage(standbyViewModel = viewModel) }
        16 -> DuoAspectScaledScreen { TimerPage(viewModel = viewModel) }
    }
}
