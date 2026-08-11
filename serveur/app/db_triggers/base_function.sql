CREATE OR REPLACE FUNCTION notify_row_change() RETURNS trigger AS $$
DECLARE
  rec RECORD;
  op  TEXT;
  row JSONB;
  payload JSONB;
  origin_client_id TEXT;  -- ✅ NOUVEAU
BEGIN
  IF TG_OP = 'INSERT' THEN
    rec := NEW; op := 'insert';
  ELSIF TG_OP = 'UPDATE' THEN
    rec := NEW; op := 'update';
  ELSIF TG_OP = 'DELETE' THEN
    rec := OLD; op := 'delete';
  END IF;

  row := row_to_json(rec)::jsonb;

  -- ICI : blocs par table insérés par concat (Python)
  -- ex: {{TABLE_SPECIFIC_BLOCKS}}
  -- Ces blocs doivent juste mettre la variable 'payload'

  -- ✅ récupère l'id client posé par l'API pour CETTE transaction (retourne '' si absent)
  origin_client_id := current_setting('app.client_id', true);

  -- ✅ merge au top-level
  IF payload IS NOT NULL THEN
    payload := payload || jsonb_build_object('originClientId', NULLIF(origin_client_id, ''));
  END IF;

  PERFORM pg_notify('db_events', payload::text);
  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
