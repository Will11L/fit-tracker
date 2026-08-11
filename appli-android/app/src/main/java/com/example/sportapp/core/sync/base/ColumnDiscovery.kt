package com.example.sportapp.core.sync.base

import kotlin.reflect.KClass

/**
 * Découvre les colonnes d'une entité Room par reflection sur ses champs Kotlin.
 *
 * - Itère `declaredFields` dans l'ordre de déclaration Kotlin.
 * - Skippe les fields synthétiques + le Companion.
 * - Dérive le nom SQL via [NamingConventions.fieldToSqlColumn] (camelCase →
 *   snake_case, sauf exceptions politique 17 comme `pendingDeletion`).
 * - Infère le type via le Java type (Boolean → BOOL, Int/Long/Float/Double → NUMBER,
 *   String avec sqlColumn endsWith("_at") → DATE, autre → STRING).
 * - Largeur heuristique selon type.
 *
 * **Note runtime** : les annotations Room (`@ColumnInfo`, `@Entity`) sont
 * `AnnotationRetention.BINARY` → strippées du runtime. Impossible de les lire
 * via reflection. On s'appuie donc sur la convention de naming, validée sur
 * les 20 entités du projet (politique 17).
 */
object ColumnDiscovery {
    fun discoverColumns(klass: KClass<*>): List<ColumnDef> =
        klass.java.declaredFields
            .filterNot {
                // Skip : synthetic, Compose `$stable` (injecté par le compiler Compose pour
                // les @Stable), Companion, et tout field qui commence par `$` (synthetic
                // Kotlin/Compose).
                it.isSynthetic || it.name == "Companion" || it.name.startsWith("$")
            }
            .map { field ->
                val sqlColumn = NamingConventions.fieldToSqlColumn(field.name)

                val type = when (field.type) {
                    Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> ColumnType.BOOL
                    Int::class.javaPrimitiveType, Int::class.javaObjectType,
                    Long::class.javaPrimitiveType, Long::class.javaObjectType,
                    Float::class.javaPrimitiveType, Float::class.javaObjectType,
                    Double::class.javaPrimitiveType, Double::class.javaObjectType -> ColumnType.NUMBER
                    else -> if (sqlColumn.endsWith("_at")) ColumnType.DATE else ColumnType.STRING
                }

                val widthDp = when (type) {
                    ColumnType.BOOL -> 30
                    ColumnType.NUMBER -> 40
                    ColumnType.DATE -> 80
                    ColumnType.STRING -> 70
                }

                ColumnDef(
                    fieldName = field.name,
                    sqlColumn = sqlColumn,
                    widthDp = widthDp,
                    type = type,
                )
            }
}
