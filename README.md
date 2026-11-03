# Coach Sportif

Application Android native + base partagée + **connecteur MCP distant** pour que Claude (ton coach, dans une conversation ordinaire) publie les séances validées et relise ce que tu as réellement fait.

```
 Conversation Claude ──(MCP, OAuth 2.1)──▶ coach-mcp ──(JWT utilisateur)──▶ Supabase Postgres (RLS)
                                                                                  ▲
                                   Application Android (Kotlin/Compose) ──(JWT)───┘
```

- **Claude décide**, l'application **affiche et enregistre**. Pas de second coach IA dans l'app.
- `planned` / `pending_confirmation` / `completed` sont strictement séparés ; une séance ne devient `completed` que par un résultat déclaré.
- Toute la logique métier (versions, idempotence, audit, protection du réalisé) vit dans des fonctions SQL partagées par l'app et le MCP.

## Contenu

| Dossier | Rôle | État |
|---|---|---|
| `db/migrations/` | Schéma, RLS, fonctions RPC, import du seed | ✅ appliqué et testé sur Postgres 16 |
| `mcp-server/` | Serveur MCP distant (Python, SDK MCP 2.3, HTTP streamable, OAuth 2.1 + PKCE + DCR) | ✅ 28 tests, flux A et B de bout en bout |
| `android/` | Application native Kotlin + Jetpack Compose + Room + WorkManager | ✍️ écrite, **pas encore compilée** (SDK Android inaccessible ici) |
| `seed/` | Données initiales du dossier + script d'import | ✅ import testé |
| `docs/` | Architecture, déploiement, recette, instructions du coach | — |

## Démarrage rapide (développement)

```bash
# Base locale jetable (Postgres 16) + migrations + tests MCP
cd mcp-server && pip install -r requirements-dev.txt && python3 -m pytest -q

# Serveur MCP local sans OAuth (127.0.0.1 uniquement)
COACH_DEV_MODE=1 COACH_DEV_USER_ID=<uuid> COACH_PG_DSN=postgresql://postgres@127.0.0.1:54329/coach \
  uvicorn coach_mcp.server:app --port 8000
```

Déploiement réel (Supabase, hébergement du MCP, connecteur Claude, APK) : **[docs/DEPLOIEMENT.md](docs/DEPLOIEMENT.md)**.
État de chaque scénario de recette : **[docs/RECETTE.md](docs/RECETTE.md)**.
