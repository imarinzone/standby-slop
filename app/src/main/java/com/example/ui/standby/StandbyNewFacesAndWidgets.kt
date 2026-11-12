package com.example.ui.standby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * 1. High-Precision Equirectangular World Map Dot Matrix (96 cols x 44 rows)
 *    Longitude: -180° (col 0) to +180° (col 95) -> 3.75° per col
 *    Latitude:  +82°N (row 0) to -60°S (row 43) -> 3.23° per row
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

internal data class WorldMapSolarState(
    val discreteLocalHour: Int,
    val subsolarLonDeg: Float,
    val declinationDeg: Float
) {
    private val declRad = Math.toRadians(declinationDeg.toDouble())
    private val sinDecl = sin(declRad)
    private val cosDecl = cos(declRad)
    private val tanDecl = tan(declRad)

    /**
     * Exact latitude (in degrees, -90..+90) of the Day/Night solar terminator at [lonDeg].
     */
    fun terminatorLatitudeDeg(lonDeg: Float): Float {
        val deltaLonRad = Math.toRadians((lonDeg - subsolarLonDeg).toDouble())
        val latRad = atan(-cos(deltaLonRad) / tanDecl)
        return Math.toDegrees(latRad).toFloat()
    }

    /**
     * Sine of solar altitude at ([lonDeg], [latDeg]): >= 0f means Daylight, < 0f means Night.
     */
    fun solarSinAltitude(lonDeg: Float, latDeg: Float): Float {
        val latRad = Math.toRadians(latDeg.toDouble())
        val deltaLonRad = Math.toRadians((lonDeg - subsolarLonDeg).toDouble())
        return (sin(latRad) * sinDecl + cos(latRad) * cosDecl * cos(deltaLonRad)).toFloat()
    }

    fun isDaylight(lonDeg: Float, latDeg: Float): Boolean = solarSinAltitude(lonDeg, latDeg) >= 0f
}

/**
 * Computes the solar day/night state stepped strictly every 1 hour (15° longitude per hour).
 * Within any single hour (e.g. 14:00..14:59), the terminator parabola remains fixed at that hour's
 * exact solar position and advances by 15° westward on each new hour.
 */
