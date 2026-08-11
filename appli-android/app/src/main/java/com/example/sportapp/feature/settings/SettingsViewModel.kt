package com.example.sportapp.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.feature.onboarding.data.AppLocale
import com.example.sportapp.feature.onboarding.data.LengthUnit
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.feature.onboarding.data.OnboardingPreferences
import com.example.sportapp.feature.onboarding.data.OnboardingRepository
import com.example.sportapp.feature.onboarding.data.StartScreen
import com.example.sportapp.feature.onboarding.data.ThemeMode
import com.example.sportapp.feature.onboarding.data.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: AppSettingsRepository,
    private val onboardingRepo: OnboardingRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings

    /** Préférences user (units, weekStart, etc.) -- exposées en Settings
     *  post-onboarding pour modification ultérieure. */
    val userPreferences: Flow<OnboardingPreferences> = onboardingRepo.preferences

    fun setVibrate(enabled: Boolean) = viewModelScope.launch { repo.setVibrateOnInAppNotification(enabled) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { repo.setSoundOnInAppNotification(enabled) }
    fun setOverlay(enabled: Boolean) = viewModelScope.launch { repo.setShowInAppNotificationOverlay(enabled) }
    fun setPhone(enabled: Boolean) = viewModelScope.launch { repo.setShowPhoneNotifications(enabled) }

    /** Activation des notifications par catégorie (tâches / timers / routines). */
    fun setNotifyTasks(enabled: Boolean) = viewModelScope.launch { repo.setNotifyTasks(enabled) }
    fun setNotifyTimers(enabled: Boolean) = viewModelScope.launch { repo.setNotifyTimers(enabled) }
    fun setNotifyRoutines(enabled: Boolean) = viewModelScope.launch { repo.setNotifyRoutines(enabled) }

    /** Rappel par défaut (null = "Aucun"). Pré-remplit le sélecteur à la création
     *  d'une tâche/période sans réglage propre. */
    fun setDefaultReminder(minutes: Int?) = viewModelScope.launch { repo.setDefaultReminderMinutesBefore(minutes) }

    fun setWeightUnit(unit: WeightUnit) = viewModelScope.launch { onboardingRepo.setWeightUnit(unit) }
    fun setLengthUnit(unit: LengthUnit) = viewModelScope.launch { onboardingRepo.setLengthUnit(unit) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { onboardingRepo.setThemeMode(mode) }
    fun setStartScreen(screen: StartScreen) = viewModelScope.launch { onboardingRepo.setStartScreen(screen) }

    /** Change la locale globale. Persiste juste DataStore -- le
     *  CompositionLocalProvider au niveau MainActivity observe ce flow et
     *  recompose toute l'app instant en EN/FR sans recreation d'Activity
     *  (= 0 flash). Fix 2026-05-12 : AppCompat.setApplicationLocales sur
     *  ComponentActivity etait flaky -> remplace par compose-driven global. */
    fun setAppLocale(locale: AppLocale) = viewModelScope.launch {
        onboardingRepo.setAppLocale(locale)
    }

    /** Re-run onboarding : clear le flag done + reset les preferences au defaults
     *  (theme=SYSTEM, etc.). Le caller fait ensuite la navigation vers ONBOARDING. */
    fun restartOnboarding(onDone: () -> Unit) {
        val uid = CurrentUserManager.userId
        if (uid == null) { onDone(); return }
        viewModelScope.launch {
            onboardingRepo.resetForUser(uid)
            onDone()
        }
    }
}
