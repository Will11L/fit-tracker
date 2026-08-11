"""f2c_rename_weekiso_to_week_iso

Revision ID: f2c2_weekiso
Revises: 38449dc119b5
Create Date: 2026-05-06

F2c-2 (vague cosmétique 2026-05-06) :
- Anomalie isolée : `muscle_goals.weekISO` était la seule colonne camelCase
  côté Postgres au milieu d'un schema snake_case.
- Aligne sur la convention snake_case projet : RENAME COLUMN.
- Le wire JSON reste `weekISO` côté Pydantic via `Field(..., alias="weekISO")`.
"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'f2c2_weekiso'
down_revision: Union[str, Sequence[str], None] = '38449dc119b5'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    # Postgres supporte ALTER TABLE ... RENAME COLUMN depuis longtemps.
    # Les guillemets sont nécessaires car la colonne source est camelCase.
    op.execute('ALTER TABLE muscle_goals RENAME COLUMN "weekISO" TO week_iso')


def downgrade() -> None:
    """Downgrade schema."""
    op.execute('ALTER TABLE muscle_goals RENAME COLUMN week_iso TO "weekISO"')
