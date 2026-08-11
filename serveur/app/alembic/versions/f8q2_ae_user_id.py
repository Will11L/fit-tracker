"""F8-Q2 : available_equipments Type C -> Type A user-scoped + UniqueConstraint(user_id, name)

Revision ID: f8q2_ae_user_id
Revises: 3ec81eb456a1

Bascule available_equipments de Type C global -> Type A user-scoped
("parmi les equipements existants, j'ai celui-ci"). Pattern V5.7.

Backfill : tous les rows existants -> user_id = 1 (admin 'will',
politique CLAUDE.md §12). Pas de table de lien existante a exploiter
(table etait global, pas de FK vers le user).

Contraintes :
- DROP UNIQUE(name) (heritage Type C global, plus pertinent en Type A
  ou plusieurs users peuvent avoir le meme nom).
- ADD UniqueConstraint(user_id, name) (un user n'a pas 2 fois le meme
  equipement disponible -- decision F8-Q2 2026-05-06).

Reload notify_row_change() (politique CLAUDE.md §15) car le fragment
trigger available_equipments_trigger.sql ne reference pas user_id
aujourd'hui mais le helper user_id_helper.sql vient d'etre etendu pour
case 'available_equipments' -- la fonction PG en memoire doit relire
le helper (compose_function_sql l'inline).
"""

from alembic import op
import sqlalchemy as sa


revision = "f8q2_ae_user_id"
down_revision = "3ec81eb456a1"
branch_labels = None
depends_on = None


def upgrade():
    # 1. ADD COLUMN nullable (temporairement, pour permettre le backfill)
    op.add_column(
        "available_equipments",
        sa.Column("user_id", sa.Integer(), nullable=True),
    )

    # 2. Backfill : tous les rows -> user_id = 1 (politique CLAUDE.md §12)
    op.execute("UPDATE available_equipments SET user_id = 1 WHERE user_id IS NULL")

    # 3. SET NOT NULL + ADD FK ondelete CASCADE + index
    op.alter_column("available_equipments", "user_id", nullable=False)
    op.create_foreign_key(
        "fk_available_equipments_user_id_users",
        "available_equipments", "users",
        ["user_id"], ["id"],
        ondelete="CASCADE",
    )
    op.create_index(
        "ix_available_equipments_user_id",
        "available_equipments",
        ["user_id"],
    )

    # 4. DROP UNIQUE(name) (heritage Type C global)
    op.drop_constraint(
        "available_equipments_name_key",
        "available_equipments",
        type_="unique",
    )

    # 5. ADD UniqueConstraint(user_id, name)
    op.create_unique_constraint(
        "uq_available_equipments_user_id_name",
        "available_equipments",
        ["user_id", "name"],
    )

    # 6. Reload notify_row_change() (helper user_id_helper.sql etendu, politique 15)
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    op.drop_constraint(
        "uq_available_equipments_user_id_name",
        "available_equipments",
        type_="unique",
    )
    op.create_unique_constraint(
        "available_equipments_name_key",
        "available_equipments",
        ["name"],
    )
    op.drop_index(
        "ix_available_equipments_user_id",
        table_name="available_equipments",
    )
    op.drop_constraint(
        "fk_available_equipments_user_id_users",
        "available_equipments",
        type_="foreignkey",
    )
    op.drop_column("available_equipments", "user_id")
