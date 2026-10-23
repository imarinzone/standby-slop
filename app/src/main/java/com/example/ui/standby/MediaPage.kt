package com.example.ui.standby

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
fun MediaPage(
    modifier: Modifier = Modifier,
    standbyViewModel: StandbyViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme

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

    val trackTitle by MediaStateHolder.trackTitle.collectAsState()
    val artistName by MediaStateHolder.artistName.collectAsState()
    val isPlayingFlow by MediaStateHolder.isPlaying.collectAsState()
    val albumArt by MediaStateHolder.albumArt.collectAsState()
    val activeAppPackage by MediaStateHolder.activeAppPackage.collectAsState()

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
        installedApps = try {
            getInstalledMusicApps(context)
        } catch (e: Exception) {
            emptyList()
        }
    }

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
                                        haptics.tick()
                                        standbyViewModel.setUseClassicMediaTheme(true)
                                    }
                                } else if (verticalDragAccumulator > 80f) {
                                    if (isClassicTheme) {
                                        haptics.tick()
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
                    // Material 3 Media Launcher Hub when no track is playing
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
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
                                        imageVector = Icons.Outlined.LibraryMusic,
                                        contentDescription = null,
                                        tint = colorScheme.onPrimaryContainer,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Media Hub",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colorScheme.onSurface,
                                        fontFamily = customFont,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "No active playback session detected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!isNotificationListenerGranted) {
                                FilledTonalButton(
                                    onClick = {
                                        haptics.confirm()
                                        openNotificationListenerSettings(context)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Link System Player",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Link System Player",
                                        fontFamily = customFont,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (installedApps.isNotEmpty()) {
                            Text(
                                text = "Launch an installed music app to start listening",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 156.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                items(installedApps) { app ->
                                    MusicAppItem(app = app) {
                                        haptics.tick()
                                        launchMusicApp(context, app.packageName)
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                ElevatedCard(
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = colorScheme.surfaceContainerLow
                                    ),
                                    modifier = Modifier.widthIn(max = 460.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(64.dp),
                                            shape = CircleShape,
                                            color = colorScheme.secondaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Album,
                                                    contentDescription = null,
                                                    tint = colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Ready for Playback",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colorScheme.onSurface,
                                            fontFamily = customFont,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Start playing audio in Spotify, YouTube Music, or any media app to control playback from Standby.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
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
fun MusicAppItem(app: InstalledMusicApp, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "App Icon",
                            tint = colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Text(
                text = app.name,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
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

    val commonPackages = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.apple.android.music" to "Apple Music",
        "com.amazon.mp3" to "Amazon Music",
        "deezer.android.app" to "Deezer",
        "com.soundcloud.android" to "SoundCloud"
    )

    if (apps.isEmpty()) {
        for ((pkg, _) in commonPackages) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(info)
                val appLabel = pm.getApplicationLabel(info).toString()
                apps.add(InstalledMusicApp(appLabel, pkg, icon))
            } catch (_: Exception) {
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
    if (bitmap == null || bitmap.isRecycled) return Color(0xFF1E293B)
    try {
        if (bitmap.isRecycled) return Color(0xFF1E293B)
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
            hsv[2] = (hsv[2] * 0.35f).coerceIn(0.15f, 0.55f)
            hsv[1] = (hsv[1] * 1.15f).coerceIn(0.25f, 0.9f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
    } catch (_: Exception) {
    }
    return Color(0xFF1E293B)
}

@Composable
fun SquigglySeekBar(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null
) {
    val haptics = rememberStandbyHaptics()
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
            .height(28.dp)
            .pointerInput(onSeek) {
                if (onSeek != null) {
                    detectTapGestures { offset ->
                        haptics.step()
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
                    x += 2f
                }
                sqPath.lineTo(progressX, centerY)

                drawPath(
                    path = sqPath,
                    color = accentColor,
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            if (progressX < width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(progressX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = accentColor,
                radius = 7.dp.toPx(),
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
    val haptics = rememberStandbyHaptics()
    val colorScheme = MaterialTheme.colorScheme
    val pmLabel = appInfo?.first ?: "System Media"
    val dominantColor = remember(albumArt) { getDominantColor(albumArt) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Black.copy(alpha = 0.86f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        dominantColor.copy(alpha = 0.45f),
                                        colorScheme.surfaceContainerHighest
                                    )
                                )
                            )
                    )
                }

                val contentFg = if (albumArt != null) Color.White else colorScheme.onSurface
                val secondaryFg = if (albumArt != null) Color.White.copy(alpha = 0.72f) else colorScheme.onSurfaceVariant

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top M3 Pill Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer.copy(alpha = 0.88f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Active Speaker",
                                    tint = colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = pmLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        AssistChip(
                            onClick = {
                                haptics.tick()
                                standbyViewModel.setUseClassicMediaTheme(true)
                            },
                            label = {
                                Text(
                                    text = "Classic Vinyl",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = customFont
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = "Classic Theme",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = colorScheme.surfaceContainer.copy(alpha = 0.85f),
                                labelColor = colorScheme.onSurface,
                                leadingIconContentColor = accentColor
                            )
                        )
                    }

                    // Track Metadata & Squiggly M3 Seekbar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = trackTitle ?: "Unknown Track",
                            color = contentFg,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistName ?: "Unknown Artist",
                            color = secondaryFg,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            var simulatedProgress by remember { mutableFloatStateOf(0.35f) }

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
                                color = secondaryFg,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )

                            SquigglySeekBar(
                                progress = simulatedProgress,
                                accentColor = accentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                onSeek = { newProgress ->
                                    simulatedProgress = newProgress
                                }
                            )

                            Text(
                                text = "3:45",
                                color = secondaryFg,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Material 3 Expressive Media Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        FilledIconButton(
                            onClick = {
                                haptics.confirm()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            modifier = Modifier.size(width = 88.dp, height = 64.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor,
                                contentColor = if (accentColor == Color.White) Color.Black else Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        FilledTonalIconButton(
                            onClick = {
                                haptics.tick()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }

        EqualizerJumpingBars(
            isPlaying = isPlaying,
            accentColor = accentColor,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 40.dp, vertical = 2.dp)
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
            remember { mutableFloatStateOf(0.12f) }
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
    val haptics = rememberStandbyHaptics()
    var simulatedProgress by remember { mutableFloatStateOf(0.35f) }
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
                        Color(0xFF04141A),
                        Color(0xFF010608)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height * 0.5f
            val ragaLine = Path()
            ragaLine.moveTo(0f, centerY)
            for (x in 0..size.width.toInt() step 6) {
                val y = centerY + 24.dp.toPx() * kotlin.math.sin(x * 0.004f)
                ragaLine.lineTo(x.toFloat(), y)
            }
            drawPath(
                path = ragaLine,
                color = Color(0xFFD4AF37).copy(alpha = 0.05f),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF00D1FF).copy(alpha = 0.03f),
                radius = 150.dp.toPx(),
                center = Offset(size.width * 0.72f, centerY)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                AssistChip(
                    onClick = {
                        haptics.tick()
                        standbyViewModel.setUseClassicMediaTheme(false)
                    },
                    label = {
                        Text(
                            text = "Material 3 Theme",
                            fontSize = 11.sp,
                            fontFamily = customFont
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Switch to Material 3 Theme",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

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

                Text(
                    text = if (artistName != null) "BY ${artistName.uppercase()}" else "BY SYSTEM AUDIO",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = customFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(172.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .background(Color(0xFF051114))
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.6f)
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        FilledIconButton(
                            onClick = {
                                haptics.confirm()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            },
                            modifier = Modifier
                                .size(60.dp)
                                .align(Alignment.Center),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor,
                                contentColor = if (accentColor == Color.White) Color.Black else Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .size(72.dp)
                                .align(Alignment.Center)
                        ) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.15f),
                                radius = 34.dp.toPx(),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            drawArc(
                                color = accentColor,
                                startAngle = -90f,
                                sweepAngle = 360f * simulatedProgress,
                                useCenter = false,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.tick()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.tick()
                                sendMediaCommand(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

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
