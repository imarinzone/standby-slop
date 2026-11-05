package com.example.ui.standby

import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarPage(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
    standbyViewModel: StandbyViewModel = viewModel()
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberStandbyHaptics()
    val coroutineScope = rememberCoroutineScope()
    val events by viewModel.events.collectAsState()
    val allDeviceEvents by viewModel.deviceEvents.collectAsState()

    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage2.collectAsState()
    val accentColor = standbyViewModel.colors.getOrNull(selectedColorIdx)?.first ?: colorScheme.primary
    val customFont = standbyViewModel.fonts.getOrNull(selectedFontIdx)?.first ?: FontFamily.Default

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
            viewModel.loadLocalEvents(context)
        }
    }

    LaunchedEffect(Unit) {
        hasCalendarPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCalendarPermission) {
            viewModel.loadLocalEvents(context)
        }
        viewModel.loadCalendarSources(context)
    }

    val bgUri by standbyViewModel.getBgUriFlow(2).collectAsState()
    val initialScreen = remember { viewModel.calendarScreenIndex.value.coerceIn(0, 2) }
    val verticalPagerState = rememberPagerState(
        initialPage = initialScreen,
        pageCount = { 3 }
    )

    var hasInitPager by remember { mutableStateOf(false) }
    LaunchedEffect(verticalPagerState.currentPage) {
        if (!hasInitPager) {
            hasInitPager = true
        } else {
            haptics.tick()
            viewModel.setCalendarScreenIndex(verticalPagerState.currentPage)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_multi_screen_page"),
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

            // Multi-Screen VerticalPager (same pattern as Clock screens):
            // 0 -> Hero Day Screen (moved from old left pane into its own screen)
            // 1 -> Month View Calendar Screen (highlights Today's date)
            // 2 -> Material 3 Calendar Events / Agenda Screen
            VerticalPager(
                state = verticalPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> CalendarHeroDayScreen(
                        accentColor = accentColor,
                        customFont = customFont
                    )
                    1 -> CalendarMonthGridScreen(
                        viewModel = viewModel,
                        accentColor = accentColor,
                        customFont = customFont,
                        events = allDeviceEvents
                    )
                    2 -> {
                        if (!hasCalendarPermission) {
                            EmptyCalendarView(
                                accentColor = accentColor,
                                customFont = customFont,
                                calendarPermissionState = calendarPermissionState
                            )
                        } else {
                            CalendarEventsAgendaScreen(
                                viewModel = viewModel,
                                accentColor = accentColor,
                                customFont = customFont,
                                events = events
                            )
                        }
                    }
                }
            }

            // Right-Edge Material 3 Vertical Screen Indicator (matches Clock screen vertical indicator)
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                color = colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                shape = CircleShape,
                tonalElevation = 4.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 0 until 3) {
                        val isSelected = i == verticalPagerState.currentPage
                        val height by animateDpAsState(targetValue = if (isSelected) 16.dp else 6.dp, label = "calDotHeight")
                        Box(
                            modifier = Modifier
                                .size(width = 6.dp, height = height)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) accentColor
                                    else colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                .clickable {
                                    haptics.tick()
                                    viewModel.setCalendarScreenIndex(i)
                                    coroutineScope.launch {
                                        verticalPagerState.animateScrollToPage(i)
                                    }
                                }
                                .testTag("calendar_mode_dot_$i")
                        )
                    }
                }
            }
        }
    }
}

/**
 * CALENDAR SCREEN 01: Full-Screen Hero Today Date Display
 * (Moved from the old left pane into its own dedicated screen inside the Calendar pager)
 */
