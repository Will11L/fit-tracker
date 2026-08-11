package com.example.sportapp.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.sportapp.R
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ActionIconWithTextButton
import com.example.sportapp.designsystem.common_components.ConfirmationDialog
import com.example.sportapp.designsystem.common_components.CustomTextField
import com.example.sportapp.designsystem.common_components.EmptyListRow
import com.example.sportapp.designsystem.common_components.OptionsBottomSheet
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.SheetAction
import com.example.sportapp.designsystem.common_components.TitledDivider
import com.example.sportapp.designsystem.theme.GrayBlue
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.blueMedium
import com.example.sportapp.designsystem.theme.firstBlue
import com.example.sportapp.designsystem.theme.orangeMedium
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.designsystem.theme.secondBlue
import com.example.sportapp.feature.nutrition.domain.CatalogueGroup
import com.example.sportapp.feature.nutrition.domain.MacroKey
import com.example.sportapp.feature.nutrition.domain.MicroLineItem
import com.example.sportapp.feature.nutrition.domain.effectiveFoodKcal
import com.example.sportapp.feature.nutrition.domain.foodGroupColor
import com.example.sportapp.feature.nutrition.domain.foodGroupLabelRes
import com.example.sportapp.feature.nutrition.domain.isHighSugar
import com.example.sportapp.feature.nutrition.domain.microLineItems
import com.example.sportapp.feature.nutrition.ui.components.FoodEditorSheet
import com.example.sportapp.feature.nutrition.ui.components.FoodFilterSheet
import com.example.sportapp.feature.nutrition.ui.components.OffImportSheet
import kotlin.math.roundToInt

/** Aliment en cours d'édition : null = création, sinon édition de cet aliment. */
private sealed interface EditorTarget {
    data object Create : EditorTarget
    data class Edit(val food: Food) : EditorTarget
}

/**
 * Catalogue d'aliments (Nutrition A3). Recherche plein-texte (nom + marque) +
 * filtres multi-critères par seuil (combinables), CRUD d'aliments custom +
 * archivage + favoris + portions nommées, et import Open Food Facts. Tout est
 * réactif (Room) → la liste se met à jour en live après chaque écriture.
 */
