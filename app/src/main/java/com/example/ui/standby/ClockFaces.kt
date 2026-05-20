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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.animation.*
import androidx.compose.animation.core.*

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

    val customColor = standbyViewModel.colors[selectedColorIdx].first
    val customFont = standbyViewModel.fonts[selectedFontIdx].first

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = Calendar.getInstance()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (themeIndex % 12) {
                0 -> DigitalMinimal(currentTime, customColor, customFont, clockScaleValue, animsEnabled, use24Hour, showAmPm, showSeconds)
                1 -> AnalogClassic(currentTime, customColor, clockScaleValue, animsEnabled, showSeconds)
                2 -> DigitalNeon(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                3 -> RetroFlip(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                4 -> BinaryStyle(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                5 -> ModernBoldTextClock(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                6 -> LargeSidebarClock(currentTime, customColor, customFont, nextSystemAlarm, clockScaleValue, use24Hour, showAmPm, showSeconds)
                7 -> ContrastingSplitClock(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                8 -> AnalogDashboard(currentTime, customColor, customFont, clockScaleValue, animsEnabled, showSeconds)
                9 -> BubblePastelClock(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                10 -> AmbientGradientClock(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
                11 -> NixieTubeClock(currentTime, customColor, customFont, clockScaleValue, use24Hour, showAmPm, showSeconds)
            }
        }
        
        // Weather Overlay
        if (showWeatherValue) {
            weather?.let { w ->
                Text(
                    text = "${w.temperature_2m}°C",
                    color = customColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = customFont,
                    modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
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
    showSeconds: Boolean = true
) {
    val format = remember(use24Hour, showAmPm, showSeconds) {
        val pattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
        SimpleDateFormat(pattern, Locale.getDefault())
    }
    Text(
        text = format.format(calendar.time),
        color = color,
        fontSize = (110 * scale).sp,
        fontWeight = FontWeight.Thin,
        fontFamily = fontFamily
    )
}

@Composable
fun AnalogClassic(calendar: Calendar, color: Color, scale: Float = 1.0f, animationsEnabled: Boolean = true, showSeconds: Boolean = true) {
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = if (animationsEnabled && showSeconds) calendar.get(Calendar.SECOND) else 0

    Canvas(modifier = Modifier.size((300 * scale).dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = size.width / 2 - 20.dp.toPx()

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

@Composable
fun RetroFlip(
    calendar: Calendar, 
    color: Color, 
    fontFamily: FontFamily, 
    scale: Float = 1.0f,
    use24Hour: Boolean = false,
    showAmPm: Boolean = true,
    showSeconds: Boolean = true
) {
    val hourText = SimpleDateFormat(if (use24Hour) "HH" else "hh", Locale.getDefault()).format(calendar.time)
    val minuteText = SimpleDateFormat("mm", Locale.getDefault()).format(calendar.time)
    val secondText = SimpleDateFormat("ss", Locale.getDefault()).format(calendar.time)
    val amPmText = SimpleDateFormat("a", Locale.getDefault()).format(calendar.time)

    val boxWidth = (if (showSeconds) 120 * scale else 165 * scale).dp
    val boxHeight = (if (showSeconds) 140 * scale else 185 * scale).dp
    val fontSize = (if (showSeconds) 80 * scale else 110 * scale).sp
    val cornerRadius = (if (showSeconds) 12 * scale else 16 * scale).dp
    val spacing = (if (showSeconds) 12 * scale else 20 * scale).dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Hour flap section
        FlipFlapCard(
            text = hourText,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            cornerRadius = cornerRadius
        )

        // Minute flap section
        FlipFlapCard(
            text = minuteText,
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
            cornerRadius = cornerRadius
        )

        // Seconds flap section
        if (showSeconds) {
            FlipFlapCard(
                text = secondText,
                color = color.copy(alpha = 0.8f),
                fontSize = fontSize * 0.85f,
                fontFamily = fontFamily,
                boxWidth = boxWidth * 0.85f,
                boxHeight = boxHeight * 0.85f,
                cornerRadius = cornerRadius * 0.85f
            )
        }

        if (!use24Hour && showAmPm) {
            Text(
                text = amPmText,
                color = color.copy(alpha = 0.6f),
                fontSize = (24 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
fun FlipFlapCard(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily,
    boxWidth: androidx.compose.ui.unit.Dp,
    boxHeight: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(width = boxWidth, height = boxHeight)
            .background(Color(0xFF121214), shape = RoundedCornerShape(cornerRadius))
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = Color.Black,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 4f
            )
        }
        
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInVertically(
                    initialOffsetY = { height -> -height },
                    animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(250))
                ).togetherWith(
                    slideOutVertically(
                        targetOffsetY = { height -> height },
                        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(250))
                ).using(SizeTransform(clip = true))
            },
            label = "FlipTransition"
        ) { targetText ->
            Text(
                text = targetText,
                color = color,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
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
    Box(
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
        
        val formatPattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
        val format = SimpleDateFormat(formatPattern, Locale.getDefault())
        val text = format.format(calendar.time).map { char ->
            when {
                char == ':' -> ":"
                char == ' ' -> " "
                char.isDigit() -> Integer.toBinaryString(char.toString().toInt()).padStart(4, '0')
                else -> char.toString()
            }
        }.joinToString("  ") // Fully horizontal representation with generous space gaps
        
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = (52 * scale).sp, // Increased font size for superior landscape visibility
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color(0xFF39FF14).copy(alpha = 0.85f), // Vibrant Neon Green shadow glow
                    offset = Offset(0f, 0f),
                    blurRadius = 24f
                )
            ),
            modifier = Modifier.padding(24.dp) // Completely removed the rectangular boxing/border background!
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
    val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
    val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
    val periodName = when (hourOfDay) {
        in 5..11 -> "Morning"
        in 12..16 -> "Afternoon"
        in 17..20 -> "Evening"
        else -> "Night"
    }
    val timePattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "")
    val timeStr = SimpleDateFormat(timePattern, Locale.getDefault()).format(calendar.time)
    val amPmStr = SimpleDateFormat("a", Locale.getDefault()).format(calendar.time)
    val dateStr = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(calendar.time)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding((24 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayOfWeek,
            color = Color.White,
            fontSize = (38 * scale).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center
        )
        Text(
            text = periodName,
            color = Color.Gray,
            fontSize = (24 * scale).sp,
            fontWeight = FontWeight.Normal,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = (12 * scale).dp)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(vertical = (4 * scale).dp)
        ) {
            Text(
                text = timeStr,
                color = color,
                fontSize = (90 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                lineHeight = (90 * scale).sp
            )
            if (!use24Hour && showAmPm) {
                Text(
                    text = amPmStr,
                    color = Color.DarkGray,
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(bottom = (12 * scale).dp, start = (6 * scale).dp)
                )
            }
        }
        Text(
            text = dateStr,
            color = Color.LightGray,
            fontSize = (20 * scale).sp,
            fontWeight = FontWeight.Normal,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = (8 * scale).dp)
        )
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
    val timePattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "")
    val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
    val amPmFormat = SimpleDateFormat(" a", Locale.getDefault())
    val dayOfWeekAbbr = SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time).uppercase()
    val dayOfMonth = SimpleDateFormat("d", Locale.getDefault()).format(calendar.time)
    
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

class DiagonalSplitShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): Outline {
        val path = Path().apply {
            moveTo(size.width * 0.52f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width * 0.72f, size.height)
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
    val pattern = (if (use24Hour) "HH" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    val timeText = format.format(calendar.time)
    val actualFontSize = if (showSeconds || (!use24Hour && showAmPm)) 110 * scale else 150 * scale

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = timeText,
            color = color,
            fontSize = actualFontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = fontFamily,
            letterSpacing = (-4 * scale).sp
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(DiagonalSplitShape())
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                color = Color.Black,
                fontSize = actualFontSize.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = fontFamily,
                letterSpacing = (-4 * scale).sp
            )
        }
    }
}

@Composable
fun AnalogDashboard(calendar: Calendar, color: Color, fontFamily: FontFamily, scale: Float = 1.0f, animationsEnabled: Boolean = true, showSeconds: Boolean = true) {
    val hour = calendar.get(Calendar.HOUR)
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val dayOfWeek = SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time).uppercase()
    val dayOfMonth = SimpleDateFormat("d", Locale.getDefault()).format(calendar.time)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2

            val pad = 16.dp.toPx() * scale
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
                    val tickLength = if (isMajor) 24.dp.toPx() * scale else 12.dp.toPx() * scale
                    val strokeWidth = if (isMajor) 3.dp.toPx() * scale else 1.dp.toPx() * scale
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
            val hourHandLength = size.height * 0.25f * scale
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(
                    cx + hourHandLength * cos(hourAngle).toFloat(),
                    cy + hourHandLength * sin(hourAngle).toFloat()
                ),
                strokeWidth = 12f * scale,
                cap = StrokeCap.Round
            )
            val hCos = cos(hourAngle).toFloat()
            val hSin = sin(hourAngle).toFloat()
            drawLine(
                color = Color.Black,
                start = Offset(cx + 8f * hCos * scale, cy + 8f * hSin * scale),
                end = Offset(
                    cx + (hourHandLength - 4f * scale) * hCos,
                    cy + (hourHandLength - 4f * scale) * hSin
                ),
                strokeWidth = 2.5f * scale,
                cap = StrokeCap.Round
            )

            // Minute Hand
            val minAngle = Math.toRadians((minute * 6).toDouble() - 90)
            val minHandLength = size.height * 0.38f * scale
            drawLine(
                color = Color.White,
                start = Offset(cx, cy),
                end = Offset(
                    cx + minHandLength * cos(minAngle).toFloat(),
                    cy + minHandLength * sin(minAngle).toFloat()
                ),
                strokeWidth = 10f * scale,
                cap = StrokeCap.Round
            )
            val mCos = cos(minAngle).toFloat()
            val mSin = sin(minAngle).toFloat()
            drawLine(
                color = Color.Black,
                start = Offset(cx + 8f * mCos * scale, cy + 8f * mSin * scale),
                end = Offset(
                    cx + (minHandLength - 4f * scale) * mCos,
                    cy + (minHandLength - 4f * scale) * mSin
                ),
                strokeWidth = 2f * scale,
                cap = StrokeCap.Round
            )

            // Second Hand with Tail
            if (animationsEnabled && showSeconds) {
                val secAngle = Math.toRadians((second * 6).toDouble() - 90)
                val secMainLength = size.height * 0.44f * scale
                val secTailLength = 20.dp.toPx() * scale
                val sCos = cos(secAngle).toFloat()
                val sSin = sin(secAngle).toFloat()

                drawLine(
                    color = Color(0xFFFF3B30),
                    start = Offset(cx - secTailLength * sCos, cy - secTailLength * sSin),
                    end = Offset(cx + secMainLength * sCos, cy + secMainLength * sSin),
                    strokeWidth = 4f * scale,
                    cap = StrokeCap.Round
                )

                // Red pivot with white pin center
                drawCircle(color = Color(0xFFFF3B30), radius = 8.dp.toPx() * scale, center = Offset(cx, cy))
                drawCircle(color = Color.White, radius = 2.5.dp.toPx() * scale, center = Offset(cx, cy))
            } else {
                // Clean stationary pin center for distraction-free theme
                drawCircle(color = Color.White, radius = 4.dp.toPx() * scale, center = Offset(cx, cy))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "12",
                color = Color.White,
                fontSize = (42 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = (44 * scale).dp)
            )
            Text(
                text = "6",
                color = Color.White,
                fontSize = (42 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (44 * scale).dp)
            )
            Text(
                text = "9",
                color = Color.White,
                fontSize = (42 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = (72 * scale).dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = (64 * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayOfWeek,
                    color = Color(0xFFFF3B30),
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(end = (4 * scale).dp)
                )
                Text(
                    text = dayOfMonth,
                    color = Color.White,
                    fontSize = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    modifier = Modifier.padding(end = (16 * scale).dp)
                )
                Text(
                    text = "3",
                    color = Color.White,
                    fontSize = (42 * scale).sp,
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
    val hh = SimpleDateFormat(if (use24Hour) "HH" else "h", Locale.getDefault()).format(calendar.time)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(calendar.time)
    val ss = SimpleDateFormat("ss", Locale.getDefault()).format(calendar.time)
    val amPmText = SimpleDateFormat("a", Locale.getDefault()).format(calendar.time)
    
    val hourDigits = hh.map { it.toString() }
    val minuteDigits = mm.map { it.toString() }
    val secondDigits = ss.map { it.toString() }

    val actualScale = if (showSeconds) scale * 0.8f else scale

    Row(
        horizontalArrangement = Arrangement.spacedBy((4 * actualScale).dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                modifier = Modifier.padding(start = (6 * actualScale).dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
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
    val pattern = (if (use24Hour) "H" else "h") + ":mm" + (if (showSeconds) ":ss" else "") + (if (!use24Hour && showAmPm) " a" else "")
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    val gradientBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.25f),
            color.copy(alpha = 0.45f),
            Color.Black
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = format.format(calendar.time),
            color = color.copy(alpha = 0.85f),
            fontSize = (140 * scale).sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = fontFamily,
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
    val hh = SimpleDateFormat(if (use24Hour) "HH" else "h", Locale.getDefault()).format(calendar.time)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(calendar.time)
    val ss = SimpleDateFormat("ss", Locale.getDefault()).format(calendar.time)
    val amPmText = SimpleDateFormat("a", Locale.getDefault()).format(calendar.time)
    
    val hourDigits = hh.padStart(if (use24Hour) 2 else 1, ' ').map { it.toString() }
    val minuteDigits = mm.map { it.toString() }
    val secondDigits = ss.map { it.toString() }

    val actualScale = if (showSeconds) scale * 0.75f else scale

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        // Main container for the tubes
        Box(
            contentAlignment = Alignment.BottomCenter
        ) {
            // Tubes Row
            Row(
                horizontalArrangement = Arrangement.spacedBy((6 * actualScale).dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(bottom = (10 * actualScale).dp)
            ) {
                // Hour tubes
                hourDigits.forEach { digit ->
                    if (digit == " ") {
                        NixieDigitTube("0", color.copy(alpha = 0.05f), fontFamily, actualScale, isDimmed = true)
                    } else {
                        NixieDigitTube(digit, color, fontFamily, actualScale, isDimmed = false)
                    }
                }

                // Colon
                NixieColon(color, actualScale, isTicking = true)

                // Minute tubes
                minuteDigits.forEach { digit ->
                    NixieDigitTube(digit, color, fontFamily, actualScale, isDimmed = false)
                }

                // Colon & Seconds
                if (showSeconds) {
                    NixieColon(color, actualScale, isTicking = true)
                    secondDigits.forEach { digit ->
                        NixieDigitTube(digit, color, fontFamily, actualScale, isDimmed = false)
                    }
                }

                // AM/PM Tube
                if (!use24Hour && showAmPm) {
                    Box(
                        modifier = Modifier
                            .width((56 * actualScale).dp)
                            .height((110 * actualScale).dp)
                            .padding(horizontal = (2 * actualScale).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape((28 * actualScale).dp))
                                .background(Color.White.copy(alpha = 0.02f))
                                .border(
                                    width = (1 * actualScale).dp,
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape((28 * actualScale).dp)
                                )
                        )
                        Text(
                            text = amPmText,
                            color = color,
                            fontSize = (22 * actualScale).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = color.copy(alpha = 0.7f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = (10 * actualScale)
                                )
                            )
                        )
                    }
                }
            }
            
            // Wooden/Brass chassis/base block at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height((16 * actualScale).dp)
                    .clip(RoundedCornerShape((4 * actualScale).dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF6B4423), // Rich mahogany top highlight
                                Color(0xFF3B2312), // Deep wood brown body
                                Color(0xFF1B0F07)  // Shadowed bottom
                            )
                        )
                    )
            ) {
                // Subtle gold/brass metal strip in the center layer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((2 * actualScale).dp)
                        .align(Alignment.TopCenter)
                        .background(Color(0xFFD4AF37).copy(alpha = 0.35f)) // Brass highlight
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
    Box(
        modifier = Modifier
            .width((80 * scale).dp)
            .height((160 * scale).dp)
            .padding(horizontal = (4 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. Glass Tube Shadow & Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = (40 * scale).dp, topEnd = (40 * scale).dp, bottomStart = (4 * scale).dp, bottomEnd = (4 * scale).dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0x15FFFFFF),
                            Color(0x05FFFFFF),
                            Color(0x1AFFFFFF),
                            Color(0x02FFFFFF),
                            Color(0x22FFFFFF)
                        )
                    )
                )
                .border(
                    width = (1 * scale).dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(topStart = (40 * scale).dp, topEnd = (40 * scale).dp, bottomStart = (4 * scale).dp, bottomEnd = (4 * scale).dp)
                )
        ) {
            // Internal Grid/Mesh Anode
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 12 * scale
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.03f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.03f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
            }
        }

        // 2. Glowing Filament Stack (Dim stacked wires for 3D depth)
        val inactiveWires = listOf("8", "3", "0") // overlapping digits
        Box(contentAlignment = Alignment.Center) {
            if (!isDimmed) {
                inactiveWires.forEach { wDigit ->
                    if (wDigit != digit) {
                        Text(
                            text = wDigit,
                            color = color.copy(alpha = 0.035f),
                            fontSize = (100 * scale).sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // 3. Ambient Glow layer behind the active digit
            Box(
                modifier = Modifier
                    .size((60 * scale).dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                if (isDimmed) color.copy(alpha = 0.02f) else color.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 4. The Lit Active Filament (high intensity, bright glow)
            Text(
                text = digit,
                color = if (isDimmed) color.copy(alpha = 0.08f) else color.copy(alpha = 0.95f),
                fontSize = (100 * scale).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = if (isDimmed) Color.Transparent else color.copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = if (isDimmed) 0f else (16 * scale)
                    )
                )
            )
        }

        // 5. Glass Reflection highlight
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width((6 * scale).dp)
                .padding(top = (25 * scale).dp, bottom = (10 * scale).dp, start = (8 * scale).dp)
                .clip(RoundedCornerShape(30))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                )
        )
    }
}

@Composable
fun NixieColon(color: Color, scale: Float, isTicking: Boolean) {
    val alphaAnim = if (isTicking) {
        val infiniteTransition = rememberInfiniteTransition(label = "nixieColonAnim")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "colonAlpha"
        )
        alpha
    } else {
        0.8f
    }
    
    // Mini tube for the dual glowing separator dots
    Box(
        modifier = Modifier
            .width((28 * scale).dp)
            .height((120 * scale).dp)
            .padding(horizontal = (2 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape((14 * scale).dp))
                .background(Color.White.copy(alpha = 0.02f))
                .border(
                    width = (1 * scale).dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape((14 * scale).dp)
                )
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy((18 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size((8 * scale).dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alphaAnim))
                    .border(
                        width = (1 * scale).dp,
                        color = color.copy(alpha = alphaAnim),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size((8 * scale).dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alphaAnim))
                    .border(
                        width = (1 * scale).dp,
                        color = color.copy(alpha = alphaAnim),
                        shape = CircleShape
                    )
            )
        }
    }
}
