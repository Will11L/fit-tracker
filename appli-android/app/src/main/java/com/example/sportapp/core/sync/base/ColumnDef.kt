package com.example.sportapp.core.sync.base

/**
 * Définition d'une colonne pour la data grid Sync Settings (admin/debug).
 *
 * Construite par [ColumnDiscovery.discoverColumns] via reflection sur le modèle
 * Room. Le header affiché côté UI = [sqlColumn] directement, pour éviter toute
 * divergence avec le schéma réel (pas d'i18n : c'est un outil debug, l'user
 * attend le vrai nom de colonne).
 */
data class ColumnDef(
    /** Nom du champ Kotlin (utilisé via reflection pour le rendu cellule). */
    val fieldName: String,
    /** Nom de la colonne SQLite/Room (@ColumnInfo name, ou fieldName si absent). */
    val sqlColumn: String,
    /** Largeur fixe pour l'alignement de la grille (en dp). */
    val widthDp: Int,
    /** Type pour formattage cellule + comparateur tri + UI filtre. */
    val type: ColumnType,
)

enum class ColumnType {
    STRING,
    NUMBER,
    BOOL,
    DATE,
}
