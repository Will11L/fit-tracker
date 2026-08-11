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
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='public'
          AND table_name=r.table_name
          AND column_name='id'
    )
    AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='public'
          AND table_name=r.table_name
          AND column_name='uuid'
    )
    THEN
      EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_notify ON %I', r.table_name, r.table_name);
      EXECUTE format(
        'CREATE TRIGGER trg_%I_notify
         AFTER INSERT OR UPDATE OR DELETE ON %I
         FOR EACH ROW EXECUTE FUNCTION notify_row_change()',
        r.table_name, r.table_name
      );

      -- 🔔 LOG pour debug
      RAISE NOTICE 'Trigger attaché sur la table %', r.table_name;
    ELSE
      -- (optionnel) log si une table n’est pas concernée
      RAISE NOTICE 'Table % ignorée (pas de id/uuid)', r.table_name;
    END IF;
  END LOOP;
END $$;
