package com.example

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.standby.StandbyScreen
import com.example.ui.standby.StandbyViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val TAG = "StandbyMainActivity"

    private val powerStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action == android.content.Intent.ACTION_POWER_DISCONNECTED) {
                android.util.Log.d(TAG, "Power disconnected: keeping Standby screen active.")
            }
        }
    }

    private fun applyImmersiveFullscreen() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to apply immersive fullscreen", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val cutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (window.attributes.layoutInDisplayCutoutMode != cutoutMode) {
                    val params = window.attributes
                    params.layoutInDisplayCutoutMode = cutoutMode
                    window.attributes = params
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to configure cutout mode", e)
        }

        try {
            if (requestedOrientation != android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to set screen orientation", e)
        }

        try {
            if ((window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to set keep screen on flag", e)
        }

        applyImmersiveFullscreen()

        setContent {
            val standbyViewModel: StandbyViewModel = viewModel()
            val isPureAmoled by standbyViewModel.isPureAmoledEnabled.collectAsState()
            MyApplicationTheme(pureAmoled = isPureAmoled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StandbyScreen(viewModel = standbyViewModel)
                }
            }
        }

        applyImmersiveFullscreen()
        window.decorView.post { applyImmersiveFullscreen() }
    }

    override fun onStart() {
        super.onStart()
        applyImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveFullscreen()
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                powerStateReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onResume: Failed to register powerStateReceiver", e)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveFullscreen()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (_: Exception) {
        }
    }
}
