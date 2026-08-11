package com.example.sportapp.feature.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.feature.onboarding.domain.OnboardingStep
import com.example.sportapp.feature.onboarding.ui.components.OnboardingFooter
import com.example.sportapp.feature.onboarding.ui.components.OnboardingHeader
import com.example.sportapp.feature.onboarding.ui.steps.OnboardingBioScreen
import com.example.sportapp.feature.onboarding.ui.steps.OnboardingPermissionsScreen
import com.example.sportapp.feature.onboarding.ui.steps.OnboardingPreferencesScreen
import com.example.sportapp.feature.onboarding.ui.steps.OnboardingWelcomeScreen
import com.example.sportapp.designsystem.theme.appColors

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val prefs by viewModel.preferencesDraft.collectAsState()

    // Init firstName depuis /me au 1er render. KISS : un appel direct ici plutôt
    // que d'ajouter une route dans le VM. Si le call rate, l'user peut quand
    // même saisir un nouveau nom.
    LaunchedEffect(Unit) {
        try {
            val info = RetrofitInstance.userService.getUserInfo()
            viewModel.setInitialFirstName(info.firstName)
        } catch (_: Exception) {
            // ignore -- user pourra quand même saisir son nom
        }
    }

    // Intercept back system : retour à l'étape précédente plutôt que pop NavHost.
    // Sur l'étape Welcome, le back système n'est pas géré (= quitte l'onboarding).
    val prev = currentStep.previous()
    BackHandler(enabled = prev != null) {
        prev?.let { viewModel.goToStep(it) }
    }

    // NOTE 2026-05-12 : le CompositionLocalProvider live preview est desormais
    // au niveau MainActivity (global -- propage a tout l'app, pas que onboarding).
    // L'override LocalContext + LocalConfiguration y observe le meme
    // OnboardingRepository.preferences flow. Au tap radio Language ici,
    // setAppLocale update le DataStore -> le Provider de MainActivity recompute
    // -> tout l'arbre Compose se met a jour. Plus de CompositionLocalProvider
    // local ici.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader(step = currentStep, title = stringResource(R.string.onboarding_header_title))

            Box(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    OnboardingStep.WELCOME -> OnboardingWelcomeScreen(viewModel)
                    OnboardingStep.BIO -> OnboardingBioScreen(viewModel)
                    OnboardingStep.PREFERENCES -> OnboardingPreferencesScreen(viewModel)
                    OnboardingStep.PERMISSIONS -> OnboardingPermissionsScreen(viewModel)
                }
            }

            OnboardingFooter(
                onBack = prev?.let { p -> { viewModel.goToStep(p) } },
                onSkip = { viewModel.skipOnboarding(onFinish) },
                onNext = { viewModel.confirmAndNext(onFinish) },
                nextLabel = stringResource(
                    if (currentStep == OnboardingStep.PERMISSIONS) R.string.common_finish
                    else R.string.common_next
                ),
            )
        }
        // L'overlay loading "Setting up your workouts..." est rendu globalement
        // par MainActivity via PostOnboardingSetupState (singleton) -- il survit
        // à la destruction de cet OnboardingScreen par la nav vers home.
    }
}
