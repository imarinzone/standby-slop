package com.example.ui.standby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import com.example.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.api.CurrentWeather
import com.example.data.Alarm
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import kotlin.math.roundToInt

@Composable
fun ClockFaceRenderer(
    themeIndex: Int, 
    modifier: Modifier = Modifier, 
    weatherViewModel: WeatherViewModel = viewModel(),
    standbyViewModel: StandbyViewModel = viewModel()
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }
    val weather by weatherViewModel.weather.collectAsState()
    
    val selectedColorIdx by standbyViewModel.colorPage0.collectAsState()
    val selectedFontIdx by standbyViewModel.fontPage0.collectAsState()
    val showWeatherValue by standbyViewModel.showWeatherPage0.collectAsState()
    val nextSystemAlarm by standbyViewModel.nextSystemAlarm.collectAsState()
    val clockScaleValue by standbyViewModel.scalePage0.collectAsState()
    val animsEnabled by standbyViewModel.animPage0.collectAsState()
    
    val use24Hour by standbyViewModel.use24HourFormat.collectAsState()
    val showAmPm by standbyViewModel.showAmPm.collectAsState()
    val showSeconds by standbyViewModel.showSeconds.collectAsState()

    val targetBrightness by standbyViewModel.clockBrightnessFactor.collectAsState()
    val isPreAlarmDimming by standbyViewModel.isPreAlarmDimmingActive.collectAsState()
    val minutesUntilAlarm by standbyViewModel.minutesUntilNextAlarm.collectAsState()
    val animatedBrightness by animateFloatAsState(
        targetValue = targetBrightness.coerceIn(0.10f, 1.0f),
        animationSpec = tween(durationMillis = 600),
        label = "clockPreAlarmBrightness"
    )

    val bgUri by standbyViewModel.getBgUriFlow(0).collectAsState()
    val glowVal by standbyViewModel.getGlowFlow(0).collectAsState()
    val outlineVal by standbyViewModel.getOutlineFlow(0).collectAsState()
    val gradientIdx by standbyViewModel.getGradientFlow(0).collectAsState()
    val gradientColors = if (gradientIdx > 0 && gradientIdx < standbyViewModel.gradients.size) standbyViewModel.gradients[gradientIdx].second else null

    val customColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    val locMode by standbyViewModel.locationMode.collectAsState()
    val customLoc by standbyViewModel.customLocationName.collectAsState()
    val clockTzId by standbyViewModel.clockTimeZoneId.collectAsState()
    val gpsLoc by standbyViewModel.gpsLocationName.collectAsState()
    val mainRegionInfo = remember(locMode, customLoc, clockTzId, gpsLoc) {
        standbyViewModel.resolvePaneLocationInfo(0)
    }
    val zonedCurrentTime = remember(currentTime, mainRegionInfo.timeZoneId) {
        GoogleDeviceAndHomeMonitor.calendarForTimeZone(currentTime, mainRegionInfo.timeZoneId)
    }

    LaunchedEffect(Unit) {
        standbyViewModel.updateNextAlarm()
        while (true) {
            delay(1000)
            val now = Calendar.getInstance()
            currentTime = now
            if (now.get(Calendar.SECOND) % 15 == 0) {
                standbyViewModel.updateNextAlarm(now)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("clock_face_container")
    ) {
        if (bgUri != null) {
            coil.compose.AsyncImage(
                model = bgUri,
                contentDescription = "Background",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedBrightness },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            // Add a dark overlay just to make sure clocks remain readable
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        val clockGradientModifier = if (gradientColors != null) {
            Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                val brush = androidx.compose.ui.graphics.Brush.linearGradient(gradientColors)
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                }
            }
        } else Modifier

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = animatedBrightness }
                .then(clockGradientModifier),
            contentAlignment = Alignment.Center
        ) {
            // Compute responsive viewport factor so smaller screens scale proportionally
            val widthFactor = (maxWidth.value / 780f).coerceIn(0.45f, 1.25f)
            val heightFactor = (maxHeight.value / 380f).coerceIn(0.45f, 1.25f)
            val viewportScale = minOf(widthFactor, heightFactor)
            val effectiveScale = (clockScaleValue * viewportScale).coerceIn(0.42f, 1.65f)

            when (themeIndex.mod(16)) {
                0 -> DigitalMinimal(zonedCurrentTime, customColor, customFont, effectiveScale, animsEnabled, use24Hour, showAmPm, showSeconds, glowVal, outlineVal, gradientColors)
                1 -> AnalogClassic(zonedCurrentTime, customColor, effectiveScale, animsEnabled, true)
                2 -> RetroFlip(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds, isDuoCompact = false, standbyViewModel = standbyViewModel, paneIndex = 0)
                3 -> BinaryStyle(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                4 -> ModernBoldTextClock(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                5 -> ContrastingSplitClock(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                6 -> AnalogDashboard(zonedCurrentTime, customColor, customFont, effectiveScale, animsEnabled, true)
                7 -> BubblePastelClock(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                8 -> AmbientGradientClock(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                9 -> NixieTubeClock(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, showAmPm, showSeconds)
                10 -> WorldTimeZoneMapClockFace(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, nextSystemAlarm, isDuoCompact = false, standbyViewModel = standbyViewModel, paneIndex = 0)
                11 -> StandbySageComplicationClockFace(zonedCurrentTime, customColor, customFont, effectiveScale, use24Hour, weather?.temperature_2m, nextSystemAlarm)
                12 -> OverlappingFloatClockFace(zonedCurrentTime, customColor, effectiveScale, use24Hour)
                13 -> SquircleTickAndBatteryClockFace(zonedCurrentTime, customColor, effectiveScale, use24Hour)
                14 -> AnalogSmartHomeClockFace(zonedCurrentTime, customColor, effectiveScale, standbyViewModel = standbyViewModel)
                15 -> BigDateCalendarSplitClockFace(zonedCurrentTime, customColor)
            }
        }
        
        // Weather Overlay
        if (showWeatherValue) {
            weather?.let { w ->
                Text(
                    text = "${w.temperature_2m}°C",
                    color = customColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = customFont,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                        .graphicsLayer { alpha = animatedBrightness }
                )
            }
        }

        // Dimming scrim overlay to ensure hardware/AndroidView elements (e.g. NixieTubeClock) also lower luminance smoothly
        val scrimAlpha = (1f - animatedBrightness).coerceIn(0f, 0.90f)
        if (scrimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }

        // Subtle, low-glare status badge when pre-alarm gradual dimming is actively lowering brightness
        if (isPreAlarmDimming) {
            val badgeAlpha = animatedBrightness.coerceIn(0.35f, 0.78f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .graphicsLayer { alpha = badgeAlpha }
                    .clip(CircleShape)
                    .background(Color(0xFF141418).copy(alpha = 0.85f))
                    .border(1.dp, customColor.copy(alpha = 0.28f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("pre_alarm_dimming_indicator")
            ) {
                Text(
                    text = buildString {
                        append("Pre-alarm dim • ${(animatedBrightness * 100).roundToInt()}%")
                        minutesUntilAlarm?.let { mins ->
                            append(" • ${mins}m left")
                        }
                    },
                    color = customColor.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = customFont
                )
            }
        }
    }
}

@Composable
fun DigitalMinimal(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f, 
    animationsEnabled: Boolean = true,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true,
    glow: Boolean = false,
    outline: Boolean = false,
    gradientColors: List<Color>? = null
) {
    val format = remember(use24Hour, showAmPm, showSeconds, calendar.timeZone) {
        val pattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
        SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = calendar.timeZone ?: TimeZone.getDefault()
        }
    }
    val formattedText = format.format(calendar.time)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxFitFontSize = (maxWidth.value / (formattedText.length * 0.58f)).coerceAtMost(maxHeight.value * 0.65f)
        val finalFontSize = minOf(110f * scale, maxFitFontSize).coerceAtLeast(28f)

        val textStyle = androidx.compose.ui.text.TextStyle(
            color = if (gradientColors == null && !outline) color else Color.Unspecified,
            fontSize = finalFontSize.sp,
            fontWeight = FontWeight.Thin,
            fontFamily = fontFamily,
            drawStyle = if (outline) androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * scale) else androidx.compose.ui.graphics.drawscope.Fill,
            shadow = if (glow && !outline) androidx.compose.ui.graphics.Shadow(color = color, offset = Offset(0f, 0f), blurRadius = 20f * scale) else null
        )
        
        val brushModifier = if (gradientColors != null) {
            Modifier.graphicsLayer(alpha = 0.99f).drawWithCache {
                val brush = androidx.compose.ui.graphics.Brush.linearGradient(gradientColors)
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop)
                }
            }
        } else Modifier
        
        Text(
            text = formattedText,
            style = textStyle,
            maxLines = 1,
            softWrap = false,
            modifier = brushModifier
        )
    }
}

