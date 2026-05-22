package com.example.ui.standby

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarPage(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
    standbyViewModel: StandbyViewModel = viewModel()
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Core customizers from global Standby HUD
    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage2.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    // Local filters and toggles
    val showDailyFilter by viewModel.showDaily.collectAsState()
    val showWeeklyFilter by viewModel.showWeekly.collectAsState()
    val showMonthlyFilter by viewModel.showMonthly.collectAsState()
    val showDemoEventsVal by viewModel.showDemoEvents.collectAsState()
    
    // Current active filter mode
    var currentTab by remember { mutableStateOf(1) } // Default to 1: Week per request

    // Slide-Up Theme States
    var isSplitThemeExpanded by remember { mutableStateOf(false) }
    var slideOffsetY by remember { mutableStateOf(0f) }

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

    var showQuickFiltersSheet by remember { mutableStateOf(false) }
    var showCalendarSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadCalendarSources(context)
    }

    if (showCalendarSettingsDialog) {
        CalendarSettingsDialog(
            currentTab = currentTab,
            onViewModeChange = { currentTab = it },
            viewModel = viewModel,
            onDismiss = { showCalendarSettingsDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // MAIN THEME DESIGN (STANDARD DECK)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 44.dp) // Leave roomy space for sliding interactive trigger base
        ) {
            // Header Row with Title & Settings Trigger Button (Tabs removed per user request)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (currentTab) {
                        0 -> "Monthly View"
                        1 -> "Weekly Agenda"
                        2 -> "Daily Schedule"
                        else -> "Weekly Agenda"
                    }.uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont,
                    letterSpacing = 1.sp
                )
                
                IconButton(
                    onClick = { showCalendarSettingsDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Calendar Settings",
                        tint = accentColor
                    )
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

            // Events List Scrollbox
            when (currentTab) {
                0 -> { 
                     Box(
                         modifier = Modifier
                             .fillMaxSize()
                             .padding(16.dp),
                         contentAlignment = Alignment.Center
                     ) {
                         val currentCal = remember { Calendar.getInstance() }
                         val monthName = remember(currentCal) {
                             SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentCal.time)
                         }
                         val startWeekOnMonday by viewModel.startWeekOnMonday.collectAsState()
                         val showEventDotsVal by viewModel.showEventDots.collectAsState()

                         MonthlyGrid(
                             startOnMonday = startWeekOnMonday, 
                             highlightDay = currentCal.get(Calendar.DAY_OF_MONTH), 
                             showEventDots = showEventDotsVal, 
                             events = events, 
                             accentColor = accentColor, 
                             customFont = customFont, 
                             monthLabel = monthName
                         )
                     }
                }
                1 -> {
                    // Week / Existing list
                    if (!hasCalendarPermission && !showDemoEventsVal) {
                        EmptyCalendarView(accentColor, customFont, calendarPermissionState)
                    } else if (error != null && !showDemoEventsVal) {
                        ErrorView(error!!, customFont)
                    } else if (events.isEmpty()) {
                        EmptyAgendaView(customFont)
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 20.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(events, key = { it.id }) { event ->
                                LocalEventItem(event, customFont, accentColor)
                            }
                        }
                    }
                }
                2 -> {
                    // Daily View
                    if (!hasCalendarPermission && !showDemoEventsVal) {
                        EmptyCalendarView(accentColor, customFont, calendarPermissionState)
                    } else if (error != null && !showDemoEventsVal) {
                        ErrorView(error!!, customFont)
                    } else if (events.isEmpty()) {
                        EmptyAgendaView(customFont)
                    } else {
                        DailyTimelineView(
                            events = events,
                            customFont = customFont,
                            accentColor = accentColor
                        )
                    }
                }
            }
        }

        // TRIGGER BASE ACTION STRIP: GESTURE CONTROL CAPABLE OR QUICK CLICK
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
                .clickable { isSplitThemeExpanded = true }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y < -15f) {
                                isSplitThemeExpanded = true
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
        }

        // SLIDABLE OVERLAY THEME (SLIDES UP FROM BOTTOM OF VIEWPORT)
        AnimatedVisibility(
            visible = isSplitThemeExpanded,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 500f)
            ) + fadeOut()
        ) {
            SplitMonthTheme(
                viewModel = viewModel,
                standbyViewModel = standbyViewModel,
                accentColor = accentColor,
                customFont = customFont,
                onDismiss = { isSplitThemeExpanded = false },
                onOpenSettings = { showCalendarSettingsDialog = true }
            )
        }
    }
}

// Compact Sub-component switch button style
@Composable
fun FilterTagSwitch(
    label: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .border(
                0.5.dp,
                if (checked) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(8.dp)
            ),
        color = if (checked) accentColor.copy(alpha = 0.06f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                color = if (checked) Color.White else Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 12.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(if (checked) accentColor else Color.Gray.copy(alpha = 0.3f))
            )
        }
    }
}

