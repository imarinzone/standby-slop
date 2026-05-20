package com.example.ui.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CalendarPage(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
    standbyViewModel: StandbyViewModel = viewModel()
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val error by viewModel.error.collectAsState()
    
    val selectedColorIdx by standbyViewModel.colorPage2.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage2.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    val calendarPermissionState = rememberPermissionState(
        android.Manifest.permission.READ_CALENDAR
    )

    LaunchedEffect(calendarPermissionState.status.isGranted) {
        if (calendarPermissionState.status.isGranted) {
            viewModel.loadLocalEvents(context)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Local Calendar", 
                    color = Color.White, 
                    fontSize = 32.sp, 
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont
                )
                if (calendarPermissionState.status.isGranted) {
                    Button(
                        onClick = { viewModel.loadLocalEvents(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Refresh", fontFamily = customFont, color = if (accentColor == Color.White) Color.Black else Color.White)
                    }
                }
            }
            
            if (!calendarPermissionState.status.isGranted) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "This app needs access to your native on-device calendar to safely display upcoming events on your Standby Screen.",
                        color = Color.LightGray,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = customFont,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                    Button(
                        onClick = { calendarPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Grant Calendar Access", fontFamily = customFont, color = if (accentColor == Color.White) Color.Black else Color.White)
                    }
                }
            } else {
                if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, color = Color.Red, fontSize = 16.sp, textAlign = TextAlign.Center, fontFamily = customFont)
                    }
                } else if (events.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No upcoming events found on your device.", color = Color.Gray, fontSize = 18.sp, fontFamily = customFont)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(events) { event ->
                            LocalEventItem(event, customFont, accentColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalEventItem(event: LocalCalendarEvent, customFont: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default, accentColor: Color = Color.White) {
    val startFormat = remember { SimpleDateFormat("EEE, MMM dd 'at' hh:mm a", Locale.getDefault()) }
    val endFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val allDayFormat = remember { SimpleDateFormat("EEE, MMM dd (All Day)", Locale.getDefault()) }

    val formattedTime = if (event.allDay) {
        allDayFormat.format(Date(event.dtStart))
    } else {
        val startStr = startFormat.format(Date(event.dtStart))
        val endStr = endFormat.format(Date(event.dtEnd))
        "$startStr - $endStr"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = event.title, 
                color = Color.White, 
                fontWeight = FontWeight.SemiBold, 
                fontFamily = customFont,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedTime, 
                color = Color(0xFF94A3B8),
                fontFamily = customFont,
                fontSize = 14.sp
            )
            if (!event.eventLocation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 ${event.eventLocation}", 
                    color = accentColor, 
                    fontFamily = customFont,
                    fontSize = 13.sp
                )
            }
            if (!event.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = event.description, 
                    color = Color(0xFFCBD5E1),
                    fontFamily = customFont,
                    fontSize = 14.sp,
                    maxLines = 2
                )
            }
        }
    }
}
