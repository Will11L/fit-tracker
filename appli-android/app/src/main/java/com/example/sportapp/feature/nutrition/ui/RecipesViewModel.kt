package com.example.sportapp.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.local.MealDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.local.MealPresetDao
import com.example.sportapp.core.data.local.RecipeDao
import com.example.sportapp.core.data.local.RecipeIngredientDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.Meal
import com.example.sportapp.core.data.model.MealEntry
import com.example.sportapp.core.data.model.Recipe
import com.example.sportapp.core.data.model.RecipeIngredient
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.core.utils.CustomDateUtils.getTodayIsoDay
import com.example.sportapp.feature.nutrition.domain.JournalSection
import com.example.sportapp.feature.nutrition.domain.MicroKey
import com.example.sportapp.feature.nutrition.domain.RecipeMacros
import com.example.sportapp.feature.nutrition.domain.RecipesByKind
import com.example.sportapp.feature.nutrition.domain.buildSections
import com.example.sportapp.feature.nutrition.domain.recipeMacros
import com.example.sportapp.feature.nutrition.domain.splitRecipesByKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Une recette préparée pour la liste : la recette + son nombre d'ingrédients + ses macros. */
data class RecipeRow(
    val recipe: Recipe,
    val ingredientCount: Int,
    val macros: RecipeMacros,
)

/** Un ingrédient choisi dans l'éditeur (food + quantité en g), à persister via setIngredients. */
data class DraftItem(val foodUUID: String, val quantityG: Float)

