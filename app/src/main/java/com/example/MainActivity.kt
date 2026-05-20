package com.example

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.receivers.PowerConnectionReceiver
import com.example.ui.standby.StandbyScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val TAG = "StandbyMainActivity"

    private val powerStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            android.util.Log.d(TAG, "powerStateReceiver: received intent with action: ${intent.action}")
            if (intent.action == android.content.Intent.ACTION_POWER_DISCONNECTED) {
                // Keep the standby clock display running for continuous desk-clock mode
                android.util.Log.d(TAG, "Power disconnected: keeping Standby screen active.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Intercept all uncaught exceptions to log exactly what's breaking
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e(TAG, "CRITICAL: Uncaught exception in thread '${thread.name}':", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        android.util.Log.d(TAG, "onCreate: Initializing Standby MainActivity")
        super.onCreate(savedInstanceState)
        
        try {
            // Force Landscape screen orientation
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            android.util.Log.d(TAG, "onCreate: Screen orientation set to sensor landscape")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to set screen orientation", e)
        }
        
        try {
            // Immersive approach for Standby Display
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            android.util.Log.d(TAG, "onCreate: Immersive system bar controls configured and hidden")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to configure immersive window bars", e)
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StandbyScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        android.util.Log.d(TAG, "onStart: Standby screen starting up")
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d(TAG, "onResume: Standby screen active and visible")
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
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d(TAG, "onPause: Standby screen going to background/paused")
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.d(TAG, "onStop: Standby screen stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d(TAG, "onDestroy: Standby screen destroyed")
    }
}
