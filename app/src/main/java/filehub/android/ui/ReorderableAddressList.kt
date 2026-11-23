package filehub.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import filehub.android.R
import kotlinx.coroutines.launch

@Composable
fun ReorderableAddressList(
    addresses: List<String>,
    label: String? = null,
    placeholder: String? = null,
    onValueChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rowHeights = remember { mutableStateListOf<Float>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun heightAt(index: Int): Float = rowHeights.getOrNull(index)
        ?: with(density) { 72.dp.toPx() }

    fun computeTarget(from: Int, offset: Float): Int {
        var remaining = offset
        var index = from
        if (offset > 0f) {
            while (index < addresses.lastIndex && remaining > heightAt(index + 1) / 2f) {
                remaining -= heightAt(index + 1)
                index += 1
            }
        } else {
            while (index > 0 && -remaining > heightAt(index - 1) / 2f) {
                remaining += heightAt(index - 1)
                index -= 1
            }
        }
        return index
    }

    fun offsetForTarget(from: Int, target: Int): Float {
        var total = 0f
        if (target > from) {
            for (i in from + 1..target) total += heightAt(i)
        } else if (target < from) {
            for (i in target until from) total -= heightAt(i)
        }
        return total
    }

    val moveUpLabel = stringResource(R.string.server_address_move_up)
    val moveDownLabel = stringResource(R.string.server_address_move_down)
    addresses.forEachIndexed { index, address ->
        val dragging = draggingIndex == index
        val from = draggingIndex
        val target = targetIndex
        val shift = when {
            from == null || target == null || from == target -> 0f
            index == from -> 0f
            from < target && index in (from + 1)..target -> -heightAt(from)
            from > target && index in target until from -> heightAt(from)
            else -> 0f
        }
        val animatedShift by animateFloatAsState(
            targetValue = shift,
            animationSpec = tween(170),
            label = "address-shift-$index",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .onGloballyPositioned { coordinates ->
                    val height = coordinates.size.height.toFloat()
                    while (rowHeights.size <= index) rowHeights += height
                    rowHeights[index] = height
                }
                .zIndex(if (dragging) 2f else 0f)
                .graphicsLayer {
                    translationY = if (dragging) dragOffset else animatedShift
                    if (dragging) {
                        scaleX = 1.02f
                        scaleY = 1.02f
                        shadowElevation = 8.dp.toPx()
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = stringResource(R.string.server_address_reorder),
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(
                                    CustomAccessibilityAction(
                                        label = moveUpLabel,
                                    ) {
                                        onMove(index, index - 1)
                                        true
                                    },
                                )
                            }
                            if (index < addresses.lastIndex) {
                                add(
                                    CustomAccessibilityAction(
                                        label = moveDownLabel,
                                    ) {
                                        onMove(index, index + 1)
                                        true
                                    },
                                )
                            }
                        }
                    }
                    .pointerInput(index, addresses.size) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                targetIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                if (draggingIndex != index) return@detectDragGestures
                                change.consume()
                                dragOffset += amount.y
                                val next = computeTarget(index, dragOffset)
                                if (next != targetIndex) {
                                    targetIndex = next
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                }
                            },
                            onDragEnd = {
                                val from = draggingIndex
                                if (from == null) return@detectDragGestures
                                val destination = targetIndex ?: from
                                if (destination != from) {
                                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                                        onMove(from, destination)
                                        draggingIndex = null
                                        targetIndex = null
                                        dragOffset = 0f
                                    }
                                } else {
                                    draggingIndex = null
                                    targetIndex = null
                                    dragOffset = 0f
                                }
                            },
                            onDragCancel = {
                                draggingIndex = null
                                targetIndex = null
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
