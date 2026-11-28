package filehub.android.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import filehub.android.R

internal const val APP_LOCK_TIMEOUT_MS = 60_000L

internal fun shouldRelock(backgroundedAt: Long, now: Long): Boolean =
    backgroundedAt > 0 && now - backgroundedAt >= APP_LOCK_TIMEOUT_MS

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

internal fun canUseAppLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

internal fun promptAppLock(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onCancelled: () -> Unit = {},
) {
    if (!canUseAppLock(activity)) {
        onFailure()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED,
                    -> onCancelled()
                    else -> onFailure()
                }
            }

            override fun onAuthenticationFailed() {
                // Keep the prompt open so the user can retry.
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build(),
    )
}

@Composable
fun AppLockLoading() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findAppLockActivity() }
    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.app_lock_prompt_subtitle)
    var error by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_lock_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (error) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_lock_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val host = activity
                    if (host == null) {
                        error = true
                        return@Button
                    }
                    prompting = true
                    promptAppLock(
                        activity = host,
                        title = title,
                        subtitle = subtitle,
                        onSuccess = {
                            prompting = false
                            error = false
                            onUnlocked()
                        },
                        onFailure = {
                            prompting = false
                            error = true
                        },
                        onCancelled = { prompting = false },
                    )
                },
                enabled = !prompting,
            ) {
                Text(stringResource(R.string.app_lock_unlock))
            }
        }
    }
}

internal tailrec fun Context.findAppLockActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findAppLockActivity()
    else -> null
}