@Composable
fun FoodCatalogueScreen(
    navController: NavController,
    drawerState: DrawerState,
    closeDrawer: () -> Unit,
    viewModel: FoodCatalogueViewModel = hiltViewModel(),
) {
    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    val query by viewModel.query.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val catalogue by viewModel.catalogue.collectAsStateWithLifecycle()
    val portionsByFood by viewModel.portionsByFood.collectAsStateWithLifecycle()
    val offResults by viewModel.offResults.collectAsStateWithLifecycle()
    val offLoading by viewModel.offLoading.collectAsStateWithLifecycle()
    val offError by viewModel.offError.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }
    var showOff by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var foodForOptions by remember { mutableStateOf<Food?>(null) }
    var foodToDelete by remember { mutableStateOf<Food?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(appColors.bgScreen)) {
        ScreenTitleBar(title = stringResource(R.string.nutrition_catalog_title))

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            // Ligne 1 : Archivés · Filtres · Nouveau · Scan (4 boutons à poids égal).
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                // Toggle « Archivés » (demande user 2026-07-15) : OFF = secondaire
                // (transparent + bord GrayBlue), ON = rempli bleu — parité web.
                ActionIconWithTextButton(
                    iconRes = if (showArchived) R.drawable.ic_rounded_folder_eye else R.drawable.ic_rounded_list_alt,
                    text = stringResource(R.string.nutrition_catalog_archived),
                    iconSize = 18.dp,
                    tint = if (showArchived) appColors.textPrimary else GrayBlue,
                    textColor = if (showArchived) appColors.textPrimary else GrayBlue,
                    hasBackground = showArchived,
                    backgroundColor = blueMedium,
                    borderColor = if (showArchived) null else GrayBlue,
                    onClick = viewModel::toggleArchived,
                )
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_rounded_sort,
                    text = if (thresholds.isEmpty()) {
                        stringResource(R.string.nutrition_catalog_filters)
                    } else {
                        stringResource(R.string.nutrition_catalog_filters_count, thresholds.size)
                    },
                    iconSize = 18.dp,
                    // Fond firstBlue (demande user 2026-07-15) ; blueMedium quand filtres actifs.
                    backgroundColor = if (thresholds.isEmpty()) firstBlue else blueMedium,
                    onClick = { showFilters = true },
                )
                ActionIconWithTextButton(
                    iconRes = R.drawable.ic_add,
                    text = stringResource(R.string.nutrition_catalog_new),
                    iconSize = 18.dp,
                    backgroundColor = appColors.primaryAction,
                    onClick = { editorTarget = EditorTarget.Create },
                )
            }
            Spacer(Modifier.height(8.dp))
            // Ligne 2 : recherche par nom ou marque (flex) + bouton Scan à droite.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.nutrition_catalog_search),
                    modifier = Modifier.weight(1f),
                )
                // Scan code-barres : scanner Play services → lookup OFF → import
                // (dédup) ; l'aliment apparaît dans la liste (Room réactif) + toast.
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
                                    onFound = { food ->
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.nutrition_scan_imported, food.name),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    onNotFound = {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.nutrition_scan_not_found),
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
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
        }

        if (catalogue.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                EmptyListRow(
                    text = stringResource(R.string.nutrition_catalog_empty),
                    iconRes = R.drawable.ic_rounded_local_fire,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                catalogue.forEach { block ->
                    block.group?.let { group ->
                        item(key = "header-$group") {
                            // Espacement symétrique au-dessus/en dessous du titre (retour user).
                            Box(Modifier.padding(vertical = 6.dp)) {
                                TitledDivider(title = groupTitle(group))
                            }
                        }
                    }
                    // Cadre de liste CONTINU par section (miroir ListFrame web, demande user
                    // 2026-07-14) : rows à plat sur fond recessed, coins arrondis sur la 1re
                    // et la dernière, filet secondBlue inset entre les rows.
                    itemsIndexed(block.foods, key = { _, f -> f.uuid }) { i, food ->
                        val first = i == 0
                        val last = i == block.foods.lastIndex
                        val shape = RoundedCornerShape(
                            topStart = if (first) 12.dp else 0.dp,
                            topEnd = if (first) 12.dp else 0.dp,
                            bottomStart = if (last) 12.dp else 0.dp,
                            bottomEnd = if (last) 12.dp else 0.dp,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(appColors.bgRecessed),
                        ) {
                            FoodCatalogueRow(
                                food = food,
                                onClick = { foodForOptions = food },
                                flat = true,
                                trailing = {
                                    // Favori actif = fond orange + icône blanche ; inactif = fond neutre.
                                    ActionIconButton(
                                        iconRes = if (food.isFavorite) R.drawable.ic_rounded_star else R.drawable.ic_rounded_empty_star,
                                        tint = if (food.isFavorite) Color.White else appColors.textPrimary,
                                        customBackgroundColor = if (food.isFavorite) orangeMedium else appColors.bgButton,
                                        onClick = { viewModel.toggleFavorite(food) },
                                        iconSize = 18.dp,
                                        boxSize = 36.dp,
                                    )
                                },
                            )
                            if (!last) {
                                HorizontalDivider(
                                    thickness = 1.dp,
                                    color = secondBlue,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // ─── Filtres multi-critères ───────────────────────────────────────────────
    if (showFilters) {
        FoodFilterSheet(
            active = thresholds,
            onAdd = viewModel::addThreshold,
            onRemove = viewModel::removeThreshold,
            onClear = viewModel::clearThresholds,
            onDismiss = { showFilters = false },
        )
    }

    // ─── Import Open Food Facts ───────────────────────────────────────────────
    if (showOff) {
        OffImportSheet(
            results = offResults,
            loading = offLoading,
            error = offError,
            onSearch = viewModel::searchOff,
            onImport = { viewModel.importOff(it) },
            onDismiss = {
                showOff = false
                viewModel.clearOff()
            },
        )
    }

    // ─── Éditeur (création / édition + portions) ──────────────────────────────
    editorTarget?.let { target ->
        val food = (target as? EditorTarget.Edit)?.food
        FoodEditorSheet(
            food = food,
            portions = food?.let { portionsByFood[it.uuid] } ?: emptyList(),
            onSave = { r ->
                if (food == null) {
                    viewModel.createFood(r.name, r.brand, r.kcalPer100g, r.proteinPer100g, r.carbsPer100g, r.fatPer100g, r.fiberPer100g, r.isWater)
                } else {
                    viewModel.updateFood(food, r.name, r.brand, r.kcalPer100g, r.proteinPer100g, r.carbsPer100g, r.fatPer100g, r.fiberPer100g, r.isWater)
                }
                editorTarget = null
            },
            onAddPortion = { label, grams -> food?.let { viewModel.addPortion(it.uuid, label, grams) } },
            onDeletePortion = { viewModel.deletePortion(it) },
            onDismiss = { editorTarget = null },
        )
    }

    // ─── Options d'un aliment ─────────────────────────────────────────────────
    foodForOptions?.let { food ->
        OptionsBottomSheet(
            title = food.name,
            onDismissRequest = { foodForOptions = null },
            actions = listOf(
                SheetAction(
                    label = stringResource(R.string.nutrition_catalog_action_details),
                    iconRes = R.drawable.ic_keyboard_arrow_right,
                    color = appColors.primaryAction,
                    onClick = {
                        foodForOptions = null
                        navController.navigate(Routes.nutritionFoodDetail(food.uuid))
                    },
                ),
                SheetAction(
                    label = stringResource(
                        if (food.isFavorite) R.string.nutrition_catalog_action_unfavorite
                        else R.string.nutrition_catalog_action_favorite
                    ),
                    iconRes = if (food.isFavorite) R.drawable.ic_rounded_empty_star else R.drawable.ic_rounded_star,
                    color = orangeMedium,
                    onClick = {
                        viewModel.toggleFavorite(food)
                        foodForOptions = null
                    },
                ),
                SheetAction(
                    label = stringResource(
                        if (food.archived) R.string.nutrition_catalog_action_unarchive
                        else R.string.nutrition_catalog_action_archive
                    ),
                    iconRes = R.drawable.ic_rounded_folder_eye,
                    color = appColors.textPrimary,
                    onClick = {
                        viewModel.setArchived(food, !food.archived)
                        foodForOptions = null
                    },
                ),
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    iconRes = R.drawable.ic_rounded_delete_forever,
                    color = redMedium,
                    onClick = {
                        foodToDelete = food
                        foodForOptions = null
                    },
                ),
            ),
        )
    }

    // ─── Confirmation de suppression ──────────────────────────────────────────
    foodToDelete?.let { food ->
        ConfirmationDialog(
            title = stringResource(R.string.nutrition_catalog_delete_title),
            message = stringResource(R.string.nutrition_catalog_delete_message, food.name),
            onConfirm = {
                viewModel.deleteFood(food)
                foodToDelete = null
            },
            onDismiss = { foodToDelete = null },
        )
    }
}

/**
 * Row aliment canonique (nom + badge groupe, macros colorées « /100 g », micros
 * dépliables) — partagée entre le catalogue (trailing = bouton favori) et le
 * picker d'aliments du journal (pas de trailing, la row entière sélectionne).
 */
@Composable
internal fun FoodCatalogueRow(
    food: Food,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    // À PLAT (sans fond/coins propres) quand la row vit dans un cadre de liste
    // continu (catalogue, miroir ListFrame web) ; false = card autonome (picker).
    flat: Boolean = false,
) {
    val micros = food.microLineItems()
    var microsExpanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flat) Modifier
                else Modifier.clip(RoundedCornerShape(10.dp)).background(appColors.bgRecessed)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Nom + badge catégorie (le badge s'aligne juste après le nom).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = food.name,
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                food.foodGroup?.takeIf { it.isNotBlank() }?.let { group ->
                    Spacer(Modifier.width(6.dp))
                    FoodGroupBadge(group)
                }
            }
            Spacer(Modifier.height(4.dp))
            // Ligne macros + chevron de déploiement des micros (juste à droite des macros).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = foodMacroLine(food),
                    // 11sp + séparateurs en espaces fines : la ligne complète (marque + 6 valeurs
                    // + /100g) tient en largeur avec les sucres (retour user 2026-07-14).
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Chevron présent aussi quand seuls les sucres sont renseignés (ils vivent
                // dans le dépli depuis le retour user 2026-07-14).
                val hasDetails = micros.isNotEmpty() || food.sugarPer100g != null
                if (hasDetails) {
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (microsExpanded) 180f else 0f,
                        label = "microsChevron",
                    )
                    ActionIconButton(
                        iconRes = R.drawable.ic_keyboard_arrow_down,
                        tint = appColors.primaryAction,
                        hasBackground = false,
                        onClick = { microsExpanded = !microsExpanded },
                        iconSize = 20.dp,
                        boxSize = 26.dp,
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
            }
            if (microsExpanded && (micros.isNotEmpty() || food.sugarPer100g != null)) {
                Spacer(Modifier.height(3.dp))
                Text(text = foodMicroLine(micros, sugarPer100g = food.sugarPer100g), fontSize = 11.sp)
            }
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** Pastille colorée par groupe (mnémotechnique) : teinte `--food-grp-*` en fond léger + texte plein. */
@Composable
internal fun FoodGroupBadge(group: String) {
    val c = foodGroupColor(group)
    Text(
        text = stringResource(foodGroupLabelRes(group)),
        color = c,
        fontSize = 10.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.copy(alpha = 0.20f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Arrondi à 1 décimale, sans « .0 » superflu (façon `round1` web). */
private fun fmt1(v: Float): String {
    val r = (v * 10f).roundToInt() / 10f
    return if (r % 1f == 0f) r.toInt().toString() else r.toString()
}

/** Ligne kcal/G/L/P per-100 g, chaque macro à sa couleur (séparateurs en texte tertiaire). */
@Composable
private fun foodMacroLine(food: Food): AnnotatedString {
    val sep = appColors.textTertiary
    val fiShort = stringResource(R.string.nutrition_short_fiber)
    // Séparateurs en ESPACES FINES (U+2009) : ~5 px gagnés par séparateur.
    // Les SUCRES vivent dans le dépli micros (foodMicroLine) — la ligne débordait
    // encore avec eux (retour user 2026-07-14).
    val dot = " · "
    return buildAnnotatedString {
        food.brand?.takeIf { it.isNotBlank() }?.let {
            withStyle(SpanStyle(color = sep)) { append("$it$dot") }
        }
        withStyle(SpanStyle(color = macroColor(MacroKey.KCAL))) {
            append("${effectiveFoodKcal(food).roundToInt()} kcal")
        }
        withStyle(SpanStyle(color = sep)) { append(dot) }
        withStyle(SpanStyle(color = macroColor(MacroKey.CARBS))) { append("G ${fmt1(food.carbsPer100g)}") }
        withStyle(SpanStyle(color = sep)) { append(dot) }
        withStyle(SpanStyle(color = macroColor(MacroKey.FAT))) { append("L ${fmt1(food.fatPer100g)}") }
        withStyle(SpanStyle(color = sep)) { append(dot) }
        withStyle(SpanStyle(color = macroColor(MacroKey.PROTEIN))) { append("P ${fmt1(food.proteinPer100g)}") }
        food.fiberPer100g?.let {
            withStyle(SpanStyle(color = sep)) { append(dot) }
            withStyle(SpanStyle(color = macroColor(MacroKey.FIBER))) { append("$fiShort ${fmt1(it)}") }
        }
        withStyle(SpanStyle(color = GrayBlue)) { append(" /100g") }
    }
}

/**
 * Ligne micros présents (per-100 g), chacun coloré par sa famille (séparateurs en texte
 * tertiaire). Les SUCRES (déplacés de la ligne macros, retour user 2026-07-14) ouvrent la
 * ligne avec leur teinte dédiée (alerte si > 22,5 g/100 g, repère UK).
 */
@Composable
internal fun foodMicroLine(items: List<MicroLineItem>, sugarPer100g: Float? = null): AnnotatedString {
    val sep = appColors.textTertiary
    val suShort = stringResource(R.string.nutrition_short_sugar)
    val sugarTint = if (isHighSugar(sugarPer100g)) appColors.snackbarWarning else sugarColor
    return buildAnnotatedString {
        sugarPer100g?.let {
            withStyle(SpanStyle(color = sugarTint)) { append("$suShort ${fmt1(it)} g") }
            if (items.isNotEmpty()) withStyle(SpanStyle(color = sep)) { append(" · ") }
        }
        items.forEachIndexed { i, mi ->
            withStyle(SpanStyle(color = microColor(mi.family))) {
                append("${mi.short} ${fmt1(mi.value)} ${mi.unit}")
            }
            if (i < items.lastIndex) withStyle(SpanStyle(color = sep)) { append(" · ") }
        }
    }
}

@Composable
private fun groupTitle(group: CatalogueGroup): String = stringResource(
    when (group) {
        CatalogueGroup.RECENTS -> R.string.nutrition_catalog_group_recents
        CatalogueGroup.FAVORITES -> R.string.nutrition_catalog_group_favorites
        CatalogueGroup.ALL -> R.string.nutrition_catalog_group_all
        CatalogueGroup.ARCHIVED -> R.string.nutrition_catalog_group_archived
    }
)