@Composable
fun CalendarHeroDayScreen(
    accentColor: Color,
    customFont: FontFamily
) {
    val colorScheme = MaterialTheme.colorScheme
    val todayCal = remember { Calendar.getInstance() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 36.dp, top = 16.dp, bottom = 16.dp)
            .testTag("calendar_screen_day_hero"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 360.dp

        ElevatedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerLow
            )
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
                    .padding(if (isCompactHeight) 16.dp else 28.dp),
                contentAlignment = Alignment.Center
            ) {
                val dateLabel = remember {
                    SimpleDateFormat("EEEE", Locale.getDefault()).format(todayCal.time).uppercase()
                }
                val numLabel = remember {
                    SimpleDateFormat("d", Locale.getDefault()).format(todayCal.time)
                }
                val monthMiniLabel = remember {
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(todayCal.time).uppercase()
                }

                val heroFontSize = (minOf(maxWidth.value * 0.44f, (maxHeight.value - 80f).coerceAtLeast(70f) * 0.80f))
                    .coerceIn(68f, 178f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = dateLabel,
                            color = colorScheme.onSecondaryContainer,
                            style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            letterSpacing = 1.8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = if (isCompactHeight) 16.dp else 22.dp,
                                vertical = if (isCompactHeight) 5.dp else 8.dp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                    Text(
                        text = numLabel,
                        color = colorScheme.onSurface,
                        fontSize = heroFontSize.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = customFont,
                        maxLines = 1,
                        softWrap = false,
                        lineHeight = (heroFontSize * 0.94f).sp
                    )

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                    Text(
                        text = monthMiniLabel,
                        color = accentColor,
                        style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont,
                        letterSpacing = 1.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * CALENDAR SCREEN 02: Full-Screen Material 3 Month View Calendar
 * Highlights Today's Date prominently with Material 3 styling.
 */
@Composable
fun CalendarMonthGridScreen(
    viewModel: CalendarViewModel,
    accentColor: Color,
    customFont: FontFamily,
    events: List<LocalCalendarEvent>
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptics = rememberStandbyHaptics()
    val startOnMonday by viewModel.startWeekOnMonday.collectAsState()
    val showEventDots by viewModel.showEventDots.collectAsState()

    val todayCal = remember { Calendar.getInstance() }
    var monthOffset by remember { mutableIntStateOf(0) }

    val displayedMonthCal = remember(monthOffset) {
        (todayCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
        }
    }

    val isCurrentMonth = remember(displayedMonthCal) {
        displayedMonthCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            displayedMonthCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
    }
    val todayDayNum = remember { todayCal.get(Calendar.DAY_OF_MONTH) }
    val monthHeaderText = remember(displayedMonthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedMonthCal.time)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 36.dp, top = 16.dp, bottom = 16.dp)
            .testTag("calendar_screen_month_grid"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 360.dp

        ElevatedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isCompactHeight) 16.dp else 24.dp, vertical = if (isCompactHeight) 10.dp else 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Month Header Bar with Today badge & Prev/Next Month controls
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
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = monthHeaderText,
                            style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (monthOffset != 0) {
                            FilledTonalButton(
                                onClick = {
                                    haptics.tick()
                                    monthOffset = 0
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("calendar_today_reset_btn")
                            ) {
                                Text("Today", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                monthOffset -= 1
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("calendar_prev_month_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                monthOffset += 1
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("calendar_next_month_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

                // Full-width Material 3 Monthly Grid highlighting Today's Date
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MonthlyGrid(
                        displayedMonthCal = displayedMonthCal,
                        startOnMonday = startOnMonday,
                        highlightDay = if (isCurrentMonth) todayDayNum else -1,
                        showEventDots = showEventDots,
                        events = events,
                        accentColor = accentColor,
                        customFont = customFont
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyGrid(
    displayedMonthCal: Calendar = Calendar.getInstance(),
    startOnMonday: Boolean,
    highlightDay: Int,
    showEventDots: Boolean,
    events: List<LocalCalendarEvent>,
    accentColor: Color,
    customFont: FontFamily
) {
    val colorScheme = MaterialTheme.colorScheme
    val daysText = if (startOnMonday) {
        listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    } else {
        listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    }

    val maxDays = remember(displayedMonthCal) {
        displayedMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    val firstDayOfWeekIndex = remember(displayedMonthCal) {
        val calcCal = (displayedMonthCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        calcCal.get(Calendar.DAY_OF_WEEK)
    }

    val offsetDays = if (startOnMonday) {
        var offset = firstDayOfWeekIndex - 2
        if (offset < 0) offset += 7
        offset
    } else {
        firstDayOfWeekIndex - 1
    }

    val activeRows = remember(offsetDays, maxDays) {
        val cellsPerRow = 7
        var count = 0
        for (row in 0 until 6) {
            val startCell = row * cellsPerRow
            var hasActiveDays = false
            for (c in 0 until cellsPerRow) {
                val cellDayNum = startCell + c - offsetDays + 1
                if (cellDayNum in 1..maxDays) {
                    hasActiveDays = true
                }
            }
            if (hasActiveDays) count++
        }
        count.coerceAtLeast(4)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val isCompactGrid = maxHeight < 220.dp
        val headerReservedDp = if (isCompactGrid) 24.dp else 32.dp
        val availableRowsHeight = (maxHeight - headerReservedDp).coerceAtLeast(100.dp)
        val rowCellHeight = (availableRowsHeight / activeRows).coerceIn(22.dp, 46.dp)
        val pillHeight = (rowCellHeight - 4.dp).coerceIn(20.dp, 40.dp)
        val cellFontSize = (pillHeight.value * 0.44f).coerceIn(11f, 16f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Weekday Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysText.forEach { weekDay ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = weekDay,
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Month Day Rows
            val cellsPerRow = 7
            for (row in 0 until 6) {
                val startCell = row * cellsPerRow
                var hasActiveDaysInRow = false
                for (c in 0 until cellsPerRow) {
                    val cellDayNum = startCell + c - offsetDays + 1
                    if (cellDayNum in 1..maxDays) {
                        hasActiveDaysInRow = true
                    }
                }

                if (hasActiveDaysInRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in 0 until cellsPerRow) {
                            val cellIdx = startCell + col
                            val dayNum = cellIdx - offsetDays + 1

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(rowCellHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayNum in 1..maxDays) {
                                    val isToday = dayNum == highlightDay
                                    val hasEventThisDay = remember(dayNum, events, displayedMonthCal) {
                                        val dayCal = Calendar.getInstance()
                                        events.any { event ->
                                            dayCal.timeInMillis = event.dtStart
                                            dayCal.get(Calendar.YEAR) == displayedMonthCal.get(Calendar.YEAR) &&
                                                dayCal.get(Calendar.MONTH) == displayedMonthCal.get(Calendar.MONTH) &&
                                                dayCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = when {
                                            isToday -> accentColor
                                            hasEventThisDay && showEventDots -> colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                            else -> Color.Transparent
                                        },
                                        border = if (isToday) {
                                            BorderStroke(2.dp, colorScheme.onSurface.copy(alpha = 0.25f))
                                        } else null,
                                        modifier = Modifier
                                            .height(pillHeight)
                                            .widthIn(min = pillHeight * 1.25f)
                                            .then(
                                                if (isToday) Modifier.testTag("calendar_today_highlighted_cell")
                                                else Modifier
                                            )
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (isToday) {
                                                    if (accentColor == Color.White) Color.Black else Color.White
                                                } else {
                                                    colorScheme.onSurface
                                                },
                                                fontSize = cellFontSize,
                                                fontFamily = customFont,
                                                maxLines = 1,
                                                softWrap = false,
                                                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium
                                            )

                                            if (showEventDots && hasEventThisDay) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isToday) {
                                                                if (accentColor == Color.White) Color.Black else Color.White
                                                            } else {
                                                                accentColor
                                                            }
                                                        )
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
    }
}

/**
 * CALENDAR SCREEN 03: Dedicated Full-Screen Material 3 Calendar Events & Agenda Screen
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalendarEventsAgendaScreen(
    viewModel: CalendarViewModel,
    accentColor: Color,
    customFont: FontFamily,
    events: List<LocalCalendarEvent>
) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

    val filterVal by viewModel.calendarFilter.collectAsState()
    val use24Hour by viewModel.use24HourFormat.collectAsState()
    var showEmptyInfo by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 36.dp, top = 16.dp, bottom = 16.dp)
            .testTag("calendar_screen_events_agenda")
    ) {
        val isCompactHeight = maxHeight < 380.dp
        val isCompactWidth = maxWidth < 600.dp

        ElevatedCard(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompactHeight || isCompactWidth) 14.dp else 20.dp)
            ) {
                // Top Material 3 Header + SegmentedButton Row for Daily / Weekly / Monthly
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (isCompactHeight) 10.dp else 14.dp),
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
                                imageVector = Icons.Outlined.EventNote,
                                contentDescription = null,
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = "Events",
                            style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont
                        )
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        val filterOptions = listOf("Daily" to 0, "Weekly" to 1, "Monthly" to 2)
                        filterOptions.forEachIndexed { idx, (label, filterIndex) ->
                            SegmentedButton(
                                selected = filterVal == filterIndex,
                                onClick = {
                                    haptics.tick()
                                    viewModel.setCalendarFilter(filterIndex)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = filterOptions.size
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = customFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                if (events.isEmpty() && filterVal != 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(if (isCompactHeight) 48.dp else 60.dp),
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.EventAvailable,
                                    contentDescription = null,
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(if (isCompactHeight) 24.dp else 28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "No Scheduled Events",
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont
                            )
                            IconButton(
                                onClick = { showEmptyInfo = !showEmptyInfo },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "More Info",
                                    tint = if (showEmptyInfo) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        AnimatedVisibility(visible = showEmptyInfo) {
                            Text(
                                text = "Events added to your device calendar will appear here automatically.",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (filterVal == 0) {
                    val isEventInHour: (LocalCalendarEvent, Int) -> Boolean = { event, hr ->
                        if (event.allDay) {
                            true
                        } else {
                            val startCal = Calendar.getInstance().apply { timeInMillis = event.dtStart }
                            val endCal = Calendar.getInstance().apply { timeInMillis = event.dtEnd }
                            val startHour = startCal.get(Calendar.HOUR_OF_DAY)
                            val endHour = endCal.get(Calendar.HOUR_OF_DAY)
                            hr in startHour..endHour
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 8.dp)
                    ) {
                        items(24) { hour ->
                            val now = Calendar.getInstance()
                            val currentHour = now.get(Calendar.HOUR_OF_DAY)
                            val isPastHour = hour < currentHour
                            val isCurrentHour = hour == currentHour

                            val eventsInHour = events.filter { isEventInHour(it, hour) }
                            val itemAlpha = if (isPastHour) 0.55f else 1.0f

                            val containerColor = when {
                                isCurrentHour -> colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                eventsInHour.isNotEmpty() -> colorScheme.surfaceContainerHigh
                                else -> colorScheme.surfaceContainer
                            }
                            val borderColor = when {
                                isCurrentHour -> accentColor.copy(alpha = 0.65f)
                                eventsInHour.isNotEmpty() -> colorScheme.outlineVariant
                                else -> Color.Transparent
                            }

                            val hourLabel = if (use24Hour) {
                                String.format(Locale.getDefault(), "%02d:00", hour)
                            } else {
                                val ampm = if (hour >= 12) "PM" else "AM"
                                val displayH = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                                "$displayH $ampm"
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(itemAlpha),
                                shape = RoundedCornerShape(14.dp),
                                color = containerColor,
                                border = if (borderColor != Color.Transparent) {
                                    BorderStroke(1.dp, borderColor)
                                } else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = hourLabel,
                                        color = if (eventsInHour.isNotEmpty() || isCurrentHour) accentColor else colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        modifier = Modifier.width(64.dp)
                                    )

                                    VerticalDivider(
                                        modifier = Modifier
                                            .height(26.dp)
                                            .padding(horizontal = 10.dp),
                                        color = colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )

                                    if (eventsInHour.isEmpty()) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (isCurrentHour) {
                                                CurrentTimeIndicator(use24Hour = use24Hour)
                                            } else {
                                                HorizontalDivider(
                                                    color = colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isCurrentHour) {
                                                CurrentTimeIndicator(use24Hour = use24Hour)
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            eventsInHour.forEach { event ->
                                                val startFormat = remember(use24Hour) {
                                                    SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
                                                }
                                                val endFormat = remember(use24Hour) {
                                                    SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
                                                }
                                                val timePeriod = if (event.allDay) "All Day" else {
                                                    "${startFormat.format(Date(event.dtStart))} - ${endFormat.format(Date(event.dtEnd))}"
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            haptics.tick()
                                                            openEventInCalendar(context, event.id)
                                                        }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = event.title,
                                                            color = colorScheme.onPrimaryContainer,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = customFont,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            if (!event.eventLocation.isNullOrBlank()) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Place,
                                                                        contentDescription = null,
                                                                        tint = colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Text(
                                                                        text = event.eventLocation,
                                                                        color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = timePeriod,
                                                                color = colorScheme.onPrimaryContainer,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1
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
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(events, key = { "${it.id}_${it.dtStart}" }) { event ->
                            val startFormat = remember(use24Hour) {
                                SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
                            }
                            val endFormat = remember(use24Hour) {
                                SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
                            }
                            val timeLabel = if (event.allDay) "All Day" else {
                                "${startFormat.format(Date(event.dtStart))} - ${endFormat.format(Date(event.dtEnd))}"
                            }
                            val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
                            val dateLabel = dateFormat.format(Date(event.dtStart))

                            ElevatedCard(
                                onClick = {
                                    haptics.tick()
                                    openEventInCalendar(context, event.id)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = colorScheme.surfaceContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = event.title,
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = customFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!event.eventLocation.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Place,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = event.eventLocation,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colorScheme.secondaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CalendarMonth,
                                                    contentDescription = null,
                                                    tint = colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = dateLabel,
                                                    color = colorScheme.onSecondaryContainer,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colorScheme.surfaceContainerHighest
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Schedule,
                                                    contentDescription = null,
                                                    tint = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = timeLabel,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
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
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EmptyCalendarView(
    accentColor: Color,
    customFont: FontFamily,
    calendarPermissionState: com.google.accompanist.permissions.PermissionState
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    var showPermissionInfo by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 36.dp, top = 16.dp, bottom = 16.dp)
            .testTag("calendar_screen_events_agenda"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 370.dp
        ElevatedCard(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isCompactHeight) 24.dp else 32.dp,
                        vertical = if (isCompactHeight) 18.dp else 28.dp
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(if (isCompactHeight) 52.dp else 64.dp),
                    shape = CircleShape,
                    color = colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Calendar Permission Needed",
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(if (isCompactHeight) 26.dp else 30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Connect Calendar",
                        color = colorScheme.onSurface,
                        style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont
                    )
                    IconButton(
                        onClick = { showPermissionInfo = !showPermissionInfo },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "More Info",
                            tint = if (showPermissionInfo) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = showPermissionInfo) {
                    Text(
                        text = "Grant read access to display your daily schedule and events in Standby.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        fontFamily = customFont,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        haptics.confirm()
                        calendarPermissionState.launchPermissionRequest()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Authorize Calendar",
                        fontFamily = customFont,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun openEventInCalendar(context: android.content.Context, eventId: Long) {
    try {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val builder = CalendarContract.CONTENT_URI.buildUpon()
                builder.appendPath("time")
                ContentUris.appendId(builder, System.currentTimeMillis())
                data = builder.build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            android.util.Log.e("CalendarPage", "Could not open calendar", ex)
        }
    }
}

@Composable
fun CurrentTimeIndicator(use24Hour: Boolean, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val liveTime = remember(use24Hour) {
            val formatPattern = if (use24Hour) "HH:mm" else "h:mm a"
            SimpleDateFormat(formatPattern, Locale.getDefault())
        }
        val currentTimeStr = liveTime.format(Date())

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = colorScheme.errorContainer
        ) {
            Text(
                text = "NOW • $currentTimeStr",
                color = colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.5.dp,
            color = colorScheme.error.copy(alpha = 0.6f)
        )
    }
}
