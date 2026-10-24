package com.example.ui.standby

import android.content.Intent
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

    val initialClockFace = remember { viewModel.lastClockFaceIndex.value }
    val horizontalPagerState = rememberPagerState(pageCount = { 6 })
    val verticalPagerState = rememberPagerState(
        initialPage = initialClockFace,
        pageCount = { 10 }
    )

    var isHudVisible by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isOverviewMode by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }

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
    LaunchedEffect(verticalPagerState.currentPage) {
        if (!hasInitializedVertical) {
            hasInitializedVertical = true
        } else {
            haptics.tick()
            viewModel.setLastClockFaceIndex(verticalPagerState.currentPage)
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
                        ) { clockFaceIndex ->
                            ClockPage(themeIndex = clockFaceIndex)
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
                        currentIndex = verticalPagerState.currentPage,
                        pageCount = 10,
                        onDotClicked = { selectedIdx ->
                            haptics.tick()
                            viewModel.setLastClockFaceIndex(selectedIdx)
                            isHudVisible = true
                            coroutineScope.launch {
                                verticalPagerState.animateScrollToPage(selectedIdx)
                            }
                        }
                    )
                }
            }

            // Material 3 HUD Floating Action Controls (Lock, Overview Directory, Studio Settings)
            AnimatedVisibility(
                visible = isHudVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.confirm()
                                isScreenLocked = true
                                isHudVisible = false
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("hud_lock_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Lock Screen inputs",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                isHudVisible = true
                                isOverviewMode = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("hud_overview_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colorScheme.secondaryContainer,
                                contentColor = colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.GridView,
                                contentDescription = "Standby Layout Overview",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                haptics.tick()
                                isHudVisible = true
                                showSettingsDialog = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("hud_settings_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Customize Standby Menu",
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
            AppOverviewLayout(
                viewModel = viewModel,
                activePage = horizontalPagerState.currentPage,
                activeClockFace = verticalPagerState.currentPage,
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
                    viewModel.setLastClockFaceIndex(themeIdx)
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(0)
                        verticalPagerState.scrollToPage(themeIdx)
                    }
                },
                onDismiss = {
                    haptics.tick()
                    isOverviewMode = false
                }
            )
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
                                                        val nextVal = (dragAmount.value + dragAmountOffset.x).coerceIn(0f, maxDragOffsetPx)
                                                        dragAmount.snapTo(nextVal)
                                                        val bucket = ((nextVal / maxDragOffsetPx) * 8f).toInt()
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
        "Clock" to Icons.Outlined.Schedule,
        "Alarm" to Icons.Outlined.Alarm,
        "Calendar" to Icons.Outlined.CalendarToday,
        "Music" to Icons.Outlined.MusicNote,
        "Timer" to Icons.Outlined.Timer,
        "Duo" to Icons.Outlined.ViewColumn
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val isCompactWidth = maxWidth < 600.dp
        val itemHPad = if (isCompactWidth) 10.dp else 14.dp
        val itemVPad = if (isCompactWidth) 6.dp else 8.dp

        Surface(
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, (title, icon) ->
                    val isActive = currentPage == index
                    val containerColor by animateColorAsState(
                        targetValue = if (isActive) colorScheme.secondaryContainer else Color.Transparent,
                        label = "navPillBg"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isActive) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                        label = "navPillFg"
                    )

                    Surface(
                        shape = CircleShape,
                        color = containerColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onPageSelected(index) }
                            .testTag("top_nav_tab_$index")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = itemHPad, vertical = itemVPad),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = title,
                                color = contentColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
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
            for (i in 0 until pageCount) {
                val isSelected = i == currentIndex
                val height by animateDpAsState(targetValue = if (isSelected) 15.dp else 6.dp, label = "dotHeight")
                Box(
                    modifier = Modifier
                        .size(width = 6.dp, height = height)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) colorScheme.primary
                            else colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
    val colorScheme = MaterialTheme.colorScheme
    var activeTab by remember { mutableIntStateOf(currentPage.coerceIn(0, 5)) }

    val tabItems = listOf(
        Triple("Clock", "Main Display", Icons.Outlined.Schedule),
        Triple("Alarm", "Alert Sync", Icons.Outlined.Alarm),
        Triple("Calendar", "Monthly Agenda", Icons.Outlined.CalendarToday),
        Triple("Music", "Media Player", Icons.Outlined.MusicNote),
        Triple("Timer", "Focus & Laps", Icons.Outlined.Timer),
        Triple("Duo", "Split Widgets", Icons.Outlined.ViewColumn)
    )

    val selectedColorIdx by viewModel.getColorFlow(activeTab).collectAsState()
    val selectedFontIdx by viewModel.getFontFlow(activeTab).collectAsState()
    val clockScaleVal by viewModel.getScaleFlow(activeTab).collectAsState()
    val animationsEnabledVal by viewModel.getAnimFlow(activeTab).collectAsState()

    val glowVal by viewModel.getGlowFlow(activeTab).collectAsState()
    val outlineVal by viewModel.getOutlineFlow(activeTab).collectAsState()
    val gradientIdx by viewModel.getGradientFlow(activeTab).collectAsState()
    val bgUri by viewModel.getBgUriFlow(activeTab).collectAsState()

    val use24Hour by viewModel.use24HourFormat.collectAsState()
    val showAmPm by viewModel.showAmPm.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()
    val isClassicMediaTheme by viewModel.useClassicMediaTheme.collectAsState()

    val pageColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = colorScheme.surface
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactHeight = maxHeight < 380.dp
                val isCompactWidth = maxWidth < 620.dp
                val railWidth = if (isCompactWidth) 88.dp else 108.dp
                val contentHPad = if (isCompactWidth) 16.dp else 28.dp
                val contentVPad = if (isCompactHeight) 12.dp else 20.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    // LEFT PANE: Material 3 NavigationRail optimized for landscape ergonomics
                    NavigationRail(
                        containerColor = colorScheme.surfaceContainerLow,
                        contentColor = colorScheme.onSurface,
                        header = {
                            if (!isCompactHeight) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 8.dp, end = 8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Studio Hub",
                                            tint = colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Studio",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Studio",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(railWidth)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                                            maxLines = 1
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    modifier = Modifier.testTag("studio_rail_tab_$idx")
                                )
                            }
                        }
                    }

                    VerticalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // RIGHT PANE: Material 3 Tonal Settings Deck
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = contentHPad, vertical = contentVPad)
                    ) {
                        // Header Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = if (isCompactHeight) 10.dp else 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = pageColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, pageColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "0${activeTab + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "${tabItems[activeTab].first} Settings",
                                        style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tabItems[activeTab].second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("studio_apply_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Done")
                            }
                        }

                        // Scrollable Material 3 Cards
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 12.dp else 16.dp)
                        ) {
                        // 1. Interface Color Accent Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Accent Color Palette",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = "Saved automatically for ${tabItems[activeTab].first}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 14.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    viewModel.colors.forEachIndexed { index, (color, name) ->
                                        val isSelected = index == selectedColorIdx
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptics.tick()
                                                    viewModel.setColorIndexForPage(activeTab, index)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 3.dp else 1.dp,
                                                        color = if (isSelected) colorScheme.onSurface else colorScheme.outlineVariant,
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = if (color == Color.White) Color.Black else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = name.split(" ").lastOrNull() ?: name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Typography Style Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Typography Style",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    viewModel.fonts.forEachIndexed { index, (fontFamily, name) ->
                                        val isSelected = index == selectedFontIdx
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptics.tick()
                                                viewModel.setFontIndexForPage(activeTab, index)
                                            },
                                            label = {
                                                Text(
                                                    text = name,
                                                    fontFamily = fontFamily,
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                            },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Clock Scale & Format Preferences (Clock Tab 0 or Duo Tab 5)
                        if (activeTab == 0 || activeTab == 5) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (activeTab == 0) {
                                        var lastScaleBucket by remember { mutableIntStateOf((clockScaleVal * 10).roundToInt()) }
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Clock Display Scale",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colorScheme.onSurface
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.getDefault(), "%.1fx", clockScaleVal),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
                                    }

                                    M3SettingSwitchRow(
                                        title = "24-Hour Time Format",
                                        subtitle = "Display hours in 24-hour military format (e.g. 14:30)",
                                        checked = use24Hour,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            viewModel.setUse24HourFormat(it)
                                        }
                                    )

                                    AnimatedVisibility(visible = !use24Hour) {
                                        M3SettingSwitchRow(
                                            title = "Show AM / PM Indicator",
                                            subtitle = "Display meridian badge alongside 12-hour clocks",
                                            checked = showAmPm,
                                            onCheckedChange = {
                                                haptics.confirm()
                                                viewModel.setShowAmPm(it)
                                            }
                                        )
                                    }

                                    M3SettingSwitchRow(
                                        title = "Display Seconds Tracker",
                                        subtitle = "Show live ticking seconds on compatible clock faces",
                                        checked = showSeconds,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            viewModel.setShowSeconds(it)
                                        }
                                    )
                                }
                            }
                        }

                        // 4. Calendar Specific Settings (Tab 2)
                        if (activeTab == 2) {
                            val calendarFilter by calendarViewModel.calendarFilter.collectAsState()
                            val startOnMon by calendarViewModel.startWeekOnMonday.collectAsState()
                            val showDots by calendarViewModel.showEventDots.collectAsState()
                            val calUse24Hour by calendarViewModel.use24HourFormat.collectAsState()

                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Text(
                                        text = "Calendar Agenda & Grid",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface
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
                                                Text(label)
                                            }
                                        }
                                    }

                                    M3SettingSwitchRow(
                                        title = "Start Week on Monday",
                                        subtitle = "Align monthly calendar grid weeks starting Monday",
                                        checked = startOnMon,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            calendarViewModel.setStartWeekOnMonday(it)
                                        }
                                    )

                                    M3SettingSwitchRow(
                                        title = "Show Event Day Dots",
                                        subtitle = "Highlight scheduled dates with subtle accent markers",
                                        checked = showDots,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            calendarViewModel.setShowEventDots(it)
                                        }
                                    )

                                    M3SettingSwitchRow(
                                        title = "24-Hour Agenda Timeline",
                                        subtitle = "Format event timestamps using 24-hour notation",
                                        checked = calUse24Hour,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            calendarViewModel.setUse24HourFormat(it)
                                        }
                                    )
                                }
                            }
                        }

                        // 5. Music Player Theme Mode (Tab 3)
                        if (activeTab == 3) {
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = colorScheme.surfaceContainerLow
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Player Visual Style",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface
                                    )
                                    M3SettingSwitchRow(
                                        title = "Cinematic Retro Turntable Theme",
                                        subtitle = "Switch between Material 3 Expressive player and Classic Vinyl deck",
                                        checked = isClassicMediaTheme,
                                        onCheckedChange = {
                                            haptics.confirm()
                                            viewModel.setUseClassicMediaTheme(it)
                                        }
                                    )
                                }
                            }
                        }

                        // 6. Advanced Styling & Gradients Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Advanced Text & Motion Styling",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colorScheme.onSurface
                                )

                                M3SettingSwitchRow(
                                    title = "Ambient Text Glow",
                                    subtitle = "Render soft tonal luminance around display numerals",
                                    checked = glowVal,
                                    onCheckedChange = {
                                        haptics.confirm()
                                        viewModel.setTextGlowForPage(activeTab, it)
                                    }
                                )

                                M3SettingSwitchRow(
                                    title = "Stroke Outline Mode",
                                    subtitle = "Draw hollow architectural strokes instead of solid fills",
                                    checked = outlineVal,
                                    onCheckedChange = {
                                        haptics.confirm()
                                        viewModel.setTextOutlineForPage(activeTab, it)
                                    }
                                )

                                M3SettingSwitchRow(
                                    title = "Distraction-Free Motion",
                                    subtitle = "Calm down secondary animations for deep focus environments",
                                    checked = !animationsEnabledVal,
                                    onCheckedChange = { isDistractionFree ->
                                        haptics.confirm()
                                        viewModel.setAnimationsEnabledForPage(activeTab, !isDistractionFree)
                                    }
                                )

                                Text(
                                    text = "Text Shader Gradient",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(viewModel.gradients.size) { index ->
                                        val (gradName, gradColors) = viewModel.gradients[index]
                                        val isSelected = (index == gradientIdx) || (index == 0 && gradientIdx <= 0)
                                        val brush = if (index == 0) {
                                            Brush.linearGradient(
                                                listOf(colorScheme.surfaceContainerHighest, colorScheme.surfaceContainerHigh)
                                            )
                                        } else {
                                            Brush.linearGradient(gradColors)
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptics.tick()
                                                    viewModel.setTextGradientForPage(activeTab, if (index == 0) -1 else index)
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 68.dp, height = 40.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(brush)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = if (index == 0) colorScheme.onSurface else Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = gradName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) colorScheme.onSurface else colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 7. Custom Background Image Card
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

                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = colorScheme.surfaceContainerLow
                            )
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp)
                                ) {
                                    Text(
                                        text = "Custom Backdrop Image",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (bgUri != null) "Custom wallpaper active for this screen"
                                        else "Choose a local photo backdrop for ${tabItems[activeTab].first}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (bgUri != null) colorScheme.primary else colorScheme.onSurfaceVariant
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
                                            }
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.tick()
                                            launcher.launch("image/*")
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Choose Photo")
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
private fun M3SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun AppOverviewLayout(
    viewModel: StandbyViewModel,
    activePage: Int,
    activeClockFace: Int,
    onNavigateToPage: (Int) -> Unit,
    onSelectClockFace: (Int) -> Unit,
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
                Column {
                    Text(
                        text = "Standby Spaces",
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select any clock face or utility workspace",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("overview_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Overview"
                    )
                }
            }

            val clockThemes = listOf(
                "Minimal Digital Clock" to "01 | DIGITAL MINIMAL",
                "Classic Analog Clock" to "02 | ANALOG CLASSIC",
                "Retro Flip Card Clock" to "03 | RETRO FLIP CARD",
                "Binary Dots Matrix" to "04 | BINARY STYLE",
                "Modern Bold Typography" to "05 | MODERN BOLD TEXT",
                "Contrasting Split Color" to "06 | CONTRASTING SPLIT",
                "Analog Motor Dashboard" to "07 | ANALOG DASHBOARD",
                "Pastel Bubbles Clock" to "08 | BUBBLE PASTEL",
                "Ambient Horizon Gradient" to "09 | AMBIENT GRADIENT",
                "High-Tech Nixie Tube" to "10 | NIXIE TUBE"
            )

            val utilityPages = listOf(
                1 to ("System Alarm" to "ALARM DISPATCH"),
                2 to ("Calendar Agenda" to "CALENDAR AGENDA"),
                3 to ("Music Player" to "MUSIC PLAYER"),
                4 to ("Timer Focus Matrix" to "TIMER FOCUS"),
                5 to ("Duo Split Views" to "DUO WIDGETS")
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Column 1: 10 Clock Faces
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .width(196.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Clock Faces",
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "10 interactive styles",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                itemsIndexed(clockThemes) { clockThemeIdx, (_, label) ->
                                    val isActive = (activePage == 0 && activeClockFace == clockThemeIdx)

                                    OutlinedCard(
                                        onClick = { onSelectClockFace(clockThemeIdx) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.outlinedCardColors(
                                            containerColor = if (isActive) colorScheme.secondaryContainer else colorScheme.surfaceContainer
                                        ),
                                        border = BorderStroke(
                                            width = if (isActive) 2.dp else 1.dp,
                                            color = if (isActive) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = label.substringAfter("| ").trim(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) colorScheme.onSecondaryContainer else colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(156.dp, 84.dp)
                                                    .clip(RoundedCornerShape(10.dp))
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
                }

                // Columns 2..6: Utility Screens
                itemsIndexed(utilityPages) { _, uItem ->
                    val pageIdx = uItem.first
                    val (title, label) = uItem.second
                    val isActive = (activePage == pageIdx)

                    val pageColor = when (pageIdx) {
                        1 -> color1
                        2 -> color2
                        3 -> color3
                        4 -> color4
                        else -> colorScheme.primary
                    }

                    ElevatedCard(
                        onClick = { onNavigateToPage(pageIdx) },
                        modifier = Modifier
                            .width(196.dp)
                            .fillMaxHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isActive) colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            else colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) pageColor else colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(12.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isActive) colorScheme.onSurface else colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
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
                                        4 -> PreviewTimerPage(accentColor = color4, fontFamily = font4)
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
