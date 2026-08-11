package com.example.sportapp.core.sync.base

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * Direction de tri sur une colonne.
 */
enum class SortDir { ASC, DESC, NONE }

/**
 * Construit dynamiquement la SupportSQLiteQuery pour la data grid Sync Settings.
 *
 * Deux méthodes :
 * - [build] : `SELECT * FROM <table> [WHERE ...] [ORDER BY ...] [LIMIT N OFFSET M]`
 *   pour récupérer une page de lignes.
 * - [buildCount] : `SELECT COUNT(*) FROM <table> [WHERE ...]` pour le total
 *   (pagination Excel-style + label "Showing X-Y of Z").
 *
 * Sécurité :
 * - Noms de table/colonne provenant de [tableName] et [columns] = whitelist (jamais
 *   d'input user direct), concaténés brut.
 * - Valeurs des LIKE bindées via `?` placeholders + args (anti-injection).
 * - LIMIT/OFFSET = Int Kotlin, intégrés en string interpolation après coerce ≥ 0.
 *
 * - [globalSearch] : matche en LIKE %x% sur toutes les colonnes (OR), si non vide.
 * - [columnFilters] : pour chaque (sqlColumn → query) non vide, ajoute `col LIKE ?`.
 * - Combinaison : AND entre le bloc global et chaque filtre colonne.
 * - [sortColumn] + [sortDir] : ORDER BY col DIR (col validée contre whitelist).
 */
object SqlQueryBuilder {

    /** Page de lignes pour data grid. */
    fun build(
        tableName: String,
        columns: List<ColumnDef>,
        globalSearch: String = "",
        columnFilters: Map<String, String> = emptyMap(),
        sortColumn: String? = null,
        sortDir: SortDir = SortDir.NONE,
        limit: Int? = null,
        offset: Int? = null,
    ): SupportSQLiteQuery {
        val (whereClause, args) = buildWhere(columns, globalSearch, columnFilters)

        val orderClause = if (
            sortColumn != null &&
            sortDir != SortDir.NONE &&
            columns.any { it.sqlColumn == sortColumn }
        ) " ORDER BY $sortColumn ${sortDir.name}" else ""

        val limitClause = when {
            limit != null && offset != null -> " LIMIT ${limit.coerceAtLeast(0)} OFFSET ${offset.coerceAtLeast(0)}"
            limit != null -> " LIMIT ${limit.coerceAtLeast(0)}"
            else -> ""
        }

        return SimpleSQLiteQuery(
            "SELECT * FROM $tableName$whereClause$orderClause$limitClause",
            args.toTypedArray(),
        )
    }

    /** Count total des lignes matchant filtres (sans ORDER BY / LIMIT). */
    fun buildCount(
        tableName: String,
        columns: List<ColumnDef>,
        globalSearch: String = "",
        columnFilters: Map<String, String> = emptyMap(),
    ): SupportSQLiteQuery {
        val (whereClause, args) = buildWhere(columns, globalSearch, columnFilters)
        return SimpleSQLiteQuery(
            "SELECT COUNT(*) FROM $tableName$whereClause",
            args.toTypedArray(),
        )
    }

    private fun buildWhere(
        columns: List<ColumnDef>,
        globalSearch: String,
        columnFilters: Map<String, String>,
    ): Pair<String, List<Any>> {
        val args = mutableListOf<Any>()
        val whereParts = mutableListOf<String>()

        if (globalSearch.isNotBlank() && columns.isNotEmpty()) {
            val likeArg = "%$globalSearch%"
            val inner = columns.joinToString(" OR ") { "${it.sqlColumn} LIKE ?" }
            whereParts += "($inner)"
            repeat(columns.size) { args += likeArg }
        }

        for ((col, query) in columnFilters) {
            if (query.isBlank()) continue
            if (columns.none { it.sqlColumn == col }) continue
            whereParts += "$col LIKE ?"
            args += "%$query%"
        }

        val whereClause = if (whereParts.isEmpty()) "" else " WHERE ${whereParts.joinToString(" AND ")}"
        return whereClause to args
    }
}
