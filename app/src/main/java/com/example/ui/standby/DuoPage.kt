package com.example.ui.standby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    val leftScreens by viewModel.duoLeftScreenIndices.collectAsState()
    val rightScreens by viewModel.duoRightScreenIndices.collectAsState()
    val selectedLeft by viewModel.duoLeftWidgetIndex.collectAsState()
    val selectedRight by viewModel.duoRightWidgetIndex.collectAsState()

    val initialLeftPage = remember(leftScreens, selectedLeft) {
        val idx = leftScreens.indexOf(selectedLeft)
        if (idx >= 0) idx else 0
    }
    val initialRightPage = remember(rightScreens, selectedRight) {
        val idx = rightScreens.indexOf(selectedRight)
        if (idx >= 0) idx else 0
    }

    val leftPagerState = rememberPagerState(
        initialPage = initialLeftPage.coerceIn(0, (leftScreens.size - 1).coerceAtLeast(0)),
        pageCount = { leftScreens.size }
    )
    val rightPagerState = rememberPagerState(
        initialPage = initialRightPage.coerceIn(0, (rightScreens.size - 1).coerceAtLeast(0)),
        pageCount = { rightScreens.size }
    )

    var hasInitLeft by remember { mutableStateOf(false) }
    LaunchedEffect(leftPagerState.currentPage, leftScreens) {
        if (!hasInitLeft) {
            hasInitLeft = true
        } else if (leftScreens.isNotEmpty()) {
            val safePage = leftPagerState.currentPage.coerceIn(0, leftScreens.lastIndex)
            val currentScreenIdx = leftScreens[safePage]
            haptics.tick()
            viewModel.setDuoLeftWidgetIndex(currentScreenIdx)
        }
    }

    LaunchedEffect(selectedLeft, leftScreens) {
        val targetPage = leftScreens.indexOf(selectedLeft)
        if (targetPage >= 0 && leftPagerState.currentPage != targetPage) {
            leftPagerState.scrollToPage(targetPage)
        }
    }

    var hasInitRight by remember { mutableStateOf(false) }
    LaunchedEffect(rightPagerState.currentPage, rightScreens) {
        if (!hasInitRight) {
            hasInitRight = true
        } else if (rightScreens.isNotEmpty()) {
            val safePage = rightPagerState.currentPage.coerceIn(0, rightScreens.lastIndex)
            val currentScreenIdx = rightScreens[safePage]
            haptics.tick()
            viewModel.setDuoRightWidgetIndex(currentScreenIdx)
        }
    }

    LaunchedEffect(selectedRight, rightScreens) {
        val targetPage = rightScreens.indexOf(selectedRight)
        if (targetPage >= 0 && rightPagerState.currentPage != targetPage) {
            rightPagerState.scrollToPage(targetPage)
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
    var activeScreenManagerPane by remember { mutableIntStateOf(0) } // 0 = hidden, 1 = left, 2 = right

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

            // Full-Screen Edge-to-Edge Split Row
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
                    if (leftScreens.isEmpty()) {
                        // Empty State: Just show a clean + sign until user adds a screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = {
                                    haptics.confirm()
                                    activeScreenManagerPane = 1
                                },
                                modifier = Modifier
                                    .size(76.dp)
                                    .testTag("duo_left_empty_add_btn"),
                                shape = CircleShape,
                                color = Color(0xFF14161D),
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Left Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        VerticalPager(
                            state = leftPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val screenIndex = leftScreens.getOrElse(page) { leftScreens.first() }
                            WidgetContainer(pageIndex = screenIndex) {
                                RenderDuoWidget(
                                    pageIndex = screenIndex,
                                    paneIndex = 1,
                                    viewModel = viewModel,
                                    calendarViewModel = calendarViewModel,
                                    weatherViewModel = weatherViewModel
                                )
                            }
                        }

                        val currentLeftScreen = leftScreens.getOrElse(leftPagerState.currentPage.coerceIn(0, leftScreens.lastIndex)) { leftScreens.first() }

                        // Left Pane Top Bar: TimeZone Chip (only for clock/map screens) + Plus icon button
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentLeftScreen in 0..13) {
                                Surface(
                                    modifier = Modifier
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
                            }

                            Surface(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.tick()
                                        activeScreenManagerPane = 1
                                    }
                                    .testTag("duo_left_manage_screens_btn"),
                                shape = CircleShape,
                                color = Color(0xFF14161C).copy(alpha = 0.78f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Left Screen",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        // Left Pager Compact Vertical Indicator for active screens in this pane
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 4.dp),
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 0 until leftScreens.size) {
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
                                                viewModel.setDuoLeftWidgetIndex(leftScreens[i])
                                                coroutineScope.launch {
                                                    leftPagerState.animateScrollToPage(i)
                                                }
                                            }
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Screen to Left Pane",
                                    tint = colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clickable {
                                            haptics.tick()
                                            activeScreenManagerPane = 1
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
                    if (rightScreens.isEmpty()) {
                        // Empty State: Just show a clean + sign until user adds a screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                onClick = {
                                    haptics.confirm()
                                    activeScreenManagerPane = 2
                                },
                                modifier = Modifier
                                    .size(76.dp)
                                    .testTag("duo_right_empty_add_btn"),
                                shape = CircleShape,
                                color = Color(0xFF14161D),
                                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Right Screen",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        VerticalPager(
                            state = rightPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val screenIndex = rightScreens.getOrElse(page) { rightScreens.first() }
                            WidgetContainer(pageIndex = screenIndex) {
                                RenderDuoWidget(
                                    pageIndex = screenIndex,
                                    paneIndex = 2,
                                    viewModel = viewModel,
                                    calendarViewModel = calendarViewModel,
                                    weatherViewModel = weatherViewModel
                                )
                            }
                        }

                        val currentRightScreen = rightScreens.getOrElse(rightPagerState.currentPage.coerceIn(0, rightScreens.lastIndex)) { rightScreens.first() }

                        // Right Pane Top Bar: Plus icon button + TimeZone Chip (only for clock/map screens)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 10.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.tick()
                                        activeScreenManagerPane = 2
                                    }
                                    .testTag("duo_right_manage_screens_btn"),
                                shape = CircleShape,
                                color = Color(0xFF14161C).copy(alpha = 0.78f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Right Screen",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            if (currentRightScreen in 0..13) {
                                Surface(
                                    modifier = Modifier
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
                            }
                        }

                        // Right Pager Compact Vertical Indicator for active screens in this pane
                        Surface(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                for (i in 0 until rightScreens.size) {
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
                                                viewModel.setDuoRightWidgetIndex(rightScreens[i])
                                                coroutineScope.launch {
                                                    rightPagerState.animateScrollToPage(i)
                                                }
                                            }
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Screen to Right Pane",
                                    tint = colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clickable {
                                            haptics.tick()
                                            activeScreenManagerPane = 2
                                        }
                                )
                            }
                        }
                    }
                }
            }

            if (activeScreenManagerPane != 0) {
                DuoPaneScreenManagerDialog(
                    paneIndex = activeScreenManagerPane,
                    viewModel = viewModel,
                    calendarViewModel = calendarViewModel,
                    weatherViewModel = weatherViewModel,
                    onDismiss = { activeScreenManagerPane = 0 }
                )
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

/**
 * Visual-only screen picker for Duo Panes: shows live visual previews of each screen without text names.
 */
@Composable
fun DuoPaneScreenManagerDialog(
    paneIndex: Int,
    viewModel: StandbyViewModel,
    calendarViewModel: CalendarViewModel = viewModel(),
    weatherViewModel: WeatherViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val isLeft = paneIndex == 1
    val screens by (if (isLeft) viewModel.duoLeftScreenIndices else viewModel.duoRightScreenIndices).collectAsState()
    val allIndices = remember(viewModel.duoWidgetTitles) { viewModel.duoWidgetTitles.indices.toList() }
    val haptics = rememberStandbyHaptics()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.ui.theme.MyApplicationTheme {
            val colorScheme = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .testTag("duo_visual_screen_picker_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 165.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allIndices, key = { it }) { screenIdx ->
                            val isAdded = screenIdx in screens
                            val orderNum = screens.indexOf(screenIdx) + 1

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.45f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.Black)
                                    .border(
                                        width = if (isAdded) 3.dp else 1.dp,
                                        color = if (isAdded) colorScheme.primary else colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .testTag("duo_visual_picker_card_$screenIdx")
                            ) {
                                // Live visual preview of the screen (no text title)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clipToBounds()
                                ) {
                                    RenderDuoWidget(
                                        pageIndex = screenIdx,
                                        paneIndex = paneIndex,
                                        viewModel = viewModel,
                                        calendarViewModel = calendarViewModel,
                                        weatherViewModel = weatherViewModel
                                    )
                                }

                                // Transparent full-card touch interceptor so tapping anywhere toggles adding/removing the screen visual
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            haptics.confirm()
                                            if (isLeft) {
                                                viewModel.toggleScreenInDuoLeft(screenIdx)
                                            } else {
                                                viewModel.toggleScreenInDuoRight(screenIdx)
                                            }
                                        }
                                )

                                // Top-right Material 3 visual selection indicator (+ or sequence badge)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp),
                                    shape = CircleShape,
                                    color = if (isAdded) colorScheme.primary else colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                                    border = BorderStroke(1.dp, if (isAdded) colorScheme.onPrimary.copy(alpha = 0.25f) else colorScheme.outlineVariant)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isAdded) {
                                            Text(
                                                text = "$orderNum",
                                                color = colorScheme.onPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add",
                                                tint = colorScheme.onSurface,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Material 3 FloatingActionButton at bottom-end
                    FloatingActionButton(
                        onClick = {
                            haptics.confirm()
                            onDismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .testTag("duo_visual_picker_done_btn"),
                        shape = CircleShape,
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Done"
                        )
                    }
                }
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
        13 -> SquircleTickDigitalClockFace(
            calendar = zonedTime,
            accentColor = accentColor,
            customFont = customFont,
            scale = 1.0f,
            use24Hour = use24Hour,
            isDuoCompact = true,
            standbyViewModel = viewModel,
            paneIndex = paneIndex
        )

        // 14..17: The 4 Main Utility Screens (Alarm, Calendar, Music, Tools) scaled for Duo aspect ratio
        14 -> DuoAspectScaledScreen { AlarmPage(viewModel = viewModel) }
        15 -> DuoAspectScaledScreen { CalendarPage(viewModel = calendarViewModel, standbyViewModel = viewModel) }
        16 -> DuoAspectScaledScreen { MediaPage(standbyViewModel = viewModel) }
        17 -> DuoAspectScaledScreen { TimerPage(viewModel = viewModel) }
    }
}
