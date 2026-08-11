package com.example.sportapp.feature.goals.viewmodel
import com.example.sportapp.core.stats.*

import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.Zones
import com.example.sportapp.core.data.local.*
import com.example.sportapp.core.data.model.*
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.parseTargetMinimum
import com.example.sportapp.core.utils.CustomDateUtils.getCurrentWeekISO
import com.example.sportapp.core.utils.CustomDateUtils.getEndOfWeek
import com.example.sportapp.core.utils.CustomDateUtils.getStartOfWeek
import com.example.sportapp.core.utils.CustomDateUtils.getWeekISOFromOffset
import com.example.sportapp.core.utils.showSnackbar
import android.content.Context
import com.example.sportapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortMode {
    BY_NAME,
    BY_PRIORITY
}
enum class GoalsDisplayMode {
    FLAT,
    BY_ZONE
}

/**
 * Mode d'affichage de la page Goals (refonte 2026-05-09 : remplace
 * GoalsDisplayMode.FLAT/BY_ZONE par 3 niveaux alignes sur la hierarchie
 * anatomique zone > muscle_group > muscle precis du refactor 3-niveaux
 * 2026-05-08). Affecte simultanement la liste ET le graphe footer.
 *
 *  - MUSCLE : 1 ligne / 1 bar par muscle goal (granularite max, jusqu'a 35).
 *  - GROUP  : cards regroupees par muscle_group (Pecs, Lats, Triceps...) +
 *             1 bar par groupe dans le chart (jusqu'a 17).
 *  - ZONE   : cards regroupees par zone (Chest, Back, ...) + 1 bar par zone
 *             dans le chart (6 max).
 */
enum class GoalsViewMode { MUSCLE, GROUP, ZONE }

/**
 * 5 modes de tri pour la liste + le chart footer (refonte 2026-05-09).
 *  - ALPHA         : ordre alphabetique du nom (ou key pour group/zone).
 *  - PALETTE       : ordre par zone (Zones.ALL) puis alpha — regroupe les
 *                    nuances de couleur, coherent avec StatsSortMode.ZONE.
 *  - PERCENT_DESC  : par % achievement decroissant (de + a - fait).
 *  - PERCENT_ASC   : par % achievement croissant (les retards en haut).
 *  - PRIORITY      : HIGH > MEDIUM > LOW, puis alpha. Specifique a Goals.
 */
enum class GoalsSortMode { ALPHA, PALETTE, PERCENT_DESC, PERCENT_ASC, PRIORITY }

/**
 * Goal enrichit avec le pourcentage d'achievement (cap-free) et un flag
 * SKIPPED pour le rendu mi-grise. percent peut depasser 100 (user qui
 * pulverise un goal — affiche tel quel pour l'effort visible). Les goals
 * sans target valide (targetMin <= 0) sont exclus en amont.
 */
data class GoalWithPercent(
    val goal: MuscleGoal,
    val muscleName: String,
    val muscleGroup: String?,
    val zone: String?,
    val targetMin: Int,
    val percent: Float,
    val isSkipped: Boolean,
)

/**
 * Une bar du chart footer : key (muscle name / group / zone selon viewMode),
 * percent agrege (cap-free), zone associee (pour deriver la couleur via
 * paletteForZone cote UI), isSkipped (alpha 0.4 sur la bar). Pour les modes
 * GROUP/ZONE le isSkipped n'est true que si TOUS les goals agreges sont
 * skipped (cas marginal).
 */
