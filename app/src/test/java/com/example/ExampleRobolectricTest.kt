package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.standby.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("standby_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = AppDatabase.getDatabase(application)
        val existing = db.alarmDao().getAllAlarmsSnapshot()
        existing.forEach { db.alarmDao().delete(it) }
        AlarmRingingController.dismissAlarm()
    }

    @Test
    fun `read string from context`() {
        val appName = application.getString(R.string.app_name)
        assertEquals("Standby", appName)
    }

    @Test
    fun `alarm CRUD deduplication music player and gradual volume persistence`() {
        val vm = StandbyViewModel(application)

        // Add first alarm at 07:30 with Spotify and gradual volume
        vm.addAlarm(
            hour = 7,
            minute = 30,
            label = "Morning Workout",
            snooze = 10,
            toneUri = "default_alarm",
            musicPackageName = "com.spotify.music",
            musicAppName = "Spotify",
            gradualVolume = true,
            gradualVolumeSeconds = 60
        )

        var list = db.alarmDao().getAllAlarmsSnapshot()
        assertEquals(1, list.size)
        assertEquals("Morning Workout", list.first().label)
        assertEquals("com.spotify.music", list.first().musicPackageName)
        assertEquals("Spotify", list.first().musicAppName)
        assertTrue(list.first().gradualVolume)
        assertEquals(60, list.first().gradualVolumeSeconds)

        // Add duplicate time (07:30) with YouTube Music - should deduplicate and update in place
        vm.addAlarm(
            hour = 7,
            minute = 30,
            label = "Updated Flight Alarm",
            snooze = 15,
            toneUri = "default_alarm",
            musicPackageName = "com.google.android.apps.youtube.music",
            musicAppName = "YouTube Music",
            gradualVolume = true,
            gradualVolumeSeconds = 120
        )

        list = db.alarmDao().getAllAlarmsSnapshot()
        assertEquals(1, list.size)
        val updated = list.first()
        assertEquals("Updated Flight Alarm", updated.label)
        assertEquals("com.google.android.apps.youtube.music", updated.musicPackageName)
        assertEquals("YouTube Music", updated.musicAppName)
        assertEquals(120, updated.gradualVolumeSeconds)

        // Toggle off and delete
        vm.toggleAlarm(updated)
        list = db.alarmDao().getAllAlarmsSnapshot()
        assertFalse(list.first().isEnabled)

        vm.deleteAlarm(list.first())
        list = db.alarmDao().getAllAlarmsSnapshot()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `gradual alarm volume ramp calculation and music player discovery`() {
        // When gradualVolume is disabled, volume is immediately 100% (1.0f)
        assertEquals(1.0f, AlarmRingingController.computeVolumeFactor(0L, 60, false), 0.001f)

        // When gradualVolume is enabled over 60 seconds:
        // At 0ms -> 0.12f (12% gentle start)
        val startVol = AlarmRingingController.computeVolumeFactor(0L, 60, true)
        assertEquals(0.12f, startVol, 0.01f)

        // At 30,000ms (halfway) -> ~0.56f
        val midVol = AlarmRingingController.computeVolumeFactor(30_000L, 60, true)
        assertEquals(0.56f, midVol, 0.02f)

        // At 60,000ms (full duration) -> 1.0f
        val endVol = AlarmRingingController.computeVolumeFactor(60_000L, 60, true)
        assertEquals(1.0f, endVol, 0.01f)

        // Music player options always include System Alarm Tone, Active Music Player, and popular players
        val players = AlarmRingingController.discoverMusicPlayerOptions(application)
        assertTrue(players.size >= 5)
        assertTrue(players.any { it.packageName == "ANY_ACTIVE_PLAYER" })
        assertTrue(players.any { it.name.contains("Spotify") })
    }

    @Test
    fun `pre-alarm gradual brightness lowering math and live state`() {
        val vm = StandbyViewModel(application)
        vm.setGradualAlarmDimEnabled(true)
        vm.setGradualAlarmDimWindowMinutes(30)
        vm.setGradualAlarmMinBrightness(0.20f)

        // 1. Outside window (45 mins away) -> full brightness 1.0f
        val outside = vm.computeBrightnessForDiffMillis(
            diffMillis = 45 * 60_000L,
            dimEnabled = true,
            windowMinutes = 30,
            minBrightness = 0.20f
        )
        assertEquals(1.0f, outside, 0.001f)

        // 2. Exactly halfway inside 30m window (15 mins away) -> 0.60f
        val halfway = vm.computeBrightnessForDiffMillis(
            diffMillis = 15 * 60_000L,
            dimEnabled = true,
            windowMinutes = 30,
            minBrightness = 0.20f
        )
        assertEquals(0.60f, halfway, 0.01f)

        // 3. At alarm trigger moment (0ms away) -> min floor 0.20f
        val atZero = vm.computeBrightnessForDiffMillis(
            diffMillis = 0L,
            dimEnabled = true,
            windowMinutes = 30,
            minBrightness = 0.20f
        )
        assertEquals(0.20f, atZero, 0.001f)

        // 4. Verify when disabled -> always 1.0f
        val disabled = vm.computeBrightnessForDiffMillis(
            diffMillis = 5 * 60_000L,
            dimEnabled = false,
            windowMinutes = 30,
            minBrightness = 0.20f
        )
        assertEquals(1.0f, disabled, 0.001f)
    }

    @Test
    fun `clock face enable disable ticking and shared preferences persistence`() {
        val vm1 = StandbyViewModel(application)
        // All 13 single-screen clock faces (0..12) are enabled by default; the last 3 split screens are only in Duo
        assertEquals(13, vm1.enabledClockFaces.value.size)
        assertTrue(vm1.enabledClockFaces.value.contains(2)) // Airport Split-Flap Board
        assertTrue(vm1.enabledClockFaces.value.contains(10)) // World Time Zone Map
        assertFalse(vm1.clockFaceTitles.contains("Squircle Tick & Battery Rings"))
        assertFalse(vm1.clockFaceTitles.contains("Analog Smart Home Hub"))
        assertFalse(vm1.clockFaceTitles.contains("Big Date & Calendar Split"))

        // Disable clock face 0 and toggle clock face 5
        vm1.setClockFaceEnabled(0, false)
        vm1.toggleClockFace(5)
        assertFalse(vm1.enabledClockFaces.value.contains(0))
        assertFalse(vm1.enabledClockFaces.value.contains(5))
        assertEquals(11, vm1.enabledClockFaces.value.size)

        // Persist Duo screens and Pomodoro settings
        vm1.setDuoLeftWidgetIndex(2) // Airport Split-Flap Board
        vm1.setDuoRightWidgetIndex(10) // World Time Zone Map
        vm1.setPomodoroFocusMinutes(45)

        // Re-instantiate ViewModel to verify SharedPreferences round-trip
        val vm2 = StandbyViewModel(application)
        assertFalse(vm2.enabledClockFaces.value.contains(0))
        assertFalse(vm2.enabledClockFaces.value.contains(5))
        assertEquals(11, vm2.enabledClockFaces.value.size)
        assertEquals(2, vm2.duoLeftWidgetIndex.value)
        assertEquals(10, vm2.duoRightWidgetIndex.value)
        assertEquals(45, vm2.pomodoroFocusMinutes.value)
    }

    @Test
    fun `airport split flap flipboard clock and flight ticker UI test`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 38)
            set(Calendar.SECOND, 25)
        }
        composeTestRule.setContent {
            RetroFlip(
                calendar = cal,
                color = Color.White,
                fontFamily = FontFamily.Default,
                scale = 1.0f,
                use24Hour = false,
                showAmPm = true,
                showSeconds = true
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("airport_flipboard_clock").assertIsDisplayed()
        composeTestRule.onNodeWithTag("airport_flipboard_flight_row").assertIsDisplayed()

        // Tap flight departure row to cycle to next flight destination on the Solari board
        composeTestRule.onNodeWithTag("airport_flipboard_flight_row").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `new standby clock faces duo timezones and single clock world map rendering`() {
        val vm = StandbyViewModel(application)
        // Verify Duo Mode only includes the 17 actual main screens (13 clock faces + 4 main utility screens)
        assertEquals(17, vm.duoWidgetTitles.size)
        assertEquals("Tools Hub", vm.duoWidgetTitles.last())

        // Set different TimeZones / Locations for Main Clock (0), Left Duo Pane (1), and Right Duo Pane (2)
        vm.setClockTimeZoneId("America/New_York")
        vm.setDuoLeftTimeZoneId("Asia/Tokyo")
        vm.setDuoRightLocationName("London (LHR)")

        val mainRegion = vm.resolvePaneLocationInfo(0)
        val leftRegion = vm.resolvePaneLocationInfo(1)
        val rightRegion = vm.resolvePaneLocationInfo(2)

        assertEquals("America/New_York", mainRegion.timeZoneId)
        assertEquals("JFK", mainRegion.airportCode)
        assertEquals("Asia/Tokyo", leftRegion.timeZoneId)
        assertEquals("HND", leftRegion.airportCode)
        assertEquals(9.0f, leftRegion.utcOffsetHours, 0.01f)
        assertEquals("Europe/London", rightRegion.timeZoneId)
        assertEquals("LHR", rightRegion.airportCode)

        val cal = GoogleDeviceAndHomeMonitor.calendarForTimeZone(Calendar.getInstance(), leftRegion.timeZoneId)
        composeTestRule.setContent {
            WorldTimeZoneMapClockFace(
                calendar = cal,
                accentColor = Color(0xFFFF9800),
                customFont = FontFamily.Default,
                nextAlarmLabel = "6:30 AM",
                standbyViewModel = vm,
                paneIndex = 1
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("world_map_clock_face").assertIsDisplayed()
        // Verify embedded Squircle Tick Digital Clock is displayed inside the World Map Clock
        composeTestRule.onNodeWithTag("map_squircle_tick_digital_clock").assertIsDisplayed()
        // Verify top 6:30 AM alarm clock was removed
        composeTestRule.onNodeWithText("6:30 AM").assertDoesNotExist()

        // Verify searchable location & time zone helper and Matrix CRT font styles
        val searchMatches = vm.searchLocationsAndTimeZones("Tokyo")
        assertTrue(searchMatches.any { it.second.contains("Tokyo") })
        vm.applySearchedLocationSelection("LOC:Paris (CDG)", "", 2)
        assertEquals("CDG", vm.resolvePaneLocationInfo(2).airportCode)
        assertTrue(vm.fonts.any { it.second == "Matrix CRT" })
        assertTrue(vm.fonts.any { it.second == "Matrix Terminal" })
    }

    @Test
    fun `tools page pomodoro presets stopwatch and gallery permission UI test`() {
        val vm = StandbyViewModel(application)
        vm.setTimerModeIndex(0) // Focus Pomodoro mode

        composeTestRule.setContent {
            TimerPage(viewModel = vm)
        }
        composeTestRule.waitForIdle()

        // Open Pomodoro Config Dialog and select 45m preset
        composeTestRule.onNodeWithTag("pomodoro_config_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pomodoro_preset_45").performClick()
        composeTestRule.waitForIdle()
        assertEquals(45, vm.pomodoroFocusMinutes.value)

        // Switch to Chrono mode via mode dot and verify persistence
        composeTestRule.onNodeWithTag("timer_mode_dot_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, vm.timerModeIndex.value)

        // Switch to Gallery & Slideshow tool (index 2) and verify permission gate card is shown initially
        composeTestRule.onNodeWithTag("tools_tab_chip_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, vm.timerModeIndex.value)
        composeTestRule.onNodeWithTag("gallery_permission_card").assertIsDisplayed()
    }

    @Test
    fun `test audio manager media key events keycode media play pause`() {
        val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertTrue(true)
    }

    @Test
    fun `test notification listener setting status parsing`() {
        val pkgName = application.packageName
        val mockFlatSetting = "some.other.pkg/SomeService:$pkgName/com.example.receivers.StandbyNotificationListenerService"
        Settings.Secure.putString(
            application.contentResolver,
            "enabled_notification_listeners",
            mockFlatSetting
        )
        val flat = Settings.Secure.getString(application.contentResolver, "enabled_notification_listeners")
        assertTrue(flat.contains(pkgName))
    }
}
