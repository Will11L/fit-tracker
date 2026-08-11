"""Vérif sécurité : un token sport:read seul doit être REFUSÉ sur write/destructive/ops.

Args volontairement inexistants → même si l'enforcement de scope était cassé,
aucune mutation prod (les tools font un lookup qui échoue → ok:False).
On VEUT au contraire un rejet de scope (isError) AVANT toute logique.
"""
import os
import base64, hashlib, json, sys
from urllib.parse import urlparse, parse_qs
import httpx

BASE = "http://127.0.0.1:8000/mcp"
REDIRECT = "http://localhost:33418/callback"


def sse_json(text):
    for line in text.splitlines():
        if line.startswith("data:"):
            return json.loads(line[len("data:"):].strip())
    raise ValueError(text[:200])


c = httpx.Client(timeout=15.0, follow_redirects=False)
reg = c.post(f"{BASE}/oauth/register", json={"client_name": "scope-check", "redirect_uris": [REDIRECT]}).json()
cid, csec = reg["client_id"], reg["client_secret"]
verifier = base64.urlsafe_b64encode(b"scope-verifier-0123456789abcdef0123").decode().rstrip("=")
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
# Token avec sport:read UNIQUEMENT (pas write/destructive/ops)
loc = c.post(f"{BASE}/oauth/authorize", data={
    "username": "will", "password": os.getenv("SEED_USER_PASSWORD", "changeme"), "client_id": cid, "redirect_uri": REDIRECT,
    "scope": "sport:read", "state": "x", "code_challenge": challenge, "code_challenge_method": "S256",
}).headers["location"]
code = parse_qs(urlparse(loc).query)["code"][0]
access = c.post(f"{BASE}/oauth/token", data={
    "grant_type": "authorization_code", "code": code, "redirect_uri": REDIRECT,
    "client_id": cid, "client_secret": csec, "code_verifier": verifier,
}).json()["access_token"]

proto = f"{BASE}/protocol/mcp"
hdr = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream",
       "Authorization": f"Bearer {access}"}
r = c.post(proto, headers=hdr, json={"jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "s", "version": "1"}}})
hdr["mcp-session-id"] = r.headers["mcp-session-id"]
c.post(proto, headers=hdr, json={"jsonrpc": "2.0", "method": "notifications/initialized"})

print(f"Token scope = sport:read (uniquement)\n")
rid = [10]
def call(name, args):
    rid[0] += 1
    rr = c.post(proto, headers=hdr, json={"jsonrpc": "2.0", "id": rid[0], "method": "tools/call",
        "params": {"name": name, "arguments": args}})
    parsed = sse_json(rr.text)
    res = parsed.get("result", {})
    is_err = res.get("isError") or "error" in parsed
    txt = ""
    if is_err and res.get("content"):
        txt = res["content"][0].get("text", "")
    elif "error" in parsed:
        txt = parsed["error"].get("message", "")
    return is_err, txt

# 3 tools hors scope sport:read → doivent être REFUSÉS (scope manquant)
checks = [
    ("mark_set_done (write)", "mark_set_done", {"set_uuid": "nonexistent-uuid", "reps": 1, "weight": 1.0}),
    ("delete_actual_workout (destructive)", "delete_actual_workout", {"workout_uuid": "nonexistent-uuid"}),
    ("get_service_status (ops)", "get_service_status", {"name": "sportapi"}),
]
all_ok = True
for label, name, args in checks:
    is_err, txt = call(name, args)
    rejected = is_err and ("scope" in txt.lower() or "manquant" in txt.lower())
    flag = "✅ REFUSÉ" if rejected else "🔴 NON REFUSÉ"
    if not rejected:
        all_ok = False
    print(f"{flag}  {label}: isError={is_err} | {txt[:90]}")

# Sanity : un read tool DOIT passer avec sport:read
is_err, txt = call("list_muscles", {})
print(f"{'✅' if not is_err else '🔴'} CONTRÔLE  list_muscles (read, autorisé): isError={is_err}")

print(f"\nVERDICT: {'enforcement scope OK' if all_ok and not is_err else 'PROBLÈME enforcement scope'}")
sys.exit(0 if all_ok and not is_err else 1)
