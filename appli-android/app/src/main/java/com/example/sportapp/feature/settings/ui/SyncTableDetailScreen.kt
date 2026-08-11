package com.example.sportapp.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.sportapp.R
import com.example.sportapp.core.sync.base.ColumnDef
import com.example.sportapp.core.sync.base.ColumnType
import com.example.sportapp.core.sync.base.SortDir
import com.example.sportapp.designsystem.common_components.ActionIconButton
import com.example.sportapp.designsystem.common_components.ColumnHeaderActionsCard
import com.example.sportapp.designsystem.common_components.DataGridPaginationBar
import com.example.sportapp.designsystem.common_components.ScreenTitleBar
import com.example.sportapp.designsystem.common_components.StyledSearchField
import com.example.sportapp.designsystem.common_components.DialogSecondaryButton
import com.example.sportapp.designsystem.theme.appColors
import com.example.sportapp.designsystem.theme.mediumGreen
import com.example.sportapp.designsystem.theme.redMedium
import com.example.sportapp.feature.settings.viewmodel.SyncTableDetailViewModel

/**
 * Écran data grid Sync Settings (admin/debug).
 *
 * Compose des atoms/molecules existants du design system :
 *   - `StyledSearchField` (E2) pour le filtre global + filtre par colonne (popup)
 *   - `ActionIconButton` (D4) pour les actions sort/filter/pagination
 *   - `ColumnHeaderActionsCard` (M molecule) pour le popup tap-header
 *   - `DataGridPaginationBar` (M molecule) pour la pagination Excel-style
 *
 * Cf. T-sync-grid (2026-05-26).
 */
