package com.example.ui.standby

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val events by viewModel.events.collectAsState()
    
    // Core customizers from global Standby HUD
    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage2.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    // Calendar native access setup
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
    }

    LaunchedEffect(Unit) {
        viewModel.loadCalendarSources(context)
    }

    val bgUri by standbyViewModel.getBgUriFlow(2).collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Box(modifier = Modifier.fillMaxSize()) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplitMonthThemeContent(
    viewModel: CalendarViewModel,
    accentColor: Color,
    customFont: FontFamily,
    events: List<LocalCalendarEvent>
) {
    val startOnMon by viewModel.startWeekOnMonday.collectAsState()
    val showDots by viewModel.showEventDots.collectAsState()
    val leftStyle by viewModel.leftThemeStyle.collectAsState()
    val filterVal by viewModel.calendarFilter.collectAsState()

    // Get current calendar info
    val todayCal = remember { Calendar.getInstance() }
    val currentDay = todayCal.get(Calendar.DAY_OF_MONTH)
    val currentMonthLabel = remember {
        val monthSdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthSdf.format(todayCal.time).uppercase()
    }

    // Use current combined category events
    val dailyEvents = events

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // True AMOLED Black
    ) {
        // Glowing orbital node ambient light
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.CenterStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = 0.05f), Color.Transparent)
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // LEFT PANE: CALENDAR MONTHLY OR SPECIAL DATE METRICS
            Card(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // High Contrast Date display - optimized for MAXIMUM visibility from far
                    val dateLabel = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(todayCal.time).uppercase() }
                    val numLabel = remember { SimpleDateFormat("d", Locale.getDefault()).format(todayCal.time) }
                    val monthMiniLabel = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(todayCal.time).uppercase() }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = dateLabel,
                            color = accentColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = customFont,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = numLabel,
                            color = Color.White,
                            fontSize = 200.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = customFont,
                            lineHeight = 170.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = monthMiniLabel,
                            color = Color.LightGray,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            // RIGHT PANE: DAILY EVENTS AGENDA
            Card(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Side-by-side options between day/week/month (not toggle) direct in the UI header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Daily" to 0, "Weekly" to 1, "Monthly" to 2).forEach { (label, filterIndex) ->
                            val isSelected = filterVal == filterIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setCalendarFilter(filterIndex) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label.uppercase(),
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = customFont
                                )
                            }
                        }
                    }

                    // Event rendering depending on category selection
                    if (dailyEvents.isEmpty() && filterVal != 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = Color.DarkGray,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Calendar Cleared",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont
                            )
                            Text(
                                text = "No schedule conflict found on local accounts.",
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp)
                            )
                        }
                    } else if (filterVal == 0) {
                        // "Daily" scroll list spanning 0000 to 2400 Hours slices!
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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(24) { hour ->
                                val hourLabel = String.format("%02d00", hour)
                                val eventsInHour = dailyEvents.filter { isEventInHour(it, hour) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (eventsInHour.isNotEmpty()) accentColor.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.015f))
                                        .border(
                                            width = 0.5.dp, 
                                            color = if (eventsInHour.isNotEmpty()) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f), 
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Hour Label
                                    Column(
                                        modifier = Modifier.width(52.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = hourLabel,
                                            color = if (eventsInHour.isNotEmpty()) accentColor else Color.Gray,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Divider line
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(if (eventsInHour.isNotEmpty()) accentColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                                    )
                                    
                                    Spacer(modifier = Modifier.width(10.dp))
                                    
                                    if (eventsInHour.isEmpty()) {
                                        Text(
                                            text = "Free Slot",
                                            color = Color.Gray.copy(alpha = 0.35f),
                                            fontSize = 10.sp,
                                            fontFamily = customFont
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            eventsInHour.forEach { event ->
                                                val startFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                                val endFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                                                val timePeriod = if (event.allDay) "All Day" else {
                                                    "${startFormat.format(Date(event.dtStart))} - ${endFormat.format(Date(event.dtEnd))}"
                                                }
                                                
                                                Column {
                                                    Text(
                                                        text = event.title,
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = customFont,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = timePeriod,
                                                        color = accentColor.copy(alpha = 0.8f),
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            // Add final 2400 slice representing end of day
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.015f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.width(52.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "2400",
                                            color = Color.Gray,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.08f))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "End of Day",
                                        color = Color.Gray.copy(alpha = 0.35f),
                                        fontSize = 10.sp,
                                        fontFamily = customFont
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dailyEvents, key = { "${it.id}_${it.dtStart}" }) { event ->
                                val startFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                                val endFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
                                val timeLabel = if (event.allDay) "All Day" else {
                                    "${startFormat.format(Date(event.dtStart))} - ${endFormat.format(Date(event.dtEnd))}"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.03f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = event.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = customFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = timeLabel,
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                    if (!event.eventLocation.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = accentColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = event.eventLocation,
                                                color = Color.Gray,
                                                fontSize = 9.sp,
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = monthLabel,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
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
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val cellsPerRow = 7
        
        for (row in 0 until 6) {
            val startCell = row * cellsPerRow
            var hasActiveDaysInRow = false
            
            for (c in 0 until cellsPerRow) {
                val currentCellIndex = startCell + c
                val cellDayNum = currentCellIndex - offsetDays + 1
                if (cellDayNum in 1..maxDays) {
                    hasActiveDaysInRow = true
                }
            }

            if (hasActiveDaysInRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp), // increased to avoid overlap
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until cellsPerRow) {
                        val cellIdx = startCell + col
                        val dayNum = cellIdx - offsetDays + 1

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp), // increased cell height for comfortable layout without overlaps
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..maxDays) {
                                val isToday = dayNum == highlightDay
                                
                                val cellBorderModifier = if (isToday) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .size(28.dp)
                                } else {
                                    Modifier.size(28.dp)
                                }

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
                                    modifier = cellBorderModifier
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday) (if (accentColor == Color.White) Color.Black else Color.White) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold
                                    )
                                    
                                    if (showEventDots && hasEventThisDay && !isToday) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 1.dp)
                                                .size(3.dp)
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EmptyCalendarView(
    accentColor: Color,
    customFont: FontFamily,
    calendarPermissionState: com.google.accompanist.permissions.PermissionState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CalendarToday,
            contentDescription = "Permission Needed",
            tint = accentColor.copy(alpha = 0.3f),
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calendar Permission Required",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = customFont
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This screen displays your device and personal calendar schedules automatically. Please authorize access to keep calendars synced.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            fontFamily = customFont,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { calendarPermissionState.launchPermissionRequest() },
            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
        ) {
            Text(
                text = "Authorize Calendar Hub",
                fontFamily = customFont,
                fontWeight = FontWeight.Bold,
                color = if (accentColor == Color.White) Color.Black else Color.White,
                fontSize = 12.sp
            )
        }
    }
}