data class GoalsChartBar(
    val key: String,
    val percent: Float,
    val zone: String?,
    val isSkipped: Boolean,
)
enum class NormalizedZone {
    FULL_BODY,
    UPPER_BODY,
    LOWER_BODY,
    OTHER,
    // Refactor 3-niveaux 2026-05-08 : les 6 zones canoniques DB (cf. data/Zones.kt).
    CHEST,
    BACK,
    SHOULDERS,
    ARMS,
    LEGS,
    CORE,
    // Legacy fine-grained zones (pre-2026-05-08), conservees pour ne pas casser
    // d'eventuels callers externes mais plus alimentees par normalizeZone.
    UPPER_ARMS,
    LOWER_ARMS,
    UPPER_LEG,
    LOWER_LEG
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsTabViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actualWorkoutExerciseDao: ActualWorkoutExerciseDao,
    private val actualWorkoutSetDao: ActualWorkoutSetDao,
    private val exerciseMuscleDao: ExerciseMuscleDao,
    private val muscleDao: MuscleDao,
    private val muscleGoalDao: MuscleGoalDao,
    private val syncEngine: SyncEngine,
    private val syncManager: SyncManager
) : ViewModel() {

    val userId: StateFlow<Int?> =
        CurrentUserManager.userIdFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CurrentUserManager.userId)

    // Décalage de semaine (0 = courante)
    private val _currentWeekOffset = MutableStateFlow(0)
    val currentWeekOffset: StateFlow<Int> = _currentWeekOffset

    // ISO de la semaine courante
    private val currentWeekISO: StateFlow<String> =
        _currentWeekOffset
            .map { offset -> getWeekISOFromOffset(offset) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), getCurrentWeekISO())

    // Muscles (réactif depuis Room)
    val muscles: StateFlow<List<Muscle>> =
        muscleDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Mode de tri sélectionné
    private val _sortMode = MutableStateFlow(SortMode.BY_NAME)
    val sortMode: StateFlow<SortMode> = _sortMode

    // Vue combinée des goals enrichis (Flow réactif, LECTURE PURE).
    // Calcule le `done` (sets validés par muscle) en mémoire + applique le tri.
    // Aucun effet de bord : l'auto-completion est gérée par un flow dédié dans
    // init {} (cf. autoCompleteFinishedGoals) pour ne pas écrire en DB depuis la
    // lambda d'un opérateur de flow (anti-pattern : non déterministe, boucles).
    val muscleGoals: StateFlow<List<MuscleGoal>> =
        combine(currentWeekISO, sortMode) { weekISO, sort -> weekISO to sort }
            .flatMapLatest { (weekISO, sort) ->
                val start = getStartOfWeek(weekISO)
                val end = getEndOfWeek(weekISO)

                combine(
                    muscleGoalDao.observeGoalsForWeek(weekISO),
                    actualWorkoutExerciseDao.observeActualWorkoutExercisesForWeek(start, end),
                    actualWorkoutSetDao.observeActualWorkoutSetsForWeek(start, end),
                    exerciseMuscleDao.observeAllLinks(),
                    muscles,
                ) { goals, exercises, sets, links, muscleList ->

                    // === Regroupement : Sets validés par muscle ===
                    val muscleDoneCount: Map<String, Int> =
                        links
                            .flatMap { link ->
                                val matchingExercises = exercises.filter { it.exerciseUUID == link.exerciseUUID }
                                if (matchingExercises.isEmpty()) return@flatMap emptyList()

                                val totalSetsForThisLink = matchingExercises.sumOf { ex ->
                                    val expectedReps = ex.reps.split("-")
                                        .firstOrNull()?.toIntOrNull() ?: 0

                                    sets.count { s ->
                                        s.actualWorkoutExerciseUUID == ex.uuid &&
                                                ((s.reps ?: 0) >= expectedReps || s.status.equals("DONE", ignoreCase = true))
                                    }
                                }

                                if (totalSetsForThisLink == 0) emptyList()
                                else listOf(link.muscleUUID to totalSetsForThisLink)
                            }
                            .groupBy({ it.first }, { it.second })
                            .mapValues { (_, counts) -> counts.sum() }

                    // Enrichissement des goals avec le `done` calculé
                    val enriched = goals.map { g ->
                        g.copy(done = muscleDoneCount[g.muscleUUID] ?: 0)
                    }

                    // === Tri en fonction du mode choisi ===
                    sortGoals(enriched, sort, muscleList)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tri d'une liste de goals enrichis selon le SortMode (nom / priorite). */
    private fun sortGoals(
        goals: List<MuscleGoal>,
        sort: SortMode,
        muscleList: List<Muscle>,
    ): List<MuscleGoal> {
        val muscleNameMap = muscleList.associateBy({ it.uuid }, { it.name })
        return when (sort) {
            SortMode.BY_NAME ->
                goals.sortedBy { muscleNameMap[it.muscleUUID]?.lowercase() ?: "" }
            SortMode.BY_PRIORITY -> {
                val priorityOrder = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)
                goals.sortedWith(compareBy(
                    { priorityOrder[it.priority] ?: Int.MAX_VALUE },
                    { muscleNameMap[it.muscleUUID]?.lowercase() ?: "" }
                ))
            }
        }
    }

    init {
        // === Auto-completion réactive ===
        // Observe les goals enrichis (avec `done` calculé) et complète
        // automatiquement (status=DONE) ceux dont la cible est atteinte. Remplace
        // l'ancien effet de bord injecte dans la lambda du combine (ne se
        // declenchait pas de maniere fiable). distinctUntilChanged sur l'ensemble
        // des uuids eligibles evite les ecritures redondantes / boucles.
        muscleGoals
            .map { goals -> goals.filter { shouldAutoComplete(it) }.map { it.uuid }.toSet() }
            .distinctUntilChanged()
            .onEach { uuidsToComplete ->
                if (uuidsToComplete.isNotEmpty()) {
                    autoCompleteFinishedGoals(uuidsToComplete)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Persiste le passage a DONE des goals dont l'uuid est fourni (cibles
     * deja jugees atteintes en amont sur le `done` calcule) et declenche un
     * push de sync si au moins un goal a ete complete. Extraite pour etre
     * testable en isolation (Room in-memory + SyncEngine mocke).
     *
     * Garde defensive : re-lit l'etat courant en DB et ignore les goals qui
     * sont entre-temps passes a DONE/SKIPPED ou en pendingDeletion (course
     * possible entre l'evaluation du flow et l'ecriture). Ne re-evalue PAS
     * `done >= target` car `done` n'est pas persiste (calcule en memoire).
     * Retourne le nombre de goals effectivement completes.
     */
    suspend fun autoCompleteFinishedGoals(uuids: Set<String>): Int {
        var completed = 0
        uuids.forEach { uuid ->
            val current = muscleGoalDao.getByUUID(uuid) ?: return@forEach
            if (current.pendingDeletion) return@forEach
            val status = normalizeStatus(current.status)
            if (status == "DONE" || status == "SKIPPED") return@forEach
            muscleGoalDao.updateStatus(uuid, "DONE")
            completed++
        }
        if (completed > 0) {
            syncEngine.pushEntityClass(MuscleGoal::class)
        }
        return completed
    }

    // === Actions ===

    fun changeWeekOffset(newOffset: Int) {
        _currentWeekOffset.value = newOffset
    }

    fun sortGoalsByName() {
        _sortMode.value = SortMode.BY_NAME
    }

    fun sortGoalsByPriorityThenName() {
        _sortMode.value = SortMode.BY_PRIORITY
    }

    fun addMuscleGoal(goal: MuscleGoal) {
        viewModelScope.launch {
            try {
                muscleGoalDao.insert(goal)
                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Erreur lors de l'ajout du goal", e)
            }
        }
    }

    fun markMuscleGoalForDeletion(muscleGoal: MuscleGoal) {
        viewModelScope.launch {
            try {
                muscleGoalDao.markAsPendingDeletion(muscleGoal.uuid)
                muscleGoalDao.markAsUnsynced(muscleGoal.uuid)
                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Erreur lors de la suppression du goal", e)
            }
        }
    }

    fun syncAllMuscleGoals() {
        viewModelScope.launch {
            syncEngine.pushEntityClass(MuscleGoal::class)
        }
    }

    fun updateMuscleGoalStatus(uuid: String, newStatus: String) {
        viewModelScope.launch {
            try {
                muscleGoalDao.updateStatus(uuid, newStatus)
                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Failed to update status", e)
            }
        }
    }

    fun updateMuscleGoalPriority(uuid: String, newPriority: String) {
        viewModelScope.launch {
            try {
                muscleGoalDao.updatePriority(uuid, newPriority)
                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Failed to update priority", e)
            }
        }
    }

    fun updateMuscleGoalTarget(uuid: String, newTarget: String) {
        viewModelScope.launch {
            try {
                muscleGoalDao.updateTarget(uuid, newTarget)

                // ✅ recalcul côté VM avec le done calculé (state actuel)
                val current = muscleGoals.value.firstOrNull { it.uuid == uuid }
                if (current != null) {
                    val targetMin = parseTargetMinimum(newTarget)
                    val status = normalizeStatus(current.status)

                    if (status != "SKIPPED") {
                        val shouldBeDone = current.done >= targetMin
                        if (shouldBeDone && status != "DONE") {
                            muscleGoalDao.updateStatus(uuid, "DONE")
                        }
                        // Optionnel : si tu veux redescendre à IN_PROGRESS quand target augmente
                        else if (!shouldBeDone && status == "DONE") {
                            muscleGoalDao.updateStatus(uuid, "IN_PROGRESS")
                        }
                    }
                }

                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Failed to update target", e)
            }
        }
    }


    fun copyGoalsFromLastWeek() {
        viewModelScope.launch {
            try {
                val offset = _currentWeekOffset.value
                val thisWeekISO = getWeekISOFromOffset(offset)
                val lastWeekISO = getWeekISOFromOffset(offset - 1)

                val current = muscleGoalDao.getGoalsForWeek(thisWeekISO)
                    .filter { !it.pendingDeletion }
                val existingMuscles = current.map { it.muscleUUID }.toSet()

                val previous = muscleGoalDao.getGoalsForWeek(lastWeekISO)
                    .filter { !it.pendingDeletion }

                if (previous.isEmpty()) {
                    showSnackbar(
                        message = context.getString(R.string.vm_goals_no_to_copy_prev),
                        type = com.example.sportapp.core.utils.SnackbarType.INFO,
                        duration = SnackbarDuration.Short
                    )
                    return@launch
                }

                // Si la semaine est vide -> copie tout
                // Sinon -> copie uniquement les muscles absents
                val toCopy = if (current.isEmpty()) {
                    previous
                } else {
                    previous.filter { it.muscleUUID !in existingMuscles }
                }

                if (toCopy.isEmpty()) {
                    showSnackbar(
                        message = context.getString(R.string.vm_goals_no_to_copy_last),
                        type = com.example.sportapp.core.utils.SnackbarType.INFO,
                        duration = SnackbarDuration.Short
                    )
                    return@launch
                }

                val copied = toCopy.map { g ->
                    g.copy(
                        uuid = java.util.UUID.randomUUID().toString(),
                        weekISO = thisWeekISO,
                        done = 0,
                        status = "NOT_STARTED",
                        pendingDeletion = false,
                        addedManually = true
                    )
                }

                muscleGoalDao.insertAll(copied)
                syncEngine.pushEntityClass(MuscleGoal::class)
            } catch (e: Exception) {
                Log.e("GoalsTabViewModel", "❌ Failed to copy goals from last week", e)
                showSnackbar(
                    message = context.getString(R.string.vm_goals_failed_copy_last),
                    type = com.example.sportapp.core.utils.SnackbarType.ERROR,
                )
            }
        }
    }

    // Display Mode

    private val _displayMode = MutableStateFlow(GoalsDisplayMode.FLAT)
    val displayMode: StateFlow<GoalsDisplayMode> = _displayMode

    fun toggleDisplayMode() {
        _displayMode.value = when (_displayMode.value) {
            GoalsDisplayMode.FLAT -> GoalsDisplayMode.BY_ZONE
            GoalsDisplayMode.BY_ZONE -> GoalsDisplayMode.FLAT
        }
    }

    fun normalizeZone(raw: String?): NormalizedZone {
        if (raw.isNullOrBlank()) return NormalizedZone.OTHER

        val value = raw
            .trim()
            .replace("-", "_")
            .replace(" ", "_")
            .uppercase()

        return when (value) {
            // 6 zones canoniques DB (refactor 3-niveaux 2026-05-08).
            "CHEST" -> NormalizedZone.CHEST
            "BACK" -> NormalizedZone.BACK
            "SHOULDERS" -> NormalizedZone.SHOULDERS
            "ARMS" -> NormalizedZone.ARMS
            "LEGS" -> NormalizedZone.LEGS
            "CORE", "TRUNK", "TRONC", "ABS" -> NormalizedZone.CORE
            // Legacy 2-niveaux (pre-refactor) conserves pour compat retro.
            "UPPER_BODY", "UPPERBODY", "HAUT_DU_CORPS" -> NormalizedZone.UPPER_BODY
            "LOWER_BODY", "LOWERBODY", "BAS_DU_CORPS" -> NormalizedZone.LOWER_BODY
            "FULL_BODY", "FULLBODY", "CORPS_ENTIER" -> NormalizedZone.FULL_BODY
            else -> NormalizedZone.OTHER
        }
    }

    private fun normalizeStatus(s: String?) =
        s?.trim()?.uppercase()?.replace(" ", "_") ?: "IN_PROGRESS"

    @androidx.annotation.VisibleForTesting
    internal fun shouldAutoComplete(goal: MuscleGoal): Boolean {
        val status = normalizeStatus(goal.status)
        if (goal.pendingDeletion) return false
        if (status == "DONE" || status == "SKIPPED") return false
        val targetMin = parseTargetMinimum(goal.target)
        return goal.done >= targetMin
    }

    // ── Refonte 2026-05-09 : 3 niveaux d'affichage (MUSCLE/GROUP/ZONE) +
    // 5 modes de tri partages liste & chart footer (% achievement).
    // Ces flows co-existent avec muscleGoals/displayMode/sortMode legacy
    // tant que l'UI n'a pas migre (cleanup a l'etape finale de la refonte).

    private val _goalsViewMode = MutableStateFlow(GoalsViewMode.MUSCLE)
    val goalsViewMode: StateFlow<GoalsViewMode> = _goalsViewMode

    private val _goalsSortMode = MutableStateFlow(GoalsSortMode.ALPHA)
    val goalsSortMode: StateFlow<GoalsSortMode> = _goalsSortMode

    fun setGoalsViewMode(mode: GoalsViewMode) { _goalsViewMode.value = mode }
    fun setGoalsSortMode(mode: GoalsSortMode) { _goalsSortMode.value = mode }

    /**
     * Goals enrichis avec percent/skipped/zone, filtres :
     *  - pendingDeletion=true exclus (deja fait via observeGoalsForWeek mais
     *    on re-filtre par precaution)
     *  - targetMin <= 0 exclus (pas de division par zero, cf. Section B Q4
     *    de la conversation 2026-05-09)
     *  - muscle introuvable (FK pendante) exclus
     */
    val goalsWithPercent: StateFlow<List<GoalWithPercent>> =
        combine(muscleGoals, muscles) { goals, muscleList ->
            val muscleByUuid = muscleList.associateBy { it.uuid }
            goals.filter { !it.pendingDeletion }.mapNotNull { goal ->
                val muscle = muscleByUuid[goal.muscleUUID] ?: return@mapNotNull null
                val targetMin = parseTargetMinimum(goal.target)
                if (targetMin <= 0) return@mapNotNull null
                val percent = goal.done * 100f / targetMin.toFloat()
                val isSkipped = goal.status.equals("SKIPPED", ignoreCase = true)
                GoalWithPercent(
                    goal = goal,
                    muscleName = muscle.name,
                    muscleGroup = muscle.muscleGroup,
                    zone = muscle.zone,
                    targetMin = targetMin,
                    percent = percent,
                    isSkipped = isSkipped,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Liste plate triee selon goalsSortMode. Utilisee pour le mode MUSCLE. */
    val goalsListSorted: StateFlow<List<GoalWithPercent>> =
        combine(goalsWithPercent, goalsSortMode) { goals, sort -> sortGoalsList(goals, sort) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Goals groupes par muscle_group (cle = nom du group) puis tries selon
     *  goalsSortMode au niveau des cles. Goals sans muscleGroup en cle "Other".
     *
     *  Retourne List<Pair<>> et NON Map<> : `Map.equals` est set-based donc
     *  un changement de tri (meme contenu, ordre different) ne re-emettrait
     *  PAS sur StateFlow `distinctUntilChanged`. List.equals est order-aware
     *  -> les fixtures filtres ALPHA/PALETTE/PERCENT* declenchent bien un
     *  re-render UI (bug fix runtime 2026-05-09). */
    val goalsByGroupSorted: StateFlow<List<Pair<String, List<GoalWithPercent>>>> =
        combine(goalsWithPercent, goalsSortMode) { goals, sort ->
            val grouped: MutableMap<String, List<GoalWithPercent>> =
                goals.filter { it.muscleGroup != null }
                    .groupBy { it.muscleGroup!! }
                    .toMutableMap()
            val orphans = goals.filter { it.muscleGroup == null }
            if (orphans.isNotEmpty()) grouped["Other"] = orphans
            val groupToZone = goals.mapNotNull {
                val g = it.muscleGroup ?: return@mapNotNull null
                val z = it.zone ?: return@mapNotNull null
                g to z
            }.distinct().toMap()
            sortGroupedKeys(grouped, sort) { groupName -> groupToZone[groupName] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Goals groupes par zone (Chest/Back/.../Core). Goals sans zone en "Other".
     *  Retourne List<Pair<>> pour la meme raison que goalsByGroupSorted. */
    val goalsByZoneSorted: StateFlow<List<Pair<String, List<GoalWithPercent>>>> =
        combine(goalsWithPercent, goalsSortMode) { goals, sort ->
            val grouped: Map<String, List<GoalWithPercent>> =
                goals.groupBy { it.zone ?: "Other" }
            // Pour mode ZONE : la cle EST la zone -> mapping identity.
            sortGroupedKeys(grouped, sort) { zoneName -> zoneName }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Donnees du chart footer : 1 bar par muscle/group/zone selon viewMode.
     *  Percent agrege (moyenne ponderee = sum(done) / sum(target) * 100) pour
     *  les modes GROUP/ZONE. Zone associee sert a deriver la couleur via
     *  paletteForZone cote UI. */
    val chartData: StateFlow<List<GoalsChartBar>> =
        combine(
            goalsViewMode,
            goalsListSorted,
            goalsByGroupSorted,
            goalsByZoneSorted,
        ) { mode, list, byGroup, byZone ->
            when (mode) {
                GoalsViewMode.MUSCLE -> list.map {
                    GoalsChartBar(
                        key = it.muscleName,
                        percent = it.percent,
                        zone = it.zone,
                        isSkipped = it.isSkipped,
                    )
                }
                GoalsViewMode.GROUP -> byGroup.map { (group, goals) ->
                    GoalsChartBar(
                        key = group,
                        percent = aggregatePercent(goals),
                        zone = goals.firstNotNullOfOrNull { it.zone },
                        isSkipped = goals.isNotEmpty() && goals.all { it.isSkipped },
                    )
                }
                GoalsViewMode.ZONE -> byZone.map { (zone, goals) ->
                    GoalsChartBar(
                        key = zone,
                        percent = aggregatePercent(goals),
                        zone = zone,
                        isSkipped = goals.isNotEmpty() && goals.all { it.isSkipped },
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tri unifie d'une liste plate de goals selon GoalsSortMode (5 modes). */
    private fun sortGoalsList(
        goals: List<GoalWithPercent>,
        sortMode: GoalsSortMode,
    ): List<GoalWithPercent> = when (sortMode) {
        GoalsSortMode.ALPHA -> goals.sortedBy { it.muscleName.lowercase() }
        GoalsSortMode.PALETTE -> goals.sortedWith(compareBy(
            { gp ->
                val idx = Zones.ALL.indexOf(gp.zone ?: "")
                if (idx < 0) Int.MAX_VALUE else idx
            },
            { it.muscleName.lowercase() },
        ))
        GoalsSortMode.PERCENT_DESC -> goals.sortedByDescending { it.percent }
        GoalsSortMode.PERCENT_ASC -> goals.sortedBy { it.percent }
        GoalsSortMode.PRIORITY -> {
            val priorityOrder = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)
            goals.sortedWith(compareBy(
                { priorityOrder[it.goal.priority] ?: Int.MAX_VALUE },
                { it.muscleName.lowercase() },
            ))
        }
    }

    /** Tri unifie des cles d'un Map<String, List<GoalWithPercent>> (modes
     *  GROUP/ZONE). Items intra-cle restent tries alpha (lisibilite intra-card).
     *  Retourne List<Pair<>> pour preserver l'ordre cote StateFlow (cf. comment
     *  goalsByGroupSorted : Map.equals set-based casserait la re-emission). */
    private fun sortGroupedKeys(
        grouped: Map<String, List<GoalWithPercent>>,
        sortMode: GoalsSortMode,
        keyToZone: (String) -> String?,
    ): List<Pair<String, List<GoalWithPercent>>> {
        val sortedEntries = when (sortMode) {
            GoalsSortMode.ALPHA -> grouped.entries.sortedBy { it.key.lowercase() }
            GoalsSortMode.PALETTE -> grouped.entries.sortedWith(compareBy(
                { entry ->
                    val idx = Zones.ALL.indexOf(keyToZone(entry.key) ?: "")
                    if (idx < 0) Int.MAX_VALUE else idx
                },
                { it.key.lowercase() },
            ))
            GoalsSortMode.PERCENT_DESC -> grouped.entries.sortedByDescending {
                aggregatePercent(it.value)
            }
            GoalsSortMode.PERCENT_ASC -> grouped.entries.sortedBy {
                aggregatePercent(it.value)
            }
            GoalsSortMode.PRIORITY -> {
                // Sort group keys by max priority of any goal inside (HIGH first).
                val priorityOrder = mapOf("HIGH" to 0, "MEDIUM" to 1, "LOW" to 2)
                grouped.entries.sortedWith(compareBy(
                    { entry ->
                        entry.value.minOfOrNull {
                            priorityOrder[it.goal.priority] ?: Int.MAX_VALUE
                        } ?: Int.MAX_VALUE
                    },
                    { it.key.lowercase() },
                ))
            }
        }
        return sortedEntries.map { entry ->
            entry.key to entry.value.sortedBy { it.muscleName.lowercase() }
        }
    }

    /** Pourcentage d'achievement agrege : sum(done) / sum(targetMin) * 100. */
    private fun aggregatePercent(goals: List<GoalWithPercent>): Float {
        val totalDone = goals.sumOf { it.goal.done }
        val totalTarget = goals.sumOf { it.targetMin }
        return if (totalTarget <= 0) 0f else totalDone * 100f / totalTarget.toFloat()
    }

}
