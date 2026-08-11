"""OAuth 2.0 endpoints pour MCP (Authorization Code + PKCE + DCR).

Conforme aux specs MCP OAuth (https://modelcontextprotocol.io/specification/...
authorization) :
- `.well-known/oauth-authorization-server` : metadata RFC 8414
- `.well-known/oauth-protected-resource` : metadata RFC 9728
- `POST /register` : Dynamic Client Registration RFC 7591
- `GET/POST /authorize` : consent screen + emit code
- `POST /token` : échange code → access+refresh tokens, ou refresh flow

Décisions design doc :
- Q1 : montés sous `/mcp/oauth/` (préfix du sous-app `/mcp/`)
- Q2 : access_token = JWT HS256 (cf. auth.py)
- Q3 : access 1h + refresh 30j
- Q8 : page HTML standalone /authorize (multiplatform, pas de deep link Android)
"""

from __future__ import annotations

import hashlib
import base64
import logging
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request, status
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from jinja2 import Environment, FileSystemLoader, select_autoescape
from passlib.context import CryptContext
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.models.user import User
from app.mcp import auth as mcp_auth
from app.mcp.models import MCPClient, MCPOAuthCode, MCPSession

logger = logging.getLogger(__name__)
oauth_router = APIRouter()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

_TEMPLATES_DIR = Path(__file__).parent / "templates"
_jinja_env = Environment(
    loader=FileSystemLoader(_TEMPLATES_DIR),
    autoescape=select_autoescape(["html"]),
)


def _verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def _hash_secret(secret: str) -> str:
    return pwd_context.hash(secret)


