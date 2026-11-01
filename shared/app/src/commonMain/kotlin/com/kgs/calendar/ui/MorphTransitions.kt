@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kgs.calendar.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Plumbing for the shared-element morph between the 3-day/month views and the 1-day
 * view. The [SharedTransitionScope] comes from the top-level SharedTransitionLayout and
 * the [AnimatedVisibilityScope] from each AnimatedContent slot. Exposed via composition
 * locals so deeply-nested cards/headers can opt into the morph without threading the
 * scopes through every signature. Both are null outside the view-switch container, in
 * which case [morphBounds] is a no-op.
 */
internal val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalMorphAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

// Multiplier on every view-morph / timeline animation duration. 1 = production speed; raise it
// (e.g. to 3) to slow the motion down for on-device evaluation. (Search for MorphSlowFactor to
// find everything it gates.)
private const val MorphSlowFactor = 1

internal const val MorphDurationMs = 440 * MorphSlowFactor

// Fades for the month-cell <-> day container transform (the only thing morphBounds now drives;
// 3-day<->1-day is the affine overlay). The day block scales from/into the cell via ScaleToBounds;
// on top of that scale we fade it so the background, grid and cards don't pop in/out:
//
//  • month -> day: the day block fades IN over the early part of the morph as it grows out of the
//    cell (a smooth reveal rather than a hard appearance).
//  • day -> month: it fades OUT over the late part as it shrinks back into the cell.
//  • the cell's own pills are dropped/added instantly (snap) so they never scale up into a giant
//    "ballooning" copy behind the real morph.
// The day whose event/task cards should register per-item shared elements (so they morph out of
// the tapped month cell's pills). Set only while a month<->1-day morph is applicable; null in the
// 3-day affine overlay and otherwise, so cards don't register duplicate/ stray shared elements.
internal val LocalMorphItemDay = compositionLocalOf<LocalDate?> { null }

// Option B (per-event pill<->card morph). OFF by deliberate choice. A per-item element travels its
// OWN bounds path (pill -> card) whose size delta is far smaller than the grid's (a pill fills a big
// fraction of its little month cell, but a card is a small fraction of the full day), so it MUST
// reach full size before the cell->full-day grid finishes zooming — it "races ahead" on the way up.
// No easing can remove that; it's geometric. Shrinking-toward-a-point hides it on the way down,
// which is why only the way up ever looked broken. With this OFF, events are plain content inside
// the day block and are scaled by ITS container transform, so they grow/shrink in perfect lockstep
// with the grid in BOTH directions (the up becomes an exact mirror of the good-looking down), and
// the month pills cross-fade into the full cards as the whole day zooms — the real Material/Google
// Calendar container transform. The per-item plumbing below stays behind this flag in case we ever
// want to revisit the independent morph; flip to true to bring it back (with the geometric caveat).
internal const val EnablePerItemMorph = false

private val MorphContentFadeMs = MorphDurationMs * 3 / 10
internal val MorphDayEnter: EnterTransition = fadeIn(tween(MorphContentFadeMs, easing = MorphEasing))
internal val MorphDayExit: ExitTransition =
    fadeOut(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
// The month cell (the morph's container source/target) now cross-fades gradually instead of
// snapping, so the tapped day's lighter background doesn't vanish/appear abruptly.
internal val MorphCellEnter: EnterTransition =
    fadeIn(tween(MorphContentFadeMs, delayMillis = MorphDurationMs - MorphContentFadeMs, easing = MorphEasing))
internal val MorphCellExit: ExitTransition = fadeOut(tween(MorphContentFadeMs, easing = MorphEasing))

// Option B: a single event/task morphing between its month-cell pill and its full 1-day card.
// The CARD side stays visible across (almost) the whole morph — it fades in near the start when
// growing and fades out only at the very end when shrinking — so its scale (and its text scaling
// with it) is visible the entire time, matching the smooth scale-DOWN. The PILL side is the
// opposite (only present at the cell-sized end), so it never lingers as a ballooning copy.
private val MorphItemFadeMs = MorphDurationMs / 6
internal val MorphItemCardEnter: EnterTransition = fadeIn(tween(MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemCardExit: ExitTransition =
    fadeOut(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemPillEnter: EnterTransition =
    fadeIn(tween(MorphItemFadeMs, delayMillis = MorphDurationMs - MorphItemFadeMs, easing = MorphEasing))
internal val MorphItemPillExit: ExitTransition = fadeOut(tween(MorphItemFadeMs, easing = MorphEasing))

// Direction-aware bounds spec for the per-event morph.
//
// The core problem on the way UP (month -> day): a pill fills a big fraction of its little month
// cell, but the full event card is a small fraction of the full day grid. So on a shared easing the
// event's size-fraction shoots up to near-final almost instantly while the grid is still zooming —
// the event "races ahead and finishes early", which is exactly the disconnect. (The same geometric
// mismatch exists on the way down, but shrinking-toward-a-point hides it, which is why the reverse
// already looks right.)
//
// Fix for the growing direction: re-map time so the event's size-fraction TRACKS the grid's instead
// of running ahead. The grid's size-fraction at eased time e is ~e (it zooms from ~0 to full). The
// event's size-fraction is ratio + (1-ratio)*e_item, where ratio = pillSize/cardSize (how big the
// pill already is relative to the final card). Solving for the event to match the grid gives
// e_item = (e - ratio) / (1 - ratio), clamped at 0 — i.e. the event holds at pill size until the
// grid has zoomed up to the pill's relative size, then climbs in lockstep with it and they land
// together. ratio is taken per-item from the actual bounds, so tall cards (which diverge a lot) get
// strongly held back while short all-day chips (which barely diverge) are left almost untouched.
//
// The shrinking direction keeps the plain MorphEasing so the already-perfect reverse is unchanged.
internal val MorphItemBoundsTransform = androidx.compose.animation.BoundsTransform { initial, target ->
    if (target.height >= initial.height) {
        val ratio = (initial.height / target.height).coerceIn(0f, 0.92f)
        val gridTracking = androidx.compose.animation.core.Easing { f ->
            ((MorphEasing.transform(f) - ratio) / (1f - ratio)).coerceIn(0f, 1f)
        }
        tween(MorphDurationMs, easing = gridTracking)
    } else {
        tween(MorphDurationMs, easing = MorphEasing)
    }
}
internal val MorphDayBoundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
    tween(MorphDurationMs, easing = MorphEasing)
}

private val MorphCornerRadiusDp = 12.dp
internal val MorphRoundedClip: SharedTransitionScope.OverlayClip =
    object : SharedTransitionScope.OverlayClip {
        override fun getClipPath(
            sharedContentState: SharedTransitionScope.SharedContentState,
            bounds: androidx.compose.ui.geometry.Rect,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ): androidx.compose.ui.graphics.Path {
            val r = with(density) { MorphCornerRadiusDp.toPx() }
            return androidx.compose.ui.graphics.Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = bounds,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    ),
                )
            }
        }
    }
