package com.example.ui.standby

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1. High-Precision Equirectangular World Map Dot Matrix (92 cols x 42 rows)
 *    Longitude: -180° (left) to +180° (right)
 *    Latitude:  +82°N (top) to -62°S (bottom)
 */
private val WORLD_MAP_ASCII = listOf(
    "............................###########.....................................................",
    ".......................#################................#####.........##########............",
    "........#####.........###################............#########...######################.....",
    "....############.......#################...........###########.#############################",
    "..################......###############........#############################################",
    ".###################.....############.........##############################################",
    "...###################....#########..........#############################################..",
    ".....###################....#####.......##...############################################...",
    "......####################.............####.############################################....",
    ".......####################...........#####.###########################################.....",
    "........###################...........################################################..##..",
    ".........##################..........######..#########################################..###.",
    ".........#################..........#####....####..###################################..###.",
    "..........###############...........####.......##...#################################...##..",
    "...........#############............###.............################################...###..",
    "............###########................#############.##############################....##...",
    ".............#########...............################.############################..........",
    "..............#######...............#################..##########################...........",
    "...............#####..##............#################....########################...........",
    "................###....##...........##################....######################............",
    ".................##.................###################.....#######..##########.............",
    "..................##................####################.....#####....########...#..........",
    "...................####..............###################......###......######....##.........",
    "....................#######...........##################.......#........####.....##.........",
    "....................#########..........#################.................###................",
    "....................###########..........##############..................###..##..###.......",
    ".....................###########..........#############...................##..########......",
    ".....................############..........###########........................#########.....",
    "......................###########...........##########.........................###..####....",
    "......................##########............#########.......................................",
    ".......................#########............########.........................#########......",
    ".......................########.............#######........................############.....",
    "........................######..............######........................##############....",
    "........................#####...............#####.........................##############....",
    ".........................####................###..........................#############.....",
    ".........................###.................##............................###########......",
    ".........................###................................................####...##.......",
    "..........................##.......................................................#.....##.",
    "..........................#.............................................................###.",
    "..........................#..............................................................#.."
)