def _hash_refresh_token(token: str) -> str:
    """SHA-256 hex du refresh token de session MCP. Deterministe -> indexable
    (lookup O(1)), aligne sur app/refresh_tokens.py. Un refresh token est un secret
    aleatoire 256 bits : sha256 suffit, bcrypt serait inutile et non indexable.
    Le client_secret (auth client) garde bcrypt via _hash_secret/_verify_password."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _issuer_base_url(request: Request) -> str:
    """Base URL publique du serveur, surchargée par `MCP_OAUTH_ISSUER` en prod.

    En local dev : http://127.0.0.1:8000/mcp
    En prod Pi   : https://<pi-fqdn>/mcp
    """
    override = os.environ.get("MCP_OAUTH_ISSUER")
    if override:
        return override.rstrip("/")
    # Fallback : reconstruit depuis la request
    return f"{request.url.scheme}://{request.url.netloc}/mcp"


# -----------------------------------------------------------------------------
# Metadata endpoints (.well-known)
# -----------------------------------------------------------------------------


@oauth_router.get("/.well-known/oauth-authorization-server")
async def authorization_server_metadata(request: Request):
    """Metadata RFC 8414 — découverte par le client MCP."""
    issuer = _issuer_base_url(request)
    return {
        "issuer": issuer,
        "authorization_endpoint": f"{issuer}/oauth/authorize",
        "token_endpoint": f"{issuer}/oauth/token",
        "registration_endpoint": f"{issuer}/oauth/register",
        "scopes_supported": list(mcp_auth.SCOPES.keys()),
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code", "refresh_token"],
        "code_challenge_methods_supported": ["S256"],
        "token_endpoint_auth_methods_supported": ["client_secret_post", "none"],
    }


@oauth_router.get("/.well-known/oauth-protected-resource")
async def protected_resource_metadata(request: Request):
    """Metadata RFC 9728 — découverte de la resource (MCP server)."""
    issuer = _issuer_base_url(request)
    return {
        "resource": issuer,
        "authorization_servers": [issuer],
        "scopes_supported": list(mcp_auth.SCOPES.keys()),
        "bearer_methods_supported": ["header"],
    }


# -----------------------------------------------------------------------------
# Dynamic Client Registration (RFC 7591)
# -----------------------------------------------------------------------------


@oauth_router.post("/oauth/register")
async def register_client(
    payload: dict,
    db: AsyncSession = Depends(get_session),
):
    """Enregistre un client MCP (Claude Desktop, Claude Code, etc.).

    Body minimal :
    {
        "client_name": "Claude Desktop",
        "redirect_uris": ["http://localhost:33418/oauth/callback"]
    }
    Retourne `client_id` + `client_secret` à conserver côté client.
    """
    redirect_uris = payload.get("redirect_uris")
    if not redirect_uris or not isinstance(redirect_uris, list):
        raise HTTPException(
            status_code=400,
            detail="redirect_uris required (list of strings)",
        )

    client_name = payload.get("client_name") or "Unknown MCP Client"
    grant_types = payload.get("grant_types") or ["authorization_code", "refresh_token"]
    auth_method = payload.get("token_endpoint_auth_method") or "client_secret_post"

    client_id, client_secret = mcp_auth.generate_client_credentials()

    record = MCPClient(
        client_id=client_id,
        client_secret_hash=_hash_secret(client_secret),
        client_name=client_name,
        redirect_uris=redirect_uris,
        grant_types=grant_types,
        token_endpoint_auth_method=auth_method,
        created_at=datetime.now(timezone.utc),
    )
    db.add(record)
    await db.commit()

    return {
        "client_id": client_id,
        "client_secret": client_secret,
        "client_name": client_name,
        "redirect_uris": redirect_uris,
        "grant_types": grant_types,
        "token_endpoint_auth_method": auth_method,
    }


# -----------------------------------------------------------------------------
# Authorize endpoint (consent screen + login)
# -----------------------------------------------------------------------------


@oauth_router.get("/oauth/authorize", response_class=HTMLResponse)
async def authorize_get(
    request: Request,
    client_id: str = Query(...),
    redirect_uri: str = Query(...),
    response_type: str = Query("code"),
    scope: str = Query(""),
    state: Optional[str] = Query(None),
    code_challenge: Optional[str] = Query(None),
    code_challenge_method: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_session),
):
    """Affiche la page de consent + login sport-app."""
    if response_type != "code":
        raise HTTPException(400, "Only response_type=code is supported")

    # Vérifier client_id existe + redirect_uri whitelisté
    res = await db.execute(select(MCPClient).where(MCPClient.client_id == client_id))
    client = res.scalar_one_or_none()
    if not client:
        raise HTTPException(400, "Unknown client_id")
    if redirect_uri not in client.redirect_uris:
        raise HTTPException(400, "redirect_uri not registered for this client")

    requested_scopes = mcp_auth.parse_scopes(scope)
    template = _jinja_env.get_template("authorize.html")
    html = template.render(
        client_name=client.client_name or client_id,
        client_id=client_id,
        redirect_uri=redirect_uri,
        scope=" ".join(requested_scopes),
        scope_descriptions=[(s, mcp_auth.SCOPES[s]) for s in requested_scopes],
        state=state or "",
        code_challenge=code_challenge or "",
        code_challenge_method=code_challenge_method or "",
    )
    return HTMLResponse(html)


@oauth_router.post("/oauth/authorize")
async def authorize_post(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    client_id: str = Form(...),
    redirect_uri: str = Form(...),
    scope: str = Form(""),
    state: str = Form(""),
    code_challenge: str = Form(""),
    code_challenge_method: str = Form(""),
    db: AsyncSession = Depends(get_session),
):
    """Valide les credentials sport-app + émet authorization code."""
    res = await db.execute(select(User).where(User.username == username))
    user = res.scalar_one_or_none()
    if not user or not _verify_password(password, user.hashed_password):
        # Re-render avec erreur
        template = _jinja_env.get_template("authorize.html")
        requested_scopes = mcp_auth.parse_scopes(scope)
        html = template.render(
            client_name=client_id,
            client_id=client_id,
            redirect_uri=redirect_uri,
            scope=scope,
            scope_descriptions=[(s, mcp_auth.SCOPES[s]) for s in requested_scopes],
            state=state,
            code_challenge=code_challenge,
            code_challenge_method=code_challenge_method,
            error="Invalid username or password",
        )
        return HTMLResponse(html, status_code=401)

    # Émettre authorization_code
    code = mcp_auth.generate_authorization_code()
    expires_at = datetime.now(timezone.utc) + timedelta(
        minutes=mcp_auth.MCP_OAUTH_CODE_TTL_MINUTES
    )
    requested_scopes = mcp_auth.parse_scopes(scope)
    record = MCPOAuthCode(
        code=code,
        user_id=user.id,
        client_id=client_id,
        redirect_uri=redirect_uri,
        code_challenge=code_challenge or None,
        code_challenge_method=code_challenge_method or None,
        scopes=" ".join(requested_scopes),
        expires_at=expires_at,
    )
    db.add(record)
    await db.commit()

    # Redirect vers redirect_uri avec code + state
    sep = "&" if "?" in redirect_uri else "?"
    target = f"{redirect_uri}{sep}code={code}"
    if state:
        target += f"&state={state}"
    return RedirectResponse(url=target, status_code=302)


# -----------------------------------------------------------------------------
# Token endpoint (code exchange + refresh)
# -----------------------------------------------------------------------------


def _verify_pkce(code_challenge: str, method: str, code_verifier: str) -> bool:
    if method != "S256":
        return False
    digest = hashlib.sha256(code_verifier.encode()).digest()
    challenge = base64.urlsafe_b64encode(digest).decode().rstrip("=")
    return challenge == code_challenge


@oauth_router.post("/oauth/token")
async def token_endpoint(
    grant_type: str = Form(...),
    code: Optional[str] = Form(None),
    redirect_uri: Optional[str] = Form(None),
    client_id: Optional[str] = Form(None),
    client_secret: Optional[str] = Form(None),
    code_verifier: Optional[str] = Form(None),
    refresh_token: Optional[str] = Form(None),
    db: AsyncSession = Depends(get_session),
):
    """Échange authorization_code → tokens, ou refresh."""

    if grant_type == "authorization_code":
        return await _handle_code_exchange(
            db, code, redirect_uri, client_id, client_secret, code_verifier
        )
    elif grant_type == "refresh_token":
        return await _handle_refresh(db, refresh_token, client_id, client_secret)
    else:
        raise HTTPException(400, f"Unsupported grant_type: {grant_type}")


async def _validate_client(
    db: AsyncSession, client_id: Optional[str], client_secret: Optional[str]
) -> MCPClient:
    if not client_id:
        raise HTTPException(400, "client_id required")
    res = await db.execute(select(MCPClient).where(MCPClient.client_id == client_id))
    client = res.scalar_one_or_none()
    if not client:
        raise HTTPException(401, "Unknown client")
    # auth_method = client_secret_post : secret obligatoire
    if client.token_endpoint_auth_method == "client_secret_post":
        if not client_secret or not _verify_password(client_secret, client.client_secret_hash):
            raise HTTPException(401, "Invalid client_secret")
    return client


async def _handle_code_exchange(
    db: AsyncSession,
    code: Optional[str],
    redirect_uri: Optional[str],
    client_id: Optional[str],
    client_secret: Optional[str],
    code_verifier: Optional[str],
):
    if not code or not redirect_uri:
        raise HTTPException(400, "code + redirect_uri required")

    client = await _validate_client(db, client_id, client_secret)

    res = await db.execute(select(MCPOAuthCode).where(MCPOAuthCode.code == code))
    record = res.scalar_one_or_none()
    if not record:
        raise HTTPException(400, "Invalid code")
    if record.consumed_at is not None:
        raise HTTPException(400, "Code already used")
    if record.expires_at < datetime.now(timezone.utc):
        raise HTTPException(400, "Code expired")
    if record.client_id != client.client_id:
        raise HTTPException(400, "Code was issued to a different client")
    if record.redirect_uri != redirect_uri:
        raise HTTPException(400, "redirect_uri mismatch")

    # Vérifier PKCE si présent
    if record.code_challenge:
        if not code_verifier:
            raise HTTPException(400, "code_verifier required (PKCE)")
        if not _verify_pkce(record.code_challenge, record.code_challenge_method or "S256", code_verifier):
            raise HTTPException(400, "Invalid code_verifier")

    # Marquer le code consumed
    record.consumed_at = datetime.now(timezone.utc)

    # Émettre access + refresh
    scopes = record.scopes.split() if record.scopes else []
    access_token = mcp_auth.create_mcp_access_token(
        user_id=record.user_id,
        client_id=client.client_id,
        scopes=scopes,
    )
    refresh_token = mcp_auth.generate_refresh_token()

    session = MCPSession(
        user_id=record.user_id,
        client_id=client.client_id,
        client_name=client.client_name,
        refresh_token_hash=_hash_refresh_token(refresh_token),
        scopes=record.scopes,
        created_at=datetime.now(timezone.utc),
        expires_at=datetime.now(timezone.utc) + timedelta(days=mcp_auth.MCP_REFRESH_TTL_DAYS),
    )
    db.add(session)
    await db.commit()

    return {
        "access_token": access_token,
        "token_type": "Bearer",
        "expires_in": mcp_auth.MCP_ACCESS_TTL_MINUTES * 60,
        "refresh_token": refresh_token,
        "scope": record.scopes,
    }


async def _handle_refresh(
    db: AsyncSession,
    refresh_token: Optional[str],
    client_id: Optional[str],
    client_secret: Optional[str],
):
    if not refresh_token:
        raise HTTPException(400, "refresh_token required")
    client = await _validate_client(db, client_id, client_secret)

    # Lookup O(1) indexé par sha256(refresh_token), au lieu d'itérer + bcrypt.
    res = await db.execute(
        select(MCPSession)
        .where(MCPSession.client_id == client.client_id)
        .where(MCPSession.refresh_token_hash == _hash_refresh_token(refresh_token))
        .where(MCPSession.revoked_at.is_(None))
    )
    matched = res.scalar_one_or_none()

    if not matched:
        raise HTTPException(401, "Invalid refresh_token")
    if matched.expires_at < datetime.now(timezone.utc):
        raise HTTPException(401, "Refresh token expired")

    # Rotation : revoke ancien + emit nouveau pair
    matched.revoked_at = datetime.now(timezone.utc)
    new_refresh = mcp_auth.generate_refresh_token()
    new_session = MCPSession(
        user_id=matched.user_id,
        client_id=client.client_id,
        client_name=client.client_name,
        refresh_token_hash=_hash_refresh_token(new_refresh),
        scopes=matched.scopes,
        created_at=datetime.now(timezone.utc),
        expires_at=datetime.now(timezone.utc) + timedelta(days=mcp_auth.MCP_REFRESH_TTL_DAYS),
    )
    db.add(new_session)

    scopes = matched.scopes.split() if matched.scopes else []
    access_token = mcp_auth.create_mcp_access_token(
        user_id=matched.user_id,
        client_id=client.client_id,
        scopes=scopes,
    )
    await db.commit()

    return {
        "access_token": access_token,
        "token_type": "Bearer",
        "expires_in": mcp_auth.MCP_ACCESS_TTL_MINUTES * 60,
        "refresh_token": new_refresh,
        "scope": matched.scopes,
    }


# -----------------------------------------------------------------------------
# Ping public (vérifie que le sous-app est mounté)
# -----------------------------------------------------------------------------


@oauth_router.get("/__ping__")
async def ping():
    return {"status": "ok", "service": "mcp"}
