# Architecture et décisions

## 1. Composants

| Composant | Technologie | Pourquoi |
|---|---|---|
| Base | Supabase (Postgres + Auth + PostgREST), région UE | RLS natif, Auth prête, API REST/RPC sans backend maison, hébergement UE possible |
| Logique métier | Fonctions PL/pgSQL `SECURITY DEFINER` (`db/migrations/0003_functions.sql`) | Une seule implémentation des règles pour l'app ET le MCP ; transactions atomiques ; testable sur un Postgres local |
| Connecteur | Serveur MCP Python (SDK officiel `mcp` 2.3), transport HTTP streamable sans état | Format attendu par les connecteurs personnalisés Claude ; outils typés (Pydantic → JSON Schema) |
| Auth du connecteur | OAuth 2.1 propre au serveur (DCR + PKCE S256), connexion par ton compte Supabase | Claude obtient un jeton opaque ; le serveur appelle Supabase **avec ton JWT** → le RLS s'applique aussi à Claude. Aucune clé `service_role` |
| Client | Android natif Kotlin, Jetpack Compose (Material 3), Room, WorkManager, OkHttp, kotlinx.serialization | Exigence « vraie app native » ; cache et file hors ligne |

## 2. Modèle de données (résumé)

`athlete_profiles`, `goals`, `events`, `exercise_variants` (une ligne **par machine/variante** : `vertical_row_machine_66` ≠ `vertical_row_machine_120`), `exercise_references` (références historiques + objectifs **non validés**), `training_plans` (draft / validated / superseded, version), `planned_activities` (prescription, statut, version), `exercise_targets`, `completed_activities` + `completed_sets` + `endurance_metrics` (réalisé, provenance), `benchmarks`, `wellness_checks`, `device_metrics` (Garmin = estimation datée), `change_log` (audit en ajout seul), `sync_operations` (idempotence).

Conventions : `NULL` = inconnu (jamais 0) ; dates locales `date` (Europe/Paris) ; instants `timestamptz` ; `exercise_variants.performance_unit` distingue répétitions, **pas**, secondes ; `load_basis` distingue total / par main / machine.

## 3. Règles garanties par la base (testées)

| Règle | Mécanisme |
|---|---|
| Une séance prévue n'est jamais « réalisée » sans résultat | `completed` n'est atteignable que via `complete_activity` ; `revise_session` et `mark_event_confirmation` le refusent |
| Pas de publication sans accord | `user_approval_reference` obligatoire (≥ 3 caractères, citation de l'utilisateur) ; un brouillon n'apparaît pas dans l'app |
| Pas d'écrasement silencieux | version attendue sur plan / activité / résultat → `409 version_conflict` avec l'état courant |
| Le réalisé est protégé | `completed_activity_protected` ; une nouvelle semaine publiée reporte les séances terminées |
| Pas de doublon (retry, mode avion) | `sync_operations(user_id, idempotency_key)` : rejeu identique → même réponse ; clé réutilisée pour autre chose → 409 |
| Traçabilité | `change_log` : ancien/nouveau, raison, accord cité, acteur (`claude_mcp`, `android_app`, `import`) |
| Isolation des comptes | RLS forcé sur toutes les tables + filtre explicite `auth.uid()` dans chaque fonction ; aucune écriture directe sur les tables versionnées |

Erreurs : `errcode 'PTnnn'` → PostgREST renvoie HTTP `nnn` ; `message` = code machine, `detail` = JSON.

## 4. Flux

**A — Claude → app.** `get_training_context` / `list_week_plan` → discussion → accord → `revise_session` (une séance) ou `create_plan_draft` + `publish_validated_plan` (semaine) → réponse `success:true, version` → Claude annonce « synchronisé » → l'app relit `get_day_plan` à l'ouverture (ou glisser pour rafraîchir) et affiche version + « modifié par le coach ».

**B — app → Claude.** Saisie S1/S2/S3 (brouillon Room à chaque frappe) → « Terminer » → opération mise en file avec une clé d'idempotence créée **une fois** → envoi immédiat ou WorkManager au retour du réseau → `complete_activity` → `completed` + `app_logged` → Claude lit `get_recent_completed_sessions` / `get_exercise_history`.

**C — corrections.** `correct_completed_activity` (version, raison, ancien état journalisé).

## 5. Outils MCP

Lecture : `get_profile`, `get_training_context`, `list_week_plan`, `get_day_plan`, `get_exercise_history`, `get_recent_completed_sessions`, `get_week_summary`.
Écriture : `create_plan_draft`, `publish_validated_plan`, `revise_session`, `mark_event_confirmation`, `record_user_reported_session` (ajout au contrat : enregistrer des résultats que tu donnes en conversation, provenance `user_report`).
Aucun outil de suppression d'historique, de lecture d'un autre compte ni de réécriture d'un résultat sans raison.

## 6. Choix assumés / limites

- Mise à jour de l'app à l'ouverture et au rafraîchissement manuel (pas de push temps réel au MVP ; Supabase Realtime possible ensuite).
- Le serveur MCP conserve les jetons OAuth (hachés) et ta session Supabase (chiffrée Fernet) dans un SQLite sur volume persistant : une seule instance suffit pour un usage personnel.
- Connexion Android par e-mail/mot de passe Supabase (pas encore de Google Sign-In / passkeys).
- Import Garmin, notifications, statistiques avancées : phase 2.
