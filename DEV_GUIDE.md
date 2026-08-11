# Guide Dev / Déploiement — sport-app

Documentation complète du projet `sport-app` : architecture, workflow de dev sur PC, déploiement sur Raspberry Pi.

---

## 1. Vue d'ensemble

`sport-app` est un **monorepo** qui contient :

- `appli-android/` : l'application Android (Kotlin / Jetpack Compose)
- `serveur/` : l'API FastAPI (Python) qui sert de backend

Le serveur tourne en **production sur une Raspberry Pi**, et est **développé localement sur un PC Windows** avant déploiement.

```
┌─────────────────────────┐         ┌────────────────────────┐
│   PC Windows (dev)      │         │   Raspberry Pi (prod)  │
│   ─────────────         │         │   ────────────────     │
│   serveur/ FastAPI      │         │   serveur/ FastAPI     │
│   Postgres local        │         │   Postgres local       │
│   IP : <pc-lan-ip>     │         │   IP : <pi-lan-ip>    │
│                         │         │   Domaine HTTPS :      │
│                         │         │   <public-dns>│
└──────────┬──────────────┘         └────────────────────────┘
           │                                    ▲
           │                                    │
           │ git push                           │ git pull
           ▼                                    │
      ┌────────────────────────────────────────┘
      │       GitHub (Will11L/sport-app)
      └────────────────────────────────────────┐
                                               │
                                               ▼
                                  ┌──────────────────────┐
                                  │    Téléphone Android  │
                                  │    ───────────────    │
                                  │    Build debug → PC   │
                                  │    Build release → Pi │
                                  └──────────────────────┘
```

---

## 2. Structure du repo

```
sport-app/
├── .gitignore                    # Exclut exportToHTML/, OS files, etc.
├── DEV_GUIDE.md                  # Ce fichier
├── appli-android/                # Projet Android Studio
│   ├── app/
│   │   ├── build.gradle.kts      # Config Gradle (BuildConfig debug/release)
│   │   └── src/main/
│   │       ├── java/.../utils/AppConfig.kt   # Lit BuildConfig.API_BASE_URL
│   │       └── res/xml/network_security_config.xml
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── serveur/                      # API FastAPI
    ├── .env                      # ⚠️ Local PC uniquement (gitignored)
    ├── .env.example              # Template
    ├── .gitignore
    ├── alembic.ini               # Config Alembic (migrations)
    ├── deploy.sh                 # Script de déploiement Pi (one-shot)
    ├── setup_db.py               # Bootstrap idempotent : create_all (checkfirst) + helpers + triggers
    ├── reset_db.py               # ⚠️ DESTRUCTIF : drop+create+helpers+triggers (avec confirm interactif)
    ├── start_api.sh              # Wrapper systemd (path-agnostic)
    ├── requirements.txt          # Deps Python (uvloop conditionnel)
    ├── test_db_connection.py     # Helper debug asyncpg
    └── app/
        ├── alembic/              # Migrations Alembic
        ├── crud/                 # Logique CRUD
        ├── db_triggers/          # SQL triggers (notify_row_change, etc.)
        ├── middlewares/
        ├── models/               # SQLAlchemy models
        ├── routers/              # Endpoints FastAPI
        ├── schemas/              # Schemas Pydantic
        ├── database.py           # Engine asyncpg
        ├── main.py               # Point d'entrée FastAPI
        ├── settings.py           # Config pydantic-settings (lit .env)
        ├── seed_database.py      # Données de test
        ├── fill_database.py      # Re-seeding complet
        └── clear_database.py     # Vide les tables (sauf users)
```

---

## 3. Composants clés

### Côté PC Windows

| Élément | Valeur |
|---|---|
| OS | Windows 11 |
| Python | 3.14 (dans venv) |
| Venv | `serveur/venv/` |
| PostgreSQL | 18 (installé via installer EnterpriseDB) |
| User Postgres admin | `postgres` / `postgres` |
| User Postgres app | `fittracker` / `fittracker` |
| Base de données | `fittracker` |
| URL serveur local | `http://localhost:8000` ou `http://<pc-lan-ip>:8000` |
| Config | `serveur/.env` (gitignored) |

### Côté Raspberry Pi

