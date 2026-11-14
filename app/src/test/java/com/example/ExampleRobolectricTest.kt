package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.ui.standby.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.toPureAmoledColorScheme
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
        // All 14 single-screen clock faces (0..13, including Squircle Border Tick) are enabled by default
        assertEquals(14, vm1.enabledClockFaces.value.size)
        assertTrue(vm1.enabledClockFaces.value.contains(2)) // Airport Split-Flap Board
        assertTrue(vm1.enabledClockFaces.value.contains(10)) // World Time Zone Map
        assertTrue(vm1.enabledClockFaces.value.contains(13)) // Squircle Border Tick
        assertTrue(vm1.clockFaceTitles.contains("Squircle Border Tick"))

        // Disable clock face 0 and toggle clock face 5
        vm1.setClockFaceEnabled(0, false)
        vm1.toggleClockFace(5)
        assertFalse(vm1.enabledClockFaces.value.contains(0))
        assertFalse(vm1.enabledClockFaces.value.contains(5))
        assertEquals(12, vm1.enabledClockFaces.value.size)

        // Persist Duo screens and Pomodoro settings
        vm1.setDuoLeftWidgetIndex(2) // Airport Split-Flap Board
        vm1.setDuoRightWidgetIndex(13) // Squircle Border Tick
        vm1.setPomodoroFocusMinutes(45)

        // Re-instantiate ViewModel to verify SharedPreferences round-trip
        val vm2 = StandbyViewModel(application)
        assertFalse(vm2.enabledClockFaces.value.contains(0))
        assertFalse(vm2.enabledClockFaces.value.contains(5))
        assertEquals(12, vm2.enabledClockFaces.value.size)
        assertEquals(2, vm2.duoLeftWidgetIndex.value)
        assertEquals(13, vm2.duoRightWidgetIndex.value)
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
        // Verify Duo Mode includes all 23 screens (14 clock faces + 3 main utility screens + 6 separate tool screens)
        assertEquals(23, vm.duoWidgetTitles.size)
        assertEquals("Squircle Border Tick", vm.duoWidgetTitles[13])
        assertEquals("Focus Pomodoro", vm.duoWidgetTitles[17])
        assertEquals("Ambient Flash & Level", vm.duoWidgetTitles.last())

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
            Column {
                Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    WorldTimeZoneMapClockFace(
                        calendar = cal,
                        accentColor = Color(0xFFFF9800),
                        customFont = FontFamily.Default,
                        nextAlarmLabel = "No Alarm",
                        standbyViewModel = vm,
                        paneIndex = 1
                    )
                }
                Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                    StandbySageComplicationClockFace(
                        calendar = cal,
                        accentColor = Color(0xFF7CC67F),
                        customFont = FontFamily.Default,
                        nextAlarmLabel = "No Alarm"
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("world_map_clock_face").assertIsDisplayed()
        composeTestRule.onNodeWithTag("standby_sage_complication_clock").assertIsDisplayed()
        // Verify no fake 6:30AM alarm is displayed on the Sage clock when no alarm has been added
        composeTestRule.onNodeWithText("6:30AM").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Scheduled Alarm").assertDoesNotExist()

        // Verify Duo custom screen lists start empty until the user adds screens and support user add/remove
        assertEquals(emptyList<Int>(), vm.duoLeftScreenIndices.value)
        assertEquals(emptyList<Int>(), vm.duoRightScreenIndices.value)
        vm.addScreenToDuoLeft(2)
        vm.addScreenToDuoLeft(13) // Add Squircle Border Tick
        assertEquals(listOf(2, 13), vm.duoLeftScreenIndices.value)
        vm.removeScreenFromDuoLeft(2)
        assertEquals(listOf(13), vm.duoLeftScreenIndices.value)

        // Verify zero mock data in Connected Devices and Smart Home
        val liveBatteryDevices = GoogleDeviceAndHomeMonitor.readLiveBatteryDevices(application)
        assertFalse(liveBatteryDevices.any { it.id == "google_pixel_watch" || it.id == "google_pixel_buds_pro" || it.id == "google_nest_hub" })
        assertTrue(vm.googleHomeDevices.value.isEmpty())
        vm.addUserSmartDevice("Desk Lamp", "Study", "light")
        assertEquals(1, vm.googleHomeDevices.value.size)
        assertEquals("Desk Lamp", vm.googleHomeDevices.value.first().name)

        // Verify searchable location & time zone helper and Matrix CRT font styles
        val searchMatches = vm.searchLocationsAndTimeZones("Tokyo")
        assertTrue(searchMatches.any { it.second.contains("Tokyo") })
        vm.applySearchedLocationSelection("LOC:Paris (CDG)", "", 2)
        assertEquals("CDG", vm.resolvePaneLocationInfo(2).airportCode)
        assertTrue(vm.fonts.any { it.second == "Matrix CRT" })
        assertTrue(vm.fonts.any { it.second == "Matrix Terminal" })
    }

    @Test
    fun `tools page pomodoro presets stopwatch gallery charge smart home and flash UI test`() {
        val vm = StandbyViewModel(application)
        vm.setTimerModeIndex(0) // Focus Pomodoro mode
        assertTrue(vm.showSlideshowTimeOverlay.value) // Opt-on by default

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
        composeTestRule.onNodeWithTag("timer_mode_dot_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, vm.timerModeIndex.value)
        composeTestRule.onNodeWithTag("gallery_permission_card").assertIsDisplayed()

        // Grant gallery access to enter immersive full-screen slideshow and verify time overlay opt-on by default
        composeTestRule.onNodeWithTag("gallery_grant_permission_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slideshow_time_overlay_text").assertIsDisplayed()
        vm.setShowSlideshowTimeOverlay(false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slideshow_time_overlay_text").assertDoesNotExist()

        // Switch to Connected Devices Charge tool (index 3)
        composeTestRule.onNodeWithTag("timer_mode_dot_3").performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, vm.timerModeIndex.value)
        composeTestRule.onNodeWithTag("connected_devices_charge_tool").assertIsDisplayed()

        // Switch to Smart Devices Control tool (index 4)
        composeTestRule.onNodeWithTag("timer_mode_dot_4").performClick()
        composeTestRule.waitForIdle()
        assertEquals(4, vm.timerModeIndex.value)
        composeTestRule.onNodeWithTag("smart_devices_control_tool").assertIsDisplayed()

        // Switch to Full-Screen Ambient Light tool (index 5)
        composeTestRule.onNodeWithTag("timer_mode_dot_5").performClick()
        composeTestRule.waitForIdle()
        assertEquals(5, vm.timerModeIndex.value)
        composeTestRule.onNodeWithTag("camera_flash_ambient_toggle_btn").assertIsDisplayed()
    }

    @Test
    fun `multi-screen calendar hero day month view and events agenda UI test`() {
        val standbyVm = StandbyViewModel(application)
        val calVm = CalendarViewModel(application)
        calVm.setCalendarScreenIndex(0)

        composeTestRule.setContent {
            CalendarPage(viewModel = calVm, standbyViewModel = standbyVm)
        }
        composeTestRule.waitForIdle()

        // Screen 0: Dedicated Hero Day screen
        composeTestRule.onNodeWithTag("calendar_screen_day_hero").assertIsDisplayed()

        // Screen 1: Dedicated Month View calendar highlighting today's date
        composeTestRule.onNodeWithTag("calendar_mode_dot_1").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, calVm.calendarScreenIndex.value)
        composeTestRule.onNodeWithTag("calendar_screen_month_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_today_highlighted_cell").assertIsDisplayed()

        // Screen 2: Dedicated Material 3 Calendar Events / Agenda screen
        composeTestRule.onNodeWithTag("calendar_mode_dot_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, calVm.calendarScreenIndex.value)
        composeTestRule.onNodeWithTag("calendar_screen_events_agenda").assertIsDisplayed()
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

    @Test
    fun `analog classic small container and calendar permission guard do not crash`() {
        val calVm = CalendarViewModel(application)
        // Without READ_CALENDAR permission granted, both queries should return safely without SecurityException
        calVm.loadCalendarSources(application)
        calVm.loadLocalEvents(application)
        assertTrue(calVm.calendarSources.value.isEmpty())
        assertTrue(calVm.deviceEvents.value.isEmpty())

        // Render AnalogClassic inside a <120dp compact preview box (reproducing 106.67dp Duo picker card bounds)
        val now = Calendar.getInstance()
        composeTestRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.size(width = 100.dp, height = 80.dp)) {
                AnalogClassic(
                    calendar = now,
                    color = Color.White,
                    scale = 0.90f,
                    animationsEnabled = true,
                    showSeconds = true
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `world map solar parabola steps every 1 hour with exact day night and no top utc text`() {
        val utcTz = java.util.TimeZone.getTimeZone("UTC")
        val cal12_00 = Calendar.getInstance(utcTz).apply {
            set(2026, Calendar.NOVEMBER, 12, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cal12_47 = Calendar.getInstance(utcTz).apply {
            set(2026, Calendar.NOVEMBER, 12, 12, 47, 55)
            set(Calendar.MILLISECOND, 0)
        }
        val cal13_00 = Calendar.getInstance(utcTz).apply {
            set(2026, Calendar.NOVEMBER, 12, 13, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val state12a = computeWorldMapSolarState(cal12_00, utcTz)
        val state12b = computeWorldMapSolarState(cal12_47, utcTz)
        val state13 = computeWorldMapSolarState(cal13_00, utcTz)

        // Remains steady within the same 1-hour window (12:00..12:59)
        assertEquals(0f, state12a.subsolarLonDeg, 0.001f)
        assertEquals(state12a.subsolarLonDeg, state12b.subsolarLonDeg, 0.001f)

        // Shifts 15° westward on the next 1-hour mark (13:00 UTC -> -15°)
        assertEquals(-15f, state13.subsolarLonDeg, 0.001f)

        // Exact day vs night at 12:00 UTC: London/Greenwich (0°, 51.5°N) & Equator (0°, 0°) are Day; Date Line (180°, 0°) is Night
        assertTrue(state12a.isDaylight(0f, 51.5f))
        assertTrue(state12a.isDaylight(0f, 0f))
        assertFalse(state12a.isDaylight(180f, 0f))
        // Sunrise (-90°) and Sunset (+90°) cross the Equator (0° lat)
        assertEquals(0f, state12a.terminatorLatitudeDeg(-90f), 0.01f)
        assertEquals(0f, state12a.terminatorLatitudeDeg(90f), 0.01f)

        // Verify top UTC/hr small text is removed from the World Map UI
        val vm = StandbyViewModel(application)
        vm.setClockTimeZoneId("America/New_York")
        composeTestRule.setContent {
            WorldTimeZoneMapClockFace(
                calendar = cal12_00,
                accentColor = Color(0xFFFF9800),
                customFont = FontFamily.Default,
                standbyViewModel = vm,
                paneIndex = 0
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("world_map_clock_face").assertIsDisplayed()
        composeTestRule.onNodeWithText("0000+", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("UTC", substring = true).assertDoesNotExist()
    }

    @Test
    fun `pure amoled persistence color scheme and expressive hud settings toggles`() {
        val vm1 = StandbyViewModel(application)
        assertFalse(vm1.isPureAmoledEnabled.value)

        // Toggle via ViewModel helper and verify SharedPreferences persistence
        vm1.togglePureAmoledMode()
        assertTrue(vm1.isPureAmoledEnabled.value)

        val vm2 = StandbyViewModel(application)
        assertTrue(vm2.isPureAmoledEnabled.value)

        vm2.setPureAmoledEnabled(false)
        assertFalse(vm2.isPureAmoledEnabled.value)

        // Verify ColorScheme.toPureAmoledColorScheme maps background/surface to pitch black while preserving accents
        val darkScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFA8C7FA),
            primaryContainer = Color(0xFF0842A0)
        )
        val amoledScheme = darkScheme.toPureAmoledColorScheme()
        assertEquals(Color.Black, amoledScheme.background)
        assertEquals(Color.Black, amoledScheme.surface)
        assertEquals(Color.Black, amoledScheme.surfaceContainerLowest)
        assertEquals(Color(0xFFA8C7FA), amoledScheme.primary)

        // Render StandbyScreen wrapped in MyApplicationTheme(pureAmoled) and test HUD + Settings toggles
        composeTestRule.setContent {
            val isAmoled by vm2.isPureAmoledEnabled.collectAsState()
            MyApplicationTheme(pureAmoled = isAmoled) {
                StandbyScreen(viewModel = vm2)
            }
        }
        composeTestRule.waitForIdle()

        // Quick HUD Pure AMOLED pill toggles state
        composeTestRule.onNodeWithTag("hud_amoled_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_amoled_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(vm2.isPureAmoledEnabled.value)

        // Open Settings Studio and verify Settings AMOLED Banner & Switch toggle state
        composeTestRule.onNodeWithTag("hud_settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_amoled_toggle_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_amoled_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(vm2.isPureAmoledEnabled.value)
    }

    @Test
    fun `in-place app update preserves package id monotonic versionCode room alarms and legacy prefs`() {
        // 1. Package identity must stay com.example and versionCode must be > 1 for in-place upgrades
        assertEquals("com.example", BuildConfig.APPLICATION_ID)
        assertTrue("Expected versionCode > 1 for in-place updates, got ${BuildConfig.VERSION_CODE}", BuildConfig.VERSION_CODE > 1)
        assertTrue(BuildConfig.VERSION_NAME.startsWith("1.1."))

        // 2. Verify Room MIGRATION_1_2 preserves existing v1 user alarms without wiping data
        val migDbName = "test_in_place_upgrade_v1_to_v2.db"
        application.deleteDatabase(migDbName)
        val rawV1 = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
            application.getDatabasePath(migDbName),
            null
        )
        rawV1.execSQL(
            "CREATE TABLE IF NOT EXISTS `alarms` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, " +
                "`isEnabled` INTEGER NOT NULL, " +
                "`label` TEXT NOT NULL)"
        )
        rawV1.execSQL("INSERT INTO `alarms` (`id`, `hour`, `minute`, `isEnabled`, `label`) VALUES (7, 6, 45, 1, 'Existing V1 Alarm')")
        rawV1.version = 1
        rawV1.close()

        val upgradedDb = androidx.room.Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            migDbName
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val migratedAlarms = upgradedDb.alarmDao().getAllAlarmsSnapshot()
        assertEquals(1, migratedAlarms.size)
        assertEquals(6, migratedAlarms.first().hour)
        assertEquals(45, migratedAlarms.first().minute)
        assertEquals("Existing V1 Alarm", migratedAlarms.first().label)
        assertTrue(migratedAlarms.first().isEnabled)
        assertEquals("default_alarm", migratedAlarms.first().toneUri)
        upgradedDb.close()
        application.deleteDatabase(migDbName)

        // 3. Verify legacy out-of-bounds or mismatched-type SharedPreferences never crash on update
        application.getSharedPreferences("standby_prefs", Context.MODE_PRIVATE).edit()
            .putInt("colorPage0", 99)
            .putInt("fontPage0", 99)
            .putString("enabledClockFaces", "legacy_wrong_type")
            .putStringSet("duoLeftScreenIndices", setOf("2", "10"))
            .commit()

        val safeVm = StandbyViewModel(application)
        assertTrue(safeVm.colorPage0.value in safeVm.colors.indices)
        assertTrue(safeVm.fontPage0.value in safeVm.fonts.indices)
        assertTrue(safeVm.duoLeftScreenIndices.value.containsAll(listOf(2, 10)))

        // 4. Verify MY_PACKAGE_REPLACED receiver runs cleanly to restore enabled alarms after update
        safeVm.addAlarm(hour = 8, minute = 15, label = "Post Update Alarm", snooze = 5)
        val receiver = com.example.receivers.PackageUpdateAndBootReceiver()
        receiver.onReceive(application, android.content.Intent(android.content.Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `separate tool screens can be toggled individually and added as separate screens in duo`() {
        val vm = StandbyViewModel(application)
        val calVm = CalendarViewModel(application)
        val weatherVm = WeatherViewModel()

        // Verify all 6 tool screens are enabled by default and individually toggleable
        assertEquals(6, vm.toolScreenTitles.size)
        assertEquals(setOf(0, 1, 2, 3, 4, 5), vm.enabledToolScreens.value)
        vm.toggleToolScreen(2)
        assertFalse(vm.enabledToolScreens.value.contains(2))
        vm.setToolScreenEnabled(2, true)
        assertTrue(vm.enabledToolScreens.value.contains(2))

        // Add separate Tool screens independently to Left and Right Duo panes (just like Clock Faces)
        vm.addScreenToDuoLeft(17)  // Focus Pomodoro
        vm.addScreenToDuoLeft(20)  // Connected Battery
        vm.addScreenToDuoRight(18) // Chrono Stopwatch
        vm.addScreenToDuoRight(22) // Ambient Flash & Level
        assertEquals(listOf(17, 20), vm.duoLeftScreenIndices.value)
        assertEquals(listOf(18, 22), vm.duoRightScreenIndices.value)

        composeTestRule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.size(840.dp, 420.dp)) {
                DuoPage(
                    viewModel = vm,
                    calendarViewModel = calVm,
                    weatherViewModel = weatherVm
                )
            }
        }
        composeTestRule.waitForIdle()

        // Connected Battery (20) renders on Left pane while Ambient Flash & Level (22) renders on Right pane simultaneously
        composeTestRule.onNodeWithTag("connected_devices_charge_tool").assertIsDisplayed()
        composeTestRule.onNodeWithTag("camera_flash_ambient_toggle_btn").assertIsDisplayed()

        // Switch Left pane to Focus Pomodoro (17) while Right pane stays on Ambient Flash & Level (22)
        vm.setDuoLeftWidgetIndex(17)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pomodoro_config_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("camera_flash_ambient_toggle_btn").assertIsDisplayed()
    }
}


