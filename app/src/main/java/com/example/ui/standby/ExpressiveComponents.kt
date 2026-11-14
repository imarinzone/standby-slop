package com.example.ui.standby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.LocalPureAmoled

/**
 * Material 3 Expressive surface border helper that keeps sculpted cards & pills crisp
 * in both standard dynamic M3 and Pure AMOLED (#000000) mode.
 */
@Composable
fun expressiveSurfaceBorder(
    highlighted: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
): BorderStroke {
    val isAmoled = LocalPureAmoled.current
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = when {
        highlighted -> accentColor.copy(alpha = if (isAmoled) 0.90f else 0.72f)
        isAmoled -> colorScheme.outlineVariant.copy(alpha = 0.85f)
        else -> colorScheme.outlineVariant.copy(alpha = 0.36f)
    }
    return BorderStroke(width = if (highlighted) 1.5.dp else 1.dp, color = borderColor)
}

/**
 * Material 3 Expressive segmented group shape generator (inspired by Seal / Read You / Android 16 Settings):
 * Outer items carry generous 24.dp pill corners; inner adjacent boundaries tighten to 8.dp.
 */
fun expressiveGroupedShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 8.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

/**
 * Material 3 Expressive sculpted section card with bouncy spring container transitions.
 */
@Composable
fun ExpressiveSectionCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cornerRadius: Dp = 28.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedContainer by animateColorAsState(
        targetValue = containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "expressiveCardBg"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = animatedContainer,
        tonalElevation = if (LocalPureAmoled.current) 0.dp else 2.dp,
        border = expressiveSurfaceBorder(highlighted = highlighted, accentColor = accentColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * Expressive section header featuring a tonal rounded-squircle icon badge + bold M3 title.
 */
@Composable
fun ExpressiveSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    badgeContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    badgeContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = badgeContainerColor
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeContentColor,
                    modifier = Modifier
                        .padding(7.dp)
                        .size(18.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Signature Material 3 Expressive Switch with animated check / close thumb icons.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = colorScheme.onPrimary,
            checkedTrackColor = colorScheme.primary,
            checkedIconColor = colorScheme.primary,
            uncheckedThumbColor = colorScheme.outline,
            uncheckedTrackColor = colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = colorScheme.outlineVariant,
            uncheckedIconColor = colorScheme.surfaceContainerHighest
        )
    )
}

/**
 * Grouped Material 3 Expressive Settings Switch Row with animated corner morphing,
 * optional leading tonal icon squircle, expandable (i) description, and thumb-icon Switch.
 */
@Composable
fun ExpressiveSettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(18.dp)
) {
    val colorScheme = MaterialTheme.colorScheme
    var showInfo by remember { mutableStateOf(false) }

    val containerBg by animateColorAsState(
        targetValue = if (checked) {
            colorScheme.secondaryContainer.copy(alpha = if (LocalPureAmoled.current) 0.42f else 0.55f)
        } else {
            colorScheme.surfaceContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switchRowBg"
    )

    Surface(
        shape = shape,
        color = containerBg,
        border = expressiveSurfaceBorder(
            highlighted = checked,
            accentColor = colorScheme.primary.copy(alpha = 0.45f)
        ),
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (icon != null) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = if (checked) colorScheme.primaryContainer else colorScheme.surfaceContainerHigh
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (checked) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            IconButton(
                                onClick = { showInfo = !showInfo },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "More Info",
                                    tint = if (showInfo) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
                ExpressiveSwitch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
            AnimatedVisibility(visible = showInfo && subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp)
                )
            }
        }
    }
}

/**
 * Morphing Material 3 Expressive Button (animates corner radius and spring scale on press/selection).
 */
@Composable
fun ExpressiveMorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    idleCorner: Dp = 20.dp,
    activeCorner: Dp = 12.dp,
    colors: ButtonColors = if (selected) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    },
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            isPressed -> activeCorner
            selected -> 28.dp
            else -> idleCorner
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "morphBtnCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "morphBtnScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        colors = colors,
        contentPadding = contentPadding,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

/**
 * Morphing Material 3 Expressive Icon Button: smoothly morphs between rounded-squircle and circle
 * on active state and bounces subtly with spring physics on touch.
 */
@Composable
fun ExpressiveIconActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    containerColor: Color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = when {
            isPressed -> 12.dp
            selected -> size / 2
            else -> 16.dp
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconBtnCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconBtnScale"
    )
    val bgColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconBtnBg"
    )
    val fgColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "iconBtnFg"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(corner),
        color = bgColor,
        border = expressiveSurfaceBorder(highlighted = selected),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fgColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Prominent Material 3 Expressive Hero Toggle Card for Pure AMOLED Mode inside Settings.
 */
@Composable
fun ExpressiveAmoledBannerCard(
    isPureAmoled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardBg by animateColorAsState(
        targetValue = if (isPureAmoled) {
            colorScheme.surfaceContainer
        } else {
            colorScheme.primaryContainer.copy(alpha = 0.42f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "amoledBannerBg"
    )

    Surface(
        onClick = { onToggle(!isPureAmoled) },
        shape = RoundedCornerShape(28.dp),
        color = cardBg,
        border = expressiveSurfaceBorder(highlighted = isPureAmoled, accentColor = colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_amoled_toggle_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = if (isPureAmoled) CircleShape else RoundedCornerShape(16.dp),
                    color = if (isPureAmoled) colorScheme.primary else colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = if (isPureAmoled) Icons.Filled.Contrast else Icons.Outlined.Contrast,
                        contentDescription = null,
                        tint = if (isPureAmoled) colorScheme.onPrimary else colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pure AMOLED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isPureAmoled) colorScheme.primaryContainer else colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = if (isPureAmoled) "0-NIT BLACK" else "SYSTEM M3",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isPureAmoled) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "True pitch-black OLED surfaces with vibrant Material 3 Expressive accents",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ExpressiveSwitch(
                checked = isPureAmoled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("settings_amoled_switch")
            )
        }
    }
}

/**
 * Ensures Compose Dialog sub-windows also hide the status/notification bar and navigation bar.
 */
@Composable
fun KeepDialogImmersiveFullScreen() {
    val view = LocalView.current
    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val cutoutMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                if (dialogWindow.attributes.layoutInDisplayCutoutMode != cutoutMode) {
                    val params = dialogWindow.attributes
                    params.layoutInDisplayCutoutMode = cutoutMode
                    dialogWindow.attributes = params
                }
            }
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            val controller = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {
        }
    }
}
