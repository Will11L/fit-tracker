-- =====================================================================
-- Format wire canonique projet sport-app
-- =====================================================================
-- Tous les timestamps techniques (`updated_at`, `created_at`, `checked_at`)
-- du projet sport-app sont émis dans UN SEUL format sur le wire :
--
--     "YYYY-MM-DDTHH:MM:SS.UUUUUUZ"
--
-- ISO 8601, UTC strict, 6 décimales fixes (microsec), suffixe "Z".
--
-- Imposé par 3 mécanismes :
--   1. Postgres triggers : `iso_utc(rec.X)` (cette fonction)
--   2. Pydantic schemas : type `UTCDateTime` (cf. `app/utc_datetime.py`)
--   3. Android producteur : `getNowISO8601()` (cf. `CustomDateUtils.kt`)
--
-- Côté Android, toute valeur reçue est tolérée par `parseInstantSafe()`
-- (3 fallbacks pour le legacy), mais la canonical est ce qui est émis.
--
-- Voir docs/DATES.md §"Après V3.2" pour le contexte complet.
-- =====================================================================

CREATE OR REPLACE FUNCTION iso_utc(ts timestamptz) RETURNS text AS $$
  SELECT CASE WHEN $1 IS NULL THEN NULL
              ELSE to_char($1 AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
         END;
$$ LANGUAGE sql IMMUTABLE;
