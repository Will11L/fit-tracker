package com.example.sportapp.feature.nutrition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sportapp.R
import com.example.sportapp.core.network.OffProduct
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.feature.nutrition.domain.isHighSugar
import com.example.sportapp.feature.nutrition.ui.OffSearchError
import com.example.sportapp.feature.nutrition.ui.sugarColor
import kotlin.math.roundToInt

/**
 * Recherche + import Open Food Facts (A3). L'utilisateur cherche un produit
 * (proxy serveur), puis un tap copie le produit dans son catalogue local
 * (source=OFF, dédup par code-barres) → utilisable offline ensuite, y compris
 * dans le Journal. Tout texte vient de strings.xml (politique 18).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OffImportSheet(
    results: List<OffProduct>,
    loading: Boolean,
    error: OffSearchError?,
    onSearch: (String) -> Unit,
    onImport: (OffProduct) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf(setOf<String>()) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TitledDivider(title = stringResource(R.string.nutrition_catalog_off_title))
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CustomTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = stringResource(R.string.nutrition_catalog_off_search),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                    )
                }
                ActionTextButton(
                    text = stringResource(R.string.nutrition_catalog_off_search_button),
                    hasBackground = true,
                    clickable = query.trim().length >= 2,
                    onClick = { onSearch(query) },
                )
            }

            Spacer(Modifier.height(10.dp))

            when {
                loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                ) {
                    CircularProgressIndicator(color = appColors.primaryAction, modifier = Modifier.size(28.dp))
                }
                error == OffSearchError.NETWORK ->
                    EmptyListRow(text = stringResource(R.string.nutrition_catalog_off_error))
                error == OffSearchError.EMPTY ->
                    EmptyListRow(text = stringResource(R.string.nutrition_catalog_off_empty))
                results.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(results, key = { it.sourceRef }) { product ->
                        OffResultRow(
                            product = product,
                            imported = product.sourceRef in imported,
                            onImport = {
                                imported = imported + product.sourceRef
                                onImport(product)
                            },
                        )
                    }
                }
                else ->
                    EmptyListRow(text = stringResource(R.string.nutrition_catalog_off_hint))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Row résultat OFF (nom · marque · kcal + icône import) — partagée avec le FoodPickerSheet. */
@Composable
internal fun OffResultRow(product: OffProduct, imported: Boolean, onImport: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(appColors.bgRecessed)
            .clickable(enabled = !imported, onClick = onImport)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                color = appColors.textPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Sucres = information au choix (comme les rows du catalogue) : valeur colorée si
            // renseignée, alerte si riche (> 22,5 g/100 g, repère UK), rien sinon.
            val suShort = stringResource(R.string.nutrition_short_sugar)
            val sub = buildAnnotatedString {
                product.brand?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                append(stringResource(R.string.nutrition_food_kcal_per_100g, product.kcalPer100g.roundToInt()))
                product.sugarPer100g?.let { sugar ->
                    val tint = if (isHighSugar(sugar)) appColors.snackbarWarning else sugarColor
                    append(" · ")
                    withStyle(SpanStyle(color = tint)) { append("$suShort ${roundTo1(sugar)} g") }
                }
            }
            Text(text = sub, color = appColors.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (imported) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_done),
                    contentDescription = null,
                    tint = mediumGreen,
                    modifier = Modifier.height(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.nutrition_catalog_off_added),
                    color = mediumGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_rounded_cloud_download),
                contentDescription = stringResource(R.string.nutrition_catalog_off_import),
                tint = appColors.primaryAction,
                modifier = Modifier.height(22.dp),
            )
        }
    }
}

/** Arrondi à 1 décimale, sans « .0 » superflu (même rendu que la row du catalogue). */
private fun roundTo1(v: Float): String {
    val r = (v * 10f).roundToInt() / 10f
    return if (r % 1f == 0f) r.toInt().toString() else r.toString()
}
