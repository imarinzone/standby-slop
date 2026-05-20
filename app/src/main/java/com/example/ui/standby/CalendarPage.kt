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
            // Header panel containing controls & options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "UPCOMING SCHEDULE",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp,
                        fontFamily = customFont
                    )
                    Text(
                        text = "Device Calendar Decks",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = customFont
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Filter Hub toggle button
                    IconButton(
                        onClick = { showQuickFiltersSheet = !showQuickFiltersSheet },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (showQuickFiltersSheet) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Event Filter Deck",
                            tint = if (showQuickFiltersSheet) accentColor else Color.White
                        )
                    }

                    if (hasCalendarPermission) {
                        Button(
                            onClick = { viewModel.loadLocalEvents(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Sync",
                                fontFamily = customFont,
                                fontWeight = FontWeight.Bold,
                                color = if (accentColor == Color.White) Color.Black else Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)

            // Quick Filters Drawer (Card accordion list inside top screen panel)
            AnimatedVisibility(
                visible = showQuickFiltersSheet,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "CALENDAR FILTER DECK",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Daily Switch
                            FilterTagSwitch(
                                label = "DAILY EVENTS",
                                checked = showDailyFilter,
                                accentColor = accentColor,
                                onCheckedChange = { viewModel.showDaily.value = it },
                                modifier = Modifier.weight(1f)
                            )
                            // Weekly Switch
                            FilterTagSwitch(
                                label = "WEEKLY EVENTS",
                                checked = showWeeklyFilter,
                                accentColor = accentColor,
                                onCheckedChange = { viewModel.showWeekly.value = it },
                                modifier = Modifier.weight(1f)
                            )
                            // Monthly Switch
                            FilterTagSwitch(
                                label = "MONTHLY EVENTS",
                                checked = showMonthlyFilter,
                                accentColor = accentColor,
                                onCheckedChange = { viewModel.showMonthly.value = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Show Realistic Demo Schedules",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                            Switch(
                                checked = showDemoEventsVal,
                                onCheckedChange = { viewModel.showDemoEvents.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = accentColor
                                ),
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }

            // Events List Scrollbox
            if (!hasCalendarPermission && !showDemoEventsVal) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Permission Needed",
                        tint = accentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Native Calendar Access Required",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont
                    )
                    Text(
                        text = "This app synchronizes native events to maintain beautiful standby screens. Grant access below or toggle 'Show Demo Schedules' in options.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = customFont,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { calendarPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text(
                            "Authorize Calendar Provider",
                            fontFamily = customFont,
                            fontWeight = FontWeight.Bold,
                            color = if (accentColor == Color.White) Color.Black else Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                if (error != null && !showDemoEventsVal) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = error!!,
                            color = Color.Red,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = customFont
                        )
                    }
                } else if (events.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FreeBreakfast,
                            contentDescription = "Clear agenda",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your Standby Schedule looks empty",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont
                        )
                        Text(
                            text = "Try enabling 'Show Demo Schedules' or activating event category options above.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 2.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(events, key = { it.id }) { event ->
                            LocalEventItem(event, customFont, accentColor)
                        }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardDoubleArrowUp,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "SWIPE UP OR TAP FOR SPLIT MONTH THEME",
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontFamily = customFont
                )
            }
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
                onDismiss = { isSplitThemeExpanded = false }
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
    onDismiss: () -> Unit
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

    // Local configuration drawer in overlay
    var showInlineConfigDeck by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617)) // Extra cinematic obsidian black
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.4f)),
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F19)),
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

            // INLINE ACCORDION DECK PREFERENCES ROW (CONFIGURABILITY ON FLY)
            AnimatedVisibility(
                visible = showInlineConfigDeck,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SPLIT THEME PREFERENCES",
                            color = accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Week Starter Config
                            Column(modifier = Modifier.weight(1f)) {
                                Text("WEEK START DAY", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PresetSelectTag(
                                        label = "Sun",
                                        isSelected = !startOnMon,
                                        accentColor = accentColor,
                                        onClick = { viewModel.startWeekOnMonday.value = false }
                                    )
                                    PresetSelectTag(
                                        label = "Mon",
                                        isSelected = startOnMon,
                                        accentColor = accentColor,
                                        onClick = { viewModel.startWeekOnMonday.value = true }
                                    )
                                }
                            }

                            // Event Dot indicators Config
                            Column(modifier = Modifier.weight(1f)) {
                                Text("EVENT INDICATORS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PresetSelectTag(
                                        label = "Show",
                                        isSelected = showDots,
                                        accentColor = accentColor,
                                        onUncheckText = "Hide",
                                        onClick = { viewModel.showEventDots.value = true }
                                    )
                                    PresetSelectTag(
                                        label = "Hide",
                                        isSelected = !showDots,
                                        accentColor = accentColor,
                                        onClick = { viewModel.showEventDots.value = false }
                                    )
                                }
                            }

                            // Theme Split panel layouts Config
                            Column(modifier = Modifier.weight(1.5f)) {
                                Text("LEFT DECK DISPLAY", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    PresetSelectTag(
                                        label = "Both",
                                        isSelected = leftStyle == 0,
                                        accentColor = accentColor,
                                        onClick = { viewModel.leftThemeStyle.value = 0 }
                                    )
                                    PresetSelectTag(
                                        label = "Month Only",
                                        isSelected = leftStyle == 1,
                                        accentColor = accentColor,
                                        onClick = { viewModel.leftThemeStyle.value = 1 }
                                    )
                                    PresetSelectTag(
                                        label = "Date Only",
                                        isSelected = leftStyle == 2,
                                        accentColor = accentColor,
                                        onClick = { viewModel.leftThemeStyle.value = 2 }
                                    )
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
                // Settings adjust button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showInlineConfigDeck = !showInlineConfigDeck }
                        .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .background(if (showInlineConfigDeck) accentColor.copy(alpha = 0.1f) else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (showInlineConfigDeck) accentColor else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "THEME CONTROLS",
                        color = if (showInlineConfigDeck) Color.White else Color.Gray,
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
    val allDayFormat = remember { SimpleDateFormat("EEE, MMM dd (All Day)", Locale.getDefault()) }

    val formattedTime = if (event.allDay) {
        allDayFormat.format(Date(event.dtStart))
    } else {
        val startStr = startFormat.format(Date(event.dtStart))
        val endStr = endFormat.format(Date(event.dtEnd))
        "$startStr - $endStr"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.title, 
                    color = Color.White, 
                    fontWeight = FontWeight.SemiBold, 
                    fontFamily = customFont,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = event.category.uppercase(),
                        color = accentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formattedTime, 
                color = Color(0xFF94A3B8),
                fontFamily = customFont,
                fontSize = 13.sp
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
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = event.eventLocation, 
                        color = accentColor, 
                        fontFamily = customFont,
                        fontSize = 12.sp
                    )
                }
            }
            if (!event.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = event.description, 
                    color = Color(0xFFCBD5E1),
                    fontFamily = customFont,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

