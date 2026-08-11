package com.example.sportapp.core.utils

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeParseException


object CustomDateUtils {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    /** Formatter du format wire canonique projet : 6 décimales fixes, suffixe Z, UTC. */
    private val canonicalFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

    /** Renvoie l’instant courant en UTC (Instant) */
    fun getNowInstant(): Instant = Instant.now()

    /** Renvoie la date courante (jour) en UTC (LocalDate) */
    fun getTodayLocalDate(): LocalDate = LocalDate.now(ZoneOffset.UTC)

    /** Renvoie la date courante (jour) dans la timezone fournie (LocalDate) */
    fun getTodayLocalDate(zone: ZoneId): LocalDate = LocalDate.now(zone)

    /**
     * Renvoie l'instant courant au **format wire canonique projet** :
     *
     *     "YYYY-MM-DDTHH:MM:SS.UUUUUUZ"
     *
     * (ISO 8601, UTC strict, **6 décimales fixes** — microsec, suffixe `Z`).
     *
     * Imposé par 3 mécanismes :
     *  1. Postgres triggers : `iso_utc(rec.X)` (cf. `db_triggers/iso_utc_helper.sql`)
     *  2. Pydantic schemas  : type `UTCDateTime` (cf. `app/utc_datetime.py`)
     *  3. Android producteur: cette fonction
     *
     * Toute valeur reçue est tolérée par `parseInstantSafe()` (3 fallbacks
     * pour le legacy), mais la canonical est ce qui est émis ici.
     *
     * Voir docs/DATES.md §"Après V3.2" pour le contexte complet.
     */
    fun getNowISO8601(): String =
        canonicalFormatter.format(Instant.now().truncatedTo(ChronoUnit.MICROS))

    /** Convertit un LocalDate en ISO8601 UTC Z (début de journée) */
    fun toISO8601(localDate: LocalDate): String =
        localDate.atStartOfDay().toInstant(ZoneOffset.UTC).toString()

    /** Parse une ISO8601 UTC Z en LocalDate */
    fun fromISOToLocalDate(iso: String): LocalDate =
        (parseInstantSafe(iso) ?: error("Cannot parse instant: $iso"))
            .atZone(ZoneOffset.UTC).toLocalDate()


    /** Avant */
    /** 👉 Retire X jours à une date ISO8601 et retourne ISO8601 */
    fun minusDays(iso: String, days: Long): String {
        val instant = parseInstantSafe(iso) ?: error("Cannot parse instant: $iso")
        return instant.minusSeconds(days * 86400).toString()
    }

    /** 👉 Début de semaine (lundi) pour une date ISO8601 */
    fun startOfWeek(iso: String): String {
        val local = (parseInstantSafe(iso) ?: error("Cannot parse instant: $iso"))
            .atZone(ZoneOffset.UTC).toLocalDate()
        val start = local.minusDays(local.dayOfWeek.value.toLong() - 1)
        return start.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    }

    /** 👉 Début de mois pour une date ISO8601 */
    fun startOfMonth(iso: String): String {
        val local = (parseInstantSafe(iso) ?: error("Cannot parse instant: $iso"))
            .atZone(ZoneOffset.UTC).toLocalDate()
        val start = local.withDayOfMonth(1)
        return start.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    }

    /** 👉 Début d’année pour une date ISO8601 */
    fun startOfYear(iso: String): String {
        val local = (parseInstantSafe(iso) ?: error("Cannot parse instant: $iso"))
            .atZone(ZoneOffset.UTC).toLocalDate()
        val start = local.withDayOfYear(1)
        return start.atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    }

