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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val verticalPagerState = rememberPagerState(pageCount = { 11 })

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
                        pageCount = 11,
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
                onNavigateToPage = { targetPage ->
                    isOverviewMode = false
                    isHudVisible = true
                    coroutineScope.launch {
                        horizontalPagerState.scrollToPage(targetPage)
                    }
                },
                onDismiss = {
                    isOverviewMode = false
                }
            )
        }

        // Screen Lock Overlay (Premium tactical glassmorphic protector)
        AnimatedVisibility(
            visible = isScreenLocked,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(500))
        ) {
            var isPressed by remember { mutableStateOf(false) }
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = ""
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {}
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(32.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 48.dp, vertical = 32.dp)
                ) {
                    // Giant Padlock Lock Target
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isPressed) 0.15f else 0.05f))
                            .border(
                                width = 1.dp,
                                color = if (isPressed) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        isScreenLocked = false
                                        isHudVisible = true
                                    },
                                    onPress = {
                                        try {
                                            isPressed = true
                                            awaitRelease()
                                        } finally {
                                            isPressed = false
                                        }
                                    }
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Hold to unlock",
                            tint = if (isPressed) Color(0xFF34D399) else Color.White,
                            modifier = Modifier
                                .size(if (isPressed) 36.dp else 42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "TOUCH LOCKED",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isPressed) "Unlocking..." else "Press & hold lock icon to unlock",
                        color = if (isPressed) Color(0xFF34D399) else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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

    val tabTitles = listOf("CLOCK FACE", "ALARM SYSTEM", "CALENDAR GRID", "MUSIC CONTROLLER", "TIMER FOCUS", "DUO WIDGETS")
    val tabSubtitles = listOf("Main Display", "Alert Synchronization", "Monthly Calendar", "Media Playback", "Focus Tracking", "Split View Screens")

    val selectedColorIdx by viewModel.getColorFlow(activeTab).collectAsState()
    val selectedFontIdx by viewModel.getFontFlow(activeTab).collectAsState()
    val showWeatherVal by viewModel.getWeatherFlow(activeTab).collectAsState()
    val clockScaleVal by viewModel.getScaleFlow(activeTab).collectAsState()
    val animationsEnabledVal by viewModel.getAnimFlow(activeTab).collectAsState()

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
    onNavigateToPage: (Int) -> Unit,
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
    val font0 = viewModel.fonts[font0Idx].first

    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val nextSystemAlarm by viewModel.nextSystemAlarm.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = Calendar.getInstance()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(24.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.15f) {
                        onDismiss()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Dashboard Title
            Text(
                text = "STANDBY PANELS DIRECTORY",
                color = Color.LightGray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Tap a screen to focus or pinch open to zoom in",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // 3x2 Grid of Previews for 5 screens
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 760.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 0: Clock screen preview
                    OverviewPreviewCard(
                        title = "01 | CLOCK SYSTEM",
                        accentColor = color0,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(0) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                            Text(
                                text = format.format(currentTime.time),
                                color = color0,
                                fontSize = 32.sp,
                                fontFamily = font0,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Clock Live View",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Card 1: Alarm screen preview
                    OverviewPreviewCard(
                        title = "02 | ALARM SYNC",
                        accentColor = color1,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(1) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            Text(
                                text = nextSystemAlarm ?: "No Alarm",
                                color = color1,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Native Clock Sync",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Card 2: Calendar screen preview
                    OverviewPreviewCard(
                        title = "03 | CALENDAR INDEX",
                        accentColor = color2,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(2) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            Text(
                                text = "Device Calendar",
                                color = color2,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Upcoming Native Events",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 3: Media control preview
                    OverviewPreviewCard(
                        title = "04 | MUSIC DECKS",
                        accentColor = color3,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(3) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            Text(
                                text = "System Music",
                                color = color3,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Live Media Controls",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Card 4: Timer & Stopwatch preview
                    OverviewPreviewCard(
                        title = "05 | TIMER MATRIX",
                        accentColor = color4,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(4) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            Text(
                                text = "Focus & Lap Timer",
                                color = color4,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Pomodoro & High-Tech Stops",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Card 5: Duo split widget page preview
                    OverviewPreviewCard(
                        title = "06 | DUO SPLIT SPLITS",
                        accentColor = Color(0xFF2DD4BF),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPage(5) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            Text(
                                text = "Duo Screens",
                                color = Color(0xFF2DD4BF),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Side-by-Side Dual Widgets",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewPreviewCard(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() }
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}
