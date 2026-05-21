package com.example

import android.graphics.Bitmap
import android.graphics.Picture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.standby.ClockPage
import com.example.ui.standby.PreviewMediaPage
import com.example.ui.standby.PreviewTimerPage
import com.example.ui.standby.PreviewDuoPage
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h720dp-land-v36", sdk = [36])
class PictureScreenshotTest {

    @get:Rule 
    val composeTestRule = createComposeRule()

    private fun captureAndSave(fileName: String, content: @Composable () -> Unit) {
        val picture = Picture()
        val targetWidth = 1280
        val targetHeight = 720

        composeTestRule.setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            onDrawWithContent {
                                // Begin recording composable draws on the picture canvas
                                val pictureCanvas = Canvas(picture.beginRecording(targetWidth, targetHeight))
                                drawIntoCanvas { canvas ->
                                    val originalCanvas = drawContext.canvas
                                    drawContext.canvas = pictureCanvas
                                    drawContent()
                                    drawContext.canvas = originalCanvas
                                }
                                picture.endRecording()
                                
                                // Render normally back onto the screen so Compose state functions properly
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawPicture(picture)
                                }
                            }
                        }
                ) {
                    content()
                }
            }
        }
        
        composeTestRule.waitForIdle()

        // Create the android.graphics.Bitmap from the recorded commands in the Picture object (supported on API 28+)
        val bitmap = Bitmap.createBitmap(picture, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

        // Save generated screenshot file to multiple potential workspace relative directories 
        // to guarantee it correctly outputs into screenshots/ directory for both root or module execution
        val dirs = listOf(
            File("src/test/screenshots"),
            File("app/src/test/screenshots"),
            File("../app/src/test/screenshots")
        )

        var savedPath = ""
        for (dir in dirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (dir.exists() && dir.isDirectory) {
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    savedPath = file.absolutePath
                }
            } catch (e: Exception) {
                // Keep trying other paths in case of lack of write-permissions or unmatched hierarchy
            }
        }

        println("Successfully wrote actual high-quality picture screenshot to: $savedPath")
    }

    @Test
    fun capture_neon_digital_clock() {
        captureAndSave("neon_digital_clock.png") {
            ClockPage(themeIndex = 2) // Index 2 is Cyber Neon Clock face
        }
    }

    @Test
    fun capture_music_player_panel() {
        captureAndSave("music_player_panel.png") {
            PreviewMediaPage(Color(0xFF10B981), FontFamily.Default)
        }
    }

    @Test
    fun capture_focus_timer_matrix() {
        captureAndSave("focus_timer_matrix.png") {
            PreviewTimerPage(Color(0xFFE11D48), FontFamily.Default)
        }
    }

    @Test
    fun capture_standby_directory() {
        captureAndSave("standby_directory.png") {
            PreviewDuoPage(FontFamily.Default)
        }
    }
}
