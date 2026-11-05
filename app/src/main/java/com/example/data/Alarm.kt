package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val label: String = "Alarm",
    val toneUri: String = "default_alarm",
    val snoozeMinutes: Int = 5,
    val musicPackageName: String = "",
    val musicAppName: String = "Default Alarm Tone",
    val gradualVolume: Boolean = true,
    val gradualVolumeSeconds: Int = 30
)
