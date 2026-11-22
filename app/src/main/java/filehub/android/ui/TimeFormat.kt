package filehub.android.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import filehub.android.R
import kotlin.math.abs

enum class RelativeUnit {
    JustNow,
    Minutes,
    Hours,
    Days,
    Weeks,
    Months,
    Years,
}

data class RelativeTime(
    val amount: Long,
    val unit: RelativeUnit,
    val future: Boolean,
)

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val WEEK_MS = 7 * DAY_MS
private const val MONTH_MS = 30 * DAY_MS
private const val YEAR_MS = 365 * DAY_MS

/**
 * Buckets a timestamp without touching Android resources, so the rules stay
 * unit-testable. Months are 30 days and years 365 days; there is deliberately
 * no absolute-date fallback for old timestamps.
 */
fun relativeTimeOf(nowMs: Long, targetMs: Long): RelativeTime {
    val future = targetMs > nowMs
    val diff = abs(targetMs - nowMs)
    return when {
        diff < MINUTE_MS -> RelativeTime(0, RelativeUnit.JustNow, future)
        diff < HOUR_MS -> RelativeTime(diff / MINUTE_MS, RelativeUnit.Minutes, future)
        diff < DAY_MS -> RelativeTime(diff / HOUR_MS, RelativeUnit.Hours, future)
        diff < WEEK_MS -> RelativeTime(diff / DAY_MS, RelativeUnit.Days, future)
        diff < MONTH_MS -> RelativeTime(diff / WEEK_MS, RelativeUnit.Weeks, future)
        diff < YEAR_MS -> RelativeTime(diff / MONTH_MS, RelativeUnit.Months, future)
        else -> RelativeTime(diff / YEAR_MS, RelativeUnit.Years, future)
    }
}

fun relativeTimeText(context: Context, epochSeconds: Long?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    val relative = relativeTimeOf(
        nowMs = System.currentTimeMillis(),
        targetMs = epochSeconds * 1000,
    )
    val amount = relative.amount.coerceAtLeast(1)
    return when (relative.unit) {
        RelativeUnit.JustNow -> context.getString(
            if (relative.future) R.string.time_soon else R.string.time_just_now,
        )
        RelativeUnit.Minutes -> context.resources.getQuantityString(
            if (relative.future) R.plurals.time_in_minutes else R.plurals.time_minutes_ago,
            amount.toInt(),
            amount,
        )
        RelativeUnit.Hours -> context.resources.getQuantityString(
            if (relative.future) R.plurals.time_in_hours else R.plurals.time_hours_ago,
            amount.toInt(),
            amount,
        )
        RelativeUnit.Days -> when {
            amount == 1L && relative.future -> context.getString(R.string.time_tomorrow)
            amount == 1L -> context.getString(R.string.time_yesterday)
            relative.future -> context.resources.getQuantityString(
                R.plurals.time_in_days,
                amount.toInt(),
                amount,
            )
            else -> context.resources.getQuantityString(
                R.plurals.time_days_ago,
                amount.toInt(),
                amount,
            )
        }
        RelativeUnit.Weeks -> context.resources.getQuantityString(
            if (relative.future) R.plurals.time_in_weeks else R.plurals.time_weeks_ago,
            amount.toInt(),
            amount,
        )
        RelativeUnit.Months -> context.resources.getQuantityString(
            if (relative.future) R.plurals.time_in_months else R.plurals.time_months_ago,
            amount.toInt(),
            amount,
        )
        RelativeUnit.Years -> context.resources.getQuantityString(
            if (relative.future) R.plurals.time_in_years else R.plurals.time_years_ago,
            amount.toInt(),
            amount,
        )
    }
}

fun tokenValidityText(context: Context, expiresAtSeconds: Long?): String {
    if (expiresAtSeconds == null) return "—"
    val remainingMs = expiresAtSeconds * 1000 - System.currentTimeMillis()
    if (remainingMs <= 0) return context.getString(R.string.my_expiry_expired)
    if (remainingMs < DAY_MS) return context.getString(R.string.my_expiry_today)
    val days = (remainingMs + DAY_MS - 1) / DAY_MS
    return context.getString(R.string.my_expiry_days, days)
}

@Composable
fun relativeTimeText(epochSeconds: Long?): String {
    val context = LocalContext.current
    return relativeTimeText(context, epochSeconds)
}

@Composable
fun tokenValidityText(expiresAtSeconds: Long?): String {
    val context = LocalContext.current
    return tokenValidityText(context, expiresAtSeconds)
}