@Composable
fun WorldTimeZoneMapClockFace(
    calendar: Calendar,
    accentColor: Color,
    customFont: FontFamily,
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    nextAlarmLabel: String? = null,
    isDuoCompact: Boolean = false,
    standbyViewModel: StandbyViewModel? = null,
    paneIndex: Int = 0
) {
    val vm: StandbyViewModel = standbyViewModel ?: viewModel()
    val locationMode by vm.locationMode.collectAsState()
    val customLocation by vm.customLocationName.collectAsState()
    val clockTzId by vm.clockTimeZoneId.collectAsState()
    val duoLeftTzId by vm.duoLeftTimeZoneId.collectAsState()
    val duoRightTzId by vm.duoRightTimeZoneId.collectAsState()
    val duoLeftLoc by vm.duoLeftLocationName.collectAsState()
    val duoRightLoc by vm.duoRightLocationName.collectAsState()
    val gpsLocation by vm.gpsLocationName.collectAsState()
    val gpsLat by vm.gpsLatitude.collectAsState()
    val gpsLon by vm.gpsLongitude.collectAsState()

    val regionInfo = remember(
        paneIndex, locationMode, customLocation, clockTzId,
        duoLeftTzId, duoRightTzId, duoLeftLoc, duoRightLoc,
        gpsLocation, gpsLat, gpsLon, calendar.timeZone
    ) {
        vm.resolvePaneLocationInfo(paneIndex)
    }

    val activeTimeZone = remember(regionInfo.timeZoneId) {
        TimeZone.getTimeZone(regionInfo.timeZoneId)
    }

    val timeFormat = remember(use24Hour, activeTimeZone) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault()).apply {
            timeZone = activeTimeZone
        }
    }
    val timeString = timeFormat.format(calendar.time)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = if (isDuoCompact) 10.dp else 18.dp, vertical = if (isDuoCompact) 8.dp else 12.dp)
            .testTag("world_map_clock_face")
    ) {
        val clockFontSize = if (isDuoCompact) {
            (minOf(maxWidth.value * 0.24f, maxHeight.value * 0.30f)).coerceIn(32f, 64f).sp
        } else {
            (minOf(maxWidth.value * 0.15f, maxHeight.value * 0.30f) * scale).coerceIn(48f, 96f).sp
        }

        // Full-Screen Dotted World Map Canvas + Solar Terminator Curve + User Standard Time Zone Pin
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val rows = WORLD_MAP_ASCII.size
            val cols = WORLD_MAP_ASCII[0].length
            val mapWidth = size.width
            val mapHeight = size.height
            val stepX = mapWidth / cols.toFloat()
            val stepY = mapHeight / rows.toFloat()
            val dotRadius = (min(stepX, stepY) * 0.33f).coerceIn(1.4f, 4.6f)

            // Compute UTC solar position for accurate daylight terminator curve
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = calendar.timeInMillis
            }
            val utcHourDecimal = utcCal.get(Calendar.HOUR_OF_DAY) + utcCal.get(Calendar.MINUTE) / 60f
            // Subsolar longitude moves from +180° at 00:00 UTC to 0° at 12:00 UTC to -180° at 24:00 UTC
            val subsolarNormX = ((12f - utcHourDecimal) / 24f) + 0.5f

            fun terminatorY(normalizedX: Float): Float {
                val deltaPhase = (normalizedX - subsolarNormX) * 2f * PI.toFloat()
                val wave = cos(deltaPhase)
                return (mapHeight * 0.52f) - wave * (mapHeight * 0.34f)
            }

            for (r in 0 until rows) {
                val line = WORLD_MAP_ASCII[r]
                for (c in 0 until cols) {
                    if (line[c] == '#') {
                        val cx = (c + 0.5f) * stepX
                        val cy = (r + 0.5f) * stepY
                        val normX = c.toFloat() / cols.toFloat()
                        val curveY = terminatorY(normX)
                        val isDaylight = cy < curveY
                        drawCircle(
                            color = if (isDaylight) Color.White.copy(alpha = 0.94f) else Color(0xFF4B5262).copy(alpha = 0.68f),
                            radius = dotRadius,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            // Smooth Day/Night Solar Terminator Curve
            val curvePath = Path()
            val samples = 96
            for (i in 0..samples) {
                val t = i.toFloat() / samples.toFloat()
                val x = t * mapWidth
                val y = terminatorY(t).coerceIn(4f, mapHeight - 4f)
                if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
            }
            drawPath(
                path = curvePath,
                color = Color.White.copy(alpha = 0.52f),
                style = Stroke(width = (1.8f * scale).coerceIn(1.2f, 3.0f), cap = StrokeCap.Round)
            )

            // Convert (lon, lat) to equirectangular map coordinates
            fun lonToNormX(lon: Float): Float = ((lon + 180f) / 360f).coerceIn(0.04f, 0.96f)
            fun latToNormY(lat: Float): Float = ((82f - lat) / 144f).coerceIn(0.06f, 0.92f)

            val pinColor = Color(0xFFFF9500)
            // Secondary world hub pins (New York, London, Tokyo, Sydney, São Paulo)
            val referenceHubs = listOf(
                -122.42f to 37.77f, // San Francisco
                -74.01f to 40.71f,  // New York
                -0.13f to 51.51f,   // London
                77.21f to 28.61f,   // New Delhi
                139.77f to 35.68f,  // Tokyo
                151.21f to -33.87f  // Sydney
            )
            referenceHubs.forEach { (hubLon, hubLat) ->
                val hx = lonToNormX(hubLon) * mapWidth
                val hy = latToNormY(hubLat) * mapHeight
                drawCircle(
                    color = pinColor.copy(alpha = 0.82f),
                    radius = dotRadius * 1.65f,
                    center = Offset(hx, hy)
                )
            }

            // Primary User Standard Time / Location Pin with Glowing White & Orange Halo
            val userPx = lonToNormX(regionInfo.longitude) * mapWidth
            val userPy = latToNormY(regionInfo.latitude) * mapHeight
            drawCircle(
                color = pinColor.copy(alpha = 0.28f),
                radius = dotRadius * 4.8f,
                center = Offset(userPx, userPy)
            )
            drawCircle(
                color = Color.White,
                radius = dotRadius * 2.9f,
                center = Offset(userPx, userPy)
            )
            drawCircle(
                color = pinColor,
                radius = dotRadius * 1.95f,
                center = Offset(userPx, userPy)
            )
        }

        // Single Bottom-Left Region + User's Standard Time Display (Top duplicate clock removed)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = Color(0xFFFF9F0A),
                    modifier = Modifier.size(if (isDuoCompact) 13.dp else 16.dp)
                )
                Text(
                    text = "${regionInfo.regionName} • ${regionInfo.standardTimeShort}",
                    color = Color(0xFFFF9F0A),
                    fontSize = if (isDuoCompact) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = timeString,
                color = Color.White,
                fontSize = clockFontSize,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = customFont,
                letterSpacing = (-1.5).sp,
                lineHeight = clockFontSize,
                maxLines = 1
            )
        }
    }
}