| Élément | Valeur |
|---|---|
| User SSH | `william` |
| IP locale | `<pi-lan-ip>` |
| Adresse Tailscale | `<pi-fqdn>` (HTTPS via `tailscale serve`) |
| Domaine public | `<public-dns>` (HTTPS via Caddy — **dormant**, port-forward box fermé) |
| Python | 3.x (venv) |
| Venv | `serveur/env_api/` ⚠️ nom différent du PC |
| PostgreSQL | local sur la Pi |
| User Postgres app | `fittracker` / `change-me` (mot de passe par défaut) |
| Service systemd | `sportapi.service` |
| Chemin du projet | `/home/william/Applications/sport-app/` |
| Config | aucun `.env`, utilise les valeurs par défaut de `settings.py` |

⚠️ **Le mot de passe Postgres et le JWT secret sur la Pi sont littéralement `change-me`**. Pas idéal en sécurité — voir TODO en bas.

### Côté Android

| Élément | Valeur |
|---|---|
| Min SDK | 29 |
| Target SDK | 36 |
| URL en build `debug` | `http://<pc-lan-ip>:8000/` (PC) |
| URL en build `release` | `https://<pi-fqdn>/` (Pi prod via Tailscale) |
| Switch | onglet **Build Variants** en bas à gauche d'Android Studio |

---

## 4. Workflow de développement (sur PC)

### 4.1 — Démarrer le serveur en local

```powershell
# 1. Aller dans serveur/
cd C:\Users\William\Documents\Applications\sport-app\serveur

# 2. Activer le venv
.\venv\Scripts\Activate.ps1
# Si erreur "execution of scripts is disabled" :
# Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned

# 3. Lancer le serveur (mode normal — préserve les données)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Vérification : `http://localhost:8000/openapi.json` doit afficher du JSON.

### 4.2 — Setup idempotent (safe à relancer)

Crée les tables manquantes uniquement (`checkfirst=True`), recharge les helpers SQL (`CREATE OR REPLACE`) et attache les triggers (idempotent). NE drop JAMAIS.

```powershell
python setup_db.py
```

Usage : nouveau dev qui clone le repo, ou ré-attache les triggers après un changement local. Pour les changements de schéma incrémentaux, utiliser **Alembic** :

```powershell
alembic revision --autogenerate -m "..."
alembic upgrade head
```

### 4.2bis — Reset complet (rare, destructif)

⚠️ **Destructif** : drop toutes les tables, recrée le schéma + helpers + triggers. Demande confirmation interactive (taper `reset` pour confirmer). NE lance plus uvicorn — relance manuellement après.

```powershell
python reset_db.py
```

À utiliser **uniquement** quand on veut repartir de zéro (schéma corrompu, etc.). La Pi prod n'exécute JAMAIS ce script (déploiement = `alembic upgrade head` via `deploy.sh`).

### 4.3 — Re-seeder les données de test

`fill_database.py` recrée les tables (`checkfirst=True`) puis attache les helpers + triggers (idempotent depuis V7.3) puis seede les users de test.

```powershell
python -m app.fill_database
```

Crée 5 users (les identifiants du compte (cf. INFRA_LOCAL.md), `bob`/`<password>`, etc.) + exercices + workouts d'exemple + le starter_template (V8.4).

### 4.4 — Tester depuis l'app Android

1. Vérifier que ton tel est sur le **même wifi** que le PC
2. Vérifier que le **firewall Windows** ne bloque pas le port 8000 (autoriser Python si demandé)
3. Dans Android Studio : Build Variants = **`debug`**
4. Run sur ton tel → l'app pointe vers `http://<pc-lan-ip>:8000/`
5. Login avec `will` / `<password>`

Logs en temps réel dans le terminal qui fait tourner uvicorn.

### 4.5 — Modifier le code

- **Python (serveur)** : uvicorn est lancé avec `--reload`, les modifs prennent effet automatiquement
- **Kotlin (Android)** : rebuild + run depuis Android Studio

---

## 5. Workflow de déploiement (vers la Pi)

### 5.1 — Push depuis le PC

```powershell
cd C:\Users\William\Documents\Applications\sport-app
git status                    # vérifier ce qui sera commit
git add .
git commit -m "description du changement"
git push
```

### 5.2 — Déployer sur la Pi (one-liner)

```bash
ssh <ssh-user>@<pi-lan-ip>
~/Applications/sport-app/serveur/deploy.sh
```

Le script `deploy.sh` enchaîne :
1. `git pull` du monorepo
2. `pip install -r requirements.txt` (skip si rien n'a changé)
3. `sudo systemctl restart sportapi.service` (demande mot de passe sudo)
4. Affiche le statut

⚠️ Le `--reload` d'uvicorn fait que le service rebooterait tout seul à chaque modif de fichier, mais le `systemctl restart` est plus propre.

### 5.3 — Vérifier que la prod tourne

Depuis ton PC, navigateur :
- `https://<public-dns>/openapi.json` → JSON OK = prod up

Ou en SSH sur la Pi :
```bash
sudo systemctl status sportapi.service
```

### 5.4 — Build de l'APK release pour ton tel

Dans Android Studio :
1. Build Variants = **`release`**
2. Menu `Build` → `Generate App Bundles or APKs` → `Generate APKs`
3. L'APK est généré dans `app/release/`
4. Transfère sur le tel + installe

L'APK utilisera l'URL HTTPS prod.

---

## 6. Cheatsheet — commandes les plus utilisées

### PC Windows (PowerShell)

```powershell
# Activer le venv
cd C:\Users\William\Documents\Applications\sport-app\serveur
.\venv\Scripts\Activate.ps1

# Lancer le serveur (mode normal)
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Setup idempotent de la DB (safe à relancer, ne drop pas)
python setup_db.py

# Reset complet de la DB (⚠️ destructif, demande confirmation)
python reset_db.py

# Re-seeder les données de test (re-attache aussi les triggers)
python -m app.fill_database

# Test rapide de connexion DB
python test_db_connection.py

# Trouver mon IP locale
ipconfig | Select-String "IPv4"

# Vérifier que Postgres tourne
Get-Service postgresql-x64-18

# Connexion directe en SQL (admin)
psql -U postgres -h 127.0.0.1
# user app
psql -U fittracker -h 127.0.0.1 -d fittracker
```

### Raspberry Pi (SSH)

```bash
# Se connecter
ssh <ssh-user>@<pi-lan-ip>

# Déploiement complet (script tout-en-un)
~/Applications/sport-app/serveur/deploy.sh

# Manuellement :
cd ~/Applications/sport-app/serveur
git pull
source env_api/bin/activate
pip install -r requirements.txt
sudo systemctl restart sportapi.service

# Statut du service
sudo systemctl status sportapi.service

# Logs en temps réel
sudo journalctl -u sportapi.service -f

# Stop pour debug à la main
sudo systemctl stop sportapi.service
cd ~/Applications/sport-app/serveur
source env_api/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

# Restart Postgres si besoin
sudo systemctl restart postgresql
```

### Git (tous environnements)

```bash
# Voir l'état
git status
git log --oneline

# Synchroniser
git pull
git push

# Voir les changements
git diff
git diff --stat
```

---

## 7. Configuration importante / fichiers sensibles

### `serveur/.env` (PC uniquement, gitignored)

```env
DATABASE_URL=postgresql+asyncpg://fittracker:fittracker@127.0.0.1:5432/fittracker
JWT_SECRET_KEY=f4a1b6e9d2c7f5a3b8e2d7c1f4a9b6e3d8c2f7a1b4e9d6c3f8a2b7e4d1c8f5a3
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30
JWT_ISS=fittracker-api
JWT_AUD=fittracker-clients
```

### Pi : pas de `.env`, valeurs par défaut de `settings.py`

```python
DATABASE_URL: str = "postgresql+asyncpg://fittracker:change-me@localhost:5432/fittracker"
JWT_SECRET_KEY: str = "change-me"
```

### Service systemd `/etc/systemd/system/sportapi.service`

```ini
[Unit]
Description=FastAPI Sport App
After=network.target

[Service]
User=william
WorkingDirectory=/home/william/Applications/sport-app/serveur
ExecStart=/home/william/Applications/sport-app/serveur/start_api.sh
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```

Pour éditer : `sudo systemctl edit --full sportapi.service`

---

## 8. Troubleshooting

### Côté PC

| Symptôme | Cause | Solution |
|---|---|---|
| `claude: command not found` | PATH manquant | Ajouter `C:\Users\William\.local\bin` au PATH |
| `psql: command not found` | PATH manquant | Ajouter `C:\Program Files\PostgreSQL\18\bin` au PATH |
| `uvloop does not support Windows` | uvloop n'est pas Windows-compatible | Voir `requirements.txt` ligne 46 (`platform_system != "Windows"`) |
| `WinError 64 / connection reset` au démarrage | `localhost` résolu en IPv6 buggé | Utiliser `127.0.0.1` dans `.env` |
| `la relation "users" n'existe pas` | Models pas importés avant `create_all` | Voir `setup_db.py` / `reset_db.py` (`import app.models`) |
| `pydantic-settings ne lit pas .env` | Pas de `model_config` | Voir `settings.py` ligne 4 (`SettingsConfigDict(env_file=".env")`) |
| `psql FATAL authentication failed` | Mauvais mot de passe | Méthode `pg_hba.conf` → `trust` temporaire pour reset |
| Login Android échoue (timeout) | Firewall Windows bloque port 8000 | Autoriser Python dans le firewall |

### Côté Pi

| Symptôme | Cause | Solution |
|---|---|---|
| Service ne démarre pas | Mauvais path dans systemd | `sudo systemctl edit --full sportapi.service` → corriger `WorkingDirectory` et `ExecStart` |
| Permission denied sur start_api.sh | Pas exécutable | `chmod +x ~/Applications/sport-app/serveur/start_api.sh` |
| Notifications WebSocket ne marchent pas | Triggers SQL absents | Lancer `python setup_db.py` (idempotent, ré-attache les triggers sans drop) |

### Côté Android

| Symptôme | Cause | Solution |
|---|---|---|
| `cleartext not permitted` | HTTP non autorisé pour cette IP | Ajouter l'IP dans `network_security_config.xml` |
| App ignore mes changements de URL | Build cache | Build → Clean Project + Rebuild |
| App debug parle quand même à la prod | BuildConfig pas régénéré | Resync Gradle + Rebuild |

---

## 9. TODO / Améliorations futures

### Sécurité

- [ ] **V1.2 — Rotation secrets `change-me` Pi** (procédure complète, ~15 min)

  **Préalable côté code** : déjà OK — `pydantic-settings` configuré dans `settings.py` avec `env_file=".env"`. `start_api.sh` fait `cd serveur/` avant uvicorn → `.env` trouvé. `serveur/.gitignore` exclut `.env`. Il suffit de créer le fichier sur la Pi.

  **Étapes** :

  ```bash
  # 1. Générer un JWT secret fort (depuis n'importe où)
  python3 -c "import secrets; print(secrets.token_hex(32))"
  # → copier le hex (64 caractères)

  # 2. Connexion Pi
  ssh <ssh-user>@<pi-lan-ip>

  # 3. Changer le password Postgres
  sudo -u postgres psql -c "ALTER USER fittracker WITH PASSWORD '<NEW_PASSWORD>';"

  # 4. Créer le .env Pi (gitignored, perms 600)
  cat > ~/Applications/sport-app/serveur/.env <<EOF
  DATABASE_URL=postgresql+asyncpg://fittracker:<NEW_PASSWORD>@localhost:5432/fittracker
  JWT_SECRET_KEY=<NEW_JWT_HEX>
  EOF
  chmod 600 ~/Applications/sport-app/serveur/.env

  # 5. Redémarrer le service (sudoers NOPASSWD configuré T3.4)
  sudo systemctl restart sportapi.service

  # 6. Vérifier OK
  sudo systemctl status sportapi.service
  sudo journalctl -u sportapi.service -n 30 --no-pager
  ```

  **Conséquences immédiates** :
  - ⚠️ Tous les tokens JWT existants sont invalidés → re-login obligatoire depuis le S21+ (1 fois)
  - Le PC dev n'est pas affecté (envoir séparé, son propre `.env`)

  **Si le service ne redémarre pas** : vérifier `journalctl` pour l'erreur. Causes courantes :
  - Syntax `.env` (pas d'espace autour de `=`, pas de quotes inutiles)
  - Password Postgres avec caractères spéciaux non-échappés dans l'URL → préférer un password alphanumérique simple (long mais sans `@`, `:`, `/`)
  - Permissions `.env` (doit être lisible par le user du service systemd, généralement `william`)

  **À faire avant** d'ouvrir l'API à d'autres users (ami, famille). Tant que c'est un usage perso depuis le S21+, le risque est faible mais non nul (n'importe quel scan Internet sur `<public-dns>` peut tester `change-me`).
- [ ] Configurer `sudoers` pour autoriser `systemctl restart sportapi.service` sans mot de passe (workflow `deploy.sh` plus fluide). **Procédure documentée T3.4 (2026-05-06)** :
  ```bash
  ssh <ssh-user>@<pi-lan-ip>
  # Trouver le path exact de systemctl + journalctl (varie selon distrib) :
  which systemctl    # ex: /usr/bin/systemctl
  which journalctl   # ex: /usr/bin/journalctl
  # Créer un fichier sudoers dédié au projet (jamais éditer /etc/sudoers direct) :
  sudo visudo -f /etc/sudoers.d/sportapi
  ```
  Coller dans l'éditeur (adapter les paths si différents) :
  ```
  # T3.4 (2026-05-06) : NOPASSWD pour deploy.sh (restart sportapi.service)
  william ALL=(ALL) NOPASSWD: /usr/bin/systemctl restart sportapi.service, /usr/bin/systemctl status sportapi.service, /usr/bin/journalctl -u sportapi.service *
  ```
  Sauver (Ctrl+X, Y, Entrée). visudo valide la syntaxe avant d'écrire — si syntax error, il refuse de sauver.

  Smoke (sans password attendu) :
  ```bash
  sudo systemctl status sportapi.service
  sudo journalctl -u sportapi.service -n 5
  sudo systemctl restart sportapi.service
  ```
  Une fois validé, à ticker dans TODO_FEATURES.md §0 T3.4.
- [ ] **Backup automatique DB Pi (cron `pg_dump`)** — *script livré, à installer côté Pi*

  Le script `serveur/backup_pi.sh` parse `DATABASE_URL` depuis `.env` et sort un dump quotidien gzippé dans `~/backups/`. Rétention 30 jours auto (`find -mtime +30 -delete`).

  **Installation sur la Pi (une fois)** :
  ```bash
  ssh <ssh-user>@<pi-lan-ip>
  cd ~/Applications/sport-app && git pull
  chmod +x ~/Applications/sport-app/serveur/backup_pi.sh
  mkdir -p ~/backups

  # Smoke manuel : doit produire un fichier fittracker-YYYY-MM-DD.sql.gz
  ~/Applications/sport-app/serveur/backup_pi.sh

  # Installer le crontab quotidien (3h du matin)
  (crontab -l 2>/dev/null; echo "0 3 * * * /home/william/Applications/sport-app/serveur/backup_pi.sh >> /home/william/backups/cron.log 2>&1") | crontab -

  # Vérifier
  crontab -l
  ```

  **Restauration** (en cas de crash DB) :
  ```bash
  # Stop le service avant restore pour éviter les writes concurrents
  sudo systemctl stop sportapi.service

  # Drop + recreate la DB (DESTRUCTIF)
  sudo -u postgres psql -c "DROP DATABASE fittracker;"
  sudo -u postgres psql -c "CREATE DATABASE fittracker OWNER fittracker;"

  # Restore depuis le backup le plus récent
  gunzip < ~/backups/fittracker-$(ls ~/backups/ | tail -1) | psql -U fittracker -h 127.0.0.1 fittracker

  sudo systemctl start sportapi.service
  ```

  **Backups stockés localement sur la Pi only** (pas de cloud sync). Si la Pi crash physiquement (SD HS), backup perdu. Pour une vraie résilience : `rsync` vers cloud (OneDrive/GDrive) ou copie sur un autre serveur — différé tant que c'est usage perso.

### Code

- [x] **`exec_pg.py` est destructif** : séparer en 2 scripts ✅ vague F9-Q5 (2026-05-06) : `setup_db.py` (idempotent) + `reset_db.py` (destructif, prompt confirm) + lancement uvicorn découplé.
- [x] **`fill_database.py` perd les triggers** : ajouter `attach_triggers` à la fin du seed ✅ V7.3 (2026-05-05).
- [x] Migrations Alembic : autogenerate branché ✅ V3.4 (2026-05-05). Politique formalisée CLAUDE.md §16 (F9-Q3) : `Base.metadata.create_all` cantonné à `setup_db.py` / `reset_db.py` / `fill_database.py`, tout le reste via Alembic.
- [x] Mode prod sur la Pi : retirer `--reload` ✅ commit `7f2f8e7` (2026-05-04).

### Déploiement

- [ ] Script `deploy_to_pi.ps1` côté PC qui fait `git push + ssh + deploy.sh` en une commande
- [ ] CI/CD GitHub Actions : auto-déployer sur la Pi à chaque push sur `main`
- [ ] Webhook GitHub → la Pi pull automatiquement (alternative à CI/CD)

### Repos GitHub

- [ ] **Archiver les anciens repos** `Will11L/SportApp` et `Will11L/sport_api` (Settings → Archive) — devenus inutiles depuis le monorepo

### Android

- [ ] Variant `staging` en plus de `debug` et `release` (ex. pointer vers une copie de la prod pour tester avant déploiement)
- [ ] Ajouter une UI dans l'app pour switcher d'URL serveur sans rebuild (utile si on change d'IP PC souvent)

### Documentation

- [ ] Documenter la structure de la base de données (diagramme ER)
- [ ] Documenter les endpoints critiques (auth flow, sync flow)
- [ ] Tutoriel pour ajouter un nouveau type d'entity (model + crud + router + schema)

---

## 10. Tailscale — accès Pi privé

### Architecture (depuis 2026-05-21)

L'API Pi n'est **plus exposée publiquement**. L'accès se fait exclusivement via le tailnet :

```
S21+ (Tailscale VPN)  ──►  <pi-fqdn>        ──►  uvicorn :8000  (API)
ASUS  (Tailscale VPN) ──►  <pi-fqdn>        ──►  uvicorn :8000  (API)
GitHub                ──►  <pi-fqdn>:8443   ──►  webhook :8001  (deploy)
```

- `tailscale serve` (port 443, tailnet-only) → API uvicorn, cert Tailscale automatique.
- `tailscale funnel` (port 8443, public) → webhook.py, seul point public — HMAC GitHub requis.

### Devices sur le tailnet

| Device | Hostname Tailscale | Rôle |
|---|---|---|
| Raspberry Pi | `<pi-fqdn>` | Serveur prod |
| ASUS Zenbook | `willasus` | Dev mobile |
| Samsung S21+ | `s21-de-william` | Client app |

### Commandes Pi utiles

```bash
# Vérifier serve (API) + funnel (webhook)
tailscale serve status

# Relancer si tout a été perdu (rare)
tailscale serve --bg http://localhost:8000
tailscale funnel --bg --https=8443 http://localhost:8001

# Voir les devices connectés au tailnet
tailscale status
```

### Persistance au reboot

`tailscale serve --bg` et `tailscale funnel --bg` sauvegardent leur config dans tailscaled — elle survit aux reboots sans rien ajouter au systemd.

### Ré-ouvrir l'accès public (si besoin futur)

1. Livebox (`http://<box-ip>`) → Réseau → NAT/PAT → réactiver les deux règles :
   - **Web Server _HTTP_** port 80 → raspberrypi
   - **Secure Web Server _HTTPS_** port 443 → raspberrypi
2. Caddy + `<public-dns>` + DDNS reprennent immédiatement (dormants, pas supprimés).
3. ⚠️ Le certificat Let's Encrypt a pu expirer pendant la dormance → Caddy le renouvelle au redémarrage (HTTP-01 challenge, nécessite le port 80 ouvert). Pour éviter ce créneau sans cert : passer Caddy en challenge **DNS-01** (renouvellement sans port entrant).

### Changer les URLs app Android

`build.gradle.kts` release pointe sur `<pi-fqdn>`. Si le hostname Tailscale change (rare) :
1. Mettre à jour les deux `buildConfigField` dans le bloc `release`.
2. **Clean Project + Rebuild** (cache Gradle).

---

## 11. Boucle dev — ADB over Tailscale + SSH PC depuis le tel

Permet de builder + installer un APK sur le S21+ via Tailscale en wifi, sans câble USB, sans être sur le même réseau LAN que le PC. Bonus : SSH PC depuis Termux pour piloter le dev à distance.

### Prérequis tailnet (déjà OK)

```powershell
tailscale status                       # PC visible comme pc-will, tel comme s21-de-william
```

Si le tel n'apparaît pas : installer **Tailscale** sur le S21+ (Play Store), login Will11L. Apparaît avec une IP `100.x.x.x`.

### Modes de connexion ADB

| Mode | Réseau requis sur le tel | Persistance | Avantage | Limite |
|---|---|---|---|---|
| **USB** | aucun | tant que branché | Le plus rapide, aucune config | Câble obligatoire, pas mobile |
| **Débogage sans fil moderne** (Android 11+) | Wi-Fi actif | jusqu'à toggle off | Pairing sécurisé | Port dynamique change à chaque reboot tel |
| **`adb tcpip 5555`** (legacy Android < 11, marche encore sur Android 15) | wifi (tailnet en peer-direct ou via LAN) | **reset à chaque reboot du tel** | Port fixe `5555` → automatise le script `push-to-phone.ps1` | Doit être (ré)activé depuis une session ADB existante après chaque reboot tel |

> **Choix par défaut** : `adb tcpip 5555` activé une fois après chaque reboot tel, puis le script `push-to-phone.ps1` marche sans paramètre jusqu'au reboot suivant.

### Activation Débogage sans fil moderne (wifi local, S21+)

1. **Options développeur** : Paramètres → À propos du téléphone → tap 7× sur Build number
2. Paramètres → Options développeur → **Débogage sans fil** = ON
3. Tap sur "Débogage sans fil" → noter `<ip>:<adb_port>` (ex `192.168.1.42:43521`) et "Associer l'appareil avec un code de couplage" → noter `<ip>:<pair_port>` + le code à 6 chiffres

### Pairing + connect Débogage sans fil moderne (PC, 1ère fois)

```powershell
$env:Path += ";C:\Users\William\AppData\Local\Android\Sdk\platform-tools"
# IP tailnet du tel (récupérée via tailscale status — pas l'IP wifi locale)
$telTs = "<phone-ts-ip>"

adb pair ${telTs}:<pair_port>          # entrer le code à 6 chiffres affiché sur le tel
adb connect ${telTs}:<adb_port>
adb devices                            # doit lister <phone-ts-ip>:<port>  device
```

> Note Samsung : pairing + Débogage sans fil moderne **exigent que le wifi soit actif sur le tel** (le port aléatoire écoute uniquement sur l'interface wifi).

### Bascule en `adb tcpip 5555` (port fixe, après chaque reboot tel)

Une fois la session Débogage sans fil moderne établie, bascule le tel en mode legacy port fixe `5555` pour que le script `push-to-phone.ps1` marche sans paramètre :

```powershell
adb -s ${telTs}:<adb_port> tcpip 5555
```

`tcpip 5555` est réinitialisé à chaque reboot du S21+ — refaire l'étape ci-dessus depuis une session ADB existante (USB ou Débogage sans fil moderne en wifi local).

### Build + install release (la boucle dev)

**Via le script helper** (recommandé — détecte automatiquement USB > Tailscale 5555) :

```powershell
cd C:\Users\William\Documents\Applications\sport-app
.\scripts\push-to-phone.ps1                 # push APK existant
.\scripts\push-to-phone.ps1 -Build          # build release puis push
.\scripts\push-to-phone.ps1 -NoRelaunch     # install sans relancer l'app
```

Le script charge JDK + ADB dans le PATH, détecte le mode disponible (USB d'abord, Tailscale `<phone-ts-ip>:5555` ensuite), `adb install -r`, relance l'app. Cf. [scripts/push-to-phone.ps1](scripts/push-to-phone.ps1).

**Manuellement** (si le script ne convient pas) :

```powershell
$env:JAVA_HOME = "C:\Users\William\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
cd C:\Users\William\Documents\Applications\sport-app\appli-android
.\gradlew.bat :app:assembleRelease --no-daemon
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell monkey -p com.example.sportapp -c android.intent.category.LAUNCHER 1
```

### SSH PC depuis le tel (Termux, optionnel)

Utile si tu veux taper du code / lancer des commandes PC directement depuis le tel.

1. **Sur le PC (une seule fois)** : installer OpenSSH Server + le démarrer
   ```powershell
   Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
   Start-Service sshd
   Set-Service -Name sshd -StartupType Automatic
   ```
2. **Sur le S21+** : installer **Termux** (via F-Droid uniquement — la version Play Store est obsolète) puis :
   ```bash
   pkg update && pkg install openssh
   ssh William@<pc-ts-ip>        # IP tailnet du PC (pc-will)
   ```

### Troubleshooting

| Symptôme | Cause / fix |
|---|---|
| `adb connect ${telTs}:5555` refused | Le tel a rebooté → `tcpip 5555` a été reset. Refaire `adb -s ${telTs}:<adb_port> tcpip 5555` depuis une session Débogage sans fil moderne (cf. section dédiée). |
| `adb pair` refused | Mauvais port (le port pairing ≠ port connect). Re-lire l'écran "Débogage sans fil". |
| `adb devices` vide après connect Débogage sans fil moderne | Le port ADB change au reboot du tel → refaire `adb connect ${telTs}:<new_port>`. (Pour éviter ça : passer en `adb tcpip 5555`, port fixe.) |
| Le tel n'est pas dans `tailscale status` | App Tailscale fermée par le système (battery optimization). Désactiver l'optimisation batterie pour Tailscale dans les paramètres du tel. |
| SSH PC refuse la connexion | Service `sshd` pas démarré (`Get-Service sshd`) ou Windows Firewall bloque le port 22 (régle auto créée par le service, sinon `New-NetFirewallRule -Name sshd -DisplayName 'OpenSSH Server' -Enabled True -Direction Inbound -Protocol TCP -Action Allow -LocalPort 22`). |

---

## 12. Migrations Room (Android)

Depuis 2026-05-05 le projet Android utilise un système de migrations Room (avant : crash à chaque bump de version).

### Composants

- `@Database(version = DATABASE_VERSION, exportSchema = true)` ([AppDatabase.kt](appli-android/app/src/main/java/com/example/sportapp/data/local/AppDatabase.kt)) — la version est exportée à chaque build dans `appli-android/app/schemas/com.example.sportapp.data.local.AppDatabase/<N>.json`. **Ces fichiers JSON sont commités** (point de référence pour les migrations futures).
- `data/local/migrations/Migrations.kt` — registre `Migrations.ALL: Array<Migration>`. Ajouter ici chaque migration `Migration(N, N+1)`.
- `AppModule.provideDatabase` — branchement via `.addMigrations(*Migrations.ALL)`.
- `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` dans `build.gradle.kts` — emplacement des JSON exportés.

### Comment ajouter une migration v(N) → v(N+1)

1. **Bumper la version** : `DATABASE_VERSION = N+1` dans `AppDatabase.kt`.
2. **Modifier les `@Entity` concernées** (ajout/suppression de colonne, renommage, etc.).
3. **Build > Make Project** dans Android Studio. Room va :
   - Lire le précédent schéma `app/schemas/.../N.json`
   - Constater le nouveau schéma à partir des `@Entity` modifiées
   - Générer le nouveau fichier `N+1.json`
   - Si les changements ne sont pas couverts par une migration enregistrée → **erreur de compilation** explicite.
4. **Écrire la migration** dans `Migrations.kt` :
   ```kotlin
   private val MIGRATION_N_NPLUS1 = object : Migration(N, N+1) {
       override fun migrate(db: SupportSQLiteDatabase) {
           db.execSQL("ALTER TABLE users ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0")
           // ... autres opérations SQL en SQLite syntax
       }
   }
   object Migrations {
       val ALL: Array<Migration> = arrayOf(MIGRATION_N_NPLUS1)
   }
   ```
5. **Tester** : installer l'app sur un device avec la version N (ancienne), puis l'upgrade. Si la migration plante, Room throw `IllegalStateException` au démarrage (`fallbackToDestructiveMigration(false)`).

### Gotchas

- **SQLite ≠ Postgres** : pas de `ALTER COLUMN`, pas de types riches. Pour renommer/changer le type d'une colonne, il faut copier vers une nouvelle table puis swap.
- **NOT NULL sans default** : impossible sans default value (sinon les rows existants violent la contrainte). Toujours fournir `DEFAULT`.
- **`fallbackToDestructiveMigration(false)`** : si une migration manque ou crashe → exception. **Ne jamais** mettre `true` en prod sans plan : ça wipe la DB locale silencieusement.

---

## 13. Historique des changements faits durant le setup

Les fichiers suivants ont été modifiés ou créés lors du setup initial du monorepo + dev local :

| Fichier | Type | Raison |
|---|---|---|
| `.gitignore` (root) | nouveau | Exclure `appli-android/exportToHTML/` et fichiers OS |
| `DEV_GUIDE.md` | nouveau | Cette documentation |
| `serveur/.env` | nouveau | Config dev local PC (gitignored) |
| `serveur/deploy.sh` | nouveau | Script de déploiement Pi |
| `serveur/test_db_connection.py` | nouveau | Helper de debug asyncpg |
| `serveur/exec_pg.py` | modifié | Ajout `import app.models` pour que `Base.metadata` connaisse les tables |
| `serveur/requirements.txt` | modifié | `uvloop` rendu conditionnel (Windows-incompatible) |
| `serveur/start_api.sh` | modifié | Path-agnostic via `cd "$(dirname "$(readlink -f "$0")")"` |
| `serveur/app/settings.py` | modifié | Ajout `model_config = SettingsConfigDict(env_file=".env")` |
| `appli-android/app/build.gradle.kts` | modifié | `buildConfig = true` + URLs `debug` (PC) / `release` (Pi) |
| `appli-android/app/src/main/java/com/example/sportapp/utils/AppConfig.kt` | modifié | Lecture depuis `BuildConfig` au lieu de constantes hardcodées |
| `appli-android/app/src/main/res/xml/network_security_config.xml` | modifié | Ajout `<pc-lan-ip>` (PC) à la liste des IPs autorisées en HTTP |
