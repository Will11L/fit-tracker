package com.example.sportapp.core.sync.base

/**
 * Conventions de naming Kotlin ↔ SQL utilisées par [ColumnDiscovery] et
 * [SyncableEntity.sqlTableName].
 *
 * Justification runtime : les annotations Room (`@Entity`, `@ColumnInfo`) ont
 * `AnnotationRetention.BINARY` → non-visibles via Java reflection. Impossible
 * de lire le `tableName` ou le `name` SQL au runtime. On dérive donc par
 * convention.
 *
 * Exceptions (politique CLAUDE.md §17 : flags sync local-only) : certaines
 * colonnes Room restent en camelCase pour ne pas casser la rétrocompat sur
 * les 22 tables Room sans bénéfice fonctionnel. Listées dans [CAMEL_CASE_SQL_COLUMNS].
 */
object NamingConventions {

    /**
     * Fields Kotlin dont le nom SQL Room est conservé EN camelCase (pas de
     * conversion snake_case). Politique 17.
     */
    private val CAMEL_CASE_SQL_COLUMNS = setOf(
        "pendingDeletion",
    )

    /**
     * Convertit un identifiant `camelCase` ou `PascalCase` en `snake_case`.
     *
     * Gère correctement les acronymes en majuscules consécutives :
     *   - "userId"                       → "user_id"
     *   - "muscleGroup"                  → "muscle_group"
     *   - "isFavorite"                   → "is_favorite"
     *   - "updatedAt"                    → "updated_at"
     *   - "actualWorkoutExerciseUUID"    → "actual_workout_exercise_uuid"
     *   - "UUID"                         → "uuid"
     *   - "ActualWorkoutSets" (Pascal)   → "actual_workout_sets"
     *   - "kcalPer100g"                  → "kcal_per_100g"     (frontières chiffres, nutrition A1)
     *   - "vitaminB12Per100g"            → "vitamin_b12_per_100g"
     */
    fun camelToSnake(name: String): String {
        if (name.isEmpty()) return name
        val sb = StringBuilder(name.length + 4)
        for (i in name.indices) {
            val c = name[i]
            val prev = if (i > 0) name[i - 1] else ' '
            val next = if (i < name.length - 1) name[i + 1] else ' '
            when {
                c.isUpperCase() -> {
                    // underscore quand : prev=minuscule, prev=chiffre (ex: "b12Per" → "b12_per"),
                    // ou prev=majuscule + next=minuscule (fin d'un run d'acronyme suivi d'un
                    // nouveau mot, ex: UUIDValue → uuid_value)
                    if (i > 0 && (prev.isLowerCase() || prev.isDigit() || (prev.isUpperCase() && next.isLowerCase()))) {
                        sb.append('_')
                    }
                    sb.append(c.lowercaseChar())
                }
                // underscore avant un chiffre quand prev=lettre minuscule (ex: "per100g" → "per_100g").
                // Aucune colonne pré-nutrition n'a de chiffre → pas de régression.
                c.isDigit() -> {
                    if (i > 0 && prev.isLowerCase()) {
                        sb.append('_')
                    }
                    sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Convertit un `entityName` PascalCase pluriel (ex: "ActualWorkoutSets")
     * en nom de table SQL snake_case (ex: "actual_workout_sets"). Même algo
     * que [camelToSnake], juste un alias sémantique.
     */
    fun pascalToSnake(name: String): String = camelToSnake(name)

    /**
     * Nom SQL d'une colonne à partir d'un nom de field Kotlin, en appliquant
     * la convention sauf pour les exceptions listées dans [CAMEL_CASE_SQL_COLUMNS].
     */
    fun fieldToSqlColumn(fieldName: String): String =
        if (fieldName in CAMEL_CASE_SQL_COLUMNS) fieldName else camelToSnake(fieldName)
}
