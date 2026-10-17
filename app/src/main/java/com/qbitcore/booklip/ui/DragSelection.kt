package com.qbitcore.booklip.ui

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Photos-style drag selection for grids and lists in a select mode (the
// counterpart of the iOS DragSelection.swift).
//
// Each selectable cell reports its bounds (`Modifier.dragSelectItem`), the
// lazy container listens for a drag (`Modifier.dragSelection`). A sweep starts
// either with a drag that begins mostly horizontally, or — in any direction —
// with a long press. The first cell under the finger decides whether the sweep
// selects or deselects, and every cell from it to the one under the finger
// (in list order, as in Photos) gets that state; cells the sweep leaves again
// go back to how they were. Near the top or bottom edge the list scrolls on
// its own. A drag that starts vertically is left to the list's scrolling.

/** Where the cells of one container are on screen, keyed by item key. */
class DragSelectState<K> {
    internal val bounds = HashMap<K, Rect>()
    internal var container: LayoutCoordinates? = null

    internal fun keyAt(local: Offset): K? {
        val root = container?.takeIf { it.isAttached }?.localToRoot(local) ?: return null
        return bounds.entries.firstOrNull { it.value.contains(root) }?.key
    }
}

@Composable
fun <K> rememberDragSelectState(): DragSelectState<K> = remember { DragSelectState() }

/** Marks a cell as a drag-selection target identified by [key]. */
fun <K> Modifier.dragSelectItem(state: DragSelectState<K>, key: K): Modifier = composed {
    DisposableEffect(state, key) { onDispose { state.bounds.remove(key) } }
    onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }
}

/**
 * Enables sweep selection over the [dragSelectItem] cells inside this lazy
 * container. [keys] are the selectable items in display order; [scrollState]
 * is the container's state, for scrolling at the edges.
 */
fun <K> Modifier.dragSelection(
    enabled: Boolean,
    state: DragSelectState<K>,
    keys: List<K>,
    selected: Set<K>,
    onSelectionChange: (Set<K>) -> Unit,
    scrollState: ScrollableState,
): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val currentKeys by rememberUpdatedState(keys)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    onGloballyPositioned { state.container = it }.then(
        if (!enabled) Modifier else Modifier.pointerInput(state, scrollState) {
            val edge = 64.dp.toPx()
            val maxStep = 18.dp.toPx()
            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startKey = state.keyAt(down.position) ?: return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop

                    // Decide: a horizontal drag or a long press sweeps; anything else is the list's.
                    var lastPosition = down.position
                    // true = horizontal drag, false = not a sweep, null = long press (timed out).
                    val engagedByDrag = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var decision: Boolean? = null
                        while (decision == null) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                decision = false
                            } else {
                                val delta = change.position - down.position
                                if (delta.getDistance() > slop) {
                                    lastPosition = change.position
                                    decision = abs(delta.x) > abs(delta.y)
                                }
                            }
                        }
                        decision
                    }
                    if (engagedByDrag == false) return@awaitEachGesture
                    if (engagedByDrag == null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                    val order = currentKeys
                    val anchor = order.indexOf(startKey)
                    if (anchor < 0) return@awaitEachGesture
                    val original = currentSelected
                    val target = startKey !in original
                    var lastIndex = -1
                    fun sweepTo(position: Offset) {
                        val index = state.keyAt(position)?.let(order::indexOf)?.takeIf { it >= 0 } ?: return
                        if (index == lastIndex) return
                        lastIndex = index
                        val range = order.subList(minOf(anchor, index), maxOf(anchor, index) + 1)
                        currentOnChange(if (target) original + range else original - range.toSet())
                    }
                    sweepTo(down.position)
                    sweepTo(lastPosition)

                    // Scrolls while the finger rests near the top or bottom edge.
                    var autoScroll: Job? = null
                    var speed = 0f
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            event.changes.forEach { it.consume() }   // no scrolling, no taps
                            if (!change.pressed) break
                            lastPosition = change.position
                            sweepTo(lastPosition)
                            val y = lastPosition.y
                            speed = when {
                                y < edge -> -maxStep * ((edge - y) / edge).coerceAtMost(1f)
                                y > size.height - edge -> maxStep * ((y - (size.height - edge)) / edge).coerceAtMost(1f)
                                else -> 0f
                            }
                            if (speed != 0f && autoScroll?.isActive != true) {
                                autoScroll = launch {
                                    while (isActive && speed != 0f) {
                                        withFrameNanos { }
                                        scrollState.scrollBy(speed)
                                        withFrameNanos { }   // let the cells report their new bounds
                                        sweepTo(lastPosition)
                                    }
                                }
                            }
                        }
                    } finally {
                        autoScroll?.cancel()
                    }
                }
            }
        }
    )
}
