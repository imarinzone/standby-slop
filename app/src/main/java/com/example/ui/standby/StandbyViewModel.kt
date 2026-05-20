package com.example.ui.standby

import android.app.Application
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    val alarms: StateFlow<List<Alarm>> = db.alarmDao().getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _nextSystemAlarm = MutableStateFlow<String?>("No Alarm")
    val nextSystemAlarm: StateFlow<String?> = _nextSystemAlarm

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("StandbyViewModel", "onReceive: System alarm status changed!")
            updateNextAlarm()
        }
    }

    init {
        updateNextAlarm()
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(alarmReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                application.registerReceiver(alarmReceiver, filter)
            }
        } catch (e: Exception) {
            try {
                application.registerReceiver(alarmReceiver, filter)
            } catch (ex: Exception) {
                android.util.Log.e("StandbyViewModel", "Failed to register alarm changer receiver", ex)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(alarmReceiver)
        } catch (e: Exception) {
            android.util.Log.e("StandbyViewModel", "Failed to unregister alarm receiver on cleared", e)
        }
    }

    fun updateNextAlarm() {
        val app = getApplication<Application>()
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock
        if (nextAlarm != null) {
            val triggerTime = nextAlarm.triggerTime
            val calendar = Calendar.getInstance().apply { timeInMillis = triggerTime }
            val is24Hour = DateFormat.is24HourFormat(app)
            val pattern = if (is24Hour) "EEE HH:mm" else "EEE h:mm a"
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            _nextSystemAlarm.value = sdf.format(calendar.time)
        } else {
            _nextSystemAlarm.value = "No Alarm"
        }
        android.util.Log.d("StandbyViewModel", "Updated next system alarm status: ${_nextSystemAlarm.value}")
    }

    val selectedColorIndex = MutableStateFlow(0)
    val selectedFontIndex = MutableStateFlow(0)
    val showWeather = MutableStateFlow(true)
    val clockScale = MutableStateFlow(1.2f)

    // Per-page customized settings (Page 0: Clock, 1: Alarm, 2: Calendar, 3: Music, 4: Timer)
    val colorPage0 = MutableStateFlow(0)
    val colorPage1 = MutableStateFlow(2)
    val colorPage2 = MutableStateFlow(3)
    val colorPage3 = MutableStateFlow(1)
    val colorPage4 = MutableStateFlow(2)

    val fontPage0 = MutableStateFlow(0)
    val fontPage1 = MutableStateFlow(1)
    val fontPage2 = MutableStateFlow(2)
    val fontPage3 = MutableStateFlow(0)
    val fontPage4 = MutableStateFlow(0)

    val showWeatherPage0 = MutableStateFlow(true)
    val showWeatherPage1 = MutableStateFlow(true)
    val showWeatherPage2 = MutableStateFlow(false)
    val showWeatherPage3 = MutableStateFlow(false)
    val showWeatherPage4 = MutableStateFlow(false)

    val scalePage0 = MutableStateFlow(1.2f)
    val scalePage1 = MutableStateFlow(1.0f)
    val scalePage2 = MutableStateFlow(1.0f)
    val scalePage3 = MutableStateFlow(1.0f)
    val scalePage4 = MutableStateFlow(1.0f)

    // Setting to remove animation for distraction-free theme (per screen)
    val animPage0 = MutableStateFlow(true)
    val animPage1 = MutableStateFlow(true)
    val animPage2 = MutableStateFlow(true)
    val animPage3 = MutableStateFlow(true)
    val animPage4 = MutableStateFlow(true)

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

    fun setColorIndexForPage(page: Int, index: Int) {
        getColorFlow(page).value = index
        if (page == 0) {
            selectedColorIndex.value = index
        }
    }

    fun setFontIndexForPage(page: Int, index: Int) {
        getFontFlow(page).value = index
        if (page == 0) {
            selectedFontIndex.value = index
        }
    }

    fun setShowWeatherForPage(page: Int, show: Boolean) {
        getWeatherFlow(page).value = show
        if (page == 0) {
            showWeather.value = show
        }
    }

    fun setClockScaleForPage(page: Int, scale: Float) {
        getScaleFlow(page).value = scale
        if (page == 0) {
            clockScale.value = scale
        }
    }

    fun setAnimationsEnabledForPage(page: Int, enabled: Boolean) {
        getAnimFlow(page).value = enabled
    }

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
    }

    fun setFontIndex(index: Int) {
        selectedFontIndex.value = index
        fontPage0.value = index
    }

    fun setShowWeather(show: Boolean) {
        showWeather.value = show
        showWeatherPage0.value = show
    }

    fun setClockScale(scale: Float) {
        clockScale.value = scale
        scalePage0.value = scale
    }
        
    fun addAlarm(hour: Int, minute: Int, label: String, snooze: Int) {
        viewModelScope.launch {
            db.alarmDao().insert(
                Alarm(
                    hour = hour,
                    minute = minute,
                    label = label,
                    snoozeMinutes = snooze
                )
            )
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
