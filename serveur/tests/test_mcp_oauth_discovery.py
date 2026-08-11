"""Découverte OAuth au domain-root (T9) — clients MCP standard (Claude Code/Desktop).

Les métadonnées vivent sous /mcp/.well-known/ mais les clients sondent la racine
du domaine. main.py expose donc /.well-known/oauth-{protected-resource,
authorization-server} (+ variantes path-aware) pointant vers /mcp/oauth/*.
"""


async def test_root_protected_resource(client):
    r = await client.get("/.well-known/oauth-protected-resource")
    assert r.status_code == 200
    d = r.json()
    assert d["resource"].endswith("/mcp")
    assert d["authorization_servers"]
    assert "sport:read" in d["scopes_supported"]
    assert d["bearer_methods_supported"] == ["header"]


async def test_root_authorization_server(client):
    r = await client.get("/.well-known/oauth-authorization-server")
    assert r.status_code == 200
    d = r.json()
    assert d["token_endpoint"].endswith("/mcp/oauth/token")
    assert d["authorization_endpoint"].endswith("/mcp/oauth/authorize")
    assert d["registration_endpoint"].endswith("/mcp/oauth/register")
    assert "S256" in d["code_challenge_methods_supported"]
    # issuer = racine (sans /mcp), cohérent avec l'emplacement du well-known (RFC 8414)
    assert not d["issuer"].endswith("/mcp")


async def test_root_protected_resource_path_aware(client):
    # Variante RFC 9728 quand la resource est sous un path.
    r = await client.get("/.well-known/oauth-protected-resource/mcp/protocol/mcp")
    assert r.status_code == 200
    assert r.json()["resource"].endswith("/mcp")


async def test_protocol_401_has_resource_metadata(client):
    r = await client.post(
        "/mcp/protocol/mcp",
        headers={"Content-Type": "application/json",
                 "Accept": "application/json, text/event-stream"},
        json={"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}},
    )
    assert r.status_code == 401
    assert "resource_metadata" in r.headers.get("WWW-Authenticate", "")
