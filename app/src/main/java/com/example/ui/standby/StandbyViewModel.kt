package com.example.ui.standby

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.Alarm
import com.example.data.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StandbyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val prefs = application.getSharedPreferences("standby_prefs", Context.MODE_PRIVATE)
    private val alarmWriteLock = Any()

    val alarms: StateFlow<List<Alarm>> = db.alarmDao().getAllAlarms()
        .map { list -> list.distinctBy { it.hour to it.minute } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _nextSystemAlarm = MutableStateFlow<String?>("No Alarm")
    val nextSystemAlarm: StateFlow<String?> = _nextSystemAlarm

    // Persisted active clock face index (0..15) so the app always opens on the last used clock face
    val lastClockFaceIndex = MutableStateFlow(prefs.getInt("lastClockFaceIndex", 0).coerceIn(0, 15))

    val clockFaceTitles = listOf(
        "Digital Minimal",
        "Analog Classic",
        "Airport Split-Flap Board",
        "Binary Matrix",
        "Modern Bold",
        "Contrasting Split",
        "Analog Motor Dashboard",
        "Pastel Bubbles",
        "Ambient Horizon",
        "High-Tech Nixie Tube",
        "World Time Zone Map",
        "StandBy Sage & Complications",
        "Overlapping Float Numerals"
    )

    val clockFaceSubtitles = listOf(
        "01 | DIGITAL MINIMAL",
        "02 | ANALOG CLASSIC",
        "03 | AIRPORT FLIPBOARD",
        "04 | BINARY STYLE",
        "05 | MODERN BOLD TEXT",
        "06 | CONTRASTING SPLIT",
        "07 | ANALOG DASHBOARD",
        "08 | BUBBLE PASTEL",
        "09 | AMBIENT GRADIENT",
        "10 | NIXIE TUBE",
        "11 | WORLD TIME MAP",
        "12 | STANDBY SAGE",
        "13 | FLOAT NUMERALS"
    )

    val enabledClockFaces = MutableStateFlow<Set<Int>>(
        prefs.getStringSet("enabledClockFaces", null)
            ?.mapNotNull { it.toIntOrNull()?.takeIf { idx -> idx in 0..12 } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: (0..12).toSet()
    )

    // Duo screens strictly match the actual main screens we have built (13 Clock Faces + 4 Utility Screens)
    val duoWidgetTitles = listOf(
        "Digital Minimal",
        "Analog Classic",
        "Airport Split-Flap Board",
        "Binary Matrix",
        "Modern Bold",
        "Contrasting Split",
        "Analog Motor Dashboard",
        "Pastel Bubbles",
        "Ambient Horizon",
        "High-Tech Nixie Tube",
        "World Time Zone Map",
        "StandBy Sage & Complications",
        "Overlapping Float Numerals",
        "Alarm Screen",
        "Calendar Screen",
        "Music Player",
        "Tools Hub"
    )

    // Persisted Duo widget left/right indices and Tools settings
    val duoLeftWidgetIndex = MutableStateFlow(prefs.getInt("duoLeftWidgetIndex", 2).coerceIn(0, duoWidgetTitles.lastIndex))
    val duoRightWidgetIndex = MutableStateFlow(prefs.getInt("duoRightWidgetIndex", 10).coerceIn(0, duoWidgetTitles.lastIndex))
    val timerModeIndex = MutableStateFlow(prefs.getInt("timerModeIndex", 0).coerceIn(0, 3))
    val pomodoroFocusMinutes = MutableStateFlow(prefs.getInt("pomodoroFocusMinutes", 25))
    val galleryPermissionGranted = MutableStateFlow(prefs.getBoolean("galleryPermissionGranted", false))
    val galleryPhotoUris = MutableStateFlow<List<String>>(
        prefs.getStringSet("galleryPhotoUris", emptySet())?.toList()?.sorted() ?: emptyList()
    )
    val slideshowIntervalSeconds = MutableStateFlow(prefs.getInt("slideshowIntervalSeconds", 8).coerceIn(3, 60))

    // Location & TimeZone settings for Clock, World Map, Airport Split-Flap Terminal, and Duo Panes
    val locationMode = MutableStateFlow(prefs.getString("locationMode", "auto") ?: "auto")
    val customLocationName = MutableStateFlow(prefs.getString("customLocationName", "") ?: "")
    val clockTimeZoneId = MutableStateFlow(prefs.getString("clockTimeZoneId", "AUTO") ?: "AUTO")
    val duoLeftTimeZoneId = MutableStateFlow(prefs.getString("duoLeftTimeZoneId", "AUTO") ?: "AUTO")
    val duoRightTimeZoneId = MutableStateFlow(prefs.getString("duoRightTimeZoneId", "AUTO") ?: "AUTO")
    val duoLeftLocationName = MutableStateFlow(prefs.getString("duoLeftLocationName", "") ?: "")
    val duoRightLocationName = MutableStateFlow(prefs.getString("duoRightLocationName", "") ?: "")
    val gpsLocationName = MutableStateFlow<String?>(prefs.getString("gpsLocationName", null))
    val gpsLatitude = MutableStateFlow<Float?>(if (prefs.contains("gpsLatitude")) prefs.getFloat("gpsLatitude", 0f) else null)
    val gpsLongitude = MutableStateFlow<Float?>(if (prefs.contains("gpsLongitude")) prefs.getFloat("gpsLongitude", 0f) else null)

    val selectableLocations = listOf(
        "San Francisco (SFO)",
        "New York (JFK)",
        "Ohio (CMH)",
        "Chicago (ORD)",
        "London (LHR)",
        "Paris (CDG)",
        "Frankfurt (FRA)",
        "Dubai (DXB)",
        "New Delhi (DEL)",
        "Mumbai (BOM)",
        "Bengaluru (BLR)",
        "Singapore (SIN)",
        "Tokyo (HND)",
        "Sydney (SYD)",
        "Toronto (YYZ)"
    )

    val selectableTimeZones = listOf(
        "AUTO" to "Auto (Location / System TimeZone)",
        "America/Los_Angeles" to "Pacific Time • San Francisco (PST/PDT)",
        "America/Denver" to "Mountain Time • Denver (MST/MDT)",
        "America/Chicago" to "Central Time • Chicago (CST/CDT)",
        "America/New_York" to "Eastern Time • New York / Ohio (EST/EDT)",
        "America/Toronto" to "Eastern Time • Toronto (EST/EDT)",
        "America/Sao_Paulo" to "Brasília Time • São Paulo (BRT)",
        "UTC" to "Coordinated Universal Time (UTC)",
        "Europe/London" to "United Kingdom • London (GMT/BST)",
        "Europe/Paris" to "Central Europe • Paris / Berlin (CET)",
        "Europe/Athens" to "Eastern Europe • Athens / Cairo (EET)",
        "Asia/Dubai" to "Gulf Standard Time • Dubai (GST)",
        "Asia/Kolkata" to "India Standard Time • Delhi / Mumbai (IST)",
        "Asia/Bangkok" to "Indochina Time • Bangkok (ICT)",
        "Asia/Singapore" to "Singapore / Hong Kong Time (SGT)",
        "Asia/Tokyo" to "Japan Standard Time • Tokyo (JST)",
        "Australia/Sydney" to "Australian Eastern Time • Sydney (AEST)",
        "Pacific/Auckland" to "New Zealand Time • Auckland (NZST)"
    )

    // Per-page customized settings (Page 0: Clock, 1: Alarm, 2: Calendar, 3: Music, 4: Tools, 5: Duo)
    val colorPage0 = MutableStateFlow(prefs.getInt("colorPage0", prefs.getInt("selectedColorIndex", 0)))
    val colorPage1 = MutableStateFlow(prefs.getInt("colorPage1", 2))
    val colorPage2 = MutableStateFlow(prefs.getInt("colorPage2", 3))
    val colorPage3 = MutableStateFlow(prefs.getInt("colorPage3", 1))
    val colorPage4 = MutableStateFlow(prefs.getInt("colorPage4", 2))
    val colorPage5 = MutableStateFlow(prefs.getInt("colorPage5", 0))

    val fontPage0 = MutableStateFlow(prefs.getInt("fontPage0", prefs.getInt("selectedFontIndex", 0)))
    val fontPage1 = MutableStateFlow(prefs.getInt("fontPage1", 1))
    val fontPage2 = MutableStateFlow(prefs.getInt("fontPage2", 2))
    val fontPage3 = MutableStateFlow(prefs.getInt("fontPage3", 0))
    val fontPage4 = MutableStateFlow(prefs.getInt("fontPage4", 0))
    val fontPage5 = MutableStateFlow(prefs.getInt("fontPage5", 0))

    val showWeatherPage0 = MutableStateFlow(prefs.getBoolean("showWeatherPage0", prefs.getBoolean("showWeather", false)))
    val showWeatherPage1 = MutableStateFlow(prefs.getBoolean("showWeatherPage1", false))
    val showWeatherPage2 = MutableStateFlow(prefs.getBoolean("showWeatherPage2", false))
    val showWeatherPage3 = MutableStateFlow(prefs.getBoolean("showWeatherPage3", false))
    val showWeatherPage4 = MutableStateFlow(prefs.getBoolean("showWeatherPage4", false))
    val showWeatherPage5 = MutableStateFlow(prefs.getBoolean("showWeatherPage5", false))

    val scalePage0 = MutableStateFlow(prefs.getFloat("scalePage0", prefs.getFloat("clockScale", 1.2f)))
    val scalePage1 = MutableStateFlow(prefs.getFloat("scalePage1", 1.0f))
    val scalePage2 = MutableStateFlow(prefs.getFloat("scalePage2", 1.0f))
    val scalePage3 = MutableStateFlow(prefs.getFloat("scalePage3", 1.0f))
    val scalePage4 = MutableStateFlow(prefs.getFloat("scalePage4", 1.0f))
    val scalePage5 = MutableStateFlow(prefs.getFloat("scalePage5", 1.0f))

    // Setting to remove animation for distraction-free theme (per screen)
    val animPage0 = MutableStateFlow(prefs.getBoolean("animPage0", true))
    val animPage1 = MutableStateFlow(prefs.getBoolean("animPage1", true))
    val animPage2 = MutableStateFlow(prefs.getBoolean("animPage2", true))
    val animPage3 = MutableStateFlow(prefs.getBoolean("animPage3", true))
    val animPage4 = MutableStateFlow(prefs.getBoolean("animPage4", true))
    val animPage5 = MutableStateFlow(prefs.getBoolean("animPage5", true))

    // Custom visual overrides
    val textGlowPage0 = MutableStateFlow(prefs.getBoolean("textGlowPage0", true))
    val textGlowPage1 = MutableStateFlow(prefs.getBoolean("textGlowPage1", true))
    val textGlowPage2 = MutableStateFlow(prefs.getBoolean("textGlowPage2", true))
    val textGlowPage3 = MutableStateFlow(prefs.getBoolean("textGlowPage3", true))
    val textGlowPage4 = MutableStateFlow(prefs.getBoolean("textGlowPage4", true))
    val textGlowPage5 = MutableStateFlow(prefs.getBoolean("textGlowPage5", true))

    val textOutlineOnlyPage0 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage0", false))
    val textOutlineOnlyPage1 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage1", false))
    val textOutlineOnlyPage2 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage2", false))
    val textOutlineOnlyPage3 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage3", false))
    val textOutlineOnlyPage4 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage4", false))
    val textOutlineOnlyPage5 = MutableStateFlow(prefs.getBoolean("textOutlineOnlyPage5", false))

    val textGradientPage0 = MutableStateFlow(prefs.getInt("textGradientPage0", -1))
    val textGradientPage1 = MutableStateFlow(prefs.getInt("textGradientPage1", -1))
    val textGradientPage2 = MutableStateFlow(prefs.getInt("textGradientPage2", -1))
    val textGradientPage3 = MutableStateFlow(prefs.getInt("textGradientPage3", -1))
    val textGradientPage4 = MutableStateFlow(prefs.getInt("textGradientPage4", -1))
    val textGradientPage5 = MutableStateFlow(prefs.getInt("textGradientPage5", -1))

    val bgUriPage0 = MutableStateFlow<String?>(prefs.getString("bgUriPage0", null))
    val bgUriPage1 = MutableStateFlow<String?>(prefs.getString("bgUriPage1", null))
    val bgUriPage2 = MutableStateFlow<String?>(prefs.getString("bgUriPage2", null))
    val bgUriPage3 = MutableStateFlow<String?>(prefs.getString("bgUriPage3", null))
    val bgUriPage4 = MutableStateFlow<String?>(prefs.getString("bgUriPage4", null))
    val bgUriPage5 = MutableStateFlow<String?>(prefs.getString("bgUriPage5", null))

    val use24HourFormat = MutableStateFlow(prefs.getBoolean("use24HourFormat", false))
    val showAmPm = MutableStateFlow(prefs.getBoolean("showAmPm", true))
    val showSeconds = MutableStateFlow(prefs.getBoolean("showSeconds", false))
    val useClassicMediaTheme = MutableStateFlow(prefs.getBoolean("useClassicMediaTheme", false))

    // Pre-Alarm Gradual Brightness Lowering preferences & live state
    val gradualAlarmDimEnabled = MutableStateFlow(prefs.getBoolean("gradualAlarmDimEnabled", true))
    val gradualAlarmDimWindowMinutes = MutableStateFlow(prefs.getInt("gradualAlarmDimWindowMinutes", 30).coerceIn(5, 180))
    val gradualAlarmMinBrightness = MutableStateFlow(prefs.getFloat("gradualAlarmMinBrightness", 0.25f).coerceIn(0.10f, 0.80f))

    private val _clockBrightnessFactor = MutableStateFlow(1.0f)
    val clockBrightnessFactor: StateFlow<Float> = _clockBrightnessFactor

    private val _isPreAlarmDimmingActive = MutableStateFlow(false)
    val isPreAlarmDimmingActive: StateFlow<Boolean> = _isPreAlarmDimmingActive

    private val _minutesUntilNextAlarm = MutableStateFlow<Int?>(null)
    val minutesUntilNextAlarm: StateFlow<Int?> = _minutesUntilNextAlarm

    fun setGradualAlarmDimEnabled(enabled: Boolean) {
        gradualAlarmDimEnabled.value = enabled
        prefs.edit().putBoolean("gradualAlarmDimEnabled", enabled).apply()
        updateNextAlarm()
    }

    fun setGradualAlarmDimWindowMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(5, 180)
        gradualAlarmDimWindowMinutes.value = safeMinutes
        prefs.edit().putInt("gradualAlarmDimWindowMinutes", safeMinutes).apply()
        updateNextAlarm()
    }

    fun setGradualAlarmMinBrightness(minBrightness: Float) {
        val safeBrightness = minBrightness.coerceIn(0.10f, 0.80f)
        gradualAlarmMinBrightness.value = safeBrightness
        prefs.edit().putFloat("gradualAlarmMinBrightness", safeBrightness).apply()
        updateNextAlarm()
    }

    fun setLastClockFaceIndex(index: Int) {
        val safeIdx = index.coerceIn(0, clockFaceTitles.lastIndex)
        if (lastClockFaceIndex.value != safeIdx) {
            lastClockFaceIndex.value = safeIdx
        }
        prefs.edit().putInt("lastClockFaceIndex", safeIdx).apply()
    }

    fun setClockFaceEnabled(faceIndex: Int, enabled: Boolean) {
        if (faceIndex !in clockFaceTitles.indices) return
        val current = enabledClockFaces.value.toMutableSet()
        if (enabled) {
            current.add(faceIndex)
        } else if (current.size > 1) {
            current.remove(faceIndex)
        } else {
            // Keep at least one clock face active
            return
        }
        enabledClockFaces.value = current
        prefs.edit().putStringSet("enabledClockFaces", current.map { it.toString() }.toSet()).apply()
        if (lastClockFaceIndex.value !in current) {
            setLastClockFaceIndex(current.minOrNull() ?: 0)
        }
    }

    fun toggleClockFace(faceIndex: Int) {
        val isCurrentlyEnabled = faceIndex in enabledClockFaces.value
        setClockFaceEnabled(faceIndex, !isCurrentlyEnabled)
    }

    fun setDuoLeftWidgetIndex(index: Int) {
        val safeIdx = index.coerceIn(0, duoWidgetTitles.lastIndex)
        duoLeftWidgetIndex.value = safeIdx
        prefs.edit().putInt("duoLeftWidgetIndex", safeIdx).apply()
    }

    fun setDuoRightWidgetIndex(index: Int) {
        val safeIdx = index.coerceIn(0, duoWidgetTitles.lastIndex)
        duoRightWidgetIndex.value = safeIdx
        prefs.edit().putInt("duoRightWidgetIndex", safeIdx).apply()
    }

    fun setTimerModeIndex(index: Int) {
        val safeIdx = index.coerceIn(0, 3)
        timerModeIndex.value = safeIdx
        prefs.edit().putInt("timerModeIndex", safeIdx).apply()
    }

    fun setPomodoroFocusMinutes(minutes: Int) {
        pomodoroFocusMinutes.value = minutes
        prefs.edit().putInt("pomodoroFocusMinutes", minutes).apply()
    }

    fun setGalleryPermissionGranted(granted: Boolean) {
        galleryPermissionGranted.value = granted
        prefs.edit().putBoolean("galleryPermissionGranted", granted).apply()
    }

    fun addGalleryPhotoUris(uris: List<String>) {
        val merged = (galleryPhotoUris.value + uris.filter { it.isNotBlank() }).distinct()
        galleryPhotoUris.value = merged
        prefs.edit().putStringSet("galleryPhotoUris", merged.toSet()).apply()
    }

    fun removeGalleryPhotoUri(uri: String) {
        val updated = galleryPhotoUris.value.filterNot { it == uri }
        galleryPhotoUris.value = updated
        prefs.edit().putStringSet("galleryPhotoUris", updated.toSet()).apply()
    }

    fun setSlideshowIntervalSeconds(seconds: Int) {
        val safeSecs = seconds.coerceIn(3, 60)
        slideshowIntervalSeconds.value = safeSecs
        prefs.edit().putInt("slideshowIntervalSeconds", safeSecs).apply()
    }

    fun setUseClassicMediaTheme(enabled: Boolean) {
        useClassicMediaTheme.value = enabled
        prefs.edit().putBoolean("useClassicMediaTheme", enabled).apply()
    }

    fun setLocationMode(mode: String) {
        val safeMode = if (mode == "custom") "custom" else "auto"
        locationMode.value = safeMode
        prefs.edit().putString("locationMode", safeMode).apply()
    }

    fun setCustomLocationName(location: String) {
        val trimmed = location.trim()
        customLocationName.value = trimmed
        prefs.edit().putString("customLocationName", trimmed).apply()
        if (trimmed.isNotEmpty()) {
            setLocationMode("custom")
        }
    }

    fun setClockTimeZoneId(tzId: String) {
        val clean = tzId.trim().ifBlank { "AUTO" }
        clockTimeZoneId.value = clean
        prefs.edit().putString("clockTimeZoneId", clean).apply()
    }

    fun setDuoLeftTimeZoneId(tzId: String) {
        val clean = tzId.trim().ifBlank { "AUTO" }
        duoLeftTimeZoneId.value = clean
        prefs.edit().putString("duoLeftTimeZoneId", clean).apply()
    }

    fun setDuoRightTimeZoneId(tzId: String) {
        val clean = tzId.trim().ifBlank { "AUTO" }
        duoRightTimeZoneId.value = clean
        prefs.edit().putString("duoRightTimeZoneId", clean).apply()
    }

    fun setDuoLeftLocationName(location: String) {
        val clean = location.trim()
        duoLeftLocationName.value = clean
        prefs.edit().putString("duoLeftLocationName", clean).apply()
    }

    fun setDuoRightLocationName(location: String) {
        val clean = location.trim()
        duoRightLocationName.value = clean
        prefs.edit().putString("duoRightLocationName", clean).apply()
    }

    fun refreshDeviceLocation(context: Context = getApplication()): Boolean {
        val detected = GoogleDeviceAndHomeMonitor.detectDeviceLocation(context)
        if (detected != null) {
            val (city, lat, lon) = detected
            gpsLocationName.value = city
            gpsLatitude.value = lat
            gpsLongitude.value = lon
            prefs.edit()
                .putString("gpsLocationName", city)
                .putFloat("gpsLatitude", lat)
                .putFloat("gpsLongitude", lon)
                .apply()
            return true
        }
        return false
    }

    /**
     * pane: 0 = Main Clock, 1 = Duo Left Pane, 2 = Duo Right Pane
     */
    fun resolvePaneLocationInfo(pane: Int = 0): UserTimeZoneRegionInfo {
        val isCustomMain = locationMode.value == "custom" && customLocationName.value.isNotBlank()
        val mainLoc = if (isCustomMain) customLocationName.value else null
        val locOverride = when (pane) {
            1 -> duoLeftLocationName.value.takeIf { it.isNotBlank() } ?: mainLoc
            2 -> duoRightLocationName.value.takeIf { it.isNotBlank() } ?: mainLoc
            else -> mainLoc
        }
        val tzOverride = when (pane) {
            1 -> duoLeftTimeZoneId.value.takeIf { it != "AUTO" }
                ?: if (duoLeftLocationName.value.isNotBlank()) null else clockTimeZoneId.value.takeIf { it != "AUTO" }
            2 -> duoRightTimeZoneId.value.takeIf { it != "AUTO" }
                ?: if (duoRightLocationName.value.isNotBlank()) null else clockTimeZoneId.value.takeIf { it != "AUTO" }
            else -> clockTimeZoneId.value.takeIf { it != "AUTO" }
        }
        return GoogleDeviceAndHomeMonitor.resolveTimeZoneAndRegion(
            customLocationOverride = locOverride,
            customTimeZoneId = tzOverride,
            gpsCityOverride = if (locOverride == null) gpsLocationName.value else null,
            gpsLat = if (locOverride == null) gpsLatitude.value else null,
            gpsLon = if (locOverride == null) gpsLongitude.value else null
        )
    }

    fun resolveCurrentLocationInfo(): UserTimeZoneRegionInfo = resolvePaneLocationInfo(0)

    /**
     * Searches known cities, airport codes, and world time zones for the searchable Location & Time Zone field.
     * Returns pairs of (selectionValue, displayDescription).
     */
    fun searchLocationsAndTimeZones(query: String): List<Pair<String, String>> {
        val q = query.trim().lowercase(Locale.getDefault())
        val baseEntries = buildList {
            add("AUTO" to "Auto (GPS / System Time Zone)")
            GoogleDeviceAndHomeMonitor.KNOWN_REGIONS.forEach { entry ->
                val label = "${entry.displayName} (${entry.airportCode})"
                val sign = if (entry.utcOffsetHours >= 0) "+" else ""
                val offsetText = if (entry.utcOffsetHours % 1f == 0f) {
                    "UTC$sign${entry.utcOffsetHours.toInt()}"
                } else {
                    "UTC$sign${entry.utcOffsetHours}"
                }
                val subtitle = "$offsetText • ${entry.timeZoneId}"
                add("LOC:$label" to "$label — $subtitle")
            }
            selectableTimeZones.drop(1).forEach { (tzId, label) ->
                add("TZ:$tzId" to "$label [$tzId]")
            }
        }
        if (q.isEmpty()) {
            return baseEntries.take(8)
        }
        val matched = baseEntries.filter { (key, desc) ->
            desc.lowercase(Locale.getDefault()).contains(q) || key.lowercase(Locale.getDefault()).contains(q)
        }.toMutableList()

        if (matched.size < 8 && q.length >= 2) {
            java.util.TimeZone.getAvailableIDs()
                .filter { id ->
                    id.contains("/") &&
                        !id.startsWith("Etc/") &&
                        id.lowercase(Locale.getDefault()).replace("_", " ").contains(q)
                }
                .take(8 - matched.size)
                .forEach { tzId ->
                    val city = tzId.substringAfterLast("/").replace("_", " ")
                    val tz = java.util.TimeZone.getTimeZone(tzId)
                    val shortCode = tz.getDisplayName(tz.inDaylightTime( java.util.Date() ), java.util.TimeZone.SHORT, Locale.getDefault())
                    val key = "TZ:$tzId"
                    if (matched.none { it.first.equals(key, ignoreCase = true) }) {
                        matched.add(key to "$city ($shortCode) — $tzId")
                    }
                }
        }
        return matched.take(10)
    }

    fun applySearchedLocationSelection(selectionKey: String, rawQueryFallback: String = "", pane: Int = 0) {
        when {
            selectionKey == "AUTO" -> {
                when (pane) {
                    1 -> {
                        setDuoLeftLocationName("")
                        setDuoLeftTimeZoneId("AUTO")
                    }
                    2 -> {
                        setDuoRightLocationName("")
                        setDuoRightTimeZoneId("AUTO")
                    }
                    else -> {
                        setCustomLocationName("")
                        setLocationMode("auto")
                        setClockTimeZoneId("AUTO")
                    }
                }
            }
            selectionKey.startsWith("LOC:") -> {
                val locLabel = selectionKey.removePrefix("LOC:").trim()
                val resolved = GoogleDeviceAndHomeMonitor.resolveTimeZoneAndRegion(customLocationOverride = locLabel)
                when (pane) {
                    1 -> {
                        setDuoLeftLocationName(locLabel)
                        setDuoLeftTimeZoneId(resolved.timeZoneId)
                    }
                    2 -> {
                        setDuoRightLocationName(locLabel)
                        setDuoRightTimeZoneId(resolved.timeZoneId)
                    }
                    else -> {
                        setCustomLocationName(locLabel)
                        setClockTimeZoneId(resolved.timeZoneId)
                    }
                }
            }
            selectionKey.startsWith("TZ:") -> {
                val tzId = selectionKey.removePrefix("TZ:").trim()
                val resolved = GoogleDeviceAndHomeMonitor.resolveTimeZoneAndRegion(customTimeZoneId = tzId)
                val locLabel = "${resolved.regionName} (${resolved.airportCode})"
                when (pane) {
                    1 -> {
                        setDuoLeftLocationName(locLabel)
                        setDuoLeftTimeZoneId(tzId)
                    }
                    2 -> {
                        setDuoRightLocationName(locLabel)
                        setDuoRightTimeZoneId(tzId)
                    }
                    else -> {
                        setCustomLocationName(locLabel)
                        setClockTimeZoneId(tzId)
                    }
                }
            }
            rawQueryFallback.isNotBlank() -> {
                val clean = rawQueryFallback.trim()
                val resolved = GoogleDeviceAndHomeMonitor.resolveTimeZoneAndRegion(customLocationOverride = clean)
                when (pane) {
                    1 -> {
                        setDuoLeftLocationName(clean)
                        setDuoLeftTimeZoneId(resolved.timeZoneId)
                    }
                    2 -> {
                        setDuoRightLocationName(clean)
                        setDuoRightTimeZoneId(resolved.timeZoneId)
                    }
                    else -> {
                        setCustomLocationName(clean)
                        setClockTimeZoneId(resolved.timeZoneId)
                    }
                }
            }
        }
    }

    init {
        refreshDeviceLocation(application)
        synchronized(alarmWriteLock) {
            try {
                val snapshot = db.alarmDao().getAllAlarmsSnapshot()
                val seen = mutableSetOf<Pair<Int, Int>>()
                for (alarm in snapshot) {
                    val key = alarm.hour to alarm.minute
                    if (!seen.add(key)) {
                        AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
                        db.alarmDao().delete(alarm)
                    } else if (alarm.isEnabled) {
                        AlarmScheduler.scheduleAlarm(getApplication(), alarm)
                    }
                }
            } catch (_: Exception) {
            }
        }
        viewModelScope.launch {
            combine(
                alarms,
                use24HourFormat,
                gradualAlarmDimEnabled,
                gradualAlarmDimWindowMinutes,
                gradualAlarmMinBrightness
            ) { _, _, _, _, _ -> }.collect {
                updateNextAlarm()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                updateNextAlarm()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                AlarmRingingController.checkAndTriggerDueAlarm(
                    context = getApplication(),
                    enabledAlarms = alarms.value
                )
            }
        }
    }

    fun computeBrightnessForDiffMillis(
        diffMillis: Long?,
        dimEnabled: Boolean = gradualAlarmDimEnabled.value,
        windowMinutes: Int = gradualAlarmDimWindowMinutes.value,
        minBrightness: Float = gradualAlarmMinBrightness.value
    ): Float {
        if (!dimEnabled || diffMillis == null || diffMillis < 0L) return 1.0f
        val windowMillis = windowMinutes.coerceIn(5, 180) * 60_000L
        if (diffMillis > windowMillis || windowMillis <= 0L) return 1.0f
        val clampedMin = minBrightness.coerceIn(0.10f, 0.80f)
        val progress = (diffMillis.toFloat() / windowMillis.toFloat()).coerceIn(0f, 1f)
        return (clampedMin + (1.0f - clampedMin) * progress).coerceIn(clampedMin, 1.0f)
    }

    fun updateNextAlarm(now: Calendar = Calendar.getInstance()) {
        try {
            val currentAlarms = alarms.value
            val is24Hr = use24HourFormat.value
            val enabledAlarms = currentAlarms.filter { it.isEnabled }
            if (enabledAlarms.isEmpty()) {
                _nextSystemAlarm.value = "No Alarm"
                _minutesUntilNextAlarm.value = null
                _clockBrightnessFactor.value = 1.0f
                _isPreAlarmDimmingActive.value = false
                return
            }

            var soonestAlarm: Alarm? = null
            var soonestDiff = Long.MAX_VALUE

            for (alarm in enabledAlarms) {
                val alarmCal = (now.clone() as Calendar).apply {
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
                val displayCal = (now.clone() as Calendar).apply {
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

                val minutesLeft = ((soonestDiff + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
                _minutesUntilNextAlarm.value = minutesLeft

                val brightness = computeBrightnessForDiffMillis(soonestDiff)
                _clockBrightnessFactor.value = brightness
                _isPreAlarmDimmingActive.value = gradualAlarmDimEnabled.value &&
                    soonestDiff <= gradualAlarmDimWindowMinutes.value * 60_000L &&
                    brightness < 0.99f
            } else {
                _nextSystemAlarm.value = "No Alarm"
                _minutesUntilNextAlarm.value = null
                _clockBrightnessFactor.value = 1.0f
                _isPreAlarmDimmingActive.value = false
            }
        } catch (e: Exception) {
            _nextSystemAlarm.value = "No Alarm"
            _minutesUntilNextAlarm.value = null
            _clockBrightnessFactor.value = 1.0f
            _isPreAlarmDimmingActive.value = false
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
            5 -> colorPage5
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
            5 -> fontPage5
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
            5 -> showWeatherPage5
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
            5 -> scalePage5
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
            5 -> animPage5
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
            5 -> textGlowPage5
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
            5 -> textOutlineOnlyPage5
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
            5 -> textGradientPage5
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
            5 -> bgUriPage5
            else -> bgUriPage0
        }
    }

    fun setColorIndexForPage(page: Int, index: Int) {
        val safeIdx = index.coerceIn(0, colors.lastIndex)
        getColorFlow(page).value = safeIdx
        prefs.edit().putInt("colorPage$page", safeIdx).apply()
    }

    fun setFontIndexForPage(page: Int, index: Int) {
        val safeIdx = index.coerceIn(0, fonts.lastIndex)
        getFontFlow(page).value = safeIdx
        prefs.edit().putInt("fontPage$page", safeIdx).apply()
    }

    fun setShowWeatherForPage(page: Int, show: Boolean) {
        getWeatherFlow(page).value = show
        prefs.edit().putBoolean("showWeatherPage$page", show).apply()
    }

    fun setClockScaleForPage(page: Int, scale: Float) {
        getScaleFlow(page).value = scale
        prefs.edit().putFloat("scalePage$page", scale).apply()
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
        FontFamily(Font(R.font.vt323, FontWeight.Normal)) to "Matrix CRT",
        FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal)) to "Matrix Terminal",
        FontFamily(Font(R.font.orbitron, FontWeight.Bold)) to "Cyber Sci-Fi",
        FontFamily(Font(R.font.oswald, FontWeight.Bold)) to "Solari Condensed",
        FontFamily.Cursive to "Script",
        FontFamily.Default to "Default"
    )

    fun upsertAlarmSync(
        hour: Int,
        minute: Int,
        label: String,
        snooze: Int,
        toneUri: String = "default_alarm",
        musicPackageName: String = "",
        musicAppName: String = "Default Alarm Tone",
        gradualVolume: Boolean = true,
        gradualVolumeSeconds: Int = 30
    ) {
        synchronized(alarmWriteLock) {
            val existing = db.alarmDao().getAlarmByTime(hour, minute)
            if (existing != null) {
                val updated = existing.copy(
                    isEnabled = true,
                    label = label,
                    snoozeMinutes = snooze,
                    toneUri = toneUri,
                    musicPackageName = musicPackageName,
                    musicAppName = musicAppName,
                    gradualVolume = gradualVolume,
                    gradualVolumeSeconds = gradualVolumeSeconds
                )
                db.alarmDao().update(updated)
                db.alarmDao().deleteDuplicatesForTime(hour, minute, existing.id)
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                val newAlarm = Alarm(
                    hour = hour,
                    minute = minute,
                    isEnabled = true,
                    label = label,
                    snoozeMinutes = snooze,
                    toneUri = toneUri,
                    musicPackageName = musicPackageName,
                    musicAppName = musicAppName,
                    gradualVolume = gradualVolume,
                    gradualVolumeSeconds = gradualVolumeSeconds
                )
                db.alarmDao().insert(newAlarm)
                val inserted = db.alarmDao().getAlarmByTime(hour, minute) ?: newAlarm
                AlarmScheduler.scheduleAlarm(getApplication(), inserted)
            }
        }
    }

    fun addAlarm(
        hour: Int,
        minute: Int,
        label: String,
        snooze: Int,
        toneUri: String = "default_alarm",
        musicPackageName: String = "",
        musicAppName: String = "Default Alarm Tone",
        gradualVolume: Boolean = true,
        gradualVolumeSeconds: Int = 30
    ) {
        upsertAlarmSync(
            hour = hour,
            minute = minute,
            label = label,
            snooze = snooze,
            toneUri = toneUri,
            musicPackageName = musicPackageName,
            musicAppName = musicAppName,
            gradualVolume = gradualVolume,
            gradualVolumeSeconds = gradualVolumeSeconds
        )
    }

    fun updateAlarm(alarm: Alarm) {
        synchronized(alarmWriteLock) {
            db.alarmDao().update(alarm)
            AlarmScheduler.scheduleAlarm(getApplication(), alarm)
        }
    }

    fun toggleAlarmSync(alarm: Alarm) {
        synchronized(alarmWriteLock) {
            val updated = alarm.copy(isEnabled = !alarm.isEnabled)
            db.alarmDao().update(updated)
            if (updated.isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), updated.id)
            }
        }
    }

    fun toggleAlarm(alarm: Alarm) {
        toggleAlarmSync(alarm)
    }

    fun deleteAlarmSync(alarm: Alarm) {
        synchronized(alarmWriteLock) {
            AlarmScheduler.cancelAlarm(getApplication(), alarm.id)
            db.alarmDao().deleteDuplicatesForTime(alarm.hour, alarm.minute, alarm.id)
            db.alarmDao().delete(alarm)
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        deleteAlarmSync(alarm)
    }

    fun triggerTestAlarm(alarm: Alarm) {
        AlarmRingingController.triggerAlarm(getApplication(), alarm)
    }

    fun snoozeActiveAlarm() {
        AlarmRingingController.snoozeActiveAlarm(getApplication())
    }

    fun dismissActiveAlarm() {
        AlarmRingingController.dismissAlarm(getApplication())
    }
}
