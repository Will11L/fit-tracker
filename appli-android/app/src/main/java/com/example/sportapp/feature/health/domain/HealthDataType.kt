package com.example.sportapp.feature.health.domain

/**
 * Les 5 types de données santé lus depuis Health Connect (read-only).
 *
 * Vocabulaire aligné sur le contrat serveur déjà déployé (commit 539c22f1) :
 * - [STEPS] alimente `health_step_counts` (buckets intraday : date + bucketStart + steps).
 * - Les autres alimentent `health_metrics` avec `type` = [name] UPPER_CASE
 *   (politique 11) ∈ {HEART_RATE, SLEEP, DISTANCE, ACTIVE_CALORIES, SPO2}.
 *
 * [wireUnit] = unité self-describing envoyée au serveur (`health_metrics.unit`).
 * Source unique partagée par la lecture (aperçu) et la couche sync (`HealthImporter`).
 *
 * [SPO2] = saturation pulsée en oxygène du sang (SpO2, %), mesure nocturne
 * instantanée (source Samsung Health) — seul type ajouté après l'inventaire HC v1.
 */
enum class HealthDataType(val wireUnit: String) {
    STEPS("count"),
    DISTANCE("m"),
    ACTIVE_CALORIES("kcal"),
    // Total des calories brûlées du jour (BMR inclus). Seul type calories réellement partagé
    // par Samsung (HC) / exposé par la montre (Health Services CALORIES_DAILY) ; les calories
    // ACTIVES ne sont pas disponibles en passif → dérivées à l'affichage (cf. CalorieMath, Option B).
    TOTAL_CALORIES("kcal"),
    HEART_RATE("bpm"),
    SLEEP("min"),
    SPO2("%"),
}

/**
 * Disponibilité du SDK Health Connect sur le device, dérivée de
 * `HealthConnectClient.getSdkStatus()`. Détermine le fallback UI :
 * - [INSTALLED] : HC prêt, on peut demander les permissions et lire.
 * - [UPDATE_REQUIRED] : provider présent mais trop ancien → proposer la MàJ store.
 * - [NOT_SUPPORTED] : device incompatible ou HC absent → UI dégradée, jamais de crash.
 */
enum class HealthConnectAvailability {
    INSTALLED,
    UPDATE_REQUIRED,
    NOT_SUPPORTED,
}