@Composable
fun SyncTableDetailScreen(
    onBack: () -> Unit,
    viewModel: SyncTableDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()

    val horizScroll = rememberScrollState()
    val listState = rememberLazyListState()

    // Scroll vertical reset en haut à chaque changement de page / filtre / tri.
    LaunchedEffect(
        state.currentPage, state.search, state.columnFilters,
        state.sortColumn, state.sortDir, state.pageSize,
    ) {
        listState.scrollToItem(0)
    }

    // Click sur une cellule data grid → dialog avec valeur complète (utile car
    // les cellules tronquent visuellement les longues valeurs avec ellipsis).
    var cellDetail by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Local TextFieldValue pour StyledSearchField (qui prend du TFV, pas du String).
    // ⚠️ `remember` SANS key sur state.search : le `remember(state.search)` reconstruisait
    // un TextFieldValue à chaque frappe → cursor reset à 0 → les nouvelles lettres
    // s'inséraient à l'envers. On synchronise depuis l'extérieur seulement quand
    // state.search diffère vraiment du texte tapé (ex : Clear filters).
    var searchFieldValue by remember { mutableStateOf(TextFieldValue(state.search)) }
    LaunchedEffect(state.search) {
        if (state.search != searchFieldValue.text) {
            searchFieldValue = TextFieldValue(state.search, TextRange(state.search.length))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bgScreen),
    ) {
        // ─── Header : ScreenTitleBar canonique + back ActionIconButton overlay
        //     gauche + clear filters overlay droite ──────────────────────────────
        Box(modifier = Modifier.fillMaxWidth()) {
            ScreenTitleBar(title = viewModel.displayName)
            ActionIconButton(
                iconRes = R.drawable.ic_rounded_keyboard_arrow_left,
                onClick = onBack,
                hasBackground = false,
                tint = appColors.textPrimary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            )
            val anyFilter = state.search.isNotBlank() ||
                state.columnFilters.isNotEmpty() ||
                state.sortColumn != null
            if (anyFilter) {
                TextButton(
                    onClick = {
                        viewModel.clearAllFilters()
                        viewModel.setSort(null, SortDir.NONE)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text(
                        stringResource(R.string.sync_table_clear),
                        color = appColors.primaryAction,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Filtre global (StyledSearchField — E2) ───────────────────────────
        StyledSearchField(
            value = searchFieldValue,
            onValueChange = {
                searchFieldValue = it
                viewModel.setSearchQuery(it.text)
            },
            placeholderText = stringResource(R.string.sync_table_search_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ─── Chips filtres par colonne actifs ─────────────────────────────────
        if (state.columnFilters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.columnFilters.forEach { (col, query) ->
                    AssistChip(
                        onClick = { viewModel.setColumnFilter(col, "") },
                        label = { Text("$col: $query  ✕", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = appColors.bgSurface,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Ligne header colonnes ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.bgSurface)
                .padding(horizontal = 16.dp)
                .horizontalScroll(horizScroll),
        ) {
            viewModel.columns.forEach { col ->
                HeaderCell(
                    col = col,
                    isSortedCol = state.sortColumn == col.sqlColumn,
                    sortDir = if (state.sortColumn == col.sqlColumn) state.sortDir else SortDir.NONE,
                    activeFilter = state.columnFilters[col.sqlColumn].orEmpty(),
                    onSetSort = { dir -> viewModel.setSort(col.sqlColumn, dir) },
                    onFilterChange = { viewModel.setColumnFilter(col.sqlColumn, it) },
                    filterPlaceholder = stringResource(R.string.sync_table_filter_hint),
                )
            }
        }

        // ─── Body : LazyColumn de la page courante ────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.sync_table_no_rows),
                        color = appColors.textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(
                        items = rows,
                        key = { row -> viewModel.syncable.keyOf(row) },
                    ) { row ->
                        DataRow(
                            item = row,
                            columns = viewModel.columns,
                            horizScroll = horizScroll,
                            zebra = rows.indexOf(row) % 2 == 1,
                            onCellClick = { col, value -> cellDetail = col to value },
                        )
                    }
                }
            }
        }

        // ─── Dialog valeur complète d'une cellule (sur tap) ───────────────────
        cellDetail?.let { (colName, value) ->
            AlertDialog(
                onDismissRequest = { cellDetail = null },
                containerColor = appColors.bgScreen,
                title = {
                    Text(
                        text = colName,
                        color = appColors.primaryAction,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                text = {
                    Text(
                        text = value,
                        color = appColors.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    DialogSecondaryButton(text = stringResource(R.string.common_ok), onClick = { cellDetail = null })
                },
            )
        }

        // ─── Pagination bar (molecule sticky bottom) ──────────────────────────
        DataGridPaginationBar(
            totalCount = totalCount,
            pageSize = state.pageSize,
            currentPage = state.currentPage,
            pageSizeOptions = SyncTableDetailViewModel.PAGE_SIZE_OPTIONS,
            onPrev = { viewModel.prevPage() },
            onNext = { viewModel.nextPage() },
            onPageSizeChange = { viewModel.setPageSize(it) },
        )
    }
}

@Composable
private fun HeaderCell(
    col: ColumnDef,
    isSortedCol: Boolean,
    sortDir: SortDir,
    activeFilter: String,
    onSetSort: (SortDir) -> Unit,
    onFilterChange: (String) -> Unit,
    filterPlaceholder: String,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasFilter = activeFilter.isNotBlank()
    val tint = if (hasFilter || isSortedCol) appColors.primaryAction else appColors.textPrimary

    Box(
        modifier = Modifier
            .width(col.widthDp.dp)
            .height(40.dp)
            .clickable { menuExpanded = true }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = col.sqlColumn,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isSortedCol && sortDir != SortDir.NONE) {
                Text(
                    text = if (sortDir == SortDir.ASC) " ↑" else " ↓",
                    fontSize = 11.sp,
                    color = appColors.primaryAction,
                )
            }
            if (hasFilter) {
                Text(text = " ⚙", fontSize = 11.sp, color = appColors.primaryAction)
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            // Match card bg + zero elev → plus de padding gris top/bottom autour
            // du ColumnHeaderActionsCard (T-sync-grid 2026-05-27).
            containerColor = appColors.bgScreen,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            // Décalage vers le bas pour ne pas chevaucher la ligne des headers.
            offset = DpOffset(x = 0.dp, y = 8.dp),
        ) {
            ColumnHeaderActionsCard(
                columnName = col.sqlColumn,
                sortDir = sortDir,
                filterValue = activeFilter,
                onSetSort = onSetSort,
                onFilterChange = onFilterChange,
                filterPlaceholder = filterPlaceholder,
            )
        }
    }
}

@Composable
private fun DataRow(
    item: Any,
    columns: List<ColumnDef>,
    horizScroll: ScrollState,
    zebra: Boolean,
    onCellClick: (col: String, value: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (zebra) appColors.bgSurface.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 16.dp)
            .horizontalScroll(horizScroll)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { col ->
            DataCell(item, col, onCellClick)
        }
    }
}

@Composable
private fun DataCell(
    item: Any,
    col: ColumnDef,
    onCellClick: (col: String, value: String) -> Unit,
) {
    val raw: Any? = runCatching {
        val f = item::class.java.getDeclaredField(col.fieldName)
        f.isAccessible = true
        f.get(item)
    }.getOrNull()

    val fullValue = raw?.toString() ?: "null"
    val onClick = { onCellClick(col.sqlColumn, fullValue) }

    // BOOL : icône check_circle vert si true (rouge pour pendingDeletion), "-" primaryAction sinon.
    if (col.type == ColumnType.BOOL) {
        val isPendingDeletionCol = col.sqlColumn == "pendingDeletion"
        Box(
            modifier = Modifier
                .width(col.widthDp.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                raw == null -> Text("—", color = appColors.textTertiary, fontSize = 11.sp)
                raw == true -> Icon(
                    painter = painterResource(
                        if (isPendingDeletionCol) R.drawable.ic_rounded_check
                        else R.drawable.ic_rounded_check_circle,
                    ),
                    contentDescription = null,
                    tint = if (isPendingDeletionCol) redMedium else mediumGreen,
                    modifier = Modifier.size(18.dp),
                )
                else -> Text(
                    text = "-",
                    color = appColors.primaryAction,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }

    val display: String = when {
        raw == null -> "—"
        col.type == ColumnType.DATE -> formatDateShort(raw.toString())
        else -> raw.toString()
    }

    val align = when (col.type) {
        ColumnType.NUMBER -> TextAlign.Center
        else -> TextAlign.Start
    }

    Text(
        text = display,
        color = appColors.textPrimary,
        fontSize = 11.sp,
        fontFamily = if (col.sqlColumn == "uuid") FontFamily.Monospace else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        modifier = Modifier
            .width(col.widthDp.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

/** "2026-05-26T22:12:12.780Z" → "2026-05-26 22:12". Si format inattendu, retourne brut. */
private fun formatDateShort(iso: String): String {
    if (iso.length < 16) return iso
    return iso.substring(0, 10) + " " + iso.substring(11, 16)
}
