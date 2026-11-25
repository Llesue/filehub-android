package filehub.android.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import filehub.android.R
import filehub.android.core.model.ServerProfile
import filehub.android.data.ServerConnection
import filehub.android.data.ServerConnectionStatus
import androidx.compose.ui.draw.clip

@Composable
internal fun ReorderableServerGrid(
    profiles: List<ServerProfile>,
    activeId: String?,
    authExpiredProfiles: Set<String>,
    connection: ServerConnection?,
    columns: Int,
    contentPadding: PaddingValues,
    onMoveProfile: (Int, Int) -> Unit,
    onSelect: (String) -> Unit,
    onReauth: (String) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (ServerProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val moveProfile by rememberUpdatedState(onMoveProfile)
    var items by remember { mutableStateOf(profiles) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var originIndex by remember { mutableStateOf(-1) }
    var reorderReady by remember { mutableStateOf(true) }

    LaunchedEffect(profiles) {
        if (draggingId == null) {
            if (items != profiles) items = profiles
        } else {
            val byId = profiles.associateBy { it.id }
            val refreshed = items.mapNotNull { byId[it.id] }
            if (refreshed.size == items.size) {
                if (refreshed != items) items = refreshed
            } else {
                draggingId = null
                dragOffset = Offset.Zero
                items = profiles
            }
        }
    }

    // A layout pass must finish before the next slot can be resolved from
    // layoutInfo; otherwise a second pointer event in the same frame would
    // compute targets against stale positions and oscillate.
    LaunchedEffect(items) {
        withFrameNanos { }
        reorderReady = true
    }

    fun finishDrag(profile: ServerProfile) {
        if (draggingId != profile.id) return
        val finalIndex = items.indexOfFirst { it.id == profile.id }
        val from = originIndex
        draggingId = null
        dragOffset = Offset.Zero
        originIndex = -1
        if (from in items.indices && finalIndex in items.indices && from != finalIndex) {
            moveProfile(from, finalIndex)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items, key = { _, profile -> profile.id }) { index, profile ->
            val dragging = draggingId == profile.id
            val placementAnimation = if (dragging) {
                Modifier
            } else {
                Modifier.animateItem(
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(placementAnimation)
                    .zIndex(if (dragging) 2f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 8.dp.toPx()
                        }
                    }
                    .pointerInput(profile.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { local ->
                                val info = gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == profile.id } ?: return@detectDragGesturesAfterLongPress
                                draggingId = profile.id
                                originIndex = items.indexOfFirst { it.id == profile.id }
                                grab = local
                                dragOffset = Offset.Zero
                                reorderReady = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                if (draggingId != profile.id) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                change.consume()
                                dragOffset += amount
                                if (!reorderReady) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                val infos = gridState.layoutInfo.visibleItemsInfo
                                val current = infos.firstOrNull { it.key == profile.id }
                                    ?: return@detectDragGesturesAfterLongPress
                                val pointer = Offset(
                                    current.offset.x + dragOffset.x + grab.x,
                                    current.offset.y + dragOffset.y + grab.y,
                                )
                                val target = infos.minByOrNull { info ->
                                    val dx = info.offset.x + info.size.width / 2f - pointer.x
                                    val dy = info.offset.y + info.size.height / 2f - pointer.y
                                    dx * dx + dy * dy
                                } ?: return@detectDragGesturesAfterLongPress
                                if (target.key == profile.id) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                val from = items.indexOfFirst { it.id == profile.id }
                                val to = items.indexOfFirst { it.id == target.key }
                                if (from < 0 || to < 0 || from == to) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                // The card is about to land in the target slot;
                                // shift the visual offset by the same amount so it
                                // keeps following the finger without a jump.
                                dragOffset -= Offset(
                                    (target.offset.x - current.offset.x).toFloat(),
                                    (target.offset.y - current.offset.y).toFloat(),
                                )
                                items = items.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                                reorderReady = false
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            },
                            onDragEnd = { finishDrag(profile) },
                            onDragCancel = { finishDrag(profile) },
                        )
                    },
            ) {
                val needsLogin = profile.id in authExpiredProfiles
                ServerCard(
                    profile = profile,
                    active = activeId == profile.id,
                    needsLogin = needsLogin,
                    connection = connection?.takeIf { it.profileId == profile.id },
                    onClick = {
                        if (needsLogin) onReauth(profile.id) else onSelect(profile.id)
                    },
                    onRemove = { onRemove(profile) },
                    onEdit = { onEdit(profile.id) },
                    onMoveUp = if (index > 0) {
                        { onMoveProfile(index, index - 1) }
                    } else {
                        null
                    },
                    onMoveDown = if (index < items.lastIndex) {
                        { onMoveProfile(index, index + 1) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun ServerCard(
    profile: ServerProfile,
    active: Boolean,
    needsLogin: Boolean,
    connection: ServerConnection?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val moveUpLabel = stringResource(R.string.server_address_move_up)
    val moveDownLabel = stringResource(R.string.server_address_move_down)
    val moveActions = buildList {
        onMoveUp?.let { move ->
            add(CustomAccessibilityAction(label = moveUpLabel) { move(); true })
        }
        onMoveDown?.let { move ->
            add(CustomAccessibilityAction(label = moveDownLabel) { move(); true })
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { customActions = moveActions },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = profile.displayName.take(1).uppercase().ifBlank { "F" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                profile.iconUrl?.let { icon ->
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    profile.serverVersion?.takeIf { it.isNotBlank() }?.let { version ->
                        Spacer(Modifier.size(6.dp))
                        ServerVersionBadge(version)
                    }
                    if (needsLogin) {
                        Spacer(Modifier.size(6.dp))
                        ReauthBadge()
                    } else {
                        if (active && connection == null) {
                            Spacer(Modifier.size(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.servers_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        connection?.let { status ->
                            Spacer(Modifier.size(6.dp))
                            val label = when (status.status) {
                                ServerConnectionStatus.Connecting -> stringResource(
                                    R.string.server_connecting_progress,
                                    status.attempt,
                                    status.total,
                                )
                                ServerConnectionStatus.Success -> stringResource(R.string.server_connected)
                                ServerConnectionStatus.Failed -> stringResource(R.string.browse_root_offline)
                            }
                            Surface(
                                color = when (status.status) {
                                    ServerConnectionStatus.Connecting -> MaterialTheme.colorScheme.secondaryContainer
                                    ServerConnectionStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                                    ServerConnectionStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                                },
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (status.status) {
                                        ServerConnectionStatus.Connecting -> MaterialTheme.colorScheme.onSecondaryContainer
                                        ServerConnectionStatus.Success -> MaterialTheme.colorScheme.primary
                                        ServerConnectionStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = profile.username,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.server_edit)) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    CompactDropdownMenuItem(
                        text = { Text(stringResource(R.string.servers_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

