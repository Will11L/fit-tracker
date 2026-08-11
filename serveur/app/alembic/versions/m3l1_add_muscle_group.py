"""Add muscle_group column to muscles (3-level hierarchy step 1/N)

Revision ID: m3l1_add_group
Revises: f8q2_ae_user_id

Refactor majeur muscles : passage de 2 niveaux (zone + name plat) à 3 niveaux
(zone > muscle_group > muscle précis). Cf. CLAUDE.md historique 2026-05-08.

Cette migration ajoute uniquement la nouvelle colonne SQLAlchemy. Le backfill
des valeurs n'est pas fait ici car la structure attendue passe de 12 à 35
muscles (cf. seed_database.py refactor). Les rows existants sont marqués
NULL et seront soit écrasés par le re-seed (politique CLAUDE.md §12 : data
test → user_id=1, perte acceptable), soit conservés avec muscle_group=NULL
pour les muscles custom user.

Pas de reload de notify_row_change() ici : le fragment trigger
muscles_trigger.sql ne référence pas encore muscle_group (ajout dans une
migration ultérieure m3l2 qui modifiera le fragment + reloadera la fonction
PG, conformément à la politique 15).

Step suivants (séparés) :
  - app/seed_database.py : refactor _STARTER_MUSCLE_SPECS + recalibrage coefs
  - app/starter_pack.py : copy muscle_group au signup
  - app/schemas/muscle_schema.py : expose muscleGroup au wire JSON
  - app/db_triggers/muscles_trigger.sql + migration m3l2 : broadcast WS
"""

from alembic import op
import sqlalchemy as sa


revision = "m3l1_add_group"
down_revision = "f8q2_ae_user_id"
branch_labels = None
depends_on = None


def upgrade():
    op.add_column(
        "muscles",
        sa.Column("muscle_group", sa.String(), nullable=True),
    )


def downgrade():
    op.drop_column("muscles", "muscle_group")
