package com.example.sportapp.feature.health.domain

/**
 * Logique pure de l'échantillonnage des pas par tranche de 30 min (aucune dépendance
 * Android → testable JVM). L'échantillonneur relève périodiquement le **total de pas
 * du jour** dans Health Connect (lecture pleine-journée = total courant exact) et en
 * déduit de vraies tranches, là où Samsung ne fournit qu'un record jour-entier proraté.
 *
 * Modèle **SET par tranche ouverte** (idempotent, invariant de somme) : la tranche
 * courante `openSlot` vaut toujours `total - openSlotBase`. Quand la tranche change, la
 * base de la nouvelle = le total du relevé précédent → les valeurs télescopent, donc
 * la SOMME de toutes les tranches d'un jour = le total courant Samsung (contrat
 * `health_step_counts` total = SUM préservé). Ré-exécuter un relevé avec le même total
 * ré-écrit la même valeur (SET) → pas de double-comptage sur retry WorkManager.
 *
 * Changement de jour (rollover minuit) ou 1er relevé (activation en cours de journée) :
 * **rattrapage** — tout le total courant dans la tranche du moment, et [SamplingStep.resetDay]
 * signale au worker de tombstoner les buckets résiduels du jour (import proraté).
 */
object StepSamplingLogic {

    /** État persistant de l'échantillonneur (DataStore). `date` vide = jamais relevé. */
    data class SamplingState(
        val date: String,        // "YYYY-MM-DD" du dernier relevé ("" = jamais)
        val lastTotal: Int,      // total de pas du jour au dernier relevé
        val openSlot: String,    // "HH:MM" de la tranche en cours de remplissage
        val openSlotBase: Int,   // total du jour à l'ouverture de la tranche openSlot
    )

    /** Écriture à effectuer + nouvel état, produits par [next]. */
    data class SamplingStep(
        val slot: String,        // "HH:MM" de la tranche à écrire (SET)
        val value: Int,          // valeur du bucket de la tranche (= total - base)
        val resetDay: Boolean,   // 1er relevé du jour → tombstone des buckets du jour
        val newState: SamplingState,
    )

    /**
     * Calcule l'écriture du relevé courant. [today] "YYYY-MM-DD" et [nowSlot] "HH:MM"
     * (début de la tranche de 30 min du moment) situent le relevé ; [currentTotal] est
     * le total de pas du jour lu dans HC.
     */
    fun next(prev: SamplingState, today: String, nowSlot: String, currentTotal: Int): SamplingStep {
        val total = currentTotal.coerceAtLeast(0)
        if (prev.date != today) {
            // Rollover minuit ou 1er relevé (activation en cours de journée) : rattrapage.
            return SamplingStep(
                slot = nowSlot,
                value = total,
                resetDay = true,
                newState = SamplingState(today, total, nowSlot, 0),
            )
        }
        // Même jour : la tranche courante s'étend, sinon une nouvelle s'ouvre avec pour
        // base le total du relevé précédent (télescopage → somme du jour préservée).
        val slotChanged = nowSlot != prev.openSlot
        val openSlot = if (slotChanged) nowSlot else prev.openSlot
        val base = if (slotChanged) prev.lastTotal else prev.openSlotBase
        val value = (total - base).coerceAtLeast(0)
        return SamplingStep(
            slot = openSlot,
            value = value,
            resetDay = false,
            newState = SamplingState(today, total, openSlot, base),
        )
    }
}
