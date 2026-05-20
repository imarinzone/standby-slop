package com.example.ui.standby

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val selectedColorIdx by viewModel.getColorFlow(currentPage).collectAsState()
    val selectedFontIdx by viewModel.getFontFlow(currentPage).collectAsState()
    val showWeatherVal by viewModel.getWeatherFlow(currentPage).collectAsState()
    val clockScaleVal by viewModel.getScaleFlow(currentPage).collectAsState()
    val animationsEnabledVal by viewModel.getAnimFlow(currentPage).collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Customize ${listOf("Clock", "Alarm", "Calendar", "Music", "Timer").getOrElse(currentPage) { "Timer" }} Screen",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        containerColor = Color(0xFF1E293B),
        textContentColor = Color.White,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Color selection
                Column {
                    Text(
                        "Interface Color Accent",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModel.colors.forEachIndexed { index, (color, name) ->
                            val isSelected = index == selectedColorIdx
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.setColorIndexForPage(currentPage, index) },
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
                        }
                    }
                }

                // Font style selection
                Column {
                    Text(
                        "Typography Style",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        viewModel.fonts.forEachIndexed { index, (fontFamily, name) ->
                            val isSelected = index == selectedFontIdx
                            Button(
                                onClick = { viewModel.setFontIndexForPage(currentPage, index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) viewModel.colors[selectedColorIdx].first else Color(0xFF334155),
                                    contentColor = if (isSelected && viewModel.colors[selectedColorIdx].first == Color.White) Color.Black else Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontFamily = fontFamily,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Clock scale selection (Only applicable on actual Clock page)
                if (currentPage == 0) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Clock Display Size",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format("%.1fx", clockScaleVal),
                                color = viewModel.colors[selectedColorIdx].first,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = clockScaleVal,
                            onValueChange = { viewModel.setClockScaleForPage(currentPage, it) },
                            valueRange = 0.8f..1.8f,
                            modifier = Modifier.padding(top = 4.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = viewModel.colors[selectedColorIdx].first,
                                activeTrackColor = viewModel.colors[selectedColorIdx].first
                            )
                        )
                    }

                    // Show weather toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (showWeatherVal) Icons.Default.Cloud else Icons.Default.CloudOff,
                                contentDescription = "Weather Widget Toggle",
                                tint = Color.White
                            )
                            Text("Show Weather Info", color = Color.White, fontSize = 15.sp)
                        }
                        Switch(
                            checked = showWeatherVal,
                            onCheckedChange = { viewModel.setShowWeatherForPage(currentPage, it) }
                        )
                    }
                }

                // Animation removal for distraction-free theme setting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Distraction-Free Theme", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Removes ticking seconds tracker & animations", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = !animationsEnabledVal,
                        onCheckedChange = { isDistractionFree ->
                            viewModel.setAnimationsEnabledForPage(currentPage, !isDistractionFree)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = viewModel.colors[selectedColorIdx].first)
            ) {
                Text(
                    "Apply",
                    fontWeight = FontWeight.SemiBold,
                    color = if (viewModel.colors[selectedColorIdx].first == Color.White) Color.Black else Color.White
                )
            }
        }
    )
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
