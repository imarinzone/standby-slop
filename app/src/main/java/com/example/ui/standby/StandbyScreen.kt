package com.example.ui.standby

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StandbyScreen(
    modifier: Modifier = Modifier,
    viewModel: StandbyViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val horizontalPagerState = rememberPagerState(pageCount = { 6 })
    val verticalPagerState = rememberPagerState(pageCount = { 12 })

    var isHudVisible by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isOverviewMode by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }

    // Auto-hide HUD after 5 seconds of inactivity
    LaunchedEffect(isHudVisible) {
        if (isHudVisible) {
            delay(5000)
            isHudVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isOverviewMode, isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom < 0.82f && !isOverviewMode) {
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
        if (!isOverviewMode) {
            // Horizontal pages
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
                    2 -> CalendarPage()
                    3 -> MediaPage()
                    4 -> TimerPage(viewModel = viewModel)
                    5 -> DuoPage(viewModel = viewModel)
                }
            }

            // Overlay top-bar: screen names (hides after inaction)
            AnimatedVisibility(
                visible = isHudVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBarOverlay(
                    currentPage = horizontalPagerState.currentPage,
                    onPageSelected = { targetPage ->
                        isHudVisible = true
                        coroutineScope.launch {
                            horizontalPagerState.animateScrollToPage(targetPage)
                        }
                    }
                )
            }

            // Overlay right side: Vertical dots representing the clock themes (displays only on page 0)
            if (horizontalPagerState.currentPage == 0) {
                AnimatedVisibility(
                    visible = isHudVisible,
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    VerticalThemeIndicator(
                        currentIndex = verticalPagerState.currentPage,
                        pageCount = 12,
                        onDotClicked = { selectedIdx ->
                            isHudVisible = true
                            coroutineScope.launch {
                                verticalPagerState.animateScrollToPage(selectedIdx)
                            }
                        }
                    )
                }
            }

            // HUD Action Buttons (Grid Layout Overview and Customizer Settings)
            AnimatedVisibility(
                visible = isHudVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Accidental touch Lock Screen button
                    IconButton(
                        onClick = {
                            isScreenLocked = true
                            isHudVisible = false
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Screen inputs",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Quick Grid Overview entry button (essential for emulator/mouse without touch gesture)
                    IconButton(
                        onClick = {
                            isHudVisible = true
                            isOverviewMode = true
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Pinch Out Grid Layout Overview",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Settings customizer button
                    IconButton(
                        onClick = {
                            isHudVisible = true
                            showSettingsDialog = true
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Customize Standby Menu",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Settings Dialog trigger
            if (showSettingsDialog) {
                StandbyCustomizationDialog(
                    viewModel = viewModel,
                    currentPage = horizontalPagerState.currentPage,
                    onDismiss = {
                        showSettingsDialog = false
                        isHudVisible = true
                    }
                )
            }
        } else {
            // Full screen Pitch zoom out overview layout
            AppOverviewLayout(
                viewModel = viewModel,
                activePage = horizontalPagerState.currentPage,
                activeClockFace = verticalPagerState.currentPage,
                onNavigateToPage = { targetPage ->
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(targetPage)
                    }
                },
                onSelectClockFace = { themeIdx ->
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(0)
                        verticalPagerState.scrollToPage(themeIdx)
                    }
                },
                onDismiss = {
                    isOverviewMode = false
                }
            )
        }

        // Screen Lock Overlay (Premium glassmorphic protector)
        AnimatedVisibility(
            visible = isScreenLocked,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            val currentTab = horizontalPagerState.currentPage
            val activeColorIdx by viewModel.getColorFlow(currentTab).collectAsState()
            val accentColor = viewModel.colors.getOrNull(activeColorIdx)?.first ?: Color(0xFF34D399)

            var isUnlockUiVisible by remember { mutableStateOf(false) }
            val dragAmount = remember { Animatable(0f) }
            var isDraggingState by remember { mutableStateOf(false) }

            LaunchedEffect(isUnlockUiVisible, isDraggingState) {
                if (isUnlockUiVisible && !isDraggingState) {
                    delay(4000)
                    isUnlockUiVisible = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent) // Completely clean/transparent lock screen
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isUnlockUiVisible = true
                            },
                            onPress = {
                                isUnlockUiVisible = true
                            }
                        )
                    }
            ) {
                // 1. Top Capsule: [ 🔒 TOUCH INPUT LOCKED ] - Fades in/out with tap
                AnimatedVisibility(
                    visible = isUnlockUiVisible,
                    enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400)) { -it },
                    exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400)) { -it },
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "TOUCH INPUT LOCKED",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // 2. Bottom Slider: Slide To Unlock - Only shows on bottom part when touched
                AnimatedVisibility(
                    visible = isUnlockUiVisible,
                    enter = fadeIn(animationSpec = tween(400)) + slideInVertically(animationSpec = tween(400)) { it / 2 },
                    exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(animationSpec = tween(400)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 60.dp)
                    ) {
                        val density = LocalDensity.current
                        val trackWidthDp = 280.dp
                        val trackHeightDp = 52.dp
                        val thumbSizeDp = 44.dp
                        val paddingDp = 4.dp
                        
                        val maxDragOffsetDp = trackWidthDp - thumbSizeDp - (paddingDp * 2) // 228.dp
                        val maxDragOffsetPx = with(density) { maxDragOffsetDp.toPx() }
                        
                        // Text shimmer pulse animation
                        val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                        val textAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "textAlpha"
                        )

                        // Slider Track
                        Box(
                            modifier = Modifier
                                .size(width = trackWidthDp, height = trackHeightDp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Slider prompt text
                            Text(
                                text = "Slide to unlock >>>",
                                color = Color.White.copy(alpha = textAlpha),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )

                            // Progress path highlighting slide progress
                            val fraction = if (maxDragOffsetPx > 0) (dragAmount.value / maxDragOffsetPx).coerceIn(0f, 1f) else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(thumbSizeDp + (paddingDp * 2) + (maxDragOffsetDp * fraction))
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                accentColor.copy(alpha = 0.05f),
                                                accentColor.copy(alpha = 0.35f)
                                            )
                                        )
                                    )
                            )

                            // Draggable thumb/handle
                            Box(
                                modifier = Modifier
                                    .padding(start = paddingDp + with(density) { dragAmount.value.toDp() })
                                    .size(thumbSizeDp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            colors = listOf(
                                                accentColor,
                                                accentColor.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .pointerInput(maxDragOffsetPx) {
                                        detectDragGestures(
                                            onDragStart = {
                                                isDraggingState = true
                                            },
                                            onDragEnd = {
                                                isDraggingState = false
                                                coroutineScope.launch {
                                                    if (dragAmount.value >= maxDragOffsetPx * 0.85f) {
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
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Unlock handle",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
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
    val labels = listOf("CLOCK", "ALARM", "CALENDAR", "MUSIC", "TIMER", "DUO")
    
    Surface(
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier
            .padding(horizontal = 48.dp)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, title ->
                val isActive = currentPage == index
                Column(
                    modifier = Modifier
                        .clickable { onPageSelected(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = if (isActive) Color.White else Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 24.dp, height = 2.dp)
                            .background(if (isActive) Color.White else Color.Transparent)
                    )
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (i in 0 until pageCount) {
                val isSelected = i == currentIndex
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color.Gray.copy(alpha = 0.6f))
                        .clickable { onDotClicked(i) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandbyCustomizationDialog(
    viewModel: StandbyViewModel,
    currentPage: Int,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableStateOf(currentPage) }

    val tabTitles = listOf("CLOCK FACE", "ALARM", "CALENDAR", "MUSIC PLAYER", "TIMER FOCUS", "DUO WIDGETS")
    val tabSubtitles = listOf("Main Display", "Alert Synchronization", "Monthly Calendar", "Media Playback", "Focus Tracking", "Split View Screens")

    val selectedColorIdx by viewModel.getColorFlow(activeTab).collectAsState()
    val selectedFontIdx by viewModel.getFontFlow(activeTab).collectAsState()
    val showWeatherVal by viewModel.getWeatherFlow(activeTab).collectAsState()
    val clockScaleVal by viewModel.getScaleFlow(activeTab).collectAsState()
    val animationsEnabledVal by viewModel.getAnimFlow(activeTab).collectAsState()

    val use24Hour by viewModel.use24HourFormat.collectAsState()
    val showAmPm by viewModel.showAmPm.collectAsState()
    val showSeconds by viewModel.showSeconds.collectAsState()

    val pageColor = viewModel.colors.getOrNull(selectedColorIdx)?.first ?: Color.Green

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B13))
                .systemBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(pageColor.copy(alpha = 0.08f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(200f, 200f),
                            radius = 600f
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // LEFT COLUMN: SIDEBAR SELECTOR
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF0C111D))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "STUDIO HUB",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "PREFERENCES & THEMING",
                        color = pageColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        tabTitles.forEachIndexed { idx, title ->
                            val isSelected = activeTab == idx
                            val tabAccent = viewModel.colors[viewModel.getColorFlow(idx).collectAsState().value].first
                            val borderWidth = if (isSelected) 1.5.dp else 1.dp
                            val borderColor = if (isSelected) tabAccent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) tabAccent.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = borderWidth,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { activeTab = idx }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = title,
                                            color = if (isSelected) Color.White else Color.Gray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = tabSubtitles[idx],
                                            color = if (isSelected) tabAccent.copy(alpha = 0.7f) else Color.DarkGray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(tabAccent)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Customizations apply automatically across active Standby screens.",
                        color = Color.DarkGray,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.05f))
                )

                // RIGHT COLUMN: SETTINGS DECK
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .background(pageColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "0${activeTab + 1}",
                                        color = if (pageColor == Color.White) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = tabTitles[activeTab],
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Adjust dynamic layout styles and accents below",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pageColor,
                                contentColor = if (pageColor == Color.White) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Apply & Close",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Card 1: Colors selection
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C111D))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "INTERFACE COLOR ACCENT",
                                    color = pageColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    viewModel.colors.forEachIndexed { index, (color, name) ->
                                        val isSelected = index == selectedColorIdx
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                viewModel.setColorIndexForPage(activeTab, index)
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = name.split(" ").lastOrNull() ?: "",
                                                color = if (isSelected) Color.White else Color.Gray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Card 2: Font selection
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C111D))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "TYPOGRAPHY STYLE",
                                    color = pageColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    viewModel.fonts.forEachIndexed { index, (fontFamily, name) ->
                                        val isSelected = index == selectedFontIdx
                                        Button(
                                            onClick = { viewModel.setFontIndexForPage(activeTab, index) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isSelected) pageColor else Color(0x0FFFFFFF),
                                                contentColor = if (isSelected) {
                                                    if (pageColor == Color.White) Color.Black else Color.White
                                                } else Color.LightGray
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = name,
                                                fontFamily = fontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Clock Scales and Weather (Only for Clock system tab 0 or Duo widgets tab 5)
                        if (activeTab == 0 || activeTab == 5) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0C111D))
                                    .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    if (activeTab == 0) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "CLOCK DISPLAY SCALE Size",
                                                    color = pageColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.2.sp
                                                )
                                                Text(
                                                    text = String.format("%.1fx", clockScaleVal),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = clockScaleVal,
                                                onValueChange = { viewModel.setClockScaleForPage(activeTab, it) },
                                                valueRange = 0.8f..1.8f,
                                                modifier = Modifier.padding(top = 2.dp),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = pageColor,
                                                    activeTrackColor = pageColor,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.05f)
                                                )
                                            )
                                        }
                                    }

                                    // Show weather option
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "Show Live Temperature Info",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Overlays current weather metrics on compatible layouts",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Switch(
                                            checked = showWeatherVal,
                                            onCheckedChange = { viewModel.setShowWeatherForPage(activeTab, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = pageColor
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (activeTab == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0C111D))
                                    .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        "CLOCK FORMAT PREFERENCES",
                                        color = pageColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )

                                    // 24 Hour format toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "24-Hour Format",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Display time in 24-hour style (e.g. 13:00 vs 1:00)",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Switch(
                                            checked = use24Hour,
                                            onCheckedChange = { viewModel.setUse24HourFormat(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = pageColor
                                            )
                                        )
                                    }

                                    // Show AM/PM toggle (only enables/shows if 12-hour is active)
                                    AnimatedVisibility(visible = !use24Hour) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF131A26))
                                                .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Show AM/PM Indicator",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    "Displays AM or PM label alongside 12-hour clocks",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                            Switch(
                                                checked = showAmPm,
                                                onCheckedChange = { viewModel.setShowAmPm(it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.Black,
                                                    checkedTrackColor = pageColor
                                                )
                                            )
                                        }
                                    }

                                    // Show Seconds toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Display Seconds Trackers",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Show ticking seconds on compatible lock screen clock faces",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Switch(
                                            checked = showSeconds,
                                            onCheckedChange = { viewModel.setShowSeconds(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = pageColor
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Calendar Display Preferences (Only for activeTab == 2)
                        if (activeTab == 2) {
                            val calendarViewModel: CalendarViewModel = viewModel()
                            val showDaily by calendarViewModel.showDaily.collectAsState()
                            val showWeekly by calendarViewModel.showWeekly.collectAsState()
                            val showMonthly by calendarViewModel.showMonthly.collectAsState()
                            
                            val startOnMon by calendarViewModel.startWeekOnMonday.collectAsState()
                            val showDots by calendarViewModel.showEventDots.collectAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0C111D))
                                    .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        "CALENDAR CATEGORY FILTERS",
                                        color = pageColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )

                                    // Daily toggles
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Show Daily Events", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Shows schedules listed on current day", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = showDaily,
                                            onCheckedChange = { calendarViewModel.showDaily.value = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = pageColor)
                                        )
                                    }

                                    // Weekly toggles
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Show Weekly Events", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Shows schedules starting in the next 7 days", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = showWeekly,
                                            onCheckedChange = { calendarViewModel.showWeekly.value = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = pageColor)
                                        )
                                    }

                                    // Monthly toggles
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Show Monthly Events", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Shows future events further than a week ahead", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = showMonthly,
                                            onCheckedChange = { calendarViewModel.showMonthly.value = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = pageColor)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "THEME & DISPLAY SETUP",
                                        color = pageColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp
                                    )

                                    // Week Starters
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Start Week On Monday", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Changes grid start layout of monthly themes", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = startOnMon,
                                            onCheckedChange = { calendarViewModel.startWeekOnMonday.value = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = pageColor)
                                        )
                                    }

                                    // Indicators dot
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF131A26))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Show Event Day Dots", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text("Draws small markers below day cells with scheduled events", color = Color.Gray, fontSize = 9.sp)
                                        }
                                        Switch(
                                            checked = showDots,
                                            onCheckedChange = { calendarViewModel.showEventDots.value = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = pageColor)
                                        )
                                    }
                                }
                            }
                        }

                        // Distraction-Free theme for EVERY relevant customization tab
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C111D))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "DISTRACTION-FREE THEME",
                                        color = pageColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Text(
                                        text = "Calms down ticking seconds trackers & decorative details",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Highly recommended for deep focus/dark spaces to clean motion noise",
                                        color = Color.Gray,
                                        fontSize = 9.sp
                                    )
                                }
                                Switch(
                                    checked = !animationsEnabledVal,
                                    onCheckedChange = { isDistractionFree ->
                                        viewModel.setAnimationsEnabledForPage(activeTab, !isDistractionFree)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = pageColor
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

@Composable
fun AppOverviewLayout(
    viewModel: StandbyViewModel,
    activePage: Int,
    activeClockFace: Int,
    onNavigateToPage: (Int) -> Unit,
    onSelectClockFace: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Read the customized colors and typography for previewing
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

    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = Calendar.getInstance()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617).copy(alpha = 0.98f)) // Deep slate-black premium background
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.15f) {
                        onDismiss()
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STANDBY LAYOUT DIRECTORY",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap a screen to select or swipe through them",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                
                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss directory view",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val clockThemes = listOf(
                "Minimal Digital Clock" to "01 | DIGITAL MINIMAL",
                "Classic Analog Clock" to "02 | ANALOG CLASSIC",
                "Cyber Neon Clock" to "03 | DIGITAL NEON",
                "Retro Flip Card Clock" to "04 | RETRO FLIP CARD",
                "Binary Dots Matrix" to "05 | BINARY STYLE",
                "Modern Bold Typography" to "06 | MODERN BOLD TEXT",
                "Large Sidebar Alarm Clock" to "07 | LARGE SIDEBAR",
                "Contrasting Split Color" to "08 | CONTRASTING SPLIT",
                "Analog Motor Dashboard" to "09 | ANALOG DASHBOARD",
                "Pastel Bubbles Clock" to "10 | BUBBLE PASTEL",
                "Ambient Horizon Gradient" to "11 | AMBIENT GRADIENT",
                "High-Tech Nixie Tube" to "12 | NIXIE TUBE"
            )

            val utilityPages = listOf(
                1 to ("System Alarm" to "ALARM DISPATCH"),
                2 to ("Calendar Agenda" to "CALENDAR AGENDA"),
                3 to ("Music Player" to "MUSIC PLAYER"),
                4 to ("Timer Focus Matrix" to "TIMER FOCUS"),
                5 to ("Duo Split Views" to "DUO WIDGETS")
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 265.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category 1 Header: System Widget Panels
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "SYSTEM UTILITY PANELS",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                itemsIndexed(utilityPages) { _, item ->
                    val pageIdx = item.first
                    val (title, label) = item.second
                    val isActive = (activePage == pageIdx)
                    
                    val pageColor = when (pageIdx) {
                        1 -> color1
                        2 -> color2
                        3 -> color3
                        4 -> color4
                        else -> Color(0xFF2DD4BF)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPage(pageIdx) }
                            .border(
                                1.5.dp,
                                if (isActive) pageColor else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        color = if (isActive) pageColor else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = title,
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) pageColor else Color.White.copy(alpha = 0.1f))
                                        .border(1.dp, if (isActive) pageColor else Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(240.dp, 135.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(480.dp, 270.dp)
                                        .graphicsLayer {
                                            scaleX = 0.5f
                                            scaleY = 0.5f
                                        }
                                ) {
                                    when (pageIdx) {
                                        1 -> PreviewAlarmPage(accentColor = color1, fontFamily = font1)
                                        2 -> PreviewCalendarPage(accentColor = color2, fontFamily = font2)
                                        3 -> PreviewMediaPage(accentColor = color3, fontFamily = font3)
                                        4 -> PreviewTimerPage(accentColor = color4, fontFamily = font4)
                                        5 -> PreviewDuoPage(fontFamily = font0)
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }

                // Category 2 Header: Ambient Clock Faces
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = "AMBIENT CLOCK FACES",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                itemsIndexed(clockThemes) { clockThemeIdx, item ->
                    val (title, label) = item
                    val isActive = (activePage == 0 && activeClockFace == clockThemeIdx)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectClockFace(clockThemeIdx) }
                            .border(
                                1.5.dp,
                                if (isActive) color0 else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFF1E293B).copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        color = if (isActive) color0 else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = title,
                                        color = Color.Gray,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) color0 else Color.White.copy(alpha = 0.1f))
                                        .border(1.dp, if (isActive) color0 else Color.White.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isActive) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(240.dp, 135.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .requiredSize(480.dp, 270.dp)
                                        .graphicsLayer {
                                            scaleX = 0.5f
                                            scaleY = 0.5f
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

@Composable
fun PreviewAlarmPage(accentColor: Color, fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "07:30",
                color = accentColor,
                fontSize = 42.sp,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "WAKE UP ALARM",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mon, Tue, Wed, Thu, Fri",
                color = Color.Gray,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun PreviewCalendarPage(accentColor: Color, fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "MAY",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "21",
                    color = Color.White,
                    fontSize = 44.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "THURSDAY",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.7f)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "UPCOMING AGENDA",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Column {
                        Text(
                            text = "Design Review Meeting",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "10:00 AM - 11:30 AM",
                            color = Color.Gray,
                            fontSize = 8.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Column {
                        Text(
                            text = "Lunch with Team",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "12:30 PM - 1:30 PM",
                            color = Color.Gray,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewMediaPage(accentColor: Color, fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Midnight Serenade",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = "Acoustic Horizon",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1:24", color = Color.Gray, fontSize = 7.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .fillMaxHeight()
                                .background(accentColor)
                        )
                    }
                    Text("3:45", color = Color.Gray, fontSize = 7.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PreviewTimerPage(accentColor: Color, fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .weight(0.45f),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 4.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "25:00",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "FOCUS",
                        color = accentColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "FOCUS ROUND 1/4",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Deep Work Session",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("START", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {},
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("RESET", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewDuoPage(fontFamily: FontFamily) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "AMBIENT DUO",
                        color = Color(0xFF3B82F6),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "10:24",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "London 🌤️ 18°C",
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "UPCOMING",
                        color = Color(0xFF2DD4BF),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "• Tea Ceremony",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "03:30 PM - Duo Zen",
                        color = Color.Gray,
                        fontSize = 7.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Standby Sync",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
