"""uppercase_actual_workout_sets_status

Revision ID: 38449dc119b5
Revises: 1710e67a118b
Create Date: 2026-05-06 19:00:07.236109

F2c-1 (vague cosmétique 2026-05-06) :
- UPPER_CASE état `actual_workout_sets.status` (politique CLAUDE.md §11)
- Sémantiquement correct : "NOT_STARTED" (set créé = pas commencé) au lieu
  de "in_progress" (qui était sémantiquement faux).

Données existantes : `UPDATE` les rows `'in_progress'` → `'NOT_STARTED'`.
Default : `ALTER COLUMN ... SET DEFAULT 'NOT_STARTED'`.
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '38449dc119b5'
down_revision: Union[str, Sequence[str], None] = '1710e67a118b'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # 1) Migrer les rows existants
    op.execute("UPDATE actual_workout_sets SET status = 'NOT_STARTED' WHERE status = 'in_progress'")
    # 2) Bumper le default Postgres
    op.alter_column(
        'actual_workout_sets',
        'status',
        server_default='NOT_STARTED',
        existing_type=sa.String(),
        existing_nullable=False,
    )


def downgrade() -> None:
    """Downgrade schema."""
    # Revert default puis revert data
    op.alter_column(
        'actual_workout_sets',
        'status',
        server_default=None,
        existing_type=sa.String(),
        existing_nullable=False,
    )
    op.execute("UPDATE actual_workout_sets SET status = 'in_progress' WHERE status = 'NOT_STARTED'")
