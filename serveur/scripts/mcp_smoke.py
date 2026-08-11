"""Smoke E2E MCP : OAuth DCR -> authorize -> token -> initialize -> tools/list.

Usage : python /tmp/mcp_smoke.py [base_url] [username] [password]
Défaut : http://127.0.0.1:8000/mcp  will  $SEED_USER_PASSWORD
"""
import base64
import hashlib
import json
import os
import sys
from urllib.parse import urlparse, parse_qs

import httpx

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8000/mcp"
USER = sys.argv[2] if len(sys.argv) > 2 else "will"
PWD = sys.argv[3] if len(sys.argv) > 3 else os.getenv("SEED_USER_PASSWORD", "changeme")
REDIRECT = "http://localhost:33418/callback"
SCOPE = "sport:read ops:read"


def sse_json(text: str) -> dict:
    """Extrait le 1er bloc `data:` d'une réponse SSE et le parse en JSON."""
    for line in text.splitlines():
        if line.startswith("data:"):
            return json.loads(line[len("data:"):].strip())
    raise ValueError(f"Pas de data SSE dans : {text[:200]!r}")


def main() -> int:
    c = httpx.Client(timeout=15.0, follow_redirects=False)

    # 1. DCR
    r = c.post(f"{BASE}/oauth/register", json={
        "client_name": "smoke-cli", "redirect_uris": [REDIRECT],
    })
    r.raise_for_status()
    reg = r.json()
    cid, csecret = reg["client_id"], reg["client_secret"]
    print(f"1. DCR              -> client_id={cid}")

    # 2. PKCE
    verifier = base64.urlsafe_b64encode(b"smoke-verifier-0123456789abcdef0123").decode().rstrip("=")
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")

    # 3. authorize POST (login + consent) -> 302 avec ?code=
    r = c.post(f"{BASE}/oauth/authorize", data={
        "username": USER, "password": PWD, "client_id": cid,
        "redirect_uri": REDIRECT, "scope": SCOPE, "state": "xyz",
        "code_challenge": challenge, "code_challenge_method": "S256",
    })
    if r.status_code != 302:
        print(f"   authorize KO {r.status_code}: {r.text[:200]}")
        return 1
    code = parse_qs(urlparse(r.headers["location"]).query)["code"][0]
    print(f"2. authorize        -> 302 code={code[:12]}…")

    # 4. token exchange
    r = c.post(f"{BASE}/oauth/token", data={
        "grant_type": "authorization_code", "code": code,
        "redirect_uri": REDIRECT, "client_id": cid,
        "client_secret": csecret, "code_verifier": verifier,
    })
    r.raise_for_status()
    access = r.json()["access_token"]
    print(f"3. token            -> access_token {len(access)} chars, scope={r.json().get('scope')}")

    proto = f"{BASE}/protocol/mcp"
    hdr = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
           "Authorization": f"Bearer {access}"}

    # 5. initialize
    r = c.post(proto, headers=hdr, json={
        "jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2025-06-18", "capabilities": {},
                   "clientInfo": {"name": "smoke", "version": "1.0"}},
    })
    r.raise_for_status()
    sid = r.headers.get("mcp-session-id")
    init = sse_json(r.text)
    print(f"4. initialize       -> {r.status_code} session={sid[:12] if sid else None}… "
          f"server={init['result']['serverInfo']['name']}")
    hdr["mcp-session-id"] = sid

    # 6. notifications/initialized
    r = c.post(proto, headers=hdr, json={"jsonrpc": "2.0", "method": "notifications/initialized"})
    print(f"5. initialized      -> {r.status_code}")

    # 7. tools/list
    r = c.post(proto, headers=hdr, json={"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}})
    r.raise_for_status()
    tools = sse_json(r.text)["result"]["tools"]
    names = sorted(t["name"] for t in tools)
    destructive = sorted(t["name"] for t in tools
                         if (t.get("annotations") or {}).get("destructiveHint"))
    print(f"6. tools/list       -> {len(names)} tools: {', '.join(names)}")
    print(f"   destructiveHint  -> {len(destructive)}: {', '.join(destructive)}")

    # 8. bonus : appels read-only réels (Cas A + Cas B1, sûrs en prod).
    def call(rid, name, args=None):
        rr = c.post(proto, headers=hdr, json={
            "jsonrpc": "2.0", "id": rid, "method": "tools/call",
            "params": {"name": name, "arguments": args or {}},
        })
        try:
            parsed = sse_json(rr.text)
            if "error" in parsed:
                return f"(error {parsed['error'].get('message','?')})"
            res = parsed.get("result", {})
            if res.get("isError"):
                txt = (res.get("content") or [{}])[0].get("text", "?")
                return f"(isError: {txt[:80]})"
            return json.loads(res["content"][0]["text"])
        except Exception as e:
            return f"(parse skip {e})"

    print(f"7. list_muscles      -> count={call(3, 'list_muscles').get('count')}")
    st = call(4, "get_service_status", {"name": "sportapi"})
    print(f"8. get_service_status-> {st if isinstance(st, str) else st.get('active_state')+'/'+str(st.get('sub_state'))+' restarts='+str(st.get('restarts'))}")
    al = call(5, "get_alembic_status")
    print(f"9. get_alembic_status-> {al if isinstance(al, str) else 'current='+str(al.get('current'))}")

    print(f"\nVERDICT: {len(names)} tools exposés.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
