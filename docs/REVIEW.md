# REVIEW — Plan d'amélioration classé par criticité

> ✅ **PLAN LIVRÉ — DOC FIGÉ AU 2026-05-06.** Phases 0-4 toutes terminées (Quick Wins + V1-V4 sécurité/sync/cohérence schéma + V5-V6 squelette + cleanup). Quelques cases `[ ]` ci-dessous ne reflètent pas les résolutions postérieures (V1.2 secrets Pi 11-05, T1.1 tests 06-05, F9-Q5 `setup_db.py` 06-05, etc.) — **ne pas s'en servir comme TODO actuel**. Source de vérité courante : historique [CLAUDE.md](../CLAUDE.md) + [TODO_FEATURES.md](TODO_FEATURES.md).

> **Étape 7 (finale) de l'audit**. Transforme ~192 items (162 fixes [TODO_FIXES.md](TODO_FIXES.md) + ~30 features [TODO_FEATURES.md](TODO_FEATURES.md) + suggestions Claude) en un **plan d'action priorisé et exécutable**.
>
> **Méthode (2026-05-04, consigne utilisateur)** : 8 vagues + Quick Wins en tête, format mixte (narratif pour la vue d'ensemble + checklist `[ ]` exécutable pour les sections opérationnelles), pas de dates calendaires (juste phases logiques), suggestions Claude intégrées dans les vagues concernées (pas en backlog isolé).

---

## Sommaire

- [§1 Synthèse exécutive](#1--synthèse-exécutive)
- [§2 Méthodologie de priorisation](#2--méthodologie-de-priorisation)
- [§3 Quick Wins — démarrer fort](#3--quick-wins--démarrer-fort)
- [§4 Vague 1 — Sécurité critique exposée](#4--vague-1--sécurité-critique-exposée)
- [§5 Vague 2 — Bypass ownership + cascade](#5--vague-2--bypass-ownership--cascade)
- [§6 Vague 3 — Bloqueurs structurels](#6--vague-3--bloqueurs-structurels)
- [§7 Vague 4 — Sync fonctionnel (REST + WebSocket)](#7--vague-4--sync-fonctionnel-rest--websocket)
- [§8 Vague 5 — Cohérence schéma DB](#8--vague-5--cohérence-schéma-db)
- [§9 Vague 6 — Squelette uniforme + refactor archi](#9--vague-6--squelette-uniforme--refactor-archi)
- [§10 Vague 7 — Code mort + cleanup + documentation](#10--vague-7--code-mort--cleanup--documentation)
- [§11 Vague 8 — Features + Polish](#11--vague-8--features--polish)
- [§12 Graphe de dépendances](#12--graphe-de-dépendances)
- [§13 Items parallélisables](#13--items-parallélisables)
- [§14 Roadmap par phases](#14--roadmap-par-phases)

---

## §1 — Synthèse exécutive

### État actuel du projet

**Maturité fonctionnelle** : moyenne-haute. L'app est utilisable (login, sync, écrans), bien architecturée dans les grandes lignes (Hilt, Compose, Flow, Room, sync local-first + WebSocket push), avec des choix techniques solides (FastAPI async, asyncpg, Postgres triggers NOTIFY pour le push temps-réel).

**Maturité non-fonctionnelle** : faible. Audit révèle :
- **🔴 Sécurité catastrophique** : 4 routers entièrement publics exposés sur `<public-dns>` (n'importe qui peut lister/modifier/supprimer tous les users), secrets `change-me` en prod sur la Pi (Postgres + JWT), 6+ CRUDs avec bypass ownership.
- **🔴 Stabilité fragile** : Room sans Migration enregistrée → tout bump de schéma crash l'app pour les utilisateurs existants ; `--reload` actif en prod ; format wire des dates incompatible entre les 3 stacks (Z / +00:00 / +01:00) → bug de réécriture intempestive en sync confirmé par l'utilisateur ; 6 fonctions `Instant.parse` qui crashent sur le format Postgres legacy.
- **🔴 Sync partiellement cassée** : 5 endpoints serveur n'existent pas alors que l'app les appelle (CycleWorkout x2, ExerciseMuscle, SupersetExercise type mismatch, planned_workout_exercises bug delete sans userId), 5 entités sans push WebSocket (notifications, routine_*), `RemoteDataMerger.isRemoteNewer` faux à cause des dates.
- **🟠 Cohérence dégradée** : 44 endpoints POST côté Android = code mort, `MuscleWeeklySummary` entité fantôme, divergences Alembic ↔ `db_triggers/`, 4 colonnes Room camelCase vs canonique snake_case, 2 styles DAO co-existants, doublons stricts dans 3 DAOs, `RoutineTaskCheckDao` viole 2 couches (data → network + UI).
- **🟡 Dette technique** : politique squelette uniforme partiellement appliquée (~5 axes de divergence sur 27 modules), DBML obsolète sur certains points, indexes Postgres manquants, 2 ViewModels vides.

### Travail estimé

- **162 fixes** identifiés sur 9 étapes d'audit (étapes 1 → 5)
- **~30 features** prévues (de "refresh token" à "module Nutrition")
- **8 suggestions Claude** à valider

**Estimation grossière (sans dates)** :
- ⚡ Quick Wins : ~1-2 jours pour 14 items XS/S à fort impact
- 🔥 Vagues 1-3 (sécurité + bloqueurs) : phase critique, 2-4 semaines selon disponibilité
- ⭐ Vagues 4-6 (sync + cohérence) : phase consolidation, 4-8 semaines
- 💡 Vagues 7-8 (cleanup + features) : phase évolution, indéfini selon priorités

### Recommandation stratégique

1. **Faire les Quick Wins** d'abord (gain immédiat de qualité, réduit le bruit dans l'audit).
2. **Sécuriser la production** ensuite (Vagues 1+2) — c'est non-négociable si l'app est exposée publiquement.
3. **Lever les bloqueurs structurels** (Vague 3) avant tout refactor de schéma.
4. **Réparer la sync** (Vague 4) pour avoir un fonctionnement multi-device propre.
5. **Aligner schéma + archi** (Vagues 5+6) — long mais nécessaire pour une base saine.
6. **Cleanup + features** (Vagues 7+8) — au rythme des priorités utilisateur.

---

## §2 — Méthodologie de priorisation

### Critères de tri

Par ordre de priorité décroissante :

1. **Sécurité exposée publiquement** — un bug exploitable depuis Internet pèse plus qu'un bug local
2. **Stabilité production** — empêche l'usage normal (crash, données perdues)
3. **Sync fonctionnel** — l'app marche mais en partie (multi-device cassé, certaines actions perdues)
4. **Cohérence schéma + architecture** — pas de bug visible immédiat, mais dette technique qui freine les évolutions
5. **Features** — apporte de la valeur ajoutée
6. **Performance** — optimisations
7. **Polish (UX, doc, code mort)** — qualité finale

### Critères secondaires (pour départager intra-priorité)

- **Effort vs impact** : un item XS-impact-élevé passe avant un item M-impact-moyen
- **Bloqueur d'autres items** : ce qui débloque la suite passe en premier (ex. système de migrations Room avant tout fix touchant le schéma Room)
- **Réversibilité** : préférer les changements réversibles avant les irréversibles
- **Blast radius** : changement local-isolé > changement multi-stack

### Échelle d'effort

| Niveau | Durée | Caractéristiques |
|---|---|---|
| **XS** | <1h | Micro fix, 1-2 lignes (typo, suppression import, `@ColumnInfo`) |
| **S** | 1-4h | 1 fichier, fix localisé (ajout dependency, fix payload SQL) |
| **M** | 1-2 jours | Plusieurs fichiers, refactor localisé (uniformiser un CRUD + son router + son schéma) |
| **L** | ~1 semaine | Refactor multi-stack, migration DB nécessaire (suppression d'une entité avec cascade Android+serveur+DB) |
| **XL** | 2+ semaines | Nouvelle feature complète, breaking change (module Nutrition, refactor refresh token) |

---

## §3 — Quick Wins — démarrer fort

> **14 items XS/S à criticité élevée**, sans dépendance bloquante. Réalisables en **1-2 jours total**. Réduit considérablement le bruit dans l'audit + débloque psychologiquement.

### Sécurité immédiate (gain massif, effort minimal)

- [x] **XS** — Supprimer `MYSECRET123` hardcodé dans [main.py:121](../serveur/app/main.py#L121) (faux mécanisme `/secure-docs`) — **5 min**. Cf. [TODO_FIXES §1](TODO_FIXES.md#1--sécurité). ✅ commit `e9a5923`
- [x] **XS** — Retirer `OAuth2PasswordBearer` déclaré 2 fois (factoriser en important depuis `auth.py` dans `auth_router.py`) — **15 min**. Cf. [TODO_FIXES §5](TODO_FIXES.md#5--architecture--squelette-uniforme--refactor). ✅ commit `fa7d36c`
- [x] **XS** — Désactiver `--reload` dans `start_api.sh` Pi prod — **2 min**. Cf. [TODO_FIXES §2](TODO_FIXES.md#2--stabilité-production). ✅ commit `7f2f8e7`
- [x] **S** — Ajouter validation `audience=` + `issuer=` dans `jwt.decode` ([auth.py](../serveur/app/auth.py)) — **30 min**. Cf. [TODO_FIXES §1](TODO_FIXES.md#1--sécurité). ✅ commit `28df94c` (breaking : tous tokens existants invalidés, re-login requis)

### Bugs cassants à corriger immédiatement (haute valeur ajoutée)

- [x] **S** — Fix `superset_group_router.upsert_superset_group` signature sans param `uuid` ([:50-57](../serveur/app/routers/superset_group_router.py#L50)) — **30 min**. Bug 422 sur tout PUT. ✅ commit `d187ee5`
- [x] **S** — Fix `training_cycle_router.upsert_cycle` signature sans param `uuid` ([:29-35](../serveur/app/routers/training_cycle_router.py#L29)) — **30 min**. ✅ commit `b8f11d2`
- [x] **S** — Fix `superset_exercise_router.read_superset_exercise(exercise_id: int)` → `(uuid: str)` + appeler `get_superset_exercise_by_uuid` ([:21-30](../serveur/app/routers/superset_exercise_router.py#L21)) — **30 min**. ✅ commit `f36b957`
- [x] **S** — Renommer la collision `GET /planned-workouts/{uuid}` dans `planned_workout_exercise_router:21` → `/planned-workouts/{uuid}/exercises` — **20 min**. Cf. [TODO_FIXES §3](TODO_FIXES.md#3--sync-rest--websocket--dates). ✅ commit `2b39e3e`

### Code mort / Cleanup rapide

- [x] **XS** — Supprimer `serveur/old_exec_file.py` — **1 min**. ✅ commit `a0e6afc`
- [x] **XS** — Supprimer `serveur/app/db_triggers/sessions_trigger.sql` (vestige) — **1 min**. ✅ commit `eb7f985`
- [x] **XS** — Retirer le doublon `upsert_muscle_goal` dans [crud/__init__.py:95-96](../serveur/app/crud/__init__.py#L95) — **1 min**. ✅ commit `b94f9d9`
- [x] **XS** — Retirer l'import `sessionmaker` inutilisé dans [database.py:3](../serveur/app/database.py#L3) — **1 min**. ✅ commit `b5f7f9d`
- [x] **XS** — Supprimer `getTodayStartWithTimezone` ([CustomDateUtils.kt:33-40](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt#L33)) jamais appelée — **2 min**. ✅ commit `561ba03`
- [x] **XS** — Corriger `NetworkMonitor.onLost` snackbar "Token expiré !" → "Hors ligne" ([NetworkMonitor.kt:78](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt#L78)) — **5 min**. ✅ commit `24fd3d9`

**Total Quick Wins** : ✅ **TERMINÉ** (2026-05-04, 14/14 commits). Bonus : fix `const val AppConfig` (`bedd58e`) découvert au passage.

---

## §4 — Vague 1 — Sécurité critique exposée

> **Priorité absolue** si l'app est exposée publiquement (`<public-dns>`). 4 routers entièrement publics + secrets `change-me` = exploit direct depuis Internet.

### Groupe 1.1 — Sécuriser les 4 routers publics 🔴

**Effort total** : M (1-2 jours)
**Bloqueur de** : rien (peut être fait avant tout)
**Dépend de** : rien (Quick Wins préférable d'abord)

- [x] **M** — `user_router` ENTIÈREMENT PUBLIC ([user_router.py](../serveur/app/routers/user_router.py)) — ajouter `Depends(require_admin)` partout sauf POST signup (à débattre — voir Vague 8 endpoint /signup). Critique exposé sur Internet. ⚠ Décision à prendre : avant ou après avoir mis en place `User.is_admin` ? Si avant, la route admin est cassée le temps qu'on ajoute le flag → préférer faire `is_admin` d'abord (cf. Groupe 1.3 ci-dessous). ✅ commit `661b8f2` (2026-05-05) + `562a021` (POST /signup public minimal V8.3)
- [x] **S** — `equipment_router` SANS AUTH ([equipment_router.py](../serveur/app/routers/equipment_router.py)) — Type C global. Reads → `Depends(get_current_user_id)`, writes → `Depends(require_admin)`. ✅ commit `ec2f7a1` (2026-05-05)
- [x] **S** — `available_equipment_router` SANS AUTH — `DELETE /available-equipments` clear toute la table sans auth ! À décider en parallèle : Type A user-scoped ou Type C global ? (cf. [TODO_FIXES §4 AvailableEquipment](TODO_FIXES.md#4--schéma-db-postgres--pydantic--room)). ✅ commit `1d2368d` (2026-05-05, Type C provisoire — décision Type A user-scoped reportée à V5/V8)
- [x] **S** — `training_cycle_router` SANS AUTH — 8 endpoints à corriger. ✅ commit `b894d8e` (2026-05-05, Type C templates)

### Groupe 1.2 — Secrets en prod 🔴

**Effort** : S (3-4h)
**Bloqueur de** : rien
**Risque** : changer JWT_SECRET_KEY invalide tous les tokens → relogin obligatoire pour tous les utilisateurs

- [ ] **S** — Générer `JWT_SECRET_KEY` aléatoire fort (`python -c "import secrets; print(secrets.token_hex(32))"`) + `ALTER USER fittracker WITH PASSWORD '...'` Postgres Pi + créer `.env` Pi (gitignored).

### Groupe 1.3 — Préparer `User.is_admin` (prérequis pour 1.1) 🔴

**Effort** : M (1-2 jours)
**Bloqueur de** : Groupe 1.1 (`require_admin` doit exister avant qu'on puisse l'utiliser)
**Dépend de** : système de migrations DB (si on choisit Alembic — cf. Vague 3) OU peut être fait en SQL direct

- [x] **S** — Migration : `ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE` (manuelle ou via Alembic). ✅ commit `af27cbf` (2026-05-05, via Alembic + promote `will`)
- [x] **XS** — `UPDATE users SET is_admin = TRUE WHERE username = 'will'` (et autres comptes admin). ✅ commit `af27cbf` (2026-05-05, dans la même migration)
- [x] **S** — Ajouter `Depends(require_admin)` dans `app/dependencies.py` (vérifie le flag). ✅ commit `419e443` (2026-05-05)
- [x] **S** — Ajouter `is_admin` dans `UserOut` Pydantic + `/me` réponse + `UserInfo` Kotlin (résout au passage le bug actuel `role` mismatch — cf. [TODO_FIXES §3](TODO_FIXES.md#3--sync-rest--websocket--dates)). ✅ commits `419e443` (serveur) + `854b186` (Android Room v6→v7 + UserInfo DTO)

### Groupe 1.4 — Validation JWT (déjà en Quick Win)

Voir Quick Wins ci-dessus pour `JWT_ISS`/`JWT_AUD` validation.

**Total Vague 1** : ~3-5 jours.

---

## §5 — Vague 2 — Bypass ownership + cascade

> 13 items 🔴 + plusieurs 🟠 dans les CRUDs/schemas/routers. Politique de sécurité validée 2026-05-03 : chaque user voit que ses données. Aujourd'hui violée à plusieurs endroits.

### Groupe 2.1 — Bypass ownership directs (CRUDs sans check user_id) 🔴

**Effort total** : M-L (3-5 jours)
**Bloqueur de** : tests fonctionnels de la sécurité (Vague 8 plan tests)
**Dépend de** : rien (peut être fait en parallèle de Vague 1)

- [x] **S** — `delete_actual_workout` sans check ownership ([actual_workout_crud.py:88-97](../serveur/app/crud/actual_workout_crud.py#L88)). Aligner sur canonique `delete_X(db, uuid, user_id)`. ✅ commit `234769d` (2026-05-05)
- [x] **S** — `upsert_actual_workout` check conditionnel ([actual_workout_crud.py:38-59](../serveur/app/crud/actual_workout_crud.py#L38)). Forcer le check via `Depends(get_current_user_id)`. ✅ commit `095f572` (2026-05-05)
- [x] **M** — `upsert_many_actual_workouts` typage faux + pas de check ([:62-84](../serveur/app/crud/actual_workout_crud.py#L62)). Refondre selon canonique avec `list[ActualWorkoutCreate]`. ✅ commit `d22031a` (2026-05-05, bundle V2.1 + V2.3-a)
- [x] **S** — `exercise_muscle_crud.upsert_exercise_muscle` reçoit user_id mais l'ignore ([:45-69](../serveur/app/crud/exercise_muscle_crud.py#L45)). Appeler le helper `assert_user_owns_exercise` existant. ✅ commit `2c5858a` (2026-05-05, upsert + delete_*_by_uuid)
- [x] **S** — `upsert_planned_workout_exercise` cascade ownership manquante ([:73-95](../serveur/app/crud/planned_workout_exercise_crud.py#L73)). ✅ commit `2041380` (2026-05-05, planned_workout + exercise cascade)
- [x] **S** — `get_superset_exercise_by_uuid` ignore user_id (JOIN commenté) — décommenter ou retirer le param. ✅ commit `f2cf3fa` (2026-05-05)
- [x] **S** — `get_cycle_workout_by_uuid` sans check user_id. ✅ commit `cfe7805` (2026-05-05, bundle avec upsert + model_dump)
- [x] **S** — `upsert_cycle_workout` reçoit user_id mais l'utilise pas. ✅ commit `cfe7805` (2026-05-05)

### Groupe 2.2 — `user_id` lu du payload client (schémas Pydantic) 🔴

**Effort total** : M (1-2 jours)
**Bloqueur de** : Groupe 4.1 (avant de fix `SupersetGroup.userId` Room nullable)

- [x] **S** — `PlannedWorkoutBase.user_id` obligatoire — déplacer dans `Out` ([planned_workout_schema.py:8](../serveur/app/schemas/planned_workout_schema.py#L8)). ✅ commit `13f3038` (2026-05-05)
- [x] **S** — `SupersetGroupBase.user_id` obligatoire — déplacer dans `Out` ([superset_group_schema.py:10](../serveur/app/schemas/superset_group_schema.py#L10)). ✅ commit `c1981f9` (2026-05-05)
- [x] **S** — Audit transverse : confirmer absence du même bug sur les 20 autres schémas. ✅ (2026-05-05) audit terminé : 9 autres schémas avaient `user_id` mais dans `Out` uniquement (légitime). Seuls 2 cas non-conformes confirmés.

### Groupe 2.3 — Routers bulk acceptent `XOut` au lieu de `XCreate` 🔴

**Effort total** : M (1 jour)

- [x] **S** — `actual_workout_router.bulk` ([:40](../serveur/app/routers/actual_workout_router.py#L40)) — `list[ActualWorkoutOut]` → `list[ActualWorkoutCreate]`. ✅ commit `d22031a` (2026-05-05, bundle V2.1+V2.3-a)
- [x] **S** — `actual_workout_set_router.bulk` ([:40](../serveur/app/routers/actual_workout_set_router.py#L40)) — idem. ✅ commit `4004539` (2026-05-05)
- [x] **S** — `available_equipment_router.bulk` ([:24](../serveur/app/routers/available_equipment_router.py#L24)) — idem. ✅ commit `79191cc` (2026-05-05)
- [x] **S** — `equipment_crud.bulk_upsert_equipments` typage faux ([:43](../serveur/app/crud/equipment_crud.py#L43)) — `list[EquipmentOut]` → `list[EquipmentCreate]`. ✅ commit `9531f3e` (2026-05-05)
- [x] **S** — `actual_workout_set_crud.upsert_many_actual_workout_sets` typage faux (`list[models.ActualWorkoutSet]` ❌). ✅ commit `4004539` (2026-05-05, refactor delegation au singleton)

### Groupe 2.4 — Audit cascade ownership pratique 🟠

**Effort** : M-L (1 semaine, dépend du nombre de tests)
**Pour valider en pratique** que les fixes ci-dessus marchent réellement.

- [ ] **L** — Audit cascade sur les 5 entités feuilles (`actual_workout_set`, `actual_workout_exercise`, `planned_workout_exercise`, `routine_task_check`, `superset_exercise`). Tests pratiques : un user A peut-il muter une feuille appartenant à user B ? Stratégie : `user_id` dénormalisé sur la feuille OU JOIN cascade.

**Total Vague 2** : ~5-7 jours.

---

## §6 — Vague 3 — Bloqueurs structurels

> 4 chantiers structurels qui **bloquent** beaucoup d'autres items. À faire avant Vagues 4-6 sinon refactors freinés.

### Groupe 3.1 — Système de migrations Room (bloqueur multi-features) 🔴

**Effort** : L (~1 semaine)
**Bloqueur de** : Vague 5 (suppression MuscleWeeklySummary), tout fix touchant le schéma Room (4 colonnes camelCase, `target_reps`, etc.)

- [x] **M** — Activer `exportSchema = true` dans `@Database` ; commit les fichiers JSON `app/schemas/<version>.json`. ✅ commit `2439fd1` (2026-05-05, schémas v6 et v7 commités)
- [x] **M** — Créer `data/local/migrations/Migrations.kt` regroupant les migrations. ✅ commit `2439fd1`
- [x] **S** — Première migration `Migration(6, 7) { db -> /* placeholder, à remplir avec le 1er changement réel */ }`. ✅ commit `854b186` (2026-05-05, MIGRATION_6_7 ajoute `is_admin` Room — premier changement réel, pas un placeholder)
- [ ] **M** — Doc dans `DEV_GUIDE.md` : "Comment ajouter une migration Room". (encore à faire)
- [ ] **S** — Tests : faire un mini changement de schéma + migration + vérifier `assertDatabaseHaveBeenMigratedTo` (Room test framework). (encore à faire)

### Groupe 3.2 — Format wire des dates unifié (résout les bugs DATES) 🔴

**Effort** : M (2-3 jours)
**Bloqueur de** : `RemoteDataMerger.isRemoteNewer` fix (Vague 4), `Instant.parse` crash fix (Vague 4)
**Dépend de** : rien (multi-stack mais isolé)

Voir [docs/DATES.md](DATES.md) pour le contexte. **4 fixes ciblés à faire ensemble** :

- [x] **M** — Côté Postgres : créer une fonction utilitaire `iso_utc(timestamptz)` qui produit `"YYYY-MM-DDTHH:MM:SS.US Z"` strict UTC. Remplacer `'updatedAt', rec.updated_at` par `'updatedAt', iso_utc(rec.updated_at)` dans les 17 triggers `db_triggers/*.sql`. ✅ commits `5b6cdd8` (helper) + `6d79827` (sweep 16 fragments — `training_cycles_trigger.sql` LEGACY laissé pour V4.3) (2026-05-05)
- [x] **S** — Côté Pydantic : ajouter un `field_serializer` global qui force `Z` au lieu de `+00:00` sur les `datetime` Out. ✅ commit `bb6bae9` (2026-05-05) — implémenté via type explicite `UTCDateTime = Annotated[datetime, PlainSerializer]` (option A choisie pour la lisibilité) + sweep 21/22 schémas (user_schema sans timestamp).
- [x] **XS** — Côté Android : `getNowISO8601()` → `Instant.now().truncatedTo(ChronoUnit.MICROS).toString()` (truncation microsec). ✅ commit `0cab7a9` (2026-05-05) — formatter canonique `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'` ajouté pour forcer 6 décimales fixes.
- [x] **S** — `isRemoteNewer` : parser en `Instant` avant comparaison + try/catch fallback sur format Postgres legacy. ✅ commit `6848cdd` (2026-05-05).
- [x] **S** — Créer `parseInstantSafe(iso): Instant?` utilitaire avec 3 fallbacks (Instant, OffsetDateTime "yyyy-MM-dd HH:mm:ssXXX/X", LocalDate take(10)) et remplacer **tous** les `Instant.parse` par cet helper (8 callsites — cf. [TODO_FIXES §3](TODO_FIXES.md#3--sync-rest--websocket--dates)). ✅ commit `6b67114` (2026-05-05).

### Groupe 3.3 — Source de vérité unique triggers/migrations 🔴

**Effort** : M (1-2 jours)
**Bloqueur de** : Vague 4 Groupe 4.3 (réparer les 5 entités sans push WS)

- [x] **M** — Décider : tout via Alembic OU tout via `db_triggers/` + `exec_pg.py`. Recommandation Alembic (avec autogenerate branché — voir 3.4). ✅ commit `4a4dbe3` (2026-05-05) — décision : Alembic = source de vérité, le SQL des triggers reste dans `db_triggers/` mais est chargé via un helper Python partagé `app/triggers_loader.py`.
- [x] **S** — Si Alembic : retirer la définition de `notify_row_change()` de la migration `20250811_notify_triggers.py`. Garder seulement l'attache des triggers ; charger les fragments `db_triggers/*.sql` via `op.execute(open(path).read())`. ✅ commit `4a4dbe3` (2026-05-05, refactoré via helper `app.triggers_loader.compose_function_sql/attach_triggers_sql/user_id_helper_sql` partagé entre Alembic et `exec_pg.py`)
- [ ] ~~**S** — Si `db_triggers/` : supprimer entièrement la migration Alembic existante.~~ (option non retenue — Alembic conservé comme source de vérité)

### Groupe 3.4 — Refactor `exec_pg.py` + Alembic autogenerate 🟠

**Effort** : M (1-2 jours)
**Dépend de** : 3.3 (décision source de vérité)

- [ ] **M** — Créer `setup_db.py` idempotent (crée tables + triggers, ne drop pas) + `run_server.py` (juste uvicorn) + `reset_db.py` (version destructive séparée). (encore à faire)
- [x] **S** — Brancher `target_metadata = Base.metadata` dans `alembic/env.py:28`. Permet `alembic revision --autogenerate`. ✅ commit `b0546b8` (2026-05-05)

**Total Vague 3** : ~2-3 semaines.

---

## §7 — Vague 4 — Sync fonctionnel (REST + WebSocket)

> Endpoints cassés appelés en pratique + push WebSocket défaillant. Réparer pour avoir un fonctionnement multi-device propre.

### Groupe 4.1 — Endpoints cassés appelés en pratique 🔴

**Effort total** : M (1-2 jours)
**Dépend de** : Vague 2.2 (`SupersetGroupBase.user_id`) pour le SupersetGroup nullable côté Room

- [x] **S** — `cycleWorkoutApi.upsert(cw)` (PUT sans uuid) → aligner sur `upsert(uuid, item)` standard ([CycleWorkoutSyncable.kt:31](../appli-android/app/src/main/java/com/example/sportapp/sync/syncables/CycleWorkoutSyncable.kt#L31), [RemoteDataUpserter.kt:70](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataUpserter.kt#L70)). ✅ commit `dcf4d71` (2026-05-05).
- [x] **S** — `cycleWorkoutApi.delete(item)` (DELETE avec body) → `delete(uuid)` ([CycleWorkoutSyncable.kt:39](../appli-android/app/src/main/java/com/example/sportapp/sync/syncables/CycleWorkoutSyncable.kt#L39)). ✅ commit `dcf4d71` (2026-05-05, bundle avec upsert).
- [x] **S** — `exerciseMuscleApi.upsert(exerciseUUID, muscleUUID, em)` → `upsert(uuid, item)` ([ExerciseMuscleSyncable.kt:31](../appli-android/app/src/main/java/com/example/sportapp/sync/syncables/ExerciseMuscleSyncable.kt#L31)). ✅ commit `6ab569b` (2026-05-05) — `upsert(uuid, item)` + `delete(uuid)` standardisés.
- [x] **M** — Architecture incohérente CycleWorkout vs `training_cycles/{}/workouts/{}` — décider canonique. Recommandation : garder `CycleWorkoutApi` (cohérent avec modèle Room), supprimer côté serveur les endpoints `/training-cycles/{cycle_uuid}/workouts/...` (link/unlink/list). ✅ commit `adea775` (2026-05-05) — recommandation suivie : 3 endpoints router supprimés + 3 fonctions CRUD + 3 exports `__init__.py` + nettoyage imports orphelins.

### Groupe 4.2 — Triggers WS sans `userId` (broadcast à tous) 🔴

**Effort** : S (3-4h)
**Dépend de** : 3.3 (décision source de vérité)

- [x] **S** — `exercise_equipment_trigger.sql` : ajouter `userId` top-level via `get_user_id_for('exercise_equipment',...)`. ✅ commit `78693d0` (2026-05-05).
- [x] **S** — `exercise_muscle_trigger.sql` : idem. ✅ commit `78693d0` (2026-05-05, bundle).
- [x] **XS** — `planned_workout_exercises_trigger.sql:4-6` : ajouter `userId` dans le bloc `delete` (le bloc `update` l'a). ✅ commit `78693d0` (2026-05-05, bundle).

### Groupe 4.3 — 5 entités sans push WebSocket 🔴

**Effort** : M (1-2 jours)
**Dépend de** : 3.3 (décision source de vérité)

- [x] **S** — Créer `notifications_trigger.sql` (format moderne fragment IF TG_TABLE_NAME = 'notifications'). ✅ commit `85a6be3` (2026-05-05).
- [x] **S** — Créer `routine_periods_trigger.sql`. ✅ commit `85a6be3` (2026-05-05, bundle 4 fragments).
- [x] **S** — Créer `routine_tasks_trigger.sql`. ✅ commit `85a6be3` (2026-05-05).
- [x] **S** — Créer `routine_task_checks_trigger.sql`. ✅ commit `85a6be3` (2026-05-05).
- [x] **S** — Migrer `training_cycles_trigger.sql` LEGACY → format moderne fragment. ✅ commit `7fa8c9e` (2026-05-05).
- [x] **S** — Étendre `user_id_helper.sql` pour les 4 entités user-scoped + retirer le cas `sessions` vestige. ✅ commit `ca95a27` (2026-05-05).
- [x] **XS** — Ajouter les 5 fichiers à `exec_pg.py:load_sql_parts()`. ✅ commit `3dcd971` (2026-05-05) — ajoutés à `PER_TABLE_FRAGMENTS` dans `triggers_loader.py` (refondé en V3.3 comme source de vérité unique).

### Groupe 4.4 — Bugs sync architecturaux 🔴/🟠

**Effort total** : M (2-3 jours)

- [x] **S** — `RemoteDataMerger.isRemoteNewer` faux (cf. Vague 3.2 — résolu par les 4 fixes dates). ✅ V3.2-6 commit `6848cdd` (2026-05-05).
- [x] **S** — Phase 1 deletes `SyncManager.syncAllToServer` mauvais ordre FK ([SyncManager.kt:189-201](../appli-android/app/src/main/java/com/example/sportapp/sync/SyncManager.kt#L189)) → `syncables.reversed().forEach { syncEntityDeletions(it) }`. ✅ commit `aa92f19` (2026-05-05).
- [x] **S** — `*SyncHandler` : ajouter check `if (dao.getXByUUID(parentUUID) != null) insert; else ignore` dans handlers de junctions (`ExerciseMuscleSyncHandler`, `ExerciseEquipmentSyncHandler`). ✅ commit `d2c20f2` (2026-05-05) — défense in depth (le bug racine est résolu côté serveur via V4.2 userId top-level).
- [x] **S** — `mergeAllFromServer` ordre inversé → push d'abord, merge ensuite ([NetworkMonitor.kt:51-55](../appli-android/app/src/main/java/com/example/sportapp/utils/NetworkMonitor.kt#L51)). ✅ commit `0556bd1` (2026-05-05).
- [x] **S** — `mergeAllFromServer` non déclenché après login → ajouter appel explicite à la fin de `AuthManager.initAuth()`. ✅ commit `4b83726` (2026-05-05) — RemoteDataMerger injecté via Hilt + try/catch défensif.
- [ ] **M** — `RemoteDataMerger` ne supprime pas les locaux absents — ajouter `merge.deleteLocalsAbsentFromRemote()` avec garde "ne delete que les `synced=true` absents". Décision liée à Vague 5 (deleted_at simplification). **Différé V5.5** (décision liée à `deleted_at` simplification).
- [x] **S** — `SyncManager.isSyncing: Boolean` → `Mutex.withLock`. ✅ commit `5f45926` (2026-05-05).
- [x] **S** — `WebSocketManager.handleMessage` ajouter `else -> Log.w(...)` dans le `when`. ✅ commit `5ed4643` (2026-05-05, bundle avec close avant reconnect).
- [x] **XS** — `WebSocketManager.start` ajouter `webSocket?.close(1000, "Reconnect")` avant la nouvelle connexion. ✅ commit `5ed4643` (2026-05-05).
- [x] **S** — Remplacer tous les `payload.optString(key, null)` par `JsonUtils.getNullableString(...)` dans les `*SyncHandler`. ✅ commit `3790c85` (2026-05-05) — sweep 10 occurrences sur 5 fichiers.
- [x] **S** — `ExerciseSyncHandler.handle` ajouter extraction `instructions` ([ExerciseSyncHandler.kt:22-38](../appli-android/app/src/main/java/com/example/sportapp/data/remote/ExerciseSyncHandler.kt#L22)). ✅ commit `3790c85` (2026-05-05, bundle).
- [x] **M** — `RemoteDataUpserter.upsertAllUnsynced` uniformiser sur `getAllUnsynced` (les 6 entités qui font `getAllOnce` à corriger). ✅ commit `b1e452c` (2026-05-05) — 6 entités passées à `getAllUnsynced` + ajout `markAsSynced` après upsert OK.

### Groupe 4.5 — Gestion 401 proactive Android 🔴

**Effort** : M (1 jour)
**Suggestion Claude** intégrée ici (Authenticator OkHttp)

- [x] **M** — Ajouter `Authenticator` OkHttp dans `RetrofitInstance` qui détecte 401 → `TokenManager.clearToken()` + `CurrentUserManager.clearUserId()` + `SyncEvents.onTokenExpired.emit(Unit)`. La nav navigue vers login automatiquement. ✅ commits `0e8d592` (Authenticator + onTokenExpired) + `44cefe3` (MainActivity hook nav login) (2026-05-05).
- [x] **S** — Snackbar UX : remplacer "Sync error" générique par "Session expirée, reconnectez-vous" sur 401. ✅ commits `44cefe3` (snackbar "Session expirée" affiché par MainActivity sur onTokenExpired) + `d349f0d` (skip "Failed to sync X" redondant dans `safeSync*WithSnackbar` quand 401) (2026-05-05).
- [x] **S** — Préparer le terrain pour le refresh token (Vague 8) — l'`Authenticator` pourra plus tard tenter un refresh avant de forcer le logout. ✅ commit `0e8d592` (2026-05-05) — commentaire de tête dans `authAuthenticator` indique exactement où insérer l'appel `/refresh` avant le forced logout, signature OkHttp prête à reconstruire la requête + retourner pour retry.

**Total Vague 4** : ~1-2 semaines.

---

## §8 — Vague 5 — Cohérence schéma DB

> Aligner Postgres / Pydantic / Room. Long mais nécessaire pour une base saine.

### Groupe 5.1 — Suppression `MuscleWeeklySummary` (entité fantôme) 🟠

**Effort** : L (~1 semaine)
**Dépend de** : Vague 3.1 (système de migrations Room — bloqueur **strict**)
**Bloqueur de** : rien

- [x] **L** — ~~Cascade complète~~ ✅ Sous-vague G (2026-05-05) — 4 commits : Alembic `20260505_v5_1_drop_mws` (`a08a183`'s sibling actually `dc84efe`/`a461401`/`7465608` etc.). Serveur : 5 fichiers supprimés (`models/`, `schemas/`, `crud/`, `routers/`, `db_triggers/`) + 11 fichiers modifiés (`__init__.py` × 4, `main.py`, `triggers_loader.py`, `user_id_helper.sql`, `seed_database.py`, `view_database.py`, `clear_database.py`, `diagram.dbml`, `diagram.dbdiagram`). Android : 5 fichiers supprimés (`MuscleWeeklySummary.kt`, `MuscleWeeklySummaryDao.kt`, `MuscleWeeklySummarySyncHandler.kt`, `MuscleWeeklySummaryApi.kt`, `MuscleWeeklySummarySyncable.kt`) + 9 fichiers modifiés (`AppDatabase.kt`, `AppModule.kt`, `RetrofitInstance.kt`, `WebSocketManager.kt`, `SyncSettingsViewModel.kt`, `SyncManager.kt`, `RemoteDataMerger/Getter/Upserter.kt`). Migration Room `MIGRATION_9_10` (DROP TABLE), version 9→10. Smoke E2E : training-cycles GET/PUT/DELETE OK + GET /muscle-weekly-summaries → 404.

### Groupe 5.2 — Mismatches Postgres/Pydantic/Room 🔴/🟠

**Effort total** : M-L (3-4 jours)
**Dépend de** : Vague 3.1 (migrations Room)

- [x] **S** — ~~`SupersetGroup.userId` Room nullable → non-nullable (avec migration).~~ ✅ commits `949ad05` + `444bc2e` + `736fe13` (2026-05-05, V5.2-C). Backfill `user_id=1` via politique CLAUDE.md §12. SyncHandler durci option (b).
- [x] **S** — ~~`SupersetExercise.orderInGroup` Room nullable → non-nullable avec default `0`.~~ ✅ idem V5.2-C. Backfill `0`. SyncHandler durci.
- [ ] **M** — `actual_workout_sets.target_reps` champ Room fantôme — décider : (a) ajouter au modèle SQLAlchemy + Pydantic + trigger payload (option si feature utilisée), (b) retirer du modèle Room et du VM. Recommandation : (a) si la feature `targetReps` est volontaire, sinon (b). *Décision reportée en sous-vague E.*

### Groupe 5.3 — Naming colonnes Room camelCase 🟠

**Effort total** : M (1-2 jours, principalement la migration)
**Dépend de** : Vague 3.1 (migrations Room)

- [x] **S** — ~~`ActualWorkout.userId` → `@ColumnInfo(name = "user_id")` + migration `ALTER TABLE actual_workouts RENAME COLUMN userId TO user_id`.~~ ✅ commit `949ad05` (2026-05-05, V5.3-C). MIGRATION_7_8 RENAME COLUMN.
- [ ] **S** — `Muscle.isFavorite` → `is_favorite` + migration. *Découvert lors de C : déjà OK (`@ColumnInfo("is_favorite")` déjà présent). Item fermé sans modif.*
- [ ] **S** — `MuscleGoal` 5 colonnes (`priority`, `done`, `target`, `weekISO`, `status`) → snake_case + migration. *Découvert lors de C : 4/5 sont des noms 1-mot sans souci de casse. Seul `weekISO` est vraiment camelCase mais c'est un item 🟡 cosmétique séparé multi-stack (Postgres+Pydantic+Room). Item fermé en V5.3, `weekISO` traité ailleurs.*
- [x] **S** — ~~`ActualWorkoutExercise.addedManually` → `added_manually` + migration.~~ ✅ commit `949ad05` (2026-05-05, V5.3-C). MIGRATION_7_8 RENAME COLUMN.

### Groupe 5.4 — `nullable` manquants côté SQLAlchemy 🟠

**Effort total** : S (3-4h)

- [ ] **S** — `muscle_weekly_summary.user_id`, `week_start_date`, `total_sets` ajout `nullable=False` (entité à supprimer de toute façon mais à ne pas reproduire). *Skip V5.4 — entité supprimée en sous-vague G (V5.1).*
- [x] **XS** — ~~`muscle_goals.uuid` ajout `nullable=False`.~~ ✅ commits `1dab40b` + `f89df59` (2026-05-05, V5.4-B)
- [x] **XS** — ~~`actual_workout_sets.is_dropset` ajout `nullable=False`.~~ ✅ commits `1dab40b` + `f89df59` (2026-05-05, V5.4-B) — `nullable=False, default=False, server_default="false"`.
- [x] **XS** — ~~`exercise_muscles.coefficient` ajout `nullable=False`.~~ ✅ commits `1dab40b` + `f89df59` (2026-05-05, V5.4-B) — `nullable=False, default=1.0, server_default="1"` (sémantique muscle primaire, cf. CLAUDE.md politique 10).
- [x] **XS** — ~~`actual_workout_sets.status: Optional[str]` Pydantic → `str` non-nullable.~~ ✅ commit `8e55897` (2026-05-05, V5.4-B) — `Optional[str] = None` → `str = "in_progress"`. Correction sémantique `NOT_STARTED` + UPPER_CASE différée à vague cosmétique.
- [x] **XS** — ~~`actual_workout_exercises.phase` ajouter `default="TRAINING"` (cohérent PlannedWorkoutExercise).~~ ✅ commits `1dab40b` + `f89df59` (2026-05-05, V5.4-B) — `default="TRAINING", server_default="TRAINING"` (sémantique exercice classique, cf. CLAUDE.md politique 10).
- [ ] **XS** — `actual_workout_sets.set_order` ajouter default Postgres. *Décision validée 2026-05-05 : pas de default — la position est positionnelle, doit être explicite (cf. CLAUDE.md politique 10). Item à fermer en doc.*

### Groupe 5.5 — Soft-delete simplification 🟠

**Effort** : M (2-3 jours)
**Dépend de** : Vague 4.4 (`RemoteDataMerger.deleteLocalsAbsentFromRemote`)

**Recommandation Option A** (cf. [TODO_FIXES §4](TODO_FIXES.md#4--schéma-db-postgres--pydantic--room) deleted_at) :

- [x] **M** — ~~Retirer `deleted_at` partout (Postgres + Pydantic + Room) — simplification radicale.~~ ✅ Sous-vague F (2026-05-05) — 8 commits : migration Alembic `20260505_v5_5_drop_deleted_at` (`3919b07`) + 21 modèles SQLAlchemy + 5 CRUDs (`0801f8f`) + 21 schémas Pydantic + cleanup utc_datetime/seed (`54b87e4`) + 21 triggers SQL + diagram.dbml + strip BOM (`1f9bebd`) + 21 entités Room + targetReps + Room v8→v9 (`e5e3624`) + 17 SyncHandlers + RemoteDataMerger 21 branches + NotificationRepository (`693604b`) + VMs cleanup (`9efe3b3`). Bonus : `target_reps` Room fantôme retiré (V5.2 sous-vague E option b).
- [x] **S** — ~~Adapter `RemoteDataMerger` retirer le check `if remote.deletedAt != null` + ajouter la garde `deleteLocalsAbsentFromRemote(synced=true)`.~~ ✅ Volet 1 (commit `693604b`) puis volet 2 V4.4-différé (commit `c097977`, 2026-05-05) : helper privé `pruneStaleLocals` ajouté + 1 appel par entité (20 entités). Filtre `synced=true` protège les créations locales en cours non encore poussées. Refactor forcé en helper après échec compile "Method too large" pour `mergeAllFromServer` (limite JVM 65535 bytes dépassée par les 4 lignes inline × 20 entités).

### Groupe 5.6 — Diagram DBML alignement 🟠

**Effort** : S (2h)
**Dépend de** : Vague 5.1-5.5 (les changements de schéma)

- [ ] **S** — Aligner DBML sur l'état réel : retirer indexes aspirationnels OU les implémenter (cf. Vague 8 indexes Postgres).
- [ ] **XS** — Corriger types `time`/`date` → `String` dans le DBML.
- [ ] **XS** — Ajouter `[delete: cascade]` à la FK `muscle_weekly_summary.user_id` (avant suppression).
- [ ] **S** — Documenter convention "DBML = intention, modèles = état réel" dans `CLAUDE.md`.

### Groupe 5.7 — Helpers triggers SQL 🟠

**Effort** : S (2h)

- [x] **S** — ~~Étendre `user_id_helper.sql` pour 4 entités manquantes~~ ✅ inclus dans Vague 4.3 (commit `ca95a27`).
- [x] **S** — ~~Décider sort de `training_cycles` : Type A (ajouter `user_id` direct) ou Type C global.~~ ✅ **Type A choisi** (2026-05-05, V5.7) — commits `fd3e2dc` (politiques) + `16ede81` (Alembic migration) + `59d20df` (modèle+Pydantic+seed) + `a01562f` (CRUD user-scoped) + `a68558f` (router retire require_admin) + `9e3bb07` (user_id_helper direct lookup). Smoke test E2E confirmé : `will` voit ses 5 cycles, `bob` voit 0 cycle, `bob` peut créer (user_id auto-injecté), tentative cross-user → 403.

**Total Vague 5** : ~2 semaines.

---

## §9 — Vague 6 — Squelette uniforme + refactor archi

> Application de la politique squelette uniforme (mémoire 2026-05-03) aux 27 CRUDs / 22 routers / 22 schemas / 22 syncables / 22 DAOs / 22 APIs. Long mais cohérent.

### Groupe 6.1 — Bugs Room/DAO architecturaux 🔴

**Effort total** : M (2-3 jours)

- [x] **M** — ~~`RoutineTaskCheckDao.setChecked` viole 2 couches — déplacer la logique dans `RoutineTasksScreenViewModel:130` (le seul appelant). Retirer les imports `CurrentUserManager`, `showSnackbar`, `SnackbarType` du DAO.~~ ✅ commit `3660409` (2026-05-05, V6.1-D1). DAO ne contient plus que des opérations CRUD pures, logique inlinée dans `toggleTask` qui utilise les wrappers Style A déjà en place.
- [x] **M** — ~~`ActualWorkoutSetDao` Style C → Style A (wrapper public + `*Internal`).~~ ✅ commit `8cd25d7` (2026-05-05, V6.1-D2). Wrappers `insert/insertAll/update` posent automatiquement `synced=false + updatedAt=now`, délèguent à `*Internal` annotés Room. `*FromServer` appellent `*Internal` (préserve payload). `updateActualWorkoutSet` renommé `update` (0 callsite à migrer). Build compileDebugKotlin OK.
- [x] **S** — ~~`CycleWorkoutDao` bug Room `@Query` + body Kotlin → retirer le `@Query`, garder le body~~ ✅ commit `8750536` (2026-05-05, V6.1-A1)
- [x] **XS** — ~~Doublons stricts à dédupliquer : `MuscleGoalDao.deleteAll` + `clearAll` ; `ActualWorkoutSetDao.markSetsAsPendingDeletionWithUUID` + `markAsPendingDeletion` ; `MuscleDao.toggleFavorite` + `updateFavorite`.~~ ✅ commits `61d507f` + `b0d530d` + `79d211b` (2026-05-05, V6.1-A2/A3/A4)
- [x] **XS** — ~~`MuscleGoalDao.getAllUnSynced` (S majuscule) → renommer.~~ ✅ commit `0029411` (2026-05-05, V6.1-A5)

### Groupe 6.2 — Squelette CRUD/router/schema canonique 🟠

**Effort total** : L (~1 semaine, en plusieurs PRs)

- [x] **M** — ~~PR1 : ordre des paramètres CRUD `(db, uuid, dto, user_id)` partout.~~ ✅ V6.2 sous-batch I (2026-05-05) — 6 CRUDs migrés (`muscle`, `routine_period`, `routine_task`, `routine_task_check`, `superset_group`, `equipment`). Les 16 autres étaient déjà conformes.
- [x] **M** — ~~PR2 : gestion ownership uniforme (403 partout via `HTTPException(403)`).~~ ✅ V6.2 sous-batch I — `raise HTTPException(status_code=403)` ajouté sur les 4 CRUDs Routine* + Muscle + SupersetGroup. Les autres avaient déjà 403 via cascade.
- [x] **M** — ~~PR3 : style update (écrasement total via `model_dump`) sauf justification.~~ ✅ V6.2 sous-batch I — patch partiel `if dto.X is not None: setattr(...)` migré en `for key, value in dto.model_dump().items(): setattr(...)` (écrasement total) sur les 4 CRUDs Routine* + Muscle. Validé : Android Retrofit envoie toujours l'objet complet via Gson.
- [x] **M** — ~~PR4 : type d'entrée bulk (`list[XCreate]` partout, jamais `XOut`).~~ ✅ Déjà fait en V2.3 (commits V2.3-a/b/c, 2026-05-05). Vérifié en V6.2 audit : aucun bulk non typé restant.
- [x] **S** — ~~PR5 : `delete_X` retournent `bool` partout (pas dict, pas objet ORM).~~ ✅ V6.2 sous-batch I-7 — 5 CRUDs migrés (`training_cycle`, `actual_workout_exercise`, `actual_workout_set`, `cycle_workout` ex `remove_workout_from_cycle`, `exercise_muscle`) + `equipment.delete` ne retourne plus dict + `superset_group.delete` ne retourne plus l'objet. Routers correspondants : `response_model=XOut` retiré sur DELETE, retour `{"ok": True}` + 404 si False.
- [x] **S** — ~~PR6 : `tags=` + `prefix=` sur tous les routers.~~ ✅ V6.2 sous-batch II — `tags=["xxxs"]` ajouté sur 20 routers (script PowerShell). `prefix=` skipé (trop intrusif, demanderait modif de ~80 routes). Tags suffisent pour le groupement Swagger souhaité.
- [x] **S** — ~~PR7 : DAO Style A unifié (migrer les 2 Style B `ActualWorkoutDao`, `ActualWorkoutExerciseDao`).~~ ✅ V6.2 sous-batch IV — `rawInsert/rawInsertAll/rawUpdate/rawMarkAsPendingDeletion` renommés `insertInternal/insertAllInternal/updateInternal/markAsPendingDeletionInternal` (canonique Style A). 0 callsite externe.
- [x] **S** — ~~PR8 : `model_config = {"populate_by_name": True}` sur tous les `XBase` (18 schémas).~~ ✅ V6.2 sous-batch III — 18 `XBase` enrichis (script PowerShell). Permet à Pydantic d'accepter snake_case et camelCase au constructeur, robustesse cross-stack.

### Groupe 6.3 — Bugs CRUD/router résiduels 🟠

**Effort total** : M (1-2 jours)

- [x] **S** — ~~`actual_workout_exercise_crud` 3 fonctions redondantes — garder seulement `upsert_*`, supprimer `create_*` + `update_*`.~~ ✅ V6.3-A1 (commit `e833bcb`, 2026-05-05) : create + update supprimés, 0 callsite externe. .dict() → .model_dump() dans le même fichier.
- [x] **S** — ~~`actual_workout_set_crud.add_set_to_actual_workout(db, set_data: dict)` sans user_id check.~~ ✅ V6.3-A2 (commit `e833bcb`) : fonction supprimée + `bulk_create_actual_workout_sets` orpheline (0 callsite, doublon avec `upsert_many_actual_workout_sets`).
- [x] **S** — ~~Routers utilisent `dict()` (pydantic v1) → `model_dump()`.~~ ✅ Déjà migré en V2/V6.2 (vérifié par grep en V6.3-A5 : 0 occurrence restante).
- [x] **S** — ~~`exercise_equipment_crud.upsert_exercise_equipment` modifie l'UUID d'un objet existant.~~ ✅ V6.3-A3 (commit `e833bcb`) : ligne `existing_combo.uuid = uuid` retirée + `uuid` ajouté à l'exclude list du setattr.
- [x] **S** — ~~`muscle_router.upsert_muscle_route` parse manuel.~~ ✅ V6.2-I-1 (commit `d2c2941`) : Pydantic body normal + assert `dto.uuid == uuid`.
- [x] **S** — ~~5 routers mutent le DTO (`dto.uuid = uuid`).~~ ✅ V6.2-I (4 routers Routine* + Muscle + Equipment) + V6.3-A6 (commit `c7a1b58`, actual_workout_set le dernier). Tous remplacés par `if dto.uuid != uuid: raise 400`.
- [x] **S** — ~~`exercise_muscle_router` 2 DELETE chevauchent.~~ ✅ V6.3-A4 (commit `e833bcb`) : route DELETE `/exercise-muscles/{exercise_uuid}/{muscle_uuid}` supprimée + `crud.delete_exercise_muscle` orphelin retiré. Android utilise uniquement la version par uuid.
- [x] **S** — ~~`delete_X` côté router : ajouter 404 si False/None retourné.~~ ✅ Tous les routers `delete_X` retournant `bool` ajoutent maintenant 404 si False (V6.2-I-7 + V6.3-A4).
- [x] **S** — ~~Aligner `Optional[str] uuid` → `str` dans 4 schémas.~~ ✅ V6.3-A8 (commit `c7a1b58`) : 3 schémas (`actual_workout`, `actual_workout_exercise`, `actual_workout_set`). Le 4ème listé n'existait pas en réalité (audit erroné).

### Groupe 6.4 — Architecture VMs / repositories Android 🟠

**Effort total** : M (2-3 jours)

- [x] **S** — ~~`MuscleGoalsManager` + `GoalsTabViewModel` importent `ui.screens.parseTargetMinimum` → déplacer la fonction dans `utils/`.~~ ✅ V6.4-D1 (commit `68b2b9f`, 2026-05-05) : `parseTargetMinimum` + `calculateGoalProgress` migrés dans `utils/GoalUtils.kt`. Inversion de dépendance corrigée (data layer importait `ui.screens`).
- [ ] **S** — Logique dupliquée `parseTargetReps` (3 VMs) → extraire dans `utils/RepUtils.kt`. *Différé : V5.2-target_reps a déjà supprimé le champ Room, à reconsidérer après cosmétique V6.4*.
- [x] **M** — ~~`MuscleGoalDao.updateStatusAccordingToDone` SQL embarque la logique métier `parseTargetMinimum` → unifier en Kotlin (testable).~~ ✅ V6.4-D2 (commit `c67fdc4`, 2026-05-05) : 0 callsite, supprimé pur et simple. Si besoin futur de re-checker le statut, faire en Kotlin via `parseTargetMinimum` de `utils/`.
- [x] **S** — ~~`ExerciseScreenViewModel:49` typage incorrect → corriger ou retirer si dead code.~~ ✅ V6.4-D3 (commit `509a152`, 2026-05-05) : param `actualWorkoutDao: ActualWorkoutExerciseDao` injecté mais 0 ref → supprimé.
- [x] **S** — ~~`AuthManager.skipInterceptorAuth` flag jamais lu → supprimer (ou brancher si utile).~~ ✅ V6.4-D3 (commit `509a152`, 2026-05-05) : drapeau `var skipInterceptorAuth` retiré de `RetrofitInstance` + 2 écritures mortes dans `AuthManager`. L'interceptor JWT consulte `TokenManager` directement.
- [x] **XS** — ~~`AuthManager.setUserId(-1)` sentinel → `clearUserId()` (= null).~~ ✅ V6.4-D3 (commit `509a152`, 2026-05-05) : sémantique propre, plus de user "id 0/-1" bidon.
- [ ] **S** — `ChronoViewModel.onTimerFinished` hardcode `"Rest", 90` → passer le vrai nom + durée depuis le state. Lié à Vague 8 refactor Chrono.
- [x] **S** — ~~`NotificationCenter.post` double-vibration → décider via channel uniquement OU via `VibrationUtils` uniquement.~~ ✅ V6.4-D4 (commit `502b408`, 2026-05-05) : décision = via channel système uniquement (`phoneNotif.show` avec `vibrationEnabled=settings.vibrateOnInAppNotification`). Bloc direct `VibrationUtils.vibrateForNotification` retiré + `appContext` orphelin nettoyé.
- [ ] **S** — Auto-completion combine `GoalsTabViewModel` → refactor : auto-completion explicite à la fin d'un set.
- [x] **S** — ~~`ExerciseMuscleSyncable` divergence (par paire vs uuid) → uniformiser sur `upsert(uuid, item)`.~~ ✅ V4.1 (commit `6ab569b`) — confirmé canonique en V6.4-D5.
- [x] **S** — ~~`CycleWorkoutSyncable.upsert(item)` sans uuid → `upsert(uuid, item)` (déjà inclus dans Vague 4.1).~~ ✅ V4.1 (commit `dcf4d71`) — confirmé canonique en V6.4-D5.

### Groupe 6.5 — TypeConverters 🟠

**Effort total** : S (3h) → ✅ **terminé V6.4-E1 (commit `b51df9c`, 2026-05-05)**

- [x] **S** — ~~TypeConverters : `Gson()` partagé via `companion object { val gson = Gson() }`.~~ ✅ V6.4-E1 : `private object ConvertersGson { val gson: Gson = Gson() }` dans `data/local/Converters.kt`. Une seule instance réutilisée par les deux converters.
- [x] **S** — ~~TypeConverters : try/catch JSON dans `fromJson`.~~ ✅ V6.4-E1 : try/catch sur `JsonSyntaxException` dans `toList` et `toMap` ; log warning + retour `null`. Plus de crash sur payload corrompu/obsolète.
- [x] **S** — ~~`NotificationDataConverter` : décider signature `Map<String, String>` OU convention "tout en string sauf champs déclarés" pour éviter le bug Gson Int→Double.~~ ✅ V6.4-E1 : décision = `Map<String, String>?` (typage strict). `Notification.data` + `NotificationRepository.build()` x2 + callsites mis à jour. `NotificationCenter.notifyTimerDone` : `durationSeconds.toString()`. `PhoneNotificationManager` + `NotificationNavigationMapper` : retire `as? String` superflu.
- [x] **XS** — ~~Déplacer les TypeConverters dans `data/local/Converters.kt` dédié.~~ ✅ V6.4-E1 : `InstructionsConverter` retiré de `Exercise.kt`, `NotificationDataConverter` retiré de `Notification.kt`, les 2 consolidés dans `Converters.kt`. Séparation entity/converter respectée.

### Groupe 6.6 — Architecture serveur 🟡

**Effort total** : M (1-2 jours)

- [ ] **S** — `get_current_user` retourne juste un username → renvoyer un dict `{user_id, username}` ou un objet `User`. *Skip V6.6 : 2 callsites internes (require_admin + ancien get_current_user_id), changement non critique. Re-évaluer si besoin futur.*
- [x] **S** — ~~`get_current_user_id` fait +1 SELECT par requête → décoder le token et lire `payload["user_id"]` directement.~~ ✅ V6.6-B (commit `f36b14e`, 2026-05-05) : refactor pour décoder le JWT directement via `verify_token` + lire `user_id` du payload. Économie de 1 SELECT par endpoint user-scoped (~22 endpoints × N req/sec). Coté Android : tokens créés depuis V1.3 contiennent déjà `user_id`.
- [x] **XS** — ~~Erreur 404 vs 401 dans `get_current_user_id` → 401 si user supprimé.~~ ✅ Item caduc après V6.6-B : plus de SELECT user, donc plus de cas "user supprimé" = 404 ambigu. Si user_id absent du token → 401 (Token sans user_id).
- [ ] **S** — Mettre en place un logger structuré (`structlog` ou `loguru`) — supprime tous les `print` infrastructure. *Skip V6.6 : refactor lourd, demande politique de logging projet à définir.*
- [x] **S** — ~~Construire le DSN listener via `sqlalchemy.engine.url` au lieu de `replace("+asyncpg", "")`.~~ ✅ V6.6-B (commit `f36b14e`) : `make_url(...).set(drivername="postgresql")` robuste si le DSN contient des `+` ailleurs (ex. mot de passe URL-encodé).
- [x] **XS** — ~~`pg_listener` swallow Exception silencieusement → `logger.exception(...)`.~~ ✅ V6.6-B : 3 except silencieux passés en `logger.warning`/`logger.exception` explicite (logging stdlib, pas structlog).
- [x] **XS** — ~~`pg_listener.py:28-29` try/catch sans log → `logger.warning(...)`.~~ ✅ Idem V6.6-B.

**Total Vague 6** : ~3-4 semaines.

---

## §10 — Vague 7 — Code mort + cleanup + documentation

> Travail de propreté. Pas urgent, mais nécessaire pour l'hygiène long terme.

### Groupe 7.1 — Code mort confirmé 🟠

**Effort total** : M (1-2 jours)

- [x] **M** — ~~Supprimer les 44 endpoints `@POST` Android (vérifié jamais appelés).~~ ✅ V7.1-C3 (commit `00bf177`, 2026-05-05) : 38 @POST retirés sur 19 Apis Retrofit (script PowerShell + regex précis après 1ère tentative qui a écrasé trop). AuthApi.@POST("token") conservée (login OAuth légitime). Note : 38 et non 44, l'estimation initiale était haute.
- [x] **S** — ~~Supprimer endpoints Android orphelins : `PUT /actual-workout-exercises/by-uuid/{uuid}`, `GET /available-equipments/{uuid}`, `GET /planned-workout-exercises/{uuid}`, `DELETE /muscle-goals`.~~ ✅ V7.1-C3 suite (commit `d1dbff1`) : 4 endpoints retirés (0 callsite externe vérifié).
- [ ] **L** — Décider sort des endpoints serveur orphelins (cf. [TODO_FIXES §6](TODO_FIXES.md#6--code-mort--cleanup)) — soit aligner Android pour les utiliser, soit supprimer côté serveur. Liste : `PUT /users`, `PUT/DELETE /exercise-muscles/{uuid}`, `PUT/DELETE /cycle-workouts/{uuid}`, `GET /muscle-goals/{uuid}`, `GET /superset-exercises/group/{}`, `GET /planned-workout-exercise/by-uuid/{}`. **Reporté** : audit complexe par endpoint, demande à l'utilisateur de trancher cas par cas.

### Groupe 7.2 — Cleanup divers 🟡

**Effort total** : M (1 jour)

- [ ] **XS** — Supprimer `/secure-docs` HTML inline ou extraire dans `app/static/secure_docs.html`. *Skip : pas urgent, juste cosmétique de l'HTML inline.*
- [x] **XS** — ~~Retirer CORS `localhost:4200` (l'app est Android pas Angular).~~ ✅ V7.2-C1 (commit `b39142c`) : CORSMiddleware + ALLOWED_ORIGINS retirés intégralement (Android via Retrofit ne déclenche jamais CORS).
- [x] **XS** — ~~Supprimer `resolve_user_id_from_token` jamais utilisé.~~ ✅ V7.2-C1 (commit `b39142c`) : helper supprimé + imports orphelins (`AsyncSessionLocal`, `select`, `User`, etc.) retirés.
- [x] **XS** — ~~Supprimer bloc `WorkoutSession` commenté dans `seed_database.py`.~~ ✅ V7.2-C1 (commit `b39142c`) : 12 lignes commentées retirées (vestige du rename sessions → actual_workout).
- [x] **XS** — ~~Supprimer `view_database.py` référence MuscleWeeklySummary.~~ ✅ Déjà fait V5.1 sous-vague G (commit `dc84efe`).
- [ ] **XS** — Supprimer `notify_training_cycles_change()` (code mort après migration trigger Vague 4.3). *À auditer : grep n'a rien trouvé, peut-être déjà supprimé en V4.3.*
- [x] **XS** — ~~Retirer `MuscleWeeklySummarySyncHandler` injecté.~~ ✅ Déjà fait V5.1 sous-vague G (commit `7465608`).
- [ ] **S** — Décider pour `User.firstName` / `User.lastName` Room non utilisés. *Skip : décision utilisateur sur extend `UserOut` ou retire Room. Pas critique.*
- [ ] **XS** — Supprimer `RemoteDataGetter` si confirmé code mort. *Audit V7-C2 : 1 callsite réel dans `SyncSettingsViewModel:201` (bouton "Réinitialiser"). PAS du code mort. Item fermé.*
- [x] **XS** — ~~Supprimer `isLocalNewer` jamais utilisé dans `RemoteDataMerger`.~~ ✅ V7.2-C2 (commit `a5c5846`).
- [x] **XS** — ~~Corriger `RemoteDataMerger.mergeAllFromServer` shadow var `val log = true`.~~ ✅ V7.2-C2 (commit `a5c5846`) : shadow retiré + param par défaut bumpé `log: Boolean = true`.

### Groupe 7.3 — Documentation 🟠

**Effort total** : M (2-3 jours) → ✅ **terminé V7.3 (2026-05-05)**, 5 commits.

- [x] **S** — ~~Régénérer `routes.json` depuis OpenAPI ou supprimer.~~ ✅ V7.3 (commit `3ac21ed`) : supprimé. Source vivante = Swagger `/secure-docs`. `tests/full_test.py` qui le générait également supprimé.
- [x] **S** — ~~Auditer fichiers de tests existants — décider garder/adapter ou supprimer.~~ ✅ V7.3 (commit `3ac21ed`) : seul `tests/full_test.py` existait, utilisait HTTP Basic Auth obsolète (V1.3 JWT) + assertions trop lâches. Supprimé. Plan tests reels dans TODO_FEATURES "Plan de tests post-audit" (V8.1).
- [x] **M** — ~~Tutoriel "ajouter une nouvelle entity" dans `DEV_GUIDE.md` ou `docs/HOW_TO_ADD_ENTITY.md`.~~ ✅ V7.3 (commit `48d99da`) : `docs/HOW_TO_ADD_ENTITY.md` créé. Checklist 11 étapes (7 serveur + 4 Android) avec snippets template canoniques + cross-refs SERVEUR.md §2B-1 et APPLI_ANDROID.md §3D §2.
- [x] **M** — ~~Diagramme architecture global (Mermaid) dans `PROJECT_MAP.md` ou nouveau `ARCHITECTURE.md`.~~ ✅ V7.3 (commit `c16a574`) : `docs/ARCHITECTURE.md` créé. 7 diagrammes Mermaid (stack haut niveau, 3 sequences sync, couches Android internes, couches serveur internes, topologie déploiement PC dev + Pi prod).
- [x] **S** — ~~Documenter les endpoints critiques (auth flow, sync flow) avec exemples curl + payloads JSON réels.~~ ✅ V7.3 (commit `72eb095`) : `docs/FLOWS.md` créé. §1 Auth (signup/login/JWT/header/me), §2 Sync REST (patterns canoniques + exemple training-cycle + push/pull mecanique), §3 WebSocket (connexion + format event + tables 21), §4 Outillage (Swagger + psql LISTEN + Logcat).
- [x] **XS** — ~~`serveur/readme.md` → pointer vers DEV_GUIDE.md.~~ ✅ V7.3 (commit `77d31a3`) : 53 lignes legacy → 19 lignes pointer vers DEV_GUIDE.md + quickstart résumé.
- [x] **XS** — ~~`appli-android/exportToHTML/` → supprimer dossier local.~~ ✅ V7.3 : `rm -rf` (déjà gitignored, juste cleanup local).
- [x] **XS** — ~~`alembic.ini.sqlalchemy.url` → valeur cohérente.~~ ✅ V7.3 (commit `77d31a3`) : commentaire explicatif (valeur ignorée par env.py qui lit `settings.DATABASE_URL`). Garde default upstream pour ne pas masquer une vraie URL avec un faux mdp.
- [x] **XS** — ~~Revision ID Alembic placeholder `2025xxxx` → hash hex.~~ ✅ V7.3 (commit `77d31a3`) : commentaire historique. Renommer casserait `alembic upgrade head` sur PC dev + Pi prod tant qu'un `UPDATE alembic_version` n'est pas appliqué manuellement. Coût > bénéfice cosmétique.
- [x] **XS** — ~~`fill_database.py` ajouter `attach_triggers`.~~ ✅ V7.3 (commit `77d31a3`) : `conn.execute(text(attach_triggers_sql()))` après `Base.metadata.create_all`. Triggers idempotents (CREATE OR REPLACE TRIGGER).

### Groupe 7.4 — Cosmétique Android 🟡

**Effort total** : M (1 jour) → ✅ **terminé V7.4 (2026-05-05)**, 9 commits.

- [x] **XS** — ~~Routes hardcodées comme strings dans `MainActivity.NavHost` → `object Routes { const val LOGIN = "login"; ... }`.~~ ✅ V7.4 Batch 3 (commit `2b664fa`) : `navigation/Routes.kt` créé (19 const val routes simples + 5 PATTERN const val routes paramétriques + 5 helpers `fun route(uuid)` + 5 ARG_* keys). 15 fichiers migrés (MainActivity NavHost + DrawerContent 16 navigates + 8 Screens + BottomNavBar + SplashScreen + NotificationNavigationMapper). ~70 magic strings centralisées.
- [x] **XS** — ~~`session/{sessionUUID}` route → renommer ou documenter le legacy.~~ ✅ V7.4-E (commit `ce64faf`) : commentaire de doc dans MainActivity expliquant que la route UI conserve "session" alors que l'entité data a été renommée `actual_workout` (cf. memory). Param `sessionUUID` contient en réalité un `actual_workout.uuid`. 4 callsites navigate("session/...") inchangés.
- [x] **S** — ~~`println` partout dans VMs → `Log.d` filtrable.~~ ✅ V7.4 Batch 2 (commit `d522f31`) : 215 println → 0. Politique = DELETE pour debug noise (VMs + UI + 1 manager), CONVERT en Log.d/.w/.e pour les operationally-useful (EntitySyncUtils errors, RemoteDataGetter/Merger/Upserter via `if (log)` switch préservé, NetworkMonitor state transitions, SyncSettingsViewModel.logLocalDatabase button-wired dev tool). Cleanup d'orphelins induits (HomeVM init {}, WeekViewVM init {}, GoalRow.isGoalMet, MuscleGoalsManager.muscleDao injection inutilisée).
- [x] **XS** — ~~Naming class ≠ filename (3 VMs) → uniformiser.~~ ✅ V7.4-A (commit `b73fcd2`) : `LoginViewModel` → `LoginScreenViewModel`, `ProfileViewModel` → `ProfileScreenViewModel`, `RoutineTasksViewModel` → `RoutineTasksScreenViewModel`. Le nom de fichier fait foi (cf. décision utilisateur). 11 tags Log.e dans `RoutineTasksScreenViewModel` mis à jour pour rester alignés.
- [x] **XS** — ~~`NotificationRepository.build()` 2 versions → garder l'enum, marquer string `@Deprecated`.~~ ✅ V7.4-B (commit `1cf6d7d`) : `@Deprecated` + ReplaceWith sur l'overload String + `NotificationCenter.notifyTimerDone` migré vers `NotificationType.TIMER_DONE` enum. L'enum overload délègue toujours à la version String avec `@Suppress("DEPRECATION")` ciblé.
- [x] **XS** — ~~2 data classes "projection" dans fichiers de modèle → isoler dans `data/model/projections/`.~~ ✅ V7.4-C (commit `f4e6acf`) : `ActualWorkoutExerciseWithWorkoutDateAndSets` (sortie d'`ActualWorkoutExercise.kt`) + `ExerciseMuscleSimple` (sortie d'`ExerciseMuscle.kt`) → `data/model/projections/`. 5 imports mis à jour. Import `androidx.room.Embedded` orphelin retiré d'`ActualWorkoutExercise.kt`.
- [x] **XS** — ~~`StorageManager` ajouter log si image corrompue.~~ ✅ V7.4-D (commit `e772aca`) : `Log.w("StorageManager", "Corrupt custom/asset image: ...")` ajouté quand `BitmapFactory.decodeFile`/`decodeStream` retourne null. Plus de silent null impossible à diagnostiquer.

**Total Vague 7** : ~1-2 semaines.

---

## §11 — Vague 8 — Features + Polish

> Évolutions et améliorations. Au rythme des priorités utilisateur. Pas urgent.

### Groupe 8.1 — Plan de tests fonctionnels post-audit 🔥

**Consigne utilisateur explicite 2026-05-04** : *"des tests devront être faits durant le développement après"*.

**Effort total** : L-XL (2-3 semaines pour mise en place complète, démarrage progressif)

- [ ] **L** — Mettre en place pytest + httpx async client + factory_boy côté serveur. Test sur DB Postgres de test (Docker compose).
- [ ] **L** — Mettre en place JUnit + Mockk + Robolectric côté Android. Tests in-memory Room.
- [ ] **M** — Test prioritaires des 10 bugs 🔴 identifiés en analyse statique (cf. [TODO_FEATURES §4](TODO_FEATURES.md#4--plan-de-tests-fonctionnels-post-audit)).
- [ ] **L** — Multi-device test setup (2 émulateurs Android) pour valider les scénarios sync cross-user.
- [ ] **L** — Tests E2E (serveur de test + emulator) pour les flows critiques.

### Groupe 8.2 — Refresh token + sécurité avancée 🔥

**Effort total** : L (~1 semaine) → ✅ **terminé V8.2 (2026-05-06)**, 4 commits.

- [x] **M** — ~~Côté serveur : endpoint `/refresh` + table `refresh_tokens` (long-lived 30j, révocables).~~ ✅ V8.2 Batch 1 (commit `639cc91`) : modèle `RefreshToken` + migration Alembic `20260505_v8_2_refresh_tokens` + helpers `app/refresh_tokens.py` (hash bcrypt, rotation, **reuse detection** revoke-all si refresh re-utilisé) + endpoints `POST /token` (retourne `{access, refresh, token_type}`) + `POST /refresh` (rotation) + `POST /logout` (idempotent 204). Décision durée : **7 jours** au lieu de 30 (choix utilisateur, plus secure).
- [x] **M** — ~~Côté Android : étendre `Authenticator` pour appeler `/refresh` automatiquement avant logout.~~ ✅ V8.2 Batch 2 (commit `b698391`) : `Authenticator` sur 401 → POST `/refresh` sous **Mutex** (un seul refresh concurrent même avec 21 GETs parallèles, sinon reuse detection serveur + revoke all) → retry avec new access. Si `/refresh` KO → forceLogout (clear + onTokenExpired). `AuthManager.stopAuth()` devient suspend + appelle `/logout` best-effort.
- [x] **S** — ~~Migrer JWT en clair → `EncryptedSharedPreferences`.~~ ✅ V8.2 Batch 3 (commit `aaf6038`) : `androidx.security:security-crypto:1.1.0-alpha06` + `TokenManager` refactor avec `EncryptedSharedPreferences` (AES256_GCM + AES256_SIV) backed par Android Keystore. Migration silencieuse legacy `auth_prefs` → `auth_prefs_enc` au 1er init après update (idempotent, no relogin pour les users existants).
- [x] **S** — ~~Rate limit sur `/token` (slowapi, 5 tentatives/IP/min).~~ ✅ V8.2 Batch 4 (commit `b338a4c`) : `app/rate_limit.py` Limiter singleton + `SlowAPIMiddleware` + handler 429 JSON. Limites : `/token` 5/min, `/refresh` 30/min, `/signup` 3/hour. `/logout` pas de limite (no incentive attaquant). Smoke test E2E confirmé : 6e attempt sur `/token` → 429.
- [ ] **XS** — Sudoers Pi pour `systemctl restart sportapi` sans mot de passe.

**À déployer Pi prod** : `pip install -r requirements.txt` (slowapi + 3 deps transitives) + `alembic upgrade head` (migration `20260505_v8_2_refresh_tokens`). Vérifier que uvicorn tourne avec `--proxy-headers` (sinon Caddy masque l'IP source → tout le monde partage la même rate limit).

**Côté Android** : install build → migration silencieuse `auth_prefs` → `auth_prefs_enc` au 1er run, pas de relogin. Tester aussi le flow refresh en réduisant temporairement `ACCESS_TOKEN_EXPIRE_MINUTES=1` pour forcer une expiration rapide.

### Groupe 8.3 — Endpoint signup + onboarding ⭐

**Effort total** : L (~1 semaine) → 2/3 items finis (signup endpoint + UI). Onboarding flow déféré.
**Dépend de** : Vague 1.3 (`is_admin` en place), Vague 1.1 (user_router sécurisé)

- [x] **M** — ~~`POST /signup` public avec validation password, unique username, rate limit.~~ ✅ V1.1 (commit `562a021`, 2026-05-05) endpoint minimal + ✅ V8.2 Batch 4 (commit `b338a4c`) rate limit 3/hour. Validation Pydantic min_length=8 password + unique username 409 déjà en place.
- [x] **M** — ~~Écran "Créer un compte" Android.~~ ✅ V8.3 (commit `fa9bc83`, 2026-05-06). `SignupScreen.kt` + `SignupScreenViewModel.kt` + Routes.SIGNUP + lien "No account yet? Create one" sous LoginScreen + auto-login après signup réussi (réutilise `RetrofitInstance.login()` qui set les 2 tokens via V8.2). Smoke E2E : signup nouveau user → 201, duplicate → 409, login post-signup → 200 + tokens.
- [ ] **L** — Flow onboarding (3-5 écrans) : préférences, préselection muscles/exercises populaires, permission notifs. *Hors scope V8.3 MVP. Reste un nouveau user arrive sur app vide — acceptable jusqu'à V8.4+.*

### Groupe 8.4 — Stats / History / Modules feature ⭐

**Effort total** : L-XL

- [ ] **L** — `StatsViewModel` + `StatsScreen` : volume hebdo, progression, fréquence, charts Vico.
- [ ] **L** — `HistoryViewModel` + `HistoryScreen` : liste séances passées avec filtres.
- [ ] **L** — Refactor Chrono en feature module Style A (`chrono/`).
- [ ] **S** — Clarifier `ConversationsScreen` (chat coach IA ? messagerie ? notes ? placeholder ?). Demander à l'utilisateur.

### Groupe 8.5 — Module Nutrition ⭐ (long terme)

**Effort** : XL (2+ semaines)
**Dépend de** : Vagues 1-6 stabilisées (architecture saine pour ajouter une grosse feature)

- [ ] **XL** — Entités serveur : `Food`, `Meal`, `MealEntry`, `NutritionGoal`. CRUD/router/schema/trigger pour chacune.
- [ ] **XL** — Entités Android : symétriques + DAOs + Syncables + Handlers.
- [ ] **L** — Écrans : `NutritionScreen`, `MealScreen`, `FoodSearchScreen`.
- [ ] **L** — `NutritionViewModel` + calculs.
- [ ] **L** — Migration Room (ajout entities).
- [ ] **L** — Hors scope initial : intégration Open Food Facts (v2).

### Groupe 8.6 — DX / DevOps ⭐

**Effort total** : M-L

- [ ] **M** — `deploy_to_pi.ps1` PC → Pi en une commande.
- [ ] **M** — CI/CD GitHub Actions (workflow tests + workflow deploy sur push main).
- [ ] **M** — Webhook GitHub → Pi pour auto-pull (alternative à CI/CD).
- [ ] **S** — Variant Android `staging` pointer vers `staging.<public-dns>`.
- [ ] **S** — UI switcher URL serveur sans rebuild (admin caché).

### Groupe 8.7 — UX 🟠

**Effort total** : M (2-3 jours)

- [ ] **M** — Refonte snackbars sync : 1 global "Sync OK / Sync : X errors" au lieu de 22.
- [ ] **M** — Indicateur visuel persistant "Hors ligne" (bandeau sous TopAppBar).
- [ ] **L** — UI admin pour gérer les `is_admin` (lié à Vague 1.3 + Vague 8.3 onboarding).

### Groupe 8.8 — Performance 💡

**Effort total** : S (1 jour)
**Dépend de** : Vague 5.6 (DBML alignement)

- [ ] **S** — Indexes Postgres sur `user_id` (12 tables Type A).
- [ ] **S** — Indexes Postgres sur `<parent>_uuid` (FK referencing).
- [ ] **S** — Indexes Notification composites alignés DBML.
- [ ] **XS** — `User` ajouter `@Index unique` sur `username` Room.
- [ ] **S** — Tester Room journal mode WAL vs TRUNCATE actuel + mesurer impact.

### Groupe 8.9 — Suggestions Claude (à valider) 💡

**Effort total** : variable

> Items proposés en [TODO_FEATURES §9](TODO_FEATURES.md#9----suggestions-claude-à-valider). À intégrer dans les vagues concernées si validés :
> - Authenticator OkHttp → **Vague 4.5** ✅ déjà intégré
> - Logger structuré → **Vague 6.6** ✅ déjà intégré
> - Healthcheck endpoint → Vague 8 si CI/CD en place
> - Compression gzip → Vague 8 si grosses responses
> - Métriques Prometheus → Vague 8 si monitoring devient prio
> - **Backup automatique DB Pi** → 💡 reporté (décision utilisateur 2026-05-04 : *"osef, on refera forcément une DB ; la DB actuelle n'est pas importante"*). À ré-évaluer **uniquement** quand l'app aura de vrais utilisateurs avec de vraies données à protéger. Pas de prérequis avant les autres vagues.
> - Endpoint `DELETE /me` → Vague 8 si signup public en place
> - Feature flags → Vague 8 si A/B test souhaité

**Total Vague 8** : indéterminé selon priorités.

---

## §12 — Graphe de dépendances

> Diagramme ASCII des dépendances critiques entre items/groupes.

```
Quick Wins (autonome)
    │
    ▼
[Vague 1 — Sécurité critique]
    │
    ├─ 1.3 is_admin ──────┐
    │                     ▼
    └─ 1.1 routers publics ─→ require_admin disponible
    │                     │
    ├─ 1.2 secrets ──────┘ (autonome)
    │
    ▼
[Vague 2 — Bypass ownership] (parallélisable avec V1)
    │
    └─ 2.2 user_id schémas ──→ requis pour V5.2 (Room nullable fix)
    │
    ▼
[Vague 3 — Bloqueurs structurels]
    │
    ├─ 3.1 Migrations Room ──BLOQUEUR──→ V5 entière (suppression entité, naming, etc.)
    │                                    et Vague 8 Nutrition
    │
    ├─ 3.2 Format dates ────────────────→ V4.4 isRemoteNewer fix
    │                                    V4.4 Instant.parse fixes
    │
    ├─ 3.3 Source vérité Alembic ───────→ V4.2/V4.3 triggers fixes
    │                                    V3.4 alembic autogenerate
    │
    └─ 3.4 exec_pg.py refactor ─────────→ DX sans bloqueur fonctionnel
    │
    ▼
[Vague 4 — Sync fonctionnel]
    │
    ├─ 4.1 endpoints cassés (autonome)
    ├─ 4.2 triggers userId ──depuis V3.3──┘
    ├─ 4.3 5 entités sans push ──depuis V3.3──┘
    ├─ 4.4 bugs sync archi (résolu par V3.2)
    └─ 4.5 Authenticator 401 ──→ requis pour V8.2 refresh token
    │
    ▼
[Vague 5 — Cohérence schéma DB] (dépend STRICTEMENT de V3.1)
    │
    ├─ 5.1 MuscleWeeklySummary suppression ─requiert V3.1
    ├─ 5.2 Mismatches ──requiert V2.2 + V3.1
    ├─ 5.3 Naming colonnes ──requiert V3.1
    └─ 5.5 Soft-delete simplification ──→ V4.4 deleteLocalsAbsentFromRemote
    │
    ▼
[Vague 6 — Squelette uniforme] (parallélisable avec V5)
    │
    └─ 6.4 ChronoViewModel hardcode ──→ V8.4 refactor Chrono
    │
    ▼
[Vague 7 — Cleanup] (parallélisable avec V6)
    │
    └─ 7.1 endpoints orphelins serveur ──depuis V4.1 décision archi
    │
    ▼
[Vague 8 — Features + Polish] (dépend de V1-V7 stabilisées)
    │
    ├─ 8.1 Plan tests ──peut commencer dès V1
    ├─ 8.2 Refresh token ──depuis V4.5
    ├─ 8.3 Signup ──depuis V1.1 + V1.3
    ├─ 8.5 Nutrition ──depuis V3.1 + V5 stabilisée
    └─ 8.7/8.8 UX/Perf (autonomes)
```

---

## §13 — Items parallélisables

> Ce qui peut avancer en parallèle pour optimiser le débit.

### Toujours parallélisables (autonomes)

- **Quick Wins** : peuvent être faits dès maintenant, en parallèle de tout le reste
- **Documentation** (Vague 7.3) : peut s'écrire en parallèle de toutes les phases (la doc à jour aide les autres travaux)
- **Plan de tests** (Vague 8.1) : peut commencer dès Vague 1 sécurisée
- **Cleanup mineur** (Vague 7.2) : XS quand on a 30 min libre

### Parallélisables après Vague 1

- **Vague 2** (Bypass ownership) : pas de dépendance avec V1, peut tourner en parallèle
- **Vague 4.1** (endpoints cassés Android) : pas de dep avec V1/V2 (juste Android)

### Parallélisables après Vague 3

- **Vague 4.2/4.3** (triggers WS) : dès que V3.3 décidé
- **Vague 4.4** (bugs sync archi) : dès que V3.2 dates fixé
- **Vague 5** (cohérence schéma) : dès que V3.1 migrations Room en place
- **Vague 6** (squelette uniforme) : indépendant de V5, peut avancer en parallèle

### Items à faire séquentiellement (chemin critique)

```
Quick Wins → V1.3 is_admin → V1.1 routers publics → V2.2 user_id schémas
   → V3.1 Migrations Room → V5.1 MuscleWeeklySummary → V5.2 mismatches
```

→ **Chemin critique en ~3-4 semaines** si on enchaîne tout linéairement.
→ **Chemin parallélisé en ~2-3 semaines** si on peut avancer plusieurs vagues en même temps.

---

## §14 — Roadmap par phases

> Sans dates calendaires (consigne utilisateur). Phases logiques.

### Phase 0 — Pré-action

**Objectif** : se préparer à attaquer.

- [ ] Lire ce document REVIEW.md en entier
- [ ] Valider les Quick Wins (~1-2 jours)
- [ ] *(Décision utilisateur 2026-05-04 : backup auto Pi pas pertinent tant que la DB est jetable / refaite régulièrement. À ré-évaluer quand l'app aura de vrais utilisateurs.)*

### Phase 1 — Sécuriser la production

**Objectif** : rendre l'API exposée publiquement réellement sécurisée. **Non-négociable** si l'app est sur `<public-dns>`.

- [ ] Vague 1 — Sécurité critique exposée
- [ ] Vague 2 — Bypass ownership
- [ ] (Parallèle) Démarrer le plan de tests fonctionnels (Vague 8.1) sur les bugs sécurité

**Fin de phase** : aucun endpoint public écrit, ownership vérifié partout, secrets en prod sains, JWT validé proprement.

### Phase 2 — Lever les bloqueurs

**Objectif** : déverrouiller les refactors structurels. Sans cette phase, impossible de faire avancer V5 et V8.

- [ ] Vague 3 — Bloqueurs structurels (migrations Room, format dates, source de vérité, refactor exec_pg)

**Fin de phase** : Room peut migrer sans crash, format wire dates unifié, Alembic source de vérité unique, scripts DB séparés.

### Phase 3 — Réparer la sync multi-device

**Objectif** : fonctionnement multi-device propre + tests en pratique des bugs identifiés.

- [ ] Vague 4 — Sync fonctionnel (endpoints cassés, triggers, gestion 401)
- [ ] Tests pratiques des bugs 🔴 (Vague 8.1)

**Fin de phase** : sync individuelle marche pour toutes les entités, push WS marche pour les 5 entités manquantes, comportement 401 propre.

### Phase 4 — Aligner schéma + architecture

**Objectif** : cohérence complète des 3 stacks (Postgres / Pydantic / Room).

- [ ] Vague 5 — Cohérence schéma DB
- [ ] Vague 6 — Squelette uniforme + refactor archi (en parallèle)

**Fin de phase** : schéma cohérent partout, politique squelette uniforme appliquée à 100%, plus de doublons stricts, plus de violations de couches.

### Phase 5 — Hygiène

**Objectif** : nettoyer le code mort + finaliser la documentation.

- [ ] Vague 7 — Code mort + cleanup + documentation

**Fin de phase** : code mort éliminé, doc à jour, conventions documentées.

### Phase 6 — Évolution

**Objectif** : ajouter la valeur produit.

- [ ] Vague 8 — Features + Polish (au rythme des priorités utilisateur)

**Fin de phase** : open. Stats/History implémentés, Nutrition prêt, refresh token, CI/CD opérationnel.

---

## Fin

**Ce REVIEW.md est l'aboutissement de l'audit (étapes 1 → 7).** Combine :
- 162 fixes ([TODO_FIXES.md](TODO_FIXES.md))
- ~30 features ([TODO_FEATURES.md](TODO_FEATURES.md))
- 8 suggestions Claude validées ou intégrées dans les vagues

**Total estimé** : ~3-4 mois de travail temps plein pour une exécution complète. Pour un projet perso à temps partiel, compter 6-12 mois selon le rythme. Les Quick Wins + Phase 1 (~3-5 semaines) sont la priorité absolue si l'app est exposée publiquement.

**Pour utiliser ce document** :
1. Cocher les items au fur et à mesure (`[ ]` → `[x]`)
2. Mettre à jour les dépendances si certaines évolutions changent l'ordre
3. Référencer ce document dans les commits / PRs (ex: "Fixes REVIEW.md §4 Vague 1.1")
4. Re-évaluer la priorisation après chaque phase si de nouveaux findings apparaissent en pratique

---

*REVIEW.md créé en étape 7 (2026-05-04). Audit complet (étapes 1 → 7) terminé.*
