package com.kgs.calendar.widget.update

import android.appwidget.AppWidgetManager
import android.os.SystemClock
import android.util.Log
import com.kgs.calendar.R
import com.kgs.calendar.widget.KgsWidgetKind
import com.kgs.calendar.widget.TAG
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_EXPANSION_FRAME_DELAY_MS
import com.kgs.calendar.widget.WIDGET_DAY_ALL_DAY_EXPANSION_STEPS
import com.kgs.calendar.widget.WidgetDependencies
import com.kgs.calendar.widget.WidgetLog
import com.kgs.calendar.widget.data.warmWidgetMonthPageCache
import com.kgs.calendar.widget.model.MonthNavSnapshot
import com.kgs.calendar.widget.model.MonthWidgetRenderResult
import com.kgs.calendar.widget.model.authoritativeMonthPageDecision
import com.kgs.calendar.widget.model.motionStandardEasing
import com.kgs.calendar.widget.model.next
import com.kgs.calendar.widget.model.selectMonthNavigationInitialPage
import com.kgs.calendar.widget.model.shouldBuildMonthRemoteViews
import com.kgs.calendar.widget.model.usesCollectionList
import com.kgs.calendar.widget.model.usesDirectCollectionItems
import com.kgs.calendar.widget.model.usesDirectDayGridItems
import com.kgs.calendar.widget.state.WidgetMonthPageFreshness
import com.kgs.calendar.widget.state.dayAllDayTokenKey
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal class KgsWidgetUpdater(private val widgets: WidgetDependencies) {
    private val context = widgets.appContext
    private val state = widgets.state

    suspend fun update(
        kind: KgsWidgetKind,
        appWidgetIds: IntArray,
        forceFullDayUpdate: Boolean = false,
        cause: WidgetUpdateCause = WidgetUpdateCause.Unknown,
    ) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val renderer = widgets.renderer()
        appWidgetIds.forEach { appWidgetId ->
            WidgetPerformanceMonitor.trace(context, state.bitmapUris, kind, appWidgetId, cause) { metrics ->
            val options = manager.getAppWidgetOptions(appWidgetId)
            val targetMonthRevision = if (kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi) {
                state.month.revision(appWidgetId)
            } else {
                null
            }
            val incrementalDayUpdate =
                kind == KgsWidgetKind.Day &&
                    !forceFullDayUpdate &&
                    state.day.isInitialized(appWidgetId)
            var monthResult: MonthWidgetRenderResult? = null
            val preparedMulti = if (kind == KgsWidgetKind.Multi) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                runCatching {
                    renderer.prepareMultiUpdate(appWidgetId, options)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to calculate Multi widget $appWidgetId snapshot", error)
                }.getOrNull().also { prepared ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = prepared?.itemCount ?: 0,
                    )
                }
            } else {
                null
            }
            val collectionSnapshot = if (kind.usesCollectionList && kind != KgsWidgetKind.Multi) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                runCatching {
                    renderer.collectionSnapshot(kind, appWidgetId)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to calculate ${kind.name} widget $appWidgetId snapshot", error)
                }.getOrNull().also { snapshot ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = snapshot?.rows?.size ?: 0,
                    )
                }
            } else {
                preparedMulti?.collectionSnapshot
            }
            val preparedMonth = if (kind == KgsWidgetKind.Month) {
                val dataLoadStarted = SystemClock.elapsedRealtime()
                renderer.prepareMonthUpdate(appWidgetId, options).also { prepared ->
                    metrics.recordDataLoad(
                        durationMillis = SystemClock.elapsedRealtime() - dataLoadStarted,
                        rowsBuilt = prepared.itemCount,
                    )
                }
            } else {
                null
            }
            val collectionSignature = preparedMulti?.signature ?: collectionSnapshot?.signature
            if (collectionSignature != null && state.collectionSignatures.matches(kind, appWidgetId, collectionSignature)) {
                WidgetLog.d(context, "Skipped unchanged ${kind.name} widget $appWidgetId")
                return@trace
            }
            if (
                preparedMonth != null &&
                !shouldBuildMonthRemoteViews(preparedMonth.signature) { signature ->
                    state.monthSignatures.matches(appWidgetId, signature)
                }
            ) {
                WidgetLog.d(context, "Skipped unchanged Month widget $appWidgetId before RemoteViews build")
                return@trace
            }
            val renderStarted = SystemClock.elapsedRealtime()
            val views = try {
                if (kind == KgsWidgetKind.Month) {
                    renderer.renderMonthUpdate(requireNotNull(preparedMonth))
                        .also { monthResult = it }
                        .views
                } else if (incrementalDayUpdate) {
                    renderer.renderDayNavigationUpdate(
                        appWidgetId = appWidgetId,
                        options = options,
                    )
                } else {
                    renderer.render(
                        kind = kind,
                        appWidgetId = appWidgetId,
                        options = options,
                        collectionSnapshot = collectionSnapshot,
                        preparedMulti = preparedMulti,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render ${kind.name} widget $appWidgetId", error)
                renderer.error(kind, appWidgetId, error.message ?: "Widget update failed.")
            }
            metrics.recordRemoteViewsBuild(SystemClock.elapsedRealtime() - renderStarted)
            val signature = monthResult?.signature
            if (
                targetMonthRevision != null &&
                state.month.revision(appWidgetId) != targetMonthRevision
            ) {
                return@trace
            }
            if (signature != null && state.monthSignatures.matches(appWidgetId, signature)) {
                WidgetLog.d(context, "Skipped unchanged Month widget $appWidgetId")
                return@trace
            }
            runCatching {
                if (collectionSnapshot != null) {
                    state.collectionRows.put(collectionSnapshot)
                }

                val applyUpdate = {
                    val applyStarted = SystemClock.elapsedRealtime()
                    if (incrementalDayUpdate) {
                        manager.partiallyUpdateAppWidget(appWidgetId, views)
                    } else {
                        manager.updateAppWidget(appWidgetId, views)
                    }
                    metrics.recordBinderApply(SystemClock.elapsedRealtime() - applyStarted)
                    if (
                        (kind.usesCollectionList && !kind.usesDirectCollectionItems() && kind != KgsWidgetKind.Day) ||
                        (kind == KgsWidgetKind.Day && !usesDirectDayGridItems())
                    ) {
                        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                    }
                    if (kind == KgsWidgetKind.Day) {
                        state.day.markInitialized(appWidgetId)
                    }
                    if (signature != null) {
                        state.monthSignatures.markApplied(appWidgetId, signature)
                    }
                    if (collectionSignature != null) {
                        state.collectionSignatures.markApplied(kind, appWidgetId, collectionSignature)
                    }
                }
                if (targetMonthRevision != null) {
                    state.month.applyIfRevisionCurrent(
                        appWidgetId = appWidgetId,
                        revision = targetMonthRevision,
                        block = applyUpdate,
                    )
                } else {
                    applyUpdate()
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId", error)
                if (
                    (kind.usesDirectCollectionItems() || (kind == KgsWidgetKind.Day && usesDirectDayGridItems())) &&
                    error.isRemoteViewsBitmapMemoryError()
                ) {
                    runCatching {
                        val fallbackViews = renderer.render(
                            kind = kind,
                            appWidgetId = appWidgetId,
                            options = options,
                            forceServiceCollection = true,
                            collectionSnapshot = collectionSnapshot,
                            preparedMulti = preparedMulti,
                        )
                        val applyFallback = {
                            manager.updateAppWidget(appWidgetId, fallbackViews)
                            manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
                        }
                        if (targetMonthRevision != null) {
                            state.month.applyIfRevisionCurrent(
                                appWidgetId = appWidgetId,
                                revision = targetMonthRevision,
                                block = applyFallback,
                            )
                        } else {
                            applyFallback()
                        }
                    }.onFailure { fallbackError ->
                        Log.e(TAG, "Failed to update ${kind.name} widget $appWidgetId with service collection fallback", fallbackError)
                    }
                }
            }
            }
        }
    }

    suspend fun navigateMonth(
        kind: KgsWidgetKind,
        appWidgetId: Int,
        snapshot: MonthNavSnapshot,
    ) {
        require(kind == KgsWidgetKind.Month || kind == KgsWidgetKind.Multi)
        require(snapshot.widgetId == appWidgetId)
        val manager = AppWidgetManager.getInstance(context)
        val zoneId = ZoneId.systemDefault()
        val dataSource = widgets.dataSource(zoneId)
        val pageSource = widgets.monthPageSource(zoneId)
        val renderer = widgets.renderer(zoneId)
        val options = manager.getAppWidgetOptions(appWidgetId)
        val settings = dataSource.loadSettings(kind)

        fun applyIfCurrent(result: MonthWidgetRenderResult): Boolean =
            runCatching {
                state.month.applyIfCurrent(snapshot) {
                    if (kind == KgsWidgetKind.Multi) {
                        manager.partiallyUpdateAppWidget(appWidgetId, result.views)
                    } else {
                        manager.updateAppWidget(appWidgetId, result.views)
                    }
                    result.signature?.let { signature ->
                        state.monthSignatures.markApplied(appWidgetId, signature)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to apply ${kind.name} month page $appWidgetId", error)
            }.getOrDefault(false)

        var generation = state.dataGeneration.current()
        val cachedLookup = state.monthPages.getForNavigation(
            snapshot.month,
            settings,
            zoneId.id,
            generation,
        )
        val initialPage = selectMonthNavigationInitialPage(cachedLookup?.page)

        if (initialPage != null) {
            if (!applyIfCurrent(
                    renderer.renderMonthNavigationPage(
                        kind = kind,
                        snapshot = snapshot,
                        options = options,
                        settings = settings,
                        page = initialPage,
                        hasCompleteData = true,
                    ),
                )
            ) {
                return
            }
        }
        if (!state.month.isCurrent(snapshot)) return
        if (
            cachedLookup?.freshness == WidgetMonthPageFreshness.CurrentGeneration &&
            state.dataGeneration.current() == generation
        ) {
            warmWidgetMonthPageCache(widgets, zoneId, snapshot.month, settings)
            return
        }
        var authoritativePage = try {
            pageSource.load(snapshot.month, settings)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to load ${kind.name} month page $appWidgetId", error)
            return
        }
        if (state.dataGeneration.current() != generation) {
            generation = state.dataGeneration.current()
            if (!state.month.isCurrent(snapshot)) return
            authoritativePage = try {
                pageSource.load(snapshot.month, settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to reload ${kind.name} month page $appWidgetId", error)
                return
            }
        }
        val authoritativeDecision = authoritativeMonthPageDecision(
            navigationCurrent = state.month.isCurrent(snapshot),
            loadedGeneration = generation,
            currentGeneration = state.dataGeneration.current(),
        )
        if (!authoritativeDecision.apply) return
        if (authoritativeDecision.cache) {
            state.monthPages.put(
                month = snapshot.month,
                settings = settings,
                zoneId = zoneId.id,
                page = authoritativePage,
                generation = generation,
            )
            warmWidgetMonthPageCache(widgets, zoneId, snapshot.month, settings)
        }
        if (cachedLookup?.page != authoritativePage) {
            applyIfCurrent(
                renderer.renderMonthNavigationPage(
                    kind = kind,
                    snapshot = snapshot,
                    options = options,
                    settings = settings,
                    page = authoritativePage,
                    hasCompleteData = true,
                ),
            )
        }
    }

    suspend fun navigateDay(appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val renderer = widgets.renderer()
        val options = manager.getAppWidgetOptions(appWidgetId)
        val views = try {
            renderer.renderDayNavigationUpdate(
                appWidgetId = appWidgetId,
                options = options,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to render Day widget navigation $appWidgetId", error)
            renderer.error(KgsWidgetKind.Day, appWidgetId, error.message ?: "Widget update failed.")
        }
        runCatching {
            manager.partiallyUpdateAppWidget(appWidgetId, views)
            if (!usesDirectDayGridItems()) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to navigate Day widget $appWidgetId", error)
            manager.updateAppWidget(appWidgetId, views)
            if (!usesDirectDayGridItems()) {
                manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
            }
        }
    }

    suspend fun toggleDayAllDay(appWidgetId: Int) {
        if (!usesDirectDayGridItems()) {
            val nextExpanded = !state.day.isAllDayExpanded(appWidgetId)
            state.day.setAllDayExpanded(appWidgetId, nextExpanded)
            update(KgsWidgetKind.Day, intArrayOf(appWidgetId))
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        val renderer = widgets.renderer()
        val options = manager.getAppWidgetOptions(appWidgetId)
        val targetExpanded = !state.day.isAllDayExpanded(appWidgetId)
        state.day.setAllDayExpanded(appWidgetId, targetExpanded)
        val token = state.interactionTokens.next(dayAllDayTokenKey(appWidgetId))
        val frameData = try {
            renderer.dayAllDaySectionFrameData(appWidgetId, options)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "Failed to prepare Day widget all-day expansion $appWidgetId", error)
            update(KgsWidgetKind.Day, intArrayOf(appWidgetId))
            return
        }
        for (step in 0..WIDGET_DAY_ALL_DAY_EXPANSION_STEPS) {
            if (!state.interactionTokens.isCurrent(dayAllDayTokenKey(appWidgetId), token)) return
            val rawProgress = step.toFloat() / WIDGET_DAY_ALL_DAY_EXPANSION_STEPS.toFloat()
            val eased = motionStandardEasing(rawProgress)
            val progress = if (targetExpanded) eased else 1f - eased
            val views = try {
                renderer.renderDayAllDaySectionFrame(
                    frameData = frameData,
                    expansionProgress = progress,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "Failed to render Day widget all-day expansion frame $appWidgetId", error)
                update(KgsWidgetKind.Day, intArrayOf(appWidgetId))
                return
            }
            runCatching {
                manager.partiallyUpdateAppWidget(appWidgetId, views)
            }.onFailure { error ->
                Log.e(TAG, "Failed to update Day widget all-day expansion frame $appWidgetId", error)
                update(KgsWidgetKind.Day, intArrayOf(appWidgetId))
                return
            }
            if (step < WIDGET_DAY_ALL_DAY_EXPANSION_STEPS) {
                delay(WIDGET_DAY_ALL_DAY_EXPANSION_FRAME_DELAY_MS)
            }
        }
    }
}

internal fun Throwable.isRemoteViewsBitmapMemoryError(): Boolean =
    this is IllegalArgumentException &&
        message?.contains("bitmap", ignoreCase = true) == true &&
        message?.contains("memory", ignoreCase = true) == true
