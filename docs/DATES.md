# DATES — Audit transverse de la gestion des dates

> ⚠️ **DOC FIGÉ AU 2026-05-04 — BUG RÉSOLU V3.2 LE 2026-05-05.** Le bug des 3 formats wire décrit dans les §1-§9 ci-dessous est entièrement corrigé : format unifié `YYYY-MM-DDTHH:MM:SS.UUUUUUZ` posé par `iso_utc()` SQL / `UTCDateTime` Pydantic / `getNowISO8601()` Android. Doc conservé comme artefact de l'audit, non maintenu. Voir §10 « Après V3.2 » en fin de doc et historique V3.2 dans [CLAUDE.md](../CLAUDE.md).

> **Sous-étape 4.5** de l'audit. Demandée explicitement par l'utilisateur le 2026-05-04 : *"j'avais plein de problèmes de type : gestion d'type de dates, que ce soit en sync, ajouts etc. Voir des crashs côté appli. Bref que à cause du format de dates qui n'était pas le même côté serveur et appli lors d'ajouts ou de sync etc."*
>
> Audit transverse traçant la chaîne **production → wire → consommation** des dates dans le projet sport-app, sur les 3 stacks (Postgres / Pydantic / Room/Kotlin) et les 4 catégories de dates rencontrées.
>
> **Méthode** : lecture intégrale de `CustomDateUtils.kt` + grep des producteurs/consommateurs (Instant.now, LocalDate.now, Instant.parse, LocalDate.parse) + lecture des queries SQL Room + analyse format Pydantic + format triggers Postgres + lecture `RemoteDataMerger.isRemoteNewer`.

## Sommaire