/**
 * 2. StandBy Giant Sage Clock + Right Sidebar Complications (Matches lcimg_b83618b0_1284_459a_b6f6_6647aaa24d8b.jpg)
 */
@Composable
fun StandbySageComplicationClockFace(
    calendar: Calendar,
    accentColor: Color,
    customFont: FontFamily,
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    weatherTempCelsius: Double? = null,
    nextAlarmLabel: String? = null,
    isDuoCompact: Boolean = false
) {
    val sageColor = if (accentColor == Color.White) Color(0xFF7CC67F) else accentColor
    val activeTz = calendar.timeZone ?: TimeZone.getDefault()
    val timeFormat = remember(use24Hour, activeTz) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault()).apply {
            timeZone = activeTz
        }
    }
    val dayOfWeek = remember(calendar.get(Calendar.DAY_OF_WEEK), activeTz) {
        SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = activeTz }.format(calendar.time).uppercase(Locale.getDefault())
    }
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH).toString()

    val tempDisplay = remember(weatherTempCelsius) {
        if (weatherTempCelsius != null) {
            "${weatherTempCelsius.roundToInt()}°"
        } else {
            "76°"
        }
    }

    val alarmShort = remember(nextAlarmLabel) {
        if (!nextAlarmLabel.isNullOrBlank() && nextAlarmLabel != "No Alarm") {
            nextAlarmLabel.substringAfter(" ").substringBefore(" (").replace(" ", "").uppercase(Locale.getDefault()).ifBlank { "6:30AM" }
        } else {
            "6:30AM"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = if (isDuoCompact) 10.dp else 24.dp, vertical = if (isDuoCompact) 8.dp else 14.dp)
            .testTag("standby_sage_complication_clock")
    ) {
        val mainNumeralSize = if (isDuoCompact) {
            (minOf(maxWidth.value * 0.34f, maxHeight.value * 0.72f)).coerceIn(54f, 140f).sp
        } else {
            (minOf(maxWidth.value * 0.27f, maxHeight.value * 0.80f) * scale).coerceIn(88f, 230f).sp
        }
        val sideLabelSize = if (isDuoCompact) {
            (minOf(maxWidth.value, maxHeight.value) * 0.075f).coerceIn(14f, 22f).sp
        } else {
            24.sp
        }
        val sideSubSize = if (isDuoCompact) {
            (minOf(maxWidth.value, maxHeight.value) * 0.09f).coerceIn(16f, 26f).sp
        } else {
            28.sp
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Giant Condensed Sage Green Digital Numerals (9:41)
            Box(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = timeFormat.format(calendar.time),
                    color = sageColor,
                    fontSize = mainNumeralSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = customFont,
                    letterSpacing = (-3).sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Right: Top Date + Temp & Bottom Alarm Complication
            Column(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight()
                    .padding(vertical = if (isDuoCompact) 12.dp else 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = dayOfWeek,
                            color = sageColor,
                            fontSize = sideLabelSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            maxLines = 1
                        )
                        Text(
                            text = dayOfMonth,
                            color = Color.White,
                            fontSize = sideLabelSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = tempDisplay,
                        color = Color.White,
                        fontSize = sideSubSize,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = customFont,
                        maxLines = 1
                    )
                }

                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = "Scheduled Alarm",
                        tint = sageColor,
                        modifier = Modifier.size(if (isDuoCompact) 22.dp else 28.dp)
                    )
                    Text(
                        text = alarmShort,
                        color = Color.White,
                        fontSize = sideLabelSize,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = customFont,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 3. Overlapping Float Bubble Numerals Clock Face & Duo Widget
 *    (Matches ios-17-iphone-standby-use-and-customize-8.jpg [6:38] & StandBy-on-iPhone.jpg Top-Right [4:41])
 */
@Composable
fun OverlappingFloatClockFace(
    calendar: Calendar,
    accentColor: Color,
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    isDuoCompact: Boolean = false
) {
    val primaryTone = if (accentColor == Color.White) Color(0xFF38BDF8) else accentColor
    val secondaryTone = Color(
        red = (primaryTone.red * 0.62f).coerceIn(0f, 1f),
        green = (primaryTone.green * 0.68f).coerceIn(0f, 1f),
        blue = (primaryTone.blue * 0.72f).coerceIn(0f, 1f),
        alpha = 0.86f
    )
    val colonTint = Color(
        red = (primaryTone.red * 0.4f + 0.6f).coerceIn(0f, 1f),
        green = (primaryTone.green * 0.4f + 0.6f).coerceIn(0f, 1f),
        blue = (primaryTone.blue * 0.4f + 0.6f).coerceIn(0f, 1f),
        alpha = 0.90f
    )

    val rawHour = calendar.get(Calendar.HOUR_OF_DAY)
    val hourStr = if (use24Hour) {
        String.format("%02d", rawHour)
    } else {
        val h12 = if (rawHour % 12 == 0) 12 else rawHour % 12
        h12.toString()
    }
    val minStr = String.format("%02d", calendar.get(Calendar.MINUTE))

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("overlapping_float_clock"),
        contentAlignment = Alignment.Center
    ) {
        val digitFontSize = if (isDuoCompact) {
            (minOf(maxWidth.value * 0.40f, maxHeight.value * 0.78f)).coerceIn(64f, 160f).sp
        } else {
            (minOf(maxWidth.value * 0.30f, maxHeight.value * 0.82f) * scale).coerceIn(92f, 220f).sp
        }
        val overlapStep = (digitFontSize.value * -0.11f).dp
        val colonDotSize = (digitFontSize.value * 0.19f).dp

        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(overlapStep)
            ) {
                hourStr.forEachIndexed { idx, ch ->
                    Text(
                        text = ch.toString(),
                        color = if (idx % 2 == 0) primaryTone else secondaryTone,
                        fontSize = digitFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = digitFontSize
                    )
                }

                minStr.forEachIndexed { idx, ch ->
                    Text(
                        text = ch.toString(),
                        color = if (idx == 0) secondaryTone else primaryTone.copy(alpha = 0.92f),
                        fontSize = digitFontSize,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = digitFontSize
                    )
                }
            }

            val hourRatio = hourStr.length.toFloat() / (hourStr.length + minStr.length).toFloat()
            val colonHorizontalOffset = ((hourRatio - 0.5f) * digitFontSize.value * 1.15f).dp
            Column(
                modifier = Modifier.offset(x = colonHorizontalOffset),
                verticalArrangement = Arrangement.spacedBy((digitFontSize.value * 0.16f).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(colonDotSize)
                        .clip(CircleShape)
                        .background(colonTint)
                )
                Box(
                    modifier = Modifier
                        .size(colonDotSize)
                        .clip(CircleShape)
                        .background(colonTint)
                )
            }
        }
    }
}

