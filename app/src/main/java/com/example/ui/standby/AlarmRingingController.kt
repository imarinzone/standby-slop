package com.example.ui.standby

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import com.example.MainActivity
import com.example.data.Alarm
import com.example.receivers.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt

data class AlarmPlayerOption(
    val id: String,
    val name: String,
    val packageName: String,
    val isMusicPlayer: Boolean
)

object AlarmScheduler {
    const val ACTION_TRIGGER_ALARM = "com.example.ACTION_TRIGGER_ALARM"

    fun computeNextTriggerTimeMillis(
        hour: Int,
        minute: Int,
        now: Calendar = Calendar.getInstance()
    ): Long {
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm.id)
            return
        }
        val triggerAtMillis = computeNextTriggerTimeMillis(alarm.hour, alarm.minute)
        val pendingIntent = buildPendingIntent(context, alarm)
        scheduleAtMillis(alarmManager, triggerAtMillis, pendingIntent)
    }

    fun scheduleSnooze(context: Context, alarm: Alarm, snoozeMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerAtMillis = System.currentTimeMillis() + (snoozeMinutes.coerceAtLeast(1) * 60_000L)
        val pendingIntent = buildPendingIntent(context, alarm, requestCodeOffset = 10_000)
        scheduleAtMillis(alarmManager, triggerAtMillis, pendingIntent)
    }

    fun cancelAlarm(context: Context, alarmId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {
        }
    }

    private fun buildPendingIntent(
        context: Context,
        alarm: Alarm,
        requestCodeOffset: Int = 0
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_hour", alarm.hour)
            putExtra("alarm_minute", alarm.minute)
            putExtra("alarm_label", alarm.label)
            putExtra("alarm_snooze", alarm.snoozeMinutes)
            putExtra("alarm_tone_uri", alarm.toneUri)
            putExtra("alarm_music_pkg", alarm.musicPackageName)
            putExtra("alarm_music_name", alarm.musicAppName)
            putExtra("alarm_gradual_vol", alarm.gradualVolume)
            putExtra("alarm_gradual_sec", alarm.gradualVolumeSeconds)
        }
        val requestCode = (alarm.id + requestCodeOffset).coerceAtLeast(1)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAtMillis(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }
}

