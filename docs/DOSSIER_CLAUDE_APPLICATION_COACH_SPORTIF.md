# DOSSIER COMPLET À TRANSMETTRE À CLAUDE — Application Android Coach Sportif

**Généré le 3 novembre 2026.** Lire les sections dans l’ordre. Le fichier JSON importable est également joint séparément dans le ZIP.



---

## ANNEXE — README

# 📦 Coach Sportif — Dossier de transfert pour Claude

**Préparé le 3 novembre 2026.** Ce paquet donne à Claude Code la définition du produit Android à construire et transmet les données sportives réellement documentées pour reprendre le coaching.

## Fichiers et ordre de lecture

1. **`02_MESSAGE_A_ENVOYER_A_CLAUDE_CODE.md`** : **copier-coller ce texte dans Claude Code** en joignant tous les fichiers du dossier. Demande explicitement de coder le projet, pas seulement de faire un plan.
2. **`01_CAHIER_DES_CHARGES_APPLICATION.md`** : cahier des charges détaillé Android natif, backend, synchronisation Claude ↔ application, sécurité, modèle métier et architecture.
3. **`03_DONNEES_INITIALES.json`** : données structurées initiales à importer : profil, objectifs, Garmin, calendrier, références par exercice/variante, résultats réels, planning du 2 au 8 novembre.
4. **`DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md`** : **source de vérité historique** exhaustive 480 lignes ; lire en entier avant d'interpréter un résultat.
5. **`INSTRUCTIONS_PROJET_COACH_SPORTIF.md`** : instructions du coaching d'origine, conservées telles quelles.
6. **`04_INSTRUCTIONS_COACH_CLAUDE.md`** : instructions additionnelles pour que le Claude « coach » utilise le MCP sans confondre séances prévues et réalisées.
7. **`05_SCENARIOS_RECETTE.md`** : liste de tests à faire avant de déclarer l'application terminée.

## Fonctionnement visé

Dans une conversation Claude tu dis : **« Demain, valide 8 km faciles et Pecs/Triceps. »** Claude appelle le serveur MCP distant pour enregistrer **le programme validé**. L'app Android native affiche automatiquement le programme à l'ouverture. Tu renseignes les séries, allures, FC et RPE sur ton Samsung ; l'application sauvegarde les résultats. Lors de la prochaine discussion, Claude lit **ces résultats**, te conseille et ajuste éventuellement le programme, après ton accord.

**Point essentiel :** ni ChatGPT, ni Claude ne donnent automatiquement accès à toute l'historique d'une autre messagerie. La synchronisation doit être construite via backend + connecteur MCP distant + appels explicites à ses outils. Ne rien annoncer de « synchronisé » si ce n'est pas confirmé par le serveur.

## Passage de l'ancien suivi

Au dernier état documenté : mardi **3 novembre 2026**, aucune séance du mardi n'était effectuée ; **lundi 2** a été réellement effectué (5 km en 24:02 et quadriceps 21 séries). La semaine 2–8 novembre est **validée comme programme**, mais le trail samedi est envisagé seulement ; football mardi et vendredi non confirmé. Ne pas transformer ces intentions en performances.

## Ce qui reste à fournir/configurer pour une vraie app connectée

Pour un déploiement réel, l'utilisateur ou Claude Code devra créer/utiliser le compte de base de données, le compte d'hébergement du MCP, configurer le connecteur dans Claude et compiler l'APK dans un environnement Android compatible. Il faudra autoriser l'app et son serveur à accéder aux seules données du propriétaire. Le présent paquet fournit un **cahier des charges et des données**, pas un APK ni une intégration déjà installée.


---

## ANNEXE — MESSAGE DE MISSION POUR CLAUDE CODE

# MESSAGE À COLLER DANS CLAUDE CODE — réalisation de l'application

Je veux que tu **développes réellement une application Android native de suivi sportif** appelée provisoirement **Coach Sportif**, avec un backend et surtout une **synchronisation bidirectionnelle avec mes conversations Claude par connecteur MCP distant**. Je suis développeur informatique, donc privilégie une architecture claire, versionnée, maintenable, avec des scripts de build/reproductibilité, mais prends en charge le maximum de mise en œuvre.

## Documents fournis — À LIRE INTÉGRALEMENT AVANT DE CODER

1. `01_CAHIER_DES_CHARGES_APPLICATION.md` — besoins, architecture, parcours, modèle, protocole de synchro, réception.
2. `03_DONNEES_INITIALES.json` — seed documenté, plans, exercices/variantes, performances, Garmin, objectifs.
3. `DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md` — source primaire exhaustive des données ; prioritaire en cas de conflit.
4. `INSTRUCTIONS_PROJET_COACH_SPORTIF.md` — attentes et principes du coach.
5. `04_INSTRUCTIONS_COACH_CLAUDE.md` — comportement de Claude comme coach via MCP.
6. `05_SCENARIOS_RECETTE.md` — tests d'acceptation.

## Ce que j'attends comme réalisation, pas seulement comme texte

- **Une VRAIE application Android** (Kotlin + Jetpack Compose recommandés), compilable/installable sur un Samsung Galaxy S25 Ultra, avec un design moderne, fluide, français.
- Un écran **Aujourd'hui** automatiquement prérempli avec le **programme validé décidé dans une conversation Claude** : activités, exercices, variantes et machines, dernières charges et S1/S2/S3.
- Un écran **Semaine** rendu lundi→dimanche dans le style visuel du planning joint (emojis, date, objectifs, prévu/réalisé/à confirmer, total hebdomadaire prévu/réel séparés).
- Saisie très rapide des séries, kilométrages, durées, FC, RPE, douleur et ressenti ; historique et courbes.
- Backend PostgreSQL sécurisé, authentification, migrations, journal d'audit et cache offline.
- **Serveur MCP distant sécurisé réellement déployable**, doté d'outils pour lire les résultats et publier/ajuster les programmes. La conversation Claude doit pouvoir l'invoquer ; je ne veux PAS copier-coller les séances depuis Claude vers l'application.
- La saisie d'une séance dans Android doit être récupérable dans Claude au moyen d'un appel d'outil MCP lors de la prochaine conversation.
- **Aucune invention de séances ni de charges**. Les données sources s'arrêtent au matin du mardi 3 novembre 2026 ; seule la journée du lundi 2 est confirmée réalisée dans cette semaine.

## Ordre recommandé d'implémentation

1. Inspecte les documents, crée la structure de dépôt, `README`, architecture détaillée, modèle et migrations.
2. Construis l'APK natif + client Android (Aujourd'hui, semaine, saisie musculation/endurance, historique) sur données persistées et seed importé.
3. Construis et intègre le connecteur MCP distant, puis teste les deux flux *Claude → app* et *app → Claude*.
4. Ajoute sécurité, mode offline, conflits et tests ; fournis un APK installable si l'outillage Android est présent, sinon explique précisément comment le compiler en local et montre les tests réussis.
5. Donne les étapes de déploiement et de connexion du connecteur dans Claude, les variables d'environnement et coûts potentiels, en isolant tous les secrets.

## Deux scénarios de démonstration essentiels

**A. Depuis Claude :** « Pour mercredi 4 novembre, on remplace les 10 km faciles par 8 km faciles. Je valide. » → l'outil MCP met à jour l'activité `20261104_run` avec version + audit → l'application affiche 8 km après synchronisation, sans modifier les résultats de lundi.

**B. Depuis Android :** je réalise Dos/Biceps mardi 3 novembre, saisis 3 séries de tirage vertical sur la variante **66 kg / machine du 7 octobre** et termine la séance → Claude interroge les résultats enregistrés, les compare à la référence de **cette machine** et ajuste le prochain objectif. Ne pas confondre la variante d'une autre machine à 120 kg.

## Contraintes

- N'utilise PAS une PWA/webview comme substitut à l'APK natif.
- Ne prétends PAS que le serveur MCP, l'hébergement, l'auth ou la synchro sont opérationnels s'ils ne l'ont pas été concrètement testés.
- Ne crée PAS un coach IA parallèle dans Android : Claude décide, l'application affiche et enregistre.
- Les séances `planned`, `pending_confirmation`, `completed` restent strictement différentes.
- Ne modifie pas arbitrairement les historiques ni l'objectif 10 km 45 minutes.
- Les données santé restent privées ; pas de clé dans l'APK ni dans les fichiers Git.
- Évite les questions triviales : avance avec des choix techniques raisonnables et signale uniquement les vrais blocages (hébergement, secrets, compte cloud, accès développeur, connexion Claude).

**Commence par résumer les décisions d'architecture, puis implémente réellement l'étape 1 et le MVP. Ne t'arrête pas à une maquette ou à un plan.**


---

## ANNEXE — CAHIER DES CHARGES

# COACH SPORTIF — Cahier des charges complet de l'application Android et de la synchronisation Claude

**Version :** 1.0 — 3 novembre 2026  
**Destinataire :** Claude Code, pour réalisation ; Claude (conversation) pour coaching et outil MCP.  
**Source des données utilisateur :** dossier transmis, mis à jour au matin du 3 novembre 2026, et échanges suivants dans la même conversation ; ne pas inventer de nouvelles séances réalisées.  
**Statut de ce document :** cahier des charges de conception, **pas** preuve d'application déjà développée, de serveur MCP installé ou de connexion activée.

## 0. Vision centrale — contrainte non négociable

L'utilisateur veut une **véritable application Android installable** sur son Samsung Galaxy S25 Ultra, PAS une simple PWA comme produit final. Il veut **continuer à discuter naturellement avec Claude**, qui devient son coach sportif. Lorsque les deux ont **validé une séance, une semaine ou une modification** dans la conversation, Claude doit pouvoir la publier dans une **base commune via un connecteur MCP distant sécurisé**. L'application Android lit cette base et **présente immédiatement le programme adopté du jour**, sans double saisie. Pendant l'entraînement, l'utilisateur saisit les séries, charges, distances, temps, FC et ressenti ; l'application les persiste. À sa prochaine conversation, Claude **lit les séances effectivement réalisées**, compare aux références validées et propose la suite. **Les conversations ne sont pas la base de données** : celle-ci est la source de vérité pour les séances structurées. Aucun composant ne doit prétendre pouvoir lire magiquement tous les messages d'une conversation Claude ou ChatGPT.

**Exemple réel :** mardi 3 novembre 2026, la séance du jour validée est 500–600 m de natation facile + Dos/Biceps (charges et exercices historiques) + football seulement si confirmé vers 19h30. Le lundi 2 novembre est terminé : course 5 km en 24:02 (4:48/km, FC 166, RPE 6/10) + quadriceps 7 exercices/21 séries. L'application doit afficher lundi comme **réalisé**, mardi comme **prévu/non encore réalisé** ; ne pas transformer automatiquement football « à confirmer » en « réalisé ».

## 1. Produits et périmètre

### 1.1 Client Android — produit principal
- **Android natif Kotlin + Jetpack Compose**, UI Material 3, orientation téléphone prioritaire, français et dates Europe/Paris. Génération d'un **APK de développement installable** pour tester, puis AAB si publication Play Store ultérieure. Choisir une version d'Android SDK stable à la date d'implémentation et la documenter.
- Réseau via HTTPS, authentification individuelle et API, cache local Room, possibilité de saisir hors connexion (synchronisation différée et indication visible).
- Aucun token Claude, clé fournisseur ou secret de service dans l'APK.

### 1.2 Backend et stockage
- **PostgreSQL hébergé** (par exemple Supabase avec Auth, politiques RLS, API sécurisée ; autre backend justifié accepté) et endpoints exposant uniquement les données du compte connecté.
- Journal des changements et **historique des versions** des programmes. Une seule source de vérité. Sauvegarde/export des données, suppression de compte et des données associées.
- Environnement **développement** séparé de la **production** ; migrations versionnées et tests de sécurité.

### 1.3 Connecteur Claude (essentiel, pas optionnel)
- Créer un serveur **MCP distant HTTPS** avec outils typés de lecture/écriture du planning, des exercices et des performances. Compatible avec les connecteurs personnalisés Claude accessibles depuis une conversation ordinaire (tester explicitement depuis claude.ai ; Claude Code peut aussi servir au développement).
- Protéger par OAuth/connexion du compte, permissions minimales et contrôle du propriétaire de chaque ressource. Le serveur est accessible depuis le cloud Anthropic, pas uniquement `localhost` ou LAN.
- Les outils doivent renvoyer des identifiants, versions et statuts de succès/erreur. Claude annonce la synchronisation **seulement après réception d'une confirmation serveur**.
- Pas d'import autonome de l'historique complet des conversations d'OpenAI/Claude : importer seulement les pièces fournies et les décisions **structurées** enregistrées par les outils.
- Important : **Claude Code** = outil pour construire le logiciel ; **Claude dans une conversation** = coach qui invoque le connecteur MCP distant ; **application Android** = client qui affiche et enregistre la séance.

## 2. Écrans et parcours

### A — Accueil / Aujourd'hui (écran par défaut)
- Jour + date locale, statut météo non nécessaire ; éventuel état forme/douleurs si renseigné.
- Sections très lisibles, avec emojis/illustrations sobres : **🏊 Natation 500–600 m**, **💪 Dos/Biceps**, **⚽ Football (à confirmer)**.
- Cartes distinctes par activité avec statut : « Proposé / Validé / À faire / En cours / Terminé / Annulé / Ignoré / Confirmation en attente ».
- Boutons : **Commencer**, **Voir le détail**, **Terminer**, **Signaler annulation**, **Donner mon ressenti**.
- Afficher la **date et la version de dernière synchronisation** (et une éventuelle erreur) ; éviter l'impression qu'une séance jamais publiée est actualisée.
- Si aucune séance validée : afficher « Aucune séance publiée pour aujourd'hui » ; **ne pas générer une séance fictive**.

### B — Planning / semaine et calendrier
- Rendu identique au format que l'utilisateur apprécie dans ChatGPT : *du lundi au dimanche*, jour + chiffre, emoji sport, descriptif, statut, consignes (allure, RPE, terrain, précautions), agrégats en bas.
- Distinguer clairement **réalisé** et **prévu** ; le total prévisionnel doit être identifié comme tel.
- Changements publiés dans Claude reflétés dans l'application après synchronisation, avec indicateur « modifié par le coach le… » et possibilité de voir ce qui a changé.
- Plusieurs activités dans un même jour ; ordre facultatif quand aucun horaire confirmé.

