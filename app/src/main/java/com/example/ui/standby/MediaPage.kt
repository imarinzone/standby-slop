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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap

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

    // Dynamic states from live notification observer & media sessions
    val trackTitle by MediaStateHolder.trackTitle.collectAsState()
    val artistName by MediaStateHolder.artistName.collectAsState()
    val isPlayingFlow by MediaStateHolder.isPlaying.collectAsState()
    val albumArt by MediaStateHolder.albumArt.collectAsState()
    val activeAppPackage by MediaStateHolder.activeAppPackage.collectAsState()

    // Query active application metadata
    val appInfo = remember(activeAppPackage) {
        if (activeAppPackage != null) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(activeAppPackage!!, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(info)
                label to icon
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    var installedApps by remember { mutableStateOf<List<InstalledMusicApp>>(emptyList()) }

    val selectedColorIdx by standbyViewModel.colorPage3.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage3.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first
    val isClassicTheme by standbyViewModel.useClassicMediaTheme.collectAsState()

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

    val isCurrentlyPlaying = isPlayingFlow || isMusicActive || (!trackTitle.isNullOrEmpty() && trackTitle != "No Track")

    val bgUri by standbyViewModel.getBgUriFlow(3).collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isClassicTheme) {
                    var verticalDragAccumulator = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalDragAccumulator = 0f
                        },
                        onDragEnd = {
                            if (verticalDragAccumulator < -80f) {
                                if (!isClassicTheme) {
                                    standbyViewModel.setUseClassicMediaTheme(true)
                                }
                            } else if (verticalDragAccumulator > 80f) {
                                if (isClassicTheme) {
                                    standbyViewModel.setUseClassicMediaTheme(false)
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            verticalDragAccumulator += dragAmount
                        }
                    )
                }
        ) {
            if (isCurrentlyPlaying) {
                if (isClassicTheme) {
                    CinematicRetroPlayer(
                        trackTitle = trackTitle,
                        artistName = artistName,
                        albumArt = albumArt,
                        isPlaying = isPlayingFlow,
                        accentColor = accentColor,
                        customFont = customFont,
                        context = context,
                        standbyViewModel = standbyViewModel
                    )
                } else {
                    MaterialYouExpressivePlayer(
                        trackTitle = trackTitle,
                        artistName = artistName,
                        albumArt = albumArt,
                        isPlaying = isPlayingFlow,
                        appInfo = appInfo,
                        accentColor = accentColor,
                        customFont = customFont,
                        context = context,
                        standbyViewModel = standbyViewModel
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                if (!isNotificationListenerGranted) {
                    Button(
                        onClick = { openNotificationListenerSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Link System Player",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
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

private fun getDominantColor(bitmap: android.graphics.Bitmap?): Color {
    if (bitmap == null || bitmap.isRecycled) return Color.Black // True AMOLED Black for maximum power saving
    try {
        if (bitmap.isRecycled) return Color.Black
        val width = bitmap.width
        val height = bitmap.height
        val stepX = (width / 5).coerceAtLeast(1)
        val stepY = (height / 5).coerceAtLeast(1)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0
        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                sumR += android.graphics.Color.red(pixel)
                sumG += android.graphics.Color.green(pixel)
                sumB += android.graphics.Color.blue(pixel)
                count++
            }
        }
        if (count > 0) {
            val r = (sumR / count).toInt()
            val g = (sumG / count).toInt()
            val b = (sumB / count).toInt()
            val hsv = FloatArray(3)
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            hsv[2] = (hsv[2] * 0.22f).coerceIn(0.12f, 0.45f)
            hsv[1] = (hsv[1] * 1.2f).coerceIn(0.3f, 0.95f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    } catch (e: Exception) {
        // ignore and fallback
    }
    return Color.Black // True AMOLED Black for maximum power saving
}

@Composable
fun SquigglySeekBar(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(onSeek) {
                if (onSeek != null) {
                    detectTapGestures { offset ->
                        val clickedProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek.invoke(clickedProgress)
                    }
                }
            }
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val centerY = height / 2f
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progressX = width * progress
            
            // Draw played (squiggly) part
            if (progressX > 0f) {
                val sqPath = Path()
                val wavelength = 24.dp.toPx()
                val amplitude = 3.5.dp.toPx()
                
                sqPath.moveTo(0f, centerY)
                var x = 0f
                while (x < progressX) {
                    val relativeX = x / wavelength
                    val y = centerY + amplitude * kotlin.math.sin(relativeX * 2 * kotlin.math.PI.toFloat() - phaseShift)
                    sqPath.lineTo(x, y)
                    x += 2f // high resolution
                }
                sqPath.lineTo(progressX, centerY) // snap to center before thumb
                
                drawPath(
                    path = sqPath,
                    color = accentColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
            
            // Draw track (remaining part) - straight line
            if (progressX < width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(progressX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            // Draw Thumb
            drawCircle(
                color = accentColor,
                radius = 6.dp.toPx(),
                center = Offset(progressX, centerY)
            )
        }
    }
}

@Composable
fun MaterialYouExpressivePlayer(
    trackTitle: String?,
    artistName: String?,
    albumArt: android.graphics.Bitmap?,
    isPlaying: Boolean,
    appInfo: Pair<String, android.graphics.drawable.Drawable>?,
    accentColor: Color,
    customFont: FontFamily,
    context: Context,
    standbyViewModel: StandbyViewModel
) {
    val pmLabel = appInfo?.first ?: "System Player"
    val dominantColor = remember(albumArt) { getDominantColor(albumArt) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(dominantColor.copy(alpha = 0.3f), Color.Black) // True AMOLED Black base with subtle dynamic top glow
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(280.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "Background Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.35f), Color.Transparent)))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Row with Connected Speaker & Swipe Guide
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { /* Volume decorational feedback */ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Active Speaker",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Theme indicator & trigger
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { standbyViewModel.setUseClassicMediaTheme(true) }
                                .padding(4.dp)
                        ) {
                            Text(
                                "SWIPE UP FOR CLASSIC THEME",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFont,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Classic Theme",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Metadata Titles and Seek progress
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trackTitle ?: "Unknown Track",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistName ?: "Unknown Artist",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var simulatedProgress by remember { mutableStateOf(0.35f) }
                            
                            LaunchedEffect(isPlaying) {
                                if (isPlaying) {
                                    while (true) {
                                        delay(1000)
                                        simulatedProgress = (simulatedProgress + 0.005f).coerceAtMost(1f)
                                        if (simulatedProgress >= 1f) {
                                            simulatedProgress = 0f
                                        }
                                    }
                                }
                            }

                            val elapsedSeconds = (simulatedProgress * 225).toInt() 
                            val minutesVal = elapsedSeconds / 60
                            val secondsVal = elapsedSeconds % 60
                            val elapsedMinutesStr = "$minutesVal:${secondsVal.toString().padStart(2, '0')}"

                            Text(
                                text = elapsedMinutesStr,
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = customFont
                            )

                            SquigglySeekBar(
                                progress = simulatedProgress,
                                accentColor = accentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                onSeek = { newProgress ->
                                    simulatedProgress = newProgress
                                }
                            )

                            Text(
                                text = "3:45",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontFamily = customFont
                            )
                        }
                    }

                    // Media Action Controls Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        Surface(
                            modifier = Modifier
                                .size(76.dp)
                                .clickable { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                            shape = RoundedCornerShape(26.dp),
                            color = accentColor
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = if (accentColor == Color.White) Color.Black else Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
        }

        // Equalizer bits jumping at the bottom of the card/screen based on the music playing
        EqualizerJumpingBars(
            isPlaying = isPlaying,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 24.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun EqualizerJumpingBars(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    
    // Create height animations for each bar to jump asynchronously
    val animations = (0 until barCount).map { index ->
        if (isPlaying) {
            val duration = remember { (400 + (index % 5) * 120 + (index % 3) * 70) }
            transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = duration
                        0.1f at 0
                        0.8f at (duration * 0.3).toInt()
                        0.2f at (duration * 0.5).toInt()
                        0.95f at (duration * 0.75).toInt()
                        0.1f at duration
                    },
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        } else {
            remember { mutableStateOf(0.12f) }
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        animations.forEach { animState ->
            val heightFraction = animState.value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun CinematicRetroPlayer(
    trackTitle: String?,
    artistName: String?,
    albumArt: android.graphics.Bitmap?,
    isPlaying: Boolean,
    accentColor: Color,
    customFont: FontFamily,
    context: Context,
    standbyViewModel: StandbyViewModel
) {
    var simulatedProgress by remember { mutableStateOf(0.35f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(1000)
                simulatedProgress = (simulatedProgress + 0.005f).coerceAtMost(1f)
                if (simulatedProgress >= 1f) {
                    simulatedProgress = 0f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF04141A), // Moody deep teal
                        Color(0xFF010608)  // Atmospheric rich black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Overlay elegant classic vinyl contours in background using a custom Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height * 0.5f
            
            // Decorative audio waves representing the melodic raga lines
            val ragaLine = Path()
            ragaLine.moveTo(0f, centerY)
            for (x in 0..size.width.toInt() step 6) {
                val y = centerY + 24.dp.toPx() * kotlin.math.sin(x * 0.004f)
                ragaLine.lineTo(x.toFloat(), y)
            }
            drawPath(
                path = ragaLine,
                color = Color(0xFFD4AF37).copy(alpha = 0.05f), // Pale classical gold string
                style = Stroke(width = 1.dp.toPx())
            )

            // Dynamic golden aura centered around controls
            drawCircle(
                color = Color(0xFF00D1FF).copy(alpha = 0.03f),
                radius = 150.dp.toPx(),
                center = Offset(size.width * 0.72f, centerY)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // Left Content: Classical typography + Artist
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                // Return trigger hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { standbyViewModel.setUseClassicMediaTheme(false) }
                        .padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Swipe Down",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SWIPE DOWN FOR MODERN THEME",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontFamily = customFont
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Uppercased track text exactly matching retro cinema layout
                Text(
                    text = (trackTitle ?: "MAYABONO BIHARINI").uppercase(),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont,
                    lineHeight = 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist description or Mohai Minul reference
                Text(
                    text = if (artistName != null) "BY ${artistName.uppercase()}" else "BY MOHAI MINUL",
                    color = Color(0xFF00D1FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = customFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Cinematic Retro metadata note
                Text(
                    text = "BENGALI CINEMATIC CLASSIC",
                    color = Color(0xFFD4AF37).copy(alpha = 0.65f), // Soft classical gold
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    fontFamily = customFont
                )
            }

            // Right Content: Bespoke circular control wheel layout
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                        .background(Color(0xFF051114))
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Raga Master Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.6f)
                        )
                    } else {
                        // Classical Indian vector Canvas representation drawing a graceful lady in teal saree with flowers
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Silk pleat flowing arc (teal saree)
                            val sareePath = Path().apply {
                                moveTo(size.width * 0.25f, size.height * 0.85f)
                                cubicTo(
                                    size.width * 0.35f, size.height * 0.45f,
                                    size.width * 0.68f, size.height * 0.35f,
                                    size.width * 0.8f, size.height * 0.65f
                                )
                                cubicTo(
                                    size.width * 0.68f, size.height * 0.82f,
                                    size.width * 0.45f, size.height * 0.92f,
                                    size.width * 0.25f, size.height * 0.85f
                                )
                            }
                            drawPath(
                                path = sareePath,
                                color = Color(0xFF0C7A93).copy(alpha = 0.55f) // Teal saree
                            )

                            // Saree crimson border ribbon
                            drawPath(
                                path = sareePath,
                                color = Color(0xFFDC2626).copy(alpha = 0.35f), // Crimson red border
                                style = Stroke(width = 2.5.dp.toPx())
                            )

                            // Minimalist golden halo
                            drawCircle(
                                color = Color(0xFFFBBF24).copy(alpha = 0.25f),
                                radius = 20.dp.toPx(),
                                center = Offset(size.width * 0.55f, size.height * 0.38f)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        // Center Play Button (blue filled with white indicator)
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(Color(0xFF3B82F6)) // Bright classical blue
                                .clickable { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Circular seeking border dynamic arc surrounding Play/Pause button
                        Canvas(
                            modifier = Modifier
                                .size(70.dp)
                                .align(Alignment.Center)
                        ) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = 32.dp.toPx(),
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawArc(
                                color = Color(0xFF00D1FF),
                                startAngle = -90f,
                                sweepAngle = 360f * simulatedProgress,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Top: Shuffle helper
                        IconButton(
                            onClick = { /* Simulated shuffle button */ },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Bottom: Repeat helper
                        IconButton(
                            onClick = { /* Simulated repeat button */ },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = "Repeat",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Left: Skip Previous
                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Right: Skip Next
                        IconButton(
                            onClick = { sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT) },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Jumping equalizer bars at bottom of cinematic module
        EqualizerJumpingBars(
            isPlaying = isPlaying,
            accentColor = Color(0xFF00D1FF), // Dynamic cyan equalizer
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 24.dp, vertical = 2.dp)
        )
    }
}

