package com.kgs.calendar.widget

import com.kgs.calendar.R

internal const val TAG = "KgsWidget"
internal const val EXTRA_WIDGET_KIND = "kgs_widget_kind"
internal const val EXTRA_WIDGET_DATE = "kgs_widget_date"
internal const val EXTRA_WIDGET_ACTION = "kgs_widget_action"
internal const val EXTRA_WIDGET_TASK_UID = "kgs_widget_task_uid"
internal const val EXTRA_WIDGET_EVENT_UID = "kgs_widget_event_uid"
internal const val EXTRA_WIDGET_TASK_CREATE_MODE = "kgs_widget_task_create_mode"
internal const val WIDGET_ACTION_CREATE_EVENT = "create_event"
internal const val WIDGET_ACTION_CREATE_TASK = "create_task"
internal const val WIDGET_ACTION_OPEN_TASK = "open_task"
internal const val WIDGET_ACTION_OPEN_EVENT = "open_event"
internal const val WIDGET_TASK_CREATE_UNPLANNED = "Unplanned"
internal const val EXTRA_COLLECTION_ACTION = "kgs_widget_collection_action"
internal const val COLLECTION_ACTION_OPEN = "open"
internal const val COLLECTION_ACTION_TOGGLE_TASK = "toggle_task"
internal const val COLLECTION_ACTION_TOGGLE_SUBTASKS = "toggle_subtasks"
internal const val WIDGET_MONTH_DOT_SIZE_DP = 3.5f
internal const val WIDGET_MONTH_SPAN_FADE_WIDTH_DP = 14f
internal const val WIDGET_MONTH_SPAN_FADE_TEXT_INSET_DP = 3f
internal const val WIDGET_MONTH_RESIZE_DEBOUNCE_MS = 320L
internal const val WIDGET_MULTI_CONTENT_PADDING_DP = 12
internal const val WIDGET_MONTH_RENDER_SIGNATURE_VERSION = 41
internal const val WIDGET_DAY_RENDER_SIGNATURE_VERSION = 10
internal const val WIDGET_DAY_START_HOUR = 0
internal const val WIDGET_DAY_END_HOUR = 23
internal const val WIDGET_DAY_DEFAULT_TASK_DURATION_MILLIS = 30L * 60L * 1000L
internal const val WIDGET_DAY_ROOT_PADDING_DP = 12
internal const val WIDGET_DAY_LIST_SIDE_BLEED_DP = 0f
internal const val WIDGET_DAY_ROW_SIDE_INSET_DP = 0f
internal const val WIDGET_DAY_HEADER_HEIGHT_DP = 38
internal const val WIDGET_DAY_TIMELINE_TOP_MARGIN_DP = 6
internal const val WIDGET_DAY_HOUR_ROW_HEIGHT_DP = 46
internal const val WIDGET_DAY_ALL_DAY_CARD_HEIGHT_DP = 24
internal const val WIDGET_DAY_ALL_DAY_TOP_PADDING_DP = 7f
internal const val WIDGET_DAY_ALL_DAY_LANE_STRIDE_DP = 29f
internal const val WIDGET_DAY_ALL_DAY_EXPANSION_STEPS = 5
internal const val WIDGET_DAY_ALL_DAY_EXPANSION_FRAME_DELAY_MS = 40L
internal const val WIDGET_DAY_CARD_RADIUS_DP = 10f
internal const val WIDGET_DAY_TIME_COLUMN_WIDTH_DP = 30f
internal const val WIDGET_DAY_GRID_GAP_DP = 0f
internal const val WIDGET_DAY_CARD_MIN_HEIGHT_DP = 6f
internal const val WIDGET_DAY_CARD_MIN_TOUCH_HEIGHT_DP = 22f
internal const val WIDGET_DAY_PRIORITY_OVERDRAW_DP = 10f
internal const val WIDGET_DAY_PRIORITY_BITMAP_SCALE = 1.15f
internal const val WIDGET_PRIORITY_MOTION_ITEM_LIMIT = 15
internal const val WIDGET_DAY_MAX_PRIORITY_MOTION_ROWS = WIDGET_PRIORITY_MOTION_ITEM_LIMIT
internal const val WIDGET_DAY_TITLE_FADE_WIDTH_DP = 14f
internal const val WIDGET_DAY_BOUNDARY_ROW_HEIGHT_DP = 18f
internal const val WIDGET_COLLECTION_RENDER_SIGNATURE_VERSION = 11
internal const val WIDGET_TASK_MAX_DEPTH = WidgetTaskCardRenderer.MAX_DEPTH
internal const val WIDGET_TASK_ART_WIDTH_DP = 360
internal const val WIDGET_TASK_MIN_CARD_WIDTH_DP = WidgetTaskCardRenderer.MIN_CARD_WIDTH_DP
internal const val WIDGET_TASK_ROW_HEIGHT_DP = WidgetTaskCardRenderer.ROW_HEIGHT_DP
internal const val WIDGET_TASK_CARD_HEIGHT_DP = WidgetTaskCardRenderer.CARD_HEIGHT_DP
internal const val WIDGET_TASK_CARD_SIDE_INSET_DP = WidgetTaskCardRenderer.CARD_SIDE_INSET_DP
internal const val WIDGET_TASK_STATUS_RADIUS_DP = WidgetTaskCardRenderer.STATUS_RADIUS_DP
internal const val WIDGET_TASK_STATUS_STROKE_DP = WidgetTaskCardRenderer.STATUS_STROKE_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_HALF_WIDTH_DP = WidgetTaskCardRenderer.CHEVRON_HALF_WIDTH_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_HALF_HEIGHT_DP = WidgetTaskCardRenderer.CHEVRON_HALF_HEIGHT_DP
internal const val WIDGET_TASK_SUBTASK_ARROW_STROKE_DP = WidgetTaskCardRenderer.CHEVRON_STROKE_DP
internal val WIDGET_TASK_CARD_RENDERER = WidgetTaskCardRenderer()
internal const val WIDGET_TASKS_PRIORITY_FRAME_COUNT = 20
internal const val WIDGET_AGENDA_PRIORITY_FRAME_COUNT = 30
internal const val WIDGET_COLLECTION_VIEW_TYPE_COUNT = 8
internal const val WIDGET_AGENDA_ART_BITMAP_SCALE = 1.15f
internal const val WIDGET_TASK_PRIORITY_BITMAP_SCALE = 1.15f
internal const val WIDGET_TASK_TRANSITION_BITMAP_SCALE = 0.78f
internal const val WIDGET_AGENDA_DATE_COLUMN_WIDTH_DP = 50
internal const val WIDGET_AGENDA_COLUMN_GAP_DP = 12
internal const val WIDGET_AGENDA_EVENT_ROW_HEIGHT_DP = 64
internal const val WIDGET_AGENDA_SPAN_EVENT_ROW_HEIGHT_DP = 96
internal const val WIDGET_DAY_EVENT_ROW_HEIGHT_DP = 52
internal const val WIDGET_AGENDA_EVENT_CARD_RADIUS_DP = 13f
internal const val WIDGET_DAY_EVENT_CARD_RADIUS_DP = 10f
internal const val WIDGET_AGENDA_MAX_ROWS = 60
internal const val WIDGET_AGENDA_LIST_SIDE_BLEED_DP = 12
internal const val WIDGET_TASK_PRIORITY_OVERDRAW_DP = 20f
internal const val WIDGET_TASK_SORT_MORPH_STEPS = 5
internal const val WIDGET_TASK_SORT_MORPH_FRAME_DELAY_MS = 42L
internal const val WIDGET_TASK_SORT_LABEL_TRANSITION_MS = 155L
internal const val WIDGET_TASK_EXPANSION_STEPS = 5
internal const val WIDGET_TASK_EXPANSION_FRAME_DELAY_MS = 34L
internal const val WIDGET_TASK_TRANSITION_MIN_ROW_HEIGHT_DP = 1f
internal val WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS = intArrayOf(
    R.id.widget_task_priority_motion_a,
    R.id.widget_task_priority_motion_b,
    R.id.widget_task_priority_motion_c,
    R.id.widget_task_priority_motion_d,
    R.id.widget_task_priority_motion_e,
    R.id.widget_task_priority_motion_f,
    R.id.widget_task_priority_motion_g,
    R.id.widget_task_priority_motion_h,
    R.id.widget_task_priority_motion_i,
    R.id.widget_task_priority_motion_j,
    R.id.widget_task_priority_motion_k,
    R.id.widget_task_priority_motion_l,
    R.id.widget_task_priority_motion_m,
    R.id.widget_task_priority_motion_n,
    R.id.widget_task_priority_motion_o,
    R.id.widget_task_priority_motion_p,
    R.id.widget_task_priority_motion_q,
    R.id.widget_task_priority_motion_r,
    R.id.widget_task_priority_motion_s,
    R.id.widget_task_priority_motion_t,
)
internal val WIDGET_AGENDA_PRIORITY_MOTION_FRAME_IDS = WIDGET_TASK_PRIORITY_MOTION_FRAME_IDS + intArrayOf(
    R.id.widget_task_priority_motion_u,
    R.id.widget_task_priority_motion_v,
    R.id.widget_task_priority_motion_w,
    R.id.widget_task_priority_motion_x,
    R.id.widget_task_priority_motion_y,
    R.id.widget_task_priority_motion_z,
    R.id.widget_task_priority_motion_aa,
    R.id.widget_task_priority_motion_ab,
    R.id.widget_task_priority_motion_ac,
    R.id.widget_task_priority_motion_ad,
)
