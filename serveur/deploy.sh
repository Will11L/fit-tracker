#!/bin/bash
# deploy.sh - Met a jour le serveur sur la Raspberry Pi
# Usage : ./deploy.sh
#
# A lancer depuis serveur/ : ~/Applications/sport-app/serveur/deploy.sh
# Le script :
#   1. git pull (recupere les derniers changements)
#   2. installe les nouvelles dependances Python si requirements.txt a change
#   3. applique les migrations Alembic (alembic upgrade head)
#   4. redemarre le service systemd

set -e  # stop le script si une commande echoue

# Se place dans le dossier du script (serveur/) peu importe d'ou il est appele
cd "$(dirname "$(readlink -f "$0")")"

echo "=== 1. Pull du monorepo depuis GitHub ==="
cd ..
git pull
cd serveur

echo ""
echo "=== 2. Activation du venv ==="
source env_api/bin/activate

echo ""
echo "=== 3. Installation des dependances (skip si rien n'a change) ==="
pip install -r requirements.txt

echo ""
echo "=== 4. Migrations Alembic (DB schema) ==="
# Ajoute ici depuis V8.2 (1ere migration prod). Idempotent : si on est deja a head,
# Alembic skip. Si une migration touche un trigger SQL (cf. politique CLAUDE.md
# §15), c'est elle qui se charge de reload notify_row_change() (ex: f72_reload_fn).
echo "Avant : $(alembic current 2>/dev/null | tail -1)"
alembic upgrade head
echo "Apres : $(alembic current 2>/dev/null | tail -1)"

echo ""
echo "=== 5. Build du client web Angular (servi en statique par FastAPI) ==="
# Requiert Node >= 22.22.3 (Angular 22), installe en systeme via NodeSource.
# npm ci installe TOUTES les deps (devDependencies incluses, requises pour le
# build Angular). Ne PAS poser NODE_ENV=production avant ce bloc (sinon les
# devDependencies sont skippees et le build echoue). cwd ici = serveur/.
cd ../appli-web
npm ci
npm run build   # -> appli-web/dist/appli-web/browser/
cd ../serveur

echo ""
echo "=== 6. Redemarrage du service systemd ==="
sudo systemctl restart sportapi.service

echo ""
echo "=== 7. Statut du service ==="
sudo systemctl status sportapi.service --no-pager | head -10

echo ""
echo "✅ Deploiement termine."
