# DEPLOY_FLOW — Diagramme séquence

Flow d'auto-déploiement Pi sur push `main` (T3.1, 2026-05-07) : GitHub envoie un webhook signé HMAC-SHA256 vers Tailscale Funnel, le service `webhook.py` valide la signature et lance `deploy.sh` en arrière-plan (`git pull` + `pip install` + `alembic upgrade head` + `systemctl restart sportapi`). Permet de pousser des changements serveur depuis n'importe quel device (PC, Zenbook mobilité 4G) sans SSH manuel.

## Diagramme

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Dev (PC / Zenbook)
    participant GH as GitHub
    participant Funnel as Tailscale Funnel<br/>:8443 (public HMAC-only)
    participant Hook as webhook.py<br/>:8001 (loopback)
    participant Deploy as deploy.sh
    participant SC as systemctl
    participant Svc as sportapi.service<br/>(uvicorn FastAPI)
    participant PG as PostgreSQL

    Note over Dev,GH: 1. Push code
    Dev->>GH: git push origin main
    GH->>GH: webhook configuré (event=push)<br/>secret = GITHUB_WEBHOOK_SECRET

    Note over GH,Hook: 2. Webhook signé
    GH->>Funnel: POST /webhook/deploy<br/>X-Hub-Signature-256: sha256=...<br/>X-GitHub-Event: push<br/>body = JSON push event
    Funnel->>Hook: reverse_proxy 127.0.0.1:8001<br/>(Tailscale Funnel, public Internet)

    Note over Hook,Hook: 3. Validation HMAC + filtres
    Hook->>Hook: hmac.compare_digest(<br/>sha256(secret, body),<br/>X-Hub-Signature-256)
    alt signature invalide
        Hook-->>GH: 403 bad signature
    end
    Hook->>Hook: event == "push" ?<br/>ref == "refs/heads/main" ?
    alt event ping
        Hook-->>GH: 200 pong (test config)
    end
    alt branche ≠ main
        Hook-->>GH: 200 ignored
    end

    Note over Hook,SC: 4. Lancement deploy.sh (non-bloquant)
    Hook->>Deploy: subprocess.Popen(["bash", deploy.sh])<br/>start_new_session=True
    Hook-->>GH: 200 deploy launched (immédiat)

    Note over Deploy,Svc: 5. deploy.sh — étapes séquentielles (set -e)
    Deploy->>Deploy: cd .. && git pull origin main
    Deploy->>Deploy: source env_api/bin/activate
    Deploy->>Deploy: pip install -r requirements.txt<br/>(skip si rien changé)
    Deploy->>PG: alembic upgrade head<br/>(idempotent : skip si déjà à head)
    PG-->>Deploy: ok (ou erreur si migration foireuse)
    alt migration KO (rare)
        Deploy-->>Hook: set -e stoppe le script<br/>(systemctl restart NON exécuté)
        Note over Dev: rollback manuel requis :<br/>ssh + git reset --hard <prev><br/>+ alembic downgrade <prev_rev><br/>+ systemctl restart manuel
    end
    Deploy->>SC: sudo systemctl restart sportapi.service<br/>(sudoers NOPASSWD T3.4)
    SC->>Svc: SIGTERM → respawn uvicorn
    Svc->>PG: réouvre pool asyncpg
    Svc->>Svc: importe app + charge notify_row_change()<br/>(si migration touche un trigger : politique #15)
    Svc-->>SC: ready (port 8000 listening)
    Deploy->>SC: sudo systemctl status sportapi.service<br/>(log les 10 premières lignes)

    Note over Dev,GH: 6. Vérification (optionnelle)
    Dev->>Svc: curl https://<pi-fqdn>/healthz
    Svc-->>Dev: 200 {status, db, ts}
    Dev->>GH: GitHub UI → Webhooks → Recent Deliveries<br/>(voit 200 deploy launched)
```

## Notes

- **Tailscale Funnel** : `:8443` exposé sur l'Internet public uniquement pour ce webhook (cf. [TAILSCALE_MIGRATION.md](TAILSCALE_MIGRATION.md)). Tout le reste (API `/api/v1/*`, WS) est en `tailscale serve` privé (tailnet-only depuis 2026-05-21).
- **HMAC-SHA256** : secret partagé GitHub ↔ Pi (`GITHUB_WEBHOOK_SECRET` dans `/home/william/.config/sportapi-webhook.env`, perms 600, chargé par `EnvironmentFile=` systemd). `hmac.compare_digest` = comparaison constante-time (anti-timing-attack).
- **Non-bloquant** : `subprocess.Popen(..., start_new_session=True)` détache `deploy.sh` du process webhook → la réponse 200 est immédiate (sinon GitHub timeout 10s sur le ping). Le déploiement effectif prend 20-40s en arrière-plan.
- **`set -e`** : `deploy.sh` stoppe à la première commande qui échoue → si `pip install` ou `alembic upgrade head` échoue, le `systemctl restart` n'est **pas** exécuté → le service tourne encore sur l'ancien code (état dégradé mais fonctionnel).
- **Reload triggers post-migration** (politique #15) : si une migration Alembic rename/drop une colonne référencée dans un fragment trigger SQL, la migration elle-même fait `op.execute(compose_function_sql())` → recharge `notify_row_change()` en mémoire Postgres. Pas besoin de step manuel dans `deploy.sh`.
- **Logs** : `journalctl -u sportapi-webhook -f` pour le webhook (HMAC OK/KO, deploy lancé), `journalctl -u sportapi -f` pour le service FastAPI (startup, requêtes, exceptions).

## Rollback manuel

Si `alembic upgrade head` casse :

```bash
ssh william@<pi-fqdn>  # ou <pi-lan-ip> en LAN
cd ~/Applications/sport-app
git log --oneline -5                  # repérer le commit sain précédent
git reset --hard <prev-sha>
cd serveur
source env_api/bin/activate
alembic downgrade <prev-revision>     # cf. alembic history
sudo systemctl restart sportapi.service
sudo systemctl status sportapi.service --no-pager | head -10
```

Note : `git reset --hard` local sur la Pi **uniquement** — ne pas push force le main GitHub depuis la Pi (le PC dev a probablement déjà avancé). Le webhook s'auto-déclenchera au prochain push correctif côté dev.

## Sources

- `serveur/webhook/webhook.py` — HTTP server stdlib, HMAC validation, Popen non-bloquant (~100 lignes).
- `serveur/deploy.sh` — git pull + pip + alembic + systemctl restart.
- `serveur/webhook/sportapi-webhook.service` (sur la Pi) — systemd unit avec `EnvironmentFile=`.
- Caddy config Pi (`/etc/caddy/Caddyfile`) — `reverse_proxy /webhook/deploy 127.0.0.1:8001` (dormant depuis migration Tailscale, mais structure préservée).
- Sudoers `/etc/sudoers.d/sportapi` (T3.4, 2026-05-07) — NOPASSWD pour `systemctl restart/status/journalctl sportapi*`.
- Historique CLAUDE.md 2026-05-07 entrée T3.1 — install procédure complète.
