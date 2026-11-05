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
import com.example.ui.standby.StandbyScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to set screen orientation", e)
        }

        try {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "onCreate: Failed to set keep screen on flag", e)
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

    override fun onResume() {
        super.onResume()
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

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(powerStateReceiver)
        } catch (_: Exception) {
        }
    }
}
