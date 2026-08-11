package com.example.sportapp.feature.nutrition.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.core.data.local.FoodDao
import com.example.sportapp.core.data.local.FoodPortionDao
import com.example.sportapp.core.data.local.MealEntryDao
import com.example.sportapp.core.data.model.Food
import com.example.sportapp.core.data.model.FoodPortion
import com.example.sportapp.core.network.CurrentUserManager
import com.example.sportapp.core.network.OffProduct
import com.example.sportapp.core.network.RetrofitInstance
import com.example.sportapp.core.sync.SyncEngine
import com.example.sportapp.feature.nutrition.domain.FoodGroupBlock
import com.example.sportapp.feature.nutrition.domain.FoodSource
import com.example.sportapp.feature.nutrition.domain.NutrientThreshold
import com.example.sportapp.feature.nutrition.domain.buildCatalogue
import com.example.sportapp.feature.nutrition.domain.detectWaterFromOffCategories
import com.example.sportapp.feature.nutrition.domain.recentFoodUuids
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel du Catalogue d'aliments (A3). Source de vérité = Room (réactif) →
 * la liste filtrée se recalcule en live. Recherche plein-texte (nom + marque) +
 * filtres multi-critères par seuil (combinables), CRUD d'aliments custom +
 * archivage + favoris + portions nommées, et import Open Food Facts (proxy
 * serveur). Écrit via les DAOs Style A puis pousse via `SyncEngine`.
 *
 * Soft-delete : suppressions via `markAsPendingDeletion` (sync convergente) ;
 * tous les flows filtrent `!pendingDeletion`. Archivage = flag `archived` (l'item
 * survit, l'historique snapshotté aussi) — distinct de la suppression.
 */
@HiltViewModel
class FoodCatalogueViewModel @Inject constructor(
    private val foodDao: FoodDao,
    private val foodPortionDao: FoodPortionDao,
    mealEntryDao: MealEntryDao,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val started = SharingStarted.WhileSubscribed(5_000)
    private val offApi = RetrofitInstance.nutritionOffApi

    // ─── Filtres (recherche + seuils + archivés) ─────────────────────────────
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _thresholds = MutableStateFlow<List<NutrientThreshold>>(emptyList())
    val thresholds: StateFlow<List<NutrientThreshold>> = _thresholds

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived

    // ─── Sources Room ─────────────────────────────────────────────────────────
    private val foods = foodDao.observeAll().map { list -> list.filter { !it.pendingDeletion } }

    /** Portions nommées groupées par foodUUID (l'éditeur filtre par aliment). */
    val portionsByFood: StateFlow<Map<String, List<FoodPortion>>> =
        foodPortionDao.observeAll()
            .map { list -> list.filter { !it.pendingDeletion }.groupBy { it.foodUUID } }
            .stateIn(viewModelScope, started, emptyMap())

    /** uuids des aliments récemment consommés (dérivés des entries, miroir web). */
    private val recentUuids: StateFlow<List<String>> =
        mealEntryDao.observeAll()
            .map { list -> recentFoodUuids(list.filter { !it.pendingDeletion }) }
            .stateIn(viewModelScope, started, emptyList())

    /** Blocs de la liste catalogue (Récents / Favoris / Tous / Archivés, ou liste à plat filtrée). */
    val catalogue: StateFlow<List<FoodGroupBlock>> =
        combine(foods, _query, _thresholds, _showArchived, recentUuids) { list, q, th, arch, recents ->
            buildCatalogue(list, q, th, arch, recents)
        }.stateIn(viewModelScope, started, emptyList())

    /** Aliment unique observé par uuid, indépendant des filtres (null si absent/supprimé). */
    fun foodFlow(uuid: String): StateFlow<Food?> =
        foods.map { list -> list.find { it.uuid == uuid } }
            .stateIn(viewModelScope, started, null)

    // ─── Open Food Facts (recherche + import) ─────────────────────────────────
    private val _offResults = MutableStateFlow<List<OffProduct>>(emptyList())
    val offResults: StateFlow<List<OffProduct>> = _offResults

    private val _offLoading = MutableStateFlow(false)
    val offLoading: StateFlow<Boolean> = _offLoading

    /** null = aucune erreur ; sinon clé de message d'erreur (NETWORK / EMPTY). */
    private val _offError = MutableStateFlow<OffSearchError?>(null)
    val offError: StateFlow<OffSearchError?> = _offError

    // ─── Filtres ──────────────────────────────────────────────────────────────
    fun setQuery(value: String) { _query.value = value }

    fun addThreshold(threshold: NutrientThreshold) {
        // Un seul seuil par (nutriment, opérateur) : un nouveau remplace l'ancien.
        _thresholds.value = _thresholds.value.filterNot {
            it.key == threshold.key && it.op == threshold.op
        } + threshold
    }

    fun removeThreshold(threshold: NutrientThreshold) {
        _thresholds.value = _thresholds.value - threshold
    }

    fun clearThresholds() { _thresholds.value = emptyList() }

    fun toggleArchived() { _showArchived.value = !_showArchived.value }

    // ─── CRUD aliments custom ──────────────────────────────────────────────────

    /** Crée un aliment CUSTOM (push best-effort). [onDone] reçoit l'aliment créé (ex. picker → onPick). */
    fun createFood(
        name: String,
        brand: String?,
        kcalPer100g: Float,
        proteinPer100g: Float,
        carbsPer100g: Float,
        fatPer100g: Float,
        fiberPer100g: Float?,
        isWater: Boolean = false,
        onDone: ((Food) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            val food = newFood(
                name = name.trim(),
                brand = brand?.trim()?.ifBlank { null },
                source = FoodSource.CUSTOM,
                sourceRef = null,
                kcalPer100g = kcalPer100g,
                proteinPer100g = proteinPer100g,
                carbsPer100g = carbsPer100g,
                fatPer100g = fatPer100g,
                fiberPer100g = fiberPer100g,
                isWater = isWater,
            )
            foodDao.insert(food)
            onDone?.invoke(food)
            pushFoods()
        }
    }

    /**
     * Met à jour un aliment. Les valeurs nutritionnelles ne sont éditables que pour
     * les aliments CUSTOM (un OFF/CIQUAL garde ses valeurs source) ; le flag
     * `isWater` (classification utilisateur) est éditable sur tout aliment — sert
     * aussi au backfill des boissons eau importées avant la feature Hydratation.
     */
    fun updateFood(
        food: Food,
        name: String,
        brand: String?,
        kcalPer100g: Float,
        proteinPer100g: Float,
        carbsPer100g: Float,
        fatPer100g: Float,
        fiberPer100g: Float?,
        isWater: Boolean,
    ) {
        viewModelScope.launch {
            val updated = if (food.source == FoodSource.CUSTOM) {
                food.copy(
                    name = name.trim(),
                    brand = brand?.trim()?.ifBlank { null },
                    kcalPer100g = kcalPer100g,
                    proteinPer100g = proteinPer100g,
                    carbsPer100g = carbsPer100g,
                    fatPer100g = fatPer100g,
                    fiberPer100g = fiberPer100g,
                    isWater = isWater,
                )
            } else {
                food.copy(isWater = isWater)
            }
            foodDao.update(updated)
            pushFoods()
        }
    }

    fun toggleFavorite(food: Food) {
        viewModelScope.launch {
            foodDao.update(food.copy(isFavorite = !food.isFavorite))
            pushFoods()
        }
    }

    /** Archive / désarchive (masquer sans supprimer ; l'historique survit). */
    fun setArchived(food: Food, archived: Boolean) {
        viewModelScope.launch {
            foodDao.update(food.copy(archived = archived))
            pushFoods()
        }
    }

    /** Supprime un aliment (soft-delete + cascade portions). */
    fun deleteFood(food: Food) {
        viewModelScope.launch {
            (portionsByFood.value[food.uuid] ?: emptyList()).forEach {
                foodPortionDao.markAsPendingDeletion(it.uuid)
            }
            foodDao.markAsPendingDeletion(food.uuid)
            pushFoods()
        }
    }

    // ─── Portions nommées ──────────────────────────────────────────────────────
    fun addPortion(foodUuid: String, label: String, grams: Float) {
        viewModelScope.launch {
            foodPortionDao.insert(
                FoodPortion(
                    uuid = UUID.randomUUID().toString(),
                    foodUUID = foodUuid,
                    label = label.trim(),
                    grams = grams,
                )
            )
            syncEngine.pushEntityClass(FoodPortion::class)
        }
    }

    fun deletePortion(portion: FoodPortion) {
        viewModelScope.launch {
            foodPortionDao.markAsPendingDeletion(portion.uuid)
            syncEngine.pushEntityClass(FoodPortion::class)
        }
    }

    // ─── Open Food Facts ───────────────────────────────────────────────────────

    /** Recherche OFF (proxy serveur). Vide la liste + pose loading/erreur. */
    fun searchOff(text: String) {
        val q = text.trim()
        if (q.length < 2) {
            _offResults.value = emptyList()
            _offError.value = null
            return
        }
        viewModelScope.launch {
            _offLoading.value = true
            _offError.value = null
            try {
                val results = offApi.search(q)
                _offResults.value = results
                _offError.value = if (results.isEmpty()) OffSearchError.EMPTY else null
            } catch (e: Exception) {
                Log.w("FoodCatalogue", "OFF search failed", e)
                _offResults.value = emptyList()
                _offError.value = OffSearchError.NETWORK
            } finally {
                _offLoading.value = false
            }
        }
    }

    fun clearOff() {
        _offResults.value = emptyList()
        _offError.value = null
        _offLoading.value = false
    }

    /**
     * Résout un code-barres scanné via le proxy OFF puis l'importe dans le
     * catalogue (dédup par sourceRef). [onFound] reçoit l'aliment importé (ou déjà
     * présent), [onNotFound] = 404 (produit inconnu d'OFF), [onError] = erreur
     * réseau/serveur. Enchaîne sur [importOff] → même chemin d'import que l'onglet
     * OFF (dédup automatique, un re-scan ne crée pas de doublon).
     */
    fun lookupBarcode(
        barcode: String,
        onFound: (Food) -> Unit,
        onNotFound: () -> Unit,
        onError: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val product = offApi.product(barcode.trim())
                importOff(product, onFound)
            } catch (e: HttpException) {
                if (e.code() == 404) onNotFound() else onError()
            } catch (e: Exception) {
                Log.w("FoodCatalogue", "barcode lookup failed", e)
                onError()
            }
        }
    }

    /**
     * Importe un produit OFF dans le catalogue local (source=OFF, dédup par
     * sourceRef) + crée la portion `serving_size` si disponible. Idempotent : un
     * re-import du même code-barres réutilise le Food existant. [onDone] reçoit
     * l'aliment local (importé ou existant) — ex. picker → onPick.
     */
    fun importOff(product: OffProduct, onDone: ((Food) -> Unit)? = null) {
        viewModelScope.launch {
            val existing = foodDao.getAllOnce().firstOrNull {
                it.source == FoodSource.OFF && it.sourceRef == product.sourceRef && !it.pendingDeletion
            }
            if (existing != null) {
                // Re-scan : rafraîchir is_water depuis les categoriesTags frais du lookup
                // OFF (l'aliment a pu être importé avant la feature Hydratation → is_water
                // stale à false). On ne fait que PROMOUVOIR à true (jamais démarquer) pour
                // ne pas écraser un marquage manuel. La MAJ pousse en sync et fait recompter
                // rétroactivement les entrées repas existantes (total card réactif sur is_water).
                val refreshed = if (!existing.isWater && detectWaterFromOffCategories(product.categoriesTags)) {
                    existing.copy(isWater = true).also {
                        foodDao.update(it)
                        pushFoods()
                    }
                } else existing
                onDone?.invoke(refreshed)
                return@launch
            }

            val foodUuid = UUID.randomUUID().toString()
            val food = newFood(
                uuid = foodUuid,
                name = product.name,
                brand = product.brand,
                source = FoodSource.OFF,
                sourceRef = product.sourceRef,
                kcalPer100g = product.kcalPer100g,
                proteinPer100g = product.proteinPer100g,
                carbsPer100g = product.carbsPer100g,
                fatPer100g = product.fatPer100g,
                fiberPer100g = product.fiberPer100g,
                sugarPer100g = product.sugarPer100g,
                satFatPer100g = product.satFatPer100g,
                saltPer100g = product.saltPer100g,
                ironPer100g = product.ironPer100g,
                calciumPer100g = product.calciumPer100g,
                magnesiumPer100g = product.magnesiumPer100g,
                zincPer100g = product.zincPer100g,
                potassiumPer100g = product.potassiumPer100g,
                sodiumPer100g = product.sodiumPer100g,
                vitaminCPer100g = product.vitaminCPer100g,
                vitaminDPer100g = product.vitaminDPer100g,
                vitaminB12Per100g = product.vitaminB12Per100g,
                vitaminAPer100g = product.vitaminAPer100g,
                isWater = detectWaterFromOffCategories(product.categoriesTags),
            )
            foodDao.insert(food)
            // Le chip de portion réaffiche déjà « (X g) » → on nettoie le
            // serving_size OFF pour éviter le doublon « 1 portion (30 g) (30 g) »
            // (feedback Functional review 2026-07-05).
            val label = cleanServingLabel(product.servingSize)
            val grams = product.servingQuantityG
            if (!label.isNullOrBlank() && grams != null && grams > 0f) {
                foodPortionDao.insert(
                    FoodPortion(
                        uuid = UUID.randomUUID().toString(),
                        foodUUID = foodUuid,
                        label = label,
                        grams = grams,
                    )
                )
            }
            onDone?.invoke(food)
            pushFoods()
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────
    private fun newFood(
        uuid: String = UUID.randomUUID().toString(),
        name: String,
        brand: String?,
        source: String,
        sourceRef: String?,
        kcalPer100g: Float,
        proteinPer100g: Float,
        carbsPer100g: Float,
        fatPer100g: Float,
        fiberPer100g: Float? = null,
        sugarPer100g: Float? = null,
        satFatPer100g: Float? = null,
        saltPer100g: Float? = null,
        ironPer100g: Float? = null,
        calciumPer100g: Float? = null,
        magnesiumPer100g: Float? = null,
        zincPer100g: Float? = null,
        potassiumPer100g: Float? = null,
        sodiumPer100g: Float? = null,
        vitaminCPer100g: Float? = null,
        vitaminDPer100g: Float? = null,
        vitaminB12Per100g: Float? = null,
        vitaminAPer100g: Float? = null,
        isWater: Boolean = false,
    ) = Food(
        uuid = uuid,
        userId = CurrentUserManager.userId ?: 0,
        name = name,
        brand = brand,
        source = source,
        sourceRef = sourceRef,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        fiberPer100g = fiberPer100g,
        sugarPer100g = sugarPer100g,
        satFatPer100g = satFatPer100g,
        saltPer100g = saltPer100g,
        ironPer100g = ironPer100g,
        calciumPer100g = calciumPer100g,
        magnesiumPer100g = magnesiumPer100g,
        zincPer100g = zincPer100g,
        potassiumPer100g = potassiumPer100g,
        sodiumPer100g = sodiumPer100g,
        vitaminCPer100g = vitaminCPer100g,
        vitaminDPer100g = vitaminDPer100g,
        vitaminB12Per100g = vitaminB12Per100g,
        vitaminAPer100g = vitaminAPer100g,
        isWater = isWater,
    )

    private suspend fun pushFoods() {
        syncEngine.pushEntityClasses(Food::class, FoodPortion::class)
    }
}

/** Erreur de recherche OFF (résolue en message via strings.xml côté UI, politique 18). */
enum class OffSearchError { NETWORK, EMPTY }

private val SERVING_AMOUNT_PAREN = Regex("""\s*\(\s*\d+(?:[.,]\d+)?\s*(?:g|ml)\s*\)\s*$""", RegexOption.IGNORE_CASE)
private val SERVING_BARE_AMOUNT = Regex("""^\d+(?:[.,]\d+)?\s*(?:g|ml)$""", RegexOption.IGNORE_CASE)

/**
 * Nettoie un `serving_size` OFF pour en faire un **nom** de portion, le chip de
 * quantité réaffichant déjà le grammage « (X g) ». Retire un grammage/volume
 * redondant en fin (« 1 portion (30 g) » → « 1 portion ») et renvoie `null` si
 * le libellé n'est qu'un grammage brut (« 30 g », « 330 ml ») ou vide — dans ce
 * cas aucune portion nommée n'est créée (le chip serait redondant).
 */
internal fun cleanServingLabel(servingSize: String?): String? {
    val raw = servingSize?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val cleaned = raw.replace(SERVING_AMOUNT_PAREN, "").trim()
    return if (cleaned.isBlank() || cleaned.matches(SERVING_BARE_AMOUNT)) null else cleaned
}
