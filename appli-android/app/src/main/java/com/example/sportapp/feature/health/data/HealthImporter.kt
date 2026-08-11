package com.example.sportapp.feature.health.data

import android.util.Log
import com.example.sportapp.core.data.local.HealthMetricDao
import com.example.sportapp.core.data.local.HealthStepCountDao
import com.example.sportapp.core.data.model.HealthMetric
import com.example.sportapp.core.data.model.HealthStepCount
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.health.domain.HealthConnectMapper
import com.example.sportapp.feature.health.domain.HealthDataType
import com.example.sportapp.feature.health.domain.HealthMetricReading
import com.example.sportapp.feature.health.domain.HealthUuids
import com.example.sportapp.feature.health.domain.StepBucketReading
import com.example.sportapp.feature.health.wear.WearLiveState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pont Health Connect → Room : lit les données HC du jour (via [HealthConnectManager],
 * source unique du façonnage wire de la tâche 2) et les upsert dans Room en local
 * non-synchronisé (`synced=false`) pour que le prochain cycle de sync les pousse au
 * serveur. Consommé par [com.example.sportapp.core.sync.SyncCoordinator] avant chaque
 * push (login / retour réseau / action user).
 *
 * Fenêtre = jour courant uniquement (minimum viable, tâche data/sync). Les buckets de
 * pas se ré-upsert au fil de la journée pour un total near-real-time ; le HealthGoal
 * (cible utilisateur, pas une donnée HC) n'est PAS importé ici — il vient de l'UI /
 * du serveur.
 *
 * **UUID déterministe** : dérivé de (userId, date, bucketStart) pour les pas et de
 * (userId, type, date, startTime) pour les métriques. Ré-importer le même jour est donc
 * idempotent (upsert de la même row, jamais de doublon) et deux devices du même user
 * convergent sur la même row (last-write-wins par updatedAt).
 *
 * Best-effort : ne lève jamais (HC absent / permission manquante → listes vides,
 * cf. [HealthConnectManager]) pour ne pas casser la boucle de sync.
 */
