from sqlalchemy import Column, DateTime, ForeignKey, Index, Integer, String
from app.database import Base


class RefreshToken(Base):
    """Refresh tokens long-lived (durée 7j) pour le flow OAuth-like.

    Stocke `token_hash` = SHA-256 du token brut (JAMAIS le brut). Le hash
    deterministe est indexe en UNIQUE -> lookup O(1) via token_hash (au
    /refresh comme au /logout). Un refresh est un secret aleatoire 256 bits :
    SHA-256 suffit (rien a brute-forcer), bcrypt serait inutile et couteux
    (cf. refresh_tokens.py + migration rt1_sha256_token_lookup, 2026-06-25).

    Reuse detection : si `revoked_at` non NULL et qu'un client tente
    de l'utiliser à nouveau, on revoke TOUS les tokens du user (signal
    d'un attaquant qui a volé le refresh).
    """
    __tablename__ = "refresh_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    token_hash = Column(String, nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    revoked_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        # Lookup O(1) du token au /refresh + /logout (sha256 deterministe, unique).
        Index("ix_refresh_tokens_token_hash", "token_hash", unique=True),
        # Lookup des tokens d'un user (revoke_all / reuse detection).
        Index("ix_refresh_tokens_user_revoked", "user_id", "revoked_at"),
    )
