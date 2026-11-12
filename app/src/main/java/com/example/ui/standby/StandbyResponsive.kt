package com.example.ui.standby

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Responsive sizing utilities to ensure typography and UI dimensions scale
 * continuously and proportionally relative to actual screen/pane bounds.
 *
 * Prevents fixed/hardcoded sizes from overflowing on tight smartphone viewports
 * or appearing tiny on spacious tablet/foldable viewports.
 */
object StandbyResponsive {

    /**
     * Calculates a font size in `sp` as a continuous fraction of the viewport dimension.
     */
    fun relativeSp(
        dimension: Dp,
        fraction: Float,
        minSp: Float = 10f,
        maxSp: Float = 280f
    ): TextUnit {
        val calculated = (dimension.value * fraction).coerceIn(minSp, maxSp)
        return calculated.sp
    }

    /**
     * Calculates a layout dimension in `dp` as a continuous fraction of the viewport dimension.
     */
    fun relativeDp(
        dimension: Dp,
        fraction: Float,
        minDp: Float = 4f,
        maxDp: Float = 600f
    ): Dp {
        val calculated = (dimension.value * fraction).coerceIn(minDp, maxDp)
        return calculated.dp
    }
}
