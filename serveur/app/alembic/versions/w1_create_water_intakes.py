"""w1 — Hydratation : création de la table water_intakes

Table : water_intakes (2026-07-05).

- Type A user-scoped (cascade ownership politique 8) : user_id FK CASCADE.
- Une row = une prise d'eau horodatée (amount_ml > 0). Total du jour = SUM côté
  client. `date` = jour local "YYYY-MM-DD" (convention projet, String).
- Objectif journalier versionné via health_goals (type WATER_ML) : aucune
  colonne dédiée ici, pas de changement de schéma sur les tables de goals.

Trigger NOTIFY : `attach_triggers.sql` ré-attache automatiquement
`notify_row_change()` à toute table avec colonnes id+uuid. Le fragment
`water_intakes_trigger.sql` est dans PER_TABLE_FRAGMENTS et le case est ajouté
à `get_user_id_for()`. On recharge les helpers SQL + la fonction en fin de
migration (politique 15) pour que la Pi pousse les events WS avec userId top-level.

Revision ID: w1_create_water_intakes
Revises: h1_create_health
"""

from alembic import op
import sqlalchemy as sa


revision = "w1_create_water_intakes"
down_revision = "h1_create_health"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "water_intakes",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("date", sa.String(), nullable=False, index=True),
        sa.Column("amount_ml", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )

    # Politique 15 : recharger les helpers + la fonction notify_row_change()
    # + ré-attacher les triggers pour que water_intakes pousse ses events WS.
    from app.triggers_loader import (
        compose_function_sql,
        attach_triggers_sql,
        user_id_helper_sql,
    )
    op.execute(user_id_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    op.drop_table("water_intakes")

    # Recharger la fonction sans le fragment water_intakes (le loader devra aussi
    # avoir été remis en arrière pour un vrai downgrade ; best-effort).
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
