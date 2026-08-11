package com.example.sportapp.feature.health.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.example.sportapp.R
import com.example.sportapp.designsystem.common_components.FormDialog
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.lightGreen
import com.example.sportapp.designsystem.theme.brightPurple
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.yellowMedium
import java.time.LocalDate
import kotlin.math.roundToInt

/** Catégorie 1..5 d'un score de stress 0..100 (tranches de 20, modèle Samsung). */
fun stressCategory(score: Int): Int = when {
    score <= 20 -> 1
    score <= 40 -> 2
    score <= 60 -> 3
    score <= 80 -> 4
    else -> 5
}

/** Libellé i18n de la catégorie d'un score de stress. */
fun stressCategoryLabelRes(score: Int): Int = when (stressCategory(score)) {
    1 -> R.string.health_dash_stress_l1
    2 -> R.string.health_dash_stress_l2
    3 -> R.string.health_dash_stress_l3
    4 -> R.string.health_dash_stress_l4
    else -> R.string.health_dash_stress_l5
}

/** Couleur de la catégorie d'un score (code couleur vert → rouge, modèle Samsung). */
fun stressCategoryColor(score: Int): Color = when (stressCategory(score)) {
    1 -> mediumGreen
    2 -> lightGreen
    3 -> yellowMedium
    4 -> orangeMedium
    else -> redMedium
}

/**
 * Dialog de saisie du stress : SCORE 0..100 au slider (modèle Samsung — le score
 * tombe dans 1 des 5 catégories, tranches de 20), saisie manuelle (Samsung n'expose
 * pas le stress dans Health Connect). Même mécanique que la pesée : calendrier
 * partagé [HealthEntryCalendar] (accent violet, identité Stress) pour choisir/
 * rétro-dater le jour, 1 valeur/jour écrasable. Score, slider et libellé prennent
 * la couleur de la catégorie courante. [onConfirm] reçoit (score, date ISO).
 */
@Composable
fun StressEntryDialog(
    current: Int?,
    existingByDate: Map<String, Float>,
    onConfirm: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    initialDate: String? = null,
) {
    val today = remember { LocalDate.now() }
    val initial = remember { initialDate?.let(LocalDate::parse) ?: today }
    var score by remember {
        mutableIntStateOf(initialDate?.let(existingByDate::get)?.roundToInt() ?: current ?: 50)
    }
    var date by remember { mutableStateOf(initial) }
    val catColor = stressCategoryColor(score)

    FormDialog(
        title = stringResource(R.string.health_dash_stress_dialog_title),
        confirmText = stringResource(R.string.health_dash_goal_save),
        onConfirm = { onConfirm(score, date.toString()) },
        onDismiss = onDismiss,
        scrollable = true,
    ) {
        HealthEntryCalendar(
            today = today,
            selected = date,
            existingByDate = existingByDate,
            accentColor = brightPurple,
            onSelect = { day ->
                date = day
                // Jour déjà renseigné : pré-remplit son score (écrasable).
                existingByDate[day.toString()]?.let { score = it.roundToInt() }
            },
        )
        // Score hero à la couleur de la catégorie, « /100 » neutre (blanc).
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = catColor)) { append(score.toString()) }
                append("/100")
            },
            color = appColors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Slider(
            value = score.toFloat(),
            onValueChange = { score = it.roundToInt() },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = catColor,
                activeTrackColor = catColor,
                inactiveTrackColor = appColors.bgRecessed,
            ),
        )
        Text(
            text = stringResource(stressCategoryLabelRes(score)),
            color = catColor,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
