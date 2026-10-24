package com.example.ui.standby

import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
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
    val events by viewModel.events.collectAsState()

    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage2.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

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

            if (!hasCalendarPermission) {
                EmptyCalendarView(accentColor, customFont, calendarPermissionState)
            } else {
                SplitMonthThemeContent(
                    viewModel = viewModel,
                    accentColor = accentColor,
                    customFont = customFont,
                    events = events
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SplitMonthThemeContent(
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

    val todayCal = remember { Calendar.getInstance() }
    val dailyEvents = events

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompactHeight = maxHeight < 390.dp
        val isCompactWidth = maxWidth < 660.dp
        val outerHPad = if (isCompactWidth) 14.dp else 24.dp
        val outerVPad = if (isCompactHeight) 10.dp else 20.dp
        val paneSpacing = if (isCompactWidth) 12.dp else 24.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = outerHPad, vertical = outerVPad),
            horizontalArrangement = Arrangement.spacedBy(paneSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT PANE: Material 3 Elevated Hero Date Card
            ElevatedCard(
                modifier = Modifier
                    .weight(if (isCompactWidth) 1f else 1.15f)
                    .fillMaxHeight(),
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
                                colors = listOf(accentColor.copy(alpha = 0.1f), Color.Transparent)
                            )
                        )
                        .padding(if (isCompactHeight) 12.dp else 24.dp),
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

                    val heroFontSize = (minOf(maxWidth.value * 0.56f, (maxHeight.value - 72f).coerceAtLeast(60f) * 0.78f))
                        .coerceIn(56f, 152f)

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
                                style = if (isCompactHeight) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                letterSpacing = 1.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(
                                    horizontal = if (isCompactHeight) 14.dp else 18.dp,
                                    vertical = if (isCompactHeight) 4.dp else 6.dp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isCompactHeight) 2.dp else 6.dp))
                        Text(
                            text = numLabel,
                            color = colorScheme.onSurface,
                            fontSize = heroFontSize.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = customFont,
                            maxLines = 1,
                            softWrap = false,
                            lineHeight = (heroFontSize * 0.95f).sp
                        )
                        Spacer(modifier = Modifier.height(if (isCompactHeight) 2.dp else 6.dp))
                        Text(
                            text = monthMiniLabel,
                            color = accentColor,
                            style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // RIGHT PANE: Material 3 Daily/Weekly/Monthly Agenda Container
            ElevatedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isCompactHeight || isCompactWidth) 12.dp else 18.dp)
                ) {
                    // Material 3 SegmentedButton Row for Daily / Weekly / Monthly
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 8.dp else 14.dp)
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
                                    style = if (isCompactWidth) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    if (dailyEvents.isEmpty() && filterVal != 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(if (isCompactHeight) 48.dp else 64.dp),
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.EventAvailable,
                                        contentDescription = null,
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(if (isCompactHeight) 24.dp else 30.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))
                            Text(
                                text = "No Scheduled Events",
                                color = colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Your calendar is clear for this period.",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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

                                val eventsInHour = dailyEvents.filter { isEventInHour(it, hour) }
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
                                    String.format("%02d:00", hour)
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
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = hourLabel,
                                            color = if (eventsInHour.isNotEmpty() || isCurrentHour) accentColor else colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            modifier = Modifier.width(if (isCompactWidth) 56.dp else 64.dp)
                                        )

                                        VerticalDivider(
                                            modifier = Modifier
                                                .height(26.dp)
                                                .padding(horizontal = 8.dp),
                                            color = colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )

                                        if (eventsInHour.isEmpty()) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                if (isCurrentHour) {
                                                    CurrentTimeIndicator(use24Hour = use24Hour)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                }
                                                Text(
                                                    text = "Available",
                                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = customFont,
                                                    maxLines = 1
                                                )
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
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                haptics.tick()
                                                                openEventInCalendar(context, event.id)
                                                            }
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = event.title,
                                                                color = colorScheme.onSurface,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = customFont,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = timePeriod,
                                                                    color = accentColor,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    maxLines = 1
                                                                )
                                                                if (!event.eventLocation.isNullOrBlank()) {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Place,
                                                                        contentDescription = null,
                                                                        tint = colorScheme.onSurfaceVariant,
                                                                        modifier = Modifier.size(12.dp)
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
                            items(dailyEvents, key = { "${it.id}_${it.dtStart}" }) { event ->
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

                                OutlinedCard(
                                    onClick = {
                                        haptics.tick()
                                        openEventInCalendar(context, event.id)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = colorScheme.surfaceContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = event.title,
                                            color = colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = customFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CalendarMonth,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = dateLabel,
                                                    color = colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Schedule,
                                                    contentDescription = null,
                                                    tint = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = timeLabel,
                                                    color = colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (!event.eventLocation.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
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
fun MonthlyGrid(
    startOnMonday: Boolean,
    highlightDay: Int,
    showEventDots: Boolean,
    events: List<LocalCalendarEvent>,
    accentColor: Color,
    customFont: FontFamily,
    monthLabel: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val daysText = if (startOnMonday) {
        listOf("M", "T", "W", "T", "F", "S", "S")
    } else {
        listOf("S", "M", "T", "W", "T", "F", "S")
    }

    val tempCal = remember { Calendar.getInstance() }
    val maxDays = remember { tempCal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val firstDayOfWeekIndex = remember {
        val calcCal = Calendar.getInstance()
        calcCal.set(Calendar.DAY_OF_MONTH, 1)
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
        val isCompactGrid = maxHeight < 235.dp
        val headerReservedDp = if (isCompactGrid) 36.dp else 46.dp
        val availableRowsHeight = (maxHeight - headerReservedDp).coerceAtLeast(100.dp)
        val rowCellHeight = (availableRowsHeight / activeRows).coerceIn(18.dp, 32.dp)
        val circleDiameter = (rowCellHeight - 2.dp).coerceIn(16.dp, 28.dp)
        val cellFontSize = (circleDiameter.value * 0.42f).coerceIn(9f, 12f).sp

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = monthLabel.uppercase(),
                color = colorScheme.onSurface,
                style = if (isCompactGrid) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = if (isCompactGrid) 4.dp else 8.dp)
            )

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
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompactGrid) 3.dp else 6.dp))

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
                                    val hasEventThisDay = remember(dayNum, events) {
                                        val dayCal = Calendar.getInstance()
                                        events.any { event ->
                                            dayCal.timeInMillis = event.dtStart
                                            dayCal.get(Calendar.YEAR) == tempCal.get(Calendar.YEAR) &&
                                                dayCal.get(Calendar.MONTH) == tempCal.get(Calendar.MONTH) &&
                                                dayCal.get(Calendar.DAY_OF_MONTH) == dayNum
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = if (isToday) {
                                            Modifier
                                                .size(circleDiameter)
                                                .clip(CircleShape)
                                                .background(accentColor)
                                        } else {
                                            Modifier.size(circleDiameter)
                                        }
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
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                                        )

                                        if (showEventDots && hasEventThisDay && !isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(3.5.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor)
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EmptyCalendarView(
    accentColor: Color,
    customFont: FontFamily,
    calendarPermissionState: com.google.accompanist.permissions.PermissionState
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp),
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
                        vertical = if (isCompactHeight) 18.dp else 32.dp
                    ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(if (isCompactHeight) 52.dp else 68.dp),
                    shape = CircleShape,
                    color = colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = "Calendar Permission Needed",
                            tint = colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(if (isCompactHeight) 26.dp else 32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))
                Text(
                    text = "Connect Device Calendar",
                    color = colorScheme.onSurface,
                    style = if (isCompactHeight) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Authorize calendar read access to display your daily schedule, meetings, and monthly agenda directly in Standby.",
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    fontFamily = customFont
                )
                Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 20.dp))
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
