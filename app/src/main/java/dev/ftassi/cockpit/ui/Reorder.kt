package dev.ftassi.cockpit.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.zIndex
import dev.ftassi.cockpit.layout.Slot
import dev.ftassi.cockpit.layout.moveItem
import dev.ftassi.cockpit.layout.nearestSlot
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HOLD_MS = 2_000L
private const val FULL_STRENGTH = 255

// The system's own haptic feedback is too light to notice in a car.
enum class Buzz(val effect: () -> VibrationEffect) {
    Enter({ VibrationEffect.createOneShot(120, FULL_STRENGTH) }),
    Pick({ VibrationEffect.createOneShot(60, FULL_STRENGTH) }),
    Slot({ VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK) }),
}

fun Context.vibrate(buzz: Buzz) = getSystemService(Vibrator::class.java).vibrate(buzz.effect())
private const val WIGGLE_DEGREES = 1.2f
private const val WIGGLE_MS = 130
private const val DRAG_SCALE = 1.08f

// Calls onHold after a finger stays still that long. It only watches, so the
// tiles underneath still get their normal taps.
fun Modifier.holdToReorganize(onHold: () -> Unit): Modifier = composed {
    val slop = LocalViewConfiguration.current.touchSlop
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val released = withTimeoutOrNull(HOLD_MS) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.first()
                    val moved = change.position - down.position
                    if (event.changes.fastAll { !it.pressed } || abs(moved.x) > slop || abs(moved.y) > slop) break
                }
                true
            }
            if (released == null) onHold()
        }
    }
}

// Tiles of one area, placed by hand on fixed slots: while one follows the finger
// the others slide to make room, and an outline shows where it will land. Radios
// and playlists have one area each, so they cannot mix. The first slots can be
// left to something fixed, like the Spotify logo.
@Composable
fun ReorderableArea(
    keys: List<String>,
    slots: List<Slot>,
    onDrop: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    fixedSlots: Int = 0,
    item: @Composable (String) -> Unit,
) {
    val context = LocalContext.current
    // One state for the area's whole life: the gesture below outlives every
    // recomposition and would keep writing to a state replaced by remember(keys).
    var order by remember { mutableStateOf(keys) }
    var dragged by remember { mutableStateOf<String?>(null) }
    if (dragged == null && order != keys) order = keys
    var position by remember { mutableStateOf(Offset.Zero) }
    val currentSlots by rememberUpdatedState(slots)
    val currentOrder by rememberUpdatedState(order)
    val currentDrop by rememberUpdatedState(onDrop)
    val wiggle by rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = -WIGGLE_DEGREES,
        targetValue = WIGGLE_DEGREES,
        animationSpec = infiniteRepeatable(tween(WIGGLE_MS), RepeatMode.Reverse),
        label = "wiggle",
    )
    val density = LocalDensity.current

    Box(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { start ->
                    val index = currentOrder.indices.firstOrNull { currentSlots[it + fixedSlots].contains(start) }
                        ?: return@detectDragGestures
                    val slot = currentSlots[index + fixedSlots]
                    dragged = currentOrder[index]
                    position = Offset(slot.x, slot.y)
                    context.vibrate(Buzz.Pick)
                },
                onDragEnd = {
                    if (dragged != null) currentDrop(currentOrder)
                    dragged = null
                },
                onDragCancel = {
                    if (dragged != null) currentDrop(currentOrder)
                    dragged = null
                },
                onDrag = { change, amount ->
                    val moving = dragged ?: return@detectDragGestures
                    change.consume()
                    position += amount
                    val own = currentOrder.indexOf(moving) + fixedSlots
                    val size = currentSlots[own]
                    val nearest = nearestSlot(position.x + size.width / 2, position.y + size.height / 2, currentSlots, fixedSlots)
                    if (nearest != own) {
                        order = moveItem(currentOrder, moving, currentOrder[nearest - fixedSlots])
                        context.vibrate(Buzz.Slot)
                    }
                },
            )
        },
    ) {
        dragged?.let { moving ->
            val slot = slots[order.indexOf(moving) + fixedSlots]
            Box(
                Modifier.slotPlace(IntOffset(slot.x.roundToInt(), slot.y.roundToInt()), slot, density)
                    .border(2.dp, Accent, RoundedCornerShape(24.dp)),
            )
        }
        order.forEachIndexed { index, itemKey ->
            key(itemKey) {
                val slot = slots[index + fixedSlots]
                val isDragged = itemKey == dragged
                val animated by animateIntOffsetAsState(IntOffset(slot.x.roundToInt(), slot.y.roundToInt()), spring(stiffness = Spring.StiffnessMediumLow), label = "slot")
                // Opposite directions look like the icons on a phone home, not a shaking screen.
                val direction = if (index % 2 == 0) 1f else -1f
                Box(
                    Modifier
                        .slotPlace(if (isDragged) IntOffset(position.x.roundToInt(), position.y.roundToInt()) else animated, slot, density)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            rotationZ = if (isDragged) 0f else wiggle * direction
                            val scale = if (isDragged) DRAG_SCALE else 1f
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (isDragged) 24f else 0f
                        },
                ) {
                    item(itemKey)
                }
            }
        }
    }
}

private fun Modifier.slotPlace(offset: IntOffset, slot: Slot, density: Density): Modifier = with(density) {
    this@slotPlace.offset { offset }.size(slot.width.toDp(), slot.height.toDp())
}

private fun Slot.contains(point: Offset) = point.x in x..(x + width) && point.y in y..(y + height)
