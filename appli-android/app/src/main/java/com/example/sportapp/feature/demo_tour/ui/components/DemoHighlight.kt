package com.example.sportapp.feature.demo_tour.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sportapp.designsystem.theme.orangeMedium

/**
 * CompositionLocal qui transporte le `targetKey` du step actif du tour visuel,
 * ou null si pas de tour en cours. Fourni par MainActivity (au-dessus du NavHost),
 * lu par `Modifier.demoHighlight()` pour activer la bordure orange sur les
 * composables instrumentés.
 */
val LocalDemoTourActiveTarget = compositionLocalOf<String?> { null }

/**
 * Applique une bordure orange pulsée (0.5dp ↔ 2dp, 800ms) AUTOUR du composable
 * si et seulement si `targetKey == LocalDemoTourActiveTarget.current`. Sinon
 * no-op (retourne `this` inchangé).
 *
 * La bordure est dessinée à `expand` dp à l'EXTÉRIEUR du composable via
 * `drawWithContent` : pas de modification du layout interne, pas d'écrasement
 * du contenu, le cadre déborde simplement vers l'extérieur. Si le parent
 * `clipToBounds=true`, le débordement peut être tronqué -- mais en pratique
 * les Box/Column Compose ne clip pas par défaut.
 *
 * À utiliser comme : `Modifier.fillMaxWidth().demoHighlight("stats.chart")`.
 */
@Composable
fun Modifier.demoHighlight(
    targetKey: String,
    expand: Dp = 6.dp,
    cornerRadius: Dp = 2.dp,
): Modifier = composed {
    val active = LocalDemoTourActiveTarget.current
    if (active != targetKey) return@composed this
    val transition = rememberInfiniteTransition(label = "demoHighlight_$targetKey")
    val borderWidth by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "demoBorderWidth_$targetKey",
    )
    this.drawWithContent {
        drawContent()
        val expandPx = expand.toPx()
        val radiusPx = (cornerRadius + expand).toPx()
        drawRoundRect(
            color = orangeMedium,
            topLeft = Offset(-expandPx, -expandPx),
            size = Size(size.width + 2 * expandPx, size.height + 2 * expandPx),
            cornerRadius = CornerRadius(radiusPx, radiusPx),
            style = Stroke(width = borderWidth.dp.toPx()),
        )
    }
}