@Singleton
class HealthImporter @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val stepCountDao: HealthStepCountDao,
    private val metricDao: HealthMetricDao,
    private val samplingStore: StepSamplingStore,
) {

    /** Lit les données HC du jour courant et les upsert dans Room (synced=false). */
    suspend fun importRecentToRoom() {
        val userId = CurrentUserManager.userId ?: return
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val startOfToday = today.atStartOfDay(zone).toInstant()
            // Journée locale COMPLÈTE [minuit, minuit+1j) et non [minuit, now] :
            // Samsung Health écrit ses pas/distance/calories comme un unique record
            // couvrant toute la journée, que Health Connect prorate au temps couvert
            // par la fenêtre. Une fenêtre partielle pousserait des buckets proratés
            // (faux) qui changent rétroactivement le total du jour à chaque import ;
            // la fenêtre jour entier couvre le record à 100 % → la SOMME est exacte.
            val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant()

            importSteps(userId, zone, startOfToday, endOfToday)
            importMetrics(userId, startOfToday, endOfToday)
            importHeartRateSlices(userId, zone, today.toString(), startOfToday, endOfToday)
            importSleep(userId, zone, today.toString(), startOfToday, endOfToday)
            // Détail sommeil pour la parité web (le hub Android lit HC en direct, le web
            // n'a que Room/serveur) : intraday + phases 7 j + sessions.
            importSleepSlices(userId, zone, today.toString(), startOfToday, endOfToday)
            importSleepStages(userId, zone, today)
            importSleepSessions(userId, zone, today.toString())
            importSleepPhaseSlices(userId, zone, today.toString())
            // Après HC : la montre écrase le total distance/calories du jour (priorité montre, Option B).
            importWatchEnergy(userId, zone, today.toString())
        } catch (e: Exception) {
            // Best-effort : un échec d'import ne doit jamais interrompre la sync.
            Log.w("HealthImporter", "importRecentToRoom failed: ${e.message}")
        }
    }

    private suspend fun importSteps(userId: Int, zone: ZoneId, from: Instant, to: Instant) {
        // Échantillonnage actif : l'échantillonneur ([StepSamplingWorker]) possède les
        // buckets de pas du jour (vraies tranches). L'import proraté s'efface devant lui
        // (sinon il clobberait les tranches échantillonnées). À la désactivation, l'import
        // reprend et réécrit le jour en proraté (via le tombstone de la réécriture).
        if (samplingStore.isEnabled()) return
        val readings: List<StepBucketReading> = healthConnectManager.readStepBuckets(from, to, zone)
        if (readings.isEmpty()) return
        val day = readings.first().date

        // Réécriture pleine-journée (tranches de 30 min). Un bucket horaire résiduel
        // "HH:00" (import antérieur au passage 30 min) couvrant toute l'heure serait
        // ré-upserté avec la seule 1re demi-heure et le "HH:30" ajouté → OK tant que
        // la lecture fraîche produit les DEUX demi-heures. Mais si la 1re demi-heure
        // est vide (skip `count<=0`), le "HH:00" horaire survivrait et se cumulerait
        // au "HH:30" → SOMME du jour fausse. On tombstone donc tout bucket du jour
        // absent de la lecture fraîche (pendingDeletion → retiré localement ET poussé).
        val freshStarts = readings.map { it.bucketStart }.toSet()
        stepCountDao.getAllOnce()
            .filter { it.userId == userId && it.date == day && !it.pendingDeletion && it.bucketStart !in freshStarts }
            .forEach { stepCountDao.markAsPendingDeletion(it.uuid) }

        val toUpsert = readings.mapNotNull { r ->
            val entity = HealthStepCount(
                uuid = HealthUuids.stepBucket(userId, r.date, r.bucketStart),
                userId = userId,
                date = r.date,
                bucketStart = r.bucketStart,
                steps = r.steps.toInt(),
            )
            // Skip si la valeur n'a pas changé (évite un push + broadcast WS inutiles) ;
            // un bucket précédemment tombstoné est réécrit (ré-insert = un-tombstone).
            val existing = stepCountDao.getByUUID(entity.uuid)
            if (existing != null && existing.steps == entity.steps && !existing.pendingDeletion) null else entity
        }
        if (toUpsert.isNotEmpty()) stepCountDao.insertAll(toUpsert)
    }

    private suspend fun importMetrics(userId: Int, from: Instant, to: Instant) {
        val readings: List<HealthMetricReading> = healthConnectManager.readMetricReadings(from, to)
        if (readings.isEmpty()) return

        val toUpsert = readings.mapNotNull { r ->
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, r.type.name, r.date, r.startTime),
                userId = userId,
                type = r.type.name,          // UPPER_CASE (politique 11)
                value = r.value.toFloat(),
                unit = r.unit,
                date = r.date,
                startTime = r.startTime,
            )
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing != null && existing.value == entity.value && existing.unit == entity.unit) null else entity
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * FC intraday : une row `HEART_RATE_INTRADAY` par tranche de 30 min non vide du jour (bpm moyen +
     * `start_time` "HH:MM"), pour que le web trace le chart « Aujourd'hui » (parité Android, qui lui lit
     * Health Connect en direct). **Type DISTINCT** de la moyenne quotidienne `HEART_RATE` (`start_time`
     * null) : les agrégats moyenne/7 j filtrent le type exact → jamais clobberés par une tranche
     * (décision : row moyenne conservée telle quelle, pas dérivée). uuid déterministe
     * (user + type + date + tranche) → idempotent multi-device, volume borné ≤48 rows/jour. Réutilise
     * `readHourlyHeartRate` (même source BPM_AVG que le hub). Le même pattern pourrait préparer SpO2
     * intraday plus tard (non implémenté ici, non bloqué). Best-effort (dans le try de [importRecentToRoom]).
     */
    private suspend fun importHeartRateSlices(userId: Int, zone: ZoneId, date: String, from: Instant, to: Instant) {
        val slots = healthConnectManager.readHourlyHeartRate(from, to, zone) // 48 bpm (0 = pas de mesure)
        val unit = HealthDataType.HEART_RATE.wireUnit
        val toUpsert = slots.mapIndexedNotNull { slot, bpm ->
            if (bpm <= 0f) return@mapIndexedNotNull null
            val hhmm = HealthConnectMapper.slotIndexHhmm(slot)
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, HR_INTRADAY_TYPE, date, hhmm),
                userId = userId,
                type = HR_INTRADAY_TYPE,
                value = bpm,
                unit = unit,
                date = date,
                startTime = hhmm,
            )
            // Skip si inchangé (évite un push + broadcast WS inutiles) : la tranche en cours peut voir
            // sa moyenne bouger d'un refresh à l'autre → upsert ; les tranches passées sont stables.
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing != null && existing.value == entity.value && !existing.pendingDeletion) null else entity
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * Sommeil : métrique SLEEP = somme du temps dormi des sessions de la fenêtre,
     * calculée par le **mapper par stades** (`readSleepSessions` →
     * `HealthConnectMapper.sleepSession`) — MÊME source que l'aperçu Données santé
     * (fin de l'écart avec l'ancien agrégat HC SLEEP_DURATION_TOTAL). date = jour de
     * la fenêtre + startTime null → uuid déterministe inchangé : l'upsert écrase
     * l'ancienne valeur du jour.
     */
    private suspend fun importSleep(userId: Int, zone: ZoneId, date: String, from: Instant, to: Instant) {
        val sessions = healthConnectManager.readSleepSessions(from, to, zone)
        if (sessions.isEmpty()) return
        val entity = HealthMetric(
            uuid = HealthUuids.metric(userId, SLEEP_TYPE, date, null),
            userId = userId,
            type = SLEEP_TYPE,
            value = sessions.sumOf { it.asleepMinutes }.toFloat(),
            unit = "min",
            date = date,
            startTime = null,
        )
        val existing = metricDao.getByUUID(entity.uuid)
        if (existing == null || existing.value != entity.value) metricDao.insertAll(listOf(entity))
    }

    /**
     * Sommeil intraday : une row `SLEEP_INTRADAY` par tranche de 30 min dormie du jour
     * (minutes + `start_time` "HH:MM"), miroir exact de [importHeartRateSlices] — le web
     * trace le chart « Aujourd'hui » du sommeil (parité Android qui lit HC en direct).
     * Même source stades que le total SLEEP (`readHourlySleepMinutes`). ≤48 rows/jour.
     */
    private suspend fun importSleepSlices(userId: Int, zone: ZoneId, date: String, from: Instant, to: Instant) {
        val slots = healthConnectManager.readHourlySleepMinutes(from, to, zone) // 48 minutes (0 = rien)
        val toUpsert = slots.mapIndexedNotNull { slot, minutes ->
            if (minutes <= 0f) return@mapIndexedNotNull null
            val hhmm = HealthConnectMapper.slotIndexHhmm(slot)
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, SLEEP_INTRADAY_TYPE, date, hhmm),
                userId = userId,
                type = SLEEP_INTRADAY_TYPE,
                value = minutes,
                unit = "min",
                date = date,
                startTime = hhmm,
            )
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing != null && existing.value == entity.value && !existing.pendingDeletion) null else entity
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * Phases de sommeil par jour (7 derniers jours, nuit rattachée au matin du réveil,
     * cf. `readSleepStagesByDay`) : 4 rows/jour max `SLEEP_STAGE_DEEP/LIGHT/REM/AWAKE`
     * (minutes par famille, `start_time` null) → barres empilées 7 j du web.
     */
    private suspend fun importSleepStages(userId: Int, zone: ZoneId, today: LocalDate) {
        val days = (6 downTo 0).map { today.minusDays(it.toLong()).toString() }
        val byDay = healthConnectManager.readSleepStagesByDay(days, zone)
        if (byDay.isEmpty()) return
        val toUpsert = byDay.flatMap { (day, minutes) ->
            SLEEP_STAGE_TYPES.mapIndexedNotNull { bucket, type ->
                val v = minutes.getOrElse(bucket) { 0f }
                if (v <= 0f) return@mapIndexedNotNull null
                val entity = HealthMetric(
                    uuid = HealthUuids.metric(userId, type, day, null),
                    userId = userId,
                    type = type,
                    value = v,
                    unit = "min",
                    date = day,
                    startTime = null,
                )
                val existing = metricDao.getByUUID(entity.uuid)
                if (existing != null && existing.value == entity.value && !existing.pendingDeletion) null else entity
            }
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * Sessions de sommeil du jour de réveil courant : une row `SLEEP_SESSION` par session
     * finissant aujourd'hui (fenêtre 24 h glissante filtrée sur `endDate`) — `start_time` =
     * mise au lit "HH:MM", `value` = endormissement en minutes depuis minuit (unit
     * "min-of-day") → lignes « Au lit à X · Endormi à Y » du web. uuid déterministe
     * user+type+date+mise-au-lit : multi-sessions (nuit + sieste) distinctes, ré-import idempotent.
     */
    private suspend fun importSleepSessions(userId: Int, zone: ZoneId, date: String) {
        val now = Instant.now()
        val sessions = healthConnectManager
            .readSleepSessions(now.minus(Duration.ofHours(24)), now, zone)
            .filter { it.endDate == date } // la nuit appartient au matin du réveil
        val toUpsert = sessions.mapNotNull { s ->
            val asleep = runCatching {
                val (h, m) = s.asleepStartTime.split(":").map(String::toInt)
                (h * 60 + m).toFloat()
            }.getOrNull() ?: return@mapNotNull null
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, SLEEP_SESSION_TYPE, date, s.startTime),
                userId = userId,
                type = SLEEP_SESSION_TYPE,
                value = asleep,
                unit = "min-of-day",
                date = date,
                startTime = s.startTime,
            )
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing != null && existing.value == entity.value && !existing.pendingDeletion) null else entity
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * Slices de phases (hypnogramme web) : une row par stade HC des sessions finissant
     * aujourd'hui — le TYPE porte la phase (SLEEP_SLICE_DEEP/LIGHT/REM/AWAKE, comme
     * SLEEP_STAGE_*), `start_time` = début du stade "HH:MM", value = durée (min).
     * Chronologie à la lecture : heure ≥ 15:00 → la veille au soir (la nuit appartient
     * au matin du réveil, cf. HealthUiAggregations.sleepPhaseTimeline). uuid
     * déterministe user+type+date+début → idempotent multi-device.
     */
    private suspend fun importSleepPhaseSlices(userId: Int, zone: ZoneId, date: String) {
        val now = Instant.now()
        val slices = healthConnectManager
            .readSleepPhaseSlices(now.minus(Duration.ofHours(24)), now, zone)
            .filter { it.endDate == date }
        val toUpsert = slices.mapNotNull { s ->
            val type = SLEEP_SLICE_TYPES.getOrNull(s.bucket) ?: return@mapNotNull null
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, type, date, s.startTime),
                userId = userId,
                type = type,
                value = s.minutes.toFloat(),
                unit = "min",
                date = date,
                startTime = s.startTime,
            )
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing != null && existing.value == entity.value && !existing.pendingDeletion) null else entity
        }
        if (toUpsert.isNotEmpty()) metricDao.insertAll(toUpsert)
    }

    /**
     * Distance & calories TOTALES du jour depuis l'agrégat live de la montre ([WearLiveState],
     * alimenté par le canal Wearable Data Layer) → `health_metrics` (Option B). uuid déterministe
     * `(user, type, date, null)` = MÊME row que l'import HC → appelé APRÈS [importMetrics], la valeur
     * montre écrase la valeur HC du jour (priorité montre). Ne persiste que si la mesure live date
     * bien d'aujourd'hui (une valeur figée en veille ne réécrit pas un autre jour).
     *
     * Persistance justifiée (≠ steps/FC affichage-only) : Samsung n'écrit rien dans HC pour ces types
     * → aucun risque de double-comptage. Best-effort, appelé dans le try de [importRecentToRoom].
     */
    private suspend fun importWatchEnergy(userId: Int, zone: ZoneId, date: String) {
        val live = WearLiveState.live.value ?: return
        val liveDay = Instant.ofEpochMilli(live.timestampMillis).atZone(zone).toLocalDate().toString()
        if (liveDay != date) return

        // La montre expose des calories ACTIVES (Health Services CALORIES_DAILY = actives sur la
        // Watch4) → stockées en ACTIVE_CALORIES (le total s'affiche dérivé = actives + BMR). Distance
        // inchangée.
        val measures = buildList {
            live.distanceM?.let { add(HealthDataType.DISTANCE to it.toFloat()) }
            live.caloriesKcal?.let { add(HealthDataType.ACTIVE_CALORIES to it.toFloat()) }
        }
        if (measures.isNotEmpty()) {
            Log.d("WearPull", "phone persist watch energy $date: " +
                measures.joinToString { "${it.first.name}=${it.second}" })
        }
        measures.forEach { (type, value) ->
            val entity = HealthMetric(
                uuid = HealthUuids.metric(userId, type.name, date, null),
                userId = userId,
                type = type.name,
                value = value,
                unit = type.wireUnit,
                date = date,
                startTime = null,
            )
            val existing = metricDao.getByUUID(entity.uuid)
            if (existing == null || existing.value != entity.value) metricDao.insertAll(listOf(entity))
        }

        // La montre fournit les ACTIVES → un TOTAL_CALORIES du jour devient incohérent (stale d'un
        // ancien build qui stockait les actives en TOTAL, ou total HC redondant quand la montre prime).
        // On le tombstone (retiré localement + suppression poussée) → pas de doublon incohérent.
        live.caloriesKcal?.let {
            val totalUuid = HealthUuids.metric(userId, HealthDataType.TOTAL_CALORIES.name, date, null)
            metricDao.getByUUID(totalUuid)?.let { stale ->
                if (!stale.pendingDeletion) metricDao.markAsPendingDeletion(stale.uuid)
            }
        }
    }

    private companion object {
        const val SLEEP_TYPE = "SLEEP" // UPPER_CASE (politique 11)
        // Type des tranches FC intraday (≠ HEART_RATE moyenne quotidienne) : le web les trace,
        // les agrégats moyenne/7 j (filtre type exact) restent intacts. UPPER_CASE (politique 11).
        const val HR_INTRADAY_TYPE = "HEART_RATE_INTRADAY"
        // Détail sommeil pour le web (le hub Android lit HC en direct). UPPER_CASE (politique 11).
        const val SLEEP_INTRADAY_TYPE = "SLEEP_INTRADAY"
        const val SLEEP_SESSION_TYPE = "SLEEP_SESSION"
        // Ordre = familles STAGE_BUCKET_* du mapper : profond / léger / paradoxal (REM) / éveillé.
        val SLEEP_STAGE_TYPES = listOf(
            "SLEEP_STAGE_DEEP", "SLEEP_STAGE_LIGHT", "SLEEP_STAGE_REM", "SLEEP_STAGE_AWAKE",
        )
        // Slices de phases (hypnogramme) : même ordre de familles, le type porte la phase.
        val SLEEP_SLICE_TYPES = listOf(
            "SLEEP_SLICE_DEEP", "SLEEP_SLICE_LIGHT", "SLEEP_SLICE_REM", "SLEEP_SLICE_AWAKE",
        )
    }
}
