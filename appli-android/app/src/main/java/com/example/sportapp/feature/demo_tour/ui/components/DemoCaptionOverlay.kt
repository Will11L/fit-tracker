package com.example.sportapp.feature.demo_tour.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.sportapp.designsystem.common_components.ActionTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.sportapp.R
import com.example.sportapp.feature.demo_tour.ui.DemoTourViewModel
import com.example.sportapp.designsystem.theme.appColors

/**
 * Overlay bottom card affiché par-dessus le NavHost pendant le tour visuel.
 *
 * Rendu conditionnel : visible UNIQUEMENT si `viewModel.currentStep != null`.
 * Style cohérent avec les SectionCard de l'onboarding (appColors.bgRecessed background +
 * appColors.primaryAction title) et avec les overlays existants (MiniChronoOverlay).
 *
 * Bouton "Next" advance le step. À GOODBYE, le label devient "Got it" et le clic
 * déclenche endTour (cleanup sample data). Bouton "Skip tour" termine
 * immédiatement.
 *
 * Pas de scrim sur le fond — l'user voit l'UI réelle derrière (Stats/Calendar/etc.).
 */
@Composable
fun DemoCaptionOverlay(
    viewModel: DemoTourViewModel,
    modifier: Modifier = Modifier,
) {
    val step by viewModel.currentStep.collectAsState()

    // Drag vertical : permet à l'user de tirer la caption vers le haut/bas pour
    // ne pas masquer l'élément expliqué (utile sur Chrono où la caption est
    // au-dessus des boutons d'action). Reset à chaque changement de step.
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(step) { dragOffsetY = 0f }

    AnimatedVisibility(
        visible = step != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        val current = step ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, dragOffsetY.toInt()) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors.bgRecessed)
                    .border(1.dp, appColors.dividerStrong, RoundedCornerShape(12.dp))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            // delta en pixels. Clamp : -1800px max vers le haut
                            // (~600dp à densité 3x, couvre tout l'écran utile),
                            // +200px max vers le bas (un peu de marge pour rebond).
                            dragOffsetY = (dragOffsetY + delta).coerceIn(-1800f, 200f)
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Drag handle visuel (hint UX que la card est draggable verticalement)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(2.dp))
                        .background(appColors.textTertiary.copy(alpha = 0.6f))
                        .width(40.dp)
                        .height(4.dp),
                )

                // Header : title + indicateur step X/N
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(current.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors.primaryAction,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${current.index + 1}/${com.example.sportapp.feature.demo_tour.domain.DemoTourStep.entries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors.textTertiary,
                    )
                }
                Text(
                    text = stringResource(current.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.textTertiary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionTextButton(
                        text = stringResource(R.string.demo_tour_skip),
                        textColor = appColors.textTertiary,
                        hasBackground = false,
                        clickable = true,
                        onClick = { viewModel.skipTour() },
                        modifier = Modifier.fillMaxWidth(0.3f).height(40.dp),
                    )
                    ActionTextButton(
                        text = stringResource(current.nextLabelRes),
                        textColor = appColors.textPrimary,
                        hasBackground = true,
                        backgroundColor = appColors.primaryAction,
                        clickable = true,
                        onClick = { viewModel.nextStep() },
                        modifier = Modifier.fillMaxWidth(0.3f).height(40.dp),
                    )
                }
            }
        }
    }
}