/**
 * 4. Squircle 60-Tick Radial Digital Clock + Real Google-Connected 2x2 Battery & Charging Rings
 *    (Matches 61XGdIxEj+L.jpg while showing real Android BatteryManager & Google peripheral charging state)
 */
@Composable
fun SquircleTickClockWidget(
    calendar: Calendar,
    accentColor: Color = Color.White,
    scale: Float = 1.0f,
    use24Hour: Boolean = false
) {
    val activeTz = calendar.timeZone ?: TimeZone.getDefault()
    val timeFormat = remember(use24Hour, activeTz) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault()).apply {
            timeZone = activeTz
        }
    }
    val currentSecond = calendar.get(Calendar.SECOND)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(6.dp)
            .testTag("squircle_tick_clock_widget"),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = (minOf(maxWidth.value * 0.36f, maxHeight.value * 0.42f) * scale).coerceIn(36f, 110f).sp

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val halfW = size.width * 0.46f
                val halfH = size.height * 0.46f
                val tickLength = size.minDimension * 0.06f

                for (i in 0 until 60) {
                    val angleRad = ((i * 6f - 90f) * PI / 180.0).toFloat()
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)

                    // Superellipse (squircle n=4) radius in direction (cosA, sinA)
                    val denom = Math.pow(
                        Math.pow((kotlin.math.abs(cosA) / halfW).toDouble(), 4.0) +
                            Math.pow((kotlin.math.abs(sinA) / halfH).toDouble(), 4.0),
                        0.25
                    ).toFloat().coerceAtLeast(0.0001f)
                    val rOuter = 1f / denom
                    val rInner = (rOuter - tickLength).coerceAtLeast(0f)

                    val start = Offset(cx + cosA * rInner, cy + sinA * rInner)
                    val end = Offset(cx + cosA * rOuter, cy + sinA * rOuter)

                    val isElapsed = i <= currentSecond
                    drawLine(
                        color = if (isElapsed) Color.White else Color.White.copy(alpha = 0.24f),
                        start = start,
                        end = end,
                        strokeWidth = if (i % 5 == 0) 3.8f else 2.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = timeFormat.format(calendar.time),
                color = accentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-1.5).sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun BatteryChargingRingsWidget(
    modifier: Modifier = Modifier
) {
    val devices = rememberGoogleBatteryDevices()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(10.dp)
            .testTag("battery_charging_rings_widget"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (col in 0..1) {
                        val idx = row * 2 + col
                        val device = devices.getOrNull(idx)
                        if (device != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                ChargingRingItem(device = device)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargingRingItem(
    device: GoogleConnectedBatteryDevice
) {
    val neonGreen = Color(0xFF30D158)
    val activeBlue = Color(0xFF38BDF8)
    val trackGray = Color(0xFF232528)
    val ringColor = when {
        device.isCharging -> neonGreen
        device.isConnected -> activeBlue
        else -> Color(0xFF525866)
    }
    val progress = if (device.isConnected) {
        (device.batteryPercent / 100f).coerceIn(0.08f, 1f)
    } else {
        0.15f
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val ringDiameter: Dp = (minOf(maxWidth, maxHeight) * 0.84f).coerceIn(52.dp, 160.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(ringDiameter),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = (size.minDimension * 0.11f).coerceIn(6f, 15f)
                    val radius = (size.minDimension - strokePx) / 2f
                    drawCircle(
                        color = trackGray,
                        radius = radius,
                        style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -80f,
                        sweepAngle = 340f * progress,
                        useCenter = false,
                        topLeft = Offset(strokePx / 2f, strokePx / 2f),
                        size = Size(size.width - strokePx, size.height - strokePx),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                // Top Lightning Bolt Badge ONLY when the device is actually charging!
                if (device.isCharging) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "${device.name} charging",
                        tint = neonGreen,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size((ringDiameter * 0.22f).coerceAtLeast(12.dp))
                    )
                }

                // Center Device Icon + Real Battery Percentage
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = device.icon,
                        contentDescription = device.name,
                        tint = if (device.isConnected) Color.White else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size((ringDiameter * 0.34f).coerceAtLeast(18.dp))
                    )
                    Text(
                        text = if (device.isConnected) "${device.batteryPercent}%" else "Sync",
                        color = if (device.isCharging) neonGreen else Color.White.copy(alpha = 0.85f),
                        fontSize = (ringDiameter.value * 0.13f).coerceIn(9f, 14f).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = device.name,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SquircleTickAndBatteryClockFace(
    calendar: Calendar,
    accentColor: Color,
    scale: Float = 1.0f,
    use24Hour: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("squircle_tick_battery_clock_face"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SquircleTickClockWidget(
                calendar = calendar,
                accentColor = accentColor,
                scale = scale,
                use24Hour = use24Hour
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BatteryChargingRingsWidget()
        }
    }
}

/**
 * 5. StandBy Numbered Analog Dial + Google Home Connected 2x2 Tile Grid
 *    (Matches 034324ef-6308-4853-af0c-9674b143f9fa.jpg with Google Home integration only)
 */
@Composable
fun StandbyNumberedAnalogWidget(
    calendar: Calendar,
    accentColor: Color = Color(0xFFFF9500),
    scale: Float = 1.0f
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(6.dp)
            .testTag("standby_numbered_analog_widget"),
        contentAlignment = Alignment.Center
    ) {
        val diameter = minOf(maxWidth, maxHeight) * 0.96f
        Box(
            modifier = Modifier.size(diameter),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerRadius = (size.minDimension / 2f) * 0.96f

                for (i in 0 until 60) {
                    val isHourTick = i % 5 == 0
                    val angleRad = ((i * 6f - 90f) * PI / 180.0).toFloat()
                    val tickLen = if (isHourTick) outerRadius * 0.085f else outerRadius * 0.048f
                    val startR = outerRadius - tickLen
                    drawLine(
                        color = if (isHourTick) Color.White else Color.White.copy(alpha = 0.42f),
                        start = Offset(cx + cos(angleRad) * startR, cy + sin(angleRad) * startR),
                        end = Offset(cx + cos(angleRad) * outerRadius, cy + sin(angleRad) * outerRadius),
                        strokeWidth = if (isHourTick) 3.5f else 1.8f,
                        cap = StrokeCap.Round
                    )
                }

                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    textSize = outerRadius * 0.19f
                }
                val fontMetrics = textPaint.fontMetrics
                val textVerticalOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
                val numRadius = outerRadius * 0.75f

                for (h in 1..12) {
                    val angleRad = ((h * 30f - 90f) * PI / 180.0).toFloat()
                    val nx = cx + cos(angleRad) * numRadius
                    val ny = cy + sin(angleRad) * numRadius - textVerticalOffset
                    drawContext.canvas.nativeCanvas.drawText(h.toString(), nx, ny, textPaint)
                }

                val hours = calendar.get(Calendar.HOUR)
                val minutes = calendar.get(Calendar.MINUTE)
                val seconds = calendar.get(Calendar.SECOND)

                val secondAngle = ((seconds * 6f - 90f) * PI / 180.0).toFloat()
                val minuteAngle = (((minutes + seconds / 60f) * 6f - 90f) * PI / 180.0).toFloat()
                val hourAngle = (((hours + minutes / 60f) * 30f - 90f) * PI / 180.0).toFloat()

                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(hourAngle) * (outerRadius * 0.50f), cy + sin(hourAngle) * (outerRadius * 0.50f)),
                    strokeWidth = outerRadius * 0.065f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = Color.White,
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(minuteAngle) * (outerRadius * 0.78f), cy + sin(minuteAngle) * (outerRadius * 0.78f)),
                    strokeWidth = outerRadius * 0.052f,
                    cap = StrokeCap.Round
                )

                val secColor = if (accentColor == Color.White) Color(0xFFFF9500) else accentColor
                drawLine(
                    color = secColor,
                    start = Offset(cx - cos(secondAngle) * (outerRadius * 0.14f), cy - sin(secondAngle) * (outerRadius * 0.14f)),
                    end = Offset(cx + cos(secondAngle) * (outerRadius * 0.86f), cy + sin(secondAngle) * (outerRadius * 0.86f)),
                    strokeWidth = outerRadius * 0.022f,
                    cap = StrokeCap.Round
                )

                drawCircle(color = Color.White, radius = outerRadius * 0.05f, center = Offset(cx, cy))
                drawCircle(color = Color.Black, radius = outerRadius * 0.02f, center = Offset(cx, cy))
            }
        }
    }
}

