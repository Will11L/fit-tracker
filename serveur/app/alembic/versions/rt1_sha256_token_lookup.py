"""rt1 — refresh tokens : lookup O(1) par SHA-256 indexe (fix hang logout/refresh)

Avant : token_hash = bcrypt(token) (salt aleatoire => non indexable). find_token
iterait sur TOUTE la table en bcrypt.checkpw() jusqu'a un match. La table n'etant
jamais purgee, elle a grossi (633 lignes en dev) -> /logout et /refresh faisaient
des centaines de bcrypt sur l'event loop du worker unique = CPU 100%, ~2 min de
hang bloquant tout le serveur (diagnostique par py-spy le 2026-06-25).

Apres : token_hash = sha256(token) (deterministe => indexable). Lookup O(1) via
un index UNIQUE sur token_hash. Pour un secret aleatoire de 256 bits, SHA-256
suffit (rien a brute-forcer) ; bcrypt n'apportait rien et coutait tout.

Les hashes bcrypt existants ne matcheront jamais un lookup sha256 -> on PURGE la
table (les clients se re-loggueront UNE fois ; l'access JWT 30 min reste valide en
attendant). Ca vide aussi la bloat accumulee.

Revision ID: rt1_sha256_token_lookup
Revises: fg1_add_food_group
"""
from alembic import op


revision = "rt1_sha256_token_lookup"
down_revision = "fg1_add_food_group"
branch_labels = None
depends_on = None


def upgrade():
    # Les token_hash bcrypt existants sont inutilisables avec le nouveau lookup
    # sha256 -> purge (re-login une fois). Vide aussi la bloat (633 lignes).
    op.execute("DELETE FROM refresh_tokens")
    op.create_index(
        "ix_refresh_tokens_token_hash",
        "refresh_tokens",
        ["token_hash"],
        unique=True,
    )


def downgrade():
    op.drop_index("ix_refresh_tokens_token_hash", table_name="refresh_tokens")
