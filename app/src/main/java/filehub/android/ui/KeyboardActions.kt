package filehub.android.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Standard Done action for editable fields: optionally submit, then always
 * leave the editing state so the keyboard and focus do not stay behind.
 */
@Composable
fun clearFocusOnDone(onDone: (() -> Unit)? = null): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard, onDone) {
        KeyboardActions(
            onDone = {
                onDone?.invoke()
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
}
