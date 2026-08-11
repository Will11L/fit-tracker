"""Phase 0 : unify routine_tasks/routine_task_checks into tasks/task_checks

Revision ID: p0_unify_tasks
Revises: bio1_user_bio_fields

Phase 0 (2026-05-12) : refactor majeur de la gestion des taches.

Avant : `RoutineTask` (recurrentes quotidiennes par periode) +
        `RoutineTaskCheck` (coches par date).
Apres : `Task` unifie (NONE/DAILY/WEEKLY/MONTHLY/YEARLY) +
        `TaskCheck` (rename `date` -> `occurrence_date`).

`RoutinePeriod` reste **intact** (table de reference Morning/Midday/Evening
editable par user, FK depuis Task.period_uuid).

Strategie migration (idempotent via Alembic, atomic via transaction) :
  1. CREATE TABLE tasks (schema complet futur-ready)
  2. CREATE TABLE task_checks
  3. INSERT INTO tasks SELECT FROM routine_tasks
     (recurrence_kind='DAILY', period_uuid=<existing>, recurrence_start_date=<created_date_fallback>)
  4. INSERT INTO task_checks SELECT FROM routine_task_checks
     (date -> occurrence_date, autres champs preserves)
  5. DROP TABLE routine_task_checks (avant routine_tasks pour FK CASCADE)
  6. DROP TABLE routine_tasks
  7. Reload notify_row_change() (politique 15 : trigger fragments changes)

Pas de data perdue : tous les routine_tasks deviennent des Task DAILY,
toutes les coches deviennent des TaskCheck (rename column).

Note : `recurrence_start_date` est REQUIRED pour DAILY. On utilise la date
de l'`updated_at` comme fallback (sinon CURRENT_DATE comme dernier recours).
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB


revision = "p0_unify_tasks"
down_revision = "bio1_user_bio_fields"
branch_labels = None
depends_on = None


def upgrade():
    # === 1. CREATE TABLE tasks (schema unifie complet) ===
    op.create_table(
        "tasks",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        # Core
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("notes", sa.String(), nullable=True),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("order_index", sa.Integer(), nullable=False, server_default="0"),
        # Recurrence
        sa.Column("recurrence_kind", sa.String(), nullable=False, server_default="DAILY"),
        # Conditional fields (nullable, validated par Pydantic)
        sa.Column("due_date", sa.String(), nullable=True),
        sa.Column("due_time", sa.String(), nullable=True),
        sa.Column(
            "period_uuid",
            sa.String(),
            sa.ForeignKey("routine_periods.uuid", ondelete="SET NULL"),
            nullable=True,
            index=True,
        ),
        sa.Column("recurrence_weekdays", JSONB(), nullable=True),
        sa.Column("recurrence_start_date", sa.String(), nullable=True),
        sa.Column("recurrence_end_date", sa.String(), nullable=True),
        # Phase 3 (reminders)
        sa.Column("reminder_minutes_before", sa.Integer(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    # === 1.5. Drop la constraint orpheline `uq_task_check_per_day` sur l'ancienne
    # table routine_task_checks (encore presente sur Pi prod a ce stade puisque
    # le DROP est fait a l'etape 5). Sinon CREATE TABLE task_checks plante avec
    # `relation "uq_task_check_per_day" already exists` (memes constraint name
    # entre l'ancien RoutineTaskCheck et le nouveau TaskCheck).
    # IF EXISTS pour idempotence si la table n'est plus la (ex. PC dev qui a
    # deja fini la migration). ===
    op.execute(
        "ALTER TABLE IF EXISTS routine_task_checks "
        "DROP CONSTRAINT IF EXISTS uq_task_check_per_day"
    )

    # === 2. CREATE TABLE task_checks ===
    op.create_table(
        "task_checks",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column(
            "task_uuid",
            sa.String(),
            sa.ForeignKey("tasks.uuid", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("occurrence_date", sa.String(), nullable=False),
        sa.Column("is_checked", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("checked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint("user_id", "task_uuid", "occurrence_date", name="uq_task_check_per_day"),
    )

    # === 3. INSERT INTO tasks SELECT FROM routine_tasks ===
    # Mapping :
    #   - recurrence_kind = 'DAILY' (tous les routine_tasks etaient quotidiens)
    #   - period_uuid <- routine_tasks.period_uuid
    #   - recurrence_start_date <- updated_at::date OR CURRENT_DATE
    #   - autres champs : pass-through
    op.execute("""
        INSERT INTO tasks (
            user_id, uuid, title, notes, is_active, order_index,
            recurrence_kind, period_uuid, recurrence_start_date, updated_at
        )
        SELECT
            user_id,
            uuid,
            title,
            notes,
            is_active,
            order_index,
            'DAILY' AS recurrence_kind,
            period_uuid,
            COALESCE(TO_CHAR(updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD'),
                     TO_CHAR(CURRENT_DATE, 'YYYY-MM-DD')) AS recurrence_start_date,
            updated_at
        FROM routine_tasks
    """)

    # === 4. INSERT INTO task_checks SELECT FROM routine_task_checks ===
    # Mapping :
    #   - date -> occurrence_date (rename pur)
    #   - autres champs : pass-through
    op.execute("""
        INSERT INTO task_checks (
            user_id, task_uuid, occurrence_date, is_checked, checked_at, uuid, updated_at
        )
        SELECT
            user_id,
            task_uuid,
            date AS occurrence_date,
            is_checked,
            checked_at,
            uuid,
            updated_at
        FROM routine_task_checks
    """)

    # === 5. DROP TABLE routine_task_checks (FK CASCADE depuis routine_tasks) ===
    op.drop_table("routine_task_checks")

    # === 6. DROP TABLE routine_tasks ===
    op.drop_table("routine_tasks")

    # === 7. Reload notify_row_change() ===
    # Politique 15 : le fragment trigger pour routine_tasks a ete retire
    # de PER_TABLE_FRAGMENTS et remplace par tasks_trigger.sql + task_checks_trigger.sql.
    # On reload pour que la fonction PG reflete la nouvelle definition.
    from app.triggers_loader import compose_function_sql, attach_triggers_sql, user_id_helper_sql
    op.execute(user_id_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    # Rollback : recreer routine_tasks + routine_task_checks depuis tasks + task_checks
    # Avec mapping inverse (DAILY -> routine_tasks ; date <- occurrence_date).
    op.create_table(
        "routine_tasks",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("period_uuid", sa.String(), sa.ForeignKey("routine_periods.uuid", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("title", sa.String(), nullable=False),
        sa.Column("notes", sa.String(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_table(
        "routine_task_checks",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("task_uuid", sa.String(), sa.ForeignKey("routine_tasks.uuid", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("date", sa.String(), nullable=False),
        sa.Column("is_checked", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.Column("checked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.UniqueConstraint("user_id", "task_uuid", "date", name="uq_task_check_per_day"),
    )

    # Copy back DAILY tasks vers routine_tasks (les autres recurrences sont perdues)
    op.execute("""
        INSERT INTO routine_tasks (user_id, period_uuid, title, notes, order_index, is_active, uuid, updated_at)
        SELECT user_id, period_uuid, title, notes, order_index, is_active, uuid, updated_at
        FROM tasks WHERE recurrence_kind = 'DAILY' AND period_uuid IS NOT NULL
    """)
    op.execute("""
        INSERT INTO routine_task_checks (user_id, task_uuid, date, is_checked, checked_at, uuid, updated_at)
        SELECT tc.user_id, tc.task_uuid, tc.occurrence_date, tc.is_checked, tc.checked_at, tc.uuid, tc.updated_at
        FROM task_checks tc
        INNER JOIN tasks t ON t.uuid = tc.task_uuid
        WHERE t.recurrence_kind = 'DAILY'
    """)

    op.drop_table("task_checks")
    op.drop_table("tasks")
