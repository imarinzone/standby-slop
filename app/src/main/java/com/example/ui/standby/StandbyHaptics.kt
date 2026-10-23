package com.example.ui.standby

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Centralized Material 3 Haptic Feedback controller for Standby interactions.
 * Focused on subtle tick feedback for sliders, time pickers, and clock/screen switching.
 */
class StandbyHaptics(
    private val view: View,
    private val composeHaptics: HapticFeedback
) {
    /**
     * Subtle tick for clock face switching, pager snapping, time picker spinners, and filter chips.
     */
    fun tick() {
        val performed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            false
        }
        if (!performed) {
            composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Fine-grained stepped tick for continuous sliders (scale slider, slide-to-unlock, media seek).
     */
    fun step() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        } else {
            tick()
        }
    }

    /**
     * Crisp confirmation click for switches, primary transport actions, and dialog confirmations.
     */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberStandbyHaptics(): StandbyHaptics {
    val view = LocalView.current
    val composeHaptics = LocalHapticFeedback.current
    return remember(view, composeHaptics) {
        StandbyHaptics(view, composeHaptics)
    }
}
