package com.example.sportapp.feature.auth.viewmodel

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.feature.auth.AuthManager
import com.example.sportapp.feature.demo_tour.data.DemoTourRepository
import com.example.sportapp.feature.demo_tour.data.SampleDataInserter
import com.example.sportapp.app.navigation.NavMode
import com.example.sportapp.app.navigation.NavModeManager
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.onboarding.data.OnboardingRepository
import com.example.sportapp.feature.onboarding.data.StartScreen
import com.example.sportapp.core.data.model.Quote
import com.example.sportapp.feature.quotes.data.QuotesRepository
import com.example.sportapp.core.sync.SyncCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashScreenViewModel @Inject constructor(
    private val syncCoordinator: SyncCoordinator,
    private val authManager: AuthManager,
    private val onboardingRepository: OnboardingRepository,
    private val sampleInserter: SampleDataInserter,
    private val demoTourRepo: DemoTourRepository,
    private val quotesRepository: QuotesRepository,
) : ViewModel() {

    var loadingText = mutableStateOf("Initialization...")
    val progress = mutableFloatStateOf(0f)

    val nextRoute = mutableStateOf("login")

    // Citation motivante tiree aleatoirement parmi celles du user (locale Room).
    // null = aucune citation -> le SplashScreen retombe proprement sur son
    // texte de chargement par defaut (fallback). Tirage non bloquant : il ne
    // gate jamais la navigation (cf. spec "local et non bloquant").
    val motivationalQuote = mutableStateOf<Quote?>(null)

    private val _isFinished = mutableStateOf(false)
    val isFinished get() = _isFinished

    init {
        loadRandomQuote()
        startSync { _isFinished.value = true }
    }

    /** Tire une citation au hasard parmi les citations locales actives. Best-effort. */
    private fun loadRandomQuote() {
        viewModelScope.launch {
            runCatching {
                quotesRepository.getActive().randomOrNull()?.let { motivationalQuote.value = it }
            }
        }
    }

    private fun startSync(onFinished: () -> Unit) {
        viewModelScope.launch {
            loadingText.value = "Authenticating..."
            progress.floatValue = 0.2f

            when (authManager.initAuth()) {
                AuthManager.AuthState.NeedLogin -> {
                    nextRoute.value = "login"
                    progress.floatValue = 1f
                    onFinished()
                    return@launch
                }
                AuthManager.AuthState.Offline -> {
                    // Mode offline -> respecte la pref startScreen (DataStore local OK
                    // sans réseau). Fallback HOME si lecture échoue.
                    nextRoute.value = pickStartRoute()
                    progress.floatValue = 1f
                    onFinished()
                    return@launch
                }
                AuthManager.AuthState.Authenticated -> {
                    // Default home, sera override par check onboarding + startScreen
                    // pref plus bas après le sync (besoin d'avoir le User Room hydraté
                    // + le CurrentUserManager.userId set par verifyToken/getUserInfo).
                    nextRoute.value = "home"
                }
            }

            progress.floatValue = 0.5f
            loadingText.value = "Synchronizing data..."

            try {
                // SyncCoordinator.onLogin = merge puis push (ordre Splash V4.4-B3 inverse)
                // + retry exponentiel sur echec.
                syncCoordinator.onLogin()
                progress.floatValue = 0.9f

                // Re-tire une citation maintenant que le sync a pu hydrater Room
                // (cas 1er login : les citations pre-seedees arrivent au pull).
                // Ne remplace que si le tirage initial n'avait rien trouve.
                if (motivationalQuote.value == null) loadRandomQuote()

                loadingText.value = "Finalizing..."
                delay(600)
            } catch (e: Exception) {
                loadingText.value = "Synchronization error."
            }

            // demo_tour cleanup : si le tour était actif au ColdStart précédent
            // (l'app a été fermée pendant ou après le tour, ou crashe), purger
            // les sample data avant de poursuivre vers home. Crash-safe.
            CurrentUserManager.userId?.let { uid ->
                if (demoTourRepo.isTourActive(uid)) {
                    try {
                        sampleInserter.cleanupSampleWorkouts()
                    } catch (_: Exception) {
                        // no-op : si cleanup échoue, on clear le flag quand même
                        // pour ne pas boucler indéfiniment au prochain ColdStart.
                    }
                    demoTourRepo.clearTourActive(uid)
                }
            }

            // B1 onboarding : check post-sync. Si user pas encore done -> ONBOARDING
            // au lieu de HOME. Per-user (clé `onboarding_done_user_<userId>`).
            // Sinon : respecte la pref startScreen choisie dans Settings.
            val uid = CurrentUserManager.userId
            nextRoute.value = when {
                uid != null && !onboardingRepository.isDone(uid) -> "onboarding"
                else -> pickStartRoute()
            }

            progress.floatValue = 1f
            onFinished()
        }
    }

    /** Lit la pref `startScreen` (DataStore) et mappe vers la route Compose
     *  correspondante. Fallback HOME en cas d'erreur (DataStore corrompu / I/O).
     *
     *  A7 — Le mode de nav (Sport/Nutrition/Santé) est persisté et doit survivre au
     *  cold start. En mode Nutrition on reprend sur le journal nutrition, en mode
     *  Santé sur le hub Santé (la pref startScreen ne couvre que des écrans Sport).
     *  En mode Sport : pref classique. */
    private suspend fun pickStartRoute(): String {
        if (NavModeManager.current == NavMode.NUTRITION) return Routes.NUTRITION
        if (NavModeManager.current == NavMode.HEALTH) return Routes.HEALTH_DASHBOARD
        return pickSportStartRoute()
    }

    private suspend fun pickSportStartRoute(): String = try {
        when (onboardingRepository.preferences.first().startScreen) {
            StartScreen.HOME -> Routes.HOME
            StartScreen.TASKS -> Routes.TASKS
            StartScreen.CALENDAR -> Routes.CALENDAR
            StartScreen.STATS -> Routes.STATS
            StartScreen.CHRONO -> Routes.CHRONO
            StartScreen.PROGRAM -> Routes.PROGRAM
            StartScreen.NOTIFICATIONS -> Routes.NOTIFICATIONS
            StartScreen.CONVERSATIONS -> Routes.CONVERSATIONS
        }
    } catch (_: Exception) {
        Routes.HOME
    }
}
