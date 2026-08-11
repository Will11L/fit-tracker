package com.example.sportapp.core.domain.routines

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.data.model.Notification
import com.example.sportapp.feature.notifications.domain.NotificationNavigationMapper
import com.example.sportapp.feature.notifications.utils.NotificationLevel
import com.example.sportapp.feature.notifications.utils.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 2026-06-08 (dev-sport) : couverture du scheduling des rappels de période
 * (ROUTINE_PERIOD_START / ROUTINE_PERIOD_END) avec offset "avant début / avant fin".
 *
 * Comportements observables testés (feature Android pure, pas de device) :
 *  - la math de planification [RoutinePeriodStartScheduler.nextTriggerDateTime] :
 *    le TRIGGER = occurrence − offset (et NON l'occurrence), dans le futur, qui
 *    roule au jour suivant quand le trigger d'aujourd'hui est déjà passé (fix de
 *    la boucle de re-notif à offset > 0) ;
 *  - [RoutinePeriodStartScheduler.parseTime] : null si "HH:mm" malformé ;
 *  - le nom de travail unique par (period, kind) -> 2 works coexistants début/fin ;
 *  - le contrat wire NotificationType + le deep-link (tap -> écran routines).
 *
 * Robolectric : uniquement pour fournir un Context au scheduler (sa logique de
 * date n'utilise que java.time). On ne teste PAS schedule()/le Worker (ils
 * orchestrent WorkManager + le système de notif -> domaine instrumenté).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RoutinePeriodStartTest {

    private lateinit var scheduler: RoutinePeriodStartScheduler

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scheduler = RoutinePeriodStartScheduler(context)
    }

    // ─── parseTime : "HH:mm" malformé => null (le scheduler annule le rappel) ────

    @Test
    fun `parseTime returns null when time is malformed`() {
        // NB: "24:00" est volontairement absent — le resolver SMART de java.time
        // le normalise en minuit ("00:00"), ce n'est donc PAS un format invalide.
        listOf("", "abc", "7h30", "12:60", "07", "07:5", "7:5", "99:99").forEach { bad ->
            assertNull("expected null for malformed time '$bad'", scheduler.parseTime(bad))
        }
        assertEquals(LocalTime.of(7, 30), scheduler.parseTime("07:30"))
    }

    // ─── nextTriggerDateTime : la décision du déclencheur (trigger = occ − offset) ─

    @Test
    fun `nextTriggerDateTime at offset 0 equals the next occurrence`() {
        val now = LocalDateTime.now()
        assumeTrue("near midnight, branch not exercisable", now.toLocalTime().isBefore(LocalTime.of(23, 50)))

        val occTime = now.toLocalTime().plusMinutes(5).truncatedTo(ChronoUnit.MINUTES)
        val trigger = scheduler.nextTriggerDateTime(occTime, 0)

        assertEquals("offset 0 -> trigger == occurrence (today)", LocalDate.now(), trigger.toLocalDate())
        assertEquals(occTime, trigger.toLocalTime())
    }

    @Test
    fun `nextTriggerDateTime fires offsetMin before the occurrence, in the future, today`() {
        val now = LocalDateTime.now()
        assumeTrue("midnight margins", now.toLocalTime().isAfter(LocalTime.of(0, 30)))
        assumeTrue("midnight margins", now.toLocalTime().isBefore(LocalTime.of(23, 30)))

        val occTime = now.toLocalTime().plusMinutes(20).truncatedTo(ChronoUnit.MINUTES)
        val offset = 15
        val before = LocalDateTime.now()

        val trigger = scheduler.nextTriggerDateTime(occTime, offset)

        // trigger = occurrence − offset, et l'occurrence = trigger + offset
        assertEquals("trigger fires offset min before the occurrence", occTime.minusMinutes(offset.toLong()), trigger.toLocalTime())
        assertEquals(occTime, trigger.plusMinutes(offset.toLong()).toLocalTime())
        assertEquals(LocalDate.now(), trigger.toLocalDate())
        assertTrue("trigger must be in the future", !trigger.isBefore(before))
    }

    @Test
    fun `nextTriggerDateTime rolls to tomorrow when today occurrence already passed (offset 0)`() {
        val now = LocalDateTime.now()
        assumeTrue("just after midnight, branch not exercisable", now.toLocalTime().isAfter(LocalTime.of(0, 10)))

        val occTime = now.toLocalTime().minusMinutes(5).truncatedTo(ChronoUnit.MINUTES)
        val trigger = scheduler.nextTriggerDateTime(occTime, 0)

        assertEquals("a passed occurrence is scheduled tomorrow", LocalDate.now().plusDays(1), trigger.toLocalDate())
        assertEquals(occTime, trigger.toLocalTime())
    }

    @Test
    fun `nextTriggerDateTime rolls to tomorrow when today trigger already passed (offset gt 0)`() {
        // 🔴 Régression ciblée : à offset > 0 l'occurrence est encore future
        // (dans 5 min) mais le TRIGGER (occ − 15) est déjà passé -> on NE doit PAS
        // re-planifier aujourd'hui (boucle de notifs), mais rouler à demain.
        val now = LocalDateTime.now()
        assumeTrue("midnight margins", now.toLocalTime().isAfter(LocalTime.of(0, 30)))
        assumeTrue("midnight margins", now.toLocalTime().isBefore(LocalTime.of(23, 30)))

        val occTime = now.toLocalTime().plusMinutes(5).truncatedTo(ChronoUnit.MINUTES)
        val offset = 15
        val trigger = scheduler.nextTriggerDateTime(occTime, offset)

        assertEquals("passed trigger -> tomorrow, no same-day re-fire", LocalDate.now().plusDays(1), trigger.toLocalDate())
        assertEquals(occTime.minusMinutes(offset.toLong()), trigger.toLocalTime())
        assertTrue("the rescheduled trigger is strictly in the future", trigger.isAfter(LocalDateTime.now()))
    }

    // ─── workNameFor : 2 works distincts (début + fin) par période ──────────────

    @Test
    fun `workNameFor distinguishes start and end works, deterministic and unique per period`() {
        val start = RoutinePeriodStartScheduler.workNameFor("uuid-A", PeriodReminderKind.START)
        val end = RoutinePeriodStartScheduler.workNameFor("uuid-A", PeriodReminderKind.END)

        assertEquals("routine_period_start_uuid-A", start)
        assertEquals("routine_period_end_uuid-A", end)
        // début et fin coexistent pour une même période (annulés ensemble par cancel()).
        assertNotEquals("start and end must be 2 distinct unique-works", start, end)
        // déterministe (REPLACE) + isolé par uuid.
        assertEquals(start, RoutinePeriodStartScheduler.workNameFor("uuid-A", PeriodReminderKind.START))
        assertNotEquals(start, RoutinePeriodStartScheduler.workNameFor("uuid-B", PeriodReminderKind.START))
    }

    // ─── Contrat notif : wire + deep-link (tap -> écran routines) ───────────────

    @Test
    fun `NotificationType round-trips the ROUTINE_PERIOD_START wire code`() {
        assertEquals(NotificationType.ROUTINE_PERIOD_START, NotificationType.fromWire("ROUTINE_PERIOD_START"))
        assertEquals(NotificationType.ROUTINE_PERIOD_START, NotificationType.fromWire("routine_period_start"))
        assertEquals("ROUTINE_PERIOD_START", NotificationType.ROUTINE_PERIOD_START.wire)
        assertEquals(NotificationLevel.INFO, NotificationType.ROUTINE_PERIOD_START.defaultLevel)
    }

    @Test
    fun `NotificationType round-trips the ROUTINE_PERIOD_END wire code`() {
        assertEquals(NotificationType.ROUTINE_PERIOD_END, NotificationType.fromWire("ROUTINE_PERIOD_END"))
        assertEquals(NotificationType.ROUTINE_PERIOD_END, NotificationType.fromWire("routine_period_end"))
        assertEquals("ROUTINE_PERIOD_END", NotificationType.ROUTINE_PERIOD_END.wire)
        assertEquals(NotificationLevel.INFO, NotificationType.ROUTINE_PERIOD_END.defaultLevel)
    }

    @Test
    fun `tapping a ROUTINE_PERIOD_START notification routes to the routines screen`() {
        val notif = Notification(
            uuid = "n-1",
            userId = 1,
            type = NotificationType.ROUTINE_PERIOD_START.wire,
            title = "Morning routine starts",
            body = "Time for your morning routine",
            data = mapOf("periodUuid" to "p-1", "kind" to "START", "screen" to "tasks"),
        )

        val target = NotificationNavigationMapper.resolve(notif)

        assertNotNull(target)
        assertEquals(Routes.TASKS, target!!.route)
    }

    @Test
    fun `tapping a ROUTINE_PERIOD_END notification routes to the routines screen`() {
        val notif = Notification(
            uuid = "n-2",
            userId = 1,
            type = NotificationType.ROUTINE_PERIOD_END.wire,
            title = "Morning routine ending",
            body = "Your morning routine ends soon",
            data = mapOf("periodUuid" to "p-1", "kind" to "END", "screen" to "tasks"),
        )

        val target = NotificationNavigationMapper.resolve(notif)

        assertNotNull(target)
        assertEquals(Routes.TASKS, target!!.route)
    }
}
