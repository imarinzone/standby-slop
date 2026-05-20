package com.example.ui.standby

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ClockPage(modifier: Modifier = Modifier, themeIndex: Int) {
    ClockFaceRenderer(themeIndex = themeIndex, modifier = modifier.fillMaxSize())
}
