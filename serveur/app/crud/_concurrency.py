"""
Optimistic concurrency control pour les upserts.

Permet aux CRUDs `upsert_X` de skip silencieusement un payload plus ancien que
la version deja en base. Garantit la semantique last-write-wins cote serveur,
symetriquement au merge cote client (`SyncMergeOps.mergeFromRemote` qui ne
descend une row que si remote.updated_at > local.updated_at).

Sans ce check, le serveur fait du last-PUSH-wins : 2 devices qui modifient la
meme row hors-ligne, le dernier a push ecrase l'autre meme s'il avait une
version plus ancienne (lost update). Avec le check, le serveur preserve
toujours la version la plus recente.

Note : applique au path single-upsert (PUT /xxx/{uuid}). Le bulk-upsert
n'a pas le check pour l'instant (extension future, cf. TODO_FEATURES.md).

Introduit 2026-05-07.
"""
from datetime import datetime
from typing import Optional


def is_payload_stale(
    payload_updated_at: Optional[datetime],
    existing_updated_at: Optional[datetime],
) -> bool:
    """
    True si `payload_updated_at < existing_updated_at` (= payload plus ancien
    que serveur, on doit le skip).

    Convention NULL : si l'un des 2 est `None` (rows orphelines pre-V3.2,
    migration legacy, payload sans datetime), on retourne False (= autorise
    l'ecrasement, comportement pre-fix). Le check ne s'active que si les 2
    cotes ont un `updated_at` comparable.
    """
    if payload_updated_at is None or existing_updated_at is None:
        return False
    return payload_updated_at < existing_updated_at
