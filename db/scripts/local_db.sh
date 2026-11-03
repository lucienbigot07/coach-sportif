#!/usr/bin/env bash
# Démarre un Postgres 16 jetable, applique le stub Supabase + les migrations.
# Usage : db/scripts/local_db.sh [répertoire_data]   → affiche l'URL de connexion.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PGBIN="${PGBIN:-$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1)}"
DATA="${1:-${TMPDIR:-/tmp}/coach-pg}"
PORT="${PGPORT_LOCAL:-54329}"
RUNAS=()
if [ "$(id -u)" = "0" ]; then RUNAS=(runuser -u postgres --); mkdir -p "$DATA"; chown postgres "$DATA"; fi
if [ ! -f "$DATA/PG_VERSION" ]; then
  "${RUNAS[@]}" "$PGBIN/initdb" -D "$DATA" -U postgres -A trust >/dev/null
fi
"${RUNAS[@]}" "$PGBIN/pg_ctl" -D "$DATA" -o "-p $PORT -k $DATA -c listen_addresses=127.0.0.1" -l "$DATA/log" -w start >/dev/null || true
PSQL=(psql -h 127.0.0.1 -p "$PORT" -U postgres -v ON_ERROR_STOP=1 -q)
"${PSQL[@]}" -d postgres -c "drop database if exists coach" -c "create database coach"
"${PSQL[@]}" -d coach -f "$ROOT/db/tests/00_supabase_stub.sql"
"${PSQL[@]}" -d coach -c "alter database coach owner to coach_owner" -c "alter schema public owner to coach_owner" \
   -c "grant create on database coach to coach_owner"
for f in "$ROOT"/db/migrations/*.sql; do
  "${PSQL[@]}" -d coach -c "set role coach_owner" -f "$f"
done
echo "postgresql://postgres@127.0.0.1:$PORT/coach"