@Composable
fun AnalogClassic(calendar: Calendar, color: Color, scale: Float = 1.0f, animationsEnabled: Boolean = true, showSeconds: Boolean = true) {
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = if (animationsEnabled && showSeconds) calendar.get(Calendar.SECOND) else 0

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxAllowed = minOf(maxWidth, maxHeight)
        val targetSize = minOf((300 * scale).dp, maxAllowed)

        Canvas(modifier = Modifier.size(targetSize)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = (size.minDimension / 2 - 16.dp.toPx()).coerceAtLeast(20f)

            drawCircle(color = color, radius = radius, style = Stroke(width = 8f))
            
            // Hour hand
            val hourAngle = Math.toRadians((hour * 30 + minute / 2).toDouble() - 90)
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(
                    cx + (radius * 0.5f) * cos(hourAngle).toFloat(),
                    cy + (radius * 0.5f) * sin(hourAngle).toFloat()
                ),
                strokeWidth = 14f, cap = StrokeCap.Round
            )

            // Minute hand
            val minAngle = Math.toRadians((minute * 6).toDouble() - 90)
            drawLine(
                color = color.copy(alpha = 0.7f),
                start = Offset(cx, cy),
                end = Offset(
                    cx + (radius * 0.75f) * cos(minAngle).toFloat(),
                    cy + (radius * 0.75f) * sin(minAngle).toFloat()
                ),
                strokeWidth = 10f, cap = StrokeCap.Round
            )

            // Second hand
            if (animationsEnabled) {
                val secAngle = Math.toRadians((second * 6).toDouble() - 90)
                drawLine(
                    color = Color.Red,
                    start = Offset(cx, cy),
                    end = Offset(
                        cx + (radius * 0.85f) * cos(secAngle).toFloat(),
                        cy + (radius * 0.85f) * sin(secAngle).toFloat()
                    ),
                    strokeWidth = 4f, cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun DigitalNeon(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val format = remember(use24Hour, showAmPm, showSeconds) {
        val pattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Warm Brick Charcoal background
            drawRect(color = Color(0xFF0F0F10))
            val brickHeight = 32.dp.toPx()
            val brickWidth = 64.dp.toPx()
            val strokeWidth = 1.dp.toPx()
            val brickColor = Color(0xFF1F1F22)
            
            // Draw horizontal brick lines
            for (y in 0..size.height.toInt() step brickHeight.toInt()) {
                drawLine(
                    color = brickColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = strokeWidth
                )
            }
            // Draw vertical offset lines
            var isOffset = false
            for (y in 0..size.height.toInt() step brickHeight.toInt()) {
                val startXOffset = if (isOffset) brickWidth / 2f else 0f
                for (x in (startXOffset.toInt() - brickWidth.toInt())..size.width.toInt() step brickWidth.toInt()) {
                    drawLine(
                        color = brickColor,
                        start = Offset(x.toFloat(), y.toFloat()),
                        end = Offset(x.toFloat(), y.toFloat() + brickHeight),
                        strokeWidth = strokeWidth
                    )
                }
                isOffset = !isOffset
            }
        }
        
        Text(
            text = format.format(calendar.time),
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                color = color,
                fontSize = (140 * scale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = color.copy(alpha = 0.8f),
                    offset = Offset(0f, 0f),
                    blurRadius = 35f
                )
            )
        )
    }
}

@Composable
fun WordClock(calendar: Calendar, color: Color, fontFamily: FontFamily) {
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    Text(
        text = "IT IS\n" + format.format(calendar.time).uppercase(),
        color = color,
        fontSize = 60.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = fontFamily
    )
}

@Composable
fun FuturisticTech(calendar: Calendar, color: Color, fontFamily: FontFamily) {
    val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    Text(
        text = format.format(calendar.time),
        color = color,
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        fontFamily = fontFamily,
        letterSpacing = 4.sp
    )
}

private val AirportFlapFontFamily = FontFamily(
    Font(R.font.oswald, FontWeight.Bold)
)

private val AirportTelemetryFontFamily = FontFamily(
    Font(R.font.share_tech_mono, FontWeight.Normal)
)

private data class AirportFlightEntry(
    val flight: String,
    val destination: String,
    val gate: String,
    val status: String,
    val statusColor: Color
)

private val AirportSampleFlights = listOf(
    AirportFlightEntry("LH 401", "FRANKFURT", "B14", "BOARDING", Color(0xFF81C995)),
    AirportFlightEntry("JL 005", "TOKYO HND", "A08", "ON TIME ", Color(0xFFFFD54F)),
    AirportFlightEntry("AF 022", "PARIS CDG", "C22", "GATE OPEN", Color(0xFF81C995)),
    AirportFlightEntry("BA 117", "LONDON LHR", "D05", "ON TIME ", Color(0xFFFFD54F)),
    AirportFlightEntry("SQ 025", "SINGAPORE", "E19", "FINAL CALL", Color(0xFFFF8A65))
)

@Composable
fun RetroFlip(
    calendar: Calendar,
    color: Color,
    fontFamily: FontFamily,
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true,
    isDuoCompact: Boolean = false,
    standbyViewModel: StandbyViewModel? = null,
    paneIndex: Int = 0
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            vm.refreshDeviceLocation(context)
        }
    }

    val hourText = SimpleDateFormat(if (use24Hour) "HH" else "hh", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time)
    val minuteText = SimpleDateFormat("mm", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time)
    val secondText = SimpleDateFormat("ss", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time)
    val amPmText = SimpleDateFormat("a", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time).uppercase(Locale.getDefault())
    val dayOfWeekText = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time).uppercase(Locale.getDefault()).take(3)
    val dayOfMonthText = SimpleDateFormat("dd", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time)
    val monthText = SimpleDateFormat("MMM", Locale.getDefault()).apply { timeZone = activeTimeZone }.format(calendar.time).uppercase(Locale.getDefault()).take(3)

    // Classic Solari airport split-flap ivory/white or user's custom accent color
    val flapTextColor = if (color == Color.White) Color(0xFFF5F3EB) else color
    val airportAmber = Color(0xFFFFC83D)

    var flightIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        vm.refreshDeviceLocation(context)
        while (true) {
            delay(7500)
            flightIndex = (flightIndex + 1) % AirportSampleFlights.size
        }
    }
    val activeFlight = AirportSampleFlights[flightIndex]

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            .padding(
                horizontal = if (isDuoCompact) 4.dp else 12.dp,
                vertical = if (isDuoCompact) 4.dp else 10.dp
            )
            .testTag("airport_flipboard_clock"),
        contentAlignment = Alignment.Center
    ) {
        val availW = maxWidth.value
        val availH = maxHeight.value
        val showFlightTicker = availH >= 130f && availW >= 180f
        val showTerminalHeader = availH >= 120f

        val baseBoardWidth = if (showSeconds && !isDuoCompact) 520f else 360f
        val baseBoardHeight = if (showFlightTicker) 275f else 190f

        val fitScale = minOf(
            scale.coerceIn(0.65f, 1.35f),
            availW / baseBoardWidth,
            availH / baseBoardHeight
        ).coerceIn(0.38f, 1.35f)

        val cellWidth = (if (showSeconds && !isDuoCompact) 66f else 82f) * fitScale
        val cellHeight = (if (showSeconds && !isDuoCompact) 108f else 130f) * fitScale
        val digitFontSize = (cellHeight * 0.68f).sp
        val cellCorner = (7f * fitScale).dp

        // Outer Brushed Anodized Aluminum Airport Solari Frame
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14161B), Color(0xFF0C0D11), Color(0xFF12141A))
                    )
                )
                .border(1.5.dp, Color(0xFF2B2E38), RoundedCornerShape(16.dp))
                .padding(
                    horizontal = (12f * fitScale).coerceAtLeast(6f).dp,
                    vertical = (10f * fitScale).coerceAtLeast(6f).dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP AIRPORT TERMINAL HEADER BAR (Solari Udine Style)
            if (showTerminalHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF090A0D))
                        .border(1.dp, Color(0xFF22252E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(airportAmber)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✈ ${regionInfo.airportCode}",
                                color = Color(0xFF0B0C0E),
                                fontSize = (11f * fitScale).coerceIn(9f, 13f).sp,
                                fontFamily = AirportTelemetryFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isDuoCompact) regionInfo.airportCityName else "${regionInfo.airportCityName} TERMINAL • ${regionInfo.standardTimeShort}",
                            color = airportAmber.copy(alpha = 0.92f),
                            fontSize = (11f * fitScale).coerceIn(8.5f, 13f).sp,
                            fontFamily = AirportTelemetryFontFamily,
                            letterSpacing = 1.2.sp,
                            maxLines = 1
                        )
                    }

                    // Mini Split-Flap Calendar Date Modules (EEE | DD | MMM)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AirportMiniWordFlap(
                            text = dayOfWeekText,
                            textColor = airportAmber,
                            fontScale = fitScale
                        )
                        AirportMiniWordFlap(
                            text = dayOfMonthText,
                            textColor = flapTextColor,
                            fontScale = fitScale
                        )
                        if (!isDuoCompact) {
                            AirportMiniWordFlap(
                                text = monthText,
                                textColor = flapTextColor,
                                fontScale = fitScale
                            )
                        }
                    }
                }
            }

            // 2. MAIN MECHANICAL SPLIT-FLAP CLOCK DRUM BAY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF060709))
                    .border(1.dp, Color(0xFF1F222B), RoundedCornerShape(12.dp))
                    .padding(
                        horizontal = (10f * fitScale).coerceAtLeast(4f).dp,
                        vertical = (8f * fitScale).coerceAtLeast(4f).dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy((4f * fitScale).dp)
                ) {
                    // Column Sub-headers (HOURS / MINUTES / SECONDS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showSeconds && !isDuoCompact) {
                                "HOURS               MINUTES              SECONDS"
                            } else {
                                "HOURS                     MINUTES"
                            },
                            color = Color(0xFF7A8090),
                            fontSize = (9.5f * fitScale).coerceIn(7.5f, 11f).sp,
                            fontFamily = AirportTelemetryFontFamily,
                            letterSpacing = 1.6.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy((6f * fitScale).coerceAtLeast(3f).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // HOURS: 2 individual mechanical split-flap drums
                        AirportSplitFlapCell(
                            char = hourText.getOrElse(0) { '0' },
                            textColor = flapTextColor,
                            fontSize = digitFontSize,
                            cellWidth = cellWidth.dp,
                            cellHeight = cellHeight.dp,
                            cornerRadius = cellCorner,
                            cascadeDelayMs = 0
                        )
                        AirportSplitFlapCell(
                            char = hourText.getOrElse(1) { '0' },
                            textColor = flapTextColor,
                            fontSize = digitFontSize,
                            cellWidth = cellWidth.dp,
                            cellHeight = cellHeight.dp,
                            cornerRadius = cellCorner,
                            cascadeDelayMs = 35
                        )

                        // Mechanical Riveted Colon Pillar
                        AirportMechanicalColon(
                            height = cellHeight.dp,
                            color = airportAmber,
                            scale = fitScale
                        )

                        // MINUTES: 2 individual mechanical split-flap drums
                        AirportSplitFlapCell(
                            char = minuteText.getOrElse(0) { '0' },
                            textColor = flapTextColor,
                            fontSize = digitFontSize,
                            cellWidth = cellWidth.dp,
                            cellHeight = cellHeight.dp,
                            cornerRadius = cellCorner,
                            cascadeDelayMs = 70
                        )
                        AirportSplitFlapCell(
                            char = minuteText.getOrElse(1) { '0' },
                            textColor = flapTextColor,
                            fontSize = digitFontSize,
                            cellWidth = cellWidth.dp,
                            cellHeight = cellHeight.dp,
                            cornerRadius = cellCorner,
                            cascadeDelayMs = 105
                        )

                        // SECONDS: 2 slightly smaller mechanical split-flap drums
                        if (showSeconds && !isDuoCompact) {
                            AirportMechanicalColon(
                                height = (cellHeight * 0.84f).dp,
                                color = airportAmber.copy(alpha = 0.7f),
                                scale = fitScale * 0.85f
                            )
                            AirportSplitFlapCell(
                                char = secondText.getOrElse(0) { '0' },
                                textColor = airportAmber,
                                fontSize = digitFontSize * 0.82f,
                                cellWidth = (cellWidth * 0.82f).dp,
                                cellHeight = (cellHeight * 0.84f).dp,
                                cornerRadius = cellCorner * 0.85f,
                                cascadeDelayMs = 0,
                                isFastSecondDrum = true
                            )
                            AirportSplitFlapCell(
                                char = secondText.getOrElse(1) { '0' },
                                textColor = airportAmber,
                                fontSize = digitFontSize * 0.82f,
                                cellWidth = (cellWidth * 0.82f).dp,
                                cellHeight = (cellHeight * 0.84f).dp,
                                cornerRadius = cellCorner * 0.85f,
                                cascadeDelayMs = 20,
                                isFastSecondDrum = true
                            )
                        }

                        // AM / PM Mechanical Split-Flap Cartridge
                        if (!use24Hour && showAmPm) {
                            Spacer(modifier = Modifier.width((2f * fitScale).dp))
                            AirportMeridianFlapCard(
                                meridian = amPmText,
                                textColor = airportAmber,
                                boxWidth = (cellWidth * 0.72f).dp,
                                boxHeight = (cellHeight * 0.56f).dp,
                                fontSize = (digitFontSize.value * 0.34f).sp,
                                cornerRadius = cellCorner * 0.75f
                            )
                        }
                    }
                }
            }

            // 3. BOTTOM AIRPORT TERMINAL LOCATION SPLIT-FLAP ROW (Shows User's Location Name from GPS or Settings)
            if (showFlightTicker) {
                val charSlots = if (isDuoCompact) 8 else 11
                val locationChars = regionInfo.airportCityName.padEnd(charSlots, ' ').take(charSlots)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF08090C))
                        .border(1.dp, Color(0xFF22252E), RoundedCornerShape(8.dp))
                        .clickable {
                            flightIndex = (flightIndex + 1) % AirportSampleFlights.size
                            if (locationMode == "auto" && gpsLocation == null) {
                                try {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                            android.Manifest.permission.ACCESS_FINE_LOCATION
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            } else {
                                vm.refreshDeviceLocation(context)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("airport_flipboard_flight_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Location / Airport Code Flap
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "LOC",
                            color = Color(0xFF6E7382),
                            fontSize = (9.5f * fitScale).coerceIn(8f, 11f).sp,
                            fontFamily = AirportTelemetryFontFamily
                        )
                        AirportMiniWordFlap(
                            text = regionInfo.airportCode,
                            textColor = airportAmber,
                            fontScale = fitScale
                        )
                    }

                    // User's Location Name Individual Character Split-Flap Array
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        locationChars.forEachIndexed { idx, ch ->
                            AirportMiniCharFlap(
                                char = ch,
                                textColor = flapTextColor,
                                scale = fitScale,
                                cascadeDelayMs = idx * 24
                            )
                        }
                    }

                    // TimeZone / Status Flap
                    if (!isDuoCompact) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ZONE",
                                color = Color(0xFF6E7382),
                                fontSize = (9.5f * fitScale).coerceIn(8f, 11f).sp,
                                fontFamily = AirportTelemetryFontFamily
                            )
                            AirportMiniWordFlap(
                                text = regionInfo.standardTimeShort,
                                textColor = flapTextColor,
                                fontScale = fitScale
                            )
                            AirportMiniWordFlap(
                                text = activeFlight.status.trim(),
                                textColor = activeFlight.statusColor,
                                fontScale = fitScale
                            )
                        }
                    } else {
                        AirportMiniWordFlap(
                            text = regionInfo.standardTimeShort,
                            textColor = activeFlight.statusColor,
                            fontScale = fitScale
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportMechanicalColon(
    height: Dp,
    color: Color,
    scale: Float
) {
    val dotSize = (8f * scale).coerceAtLeast(4f).dp
    Column(
        modifier = Modifier
            .height(height)
            .padding(horizontal = (2f * scale).dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .border(0.75.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )
        Box(
            modifier = Modifier
                .size(dotSize)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .border(0.75.dp, Color.Black.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Single-character mechanical Solari Split-Flap Drum module with true 3D top-to-bottom
 * folding flap animation (`rotationX`), recessed aluminum housing, horizontal split gap,
 * and metallic side hinge axle pins.
 */
@Composable
fun AirportSplitFlapCell(
    char: Char,
    textColor: Color,
    fontSize: TextUnit,
    cellWidth: Dp,
    cellHeight: Dp,
    cornerRadius: Dp,
    cascadeDelayMs: Int = 0,
    isFastSecondDrum: Boolean = false
) {
    var displayedChar by remember { mutableStateOf(char) }
    var previousChar by remember { mutableStateOf(char) }
    val flipRotation = remember { Animatable(180f) }

    LaunchedEffect(char) {
        if (char != displayedChar) {
            if (cascadeDelayMs > 0) {
                delay(cascadeDelayMs.toLong())
            }
            // For non-seconds drums, perform a brief intermediate mechanical drum step like a physical Solari wheel
            if (!isFastSecondDrum && char.isDigit() && displayedChar.isDigit()) {
                val intermediate = (((displayedChar.digitToInt() + 9) % 10)).digitToChar()
                if (intermediate != char && intermediate != displayedChar) {
                    previousChar = displayedChar
                    displayedChar = intermediate
                    flipRotation.snapTo(0f)
                    flipRotation.animateTo(
                        targetValue = 180f,
                        animationSpec = tween(durationMillis = 120, easing = FastOutLinearInEasing)
                    )
                }
            }
            previousChar = displayedChar
            displayedChar = char
            flipRotation.snapTo(0f)
            flipRotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(
                    durationMillis = if (isFastSecondDrum) 280 else 360,
                    easing = LinearOutSlowInEasing
                )
            )
        }
    }

    val currentAngle = flipRotation.value
    val isFlipping = currentAngle < 179.5f

    Box(
        modifier = Modifier
            .size(width = cellWidth, height = cellHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF040507))
            .border(1.2.dp, Color(0xFF2A2E39), RoundedCornerShape(cornerRadius))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Base Static Split-Flap Halves:
        // Top half shows the new (target) character; Bottom half shows previousChar while angle < 90°, then displayedChar
        val bottomStaticChar = if (isFlipping && currentAngle < 90f) previousChar else displayedChar

        // Static Top Half
        AirportFlapHalfSurface(
            char = displayedChar,
            isTopHalf = true,
            textColor = textColor,
            fontSize = fontSize,
            cornerRadius = cornerRadius,
            shadowAlpha = if (isFlipping && currentAngle < 90f) ((90f - currentAngle) / 90f) * 0.35f else 0f
        )

        // Static Bottom Half
        AirportFlapHalfSurface(
            char = bottomStaticChar,
            isTopHalf = false,
            textColor = textColor,
            fontSize = fontSize,
            cornerRadius = cornerRadius,
            shadowAlpha = if (isFlipping && currentAngle < 90f) {
                (currentAngle / 90f) * 0.55f
            } else if (isFlipping) {
                ((180f - currentAngle) / 90f) * 0.35f
            } else 0f
        )

        // 2. 3D Swinging Mechanical Flap Leaf
        if (isFlipping) {
            if (currentAngle < 90f) {
                // Falling Upper Flap (previousChar top half rotating from 0° down to -90°)
                AirportFlapHalfSurface(
                    char = previousChar,
                    isTopHalf = true,
                    textColor = textColor,
                    fontSize = fontSize,
                    cornerRadius = cornerRadius,
                    shadowAlpha = (currentAngle / 90f) * 0.45f,
                    modifier = Modifier.graphicsLayer {
                        rotationX = -currentAngle
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        cameraDistance = 14f * density
                    }
                )
            } else {
                // Unfolding Lower Flap (displayedChar bottom half rotating from +90° down to 0°)
                AirportFlapHalfSurface(
                    char = displayedChar,
                    isTopHalf = false,
                    textColor = textColor,
                    fontSize = fontSize,
                    cornerRadius = cornerRadius,
                    shadowAlpha = ((180f - currentAngle) / 90f) * 0.40f,
                    modifier = Modifier.graphicsLayer {
                        rotationX = 180f - currentAngle
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        cameraDistance = 14f * density
                    }
                )
            }
        }

        // 3. Mechanical Center Split Gap & Left/Right Metallic Hinge Axles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            val gapH = 3.dp.toPx()

            // Horizontal split shadow trench
            drawRect(
                color = Color(0xFF030305),
                topLeft = Offset(0f, midY - gapH / 2f),
                size = Size(size.width, gapH)
            )
            // Subtle top flap bottom-edge highlight & bottom flap top-edge bevel
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, midY + gapH / 2f + 0.8f),
                end = Offset(size.width, midY + gapH / 2f + 0.8f),
                strokeWidth = 1f
            )

            // Left & Right Mechanical Hinge Axle Pins
            val pinW = 4.5.dp.toPx()
            val pinH = 9.dp.toPx()
            // Left hinge notch
            drawRoundRect(
                color = Color(0xFF4B505E),
                topLeft = Offset(0f, midY - pinH / 2f),
                size = Size(pinW, pinH),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0xFF9AA0B0),
                start = Offset(1.dp.toPx(), midY - pinH / 3f),
                end = Offset(1.dp.toPx(), midY + pinH / 3f),
                strokeWidth = 1.dp.toPx()
            )
            // Right hinge notch
            drawRoundRect(
                color = Color(0xFF4B505E),
                topLeft = Offset(size.width - pinW, midY - pinH / 2f),
                size = Size(pinW, pinH),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            drawLine(
                color = Color(0xFF9AA0B0),
                start = Offset(size.width - 1.5.dp.toPx(), midY - pinH / 3f),
                end = Offset(size.width - 1.5.dp.toPx(), midY + pinH / 3f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun AirportFlapHalfSurface(
    char: Char,
    isTopHalf: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    cornerRadius: Dp,
    shadowAlpha: Float = 0f,
    modifier: Modifier = Modifier
) {
    val bgBrush = if (isTopHalf) {
        Brush.verticalGradient(
            0.0f to Color(0xFF23262E),
            0.5f to Color(0xFF191C22),
            0.5f to Color.Transparent,
            1.0f to Color.Transparent
        )
    } else {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.5f to Color.Transparent,
            0.5f to Color(0xFF14171C),
            1.0f to Color(0xFF1D2027)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBrush)
            .drawWithCache {
                onDrawWithContent {
                    clipRect(
                        left = 0f,
                        top = if (isTopHalf) 0f else size.height / 2f,
                        right = size.width,
                        bottom = if (isTopHalf) size.height / 2f else size.height
                    ) {
                        this@onDrawWithContent.drawContent()
                        if (shadowAlpha > 0.01f) {
                            drawRect(Color.Black.copy(alpha = shadowAlpha.coerceIn(0f, 0.85f)))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            color = textColor,
            fontSize = fontSize,
            fontFamily = AirportFlapFontFamily,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AirportMeridianFlapCard(
    meridian: String,
    textColor: Color,
    boxWidth: Dp,
    boxHeight: Dp,
    fontSize: TextUnit,
    cornerRadius: Dp
) {
    var displayed by remember { mutableStateOf(meridian) }
    var previous by remember { mutableStateOf(meridian) }
    val flipAngle = remember { Animatable(180f) }

    LaunchedEffect(meridian) {
        if (meridian != displayed) {
            previous = displayed
            displayed = meridian
            flipAngle.snapTo(0f)
            flipAngle.animateTo(180f, tween(340, easing = LinearOutSlowInEasing))
        }
    }

    Box(
        modifier = Modifier
            .size(width = boxWidth, height = boxHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF20232B), Color(0xFF15171D))
                )
            )
            .border(1.dp, Color(0xFF2E323E), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (flipAngle.value < 90f) previous else displayed,
            color = textColor,
            fontSize = fontSize,
            fontFamily = AirportFlapFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val midY = size.height / 2f
            drawLine(
                color = Color(0xFF040507),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun AirportMiniCharFlap(
    char: Char,
    textColor: Color,
    scale: Float,
    cascadeDelayMs: Int
) {
    var displayedChar by remember { mutableStateOf(char) }
    val rotX = remember { Animatable(0f) }

    LaunchedEffect(char) {
        if (char != displayedChar) {
            delay(cascadeDelayMs.toLong())
            rotX.snapTo(-85f)
            displayedChar = char
            rotX.animateTo(0f, tween(210, easing = LinearOutSlowInEasing))
        }
    }

    val w = (15f * scale).coerceIn(11f, 20f).dp
    val h = (23f * scale).coerceIn(17f, 30f).dp

    Box(
        modifier = Modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF20232B), Color(0xFF14161B))
                )
            )
            .border(0.75.dp, Color(0xFF2B2F3B), RoundedCornerShape(3.dp))
            .graphicsLayer {
                rotationX = rotX.value
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayedChar.toString(),
            color = textColor,
            fontSize = (13.5f * scale).coerceIn(10f, 17f).sp,
            fontFamily = AirportFlapFontFamily,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color(0xFF040507),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5f
            )
        }
    }
}

@Composable
private fun AirportMiniWordFlap(
    text: String,
    textColor: Color,
    fontScale: Float
) {
    var displayed by remember { mutableStateOf(text) }
    val rotX = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (text != displayed) {
            rotX.snapTo(-80f)
            displayed = text
            rotX.animateTo(0f, tween(240, easing = LinearOutSlowInEasing))
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF20232B), Color(0xFF14161B))
                )
            )
            .border(0.8.dp, Color(0xFF2C303C), RoundedCornerShape(4.dp))
            .graphicsLayer {
                rotationX = rotX.value
                cameraDistance = 12f * density
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayed,
            color = textColor,
            fontSize = (11.5f * fontScale).coerceIn(9f, 14f).sp,
            fontFamily = AirportTelemetryFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            drawLine(
                color = Color.Black.copy(alpha = 0.65f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.2f
            )
        }
    }
}

@Composable
fun GradientVibe(calendar: Calendar, color: Color, fontFamily: FontFamily) {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    Text(
        text = format.format(calendar.time),
        color = color,
        fontSize = 100.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = fontFamily
    )
}

class MatrixStream(
    val xPercent: Float,
    var speed: Float,
    var chars: List<String>,
    var headY: Float
)

@Composable
fun MatrixRainBackground(accentColor: Color) {
    val characters = remember {
        // Standard alphanumeric
        ('0'..'9').map { it.toString() } + 
        ('A'..'Z').map { it.toString() } + 
        ('a'..'z').map { it.toString() } +
        // Roman numerals & special script style European glyphs (guaranteeing single-character length)
        listOf("Ⅰ", "Ⅱ", "Ⅲ", "Ⅳ", "Ⅴ", "Ⅵ", "Ⅶ", "Ⅷ", "Ⅸ", "Ⅹ", "Ⅺ", "Ⅻ", "Ⅼ", "Ⅽ", "Ⅾ", "Ⅿ", "Ø", "Æ", "Å", "ß", "ç", "Δ", "Ξ", "Ω", "Ψ")
    }
    // High density columns to make the animation significantly more visible and rich
    val columnCount = 32
    val streams = remember {
        List(columnCount) { index ->
            MatrixStream(
                xPercent = (index.toFloat() / columnCount) + 0.015f,
                speed = (6..12).random().toFloat(),
                chars = List(20) { characters.random() },
                headY = (-(100..600).random()).toFloat()
            )
        }
    }

    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(40)
            streams.forEach { stream ->
                stream.headY += stream.speed
                // Dynamically recycle stream heads once they flow past the typical vertical space
                if (stream.headY > 2200f) {
                    stream.headY = (-(150..500).random()).toFloat()
                    stream.speed = (6..12).random().toFloat()
                }
                // Randomly flicker/change 20% of characters to look highly active and dynamic
                if ((0..4).random() == 0) {
                    stream.chars = List(20) { characters.random() }
                }
            }
            tick++
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dummy = tick // Reference tick to trigger redrawing on Canvas
        
        streams.forEach { stream ->
            val x = stream.xPercent * size.width
            val sizeY = 20.dp.toPx() // Slightly taller spacing for bigger characters
            
            for (i in 0 until stream.chars.size) {
                val charY = stream.headY - (i * sizeY)
                if (charY > -50f && charY < size.height + 50f) {
                    val alpha = (1f - (i.toFloat() / stream.chars.size)).coerceIn(0f, 1f)
                    val drawColor = if (i == 0) {
                        Color.White.copy(alpha = alpha * 0.95f) // White-hot digital lead
                    } else {
                        Color(0xFF39FF14).copy(alpha = alpha * 0.75f) // Vibrant glowing neon green trails as requested
                    }

                    val paint = android.graphics.Paint().apply {
                        this.color = drawColor.toArgb()
                        this.textSize = 17.dp.toPx() // Significantly increased font size for higher visibility
                        this.isAntiAlias = true
                        this.textAlign = android.graphics.Paint.Align.CENTER
                        this.typeface = android.graphics.Typeface.MONOSPACE
                    }
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        stream.chars[i],
                        x,
                        charY,
                        paint
                    )
                }
            }
        }
    }
}

@Composable
fun BinaryStyle(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        MatrixRainBackground(accentColor = color)
        
        // Slightly reduced overlay to let the dense matrix rain shine through beautifully
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
        
        val tz = calendar.timeZone ?: TimeZone.getDefault()
        val formatPattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
        val format = SimpleDateFormat(formatPattern, Locale.getDefault()).apply { timeZone = tz }
        val text = format.format(calendar.time).map { char ->
            when {
                char == ':' -> ":"
                char == ' ' -> " "
                char.isDigit() -> Integer.toBinaryString(char.toString().toInt()).padStart(4, '0')
                else -> char.toString()
            }
        }.joinToString(" ")
        
        val availableWidth = (maxWidth.value - 48f).coerceAtLeast(180f)
        val maxFitFontSize = (availableWidth / (text.length * 0.58f)).coerceIn(13f, 52f)
        val finalFontSize = minOf(52f * scale, maxFitFontSize).coerceAtLeast(13f)

        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = finalFontSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFF39FF14).copy(alpha = 0.85f),
                    offset = Offset(0f, 0f),
                    blurRadius = 24f
                )
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun ModernBoldTextClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
    val periodName = when (hourOfDay) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    val timePattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "")
    val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val amPmStr = SimpleDateFormat("a", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val heightLimitScale = (maxHeight.value / 250f).coerceIn(0.45f, 1.35f)
        val widthLimitScale = (maxWidth.value / 560f).coerceIn(0.45f, 1.35f)
        val fitScale = minOf(scale, heightLimitScale, widthLimitScale)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayOfWeek,
                color = Color.White,
                fontSize = (38 * fitScale).sp,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamily,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = periodName,
                color = Color.Gray,
                fontSize = (24 * fitScale).sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fontFamily,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = (8 * fitScale).dp)
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = (2 * fitScale).dp)
            ) {
                Text(
                    text = timeStr,
                    color = color,
                    fontSize = (90 * fitScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = (90 * fitScale).sp
                )
                if (!use24Hour && showAmPm) {
                    Text(
                        text = amPmStr,
                        color = Color.DarkGray,
                        fontSize = (24 * fitScale).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = (12 * fitScale).dp, start = (6 * fitScale).dp)
                    )
                }
            }
            Text(
                text = dateStr,
                color = Color.LightGray,
                fontSize = (20 * fitScale).sp,
                fontWeight = FontWeight.Normal,
                fontFamily = fontFamily,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = (6 * fitScale).dp)
            )
        }
    }
}

