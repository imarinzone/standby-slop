package com.example.ui.standby

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow

object MediaStateHolder {
    val trackTitle = MutableStateFlow<String?>("No Track")
    val artistName = MutableStateFlow<String?>("Unknown Artist")
    val isPlaying = MutableStateFlow<Boolean>(false)
    val albumArt = MutableStateFlow<Bitmap?>(null)
    val activeAppPackage = MutableStateFlow<String?>(null)
}
