package com.example.ui.standby

import android.content.Context
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
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
    val category: String // "Daily", "Weekly", or "Monthly"
)

class CalendarViewModel : ViewModel() {
    private val _deviceEvents = MutableStateFlow<List<LocalCalendarEvent>>(emptyList())
    val deviceEvents: StateFlow<List<LocalCalendarEvent>> = _deviceEvents
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Filtering preferences
    val showDaily = MutableStateFlow(true)
    val showWeekly = MutableStateFlow(true)
    val showMonthly = MutableStateFlow(true)
    val showDemoEvents = MutableStateFlow(false) // Enables rich default demo calendar data if system calendar is empty
    
    // Split View (Slide-Up) Theme Preferences
    val startWeekOnMonday = MutableStateFlow(false)
    val showEventDots = MutableStateFlow(true)
    val leftThemeStyle = MutableStateFlow(0) // 0 = Both Date & Month Grid, 1 = Month Grid Only, 2 = Large Date Only

    // Comprehensive list of realistic Demo/Mock Standby Events
    private val demoEventsList = listOf(
        // Daily
        LocalCalendarEvent(
            id = -101,
            title = "Standup Scrum Sync",
            description = "Daily huddle to align on layout visual assets and bug verification steps.",
            dtStart = getRelativeTimeOffset(0, 10, 0),
            dtEnd = getRelativeTimeOffset(0, 10, 45),
            eventLocation = "Huddle Room Delta",
            allDay = false,
            category = "Daily"
        ),
        LocalCalendarEvent(
            id = -102,
            title = "Lunch with Design Guild",
            description = "Unwinding with discussions over modern typography pairings and sliding spring animations.",
            dtStart = getRelativeTimeOffset(0, 12, 30),
            dtEnd = getRelativeTimeOffset(0, 13, 30),
            eventLocation = "Velvet Bistro",
            allDay = false,
            category = "Daily"
        ),
        LocalCalendarEvent(
            id = -103,
            title = "Deep Work Session",
            description = "Zero disturbance coding block to construct elegant Material 3 widgets.",
            dtStart = getRelativeTimeOffset(0, 15, 0),
            dtEnd = getRelativeTimeOffset(0, 17, 0),
            eventLocation = "Quiet Pod 4B",
            allDay = false,
            category = "Daily"
        ),
        // Weekly
        LocalCalendarEvent(
            id = -201,
            title = "Weekly Clock Engine Review",
            description = "Analyze clock frame performance and calibration offset accuracy.",
            dtStart = getRelativeTimeOffset(1, 14, 0),
            dtEnd = getRelativeTimeOffset(1, 15, 0),
            eventLocation = "Technical Lab",
            allDay = false,
            category = "Weekly"
        ),
        LocalCalendarEvent(
            id = -202,
            title = "Acoustic Sunset Concert",
            description = "Unwind at the skyline deck with live acoustic sessions.",
            dtStart = getRelativeTimeOffset(3, 18, 0),
            dtEnd = getRelativeTimeOffset(3, 20, 30),
            eventLocation = "Skyline Garden",
            allDay = false,
            category = "Weekly"
        ),
        LocalCalendarEvent(
            id = -203,
            title = "Weekend Mountain Outing",
            description = "Hiking adventure to summit with the local mountain club.",
            dtStart = getRelativeTimeOffset(4, 8, 0),
            dtEnd = getRelativeTimeOffset(4, 16, 0),
            eventLocation = "Ridge Summit Trail",
            allDay = true,
            category = "Weekly"
        ),
        // Monthly
        LocalCalendarEvent(
            id = -301,
            title = "Monthly Project Retrospective",
            description = "Reflect on design achievements and consolidate development logs.",
            dtStart = getRelativeTimeOffset(12, 11, 0),
            dtEnd = getRelativeTimeOffset(12, 13, 15),
            eventLocation = "Virtual Dome 1",
            allDay = false,
            category = "Monthly"
        ),
        LocalCalendarEvent(
            id = -302,
            title = "Alarms & Custom Sounds Benchmark",
            description = "Audit sound design benchmarks, classic alarm sweeps, and buzz waveforms.",
            dtStart = getRelativeTimeOffset(22, 13, 0),
            dtEnd = getRelativeTimeOffset(22, 15, 30),
            eventLocation = "Acoustics Sandbox",
            allDay = false,
            category = "Monthly"
        )
    )

    // Expose final events combined dynamically from settings filters
    val events: StateFlow<List<LocalCalendarEvent>> = combine(
        _deviceEvents,
        showDaily,
        showWeekly,
        showMonthly,
        showDemoEvents
    ) { deviceList, daily, weekly, monthly, useDemo ->
        val fullList = mutableListOf<LocalCalendarEvent>()
        if (useDemo) {
            fullList.addAll(demoEventsList)
        }
        fullList.addAll(deviceList)
        
        // Remove duplicates if any (matching by ID)
        val deduplicated = fullList.distinctBy { it.id }

        // Filter based on configuration
        deduplicated.filter { event ->
            when (event.category) {
                "Daily" -> daily
                "Weekly" -> weekly
                "Monthly" -> monthly
                else -> true
            }
        }.sortedBy { it.dtStart }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = demoEventsList.sortedBy { it.dtStart }
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
                        CalendarContract.Events.ALL_DAY
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

                        while (cursor.moveToNext()) {
                            val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                            val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "No Title" else "No Title"
                            val desc = if (descIdx >= 0) cursor.getString(descIdx) else null
                            val start = if (startIdx >= 0) cursor.getLong(startIdx) else 0L
                            val end = if (endIdx >= 0) cursor.getLong(endIdx) else 0L
                            val loc = if (locIdx >= 0) cursor.getString(locIdx) else null
                            val allDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false

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
                                    category = category
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
