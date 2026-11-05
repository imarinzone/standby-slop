package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.Alarm
import com.example.ui.standby.AlarmRingingController
import com.example.ui.standby.AlarmScheduler

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmScheduler.ACTION_TRIGGER_ALARM) {
            val id = intent.getIntExtra("alarm_id", 0)
            val hour = intent.getIntExtra("alarm_hour", 7)
            val minute = intent.getIntExtra("alarm_minute", 0)
            val label = intent.getStringExtra("alarm_label") ?: "Alarm"
            val snooze = intent.getIntExtra("alarm_snooze", 5)
            val toneUri = intent.getStringExtra("alarm_tone_uri") ?: "default_alarm"
            val musicPkg = intent.getStringExtra("alarm_music_pkg") ?: ""
            val musicName = intent.getStringExtra("alarm_music_name") ?: "Default Alarm Tone"
            val gradualVol = intent.getBooleanExtra("alarm_gradual_vol", true)
            val gradualSec = intent.getIntExtra("alarm_gradual_sec", 30)

            val triggeredAlarm = Alarm(
                id = id,
                hour = hour,
                minute = minute,
                isEnabled = true,
                label = label,
                toneUri = toneUri,
                snoozeMinutes = snooze,
                musicPackageName = musicPkg,
                musicAppName = musicName,
                gradualVolume = gradualVol,
                gradualVolumeSeconds = gradualSec
            )

            AlarmRingingController.triggerAlarm(context.applicationContext, triggeredAlarm)
            AlarmRingingController.bringAppToForegroundIfNeeded(context.applicationContext)
        }
    }
}
