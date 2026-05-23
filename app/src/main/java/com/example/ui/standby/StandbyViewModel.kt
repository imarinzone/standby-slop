package com.example.ui.standby

import android.app.Application
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Alarm
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = application.getSharedPreferences("standby_prefs", Context.MODE_PRIVATE)
    
    val alarms: StateFlow<List<Alarm>> = db.alarmDao().getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _nextSystemAlarm = MutableStateFlow<String?>("No Alarm")
    val nextSystemAlarm: StateFlow<String?> = _nextSystemAlarm

    val selectedColorIndex = MutableStateFlow(prefs.getInt("selectedColorIndex", 0))
    val selectedFontIndex = MutableStateFlow(prefs.getInt("selectedFontIndex", 0))
    val showWeather = MutableStateFlow(prefs.getBoolean("showWeather", false))
    val clockScale = MutableStateFlow(prefs.getFloat("clockScale", 1.2f))

    // Per-page customized settings (Page 0: Clock, 1: Alarm, 2: Calendar, 3: Music, 4: Timer)
    val colorPage0 = MutableStateFlow(prefs.getInt("colorPage0", 0))
    val colorPage1 = MutableStateFlow(prefs.getInt("colorPage1", 2))
    val colorPage2 = MutableStateFlow(prefs.getInt("colorPage2", 3))
    val colorPage3 = MutableStateFlow(prefs.getInt("colorPage3", 1))
    val colorPage4 = MutableStateFlow(prefs.getInt("colorPage4", 2))

    val fontPage0 = MutableStateFlow(prefs.getInt("fontPage0", 0))
    val fontPage1 = MutableStateFlow(prefs.getInt("fontPage1", 1))
    val fontPage2 = MutableStateFlow(prefs.getInt("fontPage2", 2))
    val fontPage3 = MutableStateFlow(prefs.getInt("fontPage3", 0))
    val fontPage4 = MutableStateFlow(prefs.getInt("fontPage4", 0))

    val showWeatherPage0 = MutableStateFlow(prefs.getBoolean("showWeatherPage0", false))
    val showWeatherPage1 = MutableStateFlow(prefs.getBoolean("showWeatherPage1", false))
    val showWeatherPage2 = MutableStateFlow(prefs.getBoolean("showWeatherPage2", false))
    val showWeatherPage3 = MutableStateFlow(prefs.getBoolean("showWeatherPage3", false))
    val showWeatherPage4 = MutableStateFlow(prefs.getBoolean("showWeatherPage4", false))

    val scalePage0 = MutableStateFlow(prefs.getFloat("scalePage0", 1.2f))
    val scalePage1 = MutableStateFlow(prefs.getFloat("scalePage1", 1.0f))
    val scalePage2 = MutableStateFlow(prefs.getFloat("scalePage2", 1.0f))
    val scalePage3 = MutableStateFlow(prefs.getFloat("scalePage3", 1.0f))
    val scalePage4 = MutableStateFlow(prefs.getFloat("scalePage4", 1.0f))

    // Setting to remove animation for distraction-free theme (per screen)
    val animPage0 = MutableStateFlow(prefs.getBoolean("animPage0", true))
    val animPage1 = MutableStateFlow(prefs.getBoolean("animPage1", true))
    val animPage2 = MutableStateFlow(prefs.getBoolean("animPage2", true))
    val animPage3 = MutableStateFlow(prefs.getBoolean("animPage3", true))
    val animPage4 = MutableStateFlow(prefs.getBoolean("animPage4", true))

    // Custom visual overrides
    val textGlowPage0 = MutableStateFlow(prefs.getBoolean("textGlowPage0", true))
    val textGlowPage1 = MutableStateFlow(prefs.getBoolean("textGlowPage1", true))
    val textGlowPage2 = MutableStateFlow(prefs.getBoolean("textGlowPage2", true))
    val textGlowPage3 = MutableStateFlow(prefs.getBoolean("textGlowPage3", true))
    val textGlowPage4 = MutableStateFlow(prefs.getBoolean("textGlowPage4", true))

    val textOutlineOnlyPage0 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage0", false))
    val textOutlineOnlyPage1 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage1", false))
    val textOutlineOnlyPage2 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage2", false))
    val textOutlineOnlyPage3 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage3", false))
    val textOutlineOnlyPage4 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage4", false))

    val textGradientPage0 = MutableStateFlow(prefs.getInt("textGradientPage0", -1))
    val textGradientPage1 = MutableStateFlow(prefs.getInt("textGradientPage1", -1))
    val textGradientPage2 = MutableStateFlow(prefs.getInt("textGradientPage2", -1))
    val textGradientPage3 = MutableStateFlow(prefs.getInt("textGradientPage3", -1))
    val textGradientPage4 = MutableStateFlow(prefs.getInt("textGradientPage4", -1))

    val bgUriPage0 = MutableStateFlow<String?>(prefs.getString("bgUriPage0", null))
    val bgUriPage1 = MutableStateFlow<String?>(prefs.getString("bgUriPage1", null))
    val bgUriPage2 = MutableStateFlow<String?>(prefs.getString("bgUriPage2", null))
    val bgUriPage3 = MutableStateFlow<String?>(prefs.getString("bgUriPage3", null))
    val bgUriPage4 = MutableStateFlow<String?>(prefs.getString("bgUriPage4", null))
    val use24HourFormat = MutableStateFlow(prefs.getBoolean("use24HourFormat", false))
    val showAmPm = MutableStateFlow(prefs.getBoolean("showAmPm", false))
    val showSeconds = MutableStateFlow(prefs.getBoolean("showSeconds", false))

    init {
        // Automatically re-calculate our custom next alarm whenever the list or time format preferences change
        viewModelScope.launch {
            combine(alarms, use24HourFormat) { _, _ -> }.collect {
                updateNextAlarm()
            }
        }
    }

    fun updateNextAlarm() {
        try {
            val currentAlarms = alarms.value
            val is24Hr = use24HourFormat.value
            val enabledAlarms = currentAlarms.filter { it.isEnabled }
            if (enabledAlarms.isEmpty()) {
                _nextSystemAlarm.value = "No Alarm"
                return
            }
            
            val now = Calendar.getInstance()
            var soonestAlarm: Alarm? = null
            var soonestDiff = Long.MAX_VALUE
            
            for (alarm in enabledAlarms) {
                val alarmCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, alarm.hour)
                    set(Calendar.MINUTE, alarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (alarmCal.before(now)) {
                    alarmCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                val diff = alarmCal.timeInMillis - now.timeInMillis
                if (diff < soonestDiff) {
                    soonestDiff = diff
                    soonestAlarm = alarm
                }
            }
            
            if (soonestAlarm != null) {
                val displayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, soonestAlarm.hour)
                    set(Calendar.MINUTE, soonestAlarm.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (displayCal.before(now)) {
                    displayCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                val formatter = SimpleDateFormat(if (is24Hr) "HH:mm" else "h:mm a", Locale.getDefault())
                val prefix = if (displayCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) "Today " else "Tom. "
                _nextSystemAlarm.value = "$prefix${formatter.format(displayCal.time)} (${soonestAlarm.label})"
            } else {
                _nextSystemAlarm.value = "No Alarm"
            }
        } catch (e: Exception) {
            _nextSystemAlarm.value = "No Alarm"
            android.util.Log.e("StandbyViewModel", "Failed to update local next alarm status", e)
        }
    }

    fun setUse24HourFormat(use24Hour: Boolean) {
        use24HourFormat.value = use24Hour
        prefs.edit().putBoolean("use24HourFormat", use24Hour).apply()
    }

    fun setShowAmPm(show: Boolean) {
        showAmPm.value = show
        prefs.edit().putBoolean("showAmPm", show).apply()
    }

    fun setShowSeconds(show: Boolean) {
        showSeconds.value = show
        prefs.edit().putBoolean("showSeconds", show).apply()
    }

    fun getColorFlow(page: Int): MutableStateFlow<Int> {
        return when (page) {
            0 -> colorPage0
            1 -> colorPage1
            2 -> colorPage2
            3 -> colorPage3
            4 -> colorPage4
            else -> colorPage0
        }
    }

    fun getFontFlow(page: Int): MutableStateFlow<Int> {
        return when (page) {
            0 -> fontPage0
            1 -> fontPage1
            2 -> fontPage2
            3 -> fontPage3
            4 -> fontPage4
            else -> fontPage0
        }
    }

    fun getWeatherFlow(page: Int): MutableStateFlow<Boolean> {
        return when (page) {
            0 -> showWeatherPage0
            1 -> showWeatherPage1
            2 -> showWeatherPage2
            3 -> showWeatherPage3
            4 -> showWeatherPage4
            else -> showWeatherPage0
        }
    }

    fun getScaleFlow(page: Int): MutableStateFlow<Float> {
        return when (page) {
            0 -> scalePage0
            1 -> scalePage1
            2 -> scalePage2
            3 -> scalePage3
            4 -> scalePage4
            else -> scalePage0
        }
    }

    fun getAnimFlow(page: Int): MutableStateFlow<Boolean> {
        return when (page) {
            0 -> animPage0
            1 -> animPage1
            2 -> animPage2
            3 -> animPage3
            4 -> animPage4
            else -> animPage0
        }
    }

    fun getGlowFlow(page: Int): MutableStateFlow<Boolean> {
        return when (page) {
            0 -> textGlowPage0
            1 -> textGlowPage1
            2 -> textGlowPage2
            3 -> textGlowPage3
            4 -> textGlowPage4
            else -> textGlowPage0
        }
    }

    fun getOutlineFlow(page: Int): MutableStateFlow<Boolean> {
        return when (page) {
            0 -> textOutlineOnlyPage0
            1 -> textOutlineOnlyPage1
            2 -> textOutlineOnlyPage2
            3 -> textOutlineOnlyPage3
            4 -> textOutlineOnlyPage4
            else -> textOutlineOnlyPage0
        }
    }

    fun getGradientFlow(page: Int): MutableStateFlow<Int> {
        return when (page) {
            0 -> textGradientPage0
            1 -> textGradientPage1
            2 -> textGradientPage2
            3 -> textGradientPage3
            4 -> textGradientPage4
            else -> textGradientPage0
        }
    }

    fun getBgUriFlow(page: Int): MutableStateFlow<String?> {
        return when (page) {
            0 -> bgUriPage0
            1 -> bgUriPage1
            2 -> bgUriPage2
            3 -> bgUriPage3
            4 -> bgUriPage4
            else -> bgUriPage0
        }
    }

    fun setColorIndexForPage(page: Int, index: Int) {
        getColorFlow(page).value = index
        prefs.edit().putInt("colorPage$page", index).apply()
        if (page == 0) {
            selectedColorIndex.value = index
            prefs.edit().putInt("selectedColorIndex", index).apply()
        }
    }

    fun setFontIndexForPage(page: Int, index: Int) {
        getFontFlow(page).value = index
        prefs.edit().putInt("fontPage$page", index).apply()
        if (page == 0) {
            selectedFontIndex.value = index
            prefs.edit().putInt("selectedFontIndex", index).apply()
        }
    }

    fun setShowWeatherForPage(page: Int, show: Boolean) {
        getWeatherFlow(page).value = show
        prefs.edit().putBoolean("showWeatherPage$page", show).apply()
        if (page == 0) {
            showWeather.value = show
            prefs.edit().putBoolean("showWeather", show).apply()
        }
    }

    fun setClockScaleForPage(page: Int, scale: Float) {
        getScaleFlow(page).value = scale
        prefs.edit().putFloat("scalePage$page", scale).apply()
        if (page == 0) {
            clockScale.value = scale
            prefs.edit().putFloat("clockScale", scale).apply()
        }
    }

    fun setAnimationsEnabledForPage(page: Int, enabled: Boolean) {
        getAnimFlow(page).value = enabled
        prefs.edit().putBoolean("animPage$page", enabled).apply()
    }

    fun setTextGlowForPage(page: Int, enabled: Boolean) {
        getGlowFlow(page).value = enabled
        prefs.edit().putBoolean("textGlowPage$page", enabled).apply()
    }

    fun setTextOutlineForPage(page: Int, outline: Boolean) {
        getOutlineFlow(page).value = outline
        prefs.edit().putBoolean("textOutlineOnlyPage$page", outline).apply()
    }

    fun setTextGradientForPage(page: Int, index: Int) {
        getGradientFlow(page).value = index
        prefs.edit().putInt("textGradientPage$page", index).apply()
    }

    fun setBgUriForPage(page: Int, uri: String?) {
        getBgUriFlow(page).value = uri
        prefs.edit().putString("bgUriPage$page", uri).apply()
    }

    val gradients = listOf(
        "None" to listOf(Color.Transparent, Color.Transparent),
        "Cyberpunk" to listOf(Color(0xFFFF007F), Color(0xFF00F0FF)),
        "Sunset" to listOf(Color(0xFFFF5E3A), Color(0xFFFF2A6D)),
        "Ocean" to listOf(Color(0xFF2E3192), Color(0xFF1BFFFF)),
        "Forest" to listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        "Gold" to listOf(Color(0xFFF9D423), Color(0xFFFF4E50))
    )

    val colors = listOf(
        Color.White to "Crystal White",
        Color(0xFF38BDF8) to "Sky Blue",
        Color(0xFFF43F5E) to "Rose Accent",
        Color(0xFF10B981) to "Emerald",
        Color(0xFFFBBF24) to "Amber Gold",
        Color(0xFFEC4899) to "Neon Pink",
        Color(0xFFA855F7) to "Purple",
        Color(0xFF06B6D4) to "Electric Cyan",
        Color(0xFFFF5722) to "Sunset Orange",
        Color(0xFF8BC34A) to "Lime Green"
    )

    val fonts = listOf(
        FontFamily.SansSerif to "Modern Sans",
        FontFamily.Serif to "Classic Serif",
        FontFamily.Monospace to "Retro Code",
        FontFamily.Cursive to "Script",
        FontFamily.Default to "Default"
    )

    fun setColorIndex(index: Int) {
        selectedColorIndex.value = index
        colorPage0.value = index
        prefs.edit().putInt("selectedColorIndex", index).putInt("colorPage0", index).apply()
    }

    fun setFontIndex(index: Int) {
        selectedFontIndex.value = index
        fontPage0.value = index
        prefs.edit().putInt("selectedFontIndex", index).putInt("fontPage0", index).apply()
    }

    fun setShowWeather(show: Boolean) {
        showWeather.value = show
        showWeatherPage0.value = show
        prefs.edit().putBoolean("showWeather", show).putBoolean("showWeatherPage0", show).apply()
    }

    fun setClockScale(scale: Float) {
        clockScale.value = scale
        scalePage0.value = scale
        prefs.edit().putFloat("clockScale", scale).putFloat("scalePage0", scale).apply()
    }
        
    fun addAlarm(hour: Int, minute: Int, label: String, snooze: Int, toneUri: String = "alarm_beep") {
        viewModelScope.launch {
            db.alarmDao().insert(
                Alarm(
                    hour = hour,
                    minute = minute,
                    label = label,
                    snoozeMinutes = snooze,
                    toneUri = toneUri
                )
            )
        }
    }
    
    fun updateAlarm(alarm: Alarm) {
        viewModelScope.launch {
            db.alarmDao().update(alarm)
        }
    }
    
    fun toggleAlarm(alarm: Alarm) {
        viewModelScope.launch {
            db.alarmDao().update(alarm.copy(isEnabled = !alarm.isEnabled))
        }
    }
    
    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            db.alarmDao().delete(alarm)
        }
    }
}