object AlarmRingingController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _activeRingingAlarm = MutableStateFlow<Alarm?>(null)
    val activeRingingAlarm: StateFlow<Alarm?> = _activeRingingAlarm

    private val _currentVolumePercent = MutableStateFlow(100)
    val currentVolumePercent: StateFlow<Int> = _currentVolumePercent

    private val _playbackStatus = MutableStateFlow("")
    val playbackStatus: StateFlow<String> = _playbackStatus

    private var volumeRampJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var lastTriggeredMinuteKey: String? = null

    fun computeVolumeFactor(
        elapsedMillis: Long,
        rampSeconds: Int,
        gradualEnabled: Boolean,
        startFactor: Float = 0.12f
    ): Float {
        if (!gradualEnabled || rampSeconds <= 0) return 1.0f
        val safeStart = startFactor.coerceIn(0.05f, 0.90f)
        val totalMillis = (rampSeconds.coerceIn(5, 300) * 1000L).toFloat()
        val progress = (elapsedMillis.coerceAtLeast(0L).toFloat() / totalMillis).coerceIn(0f, 1f)
        return (safeStart + (1.0f - safeStart) * progress).coerceIn(safeStart, 1.0f)
    }

    fun discoverMusicPlayerOptions(context: Context): List<AlarmPlayerOption> {
        val options = mutableListOf(
            AlarmPlayerOption(
                id = "default_alarm",
                name = "Default Alarm Tone",
                packageName = "",
                isMusicPlayer = false
            ),
            AlarmPlayerOption(
                id = "ANY_ACTIVE_PLAYER",
                name = "Active Music Player (Auto-Resume)",
                packageName = "ANY_ACTIVE_PLAYER",
                isMusicPlayer = true
            )
        )

        val pm = context.packageManager
        val discoveredPackages = mutableSetOf<String>()

        fun addAppOption(pkg: String, label: String) {
            if (pkg.isNotBlank() && pkg != context.packageName && discoveredPackages.add(pkg)) {
                options.add(
                    AlarmPlayerOption(
                        id = "music_app:$pkg",
                        name = label,
                        packageName = pkg,
                        isMusicPlayer = true
                    )
                )
            }
        }

        try {
            val musicIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MUSIC)
            }
            for (info in pm.queryIntentActivities(musicIntent, 0)) {
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                addAppOption(pkg, label)
            }
        } catch (_: Exception) {
        }

        try {
            val searchIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH")
            for (info in pm.queryIntentActivities(searchIntent, 0)) {
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                addAppOption(pkg, label)
            }
        } catch (_: Exception) {
        }

        val knownMusicPlayers = listOf(
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.apple.android.music" to "Apple Music",
            "com.amazon.mp3" to "Amazon Music",
            "com.soundcloud.android" to "SoundCloud",
            "deezer.android.app" to "Deezer",
            "org.videolan.vlc" to "VLC Media Player",
            "com.maxmpz.audioplayer" to "Poweramp"
        )

        for ((pkg, fallbackName) in knownMusicPlayers) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { fallbackName }
                addAppOption(pkg, label)
            } catch (_: Exception) {
                // Also expose popular music player options so users can configure them seamlessly
                addAppOption(pkg, fallbackName)
            }
        }

        return options
    }

    fun checkAndTriggerDueAlarm(
        context: Context,
        enabledAlarms: List<Alarm>,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentSecond = now.get(Calendar.SECOND)
        val dayKey = "${now.get(Calendar.YEAR)}-${now.get(Calendar.DAY_OF_YEAR)}-$currentHour:$currentMinute"

        if (currentSecond > 15 || lastTriggeredMinuteKey == dayKey) {
            return false
        }

        val matchingAlarm = enabledAlarms.firstOrNull {
            it.isEnabled && it.hour == currentHour && it.minute == currentMinute
        } ?: return false

        lastTriggeredMinuteKey = dayKey
        triggerAlarm(context, matchingAlarm)
        return true
    }

    fun triggerAlarm(context: Context, alarm: Alarm) {
        stopPlaybackInternal()
        _activeRingingAlarm.value = alarm

        val initialFactor = computeVolumeFactor(
            elapsedMillis = 0L,
            rampSeconds = alarm.gradualVolumeSeconds,
            gradualEnabled = alarm.gradualVolume
        )
        _currentVolumePercent.value = (initialFactor * 100f).roundToInt().coerceIn(10, 100)

        val usesMusicPlayer = alarm.musicPackageName.isNotBlank()
        _playbackStatus.value = if (usesMusicPlayer) {
            "Playing via ${alarm.musicAppName.ifBlank { "Music Player" }}"
        } else {
            "Playing ${alarm.musicAppName.ifBlank { "Alarm Tone" }}"
        }

        startVibration(context)
        startAudioOrMusicPlayer(context, alarm, initialFactor)
        startGradualVolumeRamp(context, alarm)
    }

    private fun startAudioOrMusicPlayer(context: Context, alarm: Alarm, initialVolume: Float) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        applySystemStreamVolume(audioManager, alarm.musicPackageName.isNotBlank(), initialVolume)

        if (alarm.musicPackageName.isNotBlank()) {
            triggerExternalMusicPlayer(context, audioManager, alarm.musicPackageName)
        }

        startAlarmToneMediaPlayer(context, alarm, initialVolume)
    }

    private fun triggerExternalMusicPlayer(
        context: Context,
        audioManager: AudioManager?,
        packageName: String
    ) {
        try {
            if (packageName != "ANY_ACTIVE_PLAYER") {
                val playSearchIntent = Intent("android.media.action.MEDIA_PLAY_FROM_SEARCH").apply {
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pm = context.packageManager
                if (playSearchIntent.resolveActivity(pm) != null) {
                    context.startActivity(playSearchIntent)
                } else {
                    pm.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    }
                }
            }
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        } catch (_: Exception) {
        }
    }

    private fun startAlarmToneMediaPlayer(context: Context, alarm: Alarm, initialVolume: Float) {
        try {
            val customUri = if (alarm.toneUri.startsWith("content://") || alarm.toneUri.startsWith("file://")) {
                Uri.parse(alarm.toneUri)
            } else {
                null
            }
            val targetUri = customUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (targetUri != null) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, targetUri)
                    isLooping = true
                    prepare()
                    setVolume(initialVolume, initialVolume)
                    start()
                }
                mediaPlayer = player
                return
            }
        } catch (_: Exception) {
        }

        // Fallback tone generator so headless/emulator environments still produce audible alarm output
        try {
            val tg = ToneGenerator(
                AudioManager.STREAM_ALARM,
                (initialVolume * 100f).roundToInt().coerceIn(15, 100)
            )
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
            toneGenerator = tg
        } catch (_: Exception) {
        }
    }

    private fun startGradualVolumeRamp(context: Context, alarm: Alarm) {
        volumeRampJob?.cancel()
        if (!alarm.gradualVolume || alarm.gradualVolumeSeconds <= 0) {
            _currentVolumePercent.value = 100
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val startTime = System.currentTimeMillis()
        val rampSeconds = alarm.gradualVolumeSeconds.coerceIn(5, 300)

        volumeRampJob = scope.launch {
            while (isActive && _activeRingingAlarm.value != null) {
                val elapsed = System.currentTimeMillis() - startTime
                val factor = computeVolumeFactor(
                    elapsedMillis = elapsed,
                    rampSeconds = rampSeconds,
                    gradualEnabled = true
                )
                _currentVolumePercent.value = (factor * 100f).roundToInt().coerceIn(10, 100)

                try {
                    mediaPlayer?.setVolume(factor, factor)
                } catch (_: Exception) {
                }

                applySystemStreamVolume(audioManager, alarm.musicPackageName.isNotBlank(), factor)

                if (factor >= 0.999f) {
                    _currentVolumePercent.value = 100
                    break
                }
                delay(500L)
            }
        }
    }

    private fun applySystemStreamVolume(
        audioManager: AudioManager?,
        useMusicStream: Boolean,
        factor: Float
    ) {
        if (audioManager == null) return
        try {
            val streamType = if (useMusicStream) AudioManager.STREAM_MUSIC else AudioManager.STREAM_ALARM
            val maxVol = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val targetVol = (maxVol * factor).roundToInt().coerceIn(1, maxVol)
            audioManager.setStreamVolume(streamType, targetVol, 0)
        } catch (_: Exception) {
        }
    }

    private fun startVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 400, 500), 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 500, 400, 500), 0)
            }
        } catch (_: Exception) {
        }
    }

    fun snoozeActiveAlarm(context: Context) {
        val current = _activeRingingAlarm.value ?: return
        AlarmScheduler.scheduleSnooze(context, current, current.snoozeMinutes)
        dismissAlarm(context)
    }

    fun dismissAlarm(context: Context? = null) {
        val wasMusicAlarm = _activeRingingAlarm.value?.musicPackageName?.isNotBlank() == true
        stopPlaybackInternal()
        if (context != null) {
            try {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            } catch (_: Exception) {
            }
            if (wasMusicAlarm) {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
                    audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                } catch (_: Exception) {
                }
            }
        }
        _activeRingingAlarm.value = null
        _playbackStatus.value = ""
    }

    private fun stopPlaybackInternal() {
        volumeRampJob?.cancel()
        volumeRampJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null

        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }

    fun bringAppToForegroundIfNeeded(context: Context) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(launchIntent)
        } catch (_: Exception) {
        }
    }
}
