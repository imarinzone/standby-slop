package com.example.ui.standby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

data class UserTimeZoneRegionInfo(
    val regionName: String,
    val airportCityName: String,
    val airportCode: String,
    val timeZoneId: String,
    val standardTimeShort: String,
    val standardTimeLong: String,
    val utcOffsetLabel: String,
    val utcOffsetHours: Float,
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
            utcOffsetHours = offsetHours,
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
}
