"""V8.2 — Helpers pour les refresh tokens (OAuth-like).

Le token brut (~43 chars URL-safe = 256 bits d'entropie) est genere cote
serveur, retourne au client UNE FOIS, puis hashe et persiste dans
`refresh_tokens`. La table ne stocke jamais le token en clair.

Hash = SHA-256 (2026-06-25). Un refresh token est un secret aleatoire de
256 bits : SHA-256 suffit (rien a brute-forcer), contrairement a un mot de
passe ou bcrypt est requis. Surtout, le hash deterministe est INDEXABLE ->
lookup O(1) par `token_hash = sha256(raw)`. L'ancienne version bcrypt-hashait
chaque token (salt aleatoire => non indexable) et devait ITERER sur toute la
table en bcrypt.checkpw() ; quand la table grossissait (jamais purgee), /logout
et /refresh saturaient l'event loop du worker unique (CPU 100%, ~2 min de hang
bloquant tout le serveur). Diagnostique par py-spy le 2026-06-25, fix migration
rt1_sha256_token_lookup.

Au refresh :
1. Le client POST son refresh brut.
2. On retrouve la ligne par `token_hash = sha256(raw)` (1 requete indexee).
3. Si match actif : revoke ce token (rotation) + emit nouveau pair.
4. Si match deja revoke/expire : reuse detection — revoke TOUS les tokens
   du user, retourne 401.
5. Sinon : 401 simple.
"""
import hashlib
import secrets
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.refresh_token import RefreshToken
from app.settings import settings


def _generate_raw_token() -> str:
    """32 bytes URL-safe = 43 chars base64 = 256 bits d'entropie."""
    return secrets.token_urlsafe(32)


def _hash_token(raw: str) -> str:
    """SHA-256 hex du token brut. Deterministe -> indexable (lookup O(1))."""
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


async def create_refresh_token(db: AsyncSession, user_id: int) -> str:
    """Genere un refresh, persiste son hash, retourne le brut (a renvoyer
    au client UNE FOIS). Ne commite pas - laisse l'appelant gerer la
    transaction (typiquement dans le meme commit que l'access token emit)."""
    raw = _generate_raw_token()
    now = datetime.now(timezone.utc)
    expires = now + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    rt = RefreshToken(
        user_id=user_id,
        token_hash=_hash_token(raw),
        expires_at=expires,
        revoked_at=None,
        created_at=now,
    )
    db.add(rt)
    await db.flush()
    return raw


async def find_token(db: AsyncSession, raw: str) -> tuple[RefreshToken | None, bool]:
    """Trouve le RefreshToken correspondant au raw via un lookup indexe O(1)
    sur `token_hash = sha256(raw)` (index unique ix_refresh_tokens_token_hash).

    Retourne `(token, was_revoked_or_expired)`. Si `token` non None et
    `was_revoked_or_expired=True` -> reuse detection : il faut revoquer
    tous les tokens du user.
    """
    res = await db.execute(
        select(RefreshToken).where(RefreshToken.token_hash == _hash_token(raw))
    )
    matched = res.scalar_one_or_none()
    if matched is None:
        return None, False

    now = datetime.now(timezone.utc)
    invalid = matched.revoked_at is not None or matched.expires_at <= now
    return matched, invalid


async def revoke_token(db: AsyncSession, token: RefreshToken) -> None:
    """Revoke un seul token (rotation : a chaque /refresh on revoke
    l'ancien et on en emit un nouveau)."""
    if token.revoked_at is None:
        token.revoked_at = datetime.now(timezone.utc)
        await db.flush()


async def revoke_all_user_tokens(db: AsyncSession, user_id: int) -> int:
    """Revoke tous les tokens actifs d'un user. Utilise lors de la reuse
    detection (signal d'un attaquant qui a vole le refresh) ou au logout
    explicite "logout from everywhere" futur."""
    now = datetime.now(timezone.utc)
    stmt = (
        update(RefreshToken)
        .where(RefreshToken.user_id == user_id, RefreshToken.revoked_at.is_(None))
        .values(revoked_at=now)
    )
    res = await db.execute(stmt)
    return res.rowcount or 0