- [1. TL;DR — le bug central en 30 secondes](#1--tldr--le-bug-central-en-30-secondes)
- [2. Inventaire des champs date par catégorie](#2--inventaire-des-champs-date-par-catégorie)
- [3. Producteurs Android (qui pose les dates côté client ?)](#3--producteurs-android)
- [4. Producteurs serveur (Pydantic + Postgres trigger)](#4--producteurs-serveur)
- [5. Format wire JSON observé pour chaque catégorie](#5--format-wire-json-observé-pour-chaque-catégorie)
- [6. Consommateurs Android (parse + comparaison + queries)](#6--consommateurs-android)
- [7. Round-trip complet d'un `updatedAt` — exemple détaillé](#7--round-trip-complet-dun-updatedat--exemple-détaillé)
- [8. Round-trip d'une `date` métier (jour de séance)](#8--round-trip-dune-date-métier-jour-de-séance)
- [9. Findings 4.5](#9--findings-45)

---

## 1. TL;DR — le bug central en 30 secondes

**3 formats de date coexistent** dans le système, **incompatibles entre eux** :

| Source | Format émis pour un timestamp |
|---|---|
| **Android `getNowISO8601()`** (= `Instant.now().toString()`) | `"2025-01-15T14:30:00.123456789Z"` — UTC, suffixe `Z`, jusqu'à **9 décimales** (nanoseconds) |
| **Pydantic** (sortie REST `XOut` via `jsonable_encoder`) | `"2025-01-15T14:30:00.123456+00:00"` — UTC offset numérique, **6 décimales** max |
| **Trigger Postgres** (`'updatedAt', rec.updated_at` dans `jsonb_build_object`) | `"2025-01-15T15:30:00.123456+01:00"` — offset **du timezone du serveur** (Europe/Paris sur la Pi), 6 décimales |

**Conséquences** :
- 🔴 **Comparaison lexicographique fausse** dans `RemoteDataMerger.isRemoteNewer` ([RemoteDataMerger.kt:594](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L594)) : `remoteUpdated > localUpdated` peut retourner n'importe quoi quand local pose `Z` et remote envoie `+00:00` ou `+01:00` car les caractères 0x5A (`Z`) > 0x2B (`+`). **C'est exactement le bug "réécriture sur changement de données en sync"** mentionné par l'utilisateur.
- 🔴 **Crashs `Instant.parse`** pour 4 fonctions de `CustomDateUtils` (`minusDays`, `startOfWeek`, `startOfMonth`, `startOfYear`, `formatRelativeTime`, `fromISOToLocalDate`) si le serveur renvoie le format avec espace `"2025-12-26 00:00:00+01"` (Postgres legacy text cast). Le commentaire dans `CustomDateUtils.toLocalDateFromDb:205-211` admet explicitement ce format → l'auteur l'a rencontré dans la pratique. **C'est exactement les "crashs côté appli"** mentionnés.
- 🔴 **Truncation silencieuse de précision** : Android pose 9 décimales, Pydantic tronque à 6 → l'`updatedAt` re-renvoyé par le serveur est **différent** de celui posé localement → marqué comme "remote plus récent" → **réécriture en boucle** sur chaque sync.
- 🟠 **Queries SQLite mixtes** : `ActualWorkoutDao` a 3 stratégies pour matcher la même colonne `date` (`substr(date, 1, 10)`, `WHERE date = :date`, `WHERE date LIKE :day || '%'`). Si la colonne contient parfois `"2025-01-15"` et parfois `"2025-01-15T14:30:00.123Z"`, les queries fixes (`WHERE date = '2025-01-15'`) ratent les workouts complets.
- 🟠 **`date()` SQLite ne reconnaît pas le format ISO complet** : `ActualWorkoutSetDao.getDoneSetsForMuscleInWeek` utilise `date(aw.date) BETWEEN ...` ([ActualWorkoutSetDao.kt:55](../appli-android/app/src/main/java/com/example/sportapp/data/local/ActualWorkoutSetDao.kt#L55)). SQLite reconnaît `YYYY-MM-DD` et `YYYY-MM-DD HH:MM:SS` mais **pas** `YYYY-MM-DDTHH:MM:SS.123Z` → `date()` retourne NULL → la query rate tous les sets.

**Ces 3 bugs expliquent tous les symptômes décrits** : (a) crashs, (b) réécriture intempestive, (c) données fantômes / non trouvées.

---

## 2. Inventaire des champs date par catégorie

### Catégorie A — Timestamps techniques (4 champs × 21 entités)

`updated_at`, `deleted_at`, `created_at`, `checked_at` — datetime ISO 8601 complet attendu, en UTC ou avec offset.

| Champ | Postgres | Pydantic | Room | Posé par |
|---|---|---|---|---|
| `updated_at` | `DateTime(timezone=True) nullable` | `Optional[datetime]` (alias `updatedAt`) | `String? = getNowISO8601()` | Android au write, **jamais** par le CRUD serveur (cf. §4) |
| `deleted_at` | idem | `Optional[datetime]` (alias `deletedAt`) | `String? = null` | jamais posé par aucun CRUD (champ inutile, cf. DATABASES §8) |
| `created_at` (Notification only) | `DateTime(timezone=True) server_default=func.now() nullable=False` | `Optional[datetime]` (alias `createdAt`) | `String? = null` 🟠 | **Postgres** au INSERT (server_default) ; Android pose via `NotificationRepository.build()` |
| `checked_at` (RoutineTaskCheck only) | `DateTime(timezone=True) nullable` | `Optional[datetime]` (alias `checkedAt`) | `String? = null` | Android via `getNowISO8601()` ([RoutineTasksScreenViewModel:129](../appli-android/app/src/main/java/com/example/sportapp/viewmodel/RoutineTasksScreenViewModel.kt#L129)) |

### Catégorie B — Dates métier "jour" (3 champs)

Format `"YYYY-MM-DD"` attendu, stocké en `String` (pas `Date`).

| Champ | Postgres | Pydantic | Room | Posé par |
|---|---|---|---|---|
| `actual_workouts.date` | `String nullable=False` | `str` | `String` | VM (CalendarViewModel utilise `day` reçu de l'UI au format `LocalDate.toString()` = `"YYYY-MM-DD"`) |
| `routine_task_checks.date` | `String nullable=False` (DBML annonce `date` 🟠 — cf. DATABASES §10) | `str` | `String` | VM via `selectedDate.value` (format `"YYYY-MM-DD"`) |
| `planned_workouts.day_of_week` (pas vraiment une date — un nom) | `String nullable=False` | `str` (alias `dayOfWeek`) | `String` | "Monday", "Tuesday"... `getTodayDayOfWeek()` |

### Catégorie C — Dates métier `Date` Postgres (3 champs)

Format `"YYYY-MM-DD"` strict, mais **typé `Date` côté serveur** → Pydantic exige le format strict.

| Champ | Postgres | Pydantic | Room | Format wire |
|---|---|---|---|---|
| `training_cycles.start_date` | `Date nullable=False` | `date` (alias `startDate`) | `String` | `"2025-01-15"` strict ; ne supporte pas un datetime ISO complet |
| `training_cycles.end_date` | idem | idem | idem | idem |
| `muscle_weekly_summary.week_start_date` | `Date` (sans `nullable=False` 🟠) | `date` (alias `weekStartDate`) | `String` | idem |

### Catégorie D — Heures métier (2 champs)

Format `"HH:MM"` attendu.

| Champ | Postgres | Pydantic | Room | Format wire |
|---|---|---|---|---|
| `routine_periods.start_time` | `String nullable=False` (DBML annonce `time` 🟠) | `str` (alias `startTime`) | `String` | `"06:30"` |
| `routine_periods.end_time` | idem | idem | idem | `"09:00"` |

---

## 3. Producteurs Android

### `CustomDateUtils.kt` — la fabrique officielle

[utils/CustomDateUtils.kt](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt) (246 lignes)

```kotlin
fun getNowISO8601(): String = Instant.now().toString()
```

**Format produit** par `Instant.now().toString()` :
- ISO 8601 strict avec `T`
- Toujours **UTC** (suffixe `Z`)
- **Précision variable** : si nano-secondes = 0 → `"2025-01-15T14:30:00Z"` (sans décimales) ; sinon **3, 6 ou 9 décimales** selon ce que produit `Instant.now()` (dépend de la résolution de l'horloge système — sur Android Linux kernel récent, microseconds ou nanoseconds).
- Exemples réels :
  - `"2025-01-15T14:30:00Z"`
  - `"2025-01-15T14:30:00.123Z"`
  - `"2025-01-15T14:30:00.123456Z"`
  - `"2025-01-15T14:30:00.123456789Z"`

→ **Variabilité de précision** = piège pour les comparaisons lexicographiques (cf. §6).

### Autres fonctions productives

| Fonction | Format produit | Usage |
|---|---|---|
| `getTodayLocalDate()` | `LocalDate` (objet, pas string) | UI Compose |
| `getTodayIsoDay()` | `"YYYY-MM-DD"` (LocalDate.now(systemDefault).toString()) | dates métier "jour" — utilise **timezone du device** ⚠ |
| `getTodayStartWithTimezone()` | `"yyyy-MM-dd HH:mm:ssXXX"` (avec espace) — **format Postgres legacy** | Inutilisé (vérifié par grep — `getTodayStartWithTimezone` n'apparaît nulle part hors la définition) |
| `toISO8601(localDate)` | `"YYYY-MM-DDT00:00:00Z"` | conversion LocalDate → instant UTC |
| `getCurrentWeekISO()` | `"2025-W03"` | semaine ISO (`muscle_goals.weekISO`) |
| `getStartOfCurrentWeek()` / `getEndOfCurrentWeek()` | `"YYYY-MM-DD"` | bornes de semaine (lundi-dimanche) |

### Producteurs concurrents (hors `CustomDateUtils`)

7 fichiers utilisent **directement** `Instant.now()` ou `LocalDate.now()` au lieu de passer par `CustomDateUtils` :

| Fichier | Ligne | Usage |
|---|---|---|
| `CalendarViewModel.kt` | 126, 163 | `LocalDate.now(zone)` — où `zone` est `ZoneId.systemDefault()` |
| `CalendarViewScreen.kt` | 49 | `LocalDate.now(zone)` |
| `DrawerViewModel.kt` | 92, 93 | `Instant.parse(isoString)` + `Instant.now()` pour calcul "depuis dernière sync" |
| `ExerciseStatsSection.kt` | 170, 191 | `Instant.parse(iso)` + `Instant.now()` pour stats |

→ **Tous les usages utilisent `java.time` via ThreeTenABP**. Aucune trace de `java.util.Date()` ou `Calendar` legacy. ✅ Bon point.
→ Mais redondance avec `CustomDateUtils` — politique squelette uniforme : tous devraient passer par `CustomDateUtils`.

---

## 4. Producteurs serveur

### Pydantic — `XOut` via routers

Tous les routers utilisent `jsonable_encoder(obj, by_alias=True)` (136 occurrences sur 22 fichiers — vérifié par grep). Pour un champ `datetime`, `jsonable_encoder` produit :

```
"2025-01-15T14:30:00.123456+00:00"   # UTC, offset numérique, 6 décimales max
```

ou si timezone non-UTC :

```
"2025-01-15T15:30:00.123456+01:00"   # offset Europe/Paris
```

**Jamais** de suffixe `Z` (Pydantic v2 par défaut écrit l'offset numérique).

### Triggers Postgres — `to_jsonb(rec.updated_at)`

Dans les 17 triggers individuels, le payload contient `'updatedAt', rec.updated_at` (clef quotée, valeur brute Postgres). Quand `jsonb_build_object` reçoit un `timestamptz`, Postgres convertit en string JSON via le format **ISO 8601 avec offset**.

Le format dépend du **`timezone` du serveur Postgres** :
- Si le serveur tourne en `Europe/Paris` (cas de la Pi en prod, déduit de `getTodayStartWithTimezone` qui produit `+01`/`+02`) → trigger émet `"2025-01-15T15:30:00.123456+01:00"`
- Si serveur en UTC (cas potentiel sur PC dev Windows) → `"2025-01-15T14:30:00.123456+00:00"`

→ **Le format dépend de l'environnement d'exécution** ! Push depuis PC → format A ; broadcast trigger Pi → format B. Incohérent.

### CRUDs serveur — `existing.updated_at = dto.updated_at`

**Trouvaille importante** : les CRUDs ne posent **jamais leur propre `updated_at`**. Ils prennent la valeur du DTO client (vérifié par grep — 7 occurrences `existing.updated_at = dto.updated_at` sur 5 CRUDs, jamais `obj.updated_at = func.now()` ou `datetime.utcnow()`).

→ **L'`updated_at` Postgres = exactement celui posé par Android via `getNowISO8601()`**, à la précision Pydantic près (tronqué à microsec).

→ Mais le trigger Postgres re-sérialise au format Postgres avec offset → **format différent en sortie** de celui qu'on a posé en entrée. Le client qui pose `Z` puis reçoit le push WS reçoit `+01:00` → **comparaison fausse**.

### Cas spécial : `Notification.created_at` posé par Postgres

```python
created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
```

→ Postgres pose `created_at` au INSERT via `NOW()`. **Précision microseconde Postgres**, format wire `"2025-01-15T15:30:00.123456+01:00"` (offset serveur).

### Seed `seed_database.py`

Pas vérifié (hors périmètre — pas de `datetime.now()` utilisé pour les timestamps techniques car ils sont nullable).

---

## 5. Format wire JSON observé pour chaque catégorie

### Catégorie A — Timestamps techniques

| Direction | Format observé |
|---|---|
| **Android → serveur** (PUT/POST body via Gson) | `"2025-01-15T14:30:00.123456789Z"` — produit par `Instant.now().toString()` |
| **Serveur → Android** (REST GET via Pydantic XOut) | `"2025-01-15T14:30:00.123456+00:00"` — Pydantic `jsonable_encoder` |
| **Serveur → Android** (WebSocket trigger payload) | `"2025-01-15T15:30:00.123456+01:00"` — Postgres `to_jsonb(timestamptz)` selon timezone serveur |

→ **3 formats reçus** côté Android pour le **même** `updatedAt`. Et 1 format envoyé. Aucun n'est strictement identique.

### Catégorie B — Dates métier "jour"

| Direction | Format |
|---|---|
| **Android → serveur** | `"2025-01-15"` (LocalDate.toString) |
| **Serveur → Android** | `"2025-01-15"` ✅ aligné (Pydantic `str` passe tel quel) |

✅ Pas de problème pour cette catégorie en pratique.

### Catégorie C — Dates Postgres `Date`

| Direction | Format |
|---|---|
| **Android → serveur** | `"2025-01-15"` (LocalDate.toString) — accepté par Pydantic `date` |
| **Serveur → Android** REST | `"2025-01-15"` ✅ (Pydantic sérialise `date` en `"YYYY-MM-DD"`) |
| **Serveur → Android** trigger WS | `"2025-01-15"` ✅ (Postgres `to_jsonb(date)` produit le même format) |

✅ Pas de problème.

→ **Mais** : si Android envoie `"2025-01-15T00:00:00Z"` au lieu de `"2025-01-15"`, Pydantic raise `ValidationError` car `date` n'accepte que `"YYYY-MM-DD"` strict. Bug latent si quelqu'un utilise `getNowISO8601()` pour un champ Date.

### Catégorie D — Heures

| Direction | Format |
|---|---|
| Android → serveur, serveur → Android | `"06:30"` ✅ aligné |

✅ Pas de problème.

---

## 6. Consommateurs Android

### Parses (`Instant.parse`, `LocalDate.parse`)

| Fichier | Ligne | Code | Format toléré |
|---|---|---|---|
| `CustomDateUtils.fromISOToLocalDate` | 48 | `Instant.parse(iso)` | ISO 8601 instant strict (`T`, `Z` ou offset) — **rejette espace** |
| `CustomDateUtils.minusDays` | 54 | `Instant.parse(iso)` | idem ; **crash** si format avec espace |
| `CustomDateUtils.startOfWeek/Month/Year` | 60, 67, 74 | `Instant.parse(iso)` | idem |
| `CustomDateUtils.formatRelativeTime` | 183 | `Instant.parse(iso)` | idem ; utilisé pour les notifs |
| `CustomDateUtils.toLocalDateFromDb` | 225-242 | **3 formats fallback** : `Instant.parse` → `OffsetDateTime.parse(yyyy-MM-dd HH:mm:ssXXX)` → `LocalDate.parse(take(10))` | ✅ robuste — l'auteur a explicitement géré le format Postgres avec espace |
| `CustomDateUtils.isDateInCurrentWeek` | 91 | `LocalDate.parse(date.take(10))` | accepte tout, prend les 10 premiers caractères |
| `CustomDateUtils.getDayOfWeekFromDate` | 102 | `LocalDate.parse(cleanedDate, formatter)` | strict `yyyy-MM-dd` |
| `DrawerViewModel.kt:92` | | `Instant.parse(isoString)` | crash si espace ; pas de try/catch |
| `ExerciseStatsSection.kt:170` | | `Instant.parse(iso)` | crash si espace |

→ **`toLocalDateFromDb` est le seul parse robuste.** Tous les autres `Instant.parse` crash sur le format Postgres legacy avec espace.

### Comparaisons — `RemoteDataMerger.isRemoteNewer`

[RemoteDataMerger.kt:591-595](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L591) :

```kotlin
private fun isRemoteNewer(localUpdated: String?, remoteUpdated: String?): Boolean {
    if (remoteUpdated == null) return false
    if (localUpdated == null) return true
    return remoteUpdated > localUpdated     // ⚠ comparaison lexicographique
}
```

**Fonctionne lexicographiquement** correct **SI** les 2 strings sont dans le **même format avec mêmes paddings**. Avec les 3 formats coexistants :

#### Cas 1 : Local pose `Z`, remote arrive avec `+01:00` (trigger Pi)

```
local  = "2025-01-15T14:30:00.123456789Z"   (UTC, 9 décimales, suffixe Z)
remote = "2025-01-15T15:30:00.123456+01:00"  (UTC+01, 6 décimales, +01:00)
```

Comparaison caractère par caractère :
- Index 0-10 : `2025-01-15T` égal
- Index 11 : `1` vs `1` égal
- Index 12 : `4` vs `5` → **`4 < 5`** → `remote > local` → `isRemoteNewer` retourne `true`

Mais en réalité, **les deux représentent le même instant !** (14:30 UTC = 15:30 UTC+01). Comparaison string ne le sait pas → **on écrase le local avec un remote qui est en fait identique** → boucle de réécriture.

#### Cas 2 : Local plus récent (réellement) mais remote a un offset positif

```
local  = "2025-01-15T16:30:00.123456789Z"   (16:30 UTC = 17:30 Paris)
remote = "2025-01-15T15:30:00.123456+01:00"  (15:30 UTC+01 = 14:30 UTC = ANCIEN !)
```

Comparaison : index 11 `1` vs `1`, index 12 `6` vs `5` → `local > remote` → `isRemoteNewer` retourne `false`. ✅ correct par chance.

#### Cas 3 : `Z` vs `+00:00` au même instant

```
local  = "2025-01-15T14:30:00.123Z"
remote = "2025-01-15T14:30:00.123456+00:00"
```

Comparaison caractère par caractère jusqu'à index 23 :
- `Z` (0x5A) vs `4` (0x34) → `local > remote` → `isRemoteNewer` retourne `false`

Mais à instant égal, c'est un faux négatif. **L'app n'écrase jamais avec le remote**, garde sa version locale → divergence permanente.

#### Cas 4 : Différence par précision uniquement

```
local  = "2025-01-15T14:30:00.123456789Z"   (Android, 9 décimales)
remote = "2025-01-15T14:30:00.123456+00:00" (Pydantic tronque à 6, retransmet en remote via REST)
```

Index 26 : `7` (local) vs `+` (remote) → `7` (0x37) > `+` (0x2B) → `local > remote` → `isRemoteNewer` retourne `false`. ✅ ok mais fragile.

→ **Globalement** : `isRemoteNewer` retourne **n'importe quoi** dès que les formats diffèrent, ce qui est le cas systématique du round-trip. Le seul cas où ça marche : si local et remote sont **strictement dans le même format**, ce qui n'arrive **jamais** entre Android (Z) et serveur (+00:00 ou +01:00).

### Queries SQLite Room sur les colonnes `date`

| DAO | Ligne | Pattern | Risque |
|---|---|---|---|
| `ActualWorkoutDao:59` | `WHERE date LIKE :day || '%'` | suppose `date` commence par `"YYYY-MM-DD"` | ✅ marche pour les 3 formats observés |
| `ActualWorkoutDao:67` | `WHERE substr(aw.date, 1, 10) BETWEEN :start AND :end` | extrait les 10 premiers car. | ✅ robuste |
| `ActualWorkoutDao:73` | `WHERE substr(date, 1, 7) = :yearMonth` | extrait `"YYYY-MM"` | ✅ robuste |
| `ActualWorkoutDao:88` | `WHERE date = :date LIMIT 1` (`getActualWorkoutByDate`) | comparaison stricte | 🟠 ne match pas si la colonne contient `"2025-01-15T..."` et qu'on cherche `"2025-01-15"` |
| `ActualWorkoutDao:92` | `WHERE substr(date, 1, 10) = :day LIMIT 1` (`getActualWorkoutByDay`) | extrait | ✅ robuste |
| `ActualWorkoutSetDao:55` | `date(aw.date) BETWEEN :startOfWeek AND :endOfWeek` | utilise `date()` SQLite | 🔴 **`date()` ne reconnaît pas le format ISO complet avec `T` et `Z`** → retourne NULL → query rate tous les sets (cf. doc SQLite : reconnaît seulement `YYYY-MM-DD`, `YYYY-MM-DD HH:MM`, `YYYY-MM-DD HH:MM:SS` et quelques variantes — pas `YYYY-MM-DDTHH:MM:SSZ`) |
| `ActualWorkoutExerciseDao:96` | `WHERE aw.date BETWEEN :start AND :end` | comparaison string lex | ✅ marche par chance car format YYYY-MM-DD passe la comparaison lex |
| `ActualWorkoutSetDao:26` | idem | idem | ✅ |
| `RoutineTaskCheckDao:20, 26` | `WHERE date = :date` | comparaison stricte | ✅ marche car `date` est posée en `"YYYY-MM-DD"` strict des deux côtés |

→ **Bug réel** : `ActualWorkoutDao.getActualWorkoutByDate` (ligne 88) si la colonne contient un datetime complet et qu'on cherche par jour → retourne null. Mais en pratique, l'app pose `actual_workouts.date = day` (LocalDate) donc le format est toujours `"YYYY-MM-DD"` → **bug latent, pas actif**. Garde-fou : ajouter une assertion ou normaliser au write.

→ **Bug réel** : `ActualWorkoutSetDao.getDoneSetsForMuscleInWeek` (ligne 55) avec `date(aw.date)` peut retourner NULL si le format de la colonne `actual_workouts.date` change. Aujourd'hui ça marche par chance (la colonne contient `"YYYY-MM-DD"` strict), mais **fragile** : un seul write avec format ISO complet casse la query.

---

## 7. Round-trip complet d'un `updatedAt` — exemple détaillé

Scenario : l'utilisateur modifie un exercise sur device A, la sync push au serveur, le serveur broadcast WS à device B, puis device A relance une sync `mergeAllFromServer` plus tard.

### Étape 1 — Device A : `ExerciseDao.update`

```kotlin
suspend fun updateExercise(exercise: Exercise) {
    val now = getNowISO8601()  // → "2025-01-15T14:30:00.123456789Z"
    updateInternal(exercise.copy(updatedAt = now, synced = false))
}
```

Room écrit en SQLite : `updatedAt = "2025-01-15T14:30:00.123456789Z"`.

### Étape 2 — Device A : sync montante via `ExerciseSyncable.upsert`

Retrofit + Gson sérialise le data class :

```json
{
  "uuid": "...",
  "userId": 1,
  "name": "Squat",
  ...
  "updatedAt": "2025-01-15T14:30:00.123456789Z"
}
```

POST/PUT vers le serveur.

### Étape 3 — Serveur : Pydantic parse

`exercise_router.upsert_exercise` reçoit un body `ExerciseCreate` :
- Pydantic lit `updatedAt` (alias) ou `updated_at` (field name)
- Parse `"2025-01-15T14:30:00.123456789Z"` en `datetime`
- `datetime` Python max précision = 6 décimales → **`123456789` est tronqué à `123456`**
- Résultat : `datetime(2025, 1, 15, 14, 30, 0, 123456, tzinfo=timezone.utc)`

### Étape 4 — Serveur : CRUD upsert

```python
existing.updated_at = dto.updated_at  # datetime UTC microseconde
```

SQLAlchemy stocke en `timestamptz`. Postgres normalise selon son `timezone` config :
- Si serveur en UTC : stocké tel quel
- Si serveur en `Europe/Paris` : stocké en interne en UTC mais affiché avec offset `+01:00` ou `+02:00` selon la saison

### Étape 5 — Serveur : trigger NOTIFY fire

```sql
payload := jsonb_build_object(
    'type', 'exercise_updated',
    'payload', jsonb_build_object(
        ...
        'updatedAt', rec.updated_at,  -- timestamptz → JSON
        ...
    ),
    'userId', get_user_id_for('exercises', rec.uuid)
);
PERFORM pg_notify('db_events', payload::text);
```

Postgres convertit `timestamptz → JSON string` via le format ISO 8601 avec offset. Sur la Pi en `Europe/Paris` :

```json
{
  "type": "exercise_updated",
  "payload": {
    ...
    "updatedAt": "2025-01-15T15:30:00.123456+01:00"
  },
  "userId": 1
}
```

→ **Le format est différent de ce que device A avait posé !** (3 différences : `T15` vs `T14`, `+01:00` vs `Z`, 6 décimales vs 9).

### Étape 6 — Device B : `ExerciseSyncHandler.handle`

```kotlin
val updatedAt = JsonUtils.getNullableString(payload, "updatedAt")  // "2025-01-15T15:30:00.123456+01:00"
val exercise = Exercise(..., updatedAt = updatedAt, synced = true)
dao.insertFromServer(exercise)
```

Room écrit `updatedAt = "2025-01-15T15:30:00.123456+01:00"` côté SQLite de device B.

→ Device B a maintenant **un format différent** dans sa Room que celui qu'il poserait s'il modifiait l'exercise lui-même (`getNowISO8601()` produirait `Z`).

### Étape 7 — Device B : merger 3-way au prochain sync

`mergeAllFromServer` récupère via REST GET → Pydantic sérialise en `+00:00` :

```json
{
  ...
  "updatedAt": "2025-01-15T14:30:00.123456+00:00"
}
```

Compare avec local :
- Local (issu du WS étape 6) : `"2025-01-15T15:30:00.123456+01:00"`
- Remote (via REST) : `"2025-01-15T14:30:00.123456+00:00"`

`remoteUpdated > localUpdated` ?
- Index 11 : `1` vs `1` égal
- Index 12 : `4` vs `5` → `4 < 5` → `remote < local` → `isRemoteNewer = false`

Donc **device B garde sa version WS (avec +01:00)**. ✅ par chance pas d'écrasement, mais purement aléatoire.

### Étape 8 — Device A : `mergeAllFromServer` plus tard

Device A relance la sync. `mergeAllFromServer` REST GET :

Local (posé étape 1) : `"2025-01-15T14:30:00.123456789Z"`
Remote (via REST Pydantic) : `"2025-01-15T14:30:00.123456+00:00"`

Compare :
- Index 0-23 : `2025-01-15T14:30:00.123` égal
- Index 23-26 : `456` vs `456` égal
- Index 26 : `7` (local) vs `+` (remote) → `7` (0x37) > `+` (0x2B) → `local > remote` → `isRemoteNewer = false`

Device A garde sa version locale. ✅

### Étape 9 — Le bug se déclenche

Maintenant supposons que device A se déconnecte et device B modifie l'exercise. Device B fait un nouveau write :

```
local B = "2025-01-15T16:00:00.789Z"   (16h00 UTC, depuis getNowISO8601)
```

Push, trigger fire, device A reçoit via WS :

```
remote au format trigger Pi = "2025-01-15T17:00:00.789+01:00"
```

`isRemoteNewer(local A, remote)` ?
- local A = `"2025-01-15T14:30:00.123456789Z"`
- remote  = `"2025-01-15T17:00:00.789+01:00"`
- Index 11 : `1` vs `1` égal
- Index 12 : `4` vs `7` → `4 < 7` → `remote > local` → `isRemoteNewer = true` ✅ (par chance)

Mais l'inverse : device A fait un write **après** B :

```
local A = "2025-01-15T17:30:00.000Z"
remote (= ce que B a fait, vu plus tôt) = "2025-01-15T17:00:00.789+01:00"
```

Compare :
- Index 11 : `1` vs `1` égal
- Index 12 : `7` vs `7` égal
- Index 13 : `:` vs `:` égal
- ... jusqu'à `30` vs `00` → `3` (0x33) > `0` (0x30) → `local > remote` → `isRemoteNewer = false`

Device A garde sa version → mais... `17:00 +01:00 = 16:00 UTC` < `17:30 UTC` donc **réellement** local A est plus récent → **comportement correct par chance**.

#### Cas pathologique

```
local A = "2025-01-15T14:30:00.000Z"           (14:30 UTC)
remote  = "2025-01-15T15:00:00.000+01:00"      (14:00 UTC = ancien !)
```

Compare :
- Index 11-12 : `1` `4` vs `1` `5` → `4 < 5` → `remote > local` → `isRemoteNewer = true`

Mais **réellement, le remote est plus ancien** ! → **Device A écrase sa version récente avec une version ancienne**. **C'est exactement le bug "réécriture sur changement de données en sync"** mentionné par l'utilisateur.

→ **Cause racine** : `isRemoteNewer` ne **normalise pas** les fuseaux avant comparaison. Solution : parser en `Instant` (qui normalise en UTC absolu) puis comparer.

```kotlin
private fun isRemoteNewer(localUpdated: String?, remoteUpdated: String?): Boolean {
    if (remoteUpdated == null) return false
    if (localUpdated == null) return true
    val l = runCatching { Instant.parse(normalize(localUpdated)) }.getOrNull() ?: return true
    val r = runCatching { Instant.parse(normalize(remoteUpdated)) }.getOrNull() ?: return false
    return r > l
}
```

(avec `normalize` qui gère le format `"YYYY-MM-DD HH:mm:ss+01"` Postgres legacy)

---

## 8. Round-trip d'une `date` métier (jour de séance)

Plus simple, **pas de bug** observé :

1. Device A : VM passe `day = "2025-01-15"` (LocalDate.toString)
2. Push → JSON `{"date": "2025-01-15"}`
3. Pydantic `str` accepte tel quel
4. Postgres stocke `actual_workouts.date = '2025-01-15'`
5. Trigger : `'date', rec.date` → JSON `"date": "2025-01-15"` ✅
6. Device B reçoit, stocke `"2025-01-15"`
7. Queries SQL : `substr(date, 1, 10) = '2025-01-15'` ✅

Pas de problème pour cette catégorie.

→ **Mais** : si quelqu'un dans le futur utilise `getNowISO8601()` pour cette colonne (par erreur), tout casse :
- Pydantic `str` accepte `"2025-01-15T14:30:00.123Z"` ❌ pas validé
- Postgres stocke la string complète
- Queries `WHERE date = '2025-01-15'` rate
- `date(aw.date)` côté SQLite retourne NULL

→ Garde-fou recommandé : valider côté Pydantic avec un `pattern` ou un type `date` strict.

---

## 9. Findings 4.5

Reportés à [TODO_FIXES.md](TODO_FIXES.md). Récap par criticité :

### 🔴 Critique

- **`RemoteDataMerger.isRemoteNewer` comparaison lexicographique fausse pour les timestamps multi-formats** ([RemoteDataMerger.kt:591-595](../appli-android/app/src/main/java/com/example/sportapp/sync/RemoteDataMerger.kt#L591)) — 3 formats coexistent (`Z`, `+00:00`, `+01:00`), comparaison string ne normalise pas les fuseaux → **réécriture intempestive** sur changement de données en sync (cas pathologique §7 étape 9). Solution : parser en `Instant` + comparer en absolu, avec `try/catch` qui tombe sur le format Postgres legacy avec espace.
- **`Instant.parse` crash sur format Postgres legacy avec espace** dans 6 fonctions de `CustomDateUtils` (`fromISOToLocalDate`, `minusDays`, `startOfWeek`, `startOfMonth`, `startOfYear`, `formatRelativeTime`) + 2 callsites externes (`DrawerViewModel.kt:92`, `ExerciseStatsSection.kt:170`). Le commentaire de `toLocalDateFromDb:205-211` admet que ce format arrive en pratique. **Cause des crashs côté appli** mentionnés. Solution : utiliser `toLocalDateFromDb` ou créer un `parseInstantSafe(iso)` utilitaire avec les 3 fallbacks.
- **`ActualWorkoutSetDao.getDoneSetsForMuscleInWeek` utilise `date(aw.date)` SQLite** ([ActualWorkoutSetDao.kt:55](../appli-android/app/src/main/java/com/example/sportapp/data/local/ActualWorkoutSetDao.kt#L55)) qui retourne NULL pour les formats ISO complet (`T...Z`). Bug latent : marche aujourd'hui car `actual_workouts.date` contient toujours `"YYYY-MM-DD"`, mais casse au premier write avec format datetime complet. Solution : remplacer par `substr(aw.date, 1, 10) BETWEEN :startOfWeek AND :endOfWeek` (cohérent avec les autres queries).
- **3 formats wire pour le même `updatedAt`** : Android pose `Z`, Pydantic REST renvoie `+00:00`, trigger Postgres renvoie `+01:00` (timezone Pi). Pas un bug isolé mais une cause racine de tous les autres : devrait être normalisé. Solution recommandée : **forcer UTC partout**
  - Côté Postgres : `(rec.updated_at AT TIME ZONE 'UTC')` dans tous les triggers, OU `SET TIMEZONE = 'UTC'` au niveau cluster
  - Côté Pydantic : configurer `model_serializer` pour forcer UTC `Z` (par défaut `+00:00`)
  - Côté Android : `Instant.now().toString()` produit déjà `Z` ✅

### 🟠 Important

- **Truncation silencieuse de précision Android (9 décimales) → Pydantic (6 décimales) → Postgres (6 décimales)** — chaque round-trip change l'`updatedAt` de 3 décimales. Conséquence avec `isRemoteNewer` : faux positifs/négatifs constants. Solution : tronquer côté Android à 6 décimales avant push (`Instant.now().truncatedTo(ChronoUnit.MICROS).toString()`) pour aligner avec Pydantic.
- **Variabilité de précision `Instant.now().toString()`** — selon que `nano = 0` ou non, la string fait 20, 24, 27 ou 30 caractères. Comparaison string sensible à la longueur. Solution : tronquer à microsec systématiquement, et padder à 6 décimales (jamais 0 décimales).
- **`ActualWorkoutDao.getActualWorkoutByDate(date)`** ([ActualWorkoutDao.kt:88](../appli-android/app/src/main/java/com/example/sportapp/data/local/ActualWorkoutDao.kt#L88)) `WHERE date = :date` — comparaison stricte. Si `date` contient `"2025-01-15T..."` et on cherche `"2025-01-15"`, retourne null. Bug latent. Solution : utiliser `substr(date, 1, 10) = :day` (ou supprimer cette query, doublon avec `getActualWorkoutByDay`).
- **7 fichiers utilisent `Instant.now()` / `LocalDate.now()` directement** au lieu de passer par `CustomDateUtils` — risque de divergence. Centraliser tous les accès dans `CustomDateUtils`.
- **`DrawerViewModel.kt:92`** `Instant.parse(isoString)` sans try/catch — crash potentiel si la valeur lue depuis SharedPreferences est au format Postgres legacy. Wrapper avec `runCatching`.
- **`ExerciseStatsSection.kt:170`** `Instant.parse(iso)` idem.
- **Pas de validation de format Pydantic** sur les champs `date: str` (catégorie B — `actual_workouts.date`, `routine_task_checks.date`). Un client peut envoyer un datetime ISO complet → stocké tel quel → casse les queries SQLite côté autre device. Solution : `Field(..., pattern=r"^\d{4}-\d{2}-\d{2}$")` ou type `datetime.date` Pydantic puis `str(...)` au CRUD.
- **`Notification.created_at: Optional[datetime]` côté Pydantic** vs `nullable=False server_default=func.now()` côté Postgres — Pydantic ne peut pas garantir non-null à la sortie. Aligner sur `datetime` non-nullable (Pydantic peut omettre l'envoi à l'INSERT, le serveur pose).
- **`getTodayStartWithTimezone` produit le format Postgres legacy avec espace** ([CustomDateUtils.kt:33-40](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt#L33)) mais **n'est appelée nulle part** (vérifié par grep). Code mort à supprimer. Confirme aussi que ce format n'est jamais émis par Android — seulement reçu (du serveur).

### 🟡 Mineur

- **`getTodayIsoDay()` utilise `ZoneId.systemDefault()`** ([CustomDateUtils.kt:80](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt#L80)) — la "date du jour" dépend du fuseau du device. Un user qui voyage peut avoir des incohérences (date locale ≠ date UTC). Décider : (a) toujours UTC (cohérent avec `getTodayLocalDate()` qui utilise `ZoneOffset.UTC`), (b) toujours local (cohérent avec l'expérience utilisateur "ma journée à moi"). Actuellement les deux fonctions divergent.
- **`getTodayLocalDate()` UTC vs `getTodayIsoDay()` systemDefault** — incohérence de timezone entre 2 fonctions de la même classe. Trancher.
- **DBML annonce `date`/`time` pour des colonnes stockées en `String`** (déjà noté DATABASES §10). Si on aligne les modèles (option recommandée), cela résout la moitié des problèmes : Pydantic `date` et `time` validateraient le format strict côté serveur.
- **`Locale.getDefault()` dans `isDateInCurrentWeek`** ([CustomDateUtils.kt:94](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt#L94)) — la définition de "semaine courante" dépend de la locale (US: dimanche, FR: lundi). Utiliser `WeekFields.ISO` partout pour cohérence avec `getCurrentWeekISO`.

### Synthèse de la cause racine

**Tous les bugs dates de l'app ont la même origine** : pas de **format wire canonique normalisé** entre Android, Pydantic et Postgres. Si on impose **UTC + ISO 8601 + 6 décimales fixe + suffixe `Z`** des 3 côtés, **tous les bugs ci-dessus disparaissent** sans toucher à la logique applicative.

Fix prioritaire (par ordre de coût croissant) :
1. **Côté Android** : créer `Instant.now().truncatedTo(ChronoUnit.MICROS).toString()` dans `getNowISO8601()` (truncation à microsec).
2. **Côté Pydantic** : `model_serializer` ou `field_serializer` qui force `Z` au lieu de `+00:00` sur tous les `datetime` Out.
3. **Côté Postgres trigger** : remplacer `'updatedAt', rec.updated_at` par `'updatedAt', to_char(rec.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')` dans tous les triggers — émission UTC + `Z` strict + 6 décimales.
4. **Côté `RemoteDataMerger.isRemoteNewer`** : parser en `Instant` avant comparaison (avec fallback robuste sur format legacy au cas où).

Ces 4 fixes pris ensemble éliminent les 4 🔴.

---

## 10. Après V3.2 (2026-05-05) — système unifié

**V3.2 entièrement appliqué** (6 commits code + 1 doc, voir [docs/REVIEW.md §6 Groupe 3.2](REVIEW.md)). Les 4 fixes prioritaires + le sweep de tous les `Instant.parse` ont été déployés.

### Format wire canonique projet

**Un seul format** sur le wire pour tous les timestamps techniques (`updated_at`, `deleted_at`, `created_at`, `read_at`, `archived_at`, `checked_at`) :

```
"YYYY-MM-DDTHH:MM:SS.UUUUUUZ"
```

ISO 8601, **UTC strict**, **6 décimales fixes** (microsec), **suffixe `Z`**.

### 3 mécanismes garants (source de vérité unique)

| Source | Fichier | Mécanisme |
|---|---|---|
| Postgres triggers | [`app/db_triggers/iso_utc_helper.sql`](../serveur/app/db_triggers/iso_utc_helper.sql) | Fonction SQL `iso_utc(timestamptz) RETURNS text` — `to_char(ts AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')`. Wrappe `rec.updated_at` / `rec.deleted_at` dans les 16 fragments triggers. |
| Pydantic schemas | [`app/utc_datetime.py`](../serveur/app/utc_datetime.py) | Type `UTCDateTime = Annotated[datetime, PlainSerializer(_to_utc_z)]`. Tous les champs `*_at` des 21 schémas (sauf user) sont `Optional[UTCDateTime]`. |
| Android producteur | [`CustomDateUtils.kt`](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt) `getNowISO8601()` | `canonicalFormatter.format(Instant.now().truncatedTo(ChronoUnit.MICROS))` avec pattern `"yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"`. Force 6 décimales fixes même quand microsec = 0. |

Chaque mécanisme contient un **docstring de tête identique** référençant les 2 autres + ce paragraphe → grep `Format wire canonique projet` retrouve les 3 sources en 1 commande.

### Tolérance lecture

Côté Android, [`CustomDateUtils.parseInstantSafe(iso): Instant?`](../appli-android/app/src/main/java/com/example/sportapp/utils/CustomDateUtils.kt) tolère 3 formats à la lecture :
1. **Format canonique projet** (cas 99% post-V3.2)
2. Format Postgres legacy avec espace (`"yyyy-MM-dd HH:mm:ss+01:00"` / `+01`)
3. Date pure (`"yyyy-MM-dd"` → début de journée UTC)

→ Aucun crash sur des données legacy résiduelles, mais en émission tout passe par les 3 mécanismes ci-dessus.

### `RemoteDataMerger.isRemoteNewer`

Refait : parse les 2 strings via `parseInstantSafe` puis compare avec `Instant.isAfter()` en **UTC absolu**. Plus de comparaison lexicographique. Le bug "réécriture intempestive en sync" mentionné par l'utilisateur est résolu.

### Conséquence pratique

- Push depuis Android → serveur reçoit format canonique → Pydantic le re-émet en canonique. Round-trip stable.
- Trigger Postgres broadcast WS → format canonique. Plus de dépendance au timezone serveur.
- Comparaison sync (`isRemoteNewer`) → en Instant absolu. Plus de faux positif/négatif sur l'offset.

**Pour ajouter un nouveau timestamp** dans le futur :
1. Côté SQLAlchemy : `DateTime(timezone=True)`
2. Côté Pydantic : `Optional[UTCDateTime]` (importer depuis `app.utc_datetime`)
3. Côté trigger : `'<champ>', iso_utc(rec.<champ>)` dans le fragment
4. Côté Room : `String? = null` (ou défaut via `getNowISO8601()` si écrit côté client)

C'est tout. Aucune conversion ailleurs n'est nécessaire.

---

*Sous-étape 4.5 terminée. Volume audité : `CustomDateUtils.kt` intégral + 4 grep ciblés + cross-référence aux 22 modèles SQLAlchemy + 22 schémas Pydantic + 22 modèles Room + 17 triggers SQL (déjà lus en §3D / §4). Étape 4 (Bases de données) **complète**. Étape suivante : 5 (Intégration serveur ↔ appli — `docs/INTEGRATION.md`).*

*V3.2 implémenté 2026-05-05 (cf. §10 ci-dessus).*
