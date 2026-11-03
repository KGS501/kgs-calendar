package com.kgs.calendar.ui.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A right click (the secondary mouse button) on this element: [onClick] gets where, in its own
 * coordinates, once the button is let go, as menus open on Windows. The innermost element with
 * one wins: it uses up the click, so a card doesn't also react when its checkbox does. Plain
 * clicks never react to the secondary button, so both can sit on one element.
 */
fun Modifier.onSecondaryClick(enabled: Boolean = true, onClick: (Offset) -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        composed {
            val currentOnClick by rememberUpdatedState(onClick)
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    var pressed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Press -> {
                                pressed = event.buttons.isSecondaryPressed && !event.buttons.isPrimaryPressed && !change.isConsumed
                                if (pressed) change.consume()
                            }
                            PointerEventType.Release -> if (pressed) {
                                pressed = false
                                if (!change.isConsumed) {
                                    change.consume()
                                    currentOnClick(change.position)
                                }
                            }
                        }
                    }
                }
            }
        }.then(this)
    }
