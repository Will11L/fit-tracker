"""Install notify_row_change() function + triggers + get_user_id_for() helper.

Charge les fragments SQL depuis `app/db_triggers/` via
`app.triggers_loader` (source de vérité unique partagée avec
`setup_db.py` / `reset_db.py` / `app/fill_database.py`).
"""
from alembic import op

from app.triggers_loader import (
    attach_triggers_sql,
    compose_function_sql,
    iso_utc_helper_sql,
    user_id_helper_sql,
)

# Révisions
# NOTE : l'ID `2025xxxx_notify_triggers` est un placeholder historique
# (première migration legacy avant que le projet adopte Alembic strict).
# Renommer en hash hex casserait `alembic upgrade head` sur PC dev + Pi
# prod tant que `alembic_version.version_num` n'est pas mis à jour
# manuellement dans chaque DB. Coût > bénéfice cosmétique : on garde.
revision = "2025xxxx_notify_triggers"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    op.execute(iso_utc_helper_sql())
    op.execute(compose_function_sql())
    op.execute(attach_triggers_sql())
    op.execute(user_id_helper_sql())


def downgrade():
    # Détache les triggers de toutes les tables `public`
    op.execute("""
    DO $$
    DECLARE r record;
    BEGIN
      FOR r IN
        SELECT t.table_name
        FROM information_schema.tables t
        WHERE t.table_schema = 'public'
          AND t.table_type = 'BASE TABLE'
          AND t.table_name NOT IN ('alembic_version')
      LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_notify ON %I', r.table_name, r.table_name);
      END LOOP;
    END $$;
    """)
    op.execute("DROP FUNCTION IF EXISTS notify_row_change();")
    op.execute("DROP FUNCTION IF EXISTS get_user_id_for(text, text);")
    op.execute("DROP FUNCTION IF EXISTS iso_utc(timestamptz);")
