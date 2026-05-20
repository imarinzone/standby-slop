package com.example.ui.standby

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AlarmPage(modifier: Modifier = Modifier, viewModel: StandbyViewModel = viewModel()) {
    val context = LocalContext.current
    val nextSystemAlarm by viewModel.nextSystemAlarm.collectAsState()
    val selectedColorIdx by viewModel.colorPage1.collectAsState()
    val selectedFontIdx by viewModel.fontPage1.collectAsState()
    
    val accentColor = viewModel.colors[selectedColorIdx].first
    val customFont = viewModel.fonts[selectedFontIdx].first
    var showAddDialog by remember { mutableStateOf(false) }

    // Periodic check to keep system alarm status accurate on view resume/active
    LaunchedEffect(Unit) {
        viewModel.updateNextAlarm()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pane: Current System Alarm Status Card
        Card(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "System alarm clock logo",
                    tint = accentColor,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "NEXT SYSTEM ALARM",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val displayedAlarm = nextSystemAlarm ?: "No Alarm"
                Text(
                    text = displayedAlarm,
                    color = if (displayedAlarm != "No Alarm") Color.White else Color.DarkGray,
                    fontSize = if (displayedAlarm != "No Alarm") 30.sp else 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Derived directly from your phone's default built-in alarm settings.",
                    color = Color.LightGray.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Right Pane: Native Integration Interface Actions
        Column(
            modifier = Modifier
                .weight(0.9f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NATIVE SYSTEM SYNC",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    try {
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmPage", "Failed to launch ACTION_SHOW_ALARMS", e)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Clock App", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set System Alarm", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scheduling alarms inside your phone's clock ensures maximum reliability, hardware-level wake schedules, and wearable sync.",
                color = Color.Gray,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    if (showAddDialog) {
        AddSystemAlarmDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { hour, minute ->
                try {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, "Standby Display Alarm")
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    // Update state flow after adding
                    showAddDialog = false
                    viewModel.updateNextAlarm()
                } catch (e: Exception) {
                    android.util.Log.e("AlarmPage", "Failed to launch ACTION_SET_ALARM", e)
                }
            }
        )
    }
}

@Composable
fun AddSystemAlarmDialog(onDismiss: () -> Unit, onAdd: (Int, Int) -> Unit) {
    var hourText by remember { mutableStateOf("08") }
    var minuteText by remember { mutableStateOf("00") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pre-set System Alarm") },
        text = {
            Column {
                Text(
                    "Select the timing to send to your default phone clock app:",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                hourText = input
                            }
                        },
                        label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { input ->
                            if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                minuteText = input
                            }
                        },
                        label = { Text("Minute (0-59)") },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 8
                val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onAdd(h, m)
            }) {
                Text("Send to Clock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
