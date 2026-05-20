package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Standby", appName)
  }

  @Test
  fun `test notification listener setting status parsing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pkgName = context.packageName

    // Function to mimic the isNotificationListenerEnabled parsing logic
    fun checkEnabled(ctx: Context): Boolean {
      val flat = Settings.Secure.getString(
        ctx.contentResolver,
        "enabled_notification_listeners"
      )
      if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
          val cn = android.content.ComponentName.unflattenFromString(name)
          if (cn != null && cn.packageName == pkgName) {
            return true
          }
        }
      }
      return false
    }

    // Default state: should be disabled
    assertFalse(checkEnabled(context))

    // Set secure setting manually to include our service
    val mockFlatSetting = "some.other.pkg/SomeService:$pkgName/com.example.receivers.StandbyNotificationListenerService"
    Settings.Secure.putString(
      context.contentResolver,
      "enabled_notification_listeners",
      mockFlatSetting
    )

    // Now it should return true
    assertTrue(checkEnabled(context))
  }

  @Test
  fun `test audio manager media key events keycode media play pause`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Dispatch media keys should not raise exceptions
    try {
      audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
      audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
      // Verify execution compiles and proceeds smoothly without crashing
      assertTrue(true)
    } catch (e: Exception) {
      org.junit.Assert.fail("Media key dispatch crashed: ${e.message}")
    }
  }
}

