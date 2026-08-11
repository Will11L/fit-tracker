"""f7_2_reload_notify_row_change

Revision ID: f72_reload_fn
Revises: f71_order_idx
Create Date: 2026-05-06

F7-2 (vague cosmétique 2026-05-06, fix runtime) :
Les migrations F2c-2 (`weekISO` → `week_iso`) et F7-1 (`order` → `order_index`)
ont renommé des colonnes Postgres référencées par les fragments triggers
SQL. Or `alembic upgrade head` ne reload **pas** automatiquement la fonction
`notify_row_change()` qui assemble ces fragments — résultat sur Pi prod :
le 1er UPDATE sur la table renommée crashe avec
`UndefinedColumnError: rec."order"` (la fonction en DB référence l'ancien
nom de colonne).

Cette migration recompose `notify_row_change()` depuis les fragments à
jour (filesystem au moment de `alembic upgrade`) via le helper partagé
`app.triggers_loader.compose_function_sql`. Idempotent (`CREATE OR REPLACE
FUNCTION`).

À l'avenir, toute migration qui renomme une col référencée par un trigger
doit appeler ce reload (ou être bundlée avec une migration successeur
qui le fait).
"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'f72_reload_fn'
down_revision: Union[str, Sequence[str], None] = 'f71_order_idx'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # Import local pour ne pas créer de dépendance Python au top-level
    # de la migration (les migrations Alembic doivent rester importables
    # même si app.triggers_loader évolue).
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade() -> None:
    """Downgrade schema.

    Pas de downgrade « propre » : la fonction `notify_row_change()` étant
    composée de fragments versionnés ailleurs, on ne peut pas restaurer
    une version "antérieure" depuis cette migration. Le downgrade est
    no-op ; pour annuler, downgrade les migrations qui ont renommé les
    colonnes (`f71_order_idx`, `f2c2_weekiso`).
    """
    pass
