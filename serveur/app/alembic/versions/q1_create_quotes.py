"""q1 — create quotes table (motivational quotes, user-scoped)

Crée la table `quotes` (Type A user-scoped, cascade ownership politique 8) :
citations motivantes privées par user, affichées aléatoirement sur le
SplashScreen après login + gérées depuis un écran dédié côté Android.
Pré-seedées au /signup via copy_starter_pack.

Trigger NOTIFY : `attach_triggers.sql` ré-attache automatiquement
`notify_row_change()` à toute table avec colonnes id+uuid. Le fragment
`quotes_trigger.sql` est désormais dans PER_TABLE_FRAGMENTS et le case
`quotes` est ajouté à `get_user_id_for()`. On recharge les 3 helpers SQL
en fin de migration (politique 15) pour que la Pi pousse les events WS.

Revision ID: q1_create_quotes
Revises: mcp1_create_tables
"""

from alembic import op
import sqlalchemy as sa


revision = "q1_create_quotes"
down_revision = "mcp1_create_tables"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "quotes",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("uuid", sa.String(), nullable=False, unique=True),
        sa.Column("text", sa.String(), nullable=False),
        sa.Column("author", sa.String(), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=True),
    )
    # Pas de create_index explicite : create_table cree deja l'index FK
    # (user_id index=True -> ix_quotes_user_id) et la contrainte unique sur
    # uuid. Un op.create_index("ix_quotes_user_id") en plus provoquait un
    # doublon -> psycopg2 DuplicateTable -> rollback de toute la migration
    # (table jamais creee). On s'aligne sur ce que create_all produit.

    # Politique 15 : recharger les helpers + la fonction notify_row_change()
    # + ré-attacher les triggers pour que `quotes` pousse ses events WS.
    from app.triggers_loader import (
        compose_function_sql,
        attach_triggers_sql,
        user_id_helper_sql,
    )
    op.execute(user_id_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())


def downgrade():
    # drop_table supprime automatiquement les index et contraintes de la table.
    op.drop_table("quotes")

    # Recharger la fonction sans le fragment quotes (le fichier loader devra
    # aussi avoir été remis en arrière pour un vrai downgrade ; best-effort).
    from app.triggers_loader import compose_function_sql, attach_triggers_sql
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
