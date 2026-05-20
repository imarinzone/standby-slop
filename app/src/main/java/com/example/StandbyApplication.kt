package com.example

import android.app.Application
import android.util.Log

class StandbyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("StandbyApplication", "==========================================================")
        Log.i("StandbyApplication", "StandbyApplication initialized on process start.")
        Log.i("StandbyApplication", "==========================================================")
        
        // Setup global uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CRITICAL_CRASH", "==========================================================")
            Log.e("CRITICAL_CRASH", "FATAL UNCAUGHT EXCEPTION IN THREAD: ${thread.name} (id: ${thread.id})")
            Log.e("CRITICAL_CRASH", "Exception Message: ${throwable.message}")
            Log.e("CRITICAL_CRASH", "Stacktrace:", throwable)
            Log.e("CRITICAL_CRASH", "==========================================================")
            
            // Allow default android crash handler to do normal process finish/crash
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
