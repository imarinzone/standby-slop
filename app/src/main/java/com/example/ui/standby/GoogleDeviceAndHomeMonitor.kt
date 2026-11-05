package com.example.ui.standby

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class GoogleConnectedBatteryDevice(
    val id: String,
    val name: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isConnected: Boolean,
    val statusLabel: String,
    val icon: ImageVector
)

data class GoogleHomeDeviceItem(
    val id: String,
    val name: String,
    val room: String,
    val deviceType: String, // "thermostat", "light", "speaker", "display"
    val isActive: Boolean,
    val statusText: String,
    val temperatureF: Int? = null
)

data class UserTimeZoneRegionInfo(
    val regionName: String,
    val airportCityName: String,
    val airportCode: String,
    val timeZoneId: String,
    val standardTimeShort: String,
    val standardTimeLong: String,
    val utcOffsetLabel: String,
    val latitude: Float,
    val longitude: Float
)

object GoogleDeviceAndHomeMonitor {

    data class CityAirportCoord(
        val keywords: List<String>,
        val displayName: String,
        val airportCity: String,
        val airportCode: String,
        val timeZoneId: String,
        val lat: Float,
        val lon: Float,
        val utcOffsetHours: Float
    )

    val KNOWN_REGIONS = listOf(
        CityAirportCoord(listOf("los_angeles", "vancouver", "tijuana", "san francisco", "sfo", "seattle", "california", "pacific"), "San Francisco", "SAN FRANCISCO", "SFO", "America/Los_Angeles", 37.77f, -122.42f, -8f),
        CityAirportCoord(listOf("denver", "phoenix", "edmonton", "boise", "salt lake", "mountain"), "Denver", "DENVER", "DEN", "America/Denver", 39.74f, -104.99f, -7f),
        CityAirportCoord(listOf("chicago", "mexico", "winnipeg", "dallas", "houston", "austin", "central", "ord"), "Chicago", "CHICAGO", "ORD", "America/Chicago", 41.88f, -87.63f, -6f),
        CityAirportCoord(listOf("ohio", "columbus", "cleveland", "cincinnati", "cmh"), "Ohio", "OHIO", "CMH", "America/New_York", 39.96f, -82.99f, -5f),
        CityAirportCoord(listOf("new_york", "new york", "jfk", "detroit", "miami", "atlanta", "boston", "washington", "eastern"), "New York", "NEW YORK", "JFK", "America/New_York", 40.71f, -74.01f, -5f),
        CityAirportCoord(listOf("toronto", "yyz", "halifax", "santiago", "caracas", "san_juan"), "Toronto", "TORONTO", "YYZ", "America/Toronto", 43.65f, -79.38f, -5f),
        CityAirportCoord(listOf("sao_paulo", "sao paulo", "gru", "buenos_aires", "montevideo", "rio"), "São Paulo", "SAO PAULO", "GRU", "America/Sao_Paulo", -23.55f, -46.63f, -3f),
        CityAirportCoord(listOf("london", "lhr", "dublin", "lisbon", "utc", "gmt", "reykjavik"), "London", "LONDON", "LHR", "Europe/London", 51.51f, -0.13f, 0f),
        CityAirportCoord(listOf("paris", "cdg", "madrid", "amsterdam", "brussels"), "Paris", "PARIS", "CDG", "Europe/Paris", 48.86f, 2.35f, 1f),
        CityAirportCoord(listOf("frankfurt", "fra", "berlin", "rome", "zurich", "vienna", "stockholm", "warsaw", "lagos"), "Frankfurt", "FRANKFURT", "FRA", "Europe/Berlin", 50.11f, 8.68f, 1f),
        CityAirportCoord(listOf("athens", "ath", "cairo", "johannesburg", "jerusalem", "helsinki", "bucharest", "kyiv"), "Athens", "ATHENS", "ATH", "Europe/Athens", 37.98f, 23.73f, 2f),
        CityAirportCoord(listOf("istanbul", "moscow", "riyadh", "nairobi", "doha", "baghdad"), "Istanbul", "ISTANBUL", "IST", "Europe/Istanbul", 41.01f, 28.98f, 3f),
        CityAirportCoord(listOf("dubai", "dxb", "baku", "tbilisi", "muscat"), "Dubai", "DUBAI", "DXB", "Asia/Dubai", 25.20f, 55.27f, 4f),
        CityAirportCoord(listOf("karachi", "khi", "tashkent", "maldives"), "Karachi", "KARACHI", "KHI", "Asia/Karachi", 24.86f, 67.00f, 5f),
        CityAirportCoord(listOf("mumbai", "bom"), "Mumbai", "MUMBAI", "BOM", "Asia/Kolkata", 19.08f, 72.88f, 5.5f),
        CityAirportCoord(listOf("bengaluru", "bangalore", "blr"), "Bengaluru", "BENGALURU", "BLR", "Asia/Kolkata", 12.97f, 77.59f, 5.5f),
        CityAirportCoord(listOf("kolkata", "calcutta", "delhi", "new delhi", "del", "chennai", "hyderabad", "colombo", "india", "ist"), "New Delhi", "NEW DELHI", "DEL", "Asia/Kolkata", 28.61f, 77.21f, 5.5f),
        CityAirportCoord(listOf("dhaka", "dac", "almaty"), "Dhaka", "DHAKA", "DAC", "Asia/Dhaka", 23.81f, 90.41f, 6f),
        CityAirportCoord(listOf("bangkok", "bkk", "jakarta", "ho_chi_minh", "hanoi"), "Bangkok", "BANGKOK", "BKK", "Asia/Bangkok", 13.76f, 100.50f, 7f),
        CityAirportCoord(listOf("singapore", "sin", "kuala_lumpur", "hong_kong", "shanghai", "beijing", "taipei", "manila", "perth"), "Singapore", "SINGAPORE", "SIN", "Asia/Singapore", 1.35f, 103.82f, 8f),
        CityAirportCoord(listOf("tokyo", "hnd", "nrt", "seoul", "osaka"), "Tokyo", "TOKYO", "HND", "Asia/Tokyo", 35.68f, 139.77f, 9f),
        CityAirportCoord(listOf("sydney", "syd", "melbourne", "brisbane", "guam"), "Sydney", "SYDNEY", "SYD", "Australia/Sydney", -33.87f, 151.21f, 10f),
        CityAirportCoord(listOf("auckland", "akl", "fiji", "wellington"), "Auckland", "AUCKLAND", "AKL", "Pacific/Auckland", -36.85f, 174.76f, 12f)
    )