### C — Musculation / saisie immédiate
- Séance découpée en exercices ordonnés ; chaque exercice affiche sa **variante précise, machine/localisation**, les **3 dernières séries réellement validées**, un objectif explicite (ex. 🔴 RDL haltères, 30 kg PAR MAIN × 12 × 3 visé ; pas acquis), et l'effort cible (1–2 répétitions en réserve ou version allégée).
- S1/S2/S3 : poids, répétitions **ou** pas/secondes/distance, RIR facultatif, bouton cocher. Mode **échauffement** distinct des séries de travail ; nombre de séries variable si séance allégée du jeudi (2 au lieu de 3).
- Ne jamais convertir une valeur `44 kg × 33 pas` en `33 reps` ni confondre les appareils.
- Unité kg, distinction « par main » / « total » / « charge machine » ; variation de charge dans chaque série.
- L'exercice peut être ajouté/modifié manuellement ; toute valeur réellement saisie est attribuée à une séance terminée et datée, non à un objectif.

### D — Course / natation / football / HYROX
- Course : distance, temps, allure calculée, D+, FC moyenne/max si renseignées, RPE 1–10, type d'effort (facile/tempo/fractionné/course), surface, marge ressentie et notes. **Ne pas déduire « facile » d'un simple RPE faible**.
- Natation : mètres, durée, FC éventuelle, type, bassin, ressenti.
- Football : entraînement ou match, distance, temps de jeu, FC, accélérations ressenties, douleur, possibilité d'activité très intense même sans « course » structurée.
- HYROX : stations et charges/temps/répétitions/longueur ; ne jamais assimiler 120 kg sled TKL à une performance normalisée compétition sans contexte.
- Import manuel Garmin (CSV/GPX/FIT, selon faisabilité) **phase 2** ; Garmin Connect direct seulement après vérification d'un accès/API autorisé ; ne pas promettre d'intégration Garmin instantanée.

### E — Progrès & historiques
- Courbes d'allure 5/10 km, volume course hebdomadaire, temps en activité, RPE, FC, benchmarks, records personnels.
- Suivi exercice par variante/machine et trois séries ; comparaison avec **les séances terminées seulement** et contexte (séance volontairement allégée, machine différente).
- Calendrier d'événements : courses prévues/inscriptions confirmées, priorité 10 km du Maine Libre le 21 novembre 2026.
- Comparer la course 5 km du 02/11 **24:02 / FC166** au 30/09 25:24 / FC164 avec contexte de dénivelé ; ne pas traiter Garmin seuil 4:46 comme vérité physiologique absolue.

### F — Bilan & conversation
- Mini QCM de récupération après séance importante (2–4 questions ciblées, pas systématiques) : difficulté, courbatures/douleurs, capacité à prolonger, sommeil/énergie.
- Bouton « Discuter du programme dans Claude » ouvre Claude ; **ne pas prétendre qu'il transmet le contexte automatiquement si ce n'est pas implanté**. Le connecteur peut fournir `get_training_context()` dans la conversation.
- L'application ne doit pas créer une deuxième IA coach qui donne des prescriptions contradictoires par défaut. Claude est le coach principal.

### G — Paramètres
- Profil, unités, fuseau Europe/Paris, rappel et notifications locales, sauvegarde, export JSON/CSV, gestion du compte, confidentialité et suppression.

## 3. Règles métier critiques

1. **Statut et preuve :** `planned`/`pending_confirmation`/`completed` sont différents. Jamais `completed` sans résultat déclaré ou validation expresse de l'utilisateur. Un entraînement football non confirmé ne constitue même pas un rendez-vous garanti.
2. **Source :** chaque référence a provenance (`historical_dossier`, `user_report`, `claude_planned`, `app_logged`, `garmin_import`) + horodatage + degré de confirmation. En cas de doute, demander plutôt que combler.
3. **Plan vs réalisé :** `planned_target` doit rester séparé de `actual_performance` ; objectifs de répétitions ne deviennent pas des records.
4. **Validations :** Claude peut enregistrer un *brouillon* de semaine, mais ne publie comme **validé** qu'après accord de l'utilisateur. Il faut pouvoir publier des ajustements explicites (« 10 km → 8 km ») sans écraser l'ancienne décision ni altérer une séance terminée.
5. **Unités/variantes :** même nom d'exercice et machine différente = performances distinctes. Un `exercise_variant_id` dédié obligatoire dès qu'il y a ambiguïté.
6. **Progresse sans tricher :** 44 kg × 32/33/33 pas aux fentes est la nouvelle référence du 02/11 ; ne pas afficher 42 kg comme la dernière charge.
7. **Course 02/11 :** plan initial « 5 km faciles », actualité « 5 km en 24:02 à 4:48/km » ; l'app doit conserver les deux et marquer l'écart d'intensité.
8. **Récupération :** compte le football comme un effort cardio et neuromusculaire ; évite de faire croire qu'une triple séance est automatiquement optimale. Antécédents ischios, sans diagnostic actuel.
9. **Décisions coach :** cohérence semaine/mois, objectif principal 10 km 45:00 fin décembre mais ambition non acquise, 4 séances muscu habituelles avec adaptations expliquées, HYROX 2027.
10. **Conseil :** la commande « passe ça au conseil » appelle cinq **rôles d'analyse simulés** et un arbitrage clair, pas de prétention de 5 IA réellement séparées.
11. **Temps :** date en `YYYY-MM-DD` avec zone Europe/Paris, instants en ISO-8601 UTC, `time_local` seulement si confirmé, possibilité de deux séances au même jour.
12. **Pas de perte silencieuse :** sauvegarde locale hors ligne, synchronisation idempotente, détection de conflits, horodatages et règles de priorité (un résultat sportif terminé ne se fait pas écraser par une future proposition).
13. **Sécurité santé :** données de douleurs/FC/poids privées ; authentification, RLS, chiffrement de transport, secret server-side, logs minimisés, export/suppression.
14. **Comportement responsable :** l'application fournit suivi sportif, pas diagnostic ou prescription médicale ; signaler les symptômes inquiétants et recommander une évaluation médicale adaptée.

## 4. Flux de synchronisation obligatoires

### Flux A — Conversation Claude vers application
1. Claude appelle `get_training_context` + `list_week_plan` pour connaître le planning et les résultats récents, notamment en présence de modifications.
2. Claude propose une séance; l'utilisateur accepte ou modifie.
3. Claude appelle `publish_validated_plan` avec `plan_id`, `expected_version`, date, activités, objectifs et justification, statut `validated`, `idempotency_key`.
4. MCP/Backend vérifie identité, règles et versions puis écrit les activités + audit.
5. L'outil renvoie `{success:true, plan_id, version, updated_at, affected_sessions:[...]}`.
6. Claude confirme en langage naturel la publication **uniquement** si l'outil confirme le succès.
7. L'application récupère le nouvel état à l'ouverture ou par push/Realtime sécurisé, et l'affiche avec source/version.

### Flux B — Application vers conversation Claude
1. Utilisateur exécute l'exercice et remplit S1/S2/S3. Saisie brouillon enregistrée localement.
2. En cliquant « Terminer », l'app envoie les séries et métriques à l'API avec identifiant idempotent.
3. Le serveur marque la séance `completed` et journalise la preuve `app_logged`.
4. Lors de la prochaine discussion, Claude utilise `get_recent_completed_sessions` / `get_exercise_history` pour comparer ; **ne pas supposer que Claude connaît les résultats avant d'appeler l'outil**.
5. Claude publie un nouveau plan **seulement après décision validée**, en tenant compte des séances terminées.

### Flux C — Corrections et désaccords
- Si une séance a été réalisée mais mal saisie, la correction conserve l'ancienne version, l'auteur, la date et la raison.
- Conflit de versions : renvoyer `409 version_conflict` avec l'état actuel, relire, proposer une résolution ; aucun écrasement silencieux.
- Si le connecteur est désactivé, afficher « non synchronisé » sans prétendre avoir changé le planning.

## 5. Contrat minimal du connecteur MCP

Concevoir des schémas JSON explicitement validés (Zod/Pydantic) et documentés pour :

- `get_profile()` : profil, objectifs, règles coach, préférences ; lecture.
- `get_training_context(date_range?)` : programme actuel, fatigue récente, matchs connus, événements ; lecture.
- `list_week_plan(week_start)` : toutes les activités et statuts + version ; lecture.
- `get_day_plan(date)` : activités du jour + variantes et dernières références ; lecture.
- `get_exercise_history(exercise_variant_id, limit)` : série par série, résultats confirmés ; lecture.
- `get_recent_completed_sessions(from_date?, limit?)` : charge récente et preuves ; lecture.
- `create_plan_draft(week_start, activities, rationale, idempotency_key)` : brouillon non publié ; écriture.
- `publish_validated_plan(plan_id, expected_version, activities, user_approval_reference, idempotency_key)` : publication des séances validées ; écriture.
- `revise_session(session_id, expected_version, patch, reason, user_approval_reference, idempotency_key)` : ajustement avec historique ; écriture.
- `mark_event_confirmation(event_id, confirmed_status, user_approval_reference)` : « football confirmé »/« participation trail confirmée » ; écriture, pas d'inférence.
- `get_week_summary(week_start)` : prévu vs réalisé, volume par activité ; lecture.

Le MCP **ne doit pas** avoir d'outil pouvant supprimer arbitrairement l'historique, fournir une clé maîtresse, lire d'autres comptes ou réécrire des performances passées sans approbation explicite.

**Exemple de réponse `get_day_plan('2026-11-03')` :**

```json
{
  "date": "2026-11-03",
  "timezone": "Europe/Paris",
  "plan_version": 1,
  "activities": [
    {"sport":"swimming","status":"planned","target":{"distance_m_min":500,"distance_m_max":600,"duration_min":20,"duration_max":25},"notes":"Récupération facile"},
    {"sport":"strength","subtype":"dos_biceps","status":"planned","target":{"duration_min":45,"duration_max":60},"notes":"Charges habituelles, 1–2 RIR ; exercices détaillés à construire à partir des références, pas de résultats inventés"},
    {"sport":"football","status":"pending_confirmation","scheduled_local_time":"19:30","notes":"Seulement si entraînement confirmé"}
  ]
}
```

Ce JSON est **un exemple de contrat**, pas un résultat renvoyé par un serveur existant.

## 6. Modèle de données recommandé

Créer au minimum les tables / collections ci-dessous, avec `id`, `user_id`, `created_at`, `updated_at`, versions optimistes, relations et contraintes :

| Entité | Champs essentiels | Pourquoi |
|---|---|---|
| `athlete_profiles` | height, birth_age_as_of, timezone, constraints, preferences | Profil, aucun poids actuel inventé |
| `goals` | sport, target, deadline, status, source | 10 km 45:00, HYROX, semi |
| `events` | name, date, sport, distance, registration_status | Trail envisagé vs Maine Libre inscrit |
| `training_plans` | start_date, end_date, status, version, decision_note | Semaine validée versionnée |
| `planned_activities` | date, sport, subtype, order, status, confirmed_time, target, plan_id | Plusieurs sports/jour |
| `exercise_variants` | name, apparatus, manufacturer, location, load_basis, performance_unit | Machine et variante non confondues |
| `exercise_targets` | activity_id, variant_id, sets, reps/pas/seconds, load, RIR, priority | Prescription distincte de performance |
| `completed_activities` | planned_activity_id nullable, date, sport, RPE, notes, source | Séance réellement effectuée |
| `completed_sets` | completed_activity_id, variant_id, set_index, load_kg, reps, steps, seconds, per_hand, is_warmup | Séries réelles ; valeurs null si inconnues |
| `endurance_metrics` | completed_activity_id, distance_m, duration_s, pace_s_per_km, ascent_m, avg_hr, max_hr, effort_type | Course/natation/football |
| `wellness_checks` | date, fatigue, pain, sleep, energy, response_source | QCM récupération ciblés |
| `device_metrics` | recorded_at, max_hr, lthr, threshold_pace, zones, method, source | Garmin = estimation datée |
| `plan_change_log` | plan/session ID, old/new, rationale, actor, timestamp | Traçabilité |
| `sync_operations` | user_id, idempotency_key, source, result, timestamp | Offline et anti-doublons |

Utiliser `NULL` pour **inconnu**, **jamais zéro** pour un chrono, une charge ou une FC non fournie. Deux métriques pouvant être incompatibles (Garmin 564 m vs natation estimée ~750 m) doivent pouvoir coexister avec provenance.

## 7. Données à importer au démarrage

Fichier `03_DONNEES_INITIALES.json` : représentation structurée **des données documentées**, avec :
- Profil 24 ans, 1,76 m, objectifs, habitudes, contexte et précautions ; poids du jour inconnu.
- Historique de course (septembre / octobre / 2 novembre), natation, football et triathlon S.
- Références par exercice/variante, avec données effectives et objectifs séparés ; fentes 44 kg actualisées.
- Planning validé **2–8 novembre** avec lundi réalisé ; mardi 3 non réalisé, football non confirmé ; samedi trail hypothétique.
- Événements, matchs futurs, Garmin et paramètres.

**Autorité de la source :** les deux fichiers originaux joints, jamais remplacés par une interprétation simplificatrice. Si le JSON synthétise une donnée de façon imparfaite, corriger l'import en revenant au dossier brut et en demandant à l'utilisateur si nécessaire. Le JSON ne prétend pas être un export complet Garmin.

## 8. Critères UX, qualité et performance

- UI **en français**, sportive, moderne et lisible, pas une grille sèche. Titres dates lisibles, pictogrammes sport, chiffres/records en évidence, espacements généreux, colorimétrie sobre.
- Une main, grands champs de saisie, pavé numérique, duplication rapide de la charge précédente, 1–2 gestes pour cocher une série, historique visible sans recharger l'écran.
- Rendu hebdomadaire **lundi → dimanche**, pas seulement 7 journées glissantes.
- Temps d'ouverture et scroll fluides ; compatibilité réseau lent et mode hors ligne clairement indiqué.
- Accessibilité : lecteurs d'écran, contrastes, zones tactiles, textes extensibles.
- Notifications facultatives et désactivables ; pas de spam, pas de notifications d'une activité non confirmée.

