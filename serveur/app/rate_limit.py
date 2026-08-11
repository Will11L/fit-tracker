"""V8.2-4 — Rate limiting via slowapi.

Limites appliquees uniquement sur les endpoints sensibles (auth_router) :
- /token   : 5 tentatives / IP / minute (anti brute-force password)
- /refresh : 30 tentatives / IP / minute (un user actif fait 1 refresh
             toutes les 30 min ; 30/min laisse de la marge pour les
             retry transitoires sans bloquer un usage legitime).
- /signup  : 10 / IP / heure (anti spam de creations de comptes).
- /logout  : pas de limite (revoke own token, pas d'interet attaquant).

Identification = IP client (`get_remote_address`). Si on ajoute un
reverse proxy (Caddy sur Pi), s'assurer que `X-Forwarded-For` est
preserve (`proxy_headers=True` cote uvicorn deja en place).
"""
from fastapi import Request
from fastapi.responses import JSONResponse
from slowapi import Limiter
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address

# In-memory storage par defaut (single-process). Si on scale a plusieurs
# workers, swapper vers Redis (`storage_uri="redis://..."`).
limiter = Limiter(key_func=get_remote_address)


def _rate_limit_handler(request: Request, exc: RateLimitExceeded) -> JSONResponse:
    return JSONResponse(
        status_code=429,
        content={"detail": f"Too many requests, retry after {exc.detail}"},
    )
