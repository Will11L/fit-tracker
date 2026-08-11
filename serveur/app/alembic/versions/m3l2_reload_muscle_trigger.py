"""Reload notify_row_change() after adding muscleGroup to muscles fragment

Revision ID: m3l2_reload_trg
Revises: m3l1_add_group

Step 2 of the muscle 3-level hierarchy refactor. Le fragment trigger
`app/db_triggers/muscles_trigger.sql` a ete etendu pour broadcaster
`muscleGroup` (= rec.muscle_group) dans le payload WS `muscle_updated`.

Politique CLAUDE.md §15 : toute modification d'un fragment trigger qui
reference une nouvelle colonne doit etre suivie d'un reload de
notify_row_change() via compose_function_sql() — sinon le 1er UPDATE/INSERT
sur la table crashe en 500 avec UndefinedColumnError parce que la fonction
PG en memoire ne connait pas encore la nouvelle colonne.

Pas de changement de schema dans cette migration : juste un CREATE OR REPLACE
de la fonction PG inline.
"""

from alembic import op


revision = "m3l2_reload_trg"
down_revision = "m3l1_add_group"
branch_labels = None
depends_on = None


def upgrade():
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())


def downgrade():
    # Pour rollback : on devrait recharger la version precedente du fragment.
    # En pratique, downgrade impossible sans rollback du fragment file lui-meme
    # (qui est dans Git). On ne fait rien ici — l'user qui downgrade doit aussi
    # checkout l'ancien fragment muscles_trigger.sql avant de relancer la migration.
    from app.triggers_loader import compose_function_sql
    op.execute(compose_function_sql())