@Composable
fun LargeSidebarClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    nextSystemAlarm: String?, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val timePattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "")
    val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault()).apply { timeZone = tz }
    val amPmFormat = SimpleDateFormat(" a", Locale.getDefault()).apply { timeZone = tz }
    val dayOfWeekAbbr = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time).uppercase()
    val dayOfMonth = SimpleDateFormat("d", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    
    val alarmText = nextSystemAlarm ?: "No Alarm"

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = (40 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val timeStr = timeFormat.format(calendar.time)
        val fullTimeText = if (!use24Hour && showAmPm) {
            timeStr + amPmFormat.format(calendar.time)
        } else {
            timeStr
        }

        Text(
            text = fullTimeText,
            color = color,
            fontSize = (if (showSeconds || (!use24Hour && showAmPm)) 100 * scale else 135 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier
                .width((2 * scale).dp)
                .height((100 * scale).dp)
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width((36 * scale).dp))

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayOfWeekAbbr,
                    color = color.copy(alpha = 0.7f),
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.width((8 * scale).dp))
                Text(
                    text = dayOfMonth,
                    color = Color.White,
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
            Spacer(modifier = Modifier.height((12 * scale).dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⏰",
                    color = Color.White,
                    fontSize = (18 * scale).sp,
                    modifier = Modifier.padding(end = (6 * scale).dp)
                )
                Text(
                    text = alarmText,
                    color = Color.LightGray,
                    fontSize = (18 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

class ClockHandsWedgeShape(private val startAngle: Float, private val sweepAngle: Float) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = maxOf(size.width, size.height)
        
        val path = Path().apply {
            moveTo(cx, cy)
            val rect = androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius)
            arcTo(rect, startAngle - 90f, sweepAngle, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun ContrastingSplitClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val pattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
    val format = SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = tz }
    val timeText = format.format(calendar.time)
    val baseFontSize = if (showSeconds || (!use24Hour && showAmPm)) 110 * scale else 150 * scale

    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    
    val minuteAngle = minute * 6f + second * 0.1f
    val hourAngle = hour * 30f + minute * 0.5f
    var sweepAngle = minuteAngle - hourAngle
    if (sweepAngle < 0) sweepAngle += 360f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val maxFitSize = ((maxWidth.value - 40f) / (timeText.length * 0.56f)).coerceAtMost(maxHeight.value * 0.65f)
        val actualFontSize = minOf(baseFontSize, maxFitSize).coerceAtLeast(28f)

        Text(
            text = timeText,
            color = color,
            fontSize = actualFontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            letterSpacing = (-3 * scale).sp
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ClockHandsWedgeShape(hourAngle, sweepAngle))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                color = Color.Black,
                fontSize = actualFontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = fontFamily,
                maxLines = 1,
                softWrap = false,
                letterSpacing = (-3 * scale).sp
            )
        }
    }
}

@Composable
fun AnalogDashboard(calendar: Calendar, color: Color, fontFamily: FontFamily, scale: Float = 1.0f, animationsEnabled: Boolean = true, showSeconds: Boolean = true) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time).uppercase()
    val dayOfMonth = SimpleDateFormat("d", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val dialScale = scale.coerceIn(0.55f, 1.15f)
        val topBottomPad = (maxHeight * 0.11f).coerceIn(24.dp, 48.dp)
        val sidePad = (maxWidth * 0.08f).coerceIn(32.dp, 72.dp)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2

            val pad = 14.dp.toPx()
            val leftBound = pad
            val rightBound = size.width - pad
            val topBound = pad
            val bottomBound = size.height - pad

            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6).toDouble() - 90)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()

                var t = Float.MAX_VALUE

                if (dx < -0.0001f) {
                    val tLeft = (leftBound - cx) / dx
                    if (tLeft > 0 && tLeft < t) t = tLeft
                } else if (dx > 0.0001f) {
                    val tRight = (rightBound - cx) / dx
                    if (tRight > 0 && tRight < t) t = tRight
                }

                if (dy < -0.0001f) {
                    val tTop = (topBound - cy) / dy
                    if (tTop > 0 && tTop < t) t = tTop
                } else if (dy > 0.0001f) {
                    val tBottom = (bottomBound - cy) / dy
                    if (tBottom > 0 && tBottom < t) t = tBottom
                }

                if (t != Float.MAX_VALUE) {
                    val xOuter = cx + t * dx
                    val yOuter = cy + t * dy

                    val isMajor = (i % 5 == 0)
                    val tickLength = if (isMajor) 20.dp.toPx() * dialScale else 10.dp.toPx() * dialScale
                    val strokeWidth = if (isMajor) 3.dp.toPx() * dialScale else 1.dp.toPx() * dialScale
                    val tickColor = if (isMajor) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f)

                    val xInner = cx + (t - tickLength) * dx
                    val yInner = cy + (t - tickLength) * dy

                    drawLine(
                        color = tickColor,
                        start = Offset(xInner, yInner),
                        end = Offset(xOuter, yOuter),
                        strokeWidth = strokeWidth
                    )
                }
            }

            // Hour Hand
            val hourAngle = Math.toRadians((hour * 30 + minute / 2).toDouble() - 90)
            val hourHandLength = size.minDimension * 0.23f * dialScale
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(
                    cx + hourHandLength * cos(hourAngle).toFloat(),
                    cy + hourHandLength * sin(hourAngle).toFloat()
                ),
                strokeWidth = 12f * dialScale,
                cap = StrokeCap.Round
            )
            val hCos = cos(hourAngle).toFloat()
            val hSin = sin(hourAngle).toFloat()
            drawLine(
                color = Color.Black,
                start = Offset(cx + 8f * hCos * dialScale, cy + 8f * hSin * dialScale),
                end = Offset(
                    cx + (hourHandLength - 4f * dialScale) * hCos,
                    cy + (hourHandLength - 4f * dialScale) * hSin
                ),
                strokeWidth = 2.5f * dialScale,
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minAngle = Math.toRadians((minute * 6).toDouble() - 90)
            val minHandLength = size.minDimension * 0.35f * dialScale
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(
                    cx + minHandLength * cos(minAngle).toFloat(),
                    cy + minHandLength * sin(minAngle).toFloat()
                ),
                strokeWidth = 10f * dialScale,
                cap = StrokeCap.Round
            )
            val mCos = cos(minAngle).toFloat()
            val mSin = sin(minAngle).toFloat()
            drawLine(
                color = Color.Black,
                start = Offset(cx + 8f * mCos * dialScale, cy + 8f * mSin * dialScale),
                end = Offset(
                    cx + (minHandLength - 4f * dialScale) * mCos,
                    cy + (minHandLength - 4f * dialScale) * mSin
                ),
                strokeWidth = 2f * dialScale,
                cap = StrokeCap.Round
            )

            // Second Hand with Tail
            if (animationsEnabled && showSeconds) {
                val secAngle = Math.toRadians((second * 6).toDouble() - 90)
                val secMainLength = size.minDimension * 0.41f * dialScale
                val secTailLength = 18.dp.toPx() * dialScale
                val sCos = cos(secAngle).toFloat()
                val sSin = sin(secAngle).toFloat()

                drawLine(
                    color = Color(0xFFFF3B30),
                    start = Offset(cx - secTailLength * sCos, cy - secTailLength * sSin),
                    end = Offset(cx + secMainLength * sCos, cy + secMainLength * sSin),
                    strokeWidth = 4f * dialScale,
                    cap = StrokeCap.Round
                )

                // Red pivot with white pin center
                drawCircle(color = Color(0xFFFF3B30), radius = 8.dp.toPx() * dialScale, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 2.5.dp.toPx() * dialScale, center = Offset(cx, cy))
            } else {
                drawCircle(color = Color.White, radius = 4.dp.toPx() * dialScale, center = Offset(cx, cy))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "12",
                color = Color.White,
                fontSize = (38 * dialScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topBottomPad)
            )
            Text(
                text = "6",
                color = Color.White,
                fontSize = (38 * dialScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = topBottomPad)
            )
            Text(
                text = "9",
                color = Color.White,
                fontSize = (38 * dialScale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = sidePad)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = sidePad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayOfWeek,
                    color = Color(0xFFFF3B30),
                    fontSize = (20 * dialScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(end = (4 * dialScale).dp)
                )
                Text(
                    text = dayOfMonth,
                    color = Color.White,
                    fontSize = (20 * dialScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(end = (12 * dialScale).dp)
                )
                Text(
                    text = "3",
                    color = Color.White,
                    fontSize = (38 * dialScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

@Composable
fun BubblePastelClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val hh = SimpleDateFormat(if (use24Hour) "HH" else "h", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val ss = SimpleDateFormat("ss", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    val amPmText = SimpleDateFormat("a", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    
    val hourDigits = hh.map { it.toString() }
    val minuteDigits = mm.map { it.toString() }
    val secondDigits = ss.map { it.toString() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalSymbols = hourDigits.size + 1 + minuteDigits.size + (if (showSeconds) 1 + secondDigits.size else 0) + (if (!use24Hour && showAmPm) 1 else 0)
        val widthFitScale = (maxWidth.value / (totalSymbols * 82f)).coerceIn(0.35f, 1.2f)
        val heightFitScale = (maxHeight.value / 190f).coerceIn(0.35f, 1.2f)
        val baseScale = if (showSeconds) scale * 0.8f else scale
        val actualScale = minOf(baseScale, widthFitScale, heightFitScale)

        Row(
            horizontalArrangement = Arrangement.spacedBy((4 * actualScale).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            hourDigits.forEachIndexed { idx, digit ->
                val digitColor = if (hourDigits.size == 1) color else {
                    if (idx == 0) color else color.copy(alpha = 0.85f)
                }
                BubbleDigit(digit, digitColor, fontFamily, actualScale)
            }

            Text(
                text = ":",
                color = color.copy(alpha = 0.5f),
                fontSize = (110 * actualScale).sp,
                fontWeight = FontWeight.Black,
                fontFamily = fontFamily,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = (4 * actualScale).dp)
            )

            minuteDigits.forEachIndexed { idx, digit ->
                val digitColor = if (idx == 0) color.copy(alpha = 0.7f) else color.copy(alpha = 0.6f)
                BubbleDigit(digit, digitColor, fontFamily, actualScale)
            }

            if (showSeconds) {
                Text(
                    text = ":",
                    color = color.copy(alpha = 0.4f),
                    fontSize = (90 * actualScale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = (4 * actualScale).dp)
                )
                secondDigits.forEachIndexed { idx, digit ->
                    val digitColor = if (idx == 0) color.copy(alpha = 0.5f) else color.copy(alpha = 0.45f)
                    BubbleDigit(digit, digitColor, fontFamily, actualScale)
                }
            }

            if (!use24Hour && showAmPm) {
                Text(
                    text = " " + amPmText.uppercase(),
                    color = color.copy(alpha = 0.5f),
                    fontSize = (20 * actualScale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    modifier = Modifier.padding(start = (6 * actualScale).dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun BubbleDigit(digit: String, color: Color, fontFamily: FontFamily, scale: Float = 1.0f) {
    Box(
        modifier = Modifier.padding((2 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = color,
            fontSize = (135 * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(4f * scale, 8f * scale),
                    blurRadius = 8f * scale
                )
            )
        )
    }
}

@Composable
fun AmbientGradientClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val pattern = (if (use24Hour) "H" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
    val format = SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = tz }
    val formattedText = format.format(calendar.time)
    val gradientBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.25f),
            color.copy(alpha = 0.45f),
            Color.Black
        )
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxFitSize = (maxWidth.value / (formattedText.length * 0.58f)).coerceAtMost(maxHeight.value * 0.65f)
        val finalFontSize = minOf(140f * scale, maxFitSize).coerceAtLeast(28f)

        Text(
            text = formattedText,
            color = color.copy(alpha = 0.85f),
            fontSize = finalFontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            letterSpacing = (-2 * scale).sp,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.15f),
                    offset = Offset(0f, 4f * scale),
                    blurRadius = 12f * scale
                )
            )
        )
    }
}

@Composable
fun NixieTubeClock(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val tz = calendar.timeZone ?: TimeZone.getDefault()
    val pattern = (if (use24Hour) "HH" else "hh") + ":mm" + (if (showSeconds) ":ss" else "") 
    val timeStr = SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
    
    val displayColor = if (color == Color.White) Color(0xFFFFB300) else color
    
    val androidColor = android.graphics.Color.argb(
        (displayColor.alpha * 255).toInt(),
        (displayColor.red * 255).toInt(),
        (displayColor.green * 255).toInt(),
        (displayColor.blue * 255).toInt()
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxNixieHeight = (maxHeight - 40.dp).coerceAtLeast(80.dp)
        val targetNixieHeight = minOf((150 * scale).dp, maxNixieHeight)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(targetNixieHeight),
                factory = { context ->
                    NixieClockView(context, null).apply {
                        setGlowColor(androidColor)
                    }
                },
                update = { view ->
                    view.setTime(timeStr)
                    view.setGlowColor(androidColor)
                }
            )
            
            if (!use24Hour && showAmPm) {
                val amPmText = SimpleDateFormat("a", Locale.getDefault()).apply { timeZone = tz }.format(calendar.time)
                Text(
                    text = amPmText.uppercase(),
                    color = color.copy(alpha = 0.5f),
                    fontSize = (20 * scale.coerceAtMost(1.1f)).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}


@Composable
fun NixieDigitTube(
    digit: String,
    color: Color,
    fontFamily: FontFamily,
    scale: Float = 1.0f,
    isDimmed: Boolean = false
) {
    // Authentic premium Nixie neon amber glow setup
    val activeGlowColor = Color(0xFFFF5200) // Saturated neon orange-red
    val activeCoreColor = Color(0xFFFFE599) // White-hot neon filament core (bright yellow-white)
    val ambientHaloColor = Color(0xFFFF3300) // Deep red/orange corona gas discharge
    
    // Inactive copper-nickel wire cathodes stack (all numbers 0 to 9)
    val inactiveCathodes = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    
    Box(
        modifier = Modifier
            .width((84 * scale).dp)
            .height((176 * scale).dp)
            .padding(horizontal = (4 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Glass Tube Envelope (rounded top dome, flat bottom sitting in the socket)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = (42 * scale).dp,
                            topEnd = (42 * scale).dp,
                            bottomStart = (6 * scale).dp,
                            bottomEnd = (6 * scale).dp
                        )
                    )
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F0B09), // Sleek vacuum dark background tint
                                Color(0xFF160E0A)  // Very subtle amber vacuum ambience
                            )
                        )
                    )
                    .border(
                        width = (1.5f * scale).dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(
                            topStart = (42 * scale).dp,
                            topEnd = (42 * scale).dp,
                            bottomStart = (6 * scale).dp,
                            bottomEnd = (6 * scale).dp
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 1. Fine Metallic Anode Mesh (Hexagonal/square pattern behind/around the filaments)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = (10f * scale).dp.toPx()
                    // Draw diagonal wire mesh grids
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y + size.width),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.05f),
                            start = Offset(size.width, y),
                            end = Offset(0f, y + size.width),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                }

                // 2. Structural internal elements: Mica end-caps (metal brackets top & bottom)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val plateThickness = (4f * scale).dp.toPx()
                    val marginSide = (10f * scale).dp.toPx()
                    
                    // Top mica plate
                    drawRect(
                        color = Color(0xFF2C241E), // Dark ceramic/mica grey
                        topLeft = Offset(marginSide, (22f * scale).dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - (marginSide * 2), plateThickness)
                    )
                    // Bottom mica plate 
                    drawRect(
                        color = Color(0xFF2C241E),
                        topLeft = Offset(marginSide, size.height - (18f * scale).dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - (marginSide * 2), plateThickness)
                    )
                    
                    // Support wires connecting plates vertically on the far edges
                    drawLine(
                        color = Color(0xFF42382F),
                        start = Offset(marginSide + 2f, (22f * scale).dp.toPx()),
                        end = Offset(marginSide + 2f, size.height - (18f * scale).dp.toPx()),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        color = Color(0xFF42382F),
                        start = Offset(size.width - marginSide - 2f, (22f * scale).dp.toPx()),
                        end = Offset(size.width - marginSide - 2f, size.height - (18f * scale).dp.toPx()),
                        strokeWidth = 1.5f
                    )
                }

                // 3. Ambient Sputter Glow inside the tube (neon gas background discharge)
                Box(
                    modifier = Modifier
                        .size((70 * scale).dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    if (isDimmed) Color.Transparent else activeGlowColor.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // 4. Physical Stack of Inactive Cathodes (stacked dark metal letters)
                Box(contentAlignment = Alignment.Center) {
                    inactiveCathodes.forEach { wDigit ->
                        if (wDigit != digit) {
                            Text(
                                text = wDigit,
                                color = Color(0xFF332014).copy(alpha = 0.16f), // weathered copper filament wire
                                fontSize = (102 * scale).sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Serif, // Serif has organic curves resembling manual wire frames
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // 5. Active Glowing Multi-Layer Neon Filament
                    if (!isDimmed) {
                        // LAYER A: Massive ambient gas corona (Outmost blurry glow)
                        Text(
                            text = digit,
                            color = ambientHaloColor.copy(alpha = 0.75f),
                            fontSize = (102 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = ambientHaloColor,
                                    offset = Offset(0f, 0f),
                                    blurRadius = (28 * scale)
                                )
                            )
                        )

                        // LAYER B: Saturated Orange Plasma Discharge (Middle thick glow)
                        Text(
                            text = digit,
                            color = activeGlowColor,
                            fontSize = (102 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = activeGlowColor,
                                    offset = Offset(0f, 0f),
                                    blurRadius = (12 * scale)
                                )
                            )
                        )

                        // LAYER C: Hot Wire Core (Central intense highlight representing the wire metal)
                        Text(
                            text = digit,
                            color = activeCoreColor,
                            fontSize = (101 * scale).sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = activeGlowColor.copy(alpha = 0.9f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = (3 * scale)
                                )
                            )
                        )
                    } else {
                        // Dimmed state for non-active digits
                        Text(
                            text = digit,
                            color = activeGlowColor.copy(alpha = 0.08f),
                            fontSize = (102 * scale).sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 6. Glass Highlights & Specular Reflections (In front of filaments)
                // Left curved highlight (Main glint)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .width((6 * scale).dp)
                        .padding(top = (22 * scale).dp, bottom = (12 * scale).dp, start = (6 * scale).dp)
                        .clip(RoundedCornerShape(30))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.02f)
                                )
                            )
                        )
                )

                // Right warm ambient edge highlight
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxHeight()
                        .width((4 * scale).dp)
                        .padding(top = (22 * scale).dp, bottom = (12 * scale).dp, end = (6 * scale).dp)
                        .clip(RoundedCornerShape(30))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.01f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            )
                        )
                )

                // Top dome light reflection flare
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (8 * scale).dp)
                        .size(width = (24 * scale).dp, height = (4 * scale).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }

            // Dark Ceramic/Metal mounting collar at the bottom of the tube
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height((12 * scale).dp)
                    .clip(RoundedCornerShape(topStart = (4 * scale).dp, topEnd = (4 * scale).dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2C2C2E), // Dark basalt black
                                Color(0xFF1C1C1E)
                            )
                        )
                    )
                    .border(
                        width = (1 * scale).dp,
                        color = Color(0xFF4A4A4F),
                        shape = RoundedCornerShape(topStart = (4 * scale).dp, topEnd = (4 * scale).dp)
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                // Gold/metal copper wire spacer seal ring
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((2 * scale).dp)
                        .background(Color(0xFF8B7355).copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
fun NixieColon(color: Color, scale: Float, isTicking: Boolean) {
    val activeGlowColor = Color(0xFFFF5200)
    val activeCoreColor = Color(0xFFFFE599)
    val ambientHaloColor = Color(0xFFFF3300)

    val alphaAnim = if (isTicking) {
        val infiniteTransition = rememberInfiniteTransition(label = "nixieColonAnim")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "colonAlpha"
        )
        alpha
    } else {
        0.85f
    }
    
    // Matched Glass Separator Tube
    Box(
        modifier = Modifier
            .width((32 * scale).dp)
            .height((176 * scale).dp)
            .padding(horizontal = (2 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Glass envelope
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = (16 * scale).dp,
                            topEnd = (16 * scale).dp,
                            bottomStart = (4 * scale).dp,
                            bottomEnd = (4 * scale).dp
                        )
                    )
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F0B09),
                                Color(0xFF160E0A)
                            )
                        )
                    )
                    .border(
                        width = (1.5f * scale).dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(
                            topStart = (16 * scale).dp,
                            topEnd = (16 * scale).dp,
                            bottomStart = (4 * scale).dp,
                            bottomEnd = (4 * scale).dp
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background mesh grid inside the colon tube
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val step = (12f * scale).dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }
                }

                // Two glowing neon beads representing the dots
                Column(
                    verticalArrangement = Arrangement.spacedBy((24 * scale).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Dot
                    Box(contentAlignment = Alignment.Center) {
                        // Outer halo
                        Box(
                            modifier = Modifier
                                .size((12 * scale).dp)
                                .clip(CircleShape)
                                .background(ambientHaloColor.copy(alpha = alphaAnim * 0.4f))
                        )
                        // Neon discharge layer
                        Box(
                            modifier = Modifier
                                .size((8 * scale).dp)
                                .clip(CircleShape)
                                .background(activeGlowColor.copy(alpha = alphaAnim))
                        )
                        // Core wire layer
                        Box(
                            modifier = Modifier
                                .size((4 * scale).dp)
                                .clip(CircleShape)
                                .background(activeCoreColor.copy(alpha = alphaAnim))
                        )
                    }

                    // Bottom Dot
                    Box(contentAlignment = Alignment.Center) {
                        // Outer halo
                        Box(
                            modifier = Modifier
                                .size((12 * scale).dp)
                                .clip(CircleShape)
                                .background(ambientHaloColor.copy(alpha = alphaAnim * 0.4f))
                        )
                        // Neon discharge layer
                        Box(
                            modifier = Modifier
                                .size((8 * scale).dp)
                                .clip(CircleShape)
                                .background(activeGlowColor.copy(alpha = alphaAnim))
                        )
                        // Core wire layer
                        Box(
                            modifier = Modifier
                                .size((4 * scale).dp)
                                .clip(CircleShape)
                                .background(activeCoreColor.copy(alpha = alphaAnim))
                        )
                    }
                }

                // Glass shine reflections
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .width((3 * scale).dp)
                        .padding(top = (16 * scale).dp, bottom = (8 * scale).dp, start = (4 * scale).dp)
                        .clip(RoundedCornerShape(30))
                        .background(Color.White.copy(alpha = 0.18f))
                )
            }

            // Mounting base collar for colon tube
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height((12 * scale).dp)
                    .clip(RoundedCornerShape(topStart = (3 * scale).dp, topEnd = (3 * scale).dp))
                    .background(Color(0xFF2C2C2E))
                    .border(
                        width = (1 * scale).dp,
                        color = Color(0xFF4A4A4F),
                        shape = RoundedCornerShape(topStart = (3 * scale).dp, topEnd = (3 * scale).dp)
                    )
            )
        }
    }
}
