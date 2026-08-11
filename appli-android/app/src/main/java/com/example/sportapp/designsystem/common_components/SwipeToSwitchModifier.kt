package com.example.sportapp.designsystem.common_components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Fait basculer une valeur de mode au swipe horizontal (gauche = mode suivant,
 * droite = précédent — sémantique pager, borné aux extrémités, pas de wrap).
 * Un seul changement par geste (seuil cumulé 60 dp). `values` doit suivre
 * l'ORDRE VISUEL du [SegmentedIconToggle] associé, pas l'ordre de l'enum.
 * Le détecteur ne verrouille que les drags horizontaux → le scroll vertical
 * du parent passe librement (demande user 2026-07-14, résumé du jour +
 * sections d'analyse des Objectifs nutrition).
 */
fun <T> Modifier.swipeToSwitch(values: List<T>, current: T, onSelect: (T) -> Unit): Modifier =
    pointerInput(values, current) {
        val threshold = 60.dp.toPx()
        var acc = 0f
        var handled = false
        detectHorizontalDragGestures(
            onDragStart = {
                acc = 0f
                handled = false
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                acc += dragAmount
                if (!handled && abs(acc) > threshold) {
                    handled = true
                    val idx = values.indexOf(current)
                    val next = if (acc < 0f) idx + 1 else idx - 1
                    if (idx >= 0 && next in values.indices) onSelect(values[next])
                }
            },
        )
    }

/**
 * Transition animée entre modes d'affichage (compagnon de [swipeToSwitch]) : le
 * contenu du nouveau mode glisse depuis le côté correspondant au sens de
 * navigation (suivant = arrive de droite, précédent = de gauche — sémantique
 * pager), avec fondu + hauteur animée entre contenus de tailles différentes.
 * S'anime aussi au tap sur le [SegmentedIconToggle] (même état).
 */
@Composable
fun <T> AnimatedModeContent(
    current: T,
    values: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            val dir = if (values.indexOf(targetState) > values.indexOf(initialState)) 1 else -1
            (slideInHorizontally(tween(220)) { it * dir } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(220)) { -it * dir } + fadeOut(tween(220)))
                .using(SizeTransform())
        },
        label = "modeContent",
        modifier = modifier,
    ) { mode -> content(mode) }
}