    /** Renvoie la date courante (jour) avec le fuseau local en String format "yyyy-MM-dd" */
    fun getTodayIsoDay(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    /** : donne le décalage d'une date avec +X jours ou -X jours en format "yyyy-MM-dd */
    fun shiftIsoDay(day: String, offset: Long): String {
        val base = runCatching { LocalDate.parse(day) }
            .getOrElse { LocalDate.parse(getTodayIsoDay()) }

        return base.plusDays(offset).toString()
    }

    fun isDateInCurrentWeek(date: String): Boolean {
        val inputDate = LocalDate.parse(date.take(10)) // 👈 coupe la chaîne après "yyyy-MM-dd"
        val now = LocalDate.now()

        val weekFields = WeekFields.of(Locale.getDefault())
        return inputDate.get(weekFields.weekOfWeekBasedYear()) == now.get(weekFields.weekOfWeekBasedYear()) &&
                inputDate.get(weekFields.weekBasedYear()) == now.get(weekFields.weekBasedYear())
    }

    fun getDayOfWeekFromDate(date: String): String {
        val cleanedDate = date.take(10) // Garde seulement la partie "yyyy-MM-dd"
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        return LocalDate.parse(cleanedDate, formatter)
            .dayOfWeek
            .name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    fun getTodayDayOfWeek(): String {
        return LocalDate.now()
            .dayOfWeek
            .name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }

    // dayName = Monday, Tuesday, ...
    fun isToday(dayName: String): Boolean {
        val todayName = getTodayDayOfWeek()
        return todayName.equals(dayName, ignoreCase = true)
    }

    fun getCurrentWeekISO(): String {
        val now = LocalDate.now()
        val weekFields = WeekFields.ISO
        val weekNumber = now.get(weekFields.weekOfWeekBasedYear())
        val year = now.get(weekFields.weekBasedYear())
        return String.Companion.format(Locale.US, "%04d-W%02d", year, weekNumber)
    }

    fun getWeekISOFromOffset(offset: Int): String {
        val today = LocalDate.now()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            .plusWeeks(offset.toLong())
        val weekFields = WeekFields.ISO
        val weekNumber = startOfWeek.get(weekFields.weekOfWeekBasedYear())
        val year = startOfWeek.get(weekFields.weekBasedYear())
        return String.Companion.format(Locale.US, "%04d-W%02d", year, weekNumber)
    }

    fun getStartOfCurrentWeek(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .format(formatter)
    }

    fun getEndOfCurrentWeek(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return LocalDate.now()
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .format(formatter)
    }

    fun getStartOfWeek(weekISO: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val parts = weekISO.split("-W")
        val year = parts[0].toInt()
        val week = parts[1].toInt()

        val startOfWeek = LocalDate.ofYearDay(year, 1)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return startOfWeek.format(formatter)
    }

    fun getEndOfWeek(weekISO: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val parts = weekISO.split("-W")
        val year = parts[0].toInt()
        val week = parts[1].toInt()

        val endOfWeek = LocalDate.ofYearDay(year, 1)
            .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week.toLong())
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        return endOfWeek.format(formatter)
    }

    // Notifications -- nouvelle signature i18n (2026-05-12) : prend un Context
    // pour resoudre les strings localises. L'ancien overload sans Context
    // reste pour compatibility, retourne EN canonique.
    fun formatRelativeTime(context: android.content.Context, iso: String?): String {
        if (iso == null) return ""
        val instant = parseInstantSafe(iso) ?: return ""
        val now = Instant.now()
        val duration = Duration.between(instant, now)

        return when {
            duration.toMinutes() < 1 -> context.getString(com.example.sportapp.R.string.rel_time_just_now)
            duration.toMinutes() < 60 -> context.getString(com.example.sportapp.R.string.rel_time_min_ago, duration.toMinutes())
            duration.toHours() < 24 -> context.getString(com.example.sportapp.R.string.rel_time_hours_ago, duration.toHours())
            duration.toDays() == 1L -> context.getString(com.example.sportapp.R.string.rel_time_yesterday)
            duration.toDays() < 7 -> context.getString(com.example.sportapp.R.string.rel_time_days_ago, duration.toDays())
            else -> {
                val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
            }
        }
    }

    /** Overload deprecated -- a remplacer par formatRelativeTime(context, iso). */
    @Deprecated("Pass Context for i18n", ReplaceWith("formatRelativeTime(context, iso)"))
    fun formatRelativeTime(iso: String?): String {
        if (iso == null) return ""
        val instant = parseInstantSafe(iso) ?: return ""
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        return when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()} min ago"
            duration.toHours() < 24 -> "${duration.toHours()} h ago"
            duration.toDays() == 1L -> "yesterday"
            duration.toDays() < 7 -> "${duration.toDays()} days ago"
            else -> {
                val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
                date.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }
    }

    // CalendarViewScreen
    private val dayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    /**
     * Formatter DB: "2025-12-26 00:00:00+01"
     * (espace entre date et heure, offset sans minutes parfois => +01)
     *
     * On supporte:
     * - +01
     * - +01:00
     */
    private val dbOffsetFormatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX", Locale.US), // +01:00
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX", Locale.US)    // +01
    )

    /**
     * Convertit une date stockée DB (plusieurs formats possibles) -> LocalDate dans le fuseau device.
     *
     * Supporte:
     * 1) ISO instant:         2025-12-25T23:00:00Z
     * 2) Timestamp offset:    2025-12-26 00:00:00+01  (ou +01:00)
     * 3) Fallback:            yyyy-MM-dd (ou string qui commence par yyyy-MM-dd)
     */
    fun toLocalDateFromDb(raw: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        // 1) ISO Instant: 2025-12-25T23:00:00Z
        try {
            val instant = Instant.parse(raw)
            return instant.atZone(zone).toLocalDate()
        } catch (_: DateTimeParseException) { }

        // 2) Offset timestamp: 2025-12-26 00:00:00+01 / +01:00
        for (fmt in dbOffsetFormatters) {
            try {
                val odt = OffsetDateTime.parse(raw, fmt)
                return odt.atZoneSameInstant(zone).toLocalDate()
            } catch (_: DateTimeParseException) { }
        }

        // 3) fallback
        return LocalDate.parse(raw.take(10), dayFormatter)
    }

    /**
     * Parse une string en `Instant`, tolérant 3 formats de wire :
     *
     *  1. **Format canonique projet** : `"YYYY-MM-DDTHH:MM:SS.UUUUUUZ"` (cas 99%
     *     post-V3.2 : ce que tous les producteurs émettent).
     *  2. Format Postgres legacy avec espace : `"yyyy-MM-dd HH:mm:ss+01:00"` ou
     *     `"yyyy-MM-dd HH:mm:ss+01"` (compatibilité avec l'ancien wire avant V3.2).
     *  3. Fallback date pure : `"yyyy-MM-dd"` → début de journée UTC.
     *
     * Retourne `null` si rien n'a matché (= input garbage). À utiliser pour
     * remplacer tous les `Instant.parse()` de l'app — sans crash sur le legacy.
     *
     * Voir docs/DATES.md §"Après V3.2" pour le contexte.
     */
    fun parseInstantSafe(iso: String): Instant? {
        // 1) Format canonique projet (et tout ISO 8601 strict)
        try { return Instant.parse(iso) } catch (_: DateTimeParseException) { }

        // 2) Format Postgres legacy avec espace
        for (fmt in dbOffsetFormatters) {
            try { return OffsetDateTime.parse(iso, fmt).toInstant() }
            catch (_: DateTimeParseException) { }
        }

        // 3) Fallback date pure
        try { return LocalDate.parse(iso.take(10), dayFormatter).atStartOfDay(ZoneOffset.UTC).toInstant() }
        catch (_: DateTimeParseException) { }

        return null
    }

    fun toIsoDay(date: LocalDate): String = date.toString() // "yyyy-MM-dd"

}