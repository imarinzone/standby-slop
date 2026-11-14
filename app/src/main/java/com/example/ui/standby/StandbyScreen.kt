package com.example.ui.standby

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandbyScreen(
    modifier: Modifier = Modifier,
    viewModel: StandbyViewModel = viewModel(),
    calendarViewModel: CalendarViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = rememberStandbyHaptics()

    val enabledClockFaces by viewModel.enabledClockFaces.collectAsState()
    val activeClockFaceIndices = remember(enabledClockFaces) {
        enabledClockFaces.sorted().ifEmpty { listOf(0) }
    }
    val initialClockFace = remember {
        val savedTheme = viewModel.lastClockFaceIndex.value
        val pos = activeClockFaceIndices.indexOf(savedTheme)
        if (pos >= 0) pos else 0
    }
    val horizontalPagerState = rememberPagerState(pageCount = { 6 })
    val verticalPagerState = rememberPagerState(
        initialPage = initialClockFace,
        pageCount = { activeClockFaceIndices.size }
    )

    var isHudVisible by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isOverviewMode by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    val activeRingingAlarm by AlarmRingingController.activeRingingAlarm.collectAsState()
    val activeAlarmVolumePct by AlarmRingingController.currentVolumePercent.collectAsState()

    // Subtle haptic tick on horizontal screen switch (skip initial composition)
    var hasInitializedHorizontal by remember { mutableStateOf(false) }
    LaunchedEffect(horizontalPagerState.currentPage) {
        if (!hasInitializedHorizontal) {
            hasInitializedHorizontal = true
        } else {
            haptics.tick()
        }
    }

    // Subtle haptic tick & persistence on vertical clock face switch (skip initial composition)
    var hasInitializedVertical by remember { mutableStateOf(false) }
    LaunchedEffect(verticalPagerState.currentPage, activeClockFaceIndices) {
        if (!hasInitializedVertical) {
            hasInitializedVertical = true
        } else {
            haptics.tick()
            val themeIdx = activeClockFaceIndices.getOrElse(verticalPagerState.currentPage) { 0 }
            viewModel.setLastClockFaceIndex(themeIdx)
        }
    }

    // Auto-hide HUD after 5 seconds of inactivity
    LaunchedEffect(isHudVisible) {
        if (isHudVisible) {
            delay(5000)
            isHudVisible = false
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val currentTabForBg = if (!isOverviewMode) horizontalPagerState.currentPage else 0
    val currentBgUri by viewModel.getBgUriFlow(currentTabForBg).collectAsState()

    val clockBrightnessFactor by viewModel.clockBrightnessFactor.collectAsState()
    val isPreAlarmDimming by viewModel.isPreAlarmDimmingActive.collectAsState()
    val context = LocalContext.current

    // Sync Activity window screenBrightness when pre-alarm gradual dimming is active on Clock or Duo display
    LaunchedEffect(isPreAlarmDimming, clockBrightnessFactor, horizontalPagerState.currentPage, isOverviewMode, showSettingsDialog) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val isClockOrDuoVisible = !isOverviewMode && !showSettingsDialog &&
            (horizontalPagerState.currentPage == 0 || horizontalPagerState.currentPage == 5)
        val targetBrightness = if (isPreAlarmDimming && isClockOrDuoVisible) {
            clockBrightnessFactor.coerceIn(0.05f, 1.0f)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        val params = window.attributes
        if (params.screenBrightness != targetBrightness) {
            params.screenBrightness = targetBrightness
            window.attributes = params
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val params = window.attributes
                if (params.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = params
                }
            }
        }
    }

    // Clock screen (page 0) keeps its signature deep canvas; non-clock screens use Adaptive Material 3 surface
    val rootContainerColor = if (!isOverviewMode && horizontalPagerState.currentPage == 0) {
        Color.Black
    } else {
        colorScheme.surface
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(rootContainerColor)
            .pointerInput(isOverviewMode, isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectTransformGestures { _, _, zoom, _ ->
                    if ((zoom < 0.88f || zoom > 1.12f) && !isOverviewMode) {
                        haptics.tick()
                        isOverviewMode = true
                    }
                }
            }
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectTapGestures(
                    onTap = {
                        isHudVisible = !isHudVisible
                    }
                )
            }
    ) {
        if (currentBgUri != null) {
            coil.compose.AsyncImage(
                model = currentBgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        if (!isOverviewMode) {
            HorizontalPager(
                state = horizontalPagerState,
                userScrollEnabled = !isScreenLocked,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        VerticalPager(
                            state = verticalPagerState,
                            userScrollEnabled = !isScreenLocked,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIdx ->
                            val themeIdx = activeClockFaceIndices.getOrElse(pageIdx) { 0 }
                            ClockPage(themeIndex = themeIdx)
                        }
                    }
                    1 -> AlarmPage(viewModel = viewModel)
                    2 -> CalendarPage(viewModel = calendarViewModel, standbyViewModel = viewModel)
                    3 -> MediaPage(standbyViewModel = viewModel)
                    4 -> TimerPage(viewModel = viewModel)
                    5 -> DuoPage(viewModel = viewModel, calendarViewModel = calendarViewModel)
                }
            }

            // Material 3 Floating Top Navigation Pill Bar
            AnimatedVisibility(
                visible = isHudVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            ) {
                TopAppBarOverlay(
                    currentPage = horizontalPagerState.currentPage,
                    onPageSelected = { targetPage ->
                        haptics.tick()
                        isHudVisible = true
                        coroutineScope.launch {
                            horizontalPagerState.animateScrollToPage(targetPage)
                        }
                    }
                )
            }

            // Material 3 Vertical Clock Face Indicator (only on Clock page 0)
            if (horizontalPagerState.currentPage == 0) {
                AnimatedVisibility(
                    visible = isHudVisible,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                ) {
                    VerticalThemeIndicator(
                        currentIndex = verticalPagerState.currentPage.coerceIn(0, (activeClockFaceIndices.size - 1).coerceAtLeast(0)),
                        pageCount = activeClockFaceIndices.size,
                        onDotClicked = { selectedPagerIdx ->
                            haptics.tick()
                            val themeIdx = activeClockFaceIndices.getOrElse(selectedPagerIdx) { 0 }
                            viewModel.setLastClockFaceIndex(themeIdx)
                            isHudVisible = true
                            coroutineScope.launch {
                                verticalPagerState.animateScrollToPage(selectedPagerIdx)
                            }
                        }
                    )
                }
            }

            // Material 3 Expressive HUD Floating Action Dock (Pure AMOLED, Lock, Overview Spaces, Settings)
            val isPureAmoled by viewModel.isPureAmoledEnabled.collectAsState()
            AnimatedVisibility(
                visible = isHudVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + scaleOut(targetScale = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    border = expressiveSurfaceBorder(highlighted = isPureAmoled)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.confirm()
                                isHudVisible = true
                                viewModel.togglePureAmoledMode()
                            },
                            icon = if (isPureAmoled) Icons.Filled.Contrast else Icons.Outlined.Contrast,
                            contentDescription = if (isPureAmoled) "Disable Pure AMOLED Mode" else "Enable Pure AMOLED Mode",
                            selected = isPureAmoled,
                            containerColor = if (isPureAmoled) colorScheme.primary else colorScheme.secondaryContainer,
                            contentColor = if (isPureAmoled) colorScheme.onPrimary else colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("hud_amoled_button")
                        )

                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.confirm()
                                isScreenLocked = true
                                isHudVisible = false
                            },
                            icon = Icons.Outlined.Lock,
                            contentDescription = "Lock Screen inputs",
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("hud_lock_button")
                        )

                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.tick()
                                isHudVisible = true
                                isOverviewMode = true
                            },
                            icon = Icons.Outlined.GridView,
                            contentDescription = "Standby Layout Overview",
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                            modifier = Modifier.testTag("hud_overview_button")
                        )

                        ExpressiveIconActionButton(
                            onClick = {
                                haptics.tick()
                                isHudVisible = true
                                showSettingsDialog = true
                            },
                            icon = Icons.Default.Settings,
                            contentDescription = "Customize Standby Menu",
                            selected = true,
                            containerColor = colorScheme.primaryContainer,
                            contentColor = colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("hud_settings_button")
                        )
                    }
                }
            }

            if (showSettingsDialog) {
                StandbyCustomizationDialog(
                    viewModel = viewModel,
                    calendarViewModel = calendarViewModel,
                    currentPage = horizontalPagerState.currentPage,
                    onDismiss = {
                        haptics.confirm()
                        showSettingsDialog = false
                        isHudVisible = true
                    }
                )
            }
        } else {
            val activeToolIdx by viewModel.timerModeIndex.collectAsState()
            AppOverviewLayout(
                viewModel = viewModel,
                activePage = horizontalPagerState.currentPage,
                activeClockFace = activeClockFaceIndices.getOrElse(verticalPagerState.currentPage) { 0 },
                activeToolScreen = activeToolIdx,
                onNavigateToPage = { targetPage ->
                    haptics.tick()
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(targetPage)
                    }
                },
                onSelectClockFace = { themeIdx ->
                    haptics.tick()
                    if (!activeClockFaceIndices.contains(themeIdx)) {
                        viewModel.setClockFaceEnabled(themeIdx, true)
                    }
                    viewModel.setLastClockFaceIndex(themeIdx)
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(0)
                        val targetPagerIdx = viewModel.enabledClockFaces.value.sorted().indexOf(themeIdx).coerceAtLeast(0)
                        verticalPagerState.scrollToPage(targetPagerIdx)
                    }
                },
                onSelectToolScreen = { toolIdx ->
                    haptics.tick()
                    if (!viewModel.enabledToolScreens.value.contains(toolIdx)) {
                        viewModel.setToolScreenEnabled(toolIdx, true)
                    }
                    viewModel.setTimerModeIndex(toolIdx)
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(4)
                    }
                },
                onDismiss = {
                    haptics.tick()
                    isOverviewMode = false
                }
            )
        }

        // Global Active Alarm Ringing Dialog (displays even when viewing Clock or Duo screens)
        activeRingingAlarm?.let { ringingAlarm ->
            if (horizontalPagerState.currentPage != 1 || isOverviewMode || showSettingsDialog) {
                val use24HourGlobal by viewModel.use24HourFormat.collectAsState()
                ActiveAlarmRingingDialog(
                    alarm = ringingAlarm,
                    accentColor = colorScheme.primary,
                    customFont = FontFamily.Default,
                    use24Hour = use24HourGlobal,
                    onSnooze = {
                        haptics.tick()
                        AlarmRingingController.snoozeActiveAlarm(context)
                    },
                    onDismiss = {
                        haptics.confirm()
                        AlarmRingingController.dismissAlarm()
                    }
                )
            }
        }

        // Material 3 Screen Lock Overlay with stepped haptic unlock slider
        AnimatedVisibility(
            visible = isScreenLocked,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            val currentTab = horizontalPagerState.currentPage
            val activeColorIdx by viewModel.getColorFlow(currentTab).collectAsState()
            val accentColor = viewModel.colors.getOrNull(activeColorIdx)?.first ?: colorScheme.primary

            var isUnlockUiVisible by remember { mutableStateOf(false) }
            val dragAmount = remember { Animatable(0f) }
            var isDraggingState by remember { mutableStateOf(false) }
            var lastHapticBucket by remember { mutableIntStateOf(0) }

            LaunchedEffect(isUnlockUiVisible, isDraggingState) {
                if (isUnlockUiVisible && !isDraggingState) {
                    delay(4000)
                    isUnlockUiVisible = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptics.tick()
                                isUnlockUiVisible = true
                            },
                            onPress = {
                                isUnlockUiVisible = true
                            }
                        )
                    }
            ) {
                AnimatedVisibility(
                    visible = isUnlockUiVisible,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { -it },
                    exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Surface(
                        modifier = Modifier.padding(top = 28.dp),
                        shape = CircleShape,
                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Touch Input Locked",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isUnlockUiVisible,
                    enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 2 },
                    exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(modifier = Modifier.padding(bottom = 44.dp)) {
                        val density = LocalDensity.current
                        val trackWidthDp = 296.dp
                        val trackHeightDp = 56.dp
                        val thumbSizeDp = 48.dp
                        val paddingDp = 4.dp

                        val maxDragOffsetDp = trackWidthDp - thumbSizeDp - (paddingDp * 2)
                        val maxDragOffsetPx = with(density) { maxDragOffsetDp.toPx() }

                        Surface(
                            modifier = Modifier.size(width = trackWidthDp, height = trackHeightDp),
                            shape = CircleShape,
                            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            tonalElevation = 8.dp,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Slide to unlock",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )

                                val fraction = if (maxDragOffsetPx > 0) (dragAmount.value / maxDragOffsetPx).coerceIn(0f, 1f) else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(thumbSizeDp + (paddingDp * 2) + (maxDragOffsetDp * fraction))
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.22f))
                                )

                                Surface(
                                    modifier = Modifier
                                        .padding(start = paddingDp + with(density) { dragAmount.value.toDp() })
                                        .size(thumbSizeDp)
                                        .pointerInput(maxDragOffsetPx) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    isDraggingState = true
                                                    lastHapticBucket = 0
                                                    haptics.tick()
                                                },
                                                onDragEnd = {
                                                    isDraggingState = false
                                                    coroutineScope.launch {
                                                        if (dragAmount.value >= maxDragOffsetPx * 0.82f) {
                                                            haptics.confirm()
                                                            isScreenLocked = false
                                                            isHudVisible = true
                                                            isUnlockUiVisible = false
                                                            dragAmount.snapTo(0f)
                                                        } else {
                                                            dragAmount.animateTo(
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                    stiffness = Spring.StiffnessLow
                                                                )
                                                            )
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    isDraggingState = false
                                                    coroutineScope.launch {
                                                        dragAmount.animateTo(0f)
                                                    }
                                                },
                                                onDrag = { change, dragAmountOffset ->
                                                    change.consume()
                                                    coroutineScope.launch {
                                                        val safeMaxDrag = maxDragOffsetPx.coerceAtLeast(0f)
                                                        val nextVal = (dragAmount.value + dragAmountOffset.x).coerceIn(0f, safeMaxDrag)
                                                        dragAmount.snapTo(nextVal)
                                                        val bucket = if (safeMaxDrag > 0f) ((nextVal / safeMaxDrag) * 8f).toInt() else 0
                                                        if (bucket != lastHapticBucket) {
                                                            lastHapticBucket = bucket
                                                            haptics.step()
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    shape = CircleShape,
                                    color = accentColor,
                                    shadowElevation = 4.dp
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "Unlock handle",
                                            tint = if (accentColor == Color.White) Color.Black else Color.White,
                                            modifier = Modifier.size(22.dp)
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
fun TopAppBarOverlay(
    currentPage: Int,
    onPageSelected: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val items = listOf(
        Triple("Clock", Icons.Outlined.Schedule, Icons.Filled.Schedule),
        Triple("Alarm", Icons.Outlined.Alarm, Icons.Filled.Alarm),
        Triple("Calendar", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
        Triple("Music", Icons.Outlined.MusicNote, Icons.Filled.MusicNote),
        Triple("Tools", Icons.Outlined.Build, Icons.Filled.Build),
        Triple("Duo", Icons.Outlined.ViewColumn, Icons.Filled.ViewColumn)
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isCompactWidth = maxWidth < 600.dp
        val itemHPad = if (isCompactWidth) 11.dp else 15.dp
        val itemVPad = if (isCompactWidth) 7.dp else 9.dp

        Surface(
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = expressiveSurfaceBorder()
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, (title, outlinedIcon, filledIcon) ->
                    val isActive = currentPage == index
                    val containerColor by animateColorAsState(
                        targetValue = if (isActive) colorScheme.primaryContainer else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "navPillBg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isActive) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "navPillFg"
                    )
                    val pillCorner by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 14.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "navPillCorner"
                    )

                    Surface(
                        shape = RoundedCornerShape(pillCorner),
                        color = containerColor,
                        onClick = { onPageSelected(index) },
                        modifier = Modifier.testTag("top_nav_tab_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = itemHPad, vertical = itemVPad),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isActive) filledIcon else outlinedIcon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = title,
                                color = contentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalThemeIndicator(
    currentIndex: Int,
    pageCount: Int,
    onDotClicked: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        shape = CircleShape,
        tonalElevation = 4.dp,
        border = expressiveSurfaceBorder()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until pageCount) {
                val isSelected = i == currentIndex
                val height by animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 7.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "dotHeight"
                )
                Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = height)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) colorScheme.primary
                            else colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                        .clickable { onDotClicked(i) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StandbyCustomizationDialog(
    viewModel: StandbyViewModel,
    calendarViewModel: CalendarViewModel = viewModel(),
    currentPage: Int,
    onDismiss: () -> Unit
) {
    val haptics = rememberStandbyHaptics()
    var activeTab by remember { mutableIntStateOf(currentPage.coerceIn(0, 5)) }

    val tabItems = listOf(
        Triple("Clock", "Customize clock faces, time format, and display styling.", Icons.Outlined.Schedule),
        Triple("Alarm", "Configure pre-alarm gradual dimming and alarm appearance.", Icons.Outlined.Alarm),
        Triple("Calendar", "Adjust agenda timeline format and calendar grid layout.", Icons.Outlined.CalendarToday),
        Triple("Music", "Choose player visual theme and accent styling.", Icons.Outlined.MusicNote),
        Triple("Tools", "Customize accent color and backdrop for Standby Tools.", Icons.Outlined.Build),
        Triple("Duo", "Add or remove visual screens for Left and Right Duo panes.", Icons.Outlined.ViewColumn)
    )

    val selectedColorIdx by viewModel.getColorFlow(activeTab).collectAsState()
    val selectedFontIdx by viewModel.getFontFlow(activeTab).collectAsState()
    val clockScaleVal by viewModel.getScaleFlow(activeTab).collectAsState()

    val glowVal by viewModel.getGlowFlow(activeTab).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(activeTab).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(activeTab).collectAsState()
    val bgUri by viewModel.getBgUriFlow(activeTab).collectAsState()

    val use24Hour by viewModel.use24HourFormat.collectAsState()
    val showAmPm by viewModel.showAmPm.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val isClassicMediaTheme by viewModel.useClassicMediaTheme.collectAsState()

    val gradualDimEnabled by viewModel.gradualAlarmDimEnabled.collectAsState()
    val gradualDimWindowMins by viewModel.gradualAlarmDimWindowMinutes.collectAsState()
    val gradualDimMinBrightness by viewModel.gradualAlarmMinBrightness.collectAsState()
    val liveBrightnessFactor by viewModel.clockBrightnessFactor.collectAsState()
    val isPreAlarmDimming by viewModel.isPreAlarmDimmingActive.collectAsState()
    val nextAlarmLabel by viewModel.nextSystemAlarm.collectAsState()
    val minutesUntilAlarm by viewModel.minutesUntilNextAlarm.collectAsState()

    val showSlideshowTime by viewModel.showSlideshowTimeOverlay.collectAsState()
    val isPureAmoled by viewModel.isPureAmoledEnabled.collectAsState()
    var showHeaderInfo by remember(activeTab) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        KeepDialogImmersiveFullScreen()
        com.example.ui.theme.MyApplicationTheme(pureAmoled = isPureAmoled) {
            val colorScheme = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorScheme.surface
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isCompactHeight = maxHeight < 380.dp
                    val isCompactWidth = maxWidth < 620.dp
                    val railWidth = if (isCompactWidth) 88.dp else 104.dp
                    val contentHPad = if (isCompactWidth) 16.dp else 24.dp
                    val contentVPad = if (isCompactHeight) 10.dp else 16.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        // LEFT PANE: Material 3 Expressive NavigationRail following system or Pure AMOLED theme
                        NavigationRail(
                            containerColor = colorScheme.surfaceContainerLow,
                            contentColor = colorScheme.onSurface,
                            header = {
                                ExpressiveIconActionButton(
                                    onClick = {
                                        haptics.confirm()
                                        viewModel.togglePureAmoledMode()
                                    },
                                    icon = if (isPureAmoled) Icons.Filled.Contrast else Icons.Outlined.Contrast,
                                    contentDescription = "Toggle Pure AMOLED Mode",
                                    selected = isPureAmoled,
                                    size = 44.dp,
                                    iconSize = 20.dp,
                                    containerColor = if (isPureAmoled) colorScheme.primary else colorScheme.primaryContainer,
                                    contentColor = if (isPureAmoled) colorScheme.onPrimary else colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 6.dp)
                                        .testTag("navigation_rail_amoled_toggle")
                                )
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(railWidth)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                tabItems.forEachIndexed { idx, (title, _, icon) ->
                                    val isSelected = activeTab == idx
                                    NavigationRailItem(
                                        selected = isSelected,
                                        onClick = {
                                            haptics.tick()
                                            activeTab = idx
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = title
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        },
                                        colors = NavigationRailItemDefaults.colors(
                                            selectedIconColor = colorScheme.onPrimaryContainer,
                                            selectedTextColor = colorScheme.primary,
                                            indicatorColor = colorScheme.primaryContainer,
                                            unselectedIconColor = colorScheme.onSurfaceVariant,
                                            unselectedTextColor = colorScheme.onSurfaceVariant
                                        ),
                                        alwaysShowLabel = true,
                                        modifier = Modifier.testTag("studio_rail_tab_$idx")
                                    )
                                }
                            }
                        }

                        VerticalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.55f))

                        // RIGHT PANE: Material 3 Expressive Settings Deck
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = contentHPad, vertical = contentVPad)
                        ) {
                            // Clean Expressive Header Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (isCompactHeight) 8.dp else 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = colorScheme.primaryContainer,
                                        border = expressiveSurfaceBorder()
                                    ) {
                                        Icon(
                                            imageVector = tabItems[activeTab].third,
                                            contentDescription = null,
                                            tint = colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .padding(10.dp)
                                                .size(20.dp)
                                        )
                                    }
                                    Text(
                                        text = tabItems[activeTab].first,
                                        style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = {
                                            haptics.tick()
                                            showHeaderInfo = !showHeaderInfo
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = "Info",
                                            tint = if (showHeaderInfo) colorScheme.primary else colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                ExpressiveMorphButton(
                                    onClick = onDismiss,
                                    selected = true,
                                    modifier = Modifier.testTag("studio_apply_close_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Done", fontWeight = FontWeight.Bold)
                                }
                            }

                            AnimatedVisibility(visible = showHeaderInfo) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                    border = expressiveSurfaceBorder(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                ) {
                                    Text(
                                        text = tabItems[activeTab].second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }

                            // Scrollable Material 3 Expressive Bento Cards
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 10.dp else 14.dp)
                            ) {
                                // 0. Hero Pure AMOLED Mode Toggle Card (always 1 tap away in Settings)
                                ExpressiveAmoledBannerCard(
                                    isPureAmoled = isPureAmoled,
                                    onToggle = { enabled ->
                                        haptics.confirm()
                                        viewModel.setPureAmoledEnabled(enabled)
                                    }
                                )

                                // 1. Visual Color Swatches (Relevant on tabs 0..4; Duo uses each added screen's own page styling)
                                if (activeTab != 5) {
                                    ExpressiveSectionCard {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.Palette,
                                            title = "Accent Color"
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            viewModel.colors.forEachIndexed { index, (color, name) ->
                                                val isSelected = index == selectedColorIdx
                                                val swatchCorner by animateDpAsState(
                                                    targetValue = if (isSelected) 14.dp else 22.dp,
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                    label = "swatchCorner"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(swatchCorner))
                                                        .background(color)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) colorScheme.onSurface else colorScheme.outlineVariant,
                                                            shape = RoundedCornerShape(swatchCorner)
                                                        )
                                                        .clickable {
                                                            haptics.tick()
                                                            viewModel.setColorIndexForPage(activeTab, index)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = name,
                                                            tint = if (color == Color.White) Color.Black else Color.White,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Clock Tab (activeTab == 0): Active Clock Faces Visual Picker, Typography, Location, Format & Visual Styling
                                if (activeTab == 0) {
                                    val enabledClockSet by viewModel.enabledClockFaces.collectAsState()
                                    var isClockListExpanded by remember { mutableStateOf(false) }

                                    // 2a. Active Clock Faces Visual Selector (Expressive Card)
                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("clock_faces_toggle_card")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    haptics.tick()
                                                    isClockListExpanded = !isClockListExpanded
                                                },
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
                                                        imageVector = Icons.Outlined.WatchLater,
                                                        contentDescription = null,
                                                        tint = colorScheme.onPrimaryContainer,
                                                        modifier = Modifier
                                                            .padding(7.dp)
                                                            .size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Clock Faces (${enabledClockSet.size}/${viewModel.clockFaceTitles.size})",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (enabledClockSet.size < viewModel.clockFaceTitles.size) {
                                                    ExpressiveMorphButton(
                                                        onClick = {
                                                            haptics.tick()
                                                            viewModel.clockFaceTitles.indices.forEach { idx ->
                                                                viewModel.setClockFaceEnabled(idx, true)
                                                            }
                                                        },
                                                        selected = false,
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("enable_all_clock_faces_button")
                                                    ) {
                                                        Text("Enable All", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                }
                                                ExpressiveMorphButton(
                                                    onClick = {
                                                        haptics.tick()
                                                        isClockListExpanded = !isClockListExpanded
                                                    },
                                                    selected = isClockListExpanded,
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(if (isClockListExpanded) "Hide" else "Customize", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        AnimatedVisibility(visible = isClockListExpanded) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                            ) {
                                                items(viewModel.clockFaceTitles.size) { idx ->
                                                    val isEnabled = enabledClockSet.contains(idx)
                                                    val canUncheck = !isEnabled || enabledClockSet.size > 1
                                                    val previewCorner by animateDpAsState(
                                                        targetValue = if (isEnabled) 18.dp else 12.dp,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                        label = "clockPreviewCorner"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 148.dp, height = 88.dp)
                                                            .clip(RoundedCornerShape(previewCorner))
                                                            .background(Color.Black)
                                                            .border(
                                                                width = if (isEnabled) 2.5.dp else 1.dp,
                                                                color = if (isEnabled) colorScheme.primary else colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(previewCorner)
                                                            )
                                                            .clickable {
                                                                if (canUncheck) {
                                                                    haptics.tick()
                                                                    viewModel.toggleClockFace(idx)
                                                                }
                                                            }
                                                            .testTag("clock_face_toggle_row_$idx"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .requiredSize(400.dp, 220.dp)
                                                                .graphicsLayer {
                                                                    scaleX = 0.34f
                                                                    scaleY = 0.34f
                                                                }
                                                        ) {
                                                            ClockPage(themeIndex = idx)
                                                        }
                                                        Surface(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(6.dp)
                                                                .size(22.dp),
                                                            shape = CircleShape,
                                                            color = if (isEnabled) colorScheme.primary else colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Add,
                                                                    contentDescription = null,
                                                                    tint = if (isEnabled) colorScheme.onPrimary else colorScheme.onSurface,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2b. Typography Style Card (Expressive morphing chips)
                                    ExpressiveSectionCard {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.FontDownload,
                                            title = "Typography"
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            viewModel.fonts.forEachIndexed { index, (fontFamily, name) ->
                                                val isSelected = index == selectedFontIdx
                                                val chipCorner by animateDpAsState(
                                                    targetValue = if (isSelected) 24.dp else 12.dp,
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                    label = "fontChipCorner"
                                                )
                                                Surface(
                                                    onClick = {
                                                        haptics.tick()
                                                        viewModel.setFontIndexForPage(activeTab, index)
                                                    },
                                                    shape = RoundedCornerShape(chipCorner),
                                                    color = if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
                                                    border = expressiveSurfaceBorder(highlighted = isSelected)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = name,
                                                            fontFamily = fontFamily,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2c. Location & Time Zone Card (Clock tab only)
                                    val settingsContext = LocalContext.current
                                    val locMode by viewModel.locationMode.collectAsState()
                                    val customLoc by viewModel.customLocationName.collectAsState()
                                    val clockTzId by viewModel.clockTimeZoneId.collectAsState()
                                    val gpsLoc by viewModel.gpsLocationName.collectAsState()
                                    val resolvedInfo = remember(locMode, customLoc, clockTzId, gpsLoc) {
                                        viewModel.resolveCurrentLocationInfo()
                                    }
                                    val locPermLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.RequestMultiplePermissions()
                                    ) { perms ->
                                        val granted = perms[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                                            perms[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
                                        if (granted) {
                                            viewModel.setLocationMode("auto")
                                            viewModel.setClockTimeZoneId("AUTO")
                                            viewModel.refreshDeviceLocation(settingsContext)
                                        }
                                    }

                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("terminal_location_settings_card")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = colorScheme.primaryContainer
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Public,
                                                        contentDescription = null,
                                                        tint = colorScheme.onPrimaryContainer,
                                                        modifier = Modifier
                                                            .padding(7.dp)
                                                            .size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "${resolvedInfo.regionName} (${resolvedInfo.airportCode})",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = resolvedInfo.standardTimeShort,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            ExpressiveMorphButton(
                                                onClick = {
                                                    haptics.tick()
                                                    viewModel.setLocationMode("auto")
                                                    viewModel.setClockTimeZoneId("AUTO")
                                                    viewModel.setCustomLocationName("")
                                                    val ok = viewModel.refreshDeviceLocation(settingsContext)
                                                    if (!ok) {
                                                        locPermLauncher.launch(
                                                            arrayOf(
                                                                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                                            )
                                                        )
                                                    }
                                                },
                                                selected = locMode == "auto",
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                modifier = Modifier.testTag("detect_gps_location_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MyLocation,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("GPS", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        SearchableLocationTimeZoneField(
                                            viewModel = viewModel,
                                            paneIndex = 0,
                                            activeSummary = "${resolvedInfo.regionName} (${resolvedInfo.airportCode}) • ${resolvedInfo.standardTimeShort}",
                                            label = "Search city, airport, or time zone...",
                                            testTag = "custom_location_input"
                                        )
                                    }

                                    // 2d. Clock Scale & Time Format Grouped Expressive Card
                                    ExpressiveSectionCard {
                                        var lastScaleBucket by remember { mutableIntStateOf((clockScaleVal * 10).roundToInt()) }
                                        Column {
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
                                                            imageVector = Icons.Outlined.AspectRatio,
                                                            contentDescription = null,
                                                            tint = colorScheme.onPrimaryContainer,
                                                            modifier = Modifier
                                                                .padding(7.dp)
                                                                .size(18.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "Clock Scale",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.onSurface
                                                    )
                                                }
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.getDefault(), "%.1fx", clockScaleVal),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            Slider(
                                                value = clockScaleVal,
                                                onValueChange = { newValue ->
                                                    val bucket = (newValue * 10).roundToInt()
                                                    if (bucket != lastScaleBucket) {
                                                        lastScaleBucket = bucket
                                                        haptics.step()
                                                    }
                                                    viewModel.setClockScaleForPage(activeTab, newValue)
                                                },
                                                valueRange = 0.8f..1.8f,
                                                steps = 9,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        val timeSwitchCount = if (!use24Hour) 3 else 2
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            ExpressiveSettingSwitchRow(
                                                title = "24-Hour Format",
                                                subtitle = "Display hours in 24-hour military format (e.g. 14:30)",
                                                checked = use24Hour,
                                                icon = Icons.Outlined.Schedule,
                                                shape = expressiveGroupedShape(0, timeSwitchCount),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    viewModel.setUse24HourFormat(it)
                                                }
                                            )

                                            if (!use24Hour) {
                                                ExpressiveSettingSwitchRow(
                                                    title = "AM / PM Indicator",
                                                    subtitle = "Display meridian badge alongside 12-hour clocks",
                                                    checked = showAmPm,
                                                    icon = Icons.Outlined.WbTwilight,
                                                    shape = expressiveGroupedShape(1, timeSwitchCount),
                                                    onCheckedChange = {
                                                        haptics.confirm()
                                                        viewModel.setShowAmPm(it)
                                                    }
                                                )
                                            }

                                            ExpressiveSettingSwitchRow(
                                                title = "Seconds Tracker",
                                                subtitle = "Show live ticking seconds on compatible clock faces",
                                                checked = showSeconds,
                                                icon = Icons.Outlined.Timer,
                                                shape = expressiveGroupedShape(timeSwitchCount - 1, timeSwitchCount),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    viewModel.setShowSeconds(it)
                                                }
                                            )
                                        }
                                    }

                                    // 2e. Clock Numeral Effects & Visual Shader Gradients (Grouped Expressive Card)
                                    ExpressiveSectionCard {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.AutoAwesome,
                                            title = "Numeral Finish & Shader"
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            ExpressiveSettingSwitchRow(
                                                title = "Ambient Glow",
                                                subtitle = "Render soft tonal luminance around display numerals",
                                                checked = glowVal,
                                                icon = Icons.Outlined.Flare,
                                                shape = expressiveGroupedShape(0, 2),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    viewModel.setTextGlowForPage(activeTab, it)
                                                }
                                            )

                                            ExpressiveSettingSwitchRow(
                                                title = "Outline Mode",
                                                subtitle = "Draw hollow architectural strokes instead of solid fills",
                                                checked = outlineVal,
                                                icon = Icons.Outlined.FormatShapes,
                                                shape = expressiveGroupedShape(1, 2),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    viewModel.setTextOutlineForPage(activeTab, it)
                                                }
                                            )
                                        }

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(viewModel.gradients.size) { index ->
                                                val (gradName, gradColors) = viewModel.gradients[index]
                                                val isSelected = (index == gradientIdx) || (index == 0 && gradientIdx <= 0)
                                                val gradCorner by animateDpAsState(
                                                    targetValue = if (isSelected) 20.dp else 12.dp,
                                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                    label = "gradCorner"
                                                )
                                                val brush = if (index == 0) {
                                                    Brush.linearGradient(
                                                        listOf(colorScheme.surfaceContainerHighest, colorScheme.surfaceContainerHigh)
                                                    )
                                                } else {
                                                    Brush.linearGradient(gradColors)
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 66.dp, height = 42.dp)
                                                        .clip(RoundedCornerShape(gradCorner))
                                                        .background(brush)
                                                        .border(
                                                            width = if (isSelected) 3.dp else 1.dp,
                                                            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                                            shape = RoundedCornerShape(gradCorner)
                                                        )
                                                        .clickable {
                                                            haptics.tick()
                                                            viewModel.setTextGradientForPage(activeTab, if (index == 0) -1 else index)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = gradName,
                                                            tint = if (index == 0) colorScheme.onSurface else Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Alarm Tab (activeTab == 1): Pre-Alarm Gradual Dimming
                                if (activeTab == 1) {
                                    var showAlarmDimInfo by remember { mutableStateOf(false) }
                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("pre_alarm_dimming_settings_card")
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = colorScheme.primaryContainer
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.BrightnessLow,
                                                        contentDescription = null,
                                                        tint = colorScheme.onPrimaryContainer,
                                                        modifier = Modifier
                                                            .padding(7.dp)
                                                            .size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Pre-Alarm Dimming",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                                IconButton(
                                                    onClick = {
                                                        haptics.tick()
                                                        showAlarmDimInfo = !showAlarmDimInfo
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Info,
                                                        contentDescription = "Pre-Alarm Info",
                                                        tint = if (showAlarmDimInfo) colorScheme.primary else colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Surface(
                                                shape = CircleShape,
                                                color = if (isPreAlarmDimming) colorScheme.primaryContainer else colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = when {
                                                        !gradualDimEnabled -> "Off"
                                                        isPreAlarmDimming -> "${(liveBrightnessFactor * 100).roundToInt()}%"
                                                        minutesUntilAlarm != null -> "${minutesUntilAlarm}m"
                                                        else -> "Ready"
                                                    },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPreAlarmDimming) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = showAlarmDimInfo) {
                                            Text(
                                                text = if (!nextAlarmLabel.isNullOrBlank() && nextAlarmLabel != "No Alarm") {
                                                    "Next scheduled alarm: $nextAlarmLabel. Smoothly lowers screen brightness before wake-up."
                                                } else {
                                                    "Smoothly lowers screen brightness during the lead-up window before your scheduled alarm."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }

                                        ExpressiveSettingSwitchRow(
                                            title = "Gradual Dimming",
                                            subtitle = "Smoothly dim the display as your scheduled alarm time approaches",
                                            checked = gradualDimEnabled,
                                            icon = Icons.Outlined.Bedtime,
                                            shape = expressiveGroupedShape(0, 1),
                                            onCheckedChange = {
                                                haptics.confirm()
                                                viewModel.setGradualAlarmDimEnabled(it)
                                            },
                                            modifier = Modifier.testTag("gradual_alarm_dim_switch")
                                        )

                                        AnimatedVisibility(visible = gradualDimEnabled) {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                val windowOptions = listOf(15, 30, 45, 60)
                                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                                    windowOptions.forEachIndexed { idx, mins ->
                                                        SegmentedButton(
                                                            selected = gradualDimWindowMins == mins,
                                                            onClick = {
                                                                haptics.tick()
                                                                viewModel.setGradualAlarmDimWindowMinutes(mins)
                                                            },
                                                            shape = SegmentedButtonDefaults.itemShape(
                                                                index = idx,
                                                                count = windowOptions.size
                                                            ),
                                                            modifier = Modifier.testTag("gradual_dim_window_$mins")
                                                        ) {
                                                            Text("${mins}m", fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }

                                                var lastBrightnessBucket by remember {
                                                    mutableIntStateOf((gradualDimMinBrightness * 20).roundToInt())
                                                }
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Minimum Brightness",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colorScheme.onSurface
                                                        )
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                text = "${(gradualDimMinBrightness * 100).roundToInt()}%",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.Bold,
                                                                color = colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                    Slider(
                                                        value = gradualDimMinBrightness,
                                                        onValueChange = { newVal ->
                                                            val bucket = (newVal * 20).roundToInt()
                                                            if (bucket != lastBrightnessBucket) {
                                                                lastBrightnessBucket = bucket
                                                                haptics.step()
                                                            }
                                                            viewModel.setGradualAlarmMinBrightness(newVal)
                                                        },
                                                        valueRange = 0.15f..0.60f,
                                                        steps = 8,
                                                        modifier = Modifier
                                                            .padding(top = 2.dp)
                                                            .testTag("gradual_dim_min_brightness_slider")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. Calendar Tab (activeTab == 2)
                                if (activeTab == 2) {
                                    val calendarFilter by calendarViewModel.calendarFilter.collectAsState()
                                    val startOnMon by calendarViewModel.startWeekOnMonday.collectAsState()
                                    val showDots by calendarViewModel.showEventDots.collectAsState()
                                    val calUse24Hour by calendarViewModel.use24HourFormat.collectAsState()

                                    ExpressiveSectionCard {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.CalendarViewMonth,
                                            title = "Calendar Layout"
                                        )

                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            val options = listOf("Daily" to 0, "Weekly" to 1, "Monthly" to 2)
                                            options.forEachIndexed { idx, (label, filterVal) ->
                                                SegmentedButton(
                                                    selected = calendarFilter == filterVal,
                                                    onClick = {
                                                        haptics.tick()
                                                        calendarViewModel.setCalendarFilter(filterVal)
                                                    },
                                                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size)
                                                ) {
                                                    Text(label, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            ExpressiveSettingSwitchRow(
                                                title = "Start Week on Monday",
                                                subtitle = "Align monthly calendar grid weeks starting Monday",
                                                checked = startOnMon,
                                                icon = Icons.Outlined.DateRange,
                                                shape = expressiveGroupedShape(0, 3),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    calendarViewModel.setStartWeekOnMonday(it)
                                                }
                                            )

                                            ExpressiveSettingSwitchRow(
                                                title = "Event Day Dots",
                                                subtitle = "Highlight scheduled dates with subtle accent markers",
                                                checked = showDots,
                                                icon = Icons.Outlined.FiberManualRecord,
                                                shape = expressiveGroupedShape(1, 3),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    calendarViewModel.setShowEventDots(it)
                                                }
                                            )

                                            ExpressiveSettingSwitchRow(
                                                title = "24-Hour Timeline",
                                                subtitle = "Format event timestamps using 24-hour notation",
                                                checked = calUse24Hour,
                                                icon = Icons.Outlined.Schedule,
                                                shape = expressiveGroupedShape(2, 3),
                                                onCheckedChange = {
                                                    haptics.confirm()
                                                    calendarViewModel.setUse24HourFormat(it)
                                                }
                                            )
                                        }
                                    }
                                }

                                // 5. Music Tab (activeTab == 3)
                                if (activeTab == 3) {
                                    ExpressiveSectionCard {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.Album,
                                            title = "Player Visual Theme"
                                        )
                                        ExpressiveSettingSwitchRow(
                                            title = "Retro Vinyl Turntable",
                                            subtitle = "Switch between Material 3 Expressive player and Classic Vinyl deck",
                                            checked = isClassicMediaTheme,
                                            icon = Icons.Outlined.DiscFull,
                                            shape = expressiveGroupedShape(0, 1),
                                            onCheckedChange = {
                                                haptics.confirm()
                                                viewModel.setUseClassicMediaTheme(it)
                                            }
                                        )
                                    }
                                }

                                // 5b. Tools Tab (activeTab == 4): Separate Tool Screens Selector + Slideshow Settings
                                if (activeTab == 4) {
                                    val enabledToolSet by viewModel.enabledToolScreens.collectAsState()
                                    var isToolListExpanded by remember { mutableStateOf(false) }
                                    val showSlideshowTime by viewModel.showSlideshowTimeOverlay.collectAsState()
                                    val slideshowInterval by viewModel.slideshowIntervalSeconds.collectAsState()

                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("tool_screens_toggle_card")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable {
                                                    haptics.tick()
                                                    isToolListExpanded = !isToolListExpanded
                                                },
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
                                                        imageVector = Icons.Outlined.Build,
                                                        contentDescription = null,
                                                        tint = colorScheme.onPrimaryContainer,
                                                        modifier = Modifier
                                                            .padding(7.dp)
                                                            .size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Tool Screens (${enabledToolSet.size}/${viewModel.toolScreenTitles.size})",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (enabledToolSet.size < viewModel.toolScreenTitles.size) {
                                                    ExpressiveMorphButton(
                                                        onClick = {
                                                            haptics.tick()
                                                            viewModel.toolScreenTitles.indices.forEach { idx ->
                                                                viewModel.setToolScreenEnabled(idx, true)
                                                            }
                                                        },
                                                        selected = false,
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("enable_all_tool_screens_button")
                                                    ) {
                                                        Text("Enable All", style = MaterialTheme.typography.labelLarge)
                                                    }
                                                }
                                                ExpressiveMorphButton(
                                                    onClick = {
                                                        haptics.tick()
                                                        isToolListExpanded = !isToolListExpanded
                                                    },
                                                    selected = isToolListExpanded,
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(if (isToolListExpanded) "Hide" else "Customize", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        AnimatedVisibility(visible = isToolListExpanded) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 4.dp)
                                            ) {
                                                items(viewModel.toolScreenTitles.size) { idx ->
                                                    val isEnabled = enabledToolSet.contains(idx)
                                                    val canUncheck = !isEnabled || enabledToolSet.size > 1
                                                    val previewCorner by animateDpAsState(
                                                        targetValue = if (isEnabled) 18.dp else 12.dp,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                                        label = "toolPreviewCorner"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(width = 148.dp, height = 88.dp)
                                                            .clip(RoundedCornerShape(previewCorner))
                                                            .background(Color.Black)
                                                            .border(
                                                                width = if (isEnabled) 2.5.dp else 1.dp,
                                                                color = if (isEnabled) colorScheme.primary else colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(previewCorner)
                                                            )
                                                            .clickable {
                                                                if (canUncheck) {
                                                                    haptics.tick()
                                                                    viewModel.toggleToolScreen(idx)
                                                                }
                                                            }
                                                            .testTag("tool_screen_toggle_row_$idx"),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .requiredSize(680.dp, 420.dp)
                                                                .graphicsLayer {
                                                                    scaleX = 0.20f
                                                                    scaleY = 0.20f
                                                                }
                                                        ) {
                                                            ToolScreenRenderer(toolIndex = idx, viewModel = viewModel)
                                                        }
                                                        Surface(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(6.dp)
                                                                .size(22.dp),
                                                            shape = CircleShape,
                                                            color = if (isEnabled) colorScheme.primary else colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    imageVector = if (isEnabled) Icons.Default.Check else Icons.Default.Add,
                                                                    contentDescription = null,
                                                                    tint = if (isEnabled) colorScheme.onPrimary else colorScheme.onSurface,
                                                                    modifier = Modifier.size(13.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("tools_slideshow_settings_card")
                                    ) {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.Slideshow,
                                            title = "Slideshow"
                                        )

                                        ExpressiveSettingSwitchRow(
                                            title = "Show Time Overlay",
                                            subtitle = "Display live clock time over the full-screen immersive photo slideshow",
                                            checked = showSlideshowTime,
                                            icon = Icons.Outlined.AccessTime,
                                            shape = expressiveGroupedShape(0, 1),
                                            onCheckedChange = {
                                                haptics.confirm()
                                                viewModel.setShowSlideshowTimeOverlay(it)
                                            },
                                            modifier = Modifier.testTag("slideshow_time_overlay_switch")
                                        )

                                        val speedOptions = listOf(5, 8, 15, 30)
                                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                            speedOptions.forEachIndexed { idx, secs ->
                                                SegmentedButton(
                                                    selected = slideshowInterval == secs,
                                                    onClick = {
                                                        haptics.tick()
                                                        viewModel.setSlideshowIntervalSeconds(secs)
                                                    },
                                                    shape = SegmentedButtonDefaults.itemShape(
                                                        index = idx,
                                                        count = speedOptions.size
                                                    )
                                                ) {
                                                    Text("${secs}s", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 6. Duo Tab (activeTab == 5): Visual-Only Left & Right Pane Screen Previews + Plus Picker
                                if (activeTab == 5) {
                                    val leftWidgetIdx by viewModel.duoLeftWidgetIndex.collectAsState()
                                    val rightWidgetIdx by viewModel.duoRightWidgetIndex.collectAsState()
                                    val leftScreens by viewModel.duoLeftScreenIndices.collectAsState()
                                    val rightScreens by viewModel.duoRightScreenIndices.collectAsState()
                                    val leftTzId by viewModel.duoLeftTimeZoneId.collectAsState()
                                    val rightTzId by viewModel.duoRightTimeZoneId.collectAsState()
                                    val leftLocName by viewModel.duoLeftLocationName.collectAsState()
                                    val rightLocName by viewModel.duoRightLocationName.collectAsState()
                                    val leftResolved = remember(leftTzId, leftLocName) { viewModel.resolvePaneLocationInfo(1) }
                                    val rightResolved = remember(rightTzId, rightLocName) { viewModel.resolvePaneLocationInfo(2) }
                                    var visualPickerPane by remember { mutableIntStateOf(0) } // 0 = closed, 1 = left, 2 = right

                                    ExpressiveSectionCard(
                                        modifier = Modifier.testTag("duo_widgets_selector_card")
                                    ) {
                                        ExpressiveSectionHeader(
                                            icon = Icons.Outlined.ViewColumn,
                                            title = "Duo Split Panes"
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            // LEFT PANE VISUAL-ONLY CONFIGURATION
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(22.dp),
                                                color = colorScheme.surfaceContainer,
                                                border = expressiveSurfaceBorder()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ViewSidebar,
                                                            contentDescription = "Left Pane",
                                                            tint = colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        ExpressiveIconActionButton(
                                                            onClick = {
                                                                haptics.confirm()
                                                                visualPickerPane = 1
                                                            },
                                                            icon = Icons.Default.Add,
                                                            contentDescription = "Add Left Screen",
                                                            size = 36.dp,
                                                            iconSize = 18.dp,
                                                            containerColor = colorScheme.primaryContainer,
                                                            contentColor = colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.testTag("duo_settings_left_add_btn")
                                                        )
                                                    }

                                                    if (leftScreens.isEmpty()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(92.dp)
                                                                .clip(RoundedCornerShape(18.dp))
                                                                .background(colorScheme.surfaceContainerHigh)
                                                                .border(expressiveSurfaceBorder(), RoundedCornerShape(18.dp))
                                                                .clickable {
                                                                    haptics.confirm()
                                                                    visualPickerPane = 1
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Add Left Screen",
                                                                tint = colorScheme.primary,
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                        }
                                                    } else {
                                                        LazyRow(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            itemsIndexed(leftScreens) { _, sIdx ->
                                                                val isActive = leftWidgetIdx == sIdx
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(width = 132.dp, height = 88.dp)
                                                                        .clip(RoundedCornerShape(16.dp))
                                                                        .background(Color.Black)
                                                                        .border(
                                                                            width = if (isActive) 2.5.dp else 1.dp,
                                                                            color = if (isActive) colorScheme.primary else colorScheme.outlineVariant,
                                                                            shape = RoundedCornerShape(16.dp)
                                                                        )
                                                                        .clickable {
                                                                            haptics.tick()
                                                                            viewModel.setDuoLeftWidgetIndex(sIdx)
                                                                        }
                                                                ) {
                                                                    RenderDuoWidget(
                                                                        pageIndex = sIdx,
                                                                        paneIndex = 1,
                                                                        viewModel = viewModel,
                                                                        calendarViewModel = calendarViewModel,
                                                                        weatherViewModel = viewModel()
                                                                    )
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopEnd)
                                                                            .padding(4.dp)
                                                                            .size(20.dp)
                                                                            .clickable {
                                                                                haptics.tick()
                                                                                viewModel.removeScreenFromDuoLeft(sIdx)
                                                                            },
                                                                        shape = CircleShape,
                                                                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                                                                    ) {
                                                                        Box(contentAlignment = Alignment.Center) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Close,
                                                                                contentDescription = "Remove",
                                                                                tint = colorScheme.onSurface,
                                                                                modifier = Modifier.size(12.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    SearchableLocationTimeZoneField(
                                                        viewModel = viewModel,
                                                        paneIndex = 1,
                                                        activeSummary = "${leftResolved.regionName} (${leftResolved.airportCode}) • ${leftResolved.standardTimeShort}",
                                                        label = "Left Location / Time Zone",
                                                        testTag = "duo_left_location_search_input"
                                                    )
                                                }
                                            }

                                            // RIGHT PANE VISUAL-ONLY CONFIGURATION
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(22.dp),
                                                color = colorScheme.surfaceContainer,
                                                border = expressiveSurfaceBorder()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.ViewSidebar,
                                                            contentDescription = "Right Pane",
                                                            tint = colorScheme.primary,
                                                            modifier = Modifier
                                                                .size(18.dp)
                                                                .graphicsLayer { scaleX = -1f }
                                                        )
                                                        ExpressiveIconActionButton(
                                                            onClick = {
                                                                haptics.confirm()
                                                                visualPickerPane = 2
                                                            },
                                                            icon = Icons.Default.Add,
                                                            contentDescription = "Add Right Screen",
                                                            size = 36.dp,
                                                            iconSize = 18.dp,
                                                            containerColor = colorScheme.primaryContainer,
                                                            contentColor = colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.testTag("duo_settings_right_add_btn")
                                                        )
                                                    }

                                                    if (rightScreens.isEmpty()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(92.dp)
                                                                .clip(RoundedCornerShape(18.dp))
                                                                .background(colorScheme.surfaceContainerHigh)
                                                                .border(expressiveSurfaceBorder(), RoundedCornerShape(18.dp))
                                                                .clickable {
                                                                    haptics.confirm()
                                                                    visualPickerPane = 2
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "Add Right Screen",
                                                                tint = colorScheme.primary,
                                                                modifier = Modifier.size(32.dp)
                                                            )
                                                        }
                                                    } else {
                                                        LazyRow(
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            itemsIndexed(rightScreens) { _, sIdx ->
                                                                val isActive = rightWidgetIdx == sIdx
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(width = 132.dp, height = 88.dp)
                                                                        .clip(RoundedCornerShape(16.dp))
                                                                        .background(Color.Black)
                                                                        .border(
                                                                            width = if (isActive) 2.5.dp else 1.dp,
                                                                            color = if (isActive) colorScheme.primary else colorScheme.outlineVariant,
                                                                            shape = RoundedCornerShape(16.dp)
                                                                        )
                                                                        .clickable {
                                                                            haptics.tick()
                                                                            viewModel.setDuoRightWidgetIndex(sIdx)
                                                                        }
                                                                ) {
                                                                    RenderDuoWidget(
                                                                        pageIndex = sIdx,
                                                                        paneIndex = 2,
                                                                        viewModel = viewModel,
                                                                        calendarViewModel = calendarViewModel,
                                                                        weatherViewModel = viewModel()
                                                                    )
                                                                    Surface(
                                                                        modifier = Modifier
                                                                            .align(Alignment.TopEnd)
                                                                            .padding(4.dp)
                                                                            .size(20.dp)
                                                                            .clickable {
                                                                                haptics.tick()
                                                                                viewModel.removeScreenFromDuoRight(sIdx)
                                                                            },
                                                                        shape = CircleShape,
                                                                        color = colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                                                                    ) {
                                                                        Box(contentAlignment = Alignment.Center) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.Close,
                                                                                contentDescription = "Remove",
                                                                                tint = colorScheme.onSurface,
                                                                                modifier = Modifier.size(12.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    SearchableLocationTimeZoneField(
                                                        viewModel = viewModel,
                                                        paneIndex = 2,
                                                        activeSummary = "${rightResolved.regionName} (${rightResolved.airportCode}) • ${rightResolved.standardTimeShort}",
                                                        label = "Right Location / Time Zone",
                                                        testTag = "duo_right_location_search_input"
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (visualPickerPane != 0) {
                                        DuoPaneScreenManagerDialog(
                                            paneIndex = visualPickerPane,
                                            viewModel = viewModel,
                                            calendarViewModel = calendarViewModel,
                                            onDismiss = { visualPickerPane = 0 }
                                        )
                                    }
                                }

                                // 7. Custom Backdrop Image Card (Expressive Card)
                                val context = LocalContext.current
                                val launcher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.GetContent()
                                ) { uri ->
                                    if (uri != null) {
                                        try {
                                            context.contentResolver.takePersistableUriPermission(
                                                uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            )
                                        } catch (_: Exception) {
                                        }
                                        viewModel.setBgUriForPage(activeTab, uri.toString())
                                    }
                                }

                                ExpressiveSectionCard {
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
                                                    imageVector = Icons.Outlined.Wallpaper,
                                                    contentDescription = null,
                                                    tint = colorScheme.onPrimaryContainer,
                                                    modifier = Modifier
                                                        .padding(7.dp)
                                                        .size(18.dp)
                                                )
                                            }
                                            Text(
                                                text = "Backdrop Photo",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (bgUri != null) {
                                                OutlinedButton(
                                                    onClick = {
                                                        haptics.tick()
                                                        viewModel.setBgUriForPage(activeTab, null)
                                                    },
                                                    shape = RoundedCornerShape(18.dp)
                                                ) {
                                                    Text("Clear", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            ExpressiveMorphButton(
                                                onClick = {
                                                    haptics.tick()
                                                    launcher.launch("image/*")
                                                },
                                                selected = bgUri != null,
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Image,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Choose Photo", fontWeight = FontWeight.SemiBold)
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
fun AppOverviewLayout(
    viewModel: StandbyViewModel,
    activePage: Int,
    activeClockFace: Int,
    activeToolScreen: Int = 0,
    onNavigateToPage: (Int) -> Unit,
    onSelectClockFace: (Int) -> Unit,
    onSelectToolScreen: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val color0Idx by viewModel.colorPage0.collectAsState()
    val color1Idx by viewModel.colorPage1.collectAsState()
    val color2Idx by viewModel.colorPage2.collectAsState()
    val color3Idx by viewModel.colorPage3.collectAsState()
    val color4Idx by viewModel.colorPage4.collectAsState()

    val color0 = viewModel.colors[color0Idx].first
    val color1 = viewModel.colors[color1Idx].first
    val color2 = viewModel.colors[color2Idx].first
    val color3 = viewModel.colors[color3Idx].first
    val color4 = viewModel.colors[color4Idx].first

    val font0Idx by viewModel.fontPage0.collectAsState()
    val font1Idx by viewModel.fontPage1.collectAsState()
    val font2Idx by viewModel.fontPage2.collectAsState()
    val font3Idx by viewModel.fontPage3.collectAsState()
    val font4Idx by viewModel.fontPage4.collectAsState()

    val font0 = viewModel.fonts[font0Idx].first
    val font1 = viewModel.fonts[font1Idx].first
    val font2 = viewModel.fonts[font2Idx].first
    val font3 = viewModel.fonts[font3Idx].first
    val font4 = viewModel.fonts[font4Idx].first

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.15f) {
                        onDismiss()
                    }
                }
            },
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Standby Spaces",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )

                ExpressiveIconActionButton(
                    onClick = onDismiss,
                    icon = Icons.Default.Close,
                    contentDescription = "Close Overview",
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("overview_close_button")
                )
            }

            val enabledClockSet by viewModel.enabledClockFaces.collectAsState()
            val clockThemes = remember(enabledClockSet) {
                viewModel.clockFaceTitles.mapIndexedNotNull { idx, title ->
                    val subtitle = viewModel.clockFaceSubtitles.getOrElse(idx) { title }
                    if (enabledClockSet.contains(idx)) Triple(idx, title, subtitle) else null
                }
            }

            val utilityPages = listOf(
                1 to "Alarm",
                2 to "Calendar",
                3 to "Music",
                4 to "Tools",
                5 to "Duo"
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Column 1: Enabled Clock Faces (Visual-only cards)
                item {
                    Surface(
                        modifier = Modifier
                            .width(196.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(28.dp),
                        color = colorScheme.surfaceContainerLow,
                        border = expressiveSurfaceBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Clock",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                itemsIndexed(clockThemes) { _, (clockThemeIdx, _, _) ->
                                    val isActive = (activePage == 0 && activeClockFace == clockThemeIdx)

                                    Surface(
                                        onClick = { onSelectClockFace(clockThemeIdx) },
                                        shape = RoundedCornerShape(18.dp),
                                        color = if (isActive) colorScheme.secondaryContainer else colorScheme.surfaceContainer,
                                        border = BorderStroke(
                                            width = if (isActive) 2.5.dp else 1.dp,
                                            color = if (isActive) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(160.dp, 90.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .requiredSize(400.dp, 220.dp)
                                                    .graphicsLayer {
                                                        scaleX = 0.36f
                                                        scaleY = 0.36f
                                                    }
                                            ) {
                                                ClockPage(themeIndex = clockThemeIdx)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Columns 2..6: Utility Screens (Tools column shows separate enabled Tool Screen cards just like Clock Faces)
                itemsIndexed(utilityPages) { _, (pageIdx, title) ->
                    val isActive = (activePage == pageIdx)

                    val pageColor = when (pageIdx) {
                        1 -> color1
                        2 -> color2
                        3 -> color3
                        4 -> color4
                        else -> colorScheme.primary
                    }

                    if (pageIdx == 4) {
                        val enabledToolSet by viewModel.enabledToolScreens.collectAsState()
                        val toolScreens = remember(enabledToolSet) {
                            viewModel.toolScreenTitles.indices.filter { enabledToolSet.contains(it) }
                                .ifEmpty { listOf(0) }
                        }

                        Surface(
                            modifier = Modifier
                                .width(196.dp)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(28.dp),
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) pageColor else colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp)
                                ) {
                                    itemsIndexed(toolScreens) { _, toolIdx ->
                                        val isToolActive = (activePage == 4 && activeToolScreen == toolIdx)

                                        Surface(
                                            onClick = { onSelectToolScreen(toolIdx) },
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (isToolActive) colorScheme.secondaryContainer else colorScheme.surfaceContainer,
                                            border = BorderStroke(
                                                width = if (isToolActive) 2.5.dp else 1.dp,
                                                color = if (isToolActive) pageColor else colorScheme.outlineVariant.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.testTag("overview_tool_card_$toolIdx")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(160.dp, 90.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.Black),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .requiredSize(680.dp, 420.dp)
                                                        .graphicsLayer {
                                                            scaleX = 0.21f
                                                            scaleY = 0.21f
                                                        }
                                                ) {
                                                    ToolScreenRenderer(
                                                        toolIndex = toolIdx,
                                                        viewModel = viewModel
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = { onNavigateToPage(pageIdx) },
                            modifier = Modifier
                                .width(196.dp)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(28.dp),
                            color = if (isActive) colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            else colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (isActive) 2.dp else 1.dp,
                                color = if (isActive) pageColor else colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(400.dp, 220.dp)
                                            .graphicsLayer {
                                                scaleX = 0.36f
                                                scaleY = 0.36f
                                            }
                                    ) {
                                        when (pageIdx) {
                                            1 -> PreviewAlarmPage(accentColor = color1, fontFamily = font1)
                                            2 -> PreviewCalendarPage(accentColor = color2, fontFamily = font2)
                                            3 -> PreviewMediaPage(accentColor = color3, fontFamily = font3)
                                            5 -> PreviewDuoPage(fontFamily = font0)
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
fun PreviewAlarmPage(accentColor: Color, fontFamily: FontFamily) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "07:30",
                color = colorScheme.onSurface,
                fontSize = 42.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "WAKE UP ALARM",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
fun PreviewCalendarPage(accentColor: Color, fontFamily: FontFamily) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TODAY",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "24",
                color = colorScheme.onSurface,
                fontSize = 44.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Column(
            modifier = Modifier
                .weight(0.55f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "UPCOMING",
                color = colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Design Sync",
                color = colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "10:00 AM",
                color = accentColor,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun PreviewMediaPage(accentColor: Color, fontFamily: FontFamily) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(84.dp),
            shape = RoundedCornerShape(16.dp),
            color = accentColor.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Now Playing",
                color = colorScheme.onSurface,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "Material You Media",
                color = colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PreviewTimerPage(accentColor: Color, fontFamily: FontFamily) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "25:00",
                color = colorScheme.onSurface,
                fontSize = 36.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "POMODORO FOCUS",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PreviewDuoPage(fontFamily: FontFamily) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surfaceContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "10:24",
                    color = colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(14.dp),
            color = colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Agenda",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SearchableLocationTimeZoneField(
    viewModel: StandbyViewModel,
    paneIndex: Int,
    activeSummary: String,
    label: String,
    testTag: String = "searchable_location_field",
    onSelectionApplied: (() -> Unit)? = null
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    var searchQuery by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    val results = remember(searchQuery) {
        viewModel.searchLocationsAndTimeZones(searchQuery).take(5)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { text ->
                searchQuery = text
                showSuggestions = true
                if (text.trim().length >= 2) {
                    viewModel.applySearchedLocationSelection(
                        selectionKey = "",
                        rawQueryFallback = text.trim(),
                        pane = paneIndex
                    )
                }
            },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text(activeSummary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Location",
                    tint = colorScheme.primary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                searchQuery = ""
                                showSuggestions = false
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                        }
                    } else {
                        IconButton(
                            onClick = {
                                haptics.tick()
                                showSuggestions = !showSuggestions
                            }
                        ) {
                            Icon(
                                imageVector = if (showSuggestions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Suggestions"
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        )

        AnimatedVisibility(visible = showSuggestions || searchQuery.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = if (com.example.ui.theme.LocalPureAmoled.current) 0.dp else 3.dp,
                border = expressiveSurfaceBorder()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    results.forEachIndexed { idx, (selectionKey, displayDesc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptics.tick()
                                    viewModel.applySearchedLocationSelection(
                                        selectionKey = selectionKey,
                                        rawQueryFallback = searchQuery,
                                        pane = paneIndex
                                    )
                                    searchQuery = ""
                                    showSuggestions = false
                                    onSelectionApplied?.invoke()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("${testTag}_result_$idx"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (selectionKey == "AUTO") Icons.Default.MyLocation else Icons.Default.Public,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = displayDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (idx < results.lastIndex) {
                            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.25f))
                        }
                    }
                }
            }
        }
    }
}

