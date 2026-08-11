"""
Helpers pour composer le SQL des triggers PostgreSQL depuis les fragments
du dossier `app/db_triggers/`.

Source de vérité unique. Importé par :
- `setup_db.py:setup_schema()` (idempotent, F9-Q5 2026-05-06)
- `reset_db.py:recreate_schema()` (destructif, F9-Q5 2026-05-06)
- `app/fill_database.py` (re-seeding)
- `app/alembic/versions/*.py` (migrations Alembic — politique 15 reload `notify_row_change()`)

Pour ajouter un trigger sur une nouvelle table user-scoped :
1. Créer `app/db_triggers/<table>_trigger.sql` au format fragment
   (`IF TG_TABLE_NAME = '<table>' THEN ... payload := jsonb_build_object(...); END IF;`)
2. Ajouter le nom du fichier dans `PER_TABLE_FRAGMENTS` ci-dessous
3. Si l'entité est user-scoped indirectement, étendre `user_id_helper.sql`
"""
from pathlib import Path

DB_TRIGGERS_DIR = Path(__file__).parent / "db_triggers"

# Fragments par table — substitués dans `base_function.sql` au placeholder
# `-- ex: {{TABLE_SPECIFIC_BLOCKS}}`. L'ordre n'a pas d'importance (chaque
# fragment est un IF mutuellement exclusif sur TG_TABLE_NAME).
PER_TABLE_FRAGMENTS: list[str] = [
    "actual_workouts_trigger.sql",
    "actual_workout_exercises_trigger.sql",
    "actual_workout_sets_trigger.sql",
    "available_equipments_trigger.sql",
    "cycle_workouts_trigger.sql",
    "equipments_trigger.sql",
    "exercise_equipment_trigger.sql",
    "exercise_muscle_trigger.sql",
    "exercises_trigger.sql",
    "foods_trigger.sql",
    "food_portions_trigger.sql",
    "health_goals_trigger.sql",
    "health_metrics_trigger.sql",
    "health_step_counts_trigger.sql",
    "meal_entries_trigger.sql",
    "meal_presets_trigger.sql",
    "meals_trigger.sql",
    "muscle_goals_trigger.sql",
    "muscles_trigger.sql",
    "notifications_trigger.sql",
    "nutrition_goals_trigger.sql",
    "planned_workout_exercises_trigger.sql",
    "planned_workouts_trigger.sql",
    "quotes_trigger.sql",
    "recipes_trigger.sql",
    "recipe_ingredients_trigger.sql",
    "routine_periods_trigger.sql",
    "superset_exercises_trigger.sql",
    "superset_groups_trigger.sql",
    "tasks_trigger.sql",
    "task_checks_trigger.sql",
    "training_cycles_trigger.sql",
    "water_intakes_trigger.sql",
]

PLACEHOLDER = "-- ex: {{TABLE_SPECIFIC_BLOCKS}}"


def _read(name: str) -> str:
    return (DB_TRIGGERS_DIR / name).read_text(encoding="utf-8")


def compose_function_sql() -> str:
    """SQL de `notify_row_change()` avec les fragments par table substitués."""
    base = _read("base_function.sql")
    fragments = "\n".join(_read(name) for name in PER_TABLE_FRAGMENTS)
    return base.replace(PLACEHOLDER, fragments)


def attach_triggers_sql() -> str:
    """SQL d'attache des triggers sur toutes les tables avec id+uuid."""
    return _read("attach_triggers.sql")


def user_id_helper_sql() -> str:
    """SQL de `get_user_id_for(table, uuid)` pour les entités user-scoped indirectes."""
    return _read("user_id_helper.sql")


def iso_utc_helper_sql() -> str:
    """SQL de `iso_utc(timestamptz)` qui formate au format wire canonique projet
    `YYYY-MM-DDTHH:MM:SS.UUUUUUZ` (UTC strict, 6 décimales, suffixe Z).
    Voir `app/db_triggers/iso_utc_helper.sql` pour la définition complète."""
    return _read("iso_utc_helper.sql")
