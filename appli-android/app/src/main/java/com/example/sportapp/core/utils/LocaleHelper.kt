package com.example.sportapp.core.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.example.sportapp.R
import com.example.sportapp.feature.onboarding.data.AppLocale

/**
 * i18n (Session A 2026-05-11) -- thin wrapper sur AppCompatDelegate pour
 * appliquer la locale per-app. AppCompat persiste lui-meme la valeur (API 33+
 * via LocaleManager systeme, API 29-32 via backport SharedPrefs interne), donc
 * pas besoin de re-apply au boot : le DataStore [OnboardingPreferences.appLocale]
 * sert uniquement de source de verite pour l'UI (RadioGroup selected state).
 *
 * AppLocale.SYSTEM -> LocaleListCompat vide (= suit la locale Android systeme).
 */
object LocaleHelper {

    fun apply(locale: AppLocale) {
        val tags = locale.tag
        val list = if (tags == null) LocaleListCompat.getEmptyLocaleList()
                   else LocaleListCompat.forLanguageTags(tags)
        AppCompatDelegate.setApplicationLocales(list)
    }
}

/**
 * Mappe un day-of-week stocke EN canonique (DB, "Monday".."Sunday", + "Rest Day"
 * filler) vers le label localise pour l'affichage. Insensible a la casse.
 * Si la cle n'est reconnue ni jour ni "Rest Day" -> renvoie la string telle quelle
 * (preserve les noms d'evenements custom potentiels, type "Holiday").
 */
@Composable
fun localizedDayOfWeek(dayKey: String): String = when (dayKey.trim().lowercase()) {
    "monday" -> stringResource(R.string.day_monday)
    "tuesday" -> stringResource(R.string.day_tuesday)
    "wednesday" -> stringResource(R.string.day_wednesday)
    "thursday" -> stringResource(R.string.day_thursday)
    "friday" -> stringResource(R.string.day_friday)
    "saturday" -> stringResource(R.string.day_saturday)
    "sunday" -> stringResource(R.string.day_sunday)
    "rest day" -> stringResource(R.string.day_rest_day)
    else -> dayKey
}

/**
 * Mappe un zone-name stocke EN canonique (DB : Chest/Back/Shoulders/Arms/Legs/
 * Core/Other) vers le label localise. Cles non reconnues = passe-plat.
 */
@Composable
fun localizedZone(zoneKey: String): String = when (zoneKey.trim().lowercase()) {
    "chest" -> stringResource(R.string.zone_chest)
    "back" -> stringResource(R.string.zone_back)
    "shoulders" -> stringResource(R.string.zone_shoulders)
    "arms" -> stringResource(R.string.zone_arms)
    "legs" -> stringResource(R.string.zone_legs)
    "core" -> stringResource(R.string.zone_core)
    "other" -> stringResource(R.string.zone_other)
    else -> zoneKey
}

/**
 * Mappe un code de statut wire UPPER_CASE (DONE, NOT_STARTED, IN_PROGRESS,
 * SKIPPED, PLANNED) vers le label localise pour l'affichage. Normalise la casse
 * et les separateurs (espace ou underscore equivalents : "NOT STARTED" ==
 * "NOT_STARTED"). Code wire inchange en stockage (politique 11) : display only.
 * Cle non reconnue -> fallback Title Case de la chaine brute.
 */
@Composable
fun localizedStatus(statusCode: String): String =
    when (statusCode.trim().uppercase().replace(" ", "_")) {
        "NOT_STARTED" -> stringResource(R.string.status_not_started)
        "IN_PROGRESS" -> stringResource(R.string.status_in_progress)
        "DONE" -> stringResource(R.string.status_done)
        "SKIPPED" -> stringResource(R.string.status_skipped)
        "PLANNED" -> stringResource(R.string.status_planned)
        else -> statusCode.lowercase().split("_", " ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

/**
 * Mappe un code de priorite wire UPPER_CASE (HIGH/MEDIUM/LOW) vers le label
 * localise pour l'affichage. Code wire inchange en stockage (politique 11) :
 * display only. Cle non reconnue -> fallback Title Case de la chaine brute.
 */
@Composable
fun localizedPriority(priorityCode: String): String =
    when (priorityCode.trim().uppercase()) {
        "HIGH" -> stringResource(R.string.priority_high)
        "MEDIUM" -> stringResource(R.string.priority_medium)
        "LOW" -> stringResource(R.string.priority_low)
        else -> priorityCode.lowercase().replaceFirstChar { it.uppercase() }
    }
