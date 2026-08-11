"""
Format wire canonique projet sport-app
=======================================
Tous les timestamps techniques (`updated_at`, `created_at`, `checked_at`)
du projet sport-app sont émis dans UN SEUL format sur le wire :

    "YYYY-MM-DDTHH:MM:SS.UUUUUUZ"

ISO 8601, UTC strict, 6 décimales fixes (microsec), suffixe "Z".

Imposé par 3 mécanismes :
  1. Postgres triggers : `iso_utc(rec.X)` (cf. `app/db_triggers/iso_utc_helper.sql`)
  2. Pydantic schemas  : type `UTCDateTime` (ce module)
  3. Android producteur: `getNowISO8601()` (cf. `CustomDateUtils.kt`)

Voir docs/DATES.md §"Après V3.2" pour le contexte complet.

Usage dans un schéma :
    from app.utc_datetime import UTCDateTime

    class XBase(BaseModel):
        ...
        updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")
"""
from datetime import datetime, timezone
from typing import Annotated

from pydantic import PlainSerializer


def _to_utc_z(dt: datetime) -> str:
    """Sérialise un datetime en format wire canonique projet.

    - Si naïf  → considéré comme UTC (cohérent avec stockage Postgres timestamptz).
    - Si offset → converti en UTC.
    - Sortie : `YYYY-MM-DDTHH:MM:SS.UUUUUUZ` (6 décimales fixes, Z).
    """
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    else:
        dt = dt.astimezone(timezone.utc)
    return f"{dt.strftime('%Y-%m-%dT%H:%M:%S')}.{dt.microsecond:06d}Z"


UTCDateTime = Annotated[
    datetime,
    PlainSerializer(_to_utc_z, return_type=str, when_used="json"),
]
"""Type Pydantic pour les timestamps techniques.

Parsing : tout ce que `datetime` accepte (ISO 8601 strict, offset numérique,
suffixe Z, etc.). La canonical sortie reste `Z` UTC 6 décimales **uniquement
en mode JSON** (`model_dump_json`, `model_dump(mode="json")`, FastAPI
response).

`when_used="json"` est critique : sans ça, `model_dump()` (mode python par
défaut) retourne directement la string sérialisée — ce que les CRUDs
canoniques font (`for k, v in dto.model_dump().items(): setattr(...)`)
puis asyncpg refuse parce qu'il attend `datetime.datetime` pour une colonne
`timestamptz`. Bug runtime introduit en V3.2 (sweep 21 schémas), résolu
en V7.5.
"""
