package com.example.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.ui.standby.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores scheduled system alarms immediately after an in-place app update
 * (ACTION_MY_PACKAGE_REPLACED) or device reboot (ACTION_BOOT_COMPLETED),
 * ensuring existing alarms continue working without opening the app or reinstalling.
 */
class PackageUpdateAndBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_MY_PACKAGE_REPLACED || action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val alarms = db.alarmDao().getAllAlarmsSnapshot()
                    var rescheduledCount = 0
                    for (alarm in alarms) {
                        if (alarm.isEnabled) {
                            AlarmScheduler.scheduleAlarm(appContext, alarm)
                            rescheduledCount++
                        }
                    }
                    Log.i(
                        "PackageUpdateReceiver",
                        "Handled $action: re-scheduled $rescheduledCount enabled alarms."
                    )
                } catch (e: Exception) {
                    Log.e("PackageUpdateReceiver", "Failed to restore alarms after $action", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
