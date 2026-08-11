"""V5.5 retrait de deleted_at des 21 tables (Option A simplification)

Revision ID: 20260505_v5_5_drop_deleted_at
Revises: 20260505_v5_7_tc_user_id

deleted_at etait un chemin aspirationnel jamais termine :
- aucun CRUD serveur ne le posait jamais (delete_X font hard DELETE)
- aucun SELECT ne filtrait WHERE deleted_at IS NULL
- cote Android, RemoteDataMerger.deletedAt != null n'etait jamais empruntee
- aucun cas d'usage UI (pas de corbeille, pas de recuperation)

Suppression radicale Option A (cf. REVIEW.md §8 Groupe 5.5).
Apres : la sync passe par pendingDeletion=true cote client + DELETE
serveur + push WS aux autres clients (deja en place).
"""

from alembic import op
import sqlalchemy as sa


revision = "20260505_v5_5_drop_deleted_at"
down_revision = "20260505_v5_7_tc_user_id"
branch_labels = None
depends_on = None


# Liste des 21 tables qui ont deleted_at (audit cf. CLAUDE.md historique 2026-05-05).
TABLES_WITH_DELETED_AT = [
    "actual_workouts",
    "actual_workout_exercises",
    "actual_workout_sets",
    "available_equipments",
    "cycle_workouts",
    "equipments",
    "exercise_equipment",
    "exercise_muscles",
    "exercises",
    "muscle_goals",
    "muscle_weekly_summary",
    "muscles",
    "notifications",
    "planned_workout_exercises",
    "planned_workouts",
    "routine_periods",
    "routine_tasks",
    "routine_task_checks",
    "superset_exercises",
    "superset_groups",
    "training_cycles",
]


def upgrade():
    for table in TABLES_WITH_DELETED_AT:
        op.drop_column(table, "deleted_at")


def downgrade():
    for table in TABLES_WITH_DELETED_AT:
        op.add_column(
            table,
            sa.Column("deleted_at", sa.DateTime(timezone=True), nullable=True),
        )
