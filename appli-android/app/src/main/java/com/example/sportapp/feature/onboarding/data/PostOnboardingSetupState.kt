package com.example.sportapp.feature.onboarding.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État global "overlay setup post-onboarding visible". Singleton
 * ApplicationScope : survit à la destruction de l'OnboardingViewModel par
 * la nav vers HOME.
 *
 * Pourquoi : à la fin de l'onboarding, l'OnboardingViewModel est détruit dès
 * le navigate vers HOME. Un overlay local à l'onboarding disparaît donc en
 * même temps que la nav transition (300ms fade), laissant visible le 1er
 * render de HomeScreen pendant que les Flow Room rechargent les data sample
 * insérées. Cet état global, observé par MainActivity au-dessus de la
 * NavHost, garde un overlay visible le temps que tout soit en place (~2s).
 */
@Singleton
class PostOnboardingSetupState @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible.asStateFlow()

    /** Affiche l'overlay et dismiss automatiquement après `durationMs`. */
    fun showFor(durationMs: Long) {
        _isVisible.value = true
        scope.launch {
            delay(durationMs)
            _isVisible.value = false
        }
    }
}