internal fun computeWorldMapSolarState(zonedCal: Calendar, activeTimeZone: TimeZone): WorldMapSolarState {
    val discreteLocalHour = zonedCal.get(Calendar.HOUR_OF_DAY) // 0..23, steps every 1 hr
    val liveOffsetHours = activeTimeZone.getOffset(zonedCal.timeInMillis) / 3_600_000f
    val utcHourStepped = discreteLocalHour.toFloat() - liveOffsetHours

    // At 12:00 UTC the Sun is overhead at 0° (Greenwich); shifts 15° West (-15°) every +1 hour
    val rawSubsolarLon = (12f - utcHourStepped) * 15f
    val subsolarLonDeg = (((rawSubsolarLon + 180f) % 360f + 360f) % 360f) - 180f

    // Seasonal solar declination with a gentle minimum tilt near equinoxes so the terminator always forms a clear parabola
    val dayOfYear = zonedCal.get(Calendar.DAY_OF_YEAR)
    val rawDeclDeg = (-23.44 * cos(2.0 * PI * (dayOfYear + 10) / 365.25)).toFloat()
    val declinationDeg = if (rawDeclDeg >= 0f) rawDeclDeg.coerceAtLeast(12f) else rawDeclDeg.coerceAtMost(-12f)

    return WorldMapSolarState(
        discreteLocalHour = discreteLocalHour,
        subsolarLonDeg = subsolarLonDeg,
        declinationDeg = declinationDeg
    )
}

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

    val timeFormat = remember(use24Hour, activeTimeZone) {
        SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", Locale.getDefault()).apply {
            timeZone = activeTimeZone
        }
    }

    // Recomputes solar terminator strictly once per hour (keyed on HOUR_OF_DAY + DAY_OF_YEAR + timeZone)
    val discreteHourKey = zonedCal.get(Calendar.HOUR_OF_DAY)
    val dayOfYearKey = zonedCal.get(Calendar.DAY_OF_YEAR)
    val solarState = remember(discreteHourKey, dayOfYearKey, activeTimeZone.id) {
        computeWorldMapSolarState(zonedCal, activeTimeZone)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030509))
            .padding(horizontal = if (isDuoCompact) 8.dp else 14.dp, vertical = if (isDuoCompact) 6.dp else 10.dp)
            .testTag("world_map_clock_face")
    ) {
        val mapAccent = if (accentColor == Color.White) Color(0xFFFF9F0A) else accentColor
        val minDim = minOf(maxWidth.value, maxHeight.value)
        val clockSize = if (isDuoCompact) {
            (minDim * 0.28f * scale).coerceIn(24f, 120f).sp
        } else {
            (minDim * 0.26f * scale).coerceIn(36f, 220f).sp
        }
        val labelSize = (clockSize.value * 0.18f).coerceIn(9f, 28f).sp

        // Full-Screen Equirectangular World Map + 1-Hour Stepped Exact Day/Night Solar Terminator Parabola
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val rows = WORLD_MAP_ASCII.size
            val cols = WORLD_MAP_ASCII[0].length
            val mapWidth = size.width
            val mapHeight = size.height
            if (mapWidth <= 1f || mapHeight <= 1f) return@Canvas

            val stepX = mapWidth / cols.toFloat()
            val stepY = mapHeight / rows.toFloat()
            val dotRadius = (min(stepX, stepY) * 0.36f).coerceIn(1.3f, 4.4f)

            // Unified equirectangular projection matching WORLD_MAP_ASCII (-180°..+180° lon, +82°N..-60°S lat)
            fun lonToNormX(lon: Float): Float = ((lon + 180f) / 360f).coerceIn(0f, 1f)
            fun normXToLon(normX: Float): Float = -180f + normX * 360f
            fun latToNormY(lat: Float): Float = (82f - lat) / 142f
            fun normYToLat(normY: Float): Float = 82f - normY * 142f

            fun terminatorY(normalizedX: Float): Float {
                val lonDeg = normXToLon(normalizedX)
                val termLatDeg = solarState.terminatorLatitudeDeg(lonDeg)
                return latToNormY(termLatDeg) * mapHeight
            }

            // Active timezone normalized X coordinate (anchored to 15°/hr meridian or city longitude)
            val tzCenterLon = (regionInfo.utcOffsetHours * 15f).coerceIn(-180f, 180f)
            val tzNormX = lonToNormX(
                if (abs(regionInfo.longitude - tzCenterLon) < 28f) regionInfo.longitude else tzCenterLon
            )
            val tzBandHalfWidth = 1f / 48f // 15° total width (±7.5° = 1 hour timezone band)

            clipRect(0f, 0f, mapWidth, mapHeight) {
                val samples = 360
                // When declination < 0 (Northern autumn/winter), North (+82°N, y=0) is Polar Night -> Night is ABOVE terminatorY.
                // When declination > 0 (Northern spring/summer), South (-60°S, y=mapHeight) is Polar Night -> Night is BELOW terminatorY.
                val nightIsAboveCurve = solarState.declinationDeg < 0f

                // 1. Subtle Sunlit Atmosphere Wash on the Daylight Hemisphere
                val dayRegionPath = Path().apply {
                    val dayEdgeY = if (nightIsAboveCurve) mapHeight + 16f else -16f
                    moveTo(0f, dayEdgeY)
                    for (i in 0..samples) {
                        val t = i.toFloat() / samples.toFloat()
                        lineTo(t * mapWidth, terminatorY(t))
                    }
                    lineTo(mapWidth, dayEdgeY)
                    close()
                }
                val sunCenterPx = Offset(
                    x = lonToNormX(solarState.subsolarLonDeg) * mapWidth,
                    y = (latToNormY(solarState.declinationDeg) * mapHeight).coerceIn(0f, mapHeight)
                )
                drawPath(
                    path = dayRegionPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF283754).copy(alpha = 0.42f),
                            Color(0xFF131C2E).copy(alpha = 0.24f),
                            Color(0xFF0A101B).copy(alpha = 0.12f)
                        ),
                        center = sunCenterPx,
                        radius = maxOf(mapWidth, mapHeight) * 0.72f
                    )
                )

                // 2. Deep Midnight Shadow Fill on the Night Hemisphere
                val nightShadePath = Path().apply {
                    val nightEdgeY = if (nightIsAboveCurve) -16f else mapHeight + 16f
                    moveTo(0f, nightEdgeY)
                    for (i in 0..samples) {
                        val t = i.toFloat() / samples.toFloat()
                        lineTo(t * mapWidth, terminatorY(t))
                    }
                    lineTo(mapWidth, nightEdgeY)
                    close()
                }
                drawPath(
                    path = nightShadePath,
                    color = Color(0xFF010205).copy(alpha = 0.76f)
                )

                // 3. Subtle 1-Hour (15° Longitude) Time Zone Meridian Grid Lines
                for (hrIdx in 1 until 24) {
                    val mx = (hrIdx / 24f) * mapWidth
                    drawLine(
                        color = Color.White.copy(alpha = if (hrIdx % 6 == 0) 0.06f else 0.025f),
                        start = Offset(mx, 0f),
                        end = Offset(mx, mapHeight),
                        strokeWidth = 1f
                    )
                }

                // Subtle Active 1-Hour Time Zone Meridian Band highlight
                val bandLeftPx = ((tzNormX - tzBandHalfWidth).coerceIn(0f, 1f)) * mapWidth
                val bandRightPx = ((tzNormX + tzBandHalfWidth).coerceIn(0f, 1f)) * mapWidth
                drawRect(
                    color = mapAccent.copy(alpha = 0.07f),
                    topLeft = Offset(bandLeftPx, 0f),
                    size = Size((bandRightPx - bandLeftPx).coerceAtLeast(4f), mapHeight)
                )

                // 4. Render Dot-Matrix World Map with Exact Day vs Night Illumination
                for (r in 0 until rows) {
                    val line = WORLD_MAP_ASCII[r]
                    val normY = (r + 0.5f) / rows.toFloat()
                    val cy = normY * mapHeight
                    val latDeg = normYToLat(normY)

                    for (c in 0 until cols) {
                        val normX = (c + 0.5f) / cols.toFloat()
                        val cx = normX * mapWidth
                        val lonDeg = normXToLon(normX)
                        val isDaylight = solarState.isDaylight(lonDeg, latDeg)
                        val inActiveTzBand = abs(normX - tzNormX) <= tzBandHalfWidth * 1.15f

                        if (line[c] == '#') {
                            val dotColor = when {
                                inActiveTzBand && isDaylight -> Color(0xFFFFE082)
                                inActiveTzBand && !isDaylight -> mapAccent.copy(alpha = 0.80f)
                                isDaylight -> Color(0xFFF8FAFC).copy(alpha = 0.96f)
                                else -> Color(0xFF263042).copy(alpha = 0.62f)
                            }
                            drawCircle(
                                color = dotColor,
                                radius = if (inActiveTzBand) dotRadius * 1.08f else dotRadius,
                                center = Offset(cx, cy)
                            )
                        } else if ((r + c) % 2 == 0) {
                            drawCircle(
                                color = if (isDaylight) Color(0xFF222D40).copy(alpha = 0.52f) else Color(0xFF0A0D14).copy(alpha = 0.28f),
                                radius = dotRadius * 0.32f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                // 5. Draw Multi-Layer Glowing Solar Terminator Parabola Curve
                val curvePath = Path()
                for (i in 0..samples) {
                    val t = i.toFloat() / samples.toFloat()
                    val x = t * mapWidth
                    val y = terminatorY(t)
                    if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
                }
                // Wide twilight horizon band
                drawPath(
                    path = curvePath,
                    color = mapAccent.copy(alpha = 0.12f),
                    style = Stroke(width = (12f * scale).coerceIn(6f, 20f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Ambient outer glow
                drawPath(
                    path = curvePath,
                    color = mapAccent.copy(alpha = 0.30f),
                    style = Stroke(width = (5.5f * scale).coerceIn(3.5f, 9.5f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Mid glow
                drawPath(
                    path = curvePath,
                    color = mapAccent.copy(alpha = 0.68f),
                    style = Stroke(width = (2.8f * scale).coerceIn(1.8f, 4.8f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Sharp solar terminator core
                drawPath(
                    path = curvePath,
                    color = Color(0xFFFFF8E1).copy(alpha = 0.92f),
                    style = Stroke(width = (1.4f * scale).coerceIn(1.0f, 2.4f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Intersection marker where the terminator crosses the active Time Zone meridian (when inside visible map latitudes)
                val tzOverlapX = tzNormX * mapWidth
                val tzOverlapY = terminatorY(tzNormX)
                if (tzOverlapY in 4f..(mapHeight - 4f)) {
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
                }

                // Reference world hub dots (bright white/gold in Daylight, warm city-light amber in Night)
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
                    val hy = latToNormY(hubLat).coerceIn(0.03f, 0.97f) * mapHeight
                    val hubDay = solarState.isDaylight(hubLon, hubLat)
                    drawCircle(
                        color = if (hubDay) Color.White.copy(alpha = 0.85f) else Color(0xFFFFB74D).copy(alpha = 0.85f),
                        radius = dotRadius * 1.45f,
                        center = Offset(hx, hy)
                    )
                }

                // Primary User Location Pin with Glowing Halo
                val userPx = lonToNormX(regionInfo.longitude) * mapWidth
                val userPy = latToNormY(regionInfo.latitude).coerceIn(0.04f, 0.96f) * mapHeight
                drawCircle(
                    color = mapAccent.copy(alpha = 0.32f),
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
        }

        // Clean StandBy Bottom-Left Region + Bold Time Readout (no top UTC/hr telemetry pill or UTC offset sub-text)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = if (isDuoCompact) 6.dp else 12.dp, bottom = if (isDuoCompact) 4.dp else 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = "Region",
                    tint = mapAccent,
                    modifier = Modifier.size(if (isDuoCompact) 13.dp else 18.dp)
                )
                val cleanRegionText = if (
                    regionInfo.standardTimeShort.startsWith("UTC", ignoreCase = true) ||
                    regionInfo.standardTimeShort.startsWith("GMT", ignoreCase = true)
                ) {
                    regionInfo.regionName
                } else {
                    "${regionInfo.regionName} • ${regionInfo.standardTimeShort}"
                }
                Text(
                    text = cleanRegionText,
                    color = mapAccent,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = customFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = timeFormat.format(zonedCal.time),
                color = Color.White,
                fontSize = clockSize,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = customFont,
                letterSpacing = (-1.5).sp,
                lineHeight = clockSize,
                maxLines = 1
            )
        }
    }
}

/**
 * Standalone Clock Screen: Squircle Border Tick Digital Clock (`themeIndex = 13`)
 * Shows 60 moving second ticks along the squircle perimeter and a bold digital clock in the center.
 */
@Composable
fun SquircleTickDigitalClockFace(
    calendar: Calendar,
    accentColor: Color,
    customFont: FontFamily = FontFamily.SansSerif,
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
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

    val tickAccent = if (accentColor == Color.White) Color(0xFFFF9F0A) else accentColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                horizontal = if (isDuoCompact) 12.dp else 28.dp,
                vertical = if (isDuoCompact) 10.dp else 18.dp
            )
            .testTag("squircle_border_tick_clock_face"),
        contentAlignment = Alignment.Center
    ) {
        SquircleTickClockWidget(
            calendar = calendar,
            accentColor = Color.White,
            tickAccentColor = tickAccent,
            scale = scale,
            use24Hour = use24Hour,
            regionLabel = "${regionInfo.regionName} • ${regionInfo.standardTimeShort}",
            customFont = customFont
        )
    }
}

/**
 * Digital Clock with 60 seconds moving on the squircle borders and digital clock in the middle.
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
            .padding(4.dp)
            .testTag("squircle_tick_clock_widget"),
        contentAlignment = Alignment.Center
    ) {
        val fontSize = (minOf(maxWidth.value * 0.34f, maxHeight.value * 0.45f) * scale).coerceIn(32f, 168f).sp
        val labelSize = (fontSize.value * 0.18f).coerceIn(10f, 18f).sp

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
                val tickLength = (size.minDimension * 0.085f).coerceIn(6f, 22f)

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
                            isCurrentSecond -> 4.6f
                            i % 5 == 0 -> 3.4f
                            else -> 2.2f
                        },
                        cap = StrokeCap.Round
                    )
                }
            }

            // Digital Clock in the Middle (with optional Region Label above)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (!regionLabel.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = null,
                            tint = tickAccentColor,
                            modifier = Modifier.size((labelSize.value + 2f).dp)
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
                    letterSpacing = (-2).sp,
                    lineHeight = fontSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * 2. StandBy Giant Sage Clock + Right Sidebar Complications
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
            null
        }
    }

    val alarmShort = remember(nextAlarmLabel) {
        if (!nextAlarmLabel.isNullOrBlank() && nextAlarmLabel != "No Alarm") {
            val parsed = nextAlarmLabel.substringAfter(" ").substringBefore(" (").replace(" ", "").uppercase(Locale.getDefault())
            parsed.ifBlank { null }
        } else {
            null
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
                verticalArrangement = if (!alarmShort.isNullOrBlank()) Arrangement.SpaceBetween else Arrangement.Center,
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
                    if (!tempDisplay.isNullOrBlank()) {
                        Text(
                            text = tempDisplay,
                            color = Color.White,
                            fontSize = sideSubSize,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = customFont,
                            maxLines = 1
                        )
                    }
                }

                if (!alarmShort.isNullOrBlank()) {
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
}

/**
 * 3. Overlapping Float Bubble Numerals Clock Face
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
 * Connected Devices Battery Charging Rings Widget (Used in Tools Hub)
 */
@Composable
fun BatteryChargingRingsWidget(
    scale: Float = 1.0f
) {
    val devices = rememberGoogleBatteryDevices()
    val primaryDevice = devices.firstOrNull()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .testTag("battery_charging_rings_widget")
    ) {
        val ringDiameter = (minOf(maxWidth.value * 0.38f, maxHeight.value * 0.34f) * scale).coerceIn(54f, 118f).dp
        val iconSize = (ringDiameter.value * 0.38f).dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${primaryDevice?.level ?: 85}%",
                        color = Color.White,
                        fontSize = (ringDiameter.value * 0.36f).coerceIn(22f, 40f).sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = if (primaryDevice?.isCharging == true) "Charging • ${primaryDevice.name}"
                        else "Google Connected • ${primaryDevice?.name ?: "Phone"}",
                        color = Color(0xFF34C759),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val topRow = devices.take(2)
                val bottomRow = devices.drop(2).take(2)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topRow.forEach { dev ->
                        SingleBatteryRingItem(
                            device = dev,
                            ringDiameter = ringDiameter,
                            iconSize = iconSize
                        )
                    }
                }
                if (bottomRow.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomRow.forEach { dev ->
                            SingleBatteryRingItem(
                                device = dev,
                                ringDiameter = ringDiameter,
                                iconSize = iconSize
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleBatteryRingItem(
    device: GoogleConnectedBatteryDevice,
    ringDiameter: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    val icon = when (device.type) {
        "phone" -> Icons.Default.Smartphone
        "watch" -> Icons.Default.Watch
        "buds" -> Icons.Default.Headphones
        else -> Icons.Default.Speaker
    }
    val ringColor = when {
        device.level >= 50 -> Color(0xFF34C759)
        device.level >= 20 -> Color(0xFFFFCC00)
        device.level >= 0 -> Color(0xFFFF3B30)
        else -> Color(0xFF38BDF8)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier.size(ringDiameter),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = (size.minDimension * 0.12f).coerceIn(6f, 13f)
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)

                drawArc(
                    color = Color(0xFF23252B),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                val sweep = if (device.level >= 0) 360f * (device.level / 100f).coerceIn(0f, 1f) else 360f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = device.name,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )

            if (device.isCharging) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Charging",
                        tint = ringColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Text(
            text = if (device.level >= 0) "${device.name.take(10)} ${device.level}%" else device.name.take(12),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Google Home Smart Devices Control Grid Widget (Used in Tools Hub)
 */
@Composable
fun SmartHomeControlGridWidget(
    standbyViewModel: StandbyViewModel? = null
) {
    val vm: StandbyViewModel = standbyViewModel ?: viewModel()
    val context = LocalContext.current
    val haptics = rememberStandbyHaptics()
    val homeDevices by vm.googleHomeDevices.collectAsState()
    val googleAccount by vm.googleConnectedAccount.collectAsState()

    LaunchedEffect(context) {
        val detectedAccount = GoogleDeviceAndHomeMonitor.detectConnectedGoogleAccount(context)
        vm.setGoogleConnectedAccount(detectedAccount)
    }

    val rows = remember(homeDevices) { homeDevices.chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
            .testTag("smart_home_control_grid_widget"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Google Home",
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$googleAccount",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Open App",
                color = Color(0xFF8AB4F8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tick()
                        GoogleDeviceAndHomeMonitor.openGoogleHomeApp(context)
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (homeDevices.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF15171D)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.HomeWork,
                        contentDescription = null,
                        tint = Color(0xFF8AB4F8),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Real Android Device Controls",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Directly control linked smart devices via Google Play Services & Device Controls",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 4.dp),
                        maxLines = 2
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                try {
                                    val intent = Intent("android.settings.ACTION_DEVICE_CONTROLS_SETTINGS").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (_: Exception) {}
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Device Controls", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = {
                                haptics.tick()
                                GoogleDeviceAndHomeMonitor.openGoogleHomeApp(context)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Google Home", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { device ->
                        val icon = when (device.type) {
                            "light" -> Icons.Default.Lightbulb
                            "thermostat" -> Icons.Default.Thermostat
                            "plug" -> Icons.Default.Power
                            "air" -> Icons.Default.Air
                            "strip" -> Icons.Default.WbTwilight
                            else -> Icons.Default.Lock
                        }
                        val activeTint = when (device.type) {
                            "light", "strip" -> Color(0xFFFFCC00)
                            "thermostat" -> Color(0xFFFF9F0A)
                            "air" -> Color(0xFF38BDF8)
                            else -> Color(0xFF34C759)
                        }

                        Surface(
                            onClick = {
                                haptics.tick()
                                vm.toggleGoogleHomeDevice(device.id)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("google_home_tile_${device.id}"),
                            shape = RoundedCornerShape(18.dp),
                            color = if (device.isOn) Color(0xFF262A34) else Color(0xFF15171D)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.Start
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = device.name,
                                        tint = if (device.isOn) activeTint else Color.White.copy(alpha = 0.40f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (device.isOn) activeTint else Color.White.copy(alpha = 0.2f))
                                    )
                                }
                                Column {
                                    Text(
                                        text = device.room.uppercase(Locale.getDefault()),
                                        color = Color.White.copy(alpha = 0.50f),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.name,
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = device.statusText,
                                        color = if (device.isOn) activeTint else Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
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
