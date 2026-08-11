#!/bin/bash
# backup_pi.sh - Backup quotidien de la DB fittracker (Postgres Pi prod).
#
# Sortie : ~/backups/fittracker-YYYY-MM-DD.sql.gz
# Retention : 30 derniers jours (find -mtime +30 -delete)
# Source du password : DATABASE_URL dans serveur/.env (V1.2 rotation).
#
# Installation crontab (une fois sur la Pi) :
#   chmod +x ~/Applications/sport-app/serveur/backup_pi.sh
#   mkdir -p ~/backups
#   (crontab -l 2>/dev/null; echo "0 3 * * * /home/william/Applications/sport-app/serveur/backup_pi.sh >> /home/william/backups/cron.log 2>&1") | crontab -
#
# Smoke manuel :
#   ~/Applications/sport-app/serveur/backup_pi.sh
#
# Restauration :
#   gunzip < ~/backups/fittracker-2026-05-12.sql.gz | psql -U fittracker -h 127.0.0.1 fittracker

set -e

ENV_FILE="$HOME/Applications/sport-app/serveur/.env"
[ -f "$ENV_FILE" ] || { echo "ERROR: .env introuvable : $ENV_FILE"; exit 1; }

# Parse DATABASE_URL = postgresql+asyncpg://USER:PASSWORD@HOST:PORT/DB
DB_URL=$(grep -E '^DATABASE_URL=' "$ENV_FILE" | head -1 | cut -d= -f2-)
DB_USER=$(echo "$DB_URL" | sed -nE 's#.*//([^:]+):.*#\1#p')
DB_PASS=$(echo "$DB_URL" | sed -nE 's#.*//[^:]+:([^@]+)@.*#\1#p')
DB_HOST=$(echo "$DB_URL" | sed -nE 's#.*@([^:/]+).*#\1#p')
DB_NAME=$(echo "$DB_URL" | sed -nE 's#.*/([^/?]+)$#\1#p')

if [ -z "$DB_USER" ] || [ -z "$DB_PASS" ] || [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ]; then
    echo "ERROR: parse DATABASE_URL incomplet (user=$DB_USER host=$DB_HOST db=$DB_NAME)"
    exit 1
fi

BACKUP_DIR="$HOME/backups"
mkdir -p "$BACKUP_DIR"

DATE=$(date +%F)
OUTPUT="$BACKUP_DIR/fittracker-${DATE}.sql.gz"

PGPASSWORD="$DB_PASS" pg_dump -U "$DB_USER" -h "$DB_HOST" "$DB_NAME" | gzip > "$OUTPUT"

find "$BACKUP_DIR" -name "fittracker-*.sql.gz" -mtime +30 -delete

SIZE=$(du -h "$OUTPUT" | cut -f1)
echo "OK: $OUTPUT ($SIZE)"
