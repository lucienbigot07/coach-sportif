# Recette — état au 3 novembre 2026

Légende : ✅ exécuté et réussi ici · 🟡 vérifié partiellement · ⏳ à exécuter après déploiement / compilation.
Tests : `cd mcp-server && python3 -m pytest -q` → **28 passed** (Postgres 16 réel, RLS forcé, SDK MCP officiel).

## A. Historique et affichage

| # | Scénario | État | Preuve |
|---|---|---|---|
| A1 | APK natif installé sur S25 Ultra | ⏳ | Code écrit ; compilation impossible ici (SDK Android bloqué) — voir DEPLOIEMENT §4 |
| A2 | Mardi 3/11 : natation + Dos/Biceps prévus, foot `pending_confirmation` 19:30 non confirmé | ✅ (données) | `test_A2_*` ; affichage Android 🟡 (fixtures décodées par les modèles) |
| A3 | Lundi 2/11 réalisé : 5 km 24:02, 4:48/km, FC 166, RPE 6 ; prévu « facile » vs réalisé « soutenu » ; quadri 7 exos / 21 séries | ✅ | `test_A3_*` |
| A4 | Fentes 44 kg × 32/33/33 **pas**, objectif 33×3 non atteint | ✅ | `test_A4_*` |
| A5 | Tirage vertical 66 kg ≠ 120 kg, non fusionnés | ✅ | `test_A2_*`, `test_scenario_B_*` |
| A6 | RDL 30 kg par main ×10×3, ×12 seulement visé, pas de record inventé | ✅ | `test_A6_*` |
| A7 | Trail 7/11 envisagé, match 8/11 prévu | ✅ | `test_A7_*` |
| A8 | Jour sans séance → « Aucune séance publiée » | ✅ | `test_A8_*` |

## B. Synchronisation Claude ↔ Android

| # | Scénario | État | Preuve |
|---|---|---|---|
| B1 | MCP HTTPS accessible depuis Claude, authentifié, outils utilisés en vraie conversation | 🟡 | OAuth 2.1 complet (DCR, PKCE, code unique, refresh rotatif `invalid_grant`) et appels d'outils sur vrai serveur HTTP : `test_http_oauth` ✅. Conversation claude.ai réelle : ⏳ après déploiement |
| B2 | « 10 → 8 km mercredi, je valide » → `revise_session`, v2, ancienne valeur conservée | ✅ | `test_scenario_A_*`, `test_B2_*` |
| B3 | « Peut-être 5 km jeudi ? » ne publie rien | ✅ | `approval_required` ; brouillon invisible côté app |
| B4 | Séries saisies → Claude les relit | ✅ (API) | `test_scenario_B_*` (même RPC que l'app) ; écran Android ⏳ |
| B5 | Connecteur en erreur → « non synchronisé » | ✅ | erreurs outil `synchronized:false` (`test_B3_B5_*`) ; consigne dans les instructions du coach |
| B6 | Replanifier n'écrase pas le réalisé | ✅ | `test_B6_*`, `test_publication_semaine_complete_preserve_le_realise` |

## C. Données, hors ligne, sécurité

| # | Scénario | État | Preuve |
|---|---|---|---|
| C1 | Hors ligne → file → une seule insertion | 🟡 | idempotence serveur ✅ ; file Room + WorkManager écrite, ⏳ test sur téléphone |
| C2 | Même `idempotency_key` → pas de doublon | ✅ | `test_B2_*`, `test_B4_*`, `test_scenario_A_*` |
| C3 | Version obsolète → 409 avec état courant | ✅ | `test_C3_*` |
| C4 | Compte B ne lit/écrit rien de A ; pas d'écriture directe ni anonyme | ✅ | `test_C4_*` (RPC + SELECT brut sous RLS) |
| C5 | Aucun secret dans APK / Git | ✅ (revue) | seule la clé publique Supabase est compilée ; `local.properties` et `.env` ignorés |
| C6 | Export JSON + suppression | ✅ (API) | `test_C6_*` ; boutons Android ⏳ |
| C7 | 7, 8, 21 novembre aux bons jours | ✅ | `test_C7_*` |
| C8 | Inconnus = NULL, jamais 0 | ✅ | `test_C8_*` (4,48 km du 22/09 sans durée, poids actuel inconnu) |

## Livrables

- ✅ Code source, migrations, tests, scripts (`db/`, `mcp-server/`, `android/`, `seed/`)
- ⏳ APK debug : à compiler (procédure exacte dans DEPLOIEMENT §4)
- ✅ Guide backend / MCP / connecteur Claude (DEPLOIEMENT)
- ⏳ Captures des écrans et preuve des flux en conditions réelles : après déploiement
- Non réalisé (phase 2) : import Garmin, notifications, Play Store, Realtime, iOS