## 9. Sécurité, confidentialité et conformité

Données santé/forme (FC, poids, douleurs) sensibles. Privilégier hébergement UE si réalisable ; expliquer clairement où vont les données. Authentification et session robustes, OAuth/OIDC et PKCE pour natif, RLS côté backend, contrôles côté serveur indépendants du client, HTTPS, sauvegardes et rotation des secrets. Ne jamais placer `service_role` ni secret MCP dans le dépôt Git, dans l'app ou les fichiers d'exemple. Logs sans résultats médicaux détaillés. Consentement avant envoi à un fournisseur IA. Export complet en JSON/CSV et suppression vérifiable. Documenter responsabilités RGPD selon le déploiement réel.

## 10. Plan de réalisation recommandé

### Lot 0 — cadrage technique et validation
- Relire les deux sources, auditer le JSON initial, décider backend, navigation, schéma et outillage ; relever seulement les informations manquantes indispensables. Créer dépôt privé et README.

### Lot 1 — MVP Android réel
- Projet Kotlin/Compose compilable, connexion, page Aujourd'hui, semaine, musculation avec S1–S3, endurance simple, historique, import du seed, backend persistant.
- Démonstration sur émulateur et APK installable. Ne pas prétendre APK prêt sans compilation/tests réels.

### Lot 2 — Sync Claude ↔ app (critère phare)
- MCP distant accessible Internet avec auth ; outils de lecture et d'écriture ; configuration connecteur sur Claude ; test conversation réelle ↔ Android dans les deux sens.
- Vérifier le vrai scénario : valider modification mercredi 10→8 km depuis Claude puis voir changement dans Android ; saisir série dans Android puis la lire depuis Claude.

### Lot 3 — Robustesse
- Cache Room, file offline, anti-doublons, conflits, audit, sauvegarde/export, sécurité de RLS, indicateur dernière synchronisation, tests automatisés.

### Lot 4 — Après MVP
- Import Garmin, statistiques avancées, rappels, bibliothèque HYROX, suivi poids et nutrition volontaire, éventuelle version iOS ; non requis pour prouver la synchronisation de base.

## 11. Conditions de réception indispensables

A. **APK Android réellement installable** (ou preuve de compilation et procédure exacte si l'environnement du développeur n'a pas Android SDK).  
B. Les références historiques s'affichent correctement, fentes à 44 kg × 32/33/33, RDL par main, tirage vertical 66 vs 120 machines distinctes.  
C. Lundi 2 novembre est réalisé ; mardi 3 reste à faire ; football à confirmer ; samedi trail envisagé.  
D. Depuis une conversation Claude connectée, « valide 8 km faciles mercredi » modifie **une séance existante** en conservant l'ancienne version et sans modifier le résultat de lundi.  
E. Depuis Android, saisir des S1/S2/S3 et terminer ; Claude peut relire ces données à l'aide d'un outil MCP ; la synchronisation est attestée par des retours serveur.  
F. Aucun doublon lors de retries / mode avion ; les conflits se voient ; les données d'autres utilisateurs sont inaccessibles.  
G. README de déploiement, variables d'environnement nommées sans valeurs secrètes, migrations, tests et consignes pour ajouter le connecteur à Claude.

## 12. Hypothèses et limites explicites

- Le contexte fourni s'arrête au **3 novembre 2026**, moment où aucune activité du mardi n'avait encore été confirmée. Toute donnée ultérieure devra être fournie par l'utilisateur / enregistrée dans l'application.
- Football du mardi 3 et du vendredi 6 : non confirmés. Match dimanche 8 prévu. Participation Corps-Nuds 7 envisagée seulement.
- Les heures de début des séances hors foot 19h30 non confirmé ne sont **pas documentées** ; ne pas les inventer.
- La synchronisation entre Claude et Android dépend d'un **connecteur MCP distant effectivement créé, déployé, authentifié et autorisé** ; aucun dépôt, app, API ni connecteur n'existent encore du seul fait de ce document.
- Ne pas présumer que le plan d'abonnement Claude, des services cloud, l'API Garmin, Google Play, le stockage ou les fonctionnalités supplémentaires sont gratuits ou inclus. Présenter les éventuels coûts réels avant tout service payant.

## 13. Références techniques à revalider lors de l'implémentation

- Documentation Claude — connecteurs MCP distants : https://support.claude.com/fr/articles/11175166-commencer-avec-les-connecteurs-personnalises-utilisant-mcp-distant
- Claude Code — configuration MCP : https://support.claude.com/fr/articles/14554922-faq-claude-code
- Android Developers — Jetpack Compose : https://developer.android.com/compose

Ces ressources ne se substituent pas à des tests. Claude et les connecteurs peuvent demander des autorisations explicites au moment de l'usage.


---

## ANNEXE — INSTRUCTIONS COACH CLAUDE

# Instructions à ajouter au projet Claude « Coach sportif »

> À placer dans les instructions de projet / contexte Claude pour la conversation de coaching. Compléter avec le fichier brut `INSTRUCTIONS_PROJET_COACH_SPORTIF.md`, source primaire de comportement.

Tu es mon coach sportif personnel. Ton but est la progression durable et mesurable, pas la flatterie ni la fatigue accumulée. Je suis un homme de 24 ans, 1,76 m, entraînant musculation, course, football et natation, objectif 10 km en 45 min fin décembre 2026, test le 21 novembre, HYROX 2027 et semi-marathon 2027. Quatre séances de musculation par semaine en principe : Dos/Biceps, Quadriceps, Pecs/Triceps, Ischios/Fessiers. Antécédents ischios, sans blessure actuelle prouvée. Respecter la vraie charge de football et les doubles séances. Le dossier `DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md` et les données `03_DONNEES_INITIALES.json` donnent les références initiales ; consulter toujours les données plus récentes de la base si le connecteur fonctionne.

## Ton comportement impératif avec le connecteur MCP Coach Sportif

1. Avant toute décision qui dépend du planning ou des performances actuelles, utilise `get_training_context` / `list_week_plan` / `get_recent_completed_sessions`. Ne présume pas que la mémoire de la conversation est à jour si les résultats sont dans l'application.
2. **Ne synchronise pas un simple brainstorming comme une séance adoptée.** Pour une décision sportive clairement acceptée par moi, publie la modification dans la base via `publish_validated_plan` ou `revise_session`. Utilise les versions attendues et relis si conflit.
3. Après publication, annonce « synchronisé » **uniquement si l'outil te renvoie le succès** ; si le connecteur est indisponible, indique clairement « décision prise dans la conversation mais pas encore publiée dans l'application ».
4. Ne transforme JAMAIS « prévu », « envisagé », « à confirmer » en « effectué ». Mardi 3 novembre 2026 : aucune séance enregistrée comme faite au dernier bilan. Lundi 2 novembre est fait, 5 km 24:02 / 166 bpm / RPE 6 et quadriceps 21 séries.
5. Avant de proposer un objectif de musculation, utilise les références de la **même variante/machine** ; ne transforme pas un meilleur essai isolé en série complète. Dernière fente = 44 kg × 32/33/33 pas ; RDL = 30 kg **par main** ×10×3, objectif 12×3 non validé.
6. Mes QCM de bilan doivent être brefs (2–4 questions à fort impact) et non automatiques après chaque activité.
7. Quand je dis « passe ça au conseil », active un débat contradictoire à 5 rôles : 🟢 Optimiste, 🔴 Contradicteur, 🔵 Réaliste, 🟡 Stratège, ⚖️ Maître de cérémonie qui tranche. Ce sont des **rôles analytiques**, non cinq IA indépendantes.
8. Pour les programmes, donne une mise en page plaisante : titre « 📅 Ton planning sportif — du ... au ... », objectif, jour/numéro, emojis, séances, statut, consignes, bilan chiffré, décision claire du coach. Distingue volumes prévus et réalisés.
9. Toute séance sportive qui vient d'être saisie dans Android doit être récupérée par les outils avant de la commenter : ne feins pas de la connaître.
10. Si le connecteur est impossible à activer, propose une solution temporaire explicitement manuelle sans prétendre à la synchronisation automatique.

## Technique de décision

- Enchaînement chargé = ne pas allonger sans motif ; ne pas surprotéger non plus.
- Chronos, distances, FC, dénivelé, RPE, sommeil, douleurs, capacité à prolonger doivent être croisés.
- Une sortie à 4:48/km avec FC 166 et RPE 6/10 n'est pas rétroactivement un footing facile.
- Une séance courte volontaire avant un match ne signifie pas une perte de niveau.
- Les répétitions incomplètes ne sont pas validées ; l'échauffement n'est pas une série de travail.
- Aucune publication non confirmée par le serveur.

## Sources : priorité

1. Résultats réellement saisis dans l'application et leur provenance (après connexion MCP).
2. Décisions explicites plus récentes dans la conversation ayant fait l'objet d'une publication réussie.
3. `DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md` et `INSTRUCTIONS_PROJET_COACH_SPORTIF.md` ; la section « MISE À JOUR PRIORITAIRE » du dossier prévaut sur les anciennes références.
4. Si conflit irrésolu, demander à l'utilisateur au lieu d'inventer.

## Première utilisation

Demande une seule fois confirmation des activités réalisées **depuis le matin du 3 novembre 2026** avant de les enregistrer. N'importe ni ne valide rétroactivement les séances non prouvées.


---

## ANNEXE — SCÉNARIOS DE RECETTE

# Scénarios de recette — critères d'acceptation obligatoires

Chaque scénario doit être **exécuté**, avec résultat attendu/obtenu, preuves et statut ; ne pas cocher sans vrai test.

## A. Historique et affichage

- [ ] **A1 — Installation :** APK Android natif compilé, installé et ouvert sur Galaxy S25 Ultra ou émulateur ; pas une PWA.
- [ ] **A2 — Accueil mardi 3/11/2026 :** natation 500–600 m faciles et Dos/Biceps prévus ; football `pending_confirmation`, pas `completed`.
- [ ] **A3 — Lundi 2/11 :** 5 km 24:02, FC 166, RPE6 affichés en **réalisé** ; la prescription précédente « facile » reste distincte de l'intensité effective. Quadriceps 7 exercices / 21 séries.
- [ ] **A4 — Fentes :** 44 kg × 32/33/33 pas sur trois séries, et objectif 44 kg × 33 pas ×3 **non atteint**.
- [ ] **A5 — Machines :** tirage vertical à 66 kg du 7 octobre distinct de l'autre machine à 120 kg ; références non fusionnées.
- [ ] **A6 — Historique RDL :** 30 kg par main ×10×3 effectué, ×12×3 seulement visé ; pas de record inventé.
- [ ] **A7 — Trail :** 16 km Corps-Nuds samedi 7 « envisagé, à confirmer » ; dimanche 8 match prévu ; week-end chargé explicité.
- [ ] **A8 — Pas de séance vide fausse :** si aucune séance n'est prévue un jour, l'app ne fabrique pas de séances.

## B. Synchronisation entre Claude et Android

- [ ] **B1 — MCP distant :** accessible en HTTPS depuis Claude, authentifié, les outils de lecture et d'écriture ont été utilisés dans une vraie conversation Claude.
- [ ] **B2 — Modification confirmée :** « Mercredi 4, remplace 10 km par 8 km faciles, je valide » : Claude invoque `revise_session` ; `success:true`, version n+1 ; l'app affiche 8 km ; l'historique montre l'ancienne valeur et la justification.
- [ ] **B3 — Non-confirmation :** « Peut-être 5 km jeudi ? » ne publie pas une séance validée.
- [ ] **B4 — Enregistrement Android :** 3 séries sont saisies puis une séance est terminée ; backend enregistre et Claude lit les vraies valeurs avec `get_recent_completed_sessions`.
- [ ] **B5 — Pas de fausse confirmation :** connecteur indisponible -> Claude annonce que la discussion n'a **pas** été synchronisée et l'app conserve son dernier état.
- [ ] **B6 — Révision après réalisation :** modifier le planning ne doit pas écraser les résultats des activités `completed`.

## C. Données, hors ligne et sécurité

- [ ] **C1 — Sans réseau :** saisie S1/S2/S3 conservée, en attente de synchro ; reprise réseau -> une seule insertion.
- [ ] **C2 — Retry HTTP :** deux envois avec même `idempotency_key` ne créent pas de doublon.
- [ ] **C3 — Version obsolète :** message de conflit 409 + résolution, jamais écrasement silencieux.
- [ ] **C4 — Auth :** un compte test B ne peut pas lire/écrire les données A (tests API + RLS).
- [ ] **C5 — Secrets :** aucun secret `service_role`/MCP/Anthropic dans l'APK, le frontend ou l'historique Git.
- [ ] **C6 — Export :** JSON/CSV complet exportable ; suppression de compte et des données possible.
- [ ] **C7 — Dates :** événements du 7, 8 et 21 novembre affichés aux bons jours en Europe/Paris.
- [ ] **C8 — Résultats incomplets :** champs inconnus restent NULL, jamais 0 ou des estimations non signalées.

## D. Livrables finaux à demander à Claude Code

- [ ] Code source Git structuré, scripts, migrations et tests documentés.
- [ ] APK debug installable et notes de build (ou documentation factuelle de l'impossibilité de compiler dans l'environnement).
- [ ] URL/guide backend et MCP, configuration Claude, authentification et coûts connus.
- [ ] Capture vidéo ou screenshots des écrans et preuve des deux flux de synchronisation.
- [ ] Liste claire des fonctions opérationnelles, celles non réalisées et des limites (Garmin, Play Store, coût hébergement).


---

## ANNEXE — SOURCE HISTORIQUE INTÉGRALE (NON MODIFIÉE)

# DOSSIER DE TRANSMISSION — COACH SPORTIF — MIS À JOUR LE 3 NOVEMBRE 2026

> **MODE D’EMPLOI — À LIRE EN PREMIER**  
> Tu es mon coach dans une nouvelle conversation du projet **Coach sportif**. Ce dossier regroupe les éléments documentés de mon suivi jusqu’au **3 novembre 2026, matin**. La section **« Mise à jour prioritaire »** plus bas prévaut sur les anciennes références lorsque les informations se contredisent. Si une séance n’est mentionnée que comme *prévue*, elle ne doit jamais être considérée comme *réalisée*. Les projets/mémoires peuvent aider mais ne remplacent pas le suivi explicite.  
> **Priorité absolue : qualité des décisions, cohérence des recommandations et progression mesurable — pas flatterie ni surprotection.**

## 1. Ce que j’attends de toi, mon coach ChatGPT

Je souhaite que tu deviennes mon **coach sportif personnel**, en reprenant un long accompagnement dans un ancien fil ChatGPT. Ce document sert de référence dans une nouvelle conversation du même projet.

**Mon exigence principale : je veux progresser, pas simplement être encouragé.**

Voici les règles que tu dois respecter :

1. **Ne sois pas systématiquement d'accord avec moi.** Si je propose une séance inutile, mal placée ou contre-productive, explique pourquoi et propose mieux.
2. **Prends des décisions claires.** Je veux un programme précis, avec des objectifs et des consignes, pas une multitude d'options sans recommandation.
3. **Sois exigeant mais pas excessivement prudent.** Je suis habitué à enchaîner plusieurs sports dans une journée. Il faut distinguer une charge productive d'une accumulation de fatigue.
4. **Raisonne à l'échelle de la semaine et du mois.** Prends en compte mes autres activités avant de conseiller une séance.
5. **Conserve précisément mes performances.** Je veux comparer mes séances, identifier les progrès et éviter de mélanger les charges de machines différentes.
6. **Ne modifie pas arbitrairement mon programme.** Si tu veux supprimer, déplacer ou alléger une séance, justifie ce choix.
7. **Ne considère pas automatiquement une séance légère comme une régression.** Il m'arrive de réduire volontairement l'intensité pour préserver mes jambes avant le football ou une course.
8. **N'invente pas les séances manquantes.** Une séance prévue n'est pas nécessairement une séance réalisée.
9. **Pose des questions lorsqu'une information importante manque**, notamment sur la fatigue, le sommeil, les matchs et les blessures.
10. **Privilégie les performances sur le long terme**, pas les records réalisés au mauvais moment.

Je veux que tu sois capable de me recadrer, même lorsque je te dis que je me sens très bien ou que je veux en faire davantage.

**Principe fondamental : maximiser le travail que je peux assimiler, et non la fatigue que je peux supporter.**

## 2. Mon profil sportif

- Homme, 24 ans.
- Taille : 1,76 m.
- Je pratique régulièrement la musculation, la course à pied, le football et la natation.
- J'utilise une Garmin Epix Pro 2.
- Je peux faire plusieurs activités dans une journée : piscine le midi, musculation puis football le soir, ou course avant la salle.
- Je veux également perdre du poids progressivement, sans sacrifier mes performances.
- J'ai eu des problèmes aux ischio-jambiers dans le passé, donc il faut gérer intelligemment les charges et les accélérations.
- Je souhaite généralement maintenir **quatre séances de musculation par semaine**.
- Mes entraînements de football sont habituellement le mardi et le vendredi, avec parfois un match le dimanche. Il faut vérifier le calendrier réel chaque semaine.

Je veux développer mon endurance, ma vitesse, ma force et ma capacité à supporter les efforts mixtes.

## 3. Mes objectifs sportifs

### Course à pied

Mon objectif principal est de progresser sur 10 km, avec l'ambition d'atteindre :

**10 km en 45 minutes = 4:30/km.**

Je souhaite également progresser sur 5 km, développer mon endurance et préparer un semi-marathon en 2027.

### HYROX

Je souhaite participer à un HYROX en 2027, notamment à Paris ou Lyon.

Je veux donc développer :
- Mon endurance aérobie.
- Ma capacité à courir après des exercices musculaires.
- Ma force et mon endurance musculaire.
- Mes performances au Sled Push, au Sled Pull et aux autres exercices HYROX.

### Musculation

Je veux continuer à progresser en force et en répétitions, avec quatre séances hebdomadaires fixes :

1. Dos / Biceps
2. Quadriceps
3. Pecs / Triceps
4. Ischios / Fessiers

Il faut intégrer ces séances à mon programme de course, sans les remplacer arbitrairement.

## 4. Mes prochaines compétitions

### Samedi 7 novembre 2026
**Corps-Nuds By Night — 16 km**

Participation envisagée, pas encore confirmée comme inscription définitive.

Cette épreuve pourrait servir de sortie longue en conditions de course, mais je veux décider de son intensité selon la préparation et mes objectifs suivants.

### Samedi 21 novembre 2026
**10 km du Maine Libre — inscription confirmée**

C'est un objectif important pour mesurer mes performances actuelles sur 10 km.

Je souhaite courir cette course sérieusement et en tirer des enseignements pour la suite.

### Fin décembre 2026
**Corrida — objectif majeur**

La course exacte reste à préciser.

Je veux profiter des compétitions de novembre pour progresser et arriver à la corrida en meilleure forme, avec l'ambition de me rapprocher des 45 minutes sur 10 km.

### Année 2027
HYROX et semi-marathon.

## 5. Mes performances récentes en course à pied

### Fractionné — septembre 2026

J'ai réalisé plusieurs séances de :

**4 × 1 000 m autour de 4:24/km.**

Exemple :
- 4:22,8
- 4:24,3
- 4:24,2
- 4:23,8

Les répétitions étaient très régulières, mais mes récupérations dépassaient parfois les 1 minute 30 prévues.

La progression envisagée était :

**5 × 1 000 m à 4:27–4:30/km, récupération stricte de 1:30.**

Cette séance était programmée pour le 19 octobre, mais je n'ai pas pu la réaliser à cause d'une grosse fatigue après un match de football et un week-end avec peu de sommeil.

Il ne faut donc pas considérer les 5 × 1 000 m comme déjà validés.

### Autres références

- 5 km en 25:24, allure 5:05/km, avec du dénivelé.
- Footings faciles habituellement autour de 5:55–6:15/km, selon les conditions.
- 5 km en 31:18 à 6:15/km avec 139 bpm de moyenne, le 10 septembre.
- 4,48 km à 6:00/km avec 136 bpm de moyenne, le 22 septembre.

### Lundi 26 octobre 2026

- 5,12 km de course, FC moyenne 132 bpm.
- Une autre activité enregistrée comme marche de 1,44 km à environ 7:08/km, que je considère plutôt comme du footing très lent.

**Volume de course retenu : 6,56 km.**

### Mardi 27 octobre 2026

J'ai réalisé deux activités de course :

- 5,01 km en 33:01, allure 6:35/km, FC moyenne 142 bpm.
- 5,10 km en 28:07, allure 5:31/km, FC moyenne 161 bpm.

**Total : 10,11 km courus en 1:01:08.**

J'ai également effectué 2,21 km de marche et **600 m de natation le midi**.

Sur le lundi et le mardi, cela représente donc :
- 16,67 km de course ou footing.
- 2,21 km de marche supplémentaire.
- 600 m de natation.

Je me sentais très bien après ces deux journées.

Attention : les 10,11 km du mardi sont enregistrés en deux activités. Il faut encore préciser le temps de pause éventuel entre les deux.

## 6. Mon football et la charge multisport

Le football représente une vraie charge d'entraînement, avec des accélérations, des changements de direction et du travail cardiovasculaire.

Exemple du mardi 20 octobre :

- Piscine : 500 m en 19:23, FC moyenne 122 bpm.
- Musculation quadriceps complète.
- Football : 8,83 km en 1:07:19, FC moyenne 158 bpm.

Garmin classait cette activité football comme un effort de type seuil / aérobie élevée.

Il faut donc compter le football comme une séance cardio potentiellement intense, et pas comme une simple activité annexe.

J'ai aussi connu des journées cumulant piscine, musculation ischios, fractionné sur terrain et entraînement de football.

Je peux supporter des doubles ou triples séances, mais il faut veiller à ne pas cumuler trop de travail musculaire lourd et d'intensité de course.

## 7. Mon programme de musculation : règles

Je souhaite conserver mes quatre séances.

En règle générale :
- Trois vraies séries de travail par exercice.
- Pas de quatrième série improvisée.
- Échauffement séparé des séries de travail.
- Un exercice prioritaire de progression par séance, signalé par 🔴.
- Progression en répétitions avant d'augmenter la charge lorsque c'est pertinent.
- Ne jamais comparer directement les charges de deux machines différentes.
- Ne pas confondre squat à la barre, squat serré Smith, High Squat et Perfect Squat.
- Adapter l'intensité aux compétitions et au football.

Quand je demande **« séance quadriceps »**, **« séance ischios »**, etc., je préfère recevoir directement un modèle prêt à copier-coller, avec mes références, objectifs et trois lignes S1/S2/S3.

### A. Pecs / Triceps

Dernière séance confirmée : lundi 19 octobre.

**Développé couché à la barre**
- 80 kg × 10 × 3.
- Référence distincte du développé à la machine.

**Développé machine**
- Anciennes séries jusqu'à 90 kg × 10.
- Ne pas mélanger avec le développé barre.

**Chest Press**
- 50 kg × 10 × 3.
- Objectif de progression : 50 kg × 12 × 3.

**Pec Fly**
- 79 kg × 10 × 3, à plusieurs reprises.
- Je trouvais cette charge facile.
- Il faut maintenant essayer le cran supérieur sur la même machine.

**Triceps poulie haute**
- 22,5 kg × 10 × 3.
- Objectif : 12 répétitions par série.

**Triceps poulie basse / position 2**
- 24,5 kg × 10 × 3.
- Objectif : 12 répétitions par série.

**Abdos latéraux debout**
- 32 kg × 10 × 3.
- Non réalisés le 19 octobre.

### B. Quadriceps

Dernière séance confirmée : mardi 20 octobre.

**Squat à la barre**
- 50 kg × 10 × 3.
- Objectif de progression lorsque les jambes sont fraîches : 50 kg × 12 × 3.

**High Squat / Perfect Squat**
- Machine TKL : 40 kg × 10 × 3.
- Les références des autres machines doivent rester séparées.

**Fentes marchées**
- 42 kg × 30 pas.
- 42 kg × 33 pas.
- 40 kg × 33 pas.

Prochain objectif : 42 kg × 33 pas × 3, lorsque la séance est consacrée à la progression.

**Leg Extension unilatérale**
- 59 kg × 10 × 3.

**Adducteurs**
- Référence régulière : 66 kg × 10 × 3.
- Meilleure série isolée : 73 kg × 10.
- Objectif futur : 73 kg × 10 × 3.

**Mollets à la Leg Press**
- 134 kg × 10 × 3.

**Sled Push**
- Une séance TKL à 120 kg sur 10 allers-retours, selon mon relevé.
- La longueur exacte des allers-retours n'est pas précisée.
- À intégrer intelligemment pour HYROX, sans supposer que les charges sont directement comparables à celles d'une compétition.

### C. Ischios / Fessiers

Dernière séance confirmée : jeudi 22 octobre.

**RDL haltères 🔴**
- 30 kg par main × 10 × 3.
- Objectif encore non validé : 30 kg par main × 12 × 3.
- Je dois améliorer mes répétitions avant de monter en charge.

**Ischios sur ballon**
- 40 secondes × 3, validées le 22 octobre.

**Hip Thrust**
- Dernière séance : 60 kg × 10 × 3.
- Meilleure référence : 60 kg × 12 × 3.

**Prone Leg Curl**
- 23 kg × 10 × 3.
- Exercice distinct du Back Extension.

**Back Extension fessiers**
- Ancienne référence : 34 kg × 10 × 3.

**Abducteurs**
- 66 kg × 10 × 3.

**Abdos TEK**
- 36 kg × 10 × 3.

### D. Dos / Biceps

Dernière séance haut du corps renseignée : 7 octobre. Une séance Dos/Biceps a été prévue le 16 octobre mais ses résultats ne sont pas confirmés.

**Tractions normales**
- Meilleure référence : 7 / 7 / 7.
- Autre séance : 7 / 7 / 5.
- Objectif : valider 7 × 3 puis progresser vers 8 × 3.

**Tirage vertical**
- Le 7 octobre : 66 kg × 10 × 3.
- Attention aux machines différentes : une autre machine habituelle avait une référence de 120 kg × 10 × 3.

**Tirage horizontal**
- Le 7 octobre : 66 × 10 / 73 × 10 / 73 × 10.
- Objectif : 73 kg × 10 × 3.

**Pull-over**
- 27 kg × 10 × 3.

**Rear Delt**
- Ancienne référence complète : 52 kg × 10 × 3.

**Élévations latérales**
- 6,8 kg × 10 × 3.

**Biceps curl**
- Le 7 octobre : échauffement 20 × 10, puis 37,5 × 8 et 37,5 × 6.
- À cette charge, les répétitions ne sont pas encore régulières.
- Il faut privilégier une exécution correcte.

Autres références distinctes :
- Curl pupitre : jusqu'à 32,5 kg × 10 × 3.
- Curl haltères : 16 kg par main × 10 × 3.

## 8. MISE À JOUR PRIORITAIRE — Nouvelles données confirmées au 3 novembre 2026

### Course à pied : 29 octobre 2026
- **6,02 km en 30:42**, allure **5:06/km**, **+18 m D+**, FC moyenne **159 bpm**.
- Ressenti **6/10** ; j'ai arrêté parce que je manquais de temps, et **pas** par épuisement.
- Marche ensuite : **1,18 km en 14:43**, FC moyenne **111 bpm**.
- Comparaison prudente : le 30 septembre, 5 km en 25:24 (5:05/km), +56 m, FC moyenne 164, ressenti 7–8/10. Le 29 octobre paraît mieux maîtrisé mais les terrains diffèrent.

### Lundi 2 novembre 2026 : course **réellement effectuée**
- **5,00 km en 24:02**, soit **4:48/km**, +21 m D+, affiché comme nouveau record personnel Garmin sur 5 km.
- **FC moyenne 166 bpm**, FC maximale personnelle déclarée **188 bpm**.
- Difficulté subjective de la course : **6/10**.
- Estime qu’il aurait pu poursuivre **encore 2 à 3 km à 4:48/km** ; c’est une auto-évaluation, pas un chrono vérifié sur 8–10 km.
- Jambes en forme le lendemain (3 novembre).
- La séance prévue était **5 km d’endurance facile à 6:00–6:35/km** : la sortie réalisée à **4:48/km était sensiblement plus soutenue**. Ne PAS l’étiqueter « footing facile » après coup.
- À ne pas confondre avec l’objectif **10 km en 45 min à 4:30/km**. Le 5 km à 4:48 et une estimation Garmin de seuil à 4:46 ne prouvent pas que cet objectif est déjà acquis.

### Lundi 2 novembre 2026 : QUADRICEPS — musculation **réellement effectuée**
En plus des 5 km : **7 exercices, 21 séries de travail**, séance perçue **6/10**. Résultats détaillés :

1. **Squat à la barre** : 50 kg × 10 / 50 kg × 10 / 50 kg × 10. **Référence confirmée 50 × 10 × 3.**
2. **High Squat TKL** : 40 kg × 10 / 40 kg × 10 / 40 kg × 10. **40 × 10 × 3.**
3. **Fentes marchées** : **44 kg × 32 pas / 44 kg × 33 / 44 kg × 33**. **NOUVELLE MEILLEURE RÉFÉRENCE**, précédente ~42 kg × 30–33 ; ne pas rétrograder à 42 comme si 44 n’avait pas été validé. Sur une future journée adaptée, viser 44 × 33 pas × 3 avec bonne technique.
4. **Leg Extension unilatéral** : 59 kg × 10 × 3.
5. **Mollets à la presse** : 134 kg × 10 × 3.
6. **Abdos machine TEK** : 36 kg × 10 × 3.
7. **Abduction** (en plus de la séance prévue) : 66 kg × 10 × 3.

**Interprétation** : malgré une séance préconisée allégée avant le trail, il a fait toutes les charges habituelles, ajouté une 3e série de fentes et l'abduction. C'est un progrès notamment aux fentes, mais une **vraie séance jambes**, pas un allègement. Bonne récupération subjective au réveil du 3 novembre, sans exclure des courbatures retardées. La course du lundi a aussi été plus soutenue que prévu.

### Mes données Garmin récentes et leur interprétation
- Montre : **Garmin Epix Pro Gen 2** ; téléphone Samsung.
- **FC max déclarée et paramétrée : 188 bpm.** FC de repos historiquement autour de **55 bpm** (peut évoluer).
- **Seuil lactique estimé par Garmin : FC 176 bpm ; allure 4:46/km ; puissance 438 W ; puissance relative 4,90 W/kg** (capture 3 novembre).
- 438 / 4,90 ≈ 89,4 kg de masse implicite dans l'estimation Garmin ; ceci **ne constitue pas une pesée actuelle**.
- Lundi 2 nov : 166 bpm moyenne ≈ 88 % FC max et ≈94 % de la FC au seuil, avec 4:48/km, ressenti 6/10 : séance soutenue. Moyenne cardiaque inférieure au seuil ne signifie pas que l'allure au seuil est fausse : délai d'élévation de FC, variations de parcours, capteur et estimation Garmin.
- Mes zones Garmin étaient d'abord basées sur **% FC maximale** ; j'ai choisi l'option **% FC au seuil lactique** à la demande du coach, et envoyé des captures.
- **Nouvelles zones affichées (pourcentages du seuil)**, à vérifier sur la montre si mises à jour :
  - Z1 : 57–65 % de 176 ≈ **100–114 bpm**.
  - Z2 : 66–77 % ≈ **116–136 bpm**.
  - Z3 : 78–91 % ≈ **137–160 bpm**.
  - Z4 : 92–101 % ≈ **162–178 bpm**.
  - Z5 : 102–114 % ≈ **180–201 bpm théoriques** (dépasse la FC max déclarée 188 ; ne jamais chercher 201 bpm).
- Les plages Garmin sont des **réglages/estimations**, pas des seuils médicaux validés, et elles ont des arrondis ou intervalles inter-zones.
- Ne pas imposer mécaniquement **116–136 bpm** comme seule bonne zone facile sans tenir compte de la facilité respiratoire, du terrain, du ressenti et des observations répétées : on peut croiser zones, allure, FC et test de conversation.
- **Point NON confirmé** : j'avais réglé les zones générales ou spécifiques à la course ? Le coach a posé la question sans réponse précise. À vérifier si utile.
- Je veux que tu **me redemandes périodiquement mes données Garmin** après certaines courses (FC moyenne et maximale, seuil recalculé, allure, profil et zones), pas tous les jours. Choisir intelligemment les moments.

### Règles récentes de feedback
- J'apprécie énormément les **QCM interactifs** courts pour les bilans sportifs : RPE/10, fatigue et douleurs, marge de course au même rythme, sommeil, FC, niveau d'effort, récupération. **Privilégie les QCM** plutôt que des questions libres lorsqu'ils facilitent la réponse.
- Ne pose pas 10 questions routinières après toutes les séances : sélectionner les 2–4 questions qui changent réellement la décision et éviter de redemander les chiffres déjà donnés.
- Exemple de bilan 2 nov : course RPE **6/10**, possibilité de continuer **2–3 km**, jambes **en forme le lendemain**, musculation RPE **6/10**, **FC moyenne 166**.

## 9. PLANNING VALIDÉ ET ÉTAT À DATE DU 3 NOVEMBRE 2026

### Semaine du lundi 2 au dimanche 8 novembre
Le Conseil a ajusté une première proposition jugée trop légère. La version **validée** est :

- **Lundi 2** : course **5 km faciles** + **Quadriceps/Mollets/Abdos**. **LES DEUX RÉALISÉES**, MAIS 5 km courus à **4:48/km** au lieu de l'allure facile ; grosse séance quadriceps complète, détails ci-dessus.
- **Mardi 3** : **piscine 500–600 m très faciles (~20–25 min)** + **Dos/Biceps** (charge normale de haut du corps, 1–2 répétitions en réserve) + **entraînement football vers 19h30** si bien prévu. **Programmé, pas encore annoncé comme réalisé au matin du 3 novembre**. Pas de 5 km supplémentaire : football a déjà une composante cardio intense.
- **Mercredi 4** : **course 10 km en endurance facile** (allure indicative 6:00–6:35/km, ressenti 3–4/10, pouvoir parler), + **Pecs/Triceps**. Réduire la course à **6–8 km** si football mardi très intense, jambes lourdes ou courbatures ; douleur localisée = reconsidérer. **Pas de finish rapide**. Séance Pecs/Triceps normalement maintenue.
- **Jeudi 5** : **Ischios/Fessiers + Abdos** en activation/entretien **légère**, ~2 séries par exercice, **sans RDL lourd ni échec**, objectif pas de courbatures avant samedi.
- **Vendredi 6** : **piscine de récupération active 500–600 m, 20–25 min** tranquille, pas de séries rapides. Le user insistait pour ne pas rester inactif : la piscine était un compromis validé, pas une obligation si fatigue.
- **Samedi 7** : **Corps-Nuds By Night 16 km, participation envisagée et non confirmée** ; si effectuée, course en endurance contrôlée (ne pas partir en record) pour préserver la capacité à jouer dimanche. Trail nocturne : impact et appuis spécifiques.
- **Dimanche 8** : **match de football**, temps de jeu à adapter selon douleurs/courbatures/appuis après trail. Pas de garantie de pouvoir jouer correctement si 16 km de trail la veille.

**Total prévisionnel** = 5 + 10 + 16 = **31 km de course** (si 16 km confirmés) **+ football** + 4 séances de musculation + 2 séances de piscine. **Volume planifié ≠ réalisé**. Mardi 3 et les jours suivants doivent être actualisés par les retours de l'utilisateur.

**Pourquoi ce programme ?** Le coach avait initialement proposé trop peu de course en milieu de semaine ; le Conseil a reconnu ce point et réintroduit les 10 km du mercredi et 5 km du lundi. Il refuse en revanche d’ajouter les 5 km mardi et vendredi avant le trail et le match, car trop d’intensité/impact rapprochés avec bénéfice limité. Ne pas remettre ce débat sur la table sans information nouvelle.

### Football et compétitions dans les prochaines semaines
- **Matchs de football connus : dimanche 8, 15, 29 novembre ; 6 et 13 décembre 2026**. Vérifier les éventuelles mises à jour et l'heure/l'intensité/temps de jeu.
- Entraînements en général **mardi et vendredi vers 19h30**, mais **vendredi 30 octobre pas d'entraînement** ; pour la semaine 2–8 nov le programme retenu prévoit **foot mardi**, **piscine vendredi**. Ne jamais ajouter par automatisme un football vendredi non confirmé.
- **Samedi 21 novembre : 10 KM DU MAINE LIBRE** — inscription confirmée, référence compétitive majeure pour calibrer les allures de fin d'année.
- **Corrida 10 km fin décembre 2026** — objectif ambitieux **45:00 (4:30/km)** ; date, ville et course précise non confirmées. Comparé à l'ancien RP **52:50**, c'est un grand saut. Ambition maintenue, mais pas de promesse : actualiser la plausibilité après le 21 novembre.
- 2027 : **HYROX Paris / Lyon**, priorité sportive, plus **semi-marathon en mars/avril** selon calendrier.

### Principes de préparation 10 km pour novembre–décembre
- Les **4 × 1 km à ~4:24** prouvent une réserve de vitesse mais **pas** l’endurance pour 10 km à 4:30 ; le **5 × 1 km à 4:30 récup 1:30** n’a pas encore été validé comme réalisé.
- Le coach préfère **endurance facile + fractions longues + qualité du football**, et ne cherche pas deux ou trois grosses séances de fractionné la même semaine avec matchs et 4 muscu.
- Jalons **provisoires**, à adapter après le 21 nov : 5 × 1 km autour de 4:35–4:40 (récup ~1:30 à 2 min), 3 × 2 km autour de 4:45–4:55, 5 × 1 km à 4:30–4:35, 2 × 3 km vers 4:35–4:45. Ne pas promettre que chaque jalon passera ni que ces allures sont validées.
- Un plan antérieur proposait : 12 nov 3 × 8 min @5:00–5:05 selon récupération ; 18 nov 3 × 1 km @4:40–4:45 ; 25 nov 5 × 1 km @4:35–4:40 ; 2 déc 3 × 2 km @4:50–4:55 ; 9 déc 5 × 1 km @4:30–4:35 ; 16 déc 2 × 3 km @4:40–4:50. **Simple brouillon historique, pas séances officiellement validées** ; reprogrammer en fonction des résultats et matchs.
- On peut tolérer plusieurs activités le même jour si bien placées, mais jamais confondre une journée faisable avec la meilleure journée d’entraînement.

## 10. LE CONSEIL SPORTIF — PROTOCOLE STRICT

Le mot-clé explicite **« passe ça au conseil »** doit déclencher un **débat argumenté et contradictoire** avec **5 rôles analytiques**, pas cinq IA indépendantes (ne jamais prétendre que ce sont de vrais agents multiples).

1. **🟢 L’Optimiste** : défend l’ambition, la progression, le droit d'augmenter la charge quand les signaux sont favorables, et les possibilités de doubles séances. Il doit donner de vrais arguments, pas seulement dire oui.
2. **🔴 Le Contradicteur** : attaque les hypothèses, les fausses équivalences (footing/course/seuil), la récupération, les douleurs, l’accumulation d’intensité et l’effet des courses/du foot.
3. **🔵 Le Réaliste** : fait les comptes du volume réel/prévu, des performances vérifiées, de la marge technique, du sommeil, des jambes, des dates et de la réponse de l'utilisateur.
4. **🟡 Le Stratège** : programme l'ensemble semaine/mois, distingue stimulus et fatigue, priorise le 10 km/HYROX et propose une organisation concrète optimale.
5. **⚖️ Le Maître de cérémonie** : organise un vrai échange avec objections et réponses, arbitre, nomme les compromis, **tranche clairement** et fournit le plan adopté.

Format conseillé pour le Conseil :
- **Question et faits connus** (incluant résultats confirmés, dates, hypothèses).
- **Premier tour :** position distincte des 4 conseillers.
- **Débat / objections :** chaque rôle répond aux autres ; on peut changer d'avis avec justification.
- **Arbitrage final du Maître de cérémonie :** décision motivée **non ambiguë**, programme concret et seuils de révision.
- **Pas d’unanimité artificielle, pas de théâtre inutile, pas de retournement gratuit de recommandation**. Expliquer tout changement par une nouvelle donnée ou un meilleur argument.
- Le Conseil n'est pas obligatoire pour chaque réponse : s'en servir quand l'utilisateur le demande, sinon coach concis, direct et exigeant.

## 11. MES PRÉFÉRENCES DE COACHING ET DE FORMAT

- Langue **française**, ton direct, précis, exigeant, sportif ; ni compliments artificiels ni pessimisme réflexe.
- **Quatre séances fixes de musculation** (Pecs/Triceps, Dos/Biceps, Quadriceps, Ischios/Fessiers), généralement 3 séries de travail, un 🔴 exercice prioritaire de progression par séance. Échauffement non compté, charges des machines séparées.
- **Modèles copier-coller** pour les séances, avec date, objectif, exercices numérotés, référence fiable, objectif et **S1: / S2: / S3:** à remplir. Ne pas réordonner/enlever les exercices sans raison.
- Quand je donne des résultats (ex. S1/S2/S3), **enregistrer réellement ce qui a été effectué**, distinguer série complète vs meilleur essai isolé, noter l’intention (séance allégée volontaire) et comparer avec la précédente.
- Après certaines séances, **QCM simple** avec 2–4 questions ciblées : difficulté /10, niveau d’énergie, capacité à continuer, FC, douleurs/récupération. QCM préférable aux réponses ouvertes si pertinent ; ne pas demander les mêmes données déjà connues.
- Pour les sorties course, croiser **allure + FC moyenne/max + RPE + profil + nature de la séance + marge**. Une séance non épuisante n'est pas nécessairement facile en physiologie.
- **J'aime être actif souvent** : si un jour doit être léger, proposer marche/piscine/mobilité/haut du corps plutôt que repos strict par défaut, tout en expliquant quand un repos complet est la bonne décision.
- Les séances doivent **servir un but défini**, pas accumuler du volume pour le compteur.
- Les rappels automatiques peuvent vivre dans d'autres fils de conversation ; ne pas promettre qu'une automatisation est modifiée/supprimée sans une action validée.
- Différencier toujours **planning adopté, planning provisoire, séance réalisée, résultat non communiqué**, et ne pas inventer les détails manquants.

## 12. AUTOMATISATIONS ET ORGANISATION DU PROJET

- Le projet ChatGPT existant est nommé **« Coach sportif »**. Il est conseillé de créer une **nouvelle discussion dans ce même projet chaque semaine**, plutôt que de garder un fil gigantesque, et de joindre ce dossier au projet afin que les nouveaux fils puissent y revenir.
- **Récap sportif du matin** : tâche quotidienne prévue à **7h00 heure de Paris**, ID `6a9938fbfdfc8191ae214d06866828b6`. Réactivation / configuration confirmée au 2 nov. Elle doit rappeler les séances du jour avec objectifs et adaptation selon récupération.
- **Planning sportif pour agenda** : tâche hebdomadaire prévue **dimanche soir vers 19h, heure de Paris**, ID `6ae8667a082c8191a10e0c1e6c52da00`, recréation confirmée au début novembre. Prépare une semaine et **si possible un vrai fichier iCalendar .ics** par séance importable manuellement ; ne jamais dire qu'il a été automatiquement ajouté à Google Agenda.
- Des alertes de courses proches de Laval et de HYROX Paris/Lyon ont aussi été demandées, mais ne pas créer de doublon d’automatisation sans vérifier l'existant.
- L'ancienne discussion pose un souci de chargement sur l'application mobile (« Connexion réseau perdue, tentative de reconnexion ») alors que les autres discussions marchent ; la conservation du contexte dans de nouveaux fils au sein du projet vise à éviter l'énorme fil, **pas à garantir la résolution du bug**.

## 13. AUTRES INFORMATIONS SPORTIVES UTILES

### Profil et suivi corporel
- 24 ans, ~**1,76 m**. Poids historique fluctuant selon périodes (**environ 89–98 kg** en 2026) ; **poids du jour inconnu** : demander une mesure récente avant de calculer des cibles précises ou des ratios.
- A perdu/gagné du poids à plusieurs reprises ; souhaite perdre de la masse grasse avec alimentation soutenable tout en progressant en force, course et football.
- Nutrition : généralement **pas de petit déjeuner**, thé non sucré le matin (~1 L), n'aime pas le poisson sauf saumon/thon, a utilisé whey Prozis Zero Diet chocolat brownie. Il n'aime pas manger juste avant course ou football. Ne pas prescrire de déficit sévère lors de semaines chargées.
- Créatine évoquée mais **pas confirmée comme démarrée**.
- Montre Garmin Epix Pro 2, salle Basic-Fit, équipement élastiques SmartWorkout, pas nécessairement de SkiErg à sa disposition. Sled Push utilisé dans une autre salle / TKL.

### Antécédents et sports
- Antécédents de blessures/claquages **ischios** notamment novembre 2025 ; être attentif aux changements de direction, sprints football, et excentriques lourds à proximité des courses. Ne pas extrapoler l’existence actuelle d’une blessure.
- Foot USLBA, matchs le dimanche habituellement. La capacité à faire foot + course + musculation a été démontrée par plusieurs séances, mais la tolérance à l'effort n'est pas une garantie de récupération.
- Triathlon S test du **27 juin 2026** : natation environ **750 m en ~30 min** (Garmin sous-comptait 564 m / 29:53) ; vélo **10,03 km en 29:29 (107 m D+)** + **10,04 km en 25:35 (80 m D+)** suite à séparation accidentelle de l'enregistrement ; course **5 km en 28:31 (+44 m)** ; transitions T1 1:28 / T2 0:43.
- Référence autre : semi-marathon couru en **2h03** tranquillement ; 16 km en **1h26** en juillet 2026, selon historique ; ancien 10 km **52:50**.
- Natation actuelle souvent **500–600 m** ; 500 m en 19:23 avec FC moyenne 122 le 20 octobre ; 600 m le 27 octobre. Ne pas surestimer la dépense ou la récupération garantie de la piscine.

## 14. PROCÉDURE À SUIVRE DANS LE NOUVEAU FIL

1. Commencer par confirmer avoir lu ce dossier et résumer **en 4–6 phrases** ce qui fait la spécificité du coaching (objectifs, préférences, 4 muscu, foot, suivi Garmin, Conseil).
2. **Ne pas repartir de zéro** : les charges et chronos ci-dessus constituent les références documentées.
3. Avant de planifier **une journée**, vérifier les séances effectuées depuis le **3 novembre 2026 au matin**, car mardi et les jours suivants n’étaient pas encore attestés dans l’ancien fil.
4. Privilégier une décision ferme et expliquée, ajustable selon **nouvelles données**. Ne pas inventer de séances ni faire passer une programmation pour une confirmation.
5. Lorsque je demande **« passe ça au conseil »**, appliquer intégralement le protocole à cinq rôles analytiques.
6. Après mes séances, poser périodiquement un QCM utile ; analyser progression à partir de chronos/FC/RPE et non simplement confirmer mes impressions.
7. Si je souhaite un **planning hebdomadaire**, donner un format bloc-notes prêt à copier et, à la demande, un `.ics` importable avec horaires confirmés seulement.

### PREMIER MESSAGE D'OUVERTURE RECOMMANDÉ

> Tu es désormais mon coach sportif dans ce nouveau fil du projet « Coach sportif ». Lis intégralement le dossier joint et adopte ses règles, en particulier le « Conseil » à cinq rôles, les quatre séances fixes, l'analyse objective et les QCM de bilan. Nous sommes au début de novembre 2026. Je veux que tu maintiennes mes références exactes et que tu distingues résultats confirmés et séances prévues. Commence par me dire ce que tu as retenu de ma situation, puis demande-moi uniquement ce qui manque depuis le 3 novembre au matin. Notre ambition est le 10 km en 45 minutes fin décembre et l'HYROX en 2027.

---

**NOTE FINALE** — Ce dossier reprend les résultats et les décisions accessibles dans les discussions, mais **ne remplace pas un export complet de Garmin**. Des séances peuvent manquer si elles n'ont pas été communiquées. Toute donnée contradictoire doit être arbitrée avec l'utilisateur. L'autonomie du coach dépend de la précision des retours après chaque entraînement.


---

## ANNEXE — INSTRUCTIONS PROJET D’ORIGINE (NON MODIFIÉES)

# Instructions du projet « Coach sportif » — à copier dans les instructions du projet

Tu agis comme un coach sportif personnel **exigeant, cohérent et factuel**, pas comme un assistant complaisant. Le principe : **maximiser le travail assimilable, pas la fatigue accumulée**. Choisir clairement les séances, expliquer pourquoi, et ne modifier une décision qu'avec une information nouvelle ou un meilleur raisonnement.

## Objectifs
- 10 km en **45:00 (4:30/km)** à une corrida de fin décembre 2026 ; objectif ambitieux à confronter aux résultats ; course **10 km du Maine Libre le 21/11/2026** pour recalibrer.
- **HYROX Paris/Lyon 2027**, semi-marathon 2027, progression force/endurance, perte de poids progressive.
- 4 séances fixes de musculation par semaine : **Quadriceps**, **Dos/Biceps**, **Pecs/Triceps**, **Ischios/Fessiers**. Football souvent mardi et vendredi, matchs dimanche, natation et course. Aime et tolère les journées multi-sports ; ne pas surprotéger mais compter la vraie charge.
- Antécédents ischios : attention aux charges excentriques/sprints proches des courses et du foot.

## Gestion des preuves
- Conserve les références d'exercices *par machine/variante* et les séries réellement validées. Ne mélange pas développé barre et machine ni les différentes presses/squats.
- **Séance prévue ≠ séance effectuée.** Pour chaque résultat, noter date, exercice/distance, charge/allure, séries, FC et ressenti ; comparer à la référence.
- Pour chaque séance muscu demandée, fournir un **bloc-notes copier-coller** avec références, objectifs et champs **S1/S2/S3** ; typiquement 3 séries effectives et 1 exercice de progression 🔴.
- Pour la course, analyser chrono, km, dénivelé, FC moyenne/max, RPE/10, marge au même rythme et récupération. Une course « pas à fond » n'est pas automatiquement facile. Garmin au 3/11 : FC max 188, FC seuil estimée 176, allure seuil estimée 4:46/km. Zones récemment basées sur % seuil.
- **QCM interactifs** courts (2–4 questions utiles) après les séances importantes ; ne pas poser les mêmes questions déjà répondues.

## Conseil sportif (mot déclencheur « passe ça au conseil »)
Faire débattre **5 rôles analytiques (non 5 agents indépendants)** : 🟢 Optimiste (ambition), 🔴 Contradicteur (risques/angles morts), 🔵 Réaliste (données), 🟡 Stratège (charge et périodisation), ⚖️ Maître de cérémonie (arbitrage final ferme). Inclure arguments distincts, objections/réponses réelles, possibilité de révision, conclusion motivée et planning concret. Pas de théâtre gratuit ni de justification inventée.

## Ton
Français, direct, précis, sans flatterie ni conservatisme réflexe. Je veux être recadré si une séance est mal placée. Je préfère une séance active pertinente à un repos imposé par réflexe, mais accepte le repos quand il est nécessaire.

## Source à utiliser systématiquement
Le fichier **DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md** contient le suivi exhaustif documenté au 3 novembre 2026. Le consulter pour les charges exactes, séances récentes, calendrier et décisions. Préférer les mises à jour plus récentes lorsqu'elles existent.


---

## DONNÉES STRUCTURÉES IMPORTABLES (COPIE JSON)

```json
{
  "dataset_version": "2026-11-03-v1",
  "as_of": "2026-11-03",
  "time_of_day_context": "après le bilan du matin et avant toute séance du mardi",
  "timezone": "Europe/Paris",
  "provenance": {
    "primary": "DOSSIER_COMPLET_COACH_SPORTIF_2026-11-03.md",
    "secondary": "INSTRUCTIONS_PROJET_COACH_SPORTIF.md",
    "note": "Données manuelles documentées, non export Garmin complet ; aucune séance réalisée mardi 3 au dernier bilan utilisateur."
  },
  "profile": {
    "age_years_as_of_2026_11_03": 24,
    "sex_reported": "homme",
    "height_cm": 176,
    "current_weight_kg": null,
    "weight_history_range_2026_kg_approx": [
      89,
      98
    ],
    "equipment": [
      "Samsung Galaxy S25 Ultra",
      "Garmin Epix Pro 2",
      "Basic-Fit",
      "élastiques SmartWorkout"
    ],
    "sports": [
      "musculation",
      "course à pied",
      "football",
      "natation",
      "HYROX",
      "vélo/triathlon occasionnels"
    ],
    "strength_sessions_per_week_target": 4,
    "football_training_usual_days": [
      "mardi",
      "vendredi"
    ],
    "football_training_this_week": "mardi 03/11 non confirmé ; vendredi 06/11 non confirmé",
    "past_injuries": "antécédents de claquages/fragilités des ischio-jambiers, notamment novembre 2025 ; pas de blessure actuelle confirmée",
    "coaching_principle": "maximiser le travail assimilable, pas la fatigue cumulée",
    "nutrition_preferences": [
      "pas de petit-déjeuner habituellement",
      "thé sans sucre le matin ~1 L",
      "poisson peu apprécié sauf saumon/thon",
      "évite de manger juste avant courir ou football",
      "whey utilisée par le passé ; créatine non confirmée"
    ],
    "preferences": [
      "coach exigeant non complaisant",
      "séances précises avec décision motivée",
      "4 muscu sauf ajustement justifié",
      "S1/S2/S3 et une priorité 🔴",
      "QCM brefs 2 à 4 questions utiles",
      "planning très visuel jour par jour + emojis + statuts + synthèse",
      "différencier machine et variante",
      "ne pas inventer de séances",
      "Conseil à cinq rôles si « passe ça au conseil »"
    ]
  },
  "goals": [
    {
      "id": "10k_dec_2026",
      "sport": "running",
      "target_distance_m": 10000,
      "target_duration_seconds": 2700,
      "pace_s_per_km": 270,
      "target_date_window": "fin décembre 2026",
      "status": "ambitieux_non_validé",
      "priority": "majeure"
    },
    {
      "id": "race_maine_libre",
      "sport": "running",
      "event_date": "2026-11-21",
      "target": "course sérieuse pour calibrer les allures",
      "registration_status": "confirmed"
    },
    {
      "id": "hyrox_2027",
      "sport": "hyrox",
      "target": "participer à HYROX Paris ou Lyon en 2027",
      "status": "préparation",
      "priority": "majeure"
    },
    {
      "id": "half_marathon_2027",
      "sport": "running",
      "target": "semi-marathon mars/avril 2027",
      "status": "projet"
    },
    {
      "id": "body_comp",
      "sport": "general",
      "target": "perte de poids progressive tout en conservant performance et force",
      "status": "en_cours"
    }
  ],
  "garmin_snapshot": {
    "recorded_date": "2026-11-03",
    "device": "Epix Pro Gen 2",
    "max_hr_bpm_reported": 188,
    "rest_hr_bpm_historic_approx": 55,
    "lactate_threshold_estimate": {
      "hr_bpm": 176,
      "pace_sec_per_km": 286,
      "power_w": 438,
      "relative_power_w_per_kg": 4.9,
      "is_estimate": true
    },
    "hr_zone_method_selected": "pourcentage FC au seuil lactique",
    "watch_setting_scope_confirmed": false,
    "zones_reported_bpm_approx": {
      "Z1": [
        100,
        114
      ],
      "Z2": [
        116,
        136
      ],
      "Z3": [
        137,
        160
      ],
      "Z4": [
        162,
        178
      ],
      "Z5_theoretical": [
        180,
        201
      ]
    },
    "caveats": [
      "ne jamais chercher à atteindre 201 bpm malgré la plage Z5 théorique supérieure à FCmax 188",
      "ne pas imposer la zone Z2 116–136 comme unique définition du footing facile",
      "le poids implicite 438/4,90 n’est pas une pesée",
      "recontrôler périodiquement après courses utiles"
    ]
  },
  "events": [
    {
      "id": "corps_nuds_night",
      "name": "Corps-Nuds By Night",
      "date": "2026-11-07",
      "distance_m": 16000,
      "category": "trail_nocturne",
      "registration_status": "envisaged_unconfirmed",
      "strategy": "endurance contrôlée, sans record, attention match lendemain"
    },
    {
      "id": "maine_libre_10k",
      "name": "10 km du Maine Libre",
      "date": "2026-11-21",
      "distance_m": 10000,
      "registration_status": "confirmed",
      "strategy": "course de référence"
    },
    {
      "id": "corrida_dec",
      "name": "Corrida fin décembre",
      "date": null,
      "date_window": "fin décembre 2026",
      "distance_m": 10000,
      "registration_status": "course_non_choisie",
      "goal_time_seconds": 2700
    },
    {
      "id": "hyrox_2027",
      "name": "HYROX Paris / Lyon",
      "date": null,
      "date_window": "2027",
      "registration_status": "dates/inscriptions non déterminées dans le dossier"
    },
    {
      "id": "half_marathon_2027",
      "name": "Semi-marathon 2027",
      "date": null,
      "date_window": "mars/avril 2027",
      "registration_status": "non connu"
    }
  ],
  "football_known_match_dates": [
    "2026-11-08",
    "2026-11-15",
    "2026-11-29",
    "2026-12-06",
    "2026-12-13"
  ],
  "coach_policy": {
    "weekly_strength_splits": [
      "dos_biceps",
      "quadriceps",
      "pecs_triceps",
      "ischios_fessiers"
    ],
    "working_sets_typical": 3,
    "warmup_separate": true,
    "priority_exercise_symbol": "🔴",
    "update_only_on_new_evidence": true,
    "session_planned_never_equals_completed": true,
    "two_to_four_qcm_on_relevant_feedback": true,
    "no_interpolation_of_missing_sets": true,
    "council_roles": [
      "optimiste",
      "contradicteur",
      "réaliste",
      "stratège",
      "maître de cérémonie"
    ]
  },
  "exercise_references": [
    {
      "exercise_variant_id": "bench_barbell",
      "split": "pecs_triceps",
      "name": "Développé couché barre",
      "equipment_variant": "barre libre",
      "load_basis": "total",
      "reference": {
        "sets": [
          [
            80,
            10
          ],
          [
            80,
            10
          ],
          [
            80,
            10
          ]
        ],
        "load_unit": "kg",
        "rep_unit": "repetitions"
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-19",
      "notes": "distinct du développé machine"
    },
    {
      "exercise_variant_id": "developpe_machine_unspecified",
      "split": "pecs_triceps",
      "name": "Développé machine",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "best_observed_set": [
          90,
          10
        ],
        "sets_complete": null,
        "load_unit": "kg"
      },
      "reference_type": "best_set_only",
      "last_complete_session_date_if_known": null,
      "notes": "ancien meilleur essai, non 3 séries prouvées"
    },
    {
      "exercise_variant_id": "chest_press",
      "split": "pecs_triceps",
      "name": "Chest Press",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            50,
            10
          ],
          [
            50,
            10
          ],
          [
            50,
            10
          ]
        ],
        "load_unit": "kg"
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "sets": 3,
        "load_kg": 50,
        "reps_each": 12
      }
    },
    {
      "exercise_variant_id": "pec_fly",
      "split": "pecs_triceps",
      "name": "Pec Fly",
      "equipment_variant": "même machine à identifier",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            79,
            10
          ],
          [
            79,
            10
          ],
          [
            79,
            10
          ]
        ],
        "load_unit": "kg"
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "try_next_machine_increment": true
      },
      "notes": "79 x10x3 facile, monter seulement si même machine"
    },
    {
      "exercise_variant_id": "triceps_high_cable",
      "split": "pecs_triceps",
      "name": "Triceps poulie haute",
      "equipment_variant": "poulie haute",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            22.5,
            10
          ],
          [
            22.5,
            10
          ],
          [
            22.5,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "load_kg": 22.5,
        "sets": 3,
        "reps_each": 12
      }
    },
    {
      "exercise_variant_id": "triceps_low_cable_pos2",
      "split": "pecs_triceps",
      "name": "Triceps poulie basse position 2",
      "equipment_variant": "poulie basse position 2",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            24.5,
            10
          ],
          [
            24.5,
            10
          ],
          [
            24.5,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "load_kg": 24.5,
        "sets": 3,
        "reps_each": 12
      }
    },
    {
      "exercise_variant_id": "standing_lateral_abs",
      "split": "pecs_triceps",
      "name": "Abdos latéraux debout",
      "equipment_variant": "machine/charge à confirmer",
      "load_basis": "unknown",
      "reference": {
        "sets": [
          [
            32,
            10
          ],
          [
            32,
            10
          ],
          [
            32,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "notes": "non réalisés le 19 octobre"
    },
    {
      "exercise_variant_id": "squat_barbell",
      "split": "quadriceps",
      "name": "Squat barre",
      "equipment_variant": "barre",
      "load_basis": "total",
      "reference": {
        "sets": [
          [
            50,
            10
          ],
          [
            50,
            10
          ],
          [
            50,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02",
      "future_target_not_validated": {
        "sets": 3,
        "load_kg": 50,
        "reps_each": 12,
        "condition": "jambes fraîches"
      }
    },
    {
      "exercise_variant_id": "high_squat_tkl",
      "split": "quadriceps",
      "name": "High Squat TKL",
      "equipment_variant": "machine TKL High Squat",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            40,
            10
          ],
          [
            40,
            10
          ],
          [
            40,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02",
      "notes": "ne pas confondre avec Perfect Squat/Smith/autres machines"
    },
    {
      "exercise_variant_id": "walking_lunges_44",
      "split": "quadriceps",
      "name": "Fentes marchées",
      "equipment_variant": "exécution et équipement identiques au 02/11",
      "load_basis": "load_basis_unknown",
      "reference": {
        "sets": [
          {
            "load_kg": 44,
            "steps": 32
          },
          {
            "load_kg": 44,
            "steps": 33
          },
          {
            "load_kg": 44,
            "steps": 33
          }
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02",
      "future_target_not_validated": {
        "sets": 3,
        "load_kg": 44,
        "steps_each": 33,
        "condition": "bonne technique et journée dédiée"
      },
      "notes": "nouvelle référence vs 42 kg ; base de charge total/par main non documentée",
      "priority_symbol": "🔴"
    },
    {
      "exercise_variant_id": "leg_extension_unilateral",
      "split": "quadriceps",
      "name": "Leg Extension unilatérale",
      "equipment_variant": "machine unilatérale",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            59,
            10
          ],
          [
            59,
            10
          ],
          [
            59,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02"
    },
    {
      "exercise_variant_id": "adductors",
      "split": "quadriceps",
      "name": "Adducteurs",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            66,
            10
          ],
          [
            66,
            10
          ],
          [
            66,
            10
          ]
        ],
        "best_isolated_set": [
          73,
          10
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "sets": 3,
        "load_kg": 73,
        "reps_each": 10
      },
      "notes": "73x10 = meilleur essai isolé, pas 3 séries validées"
    },
    {
      "exercise_variant_id": "calves_leg_press",
      "split": "quadriceps",
      "name": "Mollets à la Leg Press",
      "equipment_variant": "Leg Press",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            134,
            10
          ],
          [
            134,
            10
          ],
          [
            134,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02"
    },
    {
      "exercise_variant_id": "sled_push_tkl",
      "split": "quadriceps",
      "name": "Sled Push",
      "equipment_variant": "TKL",
      "load_basis": "machine",
      "reference": {
        "load_kg": 120,
        "round_trips": 10,
        "distance_each_m": null
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "notes": "longueur inconnue ; non comparable directement HYROX"
    },
    {
      "exercise_variant_id": "abs_tek",
      "split": "ischios_fessiers",
      "name": "Abdos machine TEK",
      "equipment_variant": "TEK",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            36,
            10
          ],
          [
            36,
            10
          ],
          [
            36,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02",
      "notes": "fait dans séance quadriceps du 02/11, apparaît aussi dans historique ischios"
    },
    {
      "exercise_variant_id": "abductors",
      "split": "ischios_fessiers",
      "name": "Abducteurs",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            66,
            10
          ],
          [
            66,
            10
          ],
          [
            66,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-11-02",
      "notes": "fait aussi dans la séance quadriceps du 02/11"
    },
    {
      "exercise_variant_id": "rdl_dumbbells",
      "split": "ischios_fessiers",
      "name": "RDL haltères",
      "equipment_variant": "deux haltères",
      "load_basis": "per_hand",
      "reference": {
        "sets": [
          [
            30,
            10
          ],
          [
            30,
            10
          ],
          [
            30,
            10
          ]
        ],
        "load_unit": "kg par main"
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-22",
      "future_target_not_validated": {
        "sets": 3,
        "load_kg_per_hand": 30,
        "reps_each": 12
      },
      "priority_symbol": "🔴"
    },
    {
      "exercise_variant_id": "swiss_ball_ham",
      "split": "ischios_fessiers",
      "name": "Ischios sur ballon",
      "equipment_variant": "ballon",
      "load_basis": "bodyweight",
      "reference": {
        "sets_seconds": [
          40,
          40,
          40
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-22"
    },
    {
      "exercise_variant_id": "hip_thrust",
      "split": "ischios_fessiers",
      "name": "Hip Thrust",
      "equipment_variant": "équipement non précisé",
      "load_basis": "unknown",
      "reference": {
        "last_session_sets": [
          [
            60,
            10
          ],
          [
            60,
            10
          ],
          [
            60,
            10
          ]
        ],
        "best_full_sets": [
          [
            60,
            12
          ],
          [
            60,
            12
          ],
          [
            60,
            12
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-22",
      "notes": "meilleure séance n’est pas la dernière"
    },
    {
      "exercise_variant_id": "prone_leg_curl",
      "split": "ischios_fessiers",
      "name": "Prone Leg Curl",
      "equipment_variant": "machine Prone Leg Curl",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            23,
            10
          ],
          [
            23,
            10
          ],
          [
            23,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-22",
      "notes": "distinct du Back Extension"
    },
    {
      "exercise_variant_id": "back_extension_glutes",
      "split": "ischios_fessiers",
      "name": "Back Extension fessiers",
      "equipment_variant": "machine Back Extension",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            34,
            10
          ],
          [
            34,
            10
          ],
          [
            34,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    },
    {
      "exercise_variant_id": "pullups_bodyweight",
      "split": "dos_biceps",
      "name": "Tractions normales",
      "equipment_variant": "poids du corps",
      "load_basis": "bodyweight",
      "reference": {
        "best_full_sets_reps": [
          7,
          7,
          7
        ],
        "other_sets_reps": [
          7,
          7,
          5
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "future_target_not_validated": {
        "sets": 3,
        "reps_each": 8,
        "prerequisite": "valider 7/7/7"
      }
    },
    {
      "exercise_variant_id": "vertical_row_machine_66",
      "split": "dos_biceps",
      "name": "Tirage vertical",
      "equipment_variant": "machine du 7 octobre, identité à préciser",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            66,
            10
          ],
          [
            66,
            10
          ],
          [
            66,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-07",
      "notes": "NE PAS mélanger avec machine habituelle 120 kg"
    },
    {
      "exercise_variant_id": "vertical_row_machine_120",
      "split": "dos_biceps",
      "name": "Tirage vertical",
      "equipment_variant": "AUTRE machine habituelle distincte",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            120,
            10
          ],
          [
            120,
            10
          ],
          [
            120,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null,
      "notes": "référence historique distincte de 66 kg"
    },
    {
      "exercise_variant_id": "horizontal_row",
      "split": "dos_biceps",
      "name": "Tirage horizontal",
      "equipment_variant": "machine du 7 octobre",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            66,
            10
          ],
          [
            73,
            10
          ],
          [
            73,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-07",
      "future_target_not_validated": {
        "sets": 3,
        "load_kg": 73,
        "reps_each": 10
      }
    },
    {
      "exercise_variant_id": "pullover",
      "split": "dos_biceps",
      "name": "Pull-over",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            27,
            10
          ],
          [
            27,
            10
          ],
          [
            27,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    },
    {
      "exercise_variant_id": "rear_delt",
      "split": "dos_biceps",
      "name": "Rear Delt",
      "equipment_variant": "machine non précisée",
      "load_basis": "machine",
      "reference": {
        "sets": [
          [
            52,
            10
          ],
          [
            52,
            10
          ],
          [
            52,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    },
    {
      "exercise_variant_id": "lateral_raise",
      "split": "dos_biceps",
      "name": "Élévations latérales",
      "equipment_variant": "charge par main non explicitée",
      "load_basis": "unknown",
      "reference": {
        "sets": [
          [
            6.8,
            10
          ],
          [
            6.8,
            10
          ],
          [
            6.8,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    },
    {
      "exercise_variant_id": "biceps_curl_machine_37",
      "split": "dos_biceps",
      "name": "Biceps curl",
      "equipment_variant": "machine ou variante non précisée",
      "load_basis": "unknown",
      "reference": {
        "warmup_set": [
          20,
          10
        ],
        "working_sets": [
          [
            37.5,
            8
          ],
          [
            37.5,
            6
          ]
        ],
        "third_working_set": null
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": "2026-10-07",
      "notes": "pas 3 séries à 37,5kg validées ; échauffement distinct"
    },
    {
      "exercise_variant_id": "preacher_curl",
      "split": "dos_biceps",
      "name": "Curl pupitre",
      "equipment_variant": "pupitre",
      "load_basis": "unknown",
      "reference": {
        "sets": [
          [
            32.5,
            10
          ],
          [
            32.5,
            10
          ],
          [
            32.5,
            10
          ]
        ]
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    },
    {
      "exercise_variant_id": "dumbbell_curl",
      "split": "dos_biceps",
      "name": "Curl haltères",
      "equipment_variant": "haltères",
      "load_basis": "per_hand",
      "reference": {
        "sets": [
          [
            16,
            10
          ],
          [
            16,
            10
          ],
          [
            16,
            10
          ]
        ],
        "load_unit": "kg par main"
      },
      "reference_type": "reference_confirmed",
      "last_complete_session_date_if_known": null
    }
  ],
  "completed_endurance_activities": [
    {
      "id": "run_2026_09_10",
      "date": "2026-09-10",
      "sport": "running",
      "distance_m": 5000,
      "duration_seconds": 1878,
      "avg_hr_bpm": 139,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "allure déclarée 6:15/km approximative"
    },
    {
      "id": "run_2026_09_22",
      "date": "2026-09-22",
      "sport": "running",
      "distance_m": 4480,
      "duration_seconds": null,
      "avg_hr_bpm": 136,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "allure approximative 6:00/km ; temps exact non fourni"
    },
    {
      "id": "run_2026_09_30",
      "date": "2026-09-30",
      "sport": "running",
      "distance_m": 5000,
      "duration_seconds": 1524,
      "avg_hr_bpm": 164,
      "ascent_m": 56,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "course avec dénivelé, 25:24 ; RPE rapporté 7–8/10",
      "rpe_range_reported": [
        7,
        8
      ]
    },
    {
      "id": "run_2026_10_26",
      "date": "2026-10-26",
      "sport": "running",
      "distance_m": 5120,
      "duration_seconds": null,
      "avg_hr_bpm": 132,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "durée non documentée"
    },
    {
      "id": "slow_2026_10_26",
      "date": "2026-10-26",
      "sport": "running_or_walk_recorded",
      "distance_m": 1440,
      "duration_seconds": null,
      "avg_hr_bpm": null,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "montre enregistrée marche à 7:08/km, utilisateur la considère comme footing très lent"
    },
    {
      "id": "run_a_2026_10_27",
      "date": "2026-10-27",
      "sport": "running",
      "distance_m": 5010,
      "duration_seconds": 1981,
      "avg_hr_bpm": 142,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report"
    },
    {
      "id": "run_b_2026_10_27",
      "date": "2026-10-27",
      "sport": "running",
      "distance_m": 5100,
      "duration_seconds": 1687,
      "avg_hr_bpm": 161,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "pause entre deux courses inconnue"
    },
    {
      "id": "walk_2026_10_27",
      "date": "2026-10-27",
      "sport": "walking",
      "distance_m": 2210,
      "duration_seconds": null,
      "avg_hr_bpm": null,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report"
    },
    {
      "id": "swim_2026_10_27",
      "date": "2026-10-27",
      "sport": "swimming",
      "distance_m": 600,
      "duration_seconds": null,
      "avg_hr_bpm": null,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report"
    },
    {
      "id": "swim_2026_10_20",
      "date": "2026-10-20",
      "sport": "swimming",
      "distance_m": 500,
      "duration_seconds": 1163,
      "avg_hr_bpm": 122,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report"
    },
    {
      "id": "football_2026_10_20",
      "date": "2026-10-20",
      "sport": "football",
      "distance_m": 8830,
      "duration_seconds": 4039,
      "avg_hr_bpm": 158,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report",
      "notes": "entraînement, classé seuil / aérobie élevée Garmin"
    },
    {
      "id": "run_2026_10_29",
      "date": "2026-10-29",
      "sport": "running",
      "distance_m": 6020,
      "duration_seconds": 1842,
      "avg_hr_bpm": 159,
      "ascent_m": 18,
      "rpe_10": 6,
      "status": "completed",
      "provenance": "user_report",
      "notes": "arrêt par manque de temps, pas épuisement"
    },
    {
      "id": "walk_2026_10_29",
      "date": "2026-10-29",
      "sport": "walking",
      "distance_m": 1180,
      "duration_seconds": 883,
      "avg_hr_bpm": 111,
      "ascent_m": null,
      "rpe_10": null,
      "status": "completed",
      "provenance": "user_report"
    },
    {
      "id": "run_2026_11_02",
      "date": "2026-11-02",
      "sport": "running",
      "distance_m": 5000,
      "duration_seconds": 1442,
      "avg_hr_bpm": 166,
      "ascent_m": 21,
      "rpe_10": 6,
      "status": "completed",
      "provenance": "user_report",
      "notes": "RP Garmin ; séance initialement prévue facile mais effectivement soutenue ; pouvait peut-être courir 2–3 km de plus : auto-estimation non vérifiée",
      "planned_session_type": "easy",
      "actual_session_type": "sustained",
      "avg_pace_seconds_per_km_reported": 288
    }
  ],
  "completed_strength_activities": [
    {
      "id": "strength_quad_2026_11_02",
      "date": "2026-11-02",
      "split": "quadriceps",
      "status": "completed",
      "rpe_10": 6,
      "work_sets_count": 21,
      "exercise_count": 7,
      "exercises": [
        {
          "exercise_variant_id": "squat_barbell",
          "sets": [
            {
              "load_kg": 50,
              "reps": 10
            },
            {
              "load_kg": 50,
              "reps": 10
            },
            {
              "load_kg": 50,
              "reps": 10
            }
          ]
        },
        {
          "exercise_variant_id": "high_squat_tkl",
          "sets": [
            {
              "load_kg": 40,
              "reps": 10
            },
            {
              "load_kg": 40,
              "reps": 10
            },
            {
              "load_kg": 40,
              "reps": 10
            }
          ]
        },
        {
          "exercise_variant_id": "walking_lunges_44",
          "sets": [
            {
              "load_kg": 44,
              "steps": 32
            },
            {
              "load_kg": 44,
              "steps": 33
            },
            {
              "load_kg": 44,
              "steps": 33
            }
          ]
        },
        {
          "exercise_variant_id": "leg_extension_unilateral",
          "sets": [
            {
              "load_kg": 59,
              "reps": 10
            },
            {
              "load_kg": 59,
              "reps": 10
            },
            {
              "load_kg": 59,
              "reps": 10
            }
          ]
        },
        {
          "exercise_variant_id": "calves_leg_press",
          "sets": [
            {
              "load_kg": 134,
              "reps": 10
            },
            {
              "load_kg": 134,
              "reps": 10
            },
            {
              "load_kg": 134,
              "reps": 10
            }
          ]
        },
        {
          "exercise_variant_id": "abs_tek",
          "sets": [
            {
              "load_kg": 36,
              "reps": 10
            },
            {
              "load_kg": 36,
              "reps": 10
            },
            {
              "load_kg": 36,
              "reps": 10
            }
          ]
        },
        {
          "exercise_variant_id": "abductors",
          "sets": [
            {
              "load_kg": 66,
              "reps": 10
            },
            {
              "load_kg": 66,
              "reps": 10
            },
            {
              "load_kg": 66,
              "reps": 10
            }
          ]
        }
      ],
      "notes": "réellement réalisé, pas séance légère ; fentes nouvelle référence ; inclut abduction additionnelle"
    }
  ],
  "historical_benchmarks": [
    {
      "id": "intervals_4x1k_sep2026",
      "date": "2026-09",
      "type": "intervals",
      "work_1000m_seconds": [
        262.8,
        264.3,
        264.2,
        263.8
      ],
      "recovery_seconds": "parfois >90",
      "status": "completed"
    },
    {
      "id": "intervals_5x1k_19oct",
      "date": "2026-10-19",
      "type": "intervals",
      "target": "5×1000m 4:27–4:30/km, récup 1:30",
      "status": "planned_not_completed"
    },
    {
      "id": "10k_old_pb",
      "date": null,
      "distance_m": 10000,
      "duration_seconds": 3170,
      "status": "historical_completed",
      "note": "ancien 10km 52:50"
    },
    {
      "id": "half_historic",
      "date": null,
      "distance_m": 21097.5,
      "duration_seconds": 7380,
      "status": "historical_completed",
      "note": "semi-marathon 2h03 tranquille"
    },
    {
      "id": "run_16k_jul",
      "date": "2026-07",
      "distance_m": 16000,
      "duration_seconds": 5160,
      "status": "historical_completed",
      "note": "16km en 1h26 selon ancien historique"
    },
    {
      "id": "triathlon_S_2026_06_27",
      "date": "2026-06-27",
      "status": "completed",
      "swim_user_estimate_m": 750,
      "swim_garmin_recorded_m": 564,
      "bike_legs": [
        {
          "distance_m": 10030,
          "duration_s": 1769,
          "ascent_m": 107
        },
        {
          "distance_m": 10040,
          "duration_s": 1535,
          "ascent_m": 80
        }
      ],
      "run": {
        "distance_m": 5000,
        "duration_s": 1711,
        "ascent_m": 44
      },
      "t1_s": 88,
      "t2_s": 43,
      "note": "vélo séparé par erreur de bouton ; natation environ 750 m en ~30 minutes, Garmin sous-comptait",
      "swim_garmin_duration_s": 1793,
      "swim_user_estimated_duration_s_approx": 1800
    }
  ],
  "weekly_plan": {
    "week_start": "2026-11-02",
    "week_end": "2026-11-08",
    "state": "validated_by_user",
    "completed_scope_as_of": "2026-11-03_early",
    "activities": [
      {
        "id": "20261102_run",
        "date": "2026-11-02",
        "sport": "running",
        "title": "5 km faciles (prescription), courus plus vite",
        "status": "completed",
        "details": {
          "planned_distance_m": 5000,
          "planned_pace_s_per_km_range": [
            360,
            395
          ],
          "actual_activity_id": "run_2026_11_02"
        }
      },
      {
        "id": "20261102_quad",
        "date": "2026-11-02",
        "sport": "strength",
        "title": "Quadriceps / Mollets / Abdos",
        "status": "completed",
        "details": {
          "actual_activity_id": "strength_quad_2026_11_02"
        }
      },
      {
        "id": "20261103_swim",
        "date": "2026-11-03",
        "sport": "swimming",
        "title": "Piscine facile",
        "status": "planned",
        "details": {
          "distance_m_range": [
            500,
            600
          ],
          "duration_min_range": [
            20,
            25
          ],
          "intensity": "facile"
        }
      },
      {
        "id": "20261103_back",
        "date": "2026-11-03",
        "sport": "strength",
        "title": "Dos/Biceps",
        "status": "planned",
        "details": {
          "duration_min_range": [
            45,
            60
          ],
          "split": "dos_biceps",
          "load": "habituelle",
          "rir_range": [
            1,
            2
          ],
          "references_see": "exercise_references ; exercices précis à confirmer dans séance construite"
        }
      },
      {
        "id": "20261103_football",
        "date": "2026-11-03",
        "sport": "football",
        "title": "Football vers 19h30",
        "status": "pending_confirmation",
        "details": {
          "provisional_time_local": "19:30"
        },
        "notes": "ne pas considérer comme entraînement confirmé"
      },
      {
        "id": "20261104_run",
        "date": "2026-11-04",
        "sport": "running",
        "title": "Endurance facile",
        "status": "planned",
        "details": {
          "distance_m": 10000,
          "pace_s_per_km_range": [
            360,
            395
          ],
          "rpe_10_range": [
            3,
            4
          ],
          "if_tired_distance_m_range": [
            6000,
            8000
          ]
        },
        "notes": "pas de finish rapide"
      },
      {
        "id": "20261104_chest",
        "date": "2026-11-04",
        "sport": "strength",
        "title": "Pecs/Triceps",
        "status": "planned",
        "details": {
          "split": "pecs_triceps",
          "intensity": "normale progressive"
        }
      },
      {
        "id": "20261105_ham",
        "date": "2026-11-05",
        "sport": "strength",
        "title": "Ischios/Fessiers + Abdos",
        "status": "planned",
        "details": {
          "split": "ischios_fessiers",
          "sets_per_exercise_approx": 2,
          "intensity": "légère",
          "avoid": [
            "RDL lourd",
            "échec musculaire"
          ]
        }
      },
      {
        "id": "20261106_swim",
        "date": "2026-11-06",
        "sport": "swimming",
        "title": "Piscine récupération active",
        "status": "planned",
        "details": {
          "distance_m_range": [
            500,
            600
          ],
          "duration_min_range": [
            20,
            25
          ]
        },
        "notes": "pas de fractionné ; foot vendredi pas confirmé"
      },
      {
        "id": "20261107_trail",
        "date": "2026-11-07",
        "sport": "running",
        "title": "Corps-Nuds By Night 16 km",
        "status": "pending_confirmation",
        "details": {
          "distance_m": 16000,
          "effort": "contrôlé"
        },
        "notes": "participation envisagée seulement"
      },
      {
        "id": "20261108_match",
        "date": "2026-11-08",
        "sport": "football",
        "title": "Match de football",
        "status": "planned",
        "details": {
          "duration_min": null
        },
        "notes": "temps de jeu à adapter récupération du trail ; aucune blessure présente affirmée"
      }
    ],
    "forecast_summary": {
      "running_distance_km_if_trail_and_wed_completed": 31,
      "strength_sessions_planned": 4,
      "strength_sessions_completed_as_of": 1,
      "swim_sessions_planned": 2,
      "football_training_pending_confirmation_count": 1,
      "football_match_planned_count": 1,
      "note": "prévu ≠ réalisé ; 31 km tient compte du trail hypothétique"
    }
  },
  "unconfirmed_questions": [
    "foot du mardi 3 novembre confirmé ?",
    "trail Corps-Nuds le 7 novembre : inscription/participation confirmée ?",
    "foot du vendredi 6 novembre prévu ?",
    "zones Garmin générales ou spécifiques course à pied ?",
    "poids actuel inconnu",
    "résultats depuis le matin du 3 novembre non connus"
  ]
}

```
