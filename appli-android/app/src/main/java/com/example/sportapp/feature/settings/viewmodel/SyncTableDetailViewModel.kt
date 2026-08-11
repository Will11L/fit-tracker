package com.example.sportapp.feature.settings.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportapp.app.navigation.Routes
import com.example.sportapp.core.sync.SyncRegistry
import com.example.sportapp.core.sync.base.ColumnDef
import com.example.sportapp.core.sync.base.SortDir
import com.example.sportapp.core.sync.base.SqlQueryBuilder
import com.example.sportapp.core.sync.base.SyncableEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * VM data grid Sync Settings (1 instance par entité ouverte).
 *
 * État unifié [GridState] (search, filtres colonne, tri, pageSize, currentPage)
 * → 2 StateFlows dérivés via SQL @RawQuery :
 *   - [rows] : page courante de lignes (50/100/250)
 *   - [totalCount] : total pour la pagination (ne change que si search/filters bougent)
 *
 * Pagination Excel-style : `setPageSize`, `nextPage`, `prevPage`, `goToPage`.
 * Cf. T-sync-grid (2026-05-26).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SyncTableDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    registry: SyncRegistry,
) : ViewModel() {

    private val entityName: String = requireNotNull(savedStateHandle[Routes.ARG_ENTITY_NAME]) {
        "Missing arg ${Routes.ARG_ENTITY_NAME}"
    }

    val syncable: SyncableEntity<Any> = requireNotNull(registry.findByEntityName(entityName)) {
        "No SyncableEntity for '$entityName' in registry"
    } as SyncableEntity<Any>

    val columns: List<ColumnDef> = syncable.columns
    // Header utilise entityName (PascalCase brut "ActualWorkouts" / "TaskChecks" /...)
    // au lieu de displayName ("Workout") → pas d'i18n nécessaire, cohérent avec
    // le nom dans la liste SyncSettings.
    val displayName: String = syncable.entityName

    data class GridState(
        val search: String = "",
        val columnFilters: Map<String, String> = emptyMap(),
        val sortColumn: String? = null,
        val sortDir: SortDir = SortDir.NONE,
        val pageSize: Int = DEFAULT_PAGE_SIZE,
        val currentPage: Int = 0,
    )

    private val _state = MutableStateFlow(GridState())
    val state: StateFlow<GridState> = _state.asStateFlow()

    // ─── Setters : tout reset currentPage à 0 quand filtres/tri/size changent ──

    fun setSearchQuery(query: String) =
        _state.update { it.copy(search = query, currentPage = 0) }

    fun setColumnFilter(sqlColumn: String, query: String) =
        _state.update {
            val nextFilters = it.columnFilters.toMutableMap().apply {
                if (query.isBlank()) remove(sqlColumn) else put(sqlColumn, query)
            }
            it.copy(columnFilters = nextFilters, currentPage = 0)
        }

    fun clearAllFilters() =
        _state.update { it.copy(search = "", columnFilters = emptyMap(), currentPage = 0) }

    /** Cycle tap sur header : NONE → ASC → DESC → NONE (resette currentPage). */
    fun cycleSort(sqlColumn: String) = _state.update {
        if (it.sortColumn != sqlColumn) {
            it.copy(sortColumn = sqlColumn, sortDir = SortDir.ASC, currentPage = 0)
        } else when (it.sortDir) {
            SortDir.NONE -> it.copy(sortDir = SortDir.ASC, currentPage = 0)
            SortDir.ASC -> it.copy(sortDir = SortDir.DESC, currentPage = 0)
            SortDir.DESC -> it.copy(sortColumn = null, sortDir = SortDir.NONE, currentPage = 0)
        }
    }

    /** Setter direct (popup menu : ASC / DESC / Clear). */
    fun setSort(sqlColumn: String?, dir: SortDir) = _state.update {
        it.copy(
            sortColumn = if (dir == SortDir.NONE) null else sqlColumn,
            sortDir = dir,
            currentPage = 0,
        )
    }

    fun setPageSize(size: Int) = _state.update { it.copy(pageSize = size, currentPage = 0) }
    fun goToPage(page: Int) = _state.update { it.copy(currentPage = page.coerceAtLeast(0)) }
    fun nextPage() = _state.update { it.copy(currentPage = it.currentPage + 1) }
    fun prevPage() = _state.update { it.copy(currentPage = (it.currentPage - 1).coerceAtLeast(0)) }

    // ─── Dérivés SQL ───────────────────────────────────────────────────────────

    /** Lignes de la page courante (LIMIT pageSize OFFSET currentPage*pageSize). */
    val rows: StateFlow<List<Any>> = state
        .mapLatest { s ->
            syncable.selectRowsRaw(
                SqlQueryBuilder.build(
                    tableName = syncable.sqlTableName,
                    columns = columns,
                    globalSearch = s.search,
                    columnFilters = s.columnFilters,
                    sortColumn = s.sortColumn,
                    sortDir = s.sortDir,
                    limit = s.pageSize,
                    offset = s.currentPage * s.pageSize,
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Count total. Ne recompute que si search / columnFilters changent. */
    val totalCount: StateFlow<Int> = state
        .map { it.search to it.columnFilters }
        .distinctUntilChanged()
        .mapLatest { (search, filters) ->
            syncable.selectCountRaw(
                SqlQueryBuilder.buildCount(
                    tableName = syncable.sqlTableName,
                    columns = columns,
                    globalSearch = search,
                    columnFilters = filters,
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        val PAGE_SIZE_OPTIONS = listOf(10, 15, 25, 50, 100, 250)
    }
}
