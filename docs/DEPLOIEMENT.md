# Déploiement — de zéro à « Claude publie, l'app affiche »

> **Parcours 100 % téléphone** (celui suivi en conversation) :
> 1. Supabase : projet + utilisateur ; coller `db/supabase_1_installation.sql` puis `db/supabase_2_import_historique.sql` dans le SQL Editor.
> 2. GitHub : dépôt privé ; GitHub Actions compile l'APK (`.github/workflows/android.yml`) → artefact `coach-sportif-apk` à télécharger sur le téléphone.
>    Variables d'Actions (publiques) : `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
> 3. Render : *New → Blueprint* sur le dépôt (`render.yaml`) → renseigner `COACH_PUBLIC_URL`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `COACH_ALLOWED_EMAILS` ; `COACH_TOKEN_ENC_KEY` est généré par Render.
>    Offre gratuite : le service s'endort après inactivité, le premier appel peut prendre ~1 min (Claude peut échouer une fois : relancer). Le serveur est sans état : un redémarrage ne déconnecte pas Claude.
> 4. Claude : connecteur personnalisé → `https://<service>.onrender.com/mcp`.
>
> Le parcours « ordinateur » ci-dessous reste valable.

Compte ~1 h. Les étapes 1 à 3 demandent **tes** comptes ; aucune valeur secrète ne va dans Git.

## 1. Supabase (base + authentification)

1. Crée un projet sur supabase.com, **région UE** (Frankfurt, Paris si proposée). Vérifie les tarifs et limites du plan gratuit sur leur site (un projet gratuit inactif peut être mis en pause).
2. *Authentication → Users → Add user* : crée ton utilisateur (e-mail + mot de passe fort). Désactive les inscriptions publiques (*Authentication → Sign In / Providers → Allow new users to sign up* : off).
3. Applique les migrations (chaîne de connexion dans *Connect*, ne la commite jamais) :
   ```bash
   SUPABASE_DB_URL='postgresql://…' db/scripts/apply_migrations.sh
   ```
   (Alternative : coller les 4 fichiers `db/migrations/*.sql` dans l'éditeur SQL, dans l'ordre.)
4. Importe tes données initiales (une seule fois) :
   ```bash
   SUPABASE_URL=https://<projet>.supabase.co SUPABASE_ANON_KEY=<clé publishable/anon> \
     python3 seed/import_seed.py ton@email.fr
   ```
   Réponse attendue : `{"success": true, "variants": 31, "completed_activities": 15, "planned_activities": 11, …}`.

La clé *publishable/anon* est publique par conception : la sécurité repose sur le RLS. **N'utilise jamais la clé `service_role`** ni dans l'app ni dans le serveur MCP.

## 2. Serveur MCP (Internet, HTTPS)

Claude se connecte depuis le cloud Anthropic (sorties depuis `160.79.104.0/21`) : le serveur doit être public en HTTPS. Exemple avec Fly.io (région Paris `cdg`) ; tout hébergeur de conteneurs avec volume persistant convient (Render, Railway, Scaleway…). Vérifie leurs tarifs avant de lancer une ressource payante.

```bash
cd mcp-server
cp fly.toml.example fly.toml           # ajuste le nom de l'app
fly launch --no-deploy --copy-config
fly secrets set \
  COACH_PUBLIC_URL=https://<app>.fly.dev \
  SUPABASE_URL=https://<projet>.supabase.co \
  SUPABASE_ANON_KEY=<clé publishable/anon> \
  COACH_ALLOWED_EMAILS=ton@email.fr \
  COACH_TOKEN_ENC_KEY=$(python3 -c "from cryptography.fernet import Fernet;print(Fernet.generate_key().decode())")
fly deploy
curl https://<app>.fly.dev/healthz        # {"ok": true}
curl -i https://<app>.fly.dev/mcp          # 401 + WWW-Authenticate: Bearer resource_metadata=…
```

Variables : voir `mcp-server/.env.example`. Le serveur est sans état (jetons scellés) : ni volume ni base OAuth.

## 3. Ajouter le connecteur dans Claude

1. Dans Claude (web ou desktop) : *Paramètres → Connecteurs → Ajouter un connecteur personnalisé*. Nom : « Coach Sportif ». URL : `https://<app>.fly.dev/mcp` (avec `/mcp`). Laisse les champs OAuth client vides : Claude s'enregistre seul (DCR).
2. *Connecter* → une page « Connexion Coach Sportif » s'ouvre → ton e-mail/mot de passe Supabase → retour dans Claude.
3. Dans une conversation, active le connecteur (menu des outils) et teste : « Quel est mon programme de demain ? » → Claude doit appeler `get_day_plan`.
4. Mets `docs/INSTRUCTIONS_COACH_CLAUDE.md` dans les instructions de ton projet Claude « Coach sportif ».

Références : [Authentification des connecteurs](https://claude.com/docs/connectors/building/authentication), [Ajouter un connecteur personnalisé](https://claude.com/docs/connectors/custom/add-unlisted). La disponibilité des connecteurs personnalisés dépend de ton offre Claude : vérifie-la sur support.claude.com.

## 4. Application Android (sur ton poste)

Prérequis : Android Studio récent (JDK 17+ fourni), SDK Android 35.

1. Ouvre le dossier `android/` dans Android Studio. Si l'IDE propose une mise à jour d'AGP / Kotlin / dépendances, accepte-la (versions figées dans `gradle/libs.versions.toml`).
2. Copie `android/local.properties.example` en `android/local.properties` et renseigne `coach.supabaseUrl` et `coach.supabaseAnonKey`.
3. Tests du contrat JSON : `./gradlew :app:testDebugUnitTest` (décode les vraies réponses du backend présentes dans `app/src/test/resources/fixtures`).
4. APK : `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
5. Installation sur le S25 Ultra : active *Options pour les développeurs → Débogage USB*, puis `adb install -r app/build/outputs/apk/debug/app-debug.apk` (ou *Run* depuis Android Studio).

⚠️ Le code Android n'a **pas** pu être compilé dans l'environnement où il a été écrit (accès à Google/Maven bloqué). Le contrat JSON a été vérifié statiquement contre les réponses réelles du backend (`android/tools/check_models.py`) et la syntaxe Kotlin a passé l'analyse du compilateur, mais attends-toi à quelques corrections mineures à la première compilation. Envoie-moi les erreurs de build : je les corrige.

## 5. Vérifications après déploiement (preuves de recette)

- [ ] Claude : « Mercredi, remplace les 10 km par 8 km faciles, je valide » → réponse outil `success: true, version: 2` → l'app (glisser pour rafraîchir) affiche 8 km, « Modifié par le coach », lundi inchangé.
- [ ] App : terminer Dos/Biceps avec 3 séries de tirage vertical (machine 66 kg) → Claude : « Qu'ai-je fait aujourd'hui ? » → `get_recent_completed_sessions` renvoie les séries.
- [ ] Mode avion pendant « Terminer » → message « Enregistré sur le téléphone » → réseau rétabli → une seule séance côté Claude.
- [ ] Déconnecte le connecteur dans Claude → Claude doit dire que rien n'est publié.

## 6. RGPD / données

Données hébergées dans ton projet Supabase (région choisie) ; le serveur MCP ne stocke que des jetons (hachés) et ta session Supabase (chiffrée). Export complet : *Réglages → Exporter*. Suppression des données : *Réglages → Supprimer* ; suppression du compte d'authentification : *Supabase → Authentication → Users → Delete* (les lignes restantes partent en cascade).
