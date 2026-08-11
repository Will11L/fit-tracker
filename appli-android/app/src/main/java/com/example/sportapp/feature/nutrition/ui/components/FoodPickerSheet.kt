package com.example.sportapp.feature.nutrition.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sportapp.R
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.ActionTextButton
import com.example.sportapp.designsystem.common_components.AppBottomSheet
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.TabRowCustom
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.feature.nutrition.ui.FoodCatalogueRow
import com.example.sportapp.feature.nutrition.ui.FoodCatalogueViewModel
import com.example.sportapp.feature.nutrition.ui.OffSearchError
import com.example.sportapp.feature.nutrition.ui.launchBarcodeScan

/**
 * Sheet de recherche/ajout d'aliment — 3 onglets (parité web) : **Mon catalogue**
 * (recherche nom + marque), **Open Food Facts** (recherche proxy serveur,
 * sélection = import dans le catalogue puis pick) et **Créer** (aliment perso
 * CUSTOM per-100 g, création puis pick). Les onglets OFF/Créer s'appuient sur
 * [FoodCatalogueViewModel] (mêmes flux et écritures que l'écran Catalogue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(
    foods: List<Food>,
    onPick: (Food) -> Unit,
    onDismiss: () -> Unit,
    catalogueViewModel: FoodCatalogueViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            TitledDivider(title = stringResource(R.string.nutrition_pick_food_title))
            Spacer(Modifier.height(8.dp))
            TabRowCustom(
                items = listOf(
                    stringResource(R.string.nutrition_picker_tab_catalog),
                    stringResource(R.string.nutrition_picker_tab_off),
                    stringResource(R.string.nutrition_picker_tab_create),
                ),
                selectedIndex = tab,
                onTabSelected = { tab = it },
                height = 42.dp,
            )
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> CatalogTab(
                    foods = foods,
                    viewModel = catalogueViewModel,
                    onPick = onPick,
                    onScanNotFound = { tab = 2 }, // fallback produit inconnu → onglet Créer
                )
                1 -> OffTab(viewModel = catalogueViewModel, onPick = onPick)
                else -> CreateTab(viewModel = catalogueViewModel, onPick = onPick)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Onglet 1 : recherche dans le catalogue local (row canonique du catalogue). */
@Composable
private fun CatalogTab(
    foods: List<Food>,
    viewModel: FoodCatalogueViewModel,
    onPick: (Food) -> Unit,
    onScanNotFound: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(foods, query) {
        val q = query.trim()
        if (q.isEmpty()) foods.take(50)
        else foods.filter {
            it.name.contains(q, ignoreCase = true) || (it.brand?.contains(q, ignoreCase = true) == true)
        }.take(80)
    }
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CustomTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.nutrition_pick_food_search),
            )
        }
        // Scan code-barres : scanner Play services → lookup OFF → import + sélection
        // directe (onPick → dialog quantité). Produit inconnu → bascule onglet Créer.
        ActionIconWithTextButton(
            iconRes = R.drawable.ic_rounded_crop_free,
            text = stringResource(R.string.nutrition_catalog_scan),
            iconSize = 18.dp,
            backgroundColor = orangeMedium,
            onClick = {
                launchBarcodeScan(
                    context = context,
                    onResult = { barcode ->
                        viewModel.lookupBarcode(
                            barcode = barcode,
                            onFound = onPick,
                            onNotFound = {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.nutrition_scan_not_found),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                                onScanNotFound()
                            },
                            onError = {
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.nutrition_catalog_off_error),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            },
                        )
                    },
                    onError = {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.nutrition_scan_error),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    if (filtered.isEmpty()) {
        EmptyListRow(text = stringResource(R.string.nutrition_pick_food_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.uuid }) { food ->
                FoodCatalogueRow(food = food, onClick = { onPick(food) })
            }
        }
    }
}

/** Onglet 2 : recherche Open Food Facts — un tap importe le produit puis le sélectionne. */
@Composable
private fun OffTab(viewModel: FoodCatalogueViewModel, onPick: (Food) -> Unit) {
    val results by viewModel.offResults.collectAsStateWithLifecycle()
    val loading by viewModel.offLoading.collectAsStateWithLifecycle()
    val error by viewModel.offError.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

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
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchOff(query) }),
            )
        }
        ActionTextButton(
            text = stringResource(R.string.nutrition_catalog_off_search_button),
            hasBackground = true,
            clickable = query.trim().length >= 2,
            onClick = { viewModel.searchOff(query) },
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
                    imported = false,
                    onImport = { viewModel.importOff(product) { food -> onPick(food) } },
                )
            }
        }
        else -> EmptyListRow(text = stringResource(R.string.nutrition_catalog_off_hint))
    }
}

/** Onglet 3 : création d'un aliment perso (CUSTOM, per-100 g) puis sélection directe. */
@Composable
private fun CreateTab(viewModel: FoodCatalogueViewModel, onPick: (Food) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }

    val pKcal = kcal.parsePickerNum()
    val pProtein = protein.parsePickerNum()
    val pCarbs = carbs.parsePickerNum()
    val pFat = fat.parsePickerNum()
    val valid = name.trim().isNotEmpty() && pKcal != null && pProtein != null && pCarbs != null && pFat != null

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        CustomTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.nutrition_food_name),
            placeholder = stringResource(R.string.nutrition_food_name_hint),
        )
        CustomTextField(
            value = brand,
            onValueChange = { brand = it },
            label = stringResource(R.string.nutrition_food_brand),
            placeholder = stringResource(R.string.nutrition_food_brand_hint),
        )
        Text(
            text = stringResource(R.string.nutrition_food_per_100g),
            color = appColors.textTertiary,
            fontSize = 12.sp,
        )
        CreateNumField(kcal, { kcal = it }, R.string.nutrition_macro_calories)
        CreateNumField(protein, { protein = it }, R.string.nutrition_macro_protein)
        CreateNumField(carbs, { carbs = it }, R.string.nutrition_macro_carbs)
        CreateNumField(fat, { fat = it }, R.string.nutrition_macro_fat)
        if (!valid) {
            Text(
                text = stringResource(R.string.nutrition_picker_create_hint),
                color = appColors.textTertiary,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
            )
        }
        ActionIconWithTextButton(
            iconRes = R.drawable.ic_add,
            text = stringResource(R.string.nutrition_picker_create_add),
            iconSize = 20.dp,
            backgroundColor = appColors.primaryAction,
            clickable = valid,
            onClick = {
                viewModel.createFood(
                    name = name,
                    brand = brand,
                    kcalPer100g = pKcal ?: 0f,
                    proteinPer100g = pProtein ?: 0f,
                    carbsPer100g = pCarbs ?: 0f,
                    fatPer100g = pFat ?: 0f,
                    fiberPer100g = null,
                ) { food -> onPick(food) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CreateNumField(value: String, onChange: (String) -> Unit, labelRes: Int) {
    CustomTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(labelRes),
        placeholder = "0",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** "12,5" ou "12.5" → 12.5f ; null si vide, non numérique ou négatif (miroir `parseMacro` web). */
private fun String.parsePickerNum(): Float? =
    trim().replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }
