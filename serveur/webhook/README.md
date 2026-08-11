# Webhook auto-deploy GitHub -> Pi (T3.1)

Mini-service Python stdlib qui écoute les webhooks GitHub et lance `deploy.sh`
quand un push arrive sur `main`.

## Architecture

```
GitHub --(push main)--> https://<public-dns>/webhook/deploy
                                       |
                                  Caddy proxy
                                       |
                              127.0.0.1:8001 (webhook.py)
                                       |
                              vérif HMAC-SHA256
                                       |
                              fork deploy.sh (non-bloquant)
                                       |
                                  réponse 200 à GitHub
```

- `webhook.py` — listener HTTP loopback (~80 lignes, stdlib only).
- `sportapi-webhook.service` — unit systemd qui lance `webhook.py` au boot.

## Installation Pi (à faire 1 fois)

### 1. Générer le secret partagé

```bash
python3 -c "import secrets; print(secrets.token_hex(32))"
```

Garder cette valeur, elle servira aux étapes 2 et 4.

### 2. Créer le fichier d'env

```bash
mkdir -p /home/william/.config
cat > /home/william/.config/sportapi-webhook.env <<EOF
GITHUB_WEBHOOK_SECRET=<le_secret_de_l_etape_1>
EOF
chmod 600 /home/william/.config/sportapi-webhook.env
```

### 3. Installer le service systemd

```bash
sudo cp /home/william/Applications/sport-app/serveur/webhook/sportapi-webhook.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable sportapi-webhook.service
sudo systemctl start sportapi-webhook.service
sudo systemctl status sportapi-webhook.service
# attendu : active (running), log "listening on 127.0.0.1:8001/webhook/deploy"
```

### 4. Configurer Caddy

Éditer le `Caddyfile` (souvent `/etc/caddy/Caddyfile`) pour ajouter le path
`/webhook/deploy` dans le bloc existant `<public-dns>` :

```caddy
<public-dns> {
    handle /webhook/deploy {
        reverse_proxy 127.0.0.1:8001
    }
    handle {
        # bloc existant qui proxy vers FastAPI :8000
        reverse_proxy 127.0.0.1:8000
    }
}
```

Recharger :

```bash
sudo systemctl reload caddy
```

### 5. Configurer le webhook GitHub

Sur https://github.com/Will11L/sport-app -> Settings -> Webhooks -> Add webhook :

- **Payload URL** : `https://<public-dns>/webhook/deploy`
- **Content type** : `application/json`
- **Secret** : `<le_secret_de_l_etape_1>` (le même que dans le fichier env)
- **SSL verification** : Enable
- **Events** : "Just the push event"
- **Active** : coché

GitHub envoie automatiquement un event `ping` à la création -> doit retourner 200
"pong" (visible dans l'onglet "Recent Deliveries" du webhook).

## Smoke test

1. Sur la Pi : `journalctl -u sportapi-webhook.service -f`
2. Sur PC : faire un commit trivial + push :
   ```powershell
   git commit --allow-empty -m "test webhook"
   git push
   ```
3. Logs attendus dans journalctl :
   ```
   deploy: launching deploy.sh for commit <hash>
   ```
4. Vérifier sur la Pi que le serveur a bien été redémarré :
   ```bash
   sudo systemctl status sportapi.service
   # uptime doit être < 1 min
   ```

## Sécurité

- **Secret jamais committé** : seul `/home/william/.config/sportapi-webhook.env` (perms 600) le contient.
- **HMAC vérifié à chaque requête** : un POST sans signature valide reçoit 403, pas de deploy.
- **Listener loopback only** : 127.0.0.1:8001, jamais exposé direct sur le réseau.
- **Seuls les pushs main déclenchent** : autres branches -> 200 "ignored", pas de deploy.
- **Sudoers NOPASSWD requis** (T3.4) : `deploy.sh` fait `sudo systemctl restart` -> doit passer sans prompt.

## Rollback

Si le webhook pose problème :

```bash
sudo systemctl stop sportapi-webhook.service
sudo systemctl disable sportapi-webhook.service
```

Le déploiement manuel via SSH continue de fonctionner (rien ne change côté `deploy.sh`).
