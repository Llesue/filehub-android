package filehub.android.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import filehub.android.R
import kotlin.math.roundToInt

@Composable
fun ReorderableAddressList(
    addresses: List<String>,
    label: String? = null,
    placeholder: String? = null,
    onValueChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    addresses.forEachIndexed { index, address ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .zIndex(if (draggingIndex == index) 1f else 0f)
                .graphicsLayer {
                    translationY = if (draggingIndex == index) dragOffset else 0f
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.server_address_reorder),
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .pointerInput(index, addresses.size) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val from = draggingIndex
                                if (from != null) {
                                    val delta = (dragOffset / rowHeightPx).roundToInt()
                                    val to = (from + delta).coerceIn(0, addresses.lastIndex)
                                    if (to != from) onMove(from, to)
                                }
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                        )
                    },
            )
            OutlinedTextField(
                value = address,
                onValueChange = { onValueChange(index, it) },
                modifier = Modifier.weight(1f),
                label = label?.let { { Text(it) } },
                placeholder = placeholder?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = clearFocusOnDone(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(index) },
                        enabled = addresses.size > 1,
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                    }
                },
            )
            Spacer(Modifier.size(2.dp))
        }
    }
}
