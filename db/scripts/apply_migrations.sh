#!/usr/bin/env bash
# Applique les migrations sur Supabase (ou tout Postgres où auth.uid() existe).
# SUPABASE_DB_URL : chaîne « Session pooler » ou directe du projet (Dashboard > Connect). Ne jamais la commiter.
set -euo pipefail
: "${SUPABASE_DB_URL:?définir SUPABASE_DB_URL}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
for f in "$ROOT"/db/migrations/*.sql; do
  echo "→ $(basename "$f")"
  psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -q -f "$f"
done
echo "Migrations appliquées."
