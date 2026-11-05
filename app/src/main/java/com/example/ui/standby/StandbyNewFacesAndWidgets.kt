package com.example.ui.standby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 1. High-Precision Equirectangular World Map Dot Matrix (96 cols x 44 rows)
 *    Longitude: -180° (col 0) to +180° (col 95) -> 3.75° per col
 *    Latitude:  +82°N (row 0) to -60°S (row 43) -> 3.23° per row
 *    Features distinct continents: North America (Alaska, Hudson Bay, Florida, Mexico),
 *    Greenland, South America, Europe (UK, Scandinavia, Spain, Italy, Mediterranean Sea),
 *    Africa (Sahara, Horn of Africa, Madagascar), Asia (Arabia, India, SE Asia, Indonesia, Japan),
 *    Australia, and New Zealand.
 */
private val WORLD_MAP_ASCII = listOf(
    "..................................#########.....................................................",
    ".............................###############....................###.............................",
    ".................####.......################..................#######........#########..........",
    ".....#####.....########.....###############..........####....#########....#################.....",
    "...#########..##########.....#############..........######..###########..#####################..",
    "..#######################.....###########..........########.###################################.",
    ".########################......#########..........#########.####################################",
    "...############...########......#######........##..#######..###################################.",
    ".....##########....########......#####........###..######...##################################..",
    ".......#########.....#######..................###.#######...################################....",
    "........#########....########....................########..###############################..#...",
    ".........####################...................########...##############################..##...",
    "..........###################..................#########...#############################..###...",
    "...........##################.................#########....############################..###....",
    "...........#################.................######.##.....###########################...##.....",
    "............###############.................####...##.#....##########################...........",
    ".............#############..................###....#..#....#########################..##........",
    "..............###########.............................##..#########################..###........",
    "...............#########....#........................###..########################...##.........",
    "................######.....##......................#####...######################...............",
    ".................####.......##....................#######...#####..#############................",
    "..................###........##..................#########...###....###########..#..............",
    "...................##...........................###########...#......#########..##..............",
    "....................##.........................#############..........#######...##..............",
    ".....................###.......................##############..........#####....................",
    "......................#####....................##############..........####....#..##............",
    ".......................#######.................##############...........###....##.###...........",
    ".......................#########................############.............##.....#####.###.......",
    "........................##########...............##########......................####.####......",
    "........................###########..............#########.........................##..###......",
    ".........................###########.............########.......................................",
    ".........................##########..............#######........................................",
    "..........................#########..............#######.........................#######........",
    "...........................########..............######....##...................#########.......",
    "...........................#######...............#####.....##..................###########......",
    "............................######................####.....#...................###########......",
    "............................#####.................###..........................##########.......",
    ".............................####..................##...........................########........",
    ".............................###.................................................######....#....",
    "..............................##.....................................................#....##....",
    "..............................##.........................................................##.....",
    "..............................#..........................................................#......",
    "..............................#.................................................................",
    "................................................................................................"
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

    val zonedCal = remember(calendar.timeInMillis, activeTimeZone) {
        Calendar.getInstance(activeTimeZone).apply {
            timeInMillis = calendar.timeInMillis
        }
    }

    // Toggle between compact bottom-left Squircle-Tick Digital Clock and expanded split view
    var isClockProminent by remember { mutableStateOf(false) }

    // Local hours elapsed since 0000 (00:00 midnight is the starting point 0.0f)
    val hourOfDay = zonedCal.get(Calendar.HOUR_OF_DAY)
    val minute = zonedCal.get(Calendar.MINUTE)
    val second = zonedCal.get(Calendar.SECOND)
    val hoursFrom0000 = hourOfDay + (minute / 60f) + (second / 3600f)
    val progressFrom0000 = (hoursFrom0000 / 24f).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = if (isDuoCompact) 8.dp else 14.dp, vertical = if (isDuoCompact) 6.dp else 10.dp)
            .testTag("world_map_clock_face")
    ) {
        val mapAccent = if (accentColor == Color.White) Color(0xFFFF9F0A) else accentColor

        // 1. Full-Screen Dot-Matrix World Map + Timezone-Overlapping Parabola (0000 Starting Point)
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val rows = WORLD_MAP_ASCII.size
            val cols = WORLD_MAP_ASCII[0].length
            val mapWidth = size.width
            val mapHeight = size.height
            val stepX = mapWidth / cols.toFloat()
            val stepY = mapHeight / rows.toFloat()
            val dotRadius = (min(stepX, stepY) * 0.36f).coerceIn(1.3f, 4.4f)

            // Convert longitude/latitude to normalized [0..1] map coordinates
            fun lonToNormX(lon: Float): Float = ((lon + 180f) / 360f).coerceIn(0.03f, 0.97f)
            fun latToNormY(lat: Float): Float = ((82f - lat) / 142f).coerceIn(0.05f, 0.94f)

            // Active timezone normalized X coordinate (anchored to timezone longitude / offset)
            val tzCenterLon = (regionInfo.utcOffsetHours * 15f).coerceIn(-180f, 180f)
            val tzNormX = lonToNormX(
                if (abs(regionInfo.longitude - tzCenterLon) < 28f) regionInfo.longitude else tzCenterLon
            )
            // Width of one 15-degree time zone band on the map
            val tzBandHalfWidth = 1f / 24f

            // Parabola / Solar Terminator anchored to 0000 (00:00) starting point and overlapping tzNormX:
            // At 0000 (progressFrom0000 = 0.0), the parabola vertex starts at tzNormX and sweeps across the timezone
            // so the curve directly overlaps the active timezone showing Day (above/crest) vs Night (below/trough).
            val parabolaShiftNormX = tzNormX + (progressFrom0000 - 0.25f) * 0.55f

            fun terminatorY(normalizedX: Float): Float {
                // Signed wrapped distance in [-0.5, 0.5] from parabola vertex
                var dx = (normalizedX - parabolaShiftNormX) % 1.0f
                if (dx > 0.5f) dx -= 1.0f
                if (dx < -0.5f) dx += 1.0f
                // True parabolic arch centered on the timezone shift + smooth cosine wings so it wraps cleanly
                val parabolicArch = 1f - 7.2f * (dx * dx)
                val cosineBlend = cos(dx * 2f * PI.toFloat())
                val wave = (parabolicArch * 0.65f + cosineBlend * 0.35f).coerceIn(-1.05f, 1.05f)
                return (mapHeight * 0.52f) - wave * (mapHeight * 0.36f)
            }

            // Subtle Active Time Zone Meridian Band highlight (shows the timezone strip overlapped by the parabola)
            val bandLeftPx = ((tzNormX - tzBandHalfWidth).coerceIn(0f, 1f)) * mapWidth
            val bandRightPx = ((tzNormX + tzBandHalfWidth).coerceIn(0f, 1f)) * mapWidth
            drawRect(
                color = mapAccent.copy(alpha = 0.07f),
                topLeft = Offset(bandLeftPx, 0f),
                size = androidx.compose.ui.geometry.Size((bandRightPx - bandLeftPx).coerceAtLeast(4f), mapHeight)
            )

            // Night-side shaded fill under the parabola curve for instant day/night visual contrast
            val nightShadePath = Path().apply {
                moveTo(0f, mapHeight)
                val samples = 120
                for (i in 0..samples) {
                    val t = i.toFloat() / samples.toFloat()
                    lineTo(t * mapWidth, terminatorY(t).coerceIn(2f, mapHeight - 2f))
                }
                lineTo(mapWidth, mapHeight)
                close()
            }
            drawPath(
                path = nightShadePath,
                color = Color(0xFF0A0E17).copy(alpha = 0.42f)
            )

            // Render Dot-Matrix World Map (Ocean micro-dots + Continent Day/Night/Timezone-Overlap dots)
            for (r in 0 until rows) {
                val line = WORLD_MAP_ASCII[r]
                val cy = (r + 0.5f) * stepY
                for (c in 0 until cols) {
                    val cx = (c + 0.5f) * stepX
                    val normX = (c + 0.5f) / cols.toFloat()
                    val curveY = terminatorY(normX)
                    val isDaylight = cy < curveY
                    val inActiveTzBand = abs(normX - tzNormX) <= tzBandHalfWidth * 1.15f

                    if (line[c] == '#') {
                        val dotColor = when {
                            inActiveTzBand && isDaylight -> Color(0xFFFFE082) // Bright warm daylight in active TZ
                            inActiveTzBand && !isDaylight -> mapAccent.copy(alpha = 0.78f) // Glowing night in active TZ
                            isDaylight -> Color.White.copy(alpha = 0.95f) // Crisp white daylight continents
                            else -> Color(0xFF3E4656).copy(alpha = 0.72f) // Deep slate night continents
                        }
                        drawCircle(
                            color = dotColor,
                            radius = if (inActiveTzBand) dotRadius * 1.08f else dotRadius,
                            center = Offset(cx, cy)
                        )
                    } else if ((r + c) % 2 == 0) {
                        // Subtle dot-matrix ocean background grid
                        drawCircle(
                            color = if (isDaylight) Color(0xFF171C26).copy(alpha = 0.55f) else Color(0xFF0E1118).copy(alpha = 0.40f),
                            radius = dotRadius * 0.34f,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }

            // Draw Smooth Parabolic Day/Night Terminator Curve
            val curvePath = Path()
            val samples = 120
            for (i in 0..samples) {
                val t = i.toFloat() / samples.toFloat()
                val x = t * mapWidth
                val y = terminatorY(t).coerceIn(4f, mapHeight - 4f)
                if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
            }
            // Outer soft glow on the parabola
            drawPath(
                path = curvePath,
                color = mapAccent.copy(alpha = 0.28f),
                style = Stroke(width = (4.5f * scale).coerceIn(3f, 7f), cap = StrokeCap.Round)
            )
            // Crisp core parabola line
            drawPath(
                path = curvePath,
                color = Color.White.copy(alpha = 0.72f),
                style = Stroke(width = (1.8f * scale).coerceIn(1.2f, 3.0f), cap = StrokeCap.Round)
            )

            // Intersection point where the parabola overlaps the active Time Zone meridian
            val tzOverlapX = tzNormX * mapWidth
            val tzOverlapY = terminatorY(tzNormX).coerceIn(8f, mapHeight - 8f)
            drawCircle(
                color = mapAccent.copy(alpha = 0.32f),
                radius = dotRadius * 3.4f,
                center = Offset(tzOverlapX, tzOverlapY)
            )
            drawCircle(
                color = mapAccent,
                radius = dotRadius * 1.5f,
                center = Offset(tzOverlapX, tzOverlapY)
            )

            // Reference world hub dots
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
                    color = mapAccent.copy(alpha = 0.78f),
                    radius = dotRadius * 1.55f,
                    center = Offset(hx, hy)
                )
            }

            // Primary User Standard Time / Location Pin with Glowing Halo
            val userPx = lonToNormX(regionInfo.longitude) * mapWidth
            val userPy = latToNormY(regionInfo.latitude) * mapHeight
            drawCircle(
                color = mapAccent.copy(alpha = 0.30f),
                radius = dotRadius * 5.0f,
                center = Offset(userPx, userPy)
            )
            drawCircle(
                color = Color.White,
                radius = dotRadius * 2.9f,
                center = Offset(userPx, userPy)
            )
            drawCircle(
                color = mapAccent,
                radius = dotRadius * 1.95f,
                center = Offset(userPx, userPy)
            )
        }

        // Top-Right 0000 Solar Telemetry Pill (Shows Day/Night status & 0000 cycle offset for active timezone)
        val isLocalDaylight = hourOfDay in 6..17
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0xFF0B0E14).copy(alpha = 0.82f))
                .border(1.dp, mapAccent.copy(alpha = 0.38f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isLocalDaylight) Color(0xFFFFD54F) else Color(0xFF38BDF8))
            )
            Text(
                text = "${if (isLocalDaylight) "DAY" else "NIGHT"} • 0000+${String.format(Locale.US, "%02d:%02d", hourOfDay, minute)} • ${regionInfo.utcOffsetLabel}",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = if (isDuoCompact) 9.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        // 2. Duo Digital Clock inside the Map Clock:
        //    60-Second Moving Border Ticks + Digital Clock in the Middle + Region Header
        val clockBoxWidth = when {
            isClockProminent -> (maxWidth * 0.54f).coerceIn(190.dp, 380.dp)
            isDuoCompact -> (maxWidth * 0.56f).coerceIn(136.dp, 220.dp)
            else -> (maxWidth * 0.36f * scale.coerceIn(0.85f, 1.25f)).coerceIn(185.dp, 310.dp)
        }
        val clockBoxHeight = when {
            isClockProminent -> (maxHeight * 0.62f).coerceIn(135.dp, 250.dp)
            isDuoCompact -> (maxHeight * 0.48f).coerceIn(92.dp, 150.dp)
            else -> (maxHeight * 0.44f * scale.coerceIn(0.85f, 1.25f)).coerceIn(118.dp, 195.dp)
        }

        Box(
            modifier = Modifier
                .align(if (isClockProminent) Alignment.CenterStart else Alignment.BottomStart)
                .size(width = clockBoxWidth, height = clockBoxHeight)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF05070B).copy(alpha = 0.86f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .clickable { isClockProminent = !isClockProminent }
                .padding(6.dp)
                .testTag("map_squircle_tick_digital_clock")
        ) {
            SquircleTickClockWidget(
                calendar = zonedCal,
                accentColor = Color.White,
                tickAccentColor = mapAccent,
                scale = 1.0f,
                use24Hour = use24Hour,
                regionLabel = "${regionInfo.regionName} • ${regionInfo.standardTimeShort}",
                customFont = customFont
            )
        }
    }
}

