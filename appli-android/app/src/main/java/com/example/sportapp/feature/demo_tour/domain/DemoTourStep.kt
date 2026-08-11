package com.example.sportapp.feature.demo_tour.domain

import androidx.annotation.StringRes
import com.example.sportapp.R

/**
 * Étapes du tour visuel post-onboarding (scope C session 2+, granularité fine 2026-05-11).
 *
 * 10 sub-steps : WELCOME -> 8 cadres (Stats x2, Calendar x2, Session x2, Chrono x2)
 * -> GOODBYE. Chaque step à cadre porte un `targetKey` qui identifie l'élément
 * cible dans les écrans (via Modifier.demoHighlight via LocalDemoTourActiveTarget).
 *
 * `targetRouteKind` indique vers quelle page MainActivity doit naviguer quand le
 * step devient actif (NONE = pas de nav, on reste sur l'écran courant).
 *
 * i18n 2026-05-12 : titleRes/bodyRes -> `stringResource` côté DemoCaptionOverlay,
 * permet FR/EN switching live via CompositionLocalProvider global.
 *
 * UPPER_CASE conforme politique 11 (états cross-stack).
 */
enum class DemoTourStep(
    val index: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val targetKey: String? = null,
    val targetRouteKind: TargetRouteKind = TargetRouteKind.NONE,
) {
    WELCOME(
        index = 0,
        titleRes = R.string.demo_welcome_title,
        bodyRes = R.string.demo_welcome_body,
    ),
    STATS_RANGE(
        index = 1,
        titleRes = R.string.demo_stats_range_title,
        bodyRes = R.string.demo_stats_range_body,
        targetKey = "stats.range_picker",
        targetRouteKind = TargetRouteKind.STATS,
    ),
    STATS_CHART(
        index = 2,
        titleRes = R.string.demo_stats_chart_title,
        bodyRes = R.string.demo_stats_chart_body,
        targetKey = "stats.chart",
        targetRouteKind = TargetRouteKind.STATS,
    ),
    CALENDAR_HEADER(
        index = 3,
        titleRes = R.string.demo_calendar_header_title,
        bodyRes = R.string.demo_calendar_header_body,
        targetKey = "calendar.header",
        targetRouteKind = TargetRouteKind.CALENDAR,
    ),
    CALENDAR_GRID(
        index = 4,
        titleRes = R.string.demo_calendar_grid_title,
        bodyRes = R.string.demo_calendar_grid_body,
        targetKey = "calendar.grid",
        targetRouteKind = TargetRouteKind.CALENDAR,
    ),
    SESSION_HEADER(
        index = 5,
        titleRes = R.string.demo_session_header_title,
        bodyRes = R.string.demo_session_header_body,
        targetKey = "session.header",
        targetRouteKind = TargetRouteKind.SESSION,
    ),
    SESSION_EXERCISES(
        index = 6,
        titleRes = R.string.demo_session_exercises_title,
        bodyRes = R.string.demo_session_exercises_body,
        targetKey = "session.exercises",
        targetRouteKind = TargetRouteKind.SESSION,
    ),
    PROGRAM_HEADER(
        index = 7,
        titleRes = R.string.demo_program_header_title,
        bodyRes = R.string.demo_program_header_body,
        targetKey = "program.header",
        targetRouteKind = TargetRouteKind.PROGRAM,
    ),
    PROGRAM_LIST(
        index = 8,
        titleRes = R.string.demo_program_list_title,
        bodyRes = R.string.demo_program_list_body,
        targetKey = "program.list",
        targetRouteKind = TargetRouteKind.PROGRAM,
    ),
    CHRONO_TABS(
        index = 9,
        titleRes = R.string.demo_chrono_tabs_title,
        bodyRes = R.string.demo_chrono_tabs_body,
        targetKey = "chrono.tabs",
        targetRouteKind = TargetRouteKind.CHRONO,
    ),
    CHRONO_BUTTONS(
        index = 10,
        titleRes = R.string.demo_chrono_buttons_title,
        bodyRes = R.string.demo_chrono_buttons_body,
        targetKey = "chrono.buttons",
        targetRouteKind = TargetRouteKind.CHRONO,
    ),
    GOODBYE(
        index = 11,
        titleRes = R.string.demo_goodbye_title,
        bodyRes = R.string.demo_goodbye_body,
    ),
    ;

    fun next(): DemoTourStep? = entries.getOrNull(index + 1)

    /** Action button label res : "Next" pour les steps intermédiaires, "Got it" pour GOODBYE. */
    @get:StringRes
    val nextLabelRes: Int
        get() = if (this == GOODBYE) R.string.demo_tour_got_it else R.string.demo_tour_next
}

/**
 * Route cible quand le step devient actif. MainActivity résout vers le bon
 * Routes.X (et passe l'UUID du 1er sample workout pour SESSION).
 */
enum class TargetRouteKind { NONE, STATS, CALENDAR, SESSION, PROGRAM, CHRONO }
