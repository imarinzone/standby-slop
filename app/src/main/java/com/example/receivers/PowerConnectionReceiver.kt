package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity

class PowerConnectionReceiver : BroadcastReceiver() {
    private val TAG = "PowerConnectionReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: Received broadcast with action = ${intent.action}")
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            try {
                Log.i(TAG, "onReceive: Power connected! Attempting to launch Standby MainActivity.")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("IS_CHARGING", true)
                }
                context.startActivity(launchIntent)
                Log.d(TAG, "onReceive: MainActivity start requested successfully")
            } catch (e: Exception) {
                Log.e(TAG, "onReceive: Failed to launch Standby MainActivity upon power connection.", e)
            }
        }
    }
}