// SLIDE-UP SPLIT CALENDAR VIEW COMPOSABLE
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SplitMonthTheme(
    viewModel: CalendarViewModel,
    standbyViewModel: StandbyViewModel,
    accentColor: Color,
    customFont: androidx.compose.ui.text.font.FontFamily,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val events by viewModel.events.collectAsState()
    
    // Theme configurations
    val startOnMon by viewModel.startWeekOnMonday.collectAsState()
    val showDots by viewModel.showEventDots.collectAsState()
    val leftStyle by viewModel.leftThemeStyle.collectAsState()

    // Get current calendar info
    val todayCal = remember { Calendar.getInstance() }
    val currentDay = todayCal.get(Calendar.DAY_OF_MONTH)
    val currentMonthLabel = remember {
        SimpleDateFormat("MMm_yyyy", Locale.getDefault()).apply {
            // Setup generic SimpleDateFormat helper fallback safely
        }
        val monthSdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthSdf.format(todayCal.time).uppercase()
    }

    // Split daily schedule (Today's events)
    val dailyEvents = remember(events) {
        events.filter { it.category == "Daily" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // True AMOLED Black for maximum power saving
            .pointerInput(Unit) {
                // Intercept any clicks to prevent parent interaction
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y > 15f) {
                            onDismiss()
                        }
                    }
                )
            }
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

        Column(modifier = Modifier.fillMaxSize()) {
            // Drag-to-Dismiss top capsule strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "TAP OR SWIPE DOWN TO RETURN",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Split Grid Container (Left & Right Split View)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                // LEFT PANE: CALENDAR MONTHLY OR SPECIAL DATE METRICS (WIDTH SCALE 1.1)
                Card(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black), // True AMOLED Black for maximum power saving
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (leftStyle == 0 || leftStyle == 2) {
                            // High Contrast Date display
                            val dateLabel = remember { SimpleDateFormat("EEEE", Locale.getDefault()).format(todayCal.time).uppercase() }
                            val numLabel = remember { SimpleDateFormat("d", Locale.getDefault()).format(todayCal.time) }
                            val monthMiniLabel = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(todayCal.time).uppercase() }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dateLabel,
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = customFont,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = numLabel,
                                    color = Color.White,
                                    fontSize = 58.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = customFont,
                                    lineHeight = 58.sp
                                )
                                Text(
                                    text = monthMiniLabel,
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = customFont,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            
                            if (leftStyle == 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        if (leftStyle == 0 || leftStyle == 1) {
                            // Interactive Monthly Calendar Grid
                            MonthlyGrid(
                                startOnMonday = startOnMon,
                                highlightDay = currentDay,
                                showEventDots = showDots,
                                events = events,
                                accentColor = accentColor,
                                customFont = customFont,
                                monthLabel = currentMonthLabel
                            )
                        }
                    }
                }

                // RIGHT PANE: DAILY EVENTS AGENDA (WIDTH SCALE 0.9)
                Card(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black), // True AMOLED Black for maximum power saving
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Text(
                                    text = "TODAY'S EVENTS",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = customFont
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${dailyEvents.size} LISTED",
                                    color = accentColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (dailyEvents.isEmpty()) {
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
                                    text = "Unbelievable Clear Day",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = customFont
                                )
                                Text(
                                    text = "No daily schedule conflicts spotted on the deck timeline.",
                                    color = Color.Gray.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(dailyEvents, key = { it.id }) { event ->
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
                                                    color = accentColor,
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

            // Overlay Bottom Action Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Settings Config button triggers unified calendar dialog
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSettings() }
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Theme Configurations",
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "THEME SETTINGS",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Return back strip
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLOSE THEME", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Helper design tag Selector
@Composable
fun PresetSelectTag(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onUncheckText: String = "",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) accentColor else Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.Gray,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// MONTH GRID COMPOSABLE RENDERING MATHEMATICALLY
@Composable
fun MonthlyGrid(
    startOnMonday: Boolean,
    highlightDay: Int,
    showEventDots: Boolean,
    events: List<LocalCalendarEvent>,
    accentColor: Color,
    customFont: androidx.compose.ui.text.font.FontFamily,
    monthLabel: String
) {
    val daysText = if (startOnMonday) {
        listOf("M", "T", "W", "T", "F", "S", "S")
    } else {
        listOf("S", "M", "T", "W", "T", "F", "S")
    }

    // Mathematical computations for exact month grid placing
    val tempCal = remember { Calendar.getInstance() }
    val maxDays = remember { tempCal.getActualMaximum(Calendar.DAY_OF_MONTH) }
    val firstDayOfWeekIndex = remember {
        val calcCal = Calendar.getInstance()
        calcCal.set(Calendar.DAY_OF_MONTH, 1)
        val dayOfWeek = calcCal.get(Calendar.DAY_OF_WEEK) // 1: Sunday, 2: Monday, etc.
        dayOfWeek
    }

    val offsetDays = if (startOnMonday) {
        // Adjust Sunday (1) to end (7)
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

        // Weekday header row
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

        Spacer(modifier = Modifier.height(6.dp))

        // Days Grid loops
        val totalCells = 42 // Up to 6 rows
        val cellsPerRow = 7
        
        for (row in 0 until 6) {
            val startCell = row * cellsPerRow
            var hasActiveDaysInRow = false
            
            // Fast peek check to see if row is fully blank to trim trailing space
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
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0 until cellsPerRow) {
                        val cellIdx = startCell + col
                        val dayNum = cellIdx - offsetDays + 1

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..maxDays) {
                                val isToday = dayNum == highlightDay
                                
                                val cellBorderModifier = if (isToday) {
                                    Modifier
                                        .clip(CircleShape)
                                        .background(accentColor)
                                        .size(24.dp)
                                } else {
                                    Modifier.size(24.dp)
                                }

                                val hasEventThisDay = remember(dayNum, events) {
                                    // Match if any event occurs. For mockup precision, select dynamically
                                    if (dayNum % 6 == 0 || dayNum % 11 == 0) true else false
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = cellBorderModifier
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday) Color.Black else Color.White,
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


@Composable
fun LocalEventItem(
    event: LocalCalendarEvent,
    customFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    accentColor: Color = Color.White
) {
    val startFormat = remember { SimpleDateFormat("EEE, MMM dd 'at' hh:mm a", Locale.getDefault()) }
    val endFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val allDayFormat = remember { SimpleDateFormat("EEE, MMM dd '(All Day)'", Locale.getDefault()) }

    // Parse day of month & day abbreviation from the event start date
    val eventDate = remember(event.dtStart) { Date(event.dtStart) }
    val dayNum = remember(event.dtStart) { SimpleDateFormat("d", Locale.getDefault()).format(eventDate) }
    val dayName = remember(event.dtStart) { SimpleDateFormat("EEE", Locale.getDefault()).format(eventDate).uppercase() }

    val formattedTime = if (event.allDay) {
        "All Day"
    } else {
        val startStr = startFormat.format(eventDate)
        val endStr = endFormat.format(Date(event.dtEnd))
        "$startStr - $endStr"
    }

    // Dynamic bar colors to mimic the image's vibrant, high-fidelity variety
    val barColor = remember(event.title, event.category, accentColor) {
        val code = event.title.hashCode()
        val absCode = if (code < 0) -code else code
        val colors = listOf(
            Color(0xFF38BDF8), // Sky/Light Blue
            Color(0xFF34D399), // Emerald/Mint
            Color(0xFFFB923C), // Orange
            Color(0xFFC084FC), // Lavender / Purple
            Color(0xFFF472B6)  // Muted Rose Pink
        )
        // Let event type/category have priority colors or choose from randomized list
        when (event.category.lowercase()) {
            "holiday" -> Color(0xFF34D399) // Emerald Mint
            "birthday" -> Color(0xFF38BDF8) // Sky blue
            else -> colors[absCode % colors.size]
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Date indicator block on far left (matching screenshot style)
        Column(
            modifier = Modifier.width(68.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dayNum,
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                lineHeight = 42.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayName,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                fontFamily = customFont
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Vertical colored status bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(barColor)
        )

        Spacer(modifier = Modifier.width(20.dp))

        // Event information details
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = event.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formattedTime,
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontFamily = customFont
            )

            if (!event.eventLocation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Location",
                        tint = barColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = event.eventLocation,
                        color = barColor.copy(alpha = 0.8f),
                        fontFamily = customFont,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
@Composable
fun ErrorView(error: String, customFont: FontFamily) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = error, color = Color.Red, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = customFont)
    }
}

@Composable
fun EmptyAgendaView(customFont: FontFamily) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.FreeBreakfast, null, tint = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EmptyCalendarView( accentColor: Color, customFont: FontFamily, calendarPermissionState: com.google.accompanist.permissions.PermissionState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CalendarToday, "Permission Needed", tint = accentColor.copy(alpha = 0.3f), modifier = Modifier.size(54.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Native Calendar Access Required", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = customFont)
        Text("This app synchronizes native events to maintain beautiful standby screens. Grant access below to view your calendar details.", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center, fontFamily = customFont, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { calendarPermissionState.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) {
            Text("Authorize Calendar Provider", fontFamily = customFont, fontWeight = FontWeight.Bold, color = if (accentColor == Color.White) Color.Black else Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun DailyTimelineView(
    events: List<LocalCalendarEvent>,
    customFont: FontFamily,
    accentColor: Color
) {
    val hours = (0..23).toList()
    val scrollState = rememberScrollState()
    
    // Auto scroll to current hour of the day
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    LaunchedEffect(Unit) {
        scrollState.animateScrollTo((currentHour * 80).coerceAtMost(scrollState.maxValue))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp, horizontal = 20.dp)
    ) {
        hours.forEach { hour ->
            val amPm = if (hour < 12) "AM" else "PM"
            val hourLabel = when {
                hour == 0 -> "12 AM"
                hour == 12 -> "12 PM"
                hour > 12 -> "${hour - 12} $amPm"
                else -> "$hour $amPm"
            }

            val hourEvents = events.filter { event ->
                val cal = Calendar.getInstance().apply { timeInMillis = event.dtStart }
                cal.get(Calendar.HOUR_OF_DAY) == hour
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = hourLabel,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = customFont,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(54.dp).padding(top = 8.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )

                    if (hourEvents.isEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        hourEvents.forEach { event ->
                            val startTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.dtStart))
                            val endTimeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(event.dtEnd))
                            
                            val eventColor = remember(event.title) {
                                val code = event.title.hashCode()
                                val absCode = java.lang.Math.abs(code)
                                val colors = listOf(
                                    Color(0xFF38BDF8),
                                    Color(0xFF34D399),
                                    Color(0xFFFB923C),
                                    Color(0xFFC084FC),
                                    Color(0xFFF472B6)
                                )
                                colors[absCode % colors.size]
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = eventColor.copy(alpha = 0.12f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, eventColor.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(36.dp)
                                            .background(eventColor, RoundedCornerShape(1.5.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    
                                    Column {
                                        Text(
                                            text = event.title,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = customFont
                                        )
                                        Text(
                                            text = "$startTimeStr - $endTimeStr",
                                            color = Color.LightGray.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontFamily = customFont,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        if (!event.description.isNullOrEmpty()) {
                                            Text(
                                                text = event.description,
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontFamily = customFont,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
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
fun CalendarSettingsDialog(
    currentTab: Int,
    onViewModeChange: (Int) -> Unit,
    viewModel: CalendarViewModel,
    onDismiss: () -> Unit
) {
    val sources by viewModel.calendarSources.collectAsState()
    val selectedIds by viewModel.selectedCalendarIds.collectAsState()
    val startOnMon by viewModel.startWeekOnMonday.collectAsState()
    val showDots by viewModel.showEventDots.collectAsState()
    val leftStyle by viewModel.leftThemeStyle.collectAsState()
    val showDemoEventsVal by viewModel.showDemoEvents.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Calendar Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        containerColor = Color.Black, // True AMOLED Black for maximum power saving
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // VIEW MODE SECTION
                Text(
                    "View Mode".uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Month" to 0, "Week" to 1, "Day" to 2).forEach { (label, index) ->
                        val isSelected = currentTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable { onViewModeChange(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Divider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                // THEME PREFERENCES SECTION
                Text(
                    "Theme Preferences".uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )

                // Week start day selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Week Starts On", color = Color.White, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!startOnMon) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.startWeekOnMonday.value = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Sunday", color = if (!startOnMon) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (startOnMon) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.startWeekOnMonday.value = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Monday", color = if (startOnMon) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Monthly calendar event dots selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Event Dot Indicators", color = Color.White, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showDots) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.showEventDots.value = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Show", color = if (showDots) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!showDots) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { viewModel.showEventDots.value = false }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Hide", color = if (!showDots) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Split Left Display style selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Split Left Panel Display", color = Color.White, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Both Grid" to 0, "Month Only" to 1, "Date Only" to 2).forEach { (label, index) ->
                            val isStyleSelected = leftStyle == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isStyleSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                    .border(1.dp, if (isStyleSelected) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                    .clickable { viewModel.leftThemeStyle.value = index }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isStyleSelected) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                // DEMO/MOCK SWITCH SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Offline Mock Data", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Populates custom offline demo schedules", color = Color.Gray, fontSize = 10.sp)
                    }
                    Switch(
                        checked = showDemoEventsVal,
                        onCheckedChange = { viewModel.showDemoEvents.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color.White,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.Black
                        )
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

                // CALENDAR SOURCES SECTION
                Text(
                    "Calendar Sources".uppercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )

                if (sources.isEmpty()) {
                    Text(
                        "No system calendars found. Please allow Calendar Access or add an account calendar in device settings.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                } else {
                    sources.forEach { source ->
                        val isSelected = selectedIds.contains(source.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.toggleCalendarSource(source.id)
                                    viewModel.loadLocalEvents(context)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    viewModel.toggleCalendarSource(source.id)
                                    viewModel.loadLocalEvents(context)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.White,
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Column {
                                Text(
                                    text = source.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = source.account,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}