@Composable
fun SmartHomeControlGridWidget(
    modifier: Modifier = Modifier,
    standbyViewModel: StandbyViewModel? = null
) {
    val vm: StandbyViewModel = standbyViewModel ?: viewModel()
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val googleAccount by vm.googleConnectedAccount.collectAsState()
    val homeDevices = rememberGoogleHomeDevices(vm)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
            .testTag("smart_home_grid_widget"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Google Home Connected Status Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF14161B))
                .clickable {
                    haptics.tick()
                    GoogleDeviceAndHomeMonitor.openGoogleHomeApp(context)
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Google Home",
                    tint = Color(0xFF4285F4),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Google Home • $googleAccount",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "GOOGLE SYNC",
                color = Color(0xFF34A853),
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // 2x2 Google Home Device Tiles
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0..1) {
                        val idx = row * 2 + col
                        val device = homeDevices.getOrNull(idx)
                        if (device != null) {
                            SmartHomeTile(
                                title = device.name,
                                subtitle = "${device.room.substringAfter("• ").trim()} • ${device.statusText}",
                                isLit = device.isActive,
                                topContent = {
                                    when (device.deviceType) {
                                        "thermostat" -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${device.temperatureF ?: 72}°",
                                                    color = if (device.isActive) Color(0xFF111827) else Color.White,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Thermostat,
                                                    contentDescription = device.name,
                                                    tint = if (device.isActive) Color(0xFFEA4335) else Color.Gray,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clickable {
                                                            haptics.tick()
                                                            vm.adjustGoogleNestTemp(device.id, 1)
                                                        }
                                                )
                                            }
                                        }
                                        "light" -> {
                                            Icon(
                                                imageVector = Icons.Default.Lightbulb,
                                                contentDescription = device.name,
                                                tint = if (device.isActive) Color(0xFFFBBC05) else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        "display" -> {
                                            Icon(
                                                imageVector = Icons.Default.SmartDisplay,
                                                contentDescription = device.name,
                                                tint = if (device.isActive) Color(0xFF4285F4) else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Default.CastConnected,
                                                contentDescription = device.name,
                                                tint = if (device.isActive) Color(0xFF34A853) else Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    haptics.tick()
                                    vm.toggleGoogleHomeDevice(device.id)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartHomeTile(
    title: String,
    subtitle: String,
    isLit: Boolean,
    topContent: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isLit) Color(0xFFF3F4F6) else Color(0xFF1F2024)
    val titleColor = if (isLit) Color(0xFF111827) else Color.White
    val subColor = if (isLit) Color(0xFF4B5563) else Color.White.copy(alpha = 0.65f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            topContent()
            Column {
                Text(
                    text = title,
                    color = titleColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = subColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AnalogSmartHomeClockFace(
    calendar: Calendar,
    accentColor: Color,
    scale: Float = 1.0f,
    standbyViewModel: StandbyViewModel? = null
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("analog_smart_home_clock_face"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            StandbyNumberedAnalogWidget(calendar = calendar, accentColor = accentColor, scale = scale)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SmartHomeControlGridWidget(standbyViewModel = standbyViewModel)
        }
    }
}

/**
 * 6. Big Date Hero ("Tue Jul 4") + Full-Pane Monthly Calendar Grid (Matches StandBy-on-iPhone.jpg Top-Left)
 */
@Composable
fun BigDateHeroWidget(
    calendar: Calendar,
    accentColor: Color = Color(0xFFFF453A)
) {
    val redAccent = if (accentColor == Color.White) Color(0xFFFF453A) else accentColor
    val weekday = remember(calendar.get(Calendar.DAY_OF_WEEK)) {
        SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)
    }
    val monthShort = remember(calendar.get(Calendar.MONTH)) {
        SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
    }
    val dayNum = calendar.get(Calendar.DAY_OF_MONTH).toString()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(8.dp)
            .testTag("big_date_hero_widget"),
        contentAlignment = Alignment.Center
    ) {
        val headerSize = (minOf(maxWidth.value, maxHeight.value) * 0.18f).coerceIn(22f, 46f).sp
        val dayFontSize = (minOf(maxWidth.value, maxHeight.value) * 0.60f).coerceIn(72f, 180f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weekday,
                    color = redAccent,
                    fontSize = headerSize,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = monthShort,
                    color = Color(0xFF9CA3AF),
                    fontSize = headerSize,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = dayNum,
                color = Color.White,
                fontSize = dayFontSize,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = dayFontSize
            )
        }
    }
}

@Composable
fun StandbyRedCalendarGridWidget(
    calendar: Calendar,
    accentColor: Color = Color(0xFFFF453A)
) {
    val redAccent = if (accentColor == Color.White) Color(0xFFFF453A) else accentColor
    val monthName = remember(calendar.get(Calendar.MONTH)) {
        SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time).uppercase(Locale.getDefault())
    }
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

    val calCopy = remember(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH)) {
        (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val firstDayOffset = (calCopy.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
    val daysInMonth = calCopy.getActualMaximum(Calendar.DAY_OF_MONTH)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("standby_red_calendar_grid_widget")
    ) {
        val cellSize = (minOf(maxWidth.value / 7.5f, maxHeight.value / 8.5f)).coerceIn(22f, 44f).dp
        val numFontSize = (cellSize.value * 0.44f).coerceIn(11f, 18f).sp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = monthName,
                color = redAccent,
                fontSize = (numFontSize.value * 1.15f).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 4.dp)
            )

            val headers = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                headers.forEach { d ->
                    Box(modifier = Modifier.size(cellSize), contentAlignment = Alignment.Center) {
                        Text(
                            text = d,
                            color = Color(0xFF9CA3AF),
                            fontSize = numFontSize,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val totalCells = firstDayOffset + daysInMonth
            val rows = ((totalCells + 6) / 7).coerceAtLeast(5)
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0..6) {
                        val cellIdx = r * 7 + c
                        val dayNumber = cellIdx - firstDayOffset + 1
                        val isToday = dayNumber == currentDay
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(CircleShape)
                                .background(if (isToday) redAccent else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNumber in 1..daysInMonth) {
                                Text(
                                    text = dayNumber.toString(),
                                    color = Color.White,
                                    fontSize = numFontSize,
                                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold
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
fun BigDateCalendarSplitClockFace(
    calendar: Calendar,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("big_date_calendar_split_clock_face"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BigDateHeroWidget(calendar = calendar, accentColor = accentColor)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            StandbyRedCalendarGridWidget(calendar = calendar, accentColor = accentColor)
        }
    }
}

/**
 * 7. StandBy Now Playing Media Card Widget (Full-Screen Adaptive Layout)
 */
@Composable
fun StandbyNowPlayingCardWidget(
    standbyViewModel: StandbyViewModel
) {
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val trackTitle by MediaStateHolder.trackTitle.collectAsState()
    val artistName by MediaStateHolder.artistName.collectAsState()
    val isPlayingFlow by MediaStateHolder.isPlaying.collectAsState()
    val albumArt by MediaStateHolder.albumArt.collectAsState()
    val selectedColorIdx by standbyViewModel.colorPage3.collectAsState()
    val accentColor = standbyViewModel.colors[selectedColorIdx].first

    var isSimulatedPlaying by remember { mutableStateOf(true) }
    val effectivePlaying = isPlayingFlow || isSimulatedPlaying

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(14.dp)
            .testTag("standby_now_playing_card_widget")
    ) {
        val artSize = (minOf(maxWidth, maxHeight) * 0.46f).coerceIn(76.dp, 160.dp)

        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Music",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(artSize)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accentColor.copy(alpha = 0.85f), Color(0xFF1E293B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt!!.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(artSize * 0.5f)
                    )
                }
            }

            Column {
                Text(
                    text = trackTitle?.takeIf { it.isNotBlank() && it != "No Track" } ?: "I Ain't Worried",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artistName?.takeIf { it.isNotBlank() } ?: "OneRepublic",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                onClick = {
                    haptics.confirm()
                    isSimulatedPlaying = !isSimulatedPlaying
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                    } catch (_: Exception) {
                    }
                },
                shape = CircleShape,
                color = Color(0xFF27272A)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (effectivePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (effectivePlaying) "Pause" else "Play",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 8. StandBy Incoming Call / Quick Contact Card Widget (Full-Screen Bleed)
 */
@Composable
fun StandbyIncomingCallCardWidget() {
    val haptics = rememberStandbyHaptics()
    var callStatus by remember { mutableStateOf("Google Voice • mobile") }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                )
            )
            .padding(16.dp)
            .testTag("standby_incoming_call_widget")
    ) {
        val avatarSize = (minOf(maxWidth, maxHeight) * 0.45f).coerceIn(68.dp, 132.dp)

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Surface(
                modifier = Modifier.size(avatarSize),
                shape = CircleShape,
                color = Color(0xFF38BDF8),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.6f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Sébastien",
                        tint = Color.White,
                        modifier = Modifier.size(avatarSize * 0.6f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = callStatus,
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "Sébastien",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptics.tick()
                            callStatus = "Sent to Voicemail"
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Voicemail,
                            contentDescription = "Send to Voicemail",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.tick()
                            callStatus = "Call Declined"
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Decline",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptics.confirm()
                            callStatus = "Connected • Google Call"
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
