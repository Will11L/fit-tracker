package com.example.sportapp.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.feature.demo_tour.data.DemoTourRepository
import com.example.sportapp.feature.demo_tour.data.SampleDataInserter
import com.example.sportapp.core.network.ApiUserService
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.MeProfileUpdateRequest
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.feature.onboarding.data.AppLocale
import com.example.sportapp.feature.onboarding.data.LengthUnit
import com.example.sportapp.feature.onboarding.data.OnboardingPreferences
import com.example.sportapp.feature.onboarding.data.OnboardingRepository
import com.example.sportapp.feature.onboarding.data.PostOnboardingSetupState
import com.example.sportapp.feature.onboarding.data.ThemeMode
import com.example.sportapp.feature.onboarding.data.WeekStart
import com.example.sportapp.feature.onboarding.data.WeightUnit
import com.example.sportapp.feature.onboarding.domain.OnboardingStep
import com.example.sportapp.feature.settings.AppSettings
import com.example.sportapp.feature.settings.AppSettingsRepository
import com.example.sportapp.core.utils.SnackbarType
import com.example.sportapp.core.utils.showSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VM partagé du flow B1 onboarding (3 écrans).
 *
 * Décisions Phase 1 (validées 2026-05-11) :
 * - Skippable à chaque étape via OnboardingFooter.
 * - Pas de persistance partielle : si user quit en plein onboarding,
 *   relance à l'étape Welcome au prochain run.
 * - 2026-05-11 (revision user) : steps Muscles + Exercises supprimés
 *   (le starter pack V8.4 suffit, pas de friction onboarding pour
 *   désélectionner -- l'user le fera via les écrans dédiés s'il veut).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repo: OnboardingRepository,
    private val appSettingsRepo: AppSettingsRepository,
    private val sampleInserter: SampleDataInserter,
    private val demoTourRepo: DemoTourRepository,
    private val postOnboardingSetupState: PostOnboardingSetupState,
) : ViewModel() {

    /** Settings globaux app (sound, vibration, overlay, phone notifs).
     *  Exposés en onboarding step 3 via Switches pour que l'user les
     *  configure dès le 1er run (cf. feedback_onboarding_extension.md). */
    val appSettings: StateFlow<AppSettings> = appSettingsRepo.settings

    private val api: ApiUserService = RetrofitInstance.userService

    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    // Welcome step state
    private val _firstNameDraft = MutableStateFlow("")
    val firstNameDraft: StateFlow<String> = _firstNameDraft.asStateFlow()
    private val _initialFirstName = MutableStateFlow<String?>(null)
    val initialFirstName: StateFlow<String?> = _initialFirstName.asStateFlow()

    // Preferences + Permissions step state (les 2 partagent le même OnboardingPreferences)
    private val _preferencesDraft = MutableStateFlow(OnboardingPreferences())
    val preferencesDraft: StateFlow<OnboardingPreferences> = _preferencesDraft.asStateFlow()

    // BIO step state -- 4 fields nullable (skippable). String pour les inputs
    // numériques (UI = TextField) -> conversion Float au moment du PATCH.
    private val _birthDateDraft = MutableStateFlow<String?>(null)  // ISO "YYYY-MM-DD"
    val birthDateDraft: StateFlow<String?> = _birthDateDraft.asStateFlow()
    private val _sexDraft = MutableStateFlow<String?>(null)  // UPPER_CASE MALE/FEMALE/OTHER
    val sexDraft: StateFlow<String?> = _sexDraft.asStateFlow()
    private val _heightCmDraft = MutableStateFlow("")
    val heightCmDraft: StateFlow<String> = _heightCmDraft.asStateFlow()
    private val _weightKgDraft = MutableStateFlow("")
    val weightKgDraft: StateFlow<String> = _weightKgDraft.asStateFlow()


    init {
        viewModelScope.launch {
            _preferencesDraft.value = repo.dataStore.snapshot()
        }
    }

    // ===== Navigation =====

    fun goToStep(step: OnboardingStep) {
        _currentStep.value = step
    }

    // ===== Welcome step =====

    fun setInitialFirstName(name: String?) {
        _initialFirstName.value = name
        if (_firstNameDraft.value.isBlank()) _firstNameDraft.value = name.orEmpty()
    }

    fun updateFirstNameDraft(value: String) {
        _firstNameDraft.value = value
    }

    /** Si le firstName a changé vs initial, push au serveur. Sinon no-op. */
    private suspend fun applyWelcomeIfNeeded() {
        val draft = _firstNameDraft.value.trim()
        if (draft.isNotBlank() && draft != (_initialFirstName.value ?: "")) {
            try {
                val updated = api.updateMeProfile(MeProfileUpdateRequest(firstName = draft))
                _initialFirstName.value = updated.firstName
            } catch (e: Exception) {
                showSnackbar(
                    message = context.getString(com.example.sportapp.R.string.vm_onboarding_save_name_error, e.message ?: e::class.simpleName ?: ""),
                    type = SnackbarType.WARNING,
                )
            }
        }
    }

    // ===== BIO step =====

    fun setBirthDate(iso: String?) { _birthDateDraft.value = iso }
    fun setSex(sex: String?) { _sexDraft.value = sex }
    fun setHeightCm(value: String) { _heightCmDraft.value = value }
    fun setWeightKg(value: String) { _weightKgDraft.value = value }

    /** Patch bio fields si au moins un n'est pas vide. Skip silencieux si rien.
     *  Convertit l'input user (dans son unité d'affichage choisie en PREFERENCES)
     *  vers les canoniques cm/kg avant l'API. */
    private suspend fun applyBioIfNeeded() {
        val birth = _birthDateDraft.value
        val sex = _sexDraft.value
        val heightInput = _heightCmDraft.value.replace(',', '.').toFloatOrNull()
        val weightInput = _weightKgDraft.value.replace(',', '.').toFloatOrNull()
        // Conversion -> canonique cm/kg selon units choisis en PREFERENCES.
        val heightCm = heightInput?.let {
            if (_preferencesDraft.value.lengthUnit == LengthUnit.INCHES) it * 2.54f else it
        }
        val weightKg = weightInput?.let {
            if (_preferencesDraft.value.weightUnit == WeightUnit.LBS) it * 0.453592f else it
        }
        if (birth == null && sex == null && heightCm == null && weightKg == null) return
        try {
            api.updateMeProfile(MeProfileUpdateRequest(
                birthDate = birth,
                sex = sex,
                heightCm = heightCm,
                weightKg = weightKg,
            ))
        } catch (e: Exception) {
            showSnackbar(
                message = context.getString(com.example.sportapp.R.string.vm_onboarding_save_bio_error, e.message ?: e::class.simpleName ?: ""),
                type = SnackbarType.WARNING,
            )
        }
    }

    // ===== Preferences + Permissions step (settings communs) =====

    fun setWeekStart(weekStart: WeekStart) {
        _preferencesDraft.value = _preferencesDraft.value.copy(weekStart = weekStart)
    }

    fun setMorningRoutineTime(hour: Int, minute: Int) {
        _preferencesDraft.value = _preferencesDraft.value.copy(
            morningRoutineHour = hour, morningRoutineMinute = minute,
        )
    }

    fun setAutoSyncOnWifi(enabled: Boolean) {
        _preferencesDraft.value = _preferencesDraft.value.copy(autoSyncOnWifi = enabled)
    }

    fun setWeightUnit(unit: WeightUnit) {
        _preferencesDraft.value = _preferencesDraft.value.copy(weightUnit = unit)
    }

    fun setLengthUnit(unit: LengthUnit) {
        _preferencesDraft.value = _preferencesDraft.value.copy(lengthUnit = unit)
    }

    fun setThemeMode(mode: ThemeMode) {
        _preferencesDraft.value = _preferencesDraft.value.copy(themeMode = mode)
    }

    /** Update draft + persist au DataStore immediatement. Le draft sert a l'etat
     *  UI (radio selectionne). Le DataStore est observe par le CompositionLocalProvider
     *  global de MainActivity -> live preview de la traduction sur la page onboarding
     *  des le tap, sans recreation d'Activity. */
    fun setAppLocale(locale: AppLocale) {
        _preferencesDraft.value = _preferencesDraft.value.copy(appLocale = locale)
        viewModelScope.launch { repo.setAppLocale(locale) }
    }

    fun setRunDemoTour(enabled: Boolean) {
        _preferencesDraft.value = _preferencesDraft.value.copy(runDemoTour = enabled)
    }

    // ===== AppSettings (sound / vibration globaux) =====
    // Sauvegardés direct (pas de draft -- Switch est un toggle live, pas
    // un draft à confirmer au Next).

    fun setSoundOnInAppNotification(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepo.setSoundOnInAppNotification(enabled) }
    }

    fun setVibrateOnInAppNotification(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepo.setVibrateOnInAppNotification(enabled) }
    }

    private suspend fun applyPreferences() {
        repo.setPreferences(_preferencesDraft.value)
    }

    // ===== Confirm next : applique les changements de l'étape courante puis avance =====

    fun confirmAndNext(onAllDone: () -> Unit) {
        viewModelScope.launch {
            when (_currentStep.value) {
                OnboardingStep.WELCOME -> applyWelcomeIfNeeded()
                OnboardingStep.BIO -> applyBioIfNeeded()
                OnboardingStep.PREFERENCES -> applyPreferences()
                OnboardingStep.PERMISSIONS -> applyPreferences()  // re-persist au cas où on a touché un toggle
            }
            val next = _currentStep.value.next()
            if (next == null) {
                finishOnboarding(onAllDone)
            } else {
                _currentStep.value = next
            }
        }
    }

    // ===== Finish / Skip =====

    fun finishOnboarding(onDone: () -> Unit) {
        val userId = CurrentUserManager.userId ?: run { onDone(); return }
        // NOTE 2026-05-12 : plus d'appel a LocaleHelper.apply ici. La locale
        // est persistee dans le DataStore (applyPreferences ci-dessus) ; au
        // boot suivant, MainActivity.CompositionLocalProvider lit le DataStore
        // et applique automatiquement la locale a toute l'app -- pas besoin
        // de AppCompat.setApplicationLocales (qui recreerait l'Activity, risque
        // de casser la nav HOME pendant l'overlay 2.2s).
        // Overlay global affiché 2.2s -- couvre l'insert (~300ms) + navigate +
        // fade transition NavHost (300ms) + 1er render HomeScreen Flow Room
        // re-query (~500ms). L'overlay est porté par MainActivity (Singleton),
        // donc survit à la destruction de ce VM par la nav vers HOME.
        postOnboardingSetupState.showFor(durationMs = 2200L)
        viewModelScope.launch {
            if (_preferencesDraft.value.runDemoTour) {
                try {
                    sampleInserter.insertSampleWorkouts(userId)
                    demoTourRepo.markTourActive(userId)
                } catch (_: Exception) {
                    // no-op : insert raté = pas de sample data, mais l'user finit son onboarding.
                }
            }
            repo.markDone(userId)
            onDone()
        }
    }

    /** Skip = mark done + go home, sans appliquer les drafts en cours.
     *  Ne déclenche PAS l'insert sample data (skip = "I don't want any of this"). */
    fun skipOnboarding(onDone: () -> Unit) {
        val userId = CurrentUserManager.userId ?: run { onDone(); return }
        repo.markDone(userId)
        onDone()
    }
}