/**
 * ViewModel des Recettes & repas enregistrés (A4). Source de vérité = Room (réactif)
 * → la liste + les macros se recalculent en live à chaque écriture. Écrit via les
 * DAOs Style A (pattern identique aux autres VMs nutrition) puis pousse via
 * `SyncEngine.pushEntityClass(...)`.
 *
 * Deux `kind` (politique 11) :
 *   - RECIPE     : ajouté au journal comme un aliment, macros AU PRORATA du poids
 *     consommé (snapshot per-100 g du plat).
 *   - SAVED_MEAL : ses ingrédients sont insérés tels quels dans une période (un tap),
 *     chacun snapshoté depuis son Food vivant (D5).
 *
 * Soft-delete : les suppressions passent par `markAsPendingDeletion` (sync
 * convergente) ; tous les flows filtrent `!pendingDeletion`.
 */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    private val recipeIngredientDao: RecipeIngredientDao,
    private val foodDao: FoodDao,
    private val mealDao: MealDao,
    private val mealEntryDao: MealEntryDao,
    private val mealPresetDao: MealPresetDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)

    val today: String = getTodayIsoDay()

    // ─── Sources Room (filtrées !pendingDeletion) ────────────────────────────
    private val recipesFlow = recipeDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val ingredientsFlow = recipeIngredientDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val foodsFlow = foodDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val mealsFlow = mealDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val entriesFlow = mealEntryDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }
    private val presetsFlow = mealPresetDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    /** Aliments par uuid : résolution des ingrédients (nom courant + macros) — référence vivante. */
    val foodsByUuid: StateFlow<Map<String, Food>> =
        foodsFlow.map { list -> list.associateBy { it.uuid } }
            .stateIn(viewModelScope, started, emptyMap())

    /** Ingrédients groupés par recette, triés par orderIndex. */
    val ingredientsByRecipe: StateFlow<Map<String, List<RecipeIngredient>>> =
        ingredientsFlow.map { list ->
            list.groupBy { it.recipeUUID }.mapValues { (_, v) -> v.sortedBy { it.orderIndex } }
        }.stateIn(viewModelScope, started, emptyMap())

    /** Catalogue non archivé, pour le picker d'ingrédients de l'éditeur. */
    val foods: StateFlow<List<Food>> =
        foodsFlow.map { list -> list.filter { !it.archived }.sortedBy { it.name.lowercase() } }
            .stateIn(viewModelScope, started, emptyList())

    /** Lignes de la liste (recette + nb ingrédients + macros), scindées par kind. */
    val rows: StateFlow<RecipesByKind<RecipeRow>> =
        combine(recipesFlow, ingredientsByRecipe, foodsByUuid) { recipes, ingByRecipe, foodsMap ->
            val mapped = recipes.map { recipe ->
                val ings = ingByRecipe[recipe.uuid] ?: emptyList()
                RecipeRow(recipe, ings.size, recipeMacros(recipe, ings, foodsMap))
            }
            splitRecipesByKind(mapped) { it.recipe.kind }
        }.stateIn(viewModelScope, started, RecipesByKind(emptyList(), emptyList()))

    /** Sections du jour courant (presets + repas ad hoc), mêmes règles que le journal. */
    val todaySections: StateFlow<List<JournalSection>> =
        combine(presetsFlow, mealsFlow, entriesFlow) { presets, meals, entries ->
            val dayMeals = meals.filter { it.date == today }.sortedBy { it.orderIndex }
            buildSections(presets, dayMeals, entries)
        }.stateIn(viewModelScope, started, emptyList())

    // ─── Création / édition ───────────────────────────────────────────────────

    /**
     * Crée ou met à jour une recette puis (re)pose ses ingrédients. [totalWeightG]
     * n'est conservé que pour kind=RECIPE (le caller passe null pour SAVED_MEAL).
     */
    fun saveRecipe(
        editUuid: String?,
        name: String,
        kind: String,
        totalWeightG: Float?,
        items: List<DraftItem>,
    ) {
        viewModelScope.launch {
            val uuid = if (editUuid != null) {
                recipeDao.getByUUID(editUuid)?.let { existing ->
                    recipeDao.update(existing.copy(name = name.trim(), kind = kind, totalWeightG = totalWeightG))
                }
                editUuid
            } else {
                val newUuid = UUID.randomUUID().toString()
                recipeDao.insert(
                    Recipe(
                        uuid = newUuid,
                        userId = CurrentUserManager.userId ?: 0,
                        name = name.trim(),
                        kind = kind,
                        totalWeightG = totalWeightG,
                    )
                )
                newUuid
            }
            setIngredients(uuid, items)
            syncEngine.pushEntityClasses(Recipe::class, RecipeIngredient::class)
        }
    }

    /** Supprime une recette (soft-delete ; cascade serveur sur ses ingrédients). */
    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            recipeDao.markAsPendingDeletion(recipe.uuid)
            syncEngine.pushEntityClasses(Recipe::class, RecipeIngredient::class)
        }
    }

    // ─── Ajout au journal ──────────────────────────────────────────────────────

    /** SAVED_MEAL : chaque ingrédient devient une entry snapshotée depuis son Food vivant (D5). */
    fun addSavedMealToJournal(recipe: Recipe, section: JournalSection) {
        viewModelScope.launch {
            val foodsMap = foodsByUuid.value
            // Ne garder que les ingrédients dont le Food existe encore (références vivantes).
            val resolved = (ingredientsByRecipe.value[recipe.uuid] ?: emptyList())
                .filter { foodsMap.containsKey(it.foodUUID) }
            if (resolved.isEmpty()) return@launch
            val mealUuid = ensureMeal(section)
            for (ing in resolved) {
                val food = foodsMap[ing.foodUUID] ?: continue
                addEntryFromFood(mealUuid, food, ing.quantityG)
            }
            pushMeals()
        }
    }

    /** RECIPE : une seule entry au prorata du poids consommé (per-100 g du plat snapshoté). */
    fun addRecipeToJournal(recipe: Recipe, section: JournalSection, quantityG: Float) {
        viewModelScope.launch {
            val ings = ingredientsByRecipe.value[recipe.uuid] ?: emptyList()
            val macros = recipeMacros(recipe, ings, foodsByUuid.value)
            // Recette vide / Foods supprimés → per-100 g nul : pas d'entry 0 kcal ni de Meal vide.
            if (macros.ingredientsWeightG <= 0f) return@launch
            val mealUuid = ensureMeal(section)
            val per = macros.per100g
            val micro = macros.microPer100g
            mealEntryDao.insert(
                MealEntry(
                    uuid = UUID.randomUUID().toString(),
                    mealUUID = mealUuid,
                    foodUUID = null,
                    recipeUUID = recipe.uuid,
                    displayName = recipe.name,
                    quantityG = quantityG,
                    portionLabel = null,
                    kcalPer100g = per.kcal,
                    proteinPer100g = per.protein,
                    carbsPer100g = per.carbs,
                    fatPer100g = per.fat,
                    fiberPer100g = per.fiber,
                    sugarPer100g = null,
                    satFatPer100g = null,
                    saltPer100g = null,
                    ironPer100g = micro[MicroKey.IRON],
                    calciumPer100g = micro[MicroKey.CALCIUM],
                    magnesiumPer100g = micro[MicroKey.MAGNESIUM],
                    zincPer100g = micro[MicroKey.ZINC],
                    potassiumPer100g = micro[MicroKey.POTASSIUM],
                    sodiumPer100g = micro[MicroKey.SODIUM],
                    vitaminCPer100g = micro[MicroKey.VITAMIN_C],
                    vitaminDPer100g = micro[MicroKey.VITAMIN_D],
                    vitaminB12Per100g = micro[MicroKey.VITAMIN_B12],
                    vitaminAPer100g = micro[MicroKey.VITAMIN_A],
                )
            )
            pushMeals()
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Remplace les ingrédients d'une recette (diff vs rows existantes, miroir
     * `RecipeRepository.setIngredients` web) : retirés → pendingDeletion, ajoutés →
     * nouvelles rows, quantités/ordre mis à jour. L'ordre de [items] devient l'orderIndex
     * → c'est ce qui rend le réordonnancement persistant.
     */
    private suspend fun setIngredients(recipeUuid: String, items: List<DraftItem>) {
        val existing = recipeIngredientDao.getAllOnce()
            .filter { it.recipeUUID == recipeUuid && !it.pendingDeletion }
        val wanted = items.mapIndexed { idx, it -> it.foodUUID to Pair(it.quantityG, idx) }.toMap()
        val existingByFood = existing.associateBy { it.foodUUID }

        for (i in existing) {
            val w = wanted[i.foodUUID]
            if (w == null) {
                recipeIngredientDao.markAsPendingDeletion(i.uuid)
            } else if (w.first != i.quantityG || w.second != i.orderIndex) {
                recipeIngredientDao.update(i.copy(quantityG = w.first, orderIndex = w.second))
            }
        }
        for ((foodUUID, w) in wanted) {
            if (!existingByFood.containsKey(foodUUID)) {
                recipeIngredientDao.insert(
                    RecipeIngredient(
                        uuid = UUID.randomUUID().toString(),
                        recipeUUID = recipeUuid,
                        foodUUID = foodUUID,
                        quantityG = w.first,
                        orderIndex = w.second,
                    )
                )
            }
        }
    }

    /** Meal de la section, créé à la 1ʳᵉ entry seulement (§3.4 — pas de rows fantômes). */
    private suspend fun ensureMeal(section: JournalSection): String {
        section.meal?.let { return it.uuid }
        return createMeal(
            date = today,
            name = section.name,
            orderIndex = section.orderIndex,
            presetUuid = section.presetUuid,
        )
    }

    private suspend fun createMeal(date: String, name: String, orderIndex: Int, presetUuid: String?): String {
        val uuid = UUID.randomUUID().toString()
        mealDao.insert(
            Meal(
                uuid = uuid,
                userId = CurrentUserManager.userId ?: 0,
                date = date,
                name = name,
                orderIndex = orderIndex,
                presetUuid = presetUuid,
            )
        )
        return uuid
    }

    /** Ajoute une entry (snapshot D5 de l'aliment vivant) dans un meal existant. */
    private suspend fun addEntryFromFood(mealUuid: String, food: Food, quantityG: Float) {
        mealEntryDao.insert(
            MealEntry(
                uuid = UUID.randomUUID().toString(),
                mealUUID = mealUuid,
                foodUUID = food.uuid,
                recipeUUID = null,
                displayName = food.name,
                quantityG = quantityG,
                portionLabel = null,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                fatPer100g = food.fatPer100g,
                fiberPer100g = food.fiberPer100g,
                sugarPer100g = food.sugarPer100g,
                satFatPer100g = food.satFatPer100g,
                saltPer100g = food.saltPer100g,
                ironPer100g = food.ironPer100g,
                calciumPer100g = food.calciumPer100g,
                magnesiumPer100g = food.magnesiumPer100g,
                zincPer100g = food.zincPer100g,
                potassiumPer100g = food.potassiumPer100g,
                sodiumPer100g = food.sodiumPer100g,
                vitaminCPer100g = food.vitaminCPer100g,
                vitaminDPer100g = food.vitaminDPer100g,
                vitaminB12Per100g = food.vitaminB12Per100g,
                vitaminAPer100g = food.vitaminAPer100g,
            )
        )
    }

    private suspend fun pushMeals() {
        syncEngine.pushEntityClasses(Meal::class, MealEntry::class)
    }
}
