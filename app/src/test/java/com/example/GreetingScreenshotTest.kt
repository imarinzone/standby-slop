package com.example

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import com.example.ui.standby.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h720dp-land-v36", sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun capture_neon_digital_clock_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ClockPage(themeIndex = 2) // Index 2 is Cyber Neon Clock
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/neon_digital_clock.png")
  }

  @Test
  fun capture_music_player_panel_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PreviewMediaPage(
          accentColor = Color(0xFF38BDF8),
          fontFamily = FontFamily.SansSerif
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/music_player_panel.png")
  }

  @Test
  fun capture_focus_timer_matrix_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        PreviewTimerPage(
          accentColor = Color(0xFFF43F5E),
          fontFamily = FontFamily.SansSerif
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/focus_timer_matrix.png")
  }

  @Test
  fun capture_standby_directory_screenshot() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = StandbyViewModel(application)

    composeTestRule.setContent {
      MyApplicationTheme {
        AppOverviewLayout(
          viewModel = viewModel,
          activePage = 0,
          activeClockFace = 2,
          onNavigateToPage = {},
          onSelectClockFace = {},
          onDismiss = {}
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/standby_directory.png")
  }
}
