"""f7_1_rename_order_to_order_index

Revision ID: f71_order_idx
Revises: f2c2_weekiso
Create Date: 2026-05-06

F7-1 (vague cosmétique 2026-05-06) :
- `routine_periods.order` et `routine_tasks.order` étaient le mot-clé SQL
  `order` non échappé, sources potentielles de bugs futurs.
- Aligne sur `order_index` côté Postgres (cohérent avec col Room SQLite).
- Wire JSON garde `"order"` (payload trigger inchangé côté nom).
- L'attribut Python SQLAlchemy reste `order` via `Column("order_index", ...)`.
"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'f71_order_idx'
down_revision: Union[str, Sequence[str], None] = 'f2c2_weekiso'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.execute('ALTER TABLE routine_periods RENAME COLUMN "order" TO order_index')
    op.execute('ALTER TABLE routine_tasks RENAME COLUMN "order" TO order_index')


def downgrade() -> None:
    """Downgrade schema."""
    op.execute('ALTER TABLE routine_periods RENAME COLUMN order_index TO "order"')
    op.execute('ALTER TABLE routine_tasks RENAME COLUMN order_index TO "order"')