    fun calendarForTimeZone(baseCalendar: Calendar, timeZoneId: String?): Calendar {
        val tz = if (!timeZoneId.isNullOrBlank() && timeZoneId != "AUTO") {
            TimeZone.getTimeZone(timeZoneId)
        } else {
            baseCalendar.timeZone ?: TimeZone.getDefault()
        }
        return Calendar.getInstance(tz).apply {
            timeInMillis = baseCalendar.timeInMillis
        }
    }

    fun resolveTimeZoneAndRegion(
        customLocationOverride: String? = null,
        customTimeZoneId: String? = null,
        gpsCityOverride: String? = null,
        gpsLat: Float? = null,
        gpsLon: Float? = null,
        timeZone: TimeZone = TimeZone.getDefault()
    ): UserTimeZoneRegionInfo {
        val locTarget = customLocationOverride?.trim()?.lowercase(Locale.US)
        val matchedByLoc = if (!locTarget.isNullOrBlank()) {
            KNOWN_REGIONS.firstOrNull { entry ->
                entry.keywords.any { kw -> locTarget.contains(kw.lowercase(Locale.US)) }
            }
        } else null

        val effectiveTz: TimeZone = when {
            !customTimeZoneId.isNullOrBlank() && customTimeZoneId != "AUTO" -> TimeZone.getTimeZone(customTimeZoneId)
            matchedByLoc != null -> TimeZone.getTimeZone(matchedByLoc.timeZoneId)
            else -> timeZone
        }

        val now = java.util.Date()
        val inDaylight = effectiveTz.inDaylightTime(now)
        val shortTz = effectiveTz.getDisplayName(inDaylight, TimeZone.SHORT, Locale.getDefault())
        val longTz = effectiveTz.getDisplayName(false, TimeZone.LONG, Locale.getDefault())
        val offsetMillis = effectiveTz.getOffset(now.time)
        val offsetHours = offsetMillis / 3_600_000f
        val offsetSign = if (offsetMillis >= 0) "+" else "-"
        val absMinutes = abs(offsetMillis / 60_000)
        val utcOffsetLabel = String.format(Locale.US, "UTC%s%02d:%02d", offsetSign, absMinutes / 60, absMinutes % 60)

        val tzIdLower = effectiveTz.id.lowercase(Locale.US)
        val tzCityRaw = effectiveTz.id.substringAfterLast("/").replace("_", " ")
        val searchTarget = (customLocationOverride?.takeIf { it.isNotBlank() }
            ?: gpsCityOverride?.takeIf { it.isNotBlank() && (customTimeZoneId.isNullOrBlank() || customTimeZoneId == "AUTO") }
            ?: "$tzIdLower $longTz").lowercase(Locale.US)

        val matchedByKeyword = matchedByLoc ?: KNOWN_REGIONS.firstOrNull { entry ->
            entry.keywords.any { kw -> searchTarget.contains(kw.lowercase(Locale.US)) }
        }
        val matchedByOffset = KNOWN_REGIONS.minByOrNull { entry ->
            abs(entry.utcOffsetHours - offsetHours)
        } ?: KNOWN_REGIONS.first()

        val chosen = matchedByKeyword ?: matchedByOffset

        val finalRegionName = when {
            !customLocationOverride.isNullOrBlank() -> customLocationOverride.substringBefore("(").trim()
            !customTimeZoneId.isNullOrBlank() && customTimeZoneId != "AUTO" -> {
                if (tzCityRaw.isNotBlank() && !tzCityRaw.equals("UTC", ignoreCase = true) && !tzCityRaw.equals("GMT", ignoreCase = true)) {
                    tzCityRaw
                } else {
                    chosen.displayName
                }
            }
            !gpsCityOverride.isNullOrBlank() -> gpsCityOverride.trim()
            tzCityRaw.isNotBlank() &&
                !tzCityRaw.equals("GMT", ignoreCase = true) &&
                !tzCityRaw.equals("UTC", ignoreCase = true) &&
                !tzCityRaw.startsWith("GMT+", ignoreCase = true) &&
                !tzCityRaw.startsWith("GMT-", ignoreCase = true) -> tzCityRaw
            else -> chosen.displayName
        }

        val finalAirportCity = finalRegionName.uppercase(Locale.US)
            .substringBefore("(")
            .trim()
            .take(12)
            .ifBlank { chosen.airportCity }

        val finalAirportCode = if (!customLocationOverride.isNullOrBlank() && customLocationOverride.contains("(")) {
            customLocationOverride.substringAfter("(").substringBefore(")").trim().uppercase(Locale.US).take(3)
                .ifBlank { chosen.airportCode }
        } else {
            chosen.airportCode
        }

        val useGpsCoords = customLocationOverride.isNullOrBlank() && (customTimeZoneId.isNullOrBlank() || customTimeZoneId == "AUTO")
        val finalLat = if (useGpsCoords && gpsLat != null) gpsLat else chosen.lat
        val finalLon = if (useGpsCoords && gpsLon != null) gpsLon else chosen.lon

        return UserTimeZoneRegionInfo(
            regionName = finalRegionName,
            airportCityName = finalAirportCity,
            airportCode = finalAirportCode,
            timeZoneId = effectiveTz.id,
            standardTimeShort = shortTz,
            standardTimeLong = longTz,
            utcOffsetLabel = utcOffsetLabel,
            latitude = finalLat,
            longitude = finalLon
        )
    }

