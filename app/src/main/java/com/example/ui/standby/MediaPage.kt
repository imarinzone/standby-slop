package com.example.ui.standby

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay

data class InstalledMusicApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable?
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MediaPage(modifier: Modifier = Modifier, standbyViewModel: StandbyViewModel = viewModel()) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var isMusicActive by remember {
        mutableStateOf(
            try {
                audioManager?.isMusicActive == true
            } catch (e: Exception) {
                false
            }
        )
    }
    var installedApps by remember { mutableStateOf<List<InstalledMusicApp>>(emptyList()) }

    val selectedColorIdx by standbyViewModel.colorPage3.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage3.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    // Notification Permission Handling for Android 13+
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var isNotificationListenerGranted by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }

    LaunchedEffect(notificationPermissionState?.status?.isGranted) {
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) ||
                    (notificationPermissionState?.status?.isGranted == true)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    // Query installed music apps
    LaunchedEffect(Unit) {
        installedApps = try {
            getInstalledMusicApps(context)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Monitor playback activity & permissions periodic sync
    LaunchedEffect(Unit) {
        while (true) {
            isMusicActive = try {
                audioManager?.isMusicActive == true
            } catch (e: Exception) {
                false
            }
            isNotificationListenerGranted = isNotificationListenerEnabled(context)
            if (Build.VERSION.SDK_INT >= 33) {
                hasNotificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "System Music Controls",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = customFont,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Dynamic special notification access card (highest priority)
            if (!isNotificationListenerGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Special Notification Access Required",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable Notification Sync settings to let Standby recognize and control active music applications.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { openNotificationListenerSettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 1.dp)
                        ) {
                            Text(
                                "Grant Access",
                                color = if (accentColor == Color.White) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else if (!hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Permission Recommended",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Allows the display to sync accurately with active media players.",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Button(
                            onClick = { notificationPermissionState?.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 1.dp)
                        ) {
                            Text(
                                "Grant",
                                color = if (accentColor == Color.White) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (isMusicActive) {
                // Active State Display
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Active Playing",
                            tint = accentColor,
                            modifier = Modifier.size(60.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "Native Media Playing",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Controlling active background audio",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Transport Actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Song",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                            modifier = Modifier
                                .size(72.dp)
                                .background(accentColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause, // Music is active, so show Pause
                                contentDescription = "Play/Pause Song",
                                tint = if (accentColor == Color.White) Color.Black else Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Song",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            } else {
                // Inactive State Display -> Ask to launch music applications
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No music is currently playing locally.",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (installedApps.isNotEmpty()) {
                        Text(
                            text = "Launch a music player to start playback:",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(vertical = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(installedApps) { app ->
                                MusicAppItem(app = app) {
                                    launchMusicApp(context, app.packageName)
                                }
                            }
                        }
                    } else {
                        // Display placeholder instruction
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Install or open a music app (Spotify, YT Music, etc.) on your device to enable Standby media controls.",
                                color = Color.Gray,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicAppItem(app: InstalledMusicApp, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val bitmap = remember(app.icon) {
                try {
                    app.icon?.toBitmap()?.asImageBitmap()
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "App Icon Placeholder",
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun getInstalledMusicApps(context: Context): List<InstalledMusicApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_APP_MUSIC)
    }
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val apps = mutableListOf<InstalledMusicApp>()

    for (info in resolveInfos) {
        val name = info.loadLabel(pm).toString()
        val packageName = info.activityInfo.packageName
        val icon = info.loadIcon(pm)
        apps.add(InstalledMusicApp(name, packageName, icon))
    }

    // Common music packages backup
    val commonPackages = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.apple.android.music" to "Apple Music",
        "com.amazon.mp3" to "Amazon Music",
        "deezer.android.app" to "Deezer",
        "com.soundcloud.android" to "SoundCloud"
    )

    if (apps.isEmpty()) {
        for ((pkg, name) in commonPackages) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(info)
                val appLabel = pm.getApplicationLabel(info).toString()
                apps.add(InstalledMusicApp(appLabel, pkg, icon))
            } catch (e: Exception) {
                // Not found
            }
        }
    }

    return apps.distinctBy { it.packageName }
}

private fun launchMusicApp(context: Context, packageName: String) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun sendMediaCommand(context: Context, keyCode: Int) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let {
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            it.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = android.content.ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}

private fun openNotificationListenerSettings(context: Context) {
    try {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            android.util.Log.e("MediaPage", "Failed to open settings pages", ex)
        }
    }
}
