package com.example.ui.standby

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Alarm

@Composable
fun AlarmPage(modifier: Modifier = Modifier, viewModel: StandbyViewModel = viewModel()) {
    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val customFont = viewModel.fonts[viewModel.fontPage1.collectAsState().value].first
    val accentColor = viewModel.colors[selectedColorIdx].first

    val alarms by viewModel.alarms.collectAsState()
    val use24Hour by viewModel.use24HourFormat.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(vertical = 16.dp, horizontal = 24.dp)
    ) {
        // High-fidelity background radial glow matching active app accent color
        Box(
            modifier = Modifier
                .size(450.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.04f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            // Screen Header Title & Add Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALARM",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = customFont,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (alarms.isEmpty()) "No alarms configured" else "${alarms.size} active alarms list",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontFamily = customFont,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Add Alarm Button with modern outline glowing look
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = accentColor,
                        containerColor = accentColor.copy(alpha = 0.05f)
                    ),
                    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Alarm Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW ALARM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = customFont,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (alarms.isEmpty()) {
                // Creative empty state view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.12f))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AlarmOff,
                                contentDescription = "No Alarms icon",
                                tint = accentColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "NO ACTIVE ALARMS",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Set customizable recurring chimes, alert labels, and snooze rules to organize your routines.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = customFont,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            } else {
                // Alarms List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(alarms, key = { it.id }) { alarm ->
                        AlarmCard(
                            alarm = alarm,
                            use24Hour = use24Hour,
                            accentColor = accentColor,
                            customFont = customFont,
                            onToggle = { viewModel.toggleAlarm(alarm) },
                            onDelete = { viewModel.deleteAlarm(alarm) }
                        )
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
            onDismiss = { showAddDialog = false },
            onConfirm = { hour, minute, label, snooze ->
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
    customFont: androidx.compose.ui.text.font.FontFamily,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val enabledState = alarm.isEnabled

    val cardBg = remember(enabledState) {
        if (enabledState) Color(0xFF0F172A) else Color(0xFF050505)
    }
    val cardBorder = remember(enabledState, accentColor) {
        if (enabledState) BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
        else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Alarm Time Display
            Column(
                modifier = Modifier.width(100.dp),
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

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "$hourText:$minuteText",
                        color = if (enabledState) Color.White else Color.Gray,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = customFont,
                        lineHeight = 32.sp
                    )
                    if (!use24Hour) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = amPmText,
                            color = if (enabledState) accentColor else Color.Gray.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Alarm Info Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = alarm.label.ifBlank { "Alarm" },
                    color = if (enabledState) Color.White else Color.Gray.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Snooze minutes config icon",
                        tint = if (enabledState) accentColor.copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Snooze: ${alarm.snoozeMinutes}m",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = customFont
                    )
                }
            }

            // Switch & Delete Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Switch(
                    checked = enabledState,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = Color.DarkGray,
                        uncheckedTrackColor = Color.Black
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomAddAlarmDialog(
    accentColor: Color,
    customFont: androidx.compose.ui.text.font.FontFamily,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, label: String, snooze: Int) -> Unit
) {
    val minHour = if (use24Hour) 0 else 1
    val maxHour = if (use24Hour) 23 else 12

    var pickedHour by remember { mutableStateOf(if (use24Hour) 7 else 7) }
    var pickedMinute by remember { mutableStateOf(0) }
    var isAm by remember { mutableStateOf(true) }
    var alarmLabel by remember { mutableStateOf("Wake Up") }
    var snoozeMinutes by remember { mutableStateOf(5) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF0F172A),
        title = {
            Text(
                text = "Add custom Alarm".uppercase(),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = customFont,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Modern visual time spinner controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // HOUR SPINNER
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (pickedHour == maxHour) pickedHour = minHour else pickedHour++
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up Hour", tint = accentColor)
                        }
                        Text(
                            text = String.format("%02d", pickedHour),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = customFont
                        )
                        IconButton(
                            onClick = {
                                if (pickedHour == minHour) pickedHour = maxHour else pickedHour--
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down Hour", tint = accentColor)
                        }
                    }

                    Text(
                        text = ":",
                        color = accentColor.copy(alpha = 0.8f),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                    )

                    // MINUTE SPINNER
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        IconButton(
                            onClick = {
                                pickedMinute = (pickedMinute + 1) % 60
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up Minute", tint = accentColor)
                        }
                        Text(
                            text = String.format("%02d", pickedMinute),
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = customFont
                        )
                        IconButton(
                            onClick = {
                                pickedMinute = if (pickedMinute == 0) 59 else pickedMinute - 1
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down Minute", tint = accentColor)
                        }
                    }

                    if (!use24Hour) {
                        Spacer(modifier = Modifier.width(16.dp))

                        // AM/PM SLIDER BUTTON
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { isAm = !isAm },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor.copy(alpha = 0.15f),
                                    contentColor = accentColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isAm) "AM" else "PM",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = customFont
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Alarm Label input
                OutlinedTextField(
                    value = alarmLabel,
                    onValueChange = { alarmLabel = it },
                    label = { Text("Alarm Note / Label", fontFamily = customFont) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Snooze selection buttons row
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Snooze interval",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = customFont,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { mins ->
                            val isSelected = snoozeMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) accentColor else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { snoozeMinutes = mins },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${mins}M",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = customFont
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalHour = if (use24Hour) {
                        pickedHour
                    } else {
                        // Convert 12-hour selection based on AM/PM
                        if (isAm) {
                            if (pickedHour == 12) 0 else pickedHour
                        } else {
                            if (pickedHour == 12) 12 else pickedHour + 12
                        }
                    }
                    onConfirm(finalHour, pickedMinute, alarmLabel, snoozeMinutes)
                }
            ) {
                Text(
                    text = "SAVE DIRECTLY",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(
                    text = "CANCEL",
                    color = Color.Gray,
                    fontFamily = customFont
                )
            }
        }
    )
}