    /**
     * Detects the user's real location city name if location permission is granted.
     */
    @Suppress("DEPRECATION")
    fun detectDeviceLocation(context: Context): Triple<String, Float, Float>? {
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) return null

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLocation: Location? = null
            for (provider in providers) {
                try {
                    if (lm.isProviderEnabled(provider)) {
                        val loc = lm.getLastKnownLocation(provider)
                        if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                            bestLocation = loc
                        }
                    }
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }

            val loc = bestLocation ?: return null
            val lat = loc.latitude.toFloat()
            val lon = loc.longitude.toFloat()

            var cityName: String? = null
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    val first = addresses?.firstOrNull()
                    cityName = first?.locality ?: first?.subAdminArea ?: first?.adminArea ?: first?.countryName
                }
            } catch (_: Exception) {
            }

            if (cityName.isNullOrBlank()) {
                val nearest = KNOWN_REGIONS.minByOrNull { entry ->
                    val dLat = entry.lat - lat
                    val dLon = entry.lon - lon
                    dLat * dLat + dLon * dLon
                }
                cityName = nearest?.displayName ?: "Local Terminal"
            }

            Triple(cityName, lat, lon)
        } catch (_: Exception) {
            null
        }
    }

    fun detectConnectedGoogleAccount(context: Context): String {
        val prefs = context.getSharedPreferences("standby_prefs", Context.MODE_PRIVATE)
        val savedAccount = prefs.getString("google_connected_email", null)
        if (!savedAccount.isNullOrBlank()) return savedAccount

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            try {
                val projection = arrayOf(
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE
                )
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                    val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                    while (cursor.moveToNext()) {
                        val accName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val accType = if (typeIdx >= 0) cursor.getString(typeIdx) else null
                        if (!accName.isNullOrBlank() && (accType?.contains("google", ignoreCase = true) == true || accName.contains("@"))) {
                            prefs.edit().putString("google_connected_email", accName).apply()
                            return accName
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return "Google Home Linked"
    }

    fun openGoogleHomeApp(context: Context): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun readLiveBatteryDevices(context: Context, batteryIntent: Intent?): List<GoogleConnectedBatteryDevice> {
        val sticky = batteryIntent ?: try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val computedPct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
        } else {
            val prop = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            prop.coerceIn(0, 100)
        }

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val isPhoneCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0 ||
            (bm?.isCharging == true)

        val plugSourceLabel = when {
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless Charging"
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "Fast Charging"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB Charging"
            isPhoneCharging -> "Charging"
            else -> "On Battery"
        }

        val rawModel = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Pixel Phone"
        val phoneName = if (rawModel.lowercase(Locale.US).contains("sdk") || rawModel.lowercase(Locale.US).contains("gphone")) {
            "Pixel Phone"
        } else {
            rawModel.take(14)
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val outputDevices = try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        val bluetoothOrUsbPeripherals = outputDevices.filter { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }

        val primaryAudioPeripheral = bluetoothOrUsbPeripherals.firstOrNull()
        val peripheralName = primaryAudioPeripheral?.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Pixel Buds"
        val isBudsConnected = primaryAudioPeripheral != null

        val result = mutableListOf<GoogleConnectedBatteryDevice>()

        result.add(
            GoogleConnectedBatteryDevice(
                id = "google_phone",
                name = phoneName,
                batteryPercent = computedPct,
                isCharging = isPhoneCharging,
                isConnected = true,
                statusLabel = "$computedPct% • $plugSourceLabel",
                icon = Icons.Default.Smartphone
            )
        )

        result.add(
            GoogleConnectedBatteryDevice(
                id = "pixel_watch",
                name = "Pixel Watch",
                batteryPercent = if (isPhoneCharging) 100 else 0,
                isCharging = false,
                isConnected = false,
                statusLabel = "Google Wear OS",
                icon = Icons.Default.Watch
            )
        )

        result.add(
            GoogleConnectedBatteryDevice(
                id = "pixel_buds",
                name = peripheralName.take(14),
                batteryPercent = if (isBudsConnected) 100 else 0,
                isCharging = false,
                isConnected = isBudsConnected,
                statusLabel = if (isBudsConnected) "Connected • Google Audio" else "Fast Pair Standby",
                icon = Icons.Default.Headphones
            )
        )

        result.add(
            GoogleConnectedBatteryDevice(
                id = "pixel_stand",
                name = "Pixel Stand",
                batteryPercent = if (isPhoneCharging) computedPct else 0,
                isCharging = isPhoneCharging,
                isConnected = isPhoneCharging,
                statusLabel = if (isPhoneCharging) "Active • Docked" else "Unplugged",
                icon = Icons.Default.BatteryChargingFull
            )
        )

        return result
    }
}

@Composable
fun rememberGoogleBatteryDevices(): List<GoogleConnectedBatteryDevice> {
    val context = LocalContextCompat()
    var latestIntent by remember { mutableStateOf<Intent?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    latestIntent = intent
                }
                tick++
            }
        }
        try {
            latestIntent = context.registerReceiver(receiver, filter)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    return remember(latestIntent, tick) {
        GoogleDeviceAndHomeMonitor.readLiveBatteryDevices(context, latestIntent)
    }
}

@Composable
fun rememberGoogleHomeDevices(viewModel: StandbyViewModel): List<GoogleHomeDeviceItem> {
    val context = LocalContextCompat()
    val googleHomeStates by viewModel.googleHomeDevices.collectAsState()

    DisposableEffect(context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        var listener: NsdManager.DiscoveryListener? = null
        if (nsdManager != null) {
            val discListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String?) {}
                override fun onServiceFound(service: NsdServiceInfo?) {
                    val rawName = service?.serviceName ?: return
                    viewModel.onGoogleCastDeviceDiscovered(rawName)
                }
                override fun onServiceLost(service: NsdServiceInfo?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
            }
            listener = discListener
            try {
                nsdManager.discoverServices("_googlecast._tcp.", NsdManager.PROTOCOL_DNS_SD, discListener)
            } catch (_: Exception) {
            }
        }
        onDispose {
            if (nsdManager != null && listener != null) {
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: Exception) {
                }
            }
        }
    }

    return googleHomeStates
}

@Composable
private fun LocalContextCompat(): Context {
    return androidx.compose.ui.platform.LocalContext.current
}
