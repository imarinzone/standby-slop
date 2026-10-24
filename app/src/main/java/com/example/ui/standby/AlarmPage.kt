package com.example.ui.standby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Alarm
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun AlarmPage(
    modifier: Modifier = Modifier,
    viewModel: StandbyViewModel = viewModel()
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val customFont = viewModel.fonts[viewModel.fontPage1.collectAsState().value].first
    val accentColor = viewModel.colors[selectedColorIdx].first
    val bgUri by viewModel.getBgUriFlow(1).collectAsState()

    val alarms by viewModel.alarms.collectAsState()
    val use24Hour by viewModel.use24HourFormat.collectAsState()
    val gradualDimEnabled by viewModel.gradualAlarmDimEnabled.collectAsState()
    val gradualDimWindowMins by viewModel.gradualAlarmDimWindowMinutes.collectAsState()
    val liveBrightness by viewModel.clockBrightnessFactor.collectAsState()
    val isPreAlarmDimming by viewModel.isPreAlarmDimmingActive.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

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

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompactHeight = maxHeight < 390.dp
                val isCompactWidth = maxWidth < 660.dp
                val hPad = if (isCompactWidth) 16.dp else 28.dp
                val vPad = if (isCompactHeight) 12.dp else 20.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = hPad, vertical = vPad)
                ) {
                    // Material 3 Header Bar with Pre-Alarm Dim Chip & Extended FAB
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 10.dp else 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(if (isCompactHeight) 8.dp else 10.dp)
                                        .size(if (isCompactHeight) 20.dp else 24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Alarms",
                                    style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                    color = colorScheme.onSurface,
                                    fontFamily = customFont,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${alarms.count { it.isEnabled }} active scheduled alerts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = gradualDimEnabled,
                                onClick = {
                                    haptics.confirm()
                                    viewModel.setGradualAlarmDimEnabled(!gradualDimEnabled)
                                },
                                label = {
                                    Text(
                                        text = when {
                                            !gradualDimEnabled -> "Pre-Alarm Dim: Off"
                                            isPreAlarmDimming -> "Dimming ${(liveBrightness * 100).toInt()}%"
                                            else -> "Pre-Alarm Dim (${gradualDimWindowMins}m)"
                                        },
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (gradualDimEnabled) Icons.Default.BrightnessLow else Icons.Default.BrightnessHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("alarm_page_gradual_dim_chip")
                            )

                            ExtendedFloatingActionButton(
                                onClick = {
                                    haptics.tick()
                                    showAddDialog = true
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.AlarmAdd,
                                        contentDescription = "Create Alarm"
                                    )
                                },
                                text = {
                                    Text(
                                        text = "New Alarm",
                                        fontFamily = customFont,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                },
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("add_alarm_fab")
                            )
                        }
                    }

                    if (alarms.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = colorScheme.surfaceContainerLow
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.widthIn(max = 460.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(
                                            horizontal = if (isCompactWidth) 24.dp else 48.dp,
                                            vertical = if (isCompactHeight) 18.dp else 32.dp
                                        )
                                ) {
                                    Surface(
                                        modifier = Modifier.size(if (isCompactHeight) 52.dp else 68.dp),
                                        shape = CircleShape,
                                        color = colorScheme.secondaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Outlined.AlarmOff,
                                                contentDescription = "No Alarms",
                                                tint = colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(if (isCompactHeight) 26.dp else 32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))
                                    Text(
                                        text = "No Active Alarms",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontFamily = customFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap New Alarm to schedule a wake-up or standby reminder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
                            contentPadding = PaddingValues(bottom = if (isCompactHeight) 48.dp else 64.dp)
                        ) {
                            items(alarms, key = { "${it.hour}_${it.minute}_${it.id}" }) { alarm ->
                                AlarmCard(
                                    alarm = alarm,
                                    use24Hour = use24Hour,
                                    accentColor = accentColor,
                                    customFont = customFont,
                                    isCompact = isCompactWidth || isCompactHeight,
                                    onToggle = {
                                        haptics.confirm()
                                        viewModel.toggleAlarm(alarm)
                                    },
                                    onDelete = {
                                        haptics.tick()
                                        viewModel.deleteAlarm(alarm)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CustomAddAlarmDialog(
            accentColor = accentColor,
            customFont = customFont,
            use24Hour = use24Hour,
            onDismiss = {
                haptics.tick()
                showAddDialog = false
            },
            onConfirm = { hour, minute, label, snooze ->
                haptics.confirm()
                viewModel.addAlarm(hour, minute, label, snooze)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
    use24Hour: Boolean,
    accentColor: Color,
    customFont: FontFamily,
    isCompact: Boolean = false,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val enabledState = alarm.isEnabled

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (enabledState) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = if (isCompact) 12.dp else 16.dp,
                    horizontal = if (isCompact) 14.dp else 22.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.widthIn(min = if (isCompact) 104.dp else 124.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val hourText = if (use24Hour) {
                    String.format("%02d", alarm.hour)
                } else {
                    val displayHour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
                    displayHour.toString()
                }
                val minuteText = String.format("%02d", alarm.minute)
                val amPmText = if (use24Hour) "" else if (alarm.hour >= 12) "PM" else "AM"

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hourText:$minuteText",
                        color = if (enabledState) colorScheme.onSurface else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = if (isCompact) 28.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont,
                        maxLines = 1,
                        softWrap = false,
                        lineHeight = if (isCompact) 30.sp else 36.sp
                    )
                    if (!use24Hour) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = amPmText,
                            color = if (enabledState) accentColor else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = customFont,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(if (isCompact) 10.dp else 16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label.ifBlank { "Alarm" },
                    color = if (enabledState) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    style = if (isCompact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    fontFamily = customFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = if (enabledState) 0.75f else 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Snooze,
                            contentDescription = "Snooze interval",
                            tint = colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Snooze ${alarm.snoozeMinutes} min",
                            color = colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 10.dp)
            ) {
                Switch(
                    checked = enabledState,
                    onCheckedChange = { onToggle() }
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete Alarm",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAddAlarmDialog(
    accentColor: Color,
    customFont: FontFamily,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, label: String, snooze: Int) -> Unit
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

    val hourValues = remember(use24Hour) {
        if (use24Hour) (0..23).toList() else (1..12).toList()
    }
    val minuteValues = remember { (0..59).toList() }

    var pickedHour by remember { mutableIntStateOf(7) }
    var pickedMinute by remember { mutableIntStateOf(0) }
    var isAm by remember { mutableStateOf(true) }
    var alarmLabel by remember { mutableStateOf("Wake Up") }
    var snoozeMinutes by remember { mutableIntStateOf(5) }
    var hasSubmitted by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val useTwoColumns = maxWidth > 500.dp && maxHeight < 480.dp
            val isVeryShort = maxHeight < 360.dp
            val wheelItemHeight = if (isVeryShort) 38.dp else 44.dp

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .widthIn(max = if (useTwoColumns) 640.dp else 440.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = if (isVeryShort) 12.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isVeryShort) 10.dp else 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Set Alarm",
                            style = if (isVeryShort) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            color = colorScheme.onSurface,
                            fontFamily = customFont,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", fontFamily = customFont)
                            }
                            Button(
                                enabled = !hasSubmitted,
                                onClick = {
                                    if (hasSubmitted) return@Button
                                    hasSubmitted = true
                                    val finalHour = if (use24Hour) {
                                        pickedHour
                                    } else {
                                        if (isAm) {
                                            if (pickedHour == 12) 0 else pickedHour
                                        } else {
                                            if (pickedHour == 12) 12 else pickedHour + 12
                                        }
                                    }
                                    onConfirm(finalHour, pickedMinute, alarmLabel, snoozeMinutes)
                                },
                                modifier = Modifier.testTag("save_alarm_button")
                            ) {
                                Text("Save Alarm", fontFamily = customFont)
                            }
                        }
                    }

                    if (useTwoColumns) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Column: Scrollable Haptic Wheel Time Dial
                            ScrollableTimeDialCard(
                                hourValues = hourValues,
                                minuteValues = minuteValues,
                                pickedHour = pickedHour,
                                pickedMinute = pickedMinute,
                                isAm = isAm,
                                use24Hour = use24Hour,
                                customFont = customFont,
                                accentColor = accentColor,
                                itemHeight = wheelItemHeight,
                                onHourChanged = { pickedHour = it },
                                onMinuteChanged = { pickedMinute = it },
                                onAmChanged = { isAm = it },
                                modifier = Modifier.weight(1f)
                            )

                            // Right Column: Label + Snooze
                            Column(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = alarmLabel,
                                    onValueChange = { alarmLabel = it },
                                    label = { Text("Alarm Label", fontFamily = customFont) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Snooze Interval",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colorScheme.onSurfaceVariant,
                                        fontFamily = customFont,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        val snoozeOptions = listOf(5, 10, 15, 30)
                                        snoozeOptions.forEachIndexed { idx, mins ->
                                            SegmentedButton(
                                                selected = snoozeMinutes == mins,
                                                onClick = {
                                                    haptics.tick()
                                                    snoozeMinutes = mins
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = snoozeOptions.size)
                                            ) {
                                                Text("${mins}m", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Single Column Fallback for Portrait / Tall Screens
                        ScrollableTimeDialCard(
                            hourValues = hourValues,
                            minuteValues = minuteValues,
                            pickedHour = pickedHour,
                            pickedMinute = pickedMinute,
                            isAm = isAm,
                            use24Hour = use24Hour,
                            customFont = customFont,
                            accentColor = accentColor,
                            itemHeight = wheelItemHeight,
                            onHourChanged = { pickedHour = it },
                            onMinuteChanged = { pickedMinute = it },
                            onAmChanged = { isAm = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = alarmLabel,
                            onValueChange = { alarmLabel = it },
                            label = { Text("Alarm Label", fontFamily = customFont) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Snooze Interval",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                                fontFamily = customFont,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                val snoozeOptions = listOf(5, 10, 15, 30)
                                snoozeOptions.forEachIndexed { idx, mins ->
                                    SegmentedButton(
                                        selected = snoozeMinutes == mins,
                                        onClick = {
                                            haptics.tick()
                                            snoozeMinutes = mins
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = snoozeOptions.size)
                                    ) {
                                        Text("${mins}m")
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
private fun ScrollableTimeDialCard(
    hourValues: List<Int>,
    minuteValues: List<Int>,
    pickedHour: Int,
    pickedMinute: Int,
    isAm: Boolean,
    use24Hour: Boolean,
    customFont: FontFamily,
    accentColor: Color,
    itemHeight: Dp,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    onAmChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelNumberDial(
                values = hourValues,
                selectedValue = pickedHour,
                onValueSelected = onHourChanged,
                customFont = customFont,
                accentColor = accentColor,
                itemHeight = itemHeight,
                testTag = "alarm_hour_wheel",
                modifier = Modifier.width(68.dp)
            )

            Text(
                text = ":",
                color = accentColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = customFont,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            WheelNumberDial(
                values = minuteValues,
                selectedValue = pickedMinute,
                onValueSelected = onMinuteChanged,
                customFont = customFont,
                accentColor = accentColor,
                itemHeight = itemHeight,
                testTag = "alarm_minute_wheel",
                modifier = Modifier.width(68.dp)
            )

            if (!use24Hour) {
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isAm,
                        onClick = {
                            haptics.tick()
                            onAmChanged(true)
                        },
                        label = { Text("AM", fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = !isAm,
                        onClick = {
                            haptics.tick()
                            onAmChanged(false)
                        },
                        label = { Text("PM", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberDial(
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    customFont: FontFamily,
    accentColor: Color,
    itemHeight: Dp,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    val itemCount = values.size.coerceAtLeast(1)
    val virtualCount = itemCount * 1000
    val middleBase = (virtualCount / 2) - ((virtualCount / 2) % itemCount)
    val initialOffset = values.indexOf(selectedValue).coerceAtLeast(0)
    val initialIndex = middleBase + initialOffset

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.visibleItemsInfo.isEmpty()) {
                initialIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: initialIndex
            }
        }
    }

    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(centerIndex) {
        val currentVal = values[centerIndex.mod(itemCount)]
        if (!hasInitialized) {
            hasInitialized = true
        } else {
            haptics.step()
            if (currentVal != selectedValue) {
                onValueSelected(currentVal)
            }
        }
    }

    val wheelHeight = itemHeight * 3

    Box(
        modifier = modifier
            .height(wheelHeight)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Center selection highlight frame so the active slot stands out against faded dial numbers
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.secondaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
        ) {}

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = virtualCount, key = { it }) { index ->
                val number = values[index.mod(itemCount)]
                val isCenter = index == centerIndex

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            val layoutInfo = listState.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (itemInfo != null && itemInfo.size > 0) {
                                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                val normalizedDistance = (abs(itemCenter - viewportCenter) / itemInfo.size.toFloat()).coerceIn(0f, 1.5f)
                                alpha = (1f - normalizedDistance * 0.65f).coerceIn(0.22f, 1f)
                                val scale = (1.08f - normalizedDistance * 0.26f).coerceIn(0.74f, 1.08f)
                                scaleX = scale
                                scaleY = scale
                                rotationX = ((viewportCenter - itemCenter) / itemInfo.size.toFloat() * 24f).coerceIn(-32f, 32f)
                            } else {
                                alpha = if (isCenter) 1f else 0.28f
                            }
                        }
                        .clickable {
                            if (index != centerIndex) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format("%02d", number),
                        color = if (isCenter) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                        fontSize = if (isCenter) 28.sp else 21.sp,
                        fontWeight = if (isCenter) FontWeight.ExtraBold else FontWeight.Medium,
                        fontFamily = customFont,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Top & Bottom soft gradient fade overlays to reinforce the curved scrollable dial look
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 0.85f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.surfaceContainer,
                            colorScheme.surfaceContainer.copy(alpha = 0f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 0.85f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colorScheme.surfaceContainer.copy(alpha = 0f),
                            colorScheme.surfaceContainer
                        )
                    )
                )
        )
    }
}
