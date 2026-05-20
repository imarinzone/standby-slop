package com.example.ui.standby

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Custom ringtone options mapped to identifiers for physical beeping playback previews
val ringtoneOptions = listOf(
    "Classic Alarm Beep" to "alarm_beep",
    "Digital Clock Buzz" to "digital_buzz",
    "Zen Sanctuary Bell" to "zen_bell",
    "Sci-Fi Aurora Pulse" to "sci_fi_pulse",
    "Gentle Acoustic Harp" to "acoustic_harp"
)

private fun playRingtonePreview(toneIdentifier: String) {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_ALARM, 85)
        when (toneIdentifier) {
            "alarm_beep" -> {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            }
            "digital_buzz" -> {
                tg.startTone(ToneGenerator.TONE_DTMF_D, 400)
            }
            "zen_bell" -> {
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            }
            "sci_fi_pulse" -> {
                tg.startTone(ToneGenerator.TONE_CDMA_PIP, 400)
            }
            "acoustic_harp" -> {
                tg.startTone(ToneGenerator.TONE_PROP_PROMPT, 600)
            }
            else -> {
                tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("AlarmPage", "Tone playback failed", e)
    }
}

@Composable
fun AlarmPage(modifier: Modifier = Modifier, viewModel: StandbyViewModel = viewModel()) {
    val alarmsList by viewModel.alarms.collectAsState()
    val nextSystemAlarm by viewModel.nextSystemAlarm.collectAsState()
    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val selectedFontIdx by viewModel.fontPage1.collectAsState()
    val is24Hr by viewModel.use24HourFormat.collectAsState()
    
    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    
    var showAddDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pane: Scrollable List of Scheduled Local Alarms
        Card(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.9f)),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SCHEDULED ALARMS (${alarmsList.size})",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    
                    if (alarmsList.isNotEmpty()) {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Alarm Quick",
                                tint = accentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (alarmsList.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Empty",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Alarms Set",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Use the panel on the right to schedule your first premium alarm.",
                            color = Color.DarkGray,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alarmsList, key = { it.id }) { alarm ->
                            val displayTimeStr = remember(alarm.hour, alarm.minute, is24Hr) {
                                if (is24Hr) {
                                    val h = if (alarm.hour < 10) "0${alarm.hour}" else "${alarm.hour}"
                                    val m = if (alarm.minute < 10) "0${alarm.minute}" else "${alarm.minute}"
                                    "$h:$m"
                                } else {
                                    val amPm = if (alarm.hour >= 12) "PM" else "AM"
                                    val h12 = when {
                                        alarm.hour == 0 -> 12
                                        alarm.hour > 12 -> alarm.hour - 12
                                        else -> alarm.hour
                                    }
                                    val m = if (alarm.minute < 10) "0${alarm.minute}" else "${alarm.minute}"
                                    "$h12:$m $amPm"
                                }
                            }
                            
                            val toneLabel = remember(alarm.toneUri) {
                                ringtoneOptions.firstOrNull { it.second == alarm.toneUri }?.first ?: "Classic Alarm Beep"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = displayTimeStr,
                                            color = if (alarm.isEnabled) Color.White else Color.Gray,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = customFont
                                        )
                                        
                                        if (alarm.label.isNotEmpty()) {
                                            Text(
                                                text = alarm.label,
                                                color = if (alarm.isEnabled) accentColor.copy(alpha = 0.9f) else Color.DarkGray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = if (alarm.isEnabled) 0.08f else 0.02f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Snooze: ${alarm.snoozeMinutes}m",
                                            color = Color.Gray,
                                            fontSize = 10.sp
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = toneLabel,
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // Quick Preview Tone button
                                    IconButton(
                                        onClick = { playRingtonePreview(alarm.toneUri) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Preview Audio",
                                            tint = if (alarm.isEnabled) Color.LightGray else Color.DarkGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = { alarmToEdit = alarm },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Alarm Settings",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Switch Toggle
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { viewModel.toggleAlarm(alarm) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = accentColor,
                                            uncheckedThumbColor = Color.Gray,
                                            uncheckedTrackColor = Color.DarkGray.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )

                                    // Delete button
                                    IconButton(
                                        onClick = { viewModel.deleteAlarm(alarm) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Alarm",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Pane: Active Alarms Overview & Engine Controller
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stats Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "Standby Alarm Engine Logo",
                    tint = accentColor,
                    modifier = Modifier.size(36.dp)
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "NEXT LOCAL SCHEDULE",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = nextSystemAlarm ?: "No Alarm",
                    color = if (nextSystemAlarm != "No Alarm") Color.White else Color.DarkGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Runs securely in background with dynamic chime alarms context.",
                    color = Color.LightGray.copy(alpha = 0.4f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Create button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Local Alarm", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    // Modal dialog for Adding New Alarms
    if (showAddDialog) {
        AlarmEditDialog(
            alarm = null,
            onDismiss = { showAddDialog = false },
            onSave = { alarm ->
                viewModel.addAlarm(
                    hour = alarm.hour,
                    minute = alarm.minute,
                    label = alarm.label,
                    snooze = alarm.snoozeMinutes,
                    toneUri = alarm.toneUri
                )
                showAddDialog = false
            },
            accentColor = accentColor
        )
    }

    // Modal dialog for Editing Existing Alarms
    if (alarmToEdit != null) {
        AlarmEditDialog(
            alarm = alarmToEdit,
            onDismiss = { alarmToEdit = null },
            onSave = { updatedAlarm ->
                viewModel.updateAlarm(updatedAlarm)
                alarmToEdit = null
            },
            accentColor = accentColor
        )
    }
}


@Composable
fun AlarmEditDialog(
    alarm: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    accentColor: Color
) {
    val isEditMode = alarm != null
    var hourText by remember { mutableStateOf(alarm?.hour?.toString() ?: "08") }
    var minuteText by remember { mutableStateOf(alarm?.minute?.toString() ?: "00") }
    var label by remember { mutableStateOf(alarm?.label ?: "") }
    var snoozeVal by remember { mutableStateOf(alarm?.snoozeMinutes ?: 5) }
    var selectedTone by remember { mutableStateOf(alarm?.toneUri ?: "alarm_beep") }
    
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Configure Alarm" else "Create Standby Alarm",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hour & Minute Inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                hourText = input
                                errorMsg = null
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedLabelColor = accentColor
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                minuteText = input
                                errorMsg = null
                            }
                        },
                        label = { Text("Minute (0-59)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedLabelColor = accentColor
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Custom Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Label (e.g. Work, Workout)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Snooze Slider or Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Snooze Duration:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15).forEach { min ->
                            val isSelected = snoozeVal == min
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor else Color(0xFF1E293B))
                                    .clickable { snoozeVal = min }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${min}m",
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Custom Tone Picker directly inline!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Select Audible Ringtone:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ringtoneOptions.forEach { (name, id) ->
                            val isSelected = selectedTone == id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable { selectedTone = id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedTone = id },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = accentColor,
                                            unselectedColor = Color.Gray
                                        ),
                                        modifier = Modifier.scale(0.85f)
                                    )
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                
                                // Mini trigger button to play preview!
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Tap to listen preview",
                                    tint = if (isSelected) accentColor else Color.DarkGray,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { playRingtonePreview(id) }
                                )
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hourText.toIntOrNull()
                    val m = minuteText.toIntOrNull()
                    
                    if (h == null || h !in 0..23) {
                        errorMsg = "Hour must be a valid integer between 0 and 23."
                        return@Button
                    }
                    if (m == null || m !in 0..59) {
                        errorMsg = "Minute must be a valid integer between 0 and 59."
                        return@Button
                    }
                    
                    val outputAlarm = Alarm(
                        id = alarm?.id ?: 0,
                        hour = h,
                        minute = m,
                        label = label.trim().takeIf { it.isNotEmpty() } ?: "Alarm",
                        snoozeMinutes = snoozeVal,
                        toneUri = selectedTone,
                        isEnabled = alarm?.isEnabled ?: true
                    )
                    onSave(outputAlarm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
