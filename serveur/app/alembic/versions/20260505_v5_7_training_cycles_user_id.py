"""V5.7 training_cycles user_id direct (Type A) + backfill orphelins user_id=1

Revision ID: 20260505_v5_7_tc_user_id
Revises: 20260505_v5_4_nullable_defaults

Bascule training_cycles de Type C global -> Type A user-scoped (user_id direct
en colonne, FK vers users.id ondelete CASCADE).

Backfill :
- Cycles avec >= 1 cycle_workout : user_id = celui du 1er planned_workout joint
  (matche le comportement actuel de get_user_id_for() avant simplification).
- Cycles orphelins (sans cycle_workout) : user_id = 1 (admin 'will', cf.
  CLAUDE.md politique 12).

Apres cette migration : user_id_helper.sql case 'training_cycles' a
simplifier en lookup direct (commit suivant), et le router/CRUD doivent
basculer de require_admin -> get_current_user_id user-scoped.
"""

from alembic import op
import sqlalchemy as sa


revision = "20260505_v5_7_tc_user_id"
down_revision = "20260505_v5_4_nullable_defaults"
branch_labels = None
depends_on = None


def upgrade():
    # 1. Add column nullable (temporairement, pour permettre le backfill)
    op.add_column(
        "training_cycles",
        sa.Column("user_id", sa.Integer(), nullable=True),
    )

    # 2. Backfill : cycles avec workouts -> user_id du 1er planned_workout joint
    #    (DISTINCT ON garantit un seul user_id par cycle, ordonne par cw.id pour
    #    determinisme matchant l'ancien get_user_id_for() qui prenait le 1er row).
    op.execute("""
        UPDATE training_cycles tc
        SET user_id = sub.user_id
        FROM (
            SELECT DISTINCT ON (cw.training_cycle_uuid)
                cw.training_cycle_uuid AS tc_uuid,
                pw.user_id
            FROM cycle_workouts cw
            JOIN planned_workouts pw ON pw.uuid = cw.planned_workout_uuid
            ORDER BY cw.training_cycle_uuid, cw.id
        ) sub
        WHERE tc.uuid = sub.tc_uuid
    """)

    # 3. Backfill : orphelins (sans cycle_workout) -> user_id = 1 (politique CLAUDE.md §12)
    op.execute("UPDATE training_cycles SET user_id = 1 WHERE user_id IS NULL")

    # 4. SET NOT NULL + ADD FK ON DELETE CASCADE
    op.alter_column("training_cycles", "user_id", nullable=False)
    op.create_foreign_key(
        "fk_training_cycles_user_id_users",
        "training_cycles", "users",
        ["user_id"], ["id"],
        ondelete="CASCADE",
    )


def downgrade():
    op.drop_constraint(
        "fk_training_cycles_user_id_users",
        "training_cycles",
        type_="foreignkey",
    )
    op.drop_column("training_cycles", "user_id")
