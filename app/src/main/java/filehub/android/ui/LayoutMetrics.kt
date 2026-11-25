package filehub.android.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun shellModeFor(
    widthDp: Int,
    heightDp: Int,
    smallestWidthDp: Int,
): ShellMode = when {
    // Tablets and car head units keep the same shell in both orientations.
    smallestWidthDp >= 600 -> ShellMode.Wide
    widthDp > heightDp && widthDp >= 600 -> ShellMode.Landscape
    else -> ShellMode.Compact
}

internal fun tabletSidebarWidth(widthDp: Float): Dp = when {
    widthDp >= 1100f -> 280.dp
    widthDp >= 840f -> 240.dp
    else -> 220.dp
}

internal fun tabletListColumns(widthDp: Float): Int = when {
    widthDp >= 1150f -> 4
    widthDp >= 780f -> 3
    widthDp >= 480f -> 2
    else -> 1
}

/** Android 11 and below in tablet mode use a gradient lightbox, not a thumbnail. */
internal fun usesLightboxBackdrop(tabletMode: Boolean, sdkInt: Int): Boolean =
    tabletMode && sdkInt < 31

internal fun fileGridColumns(widthDp: Float): Int =
    (widthDp / 180f).toInt().coerceIn(2, 6)

internal fun rootGridColumns(widthDp: Float): Int =
    if (widthDp < 600f) 1 else (widthDp / 300f).toInt().coerceIn(2, 4)
