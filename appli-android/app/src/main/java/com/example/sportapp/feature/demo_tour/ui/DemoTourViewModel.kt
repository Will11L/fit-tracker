package com.example.sportapp.feature.demo_tour.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.ActualWorkoutDao
import com.example.sportapp.feature.demo_tour.data.DemoTourRepository
import com.example.sportapp.feature.demo_tour.data.SampleDataInserter
import com.example.sportapp.feature.demo_tour.domain.DemoTourStep
import com.example.sportapp.core.network.CurrentUserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Orchestre le tour visuel post-onboarding (scope C session 2).
 *
 * Cycle de vie :
 * 1. OnboardingViewModel.finishOnboarding pose `demoTourRepo.markTourActive(uid)`
 *    + insère les sample data, puis navigate HOME.
 * 2. MainActivity hoist ce VM et appelle `checkAndStartTour()` à l'arrivée sur HOME.
 *    Si flag actif → currentStep = WELCOME, et précharge l'UUID du 1er sample
 *    workout pour la nav SESSION.
 * 3. MainActivity observe `currentStep` et navigate vers le targetRoute associé.
 * 4. L'overlay `DemoCaptionOverlay` lit `currentStep` et render la caption.
 * 5. Clic Next → `nextStep()` → advance ou si GOODBYE → `endTour()` (cleanup + clear flag).
 * 6. Clic Skip → `skipTour()` direct (cleanup + clear flag).
 *
 * Fallback crash-safe : si l'app est killée pendant le tour, le flag persiste.
 * Le SplashScreenViewModel cleanup les sample data au prochain ColdStart et
 * clear le flag — l'overlay ne reviendra pas (markDone est déjà true côté
 * onboarding, donc on file direct vers home sans checkAndStartTour).
 */
@HiltViewModel
class DemoTourViewModel @Inject constructor(
    private val sampleInserter: SampleDataInserter,
    private val demoTourRepo: DemoTourRepository,
    private val workoutDao: ActualWorkoutDao,
) : ViewModel() {

    private val _currentStep = MutableStateFlow<DemoTourStep?>(null)
    val currentStep: StateFlow<DemoTourStep?> = _currentStep.asStateFlow()

    /** TargetKey du step courant (ex. "stats.chart"). Null si pas de step actif
     *  ou si le step n'a pas de cible (WELCOME / GOODBYE). Fourni à
     *  LocalDemoTourActiveTarget par MainActivity pour activer Modifier.demoHighlight. */
    val activeTargetKey: StateFlow<String?> = _currentStep
        .map { it?.targetKey }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** UUID du 1er sample workout (J-7 "Push Day") préchargé au startTour pour
     *  permettre la nav vers SESSION. Null si pas encore chargé ou si aucun
     *  sample workout trouvé (cas edge — on skip alors la step SESSION). */
    private val _firstSampleWorkoutUuid = MutableStateFlow<String?>(null)
    val firstSampleWorkoutUuid: StateFlow<String?> = _firstSampleWorkoutUuid.asStateFlow()

    /** Garde anti-double-démarrage : MainActivity peut appeler checkAndStartTour
     *  plusieurs fois (recomposition), on ne démarre qu'une seule fois par session. */
    private var hasCheckedThisSession: Boolean = false

    /** Émis quand le tour se termine (GOODBYE clic Got it, ou Skip).
     *  MainActivity collect et navigue vers HOME. */
    private val _tourEndedEvent = MutableSharedFlow<Unit>()
    val tourEndedEvent: SharedFlow<Unit> = _tourEndedEvent.asSharedFlow()

    /** Appelé depuis MainActivity à l'arrivée sur HOME (callback onFinish onboarding).
     *  Lit le flag SharedPreferences `demo_tour_active_user_<uid>`, si actif :
     *  précharge l'UUID 1er sample workout + set currentStep = WELCOME. */
    fun checkAndStartTour() {
        if (hasCheckedThisSession) return
        hasCheckedThisSession = true
        val userId = CurrentUserManager.userId ?: return
        if (!demoTourRepo.isTourActive(userId)) return
        viewModelScope.launch {
            _firstSampleWorkoutUuid.value = workoutDao.getAllOnce()
                .firstOrNull { it.uuid.startsWith("sample-w0-") }
                ?.uuid
            _currentStep.value = DemoTourStep.WELCOME
        }
    }

    /** Avance d'un step. Si on est sur GOODBYE → endTour. */
    fun nextStep() {
        val current = _currentStep.value ?: return
        val next = current.next()
        if (next == null) {
            endTour()
        } else {
            _currentStep.value = next
        }
    }

    /** Skip immédiat : cleanup + clear flag + tour terminé. Comportement
     *  identique à endTour mais sémantique différente (user a abandonné vs fini). */
    fun skipTour() {
        endTour()
    }

    private fun endTour() {
        val userId = CurrentUserManager.userId
        _currentStep.value = null
        viewModelScope.launch {
            try {
                sampleInserter.cleanupSampleWorkouts()
            } catch (_: Exception) {
                // no-op : cleanup raté, mais on clear quand même le flag pour
                // ne pas re-trigger le tour à la prochaine session.
            }
            userId?.let { demoTourRepo.clearTourActive(it) }
            _tourEndedEvent.emit(Unit)
        }
    }
}
