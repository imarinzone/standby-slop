package com.example.ui.standby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Alarm

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
            ) {
                // Material 3 Header Bar with Extended FAB
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
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
                                    .padding(10.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Alarms",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colorScheme.onSurface,
                                fontFamily = customFont,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${alarms.count { it.isEnabled }} active scheduled alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("add_alarm_fab")
                    )
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
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 48.dp, vertical = 32.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(68.dp),
                                    shape = CircleShape,
                                    color = colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.AlarmOff,
                                            contentDescription = "No Alarms",
                                            tint = colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(alarms, key = { it.id }) { alarm ->
                            AlarmCard(
                                alarm = alarm,
                                use24Hour = use24Hour,
                                accentColor = accentColor,
                                customFont = customFont,
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
                .padding(vertical = 16.dp, horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(128.dp),
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
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFont,
                        lineHeight = 36.sp
                    )
                    if (!use24Hour) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = amPmText,
                            color = if (enabledState) accentColor else colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = customFont,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alarm.label.ifBlank { "Alarm" },
                    color = if (enabledState) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = customFont,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = if (enabledState) 0.75f else 0.35f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            fontFamily = customFont
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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

    val minHour = if (use24Hour) 0 else 1
    val maxHour = if (use24Hour) 23 else 12

    var pickedHour by remember { mutableIntStateOf(7) }
    var pickedMinute by remember { mutableIntStateOf(0) }
    var isAm by remember { mutableStateOf(true) }
    var alarmLabel by remember { mutableStateOf("Wake Up") }
    var snoozeMinutes by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Set Alarm",
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface,
                fontFamily = customFont,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll( androidx.compose.foundation.rememberScrollState() ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Material 3 Time Picker Dial Card with Haptic Ticks
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour Picker Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.tick()
                                    pickedHour = if (pickedHour == maxHour) minHour else pickedHour + 1
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase Hour")
                            }
                            Text(
                                text = String.format("%02d", pickedHour),
                                color = colorScheme.onSurface,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.tick()
                                    pickedHour = if (pickedHour == minHour) maxHour else pickedHour - 1
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease Hour")
                            }
                        }

                        Text(
                            text = ":",
                            color = colorScheme.primary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )

                        // Minute Picker Column
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.tick()
                                    pickedMinute = (pickedMinute + 1) % 60
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase Minute")
                            }
                            Text(
                                text = String.format("%02d", pickedMinute),
                                color = colorScheme.onSurface,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.tick()
                                    pickedMinute = if (pickedMinute == 0) 59 else pickedMinute - 1
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease Minute")
                            }
                        }

                        if (!use24Hour) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = isAm,
                                    onClick = {
                                        haptics.tick()
                                        isAm = true
                                    },
                                    label = { Text("AM", fontWeight = FontWeight.Bold) }
                                )
                                FilterChip(
                                    selected = !isAm,
                                    onClick = {
                                        haptics.tick()
                                        isAm = false
                                    },
                                    label = { Text("PM", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                }

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
        },
        confirmButton = {
            Button(
                onClick = {
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
                }
            ) {
                Text("Save Alarm", fontFamily = customFont)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = customFont)
            }
        }
    )
}
