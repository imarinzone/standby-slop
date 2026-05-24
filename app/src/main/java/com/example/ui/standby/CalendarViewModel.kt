package com.example.ui.standby

import android.app.Application
import android.content.Context
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class LocalCalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val dtStart: Long,
    val dtEnd: Long,
    val eventLocation: String?,
    val allDay: Boolean,
    val category: String, // "Daily", "Weekly", or "Monthly"
    val calendarId: Long = 0L
)

data class CalendarSource(
    val id: Long,
    val name: String,
    val account: String
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("calendar_prefs", Context.MODE_PRIVATE)

    private val _deviceEvents = MutableStateFlow<List<LocalCalendarEvent>>(emptyList())
    val deviceEvents: StateFlow<List<LocalCalendarEvent>> = _deviceEvents
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val calendarSources = MutableStateFlow<List<CalendarSource>>(emptyList())
    val selectedCalendarIds = MutableStateFlow<Set<Long>>(emptySet())

    fun loadCalendarSources(context: Context) {
        viewModelScope.launch {
            try {
                val sources = withContext(Dispatchers.IO) {
                    val uri = CalendarContract.Calendars.CONTENT_URI
                    val projection = arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME
                    )
                    val list = mutableListOf<CalendarSource>()
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                        val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                        val accIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                        while (cursor.moveToNext()) {
                            val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Primary" else "Primary"
                            val account = if (accIdx >= 0) cursor.getString(accIdx) ?: "Local" else "Local"
                            list.add(CalendarSource(id, name, account))
                        }
                    }
                    list
                }
                calendarSources.value = sources
                if (selectedCalendarIds.value.isEmpty() && sources.isNotEmpty()) {
                    selectedCalendarIds.value = sources.map { it.id }.toSet()
                }
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Failed to query calendar sources", e)
            }
        }
    }

    fun toggleCalendarSource(id: Long) {
        val current = selectedCalendarIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        selectedCalendarIds.value = current
    }

    // Filtering preferences
    val calendarFilter = MutableStateFlow(prefs.getInt("calendarFilter", 0)) // 0 = Daily, 1 = Weekly, 2 = Monthly
    
    // Split View (Slide-Up) Theme Preferences
    val startWeekOnMonday = MutableStateFlow(prefs.getBoolean("startWeekOnMonday", false))
    val showEventDots = MutableStateFlow(prefs.getBoolean("showEventDots", true))
    val leftThemeStyle = MutableStateFlow(prefs.getInt("leftThemeStyle", 1)) // 0 = Cal Grid Only, 1 = Bold Date Only
    val use24HourFormat = MutableStateFlow(prefs.getBoolean("use24HourFormat", false))

    fun setCalendarFilter(filter: Int) { calendarFilter.value = filter; prefs.edit().putInt("calendarFilter", filter).apply() }
    fun setStartWeekOnMonday(start: Boolean) { startWeekOnMonday.value = start; prefs.edit().putBoolean("startWeekOnMonday", start).apply() }
    fun setShowEventDots(show: Boolean) { showEventDots.value = show; prefs.edit().putBoolean("showEventDots", show).apply() }
    fun setLeftThemeStyle(style: Int) { leftThemeStyle.value = style; prefs.edit().putInt("leftThemeStyle", style).apply() }
    fun setUse24HourFormat(use24Hour: Boolean) { use24HourFormat.value = use24Hour; prefs.edit().putBoolean("use24HourFormat", use24Hour).apply() }

    // Expose final events combined dynamically from settings filters and calendar selections
    val events: StateFlow<List<LocalCalendarEvent>> = combine(
        _deviceEvents,
        calendarFilter,
        selectedCalendarIds
    ) { deviceList, filterVal, activeIds ->
        // Direct non-mock filter logic
        deviceList.filter { event ->
            val isCalendarActive = activeIds.isEmpty() || activeIds.contains(event.calendarId)
            
            val matchesFilter = when (filterVal) {
                0 -> event.category == "Daily"
                1 -> event.category == "Weekly"
                2 -> event.category == "Monthly"
                else -> true
            }
            isCalendarActive && matchesFilter
        }.sortedBy { it.dtStart }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun loadLocalEvents(context: Context) {
        android.util.Log.d("CalendarViewModel", "loadLocalEvents: starts query for local calendar events")
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val uri = CalendarContract.Events.CONTENT_URI
                    val projection = arrayOf(
                        CalendarContract.Events._ID,
                        CalendarContract.Events.TITLE,
                        CalendarContract.Events.DESCRIPTION,
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.DTEND,
                        CalendarContract.Events.EVENT_LOCATION,
                        CalendarContract.Events.ALL_DAY,
                        CalendarContract.Events.CALENDAR_ID
                    )

                    val selection = "${CalendarContract.Events.DTSTART} >= ?"
                    val nowMs = System.currentTimeMillis()
                    val selectionArgs = arrayOf(nowMs.toString())
                    val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

                    val resultList = mutableListOf<LocalCalendarEvent>()
                    context.contentResolver.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                        val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                        val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                        val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                        val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                        val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                        val allDayIdx = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
                        val calendarIdIdx = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)

                        while (cursor.moveToNext()) {
                            val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                            val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "No Title" else "No Title"
                            val desc = if (descIdx >= 0) cursor.getString(descIdx) else null
                            val start = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                            val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                            val loc = if (locIdx >= 0) cursor.getString(locIdx) else null
                            val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false
                            val calendarId = if (calendarIdIdx >= 0) cursor.getLong(calendarIdIdx) else 0L

                            val category = calculateCategory(start)

                            resultList.add(
                                LocalCalendarEvent(
                                    id = id,
                                    title = title,
                                    description = desc,
                                    dtStart = start,
                                    dtEnd = end,
                                    eventLocation = loc,
                                    allDay = allDay,
                                    category = category,
                                    calendarId = calendarId
                                )
                            )
                        }
                    }
                    resultList
                }
                android.util.Log.i("CalendarViewModel", "loadLocalEvents: successfully queried ${list.size} upcoming events from calendar")
                _deviceEvents.value = list
                _error.value = null
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Failed to query calendar content provider", e)
                _error.value = "Failed to query calendar: ${e.message}"
            }
        }
    }

    private fun calculateCategory(dtStart: Long): String {
        val now = Calendar.getInstance()
        
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        
        val endOfWeek = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }
        
        val eventCal = Calendar.getInstance().apply { timeInMillis = dtStart }
        
        return when {
            eventCal.timeInMillis in startOfToday.timeInMillis..endOfToday.timeInMillis -> "Daily"
            eventCal.timeInMillis in (endOfToday.timeInMillis + 1)..endOfWeek.timeInMillis -> "Weekly"
            else -> "Monthly"
        }
    }

    companion object {
        private fun getRelativeTimeOffset(daysOffset: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}
