"""V5.1 DROP TABLE muscle_weekly_summary (entite fantome)

Revision ID: 20260505_v5_1_drop_mws
Revises: 20260505_v5_5_drop_deleted_at

muscle_weekly_summary etait une entite jamais lue par l'UI Android :
- aucun screen / VM ne consomme MuscleWeeklySummary cote Android (verifie
  par grep audit Phase 2)
- aucun cas d'usage metier identifie (les statistiques weekly muscles sont
  agregees a la volee depuis actual_workout_sets via JOIN, pas stockees)
- la table existait avec son sync, son trigger, son DAO, son SyncHandler,
  son Api Retrofit, son Syncable - environ 20 fichiers cote stack pour
  rien.

Suppression complete (sous-vague G).
"""

from alembic import op
import sqlalchemy as sa


revision = "20260505_v5_1_drop_mws"
down_revision = "20260505_v5_5_drop_deleted_at"
branch_labels = None
depends_on = None


def upgrade():
    op.drop_table("muscle_weekly_summary")


def downgrade():
    # Recreer la table avec la structure d'avant V5.5+V5.1.
    # Note : pas de FK ondelete CASCADE rebranchee ici, juste la structure
    # minimale pour permettre un rollback rapide. A completer si besoin.
    op.create_table(
        "muscle_weekly_summary",
        sa.Column("id", sa.Integer(), primary_key=True, index=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE")),
        sa.Column(
            "muscle_uuid",
            sa.String(),
            sa.ForeignKey("muscles.uuid", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("week_start_date", sa.Date()),
        sa.Column("total_sets", sa.Integer()),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )
