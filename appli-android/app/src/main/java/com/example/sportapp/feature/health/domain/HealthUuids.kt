package com.example.sportapp.feature.health.domain

import java.util.UUID

/**
 * UUID déterministes des entités santé (source unique, partagée par [HealthImporter]
 * et l'échantillonneur de pas). Dérivés de la clé métier (user + date + tranche / type)
 * → un ré-import ou un relevé du même jour upsert la même row (idempotent), et deux
 * devices du même user convergent sur la même row (last-write-wins par updatedAt).
 */
object HealthUuids {

    /** UUID d'un bucket `health_step_counts` (user + date + tranche "HH:MM"). */
    fun stepBucket(userId: Int, date: String, bucketStart: String): String =
        deterministic("health_step_count:$userId:$date:$bucketStart")

    /** UUID d'une métrique `health_metrics` (user + type + date + startTime éventuel). */
    fun metric(userId: Int, type: String, date: String, startTime: String?): String =
        deterministic("health_metric:$userId:$type:$date:${startTime ?: ""}")

    private fun deterministic(key: String): String =
        UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
}
