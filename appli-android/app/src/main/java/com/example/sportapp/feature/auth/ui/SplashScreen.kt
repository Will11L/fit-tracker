package com.example.sportapp.feature.auth.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.designsystem.theme.*
import com.example.sportapp.feature.auth.viewmodel.SplashScreenViewModel
import com.example.sportapp.R

@Composable
fun SplashScreen(navController: NavHostController) {
    val viewModel: SplashScreenViewModel = hiltViewModel()
    val loadingText by viewModel.loadingText
    val progress by viewModel.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "progressAnimation"
    )

    val isFinished by viewModel.isFinished
    val nextRoute by viewModel.nextRoute        // Navigation quand terminé
    val motivationalQuote by viewModel.motivationalQuote   // citation aléatoire (locale, non bloquante)

    LaunchedEffect(isFinished) {
        if (isFinished) {
            navController.navigate(nextRoute) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color= MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Fit Tracker",
                fontSize = 28.sp,
                color = appColors.primaryAction,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            Image(
                painter = painterResource(id = R.drawable.ic_loading_screen),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Loading...",
                fontSize = 14.sp,
                color = firstBlue,
                style = MaterialTheme.typography.bodySmall
            )

            // Citation motivante aléatoire (locale, non bloquante). Affichée
            // seulement si une citation est disponible (fallback propre = rien).
            // Texte user-typed -> non traduit (politique 18).
            motivationalQuote?.let { quote ->
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "\"${quote.text}\"",
                    fontSize = 14.sp,
                    color = appColors.textPrimary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(horizontal = 8.dp)
                )
                quote.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        fontSize = 13.sp,
                        color = appColors.primaryAction,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = appColors.dividerStrong,
                strokeWidth = 6.dp,
                trackColor = appColors.bgBottomNav,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text=loadingText,
                fontSize = 14.sp,
                color = appColors.dividerStrong,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = {
                    animatedProgress
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(6.dp),
                color = appColors.dividerStrong,
                trackColor = appColors.bgBottomNav,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = { }
            )
        }
    }
}
