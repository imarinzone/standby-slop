package com.example.ui.standby

import android.content.Context
import android.provider.CalendarContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalCalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val dtStart: Long,
    val dtEnd: Long,
    val eventLocation: String?,
    val allDay: Boolean
)

class CalendarViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<LocalCalendarEvent>>(emptyList())
    val events: StateFlow<List<LocalCalendarEvent>> = _events
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

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

                            resultList.add(
                                LocalCalendarEvent(
                                    id = id,
                                    title = title,
                                    description = desc,
                                    dtStart = start,
                                    dtEnd = end,
                                    eventLocation = loc,
                                    allDay = allDay
                                )
                            )
                        }
                    }
                    resultList
                }
                android.util.Log.i("CalendarViewModel", "loadLocalEvents: successfully queried ${list.size} upcoming events from calendar")
                _events.value = list
                _error.value = null
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "Failed to query calendar content provider", e)
                _error.value = "Failed to query calendar: ${e.message}"
            }
        }
    }
}
