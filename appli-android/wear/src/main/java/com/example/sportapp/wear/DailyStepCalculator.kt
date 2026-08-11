package com.example.sportapp.wear

/**
 * Fallback « pas du jour » à partir du compteur cumulatif capteur
 * (`SensorManager.TYPE_STEP_COUNTER`, cumul depuis le boot) quand Health Services
 * `STEPS_DAILY` est indisponible. Mémorise une baseline (jour local + valeur du
 * compteur au 1ᵉʳ échantillon du jour) ; les pas du jour = `compteur − baseline`.
 *
 * Gère le **reboot** (compteur < baseline → re-baseline) et le **changement de
 * jour** (nouveau jour → re-baseline à 0). Logique pure → testable en JVM.
 *
 * ⚠️ Limite connue : si l'app n'observe le compteur qu'en cours de journée, la
 * baseline se pose à cet instant → les pas faits avant sont perdus (raison pour
 * laquelle STEPS_DAILY est préféré, qui compte le vrai total du jour).
 */
object DailyStepCalculator {

    data class Baseline(val epochDay: Long, val counterAtDayStart: Long)

    /** [daySteps] à afficher ; [newBaseline] non-null → à persister. */
    data class Result(val daySteps: Long, val newBaseline: Baseline?)

    fun compute(counter: Long, todayEpochDay: Long, saved: Baseline?): Result {
        val needsRebaseline = saved == null ||
            saved.epochDay != todayEpochDay ||
            counter < saved.counterAtDayStart
        return if (needsRebaseline) {
            Result(daySteps = 0L, newBaseline = Baseline(todayEpochDay, counter))
        } else {
            Result(daySteps = (counter - saved.counterAtDayStart).coerceAtLeast(0L), newBaseline = null)
        }
    }
}