/**
 * Duo Digital Clock with 60 seconds moving on the squircle borders and digital clock in the middle.
 * Used inside `WorldTimeZoneMapClockFace` and available for compact/full-screen rendering.
 */
@Composable
fun SquircleTickClockWidget(
    calendar: Calendar,
    accentColor: Color = Color.White,
    tickAccentColor: Color = Color(0xFFFF9F0A),
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    regionLabel: String? = null,
    customFont: FontFamily = FontFamily.SansSerif
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
            .padding(2.dp)
            .testTag("squircle_tick_clock_widget"),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = (minOf(maxWidth.value * 0.34f, maxHeight.value * 0.42f) * scale).coerceIn(26f, 104f).sp
        val labelSize = (fontSize.value * 0.24f).coerceIn(9.5f, 15f).sp

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 60 Second Ticks Moving Around the Squircle Borders
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val halfW = size.width * 0.47f
                val halfH = size.height * 0.46f
                val tickLength = (size.minDimension * 0.085f).coerceIn(5f, 16f)

                for (i in 0 until 60) {
                    val angleRad = ((i * 6f - 90f) * PI / 180.0).toFloat()
                    val cosA = cos(angleRad)
                    val sinA = sin(angleRad)

                    // Superellipse (squircle n=4) radius in direction (cosA, sinA)
                    val denom = (
                        (abs(cosA) / halfW).toDouble().pow(4.0) +
                            (abs(sinA) / halfH).toDouble().pow(4.0)
                        ).pow(0.25).toFloat().coerceAtLeast(0.0001f)
                    val rOuter = 1f / denom
                    val rInner = (rOuter - tickLength).coerceAtLeast(0f)

                    val start = Offset(cx + cosA * rInner, cy + sinA * rInner)
                    val end = Offset(cx + cosA * rOuter, cy + sinA * rOuter)

                    val isCurrentSecond = i == currentSecond
                    val isElapsed = i < currentSecond
                    val tickColor = when {
                        isCurrentSecond -> tickAccentColor
                        isElapsed -> Color.White.copy(alpha = 0.92f)
                        else -> Color.White.copy(alpha = 0.20f)
                    }
                    drawLine(
                        color = tickColor,
                        start = start,
                        end = end,
                        strokeWidth = when {
                            isCurrentSecond -> 4.2f
                            i % 5 == 0 -> 3.2f
                            else -> 2.0f
                        },
                        cap = StrokeCap.Round
                    )
                }
            }

            // Digital Clock in the Middle (with optional Region Label above)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (!regionLabel.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = tickAccentColor,
                            modifier = Modifier.size((labelSize.value + 1f).dp)
                        )
                        Text(
                            text = regionLabel,
                            color = tickAccentColor,
                            fontSize = labelSize,
                            fontWeight = FontWeight.Bold,
                            fontFamily = customFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = timeFormat.format(calendar.time),
                    color = accentColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = customFont,
                    letterSpacing = (-1.5).sp,
                    lineHeight = fontSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
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
 * 3. Overlapping Float Bubble Numerals Clock Face
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
