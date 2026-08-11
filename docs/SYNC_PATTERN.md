# SYNC_PATTERN.md — Sync convergent multi-device sans tombstones

> Référence du pattern de sync utilisé entre le serveur sport-app (FastAPI + Postgres + WS) et ses clients (Android Room actuel, Angular ou autre futur). Documente la convergence des deletes **sans `deleted_at` côté serveur**, et sert de tuto si tu écris un nouveau client (Angular, iOS, Flutter, Electron, etc.).

## TL;DR

- Le serveur **hard-delete** les rows. Aucun tombstone, aucune colonne `deleted_at`.
- Chaque client persiste un flag `synced` par row : `true` = "vient du serveur", `false` = "créée localement, pas encore push".
- Au pull/merge, le client supprime tous ses locaux où `synced=true && key NOT in remote_response`.
- Convergence cross-device garantie même si un client était offline au moment du delete distant.

Cf. décision politique [TODO_FEATURES.md §2 "Soft-delete propre OU retrait de deleted_at"](TODO_FEATURES.md) — Option A retenue 2026-05-05.

## Les 3 contrats côté client

Tout client qui veut converger avec le serveur sport-app doit honorer ces 3 conditions :

| # | Contrat | Pourquoi |
|---|---|---|
| 1 | **Stockage local persistant** des rows reçues du serveur | Sinon pas de "local à comparer au remote" -- le client ne peut pas détecter les divergences. |
| 2 | **Flag `synced` par row** : `true` = vient du serveur / `false` = créée localement pas encore push | Sans ça, on ne distingue pas "absente car deleted serveur" vs "absente car création locale jamais pushée". |
| 3 | **Routine `pruneStaleLocals` au pull** : pour chaque local où `synced=true && key not in remote` → delete local | C'est ce qui propage le delete depuis n'importe quel autre device. |

## Implémentation référence Android

Cf. [SyncMergeOps.kt:25-41](../appli-android/app/src/main/java/com/example/sportapp/sync/base/SyncMergeOps.kt#L25-L41) :

```kotlin
suspend fun mergeFromRemote(syncable: SyncableEntity<*>) {
    val typed = syncable as SyncableEntity<Any>
    val remote = typed.getRemote()
    val local = typed.getAllOnce().associateBy(typed::keyOf)

    // Phase 1 : insert/update les rows remote plus récentes
    for (r in remote) {
        val l = local[typed.keyOf(r)]
        if (l == null || isRemoteNewer(typed.updatedAtOf(l), typed.updatedAtOf(r))) {
            typed.insertFromServer(r)
        }
    }

    // Phase 2 : prune les locaux orphelins (CONVERGENCE DELETE)
    val remoteKeys = remote.map(typed::keyOf).toSet()
    local.values
        .filter { typed.syncedOf(it) && typed.keyOf(it) !in remoteKeys }
        .forEach { typed.deleteLocal(it) }
}
```

## Scénario complet : Device B offline pendant un delete distant

### Setup

3 devices A, B, C — tous connectés. Tous ont `{1✓, 2✓, 3✓}` localement. Postgres = `{1, 2, 3}`. (`✓` = `synced=true`)

### Étape 1 — Device A delete row 2 (online)

```
A : dao.markAsPendingDeletion(2)  // local marker
A : push REST DELETE /api/v1/.../2
    -> serveur : hard DELETE Postgres
    -> serveur : broadcast WS "row 2 deleted"
A : dao.delete(2)  // suppression locale après ack
Postgres : {1, 3}
```

### Étape 2 — Device C reçoit le WS event (online)

```
C écoute WS -> reçoit "row 2 deleted"
C : dao.delete(2)
C local : {1✓, 3✓}
```

### Étape 3 — Device B est OFFLINE (airplane mode)

```
B : rate l'event WS
B local : {1✓, 2✓, 3✓}  <- row 2 toujours là
```

### Étape 4 — Device B revient online

```
NetworkMonitor détecte connexion
  -> SyncCoordinator.onNetworkAvailable()
  -> push d'abord (rien à push pour B)
  -> mergeFromRemote() pour chaque entité
```

### Étape 5 — Le merge converge

```
mergeFromRemote(syncable) execute :

  remote = api.getAll()   -> {1, 3}  (row 2 N'EXISTE PLUS côté serveur)
  local  = dao.getAllOnce() -> {1✓, 2✓, 3✓}

  Phase upsert :
    for r in {1, 3} -> no-op (déjà identiques)

  Phase prune :
    remoteKeys = {1, 3}
    for l in local where synced=true && l.key NOT in remoteKeys :
      -> l.key = 2 : synced=true ET 2 ∉ {1,3} -> MATCH
      -> dao.deleteLocal(2)

B local après merge : {1✓, 3✓}  ✅ convergence atteinte
```

**B a déduit le delete sans recevoir d'event WS**, juste en comparant son cache local à la liste authoritative du serveur, en s'appuyant sur le flag `synced`.

## Pourquoi le flag `synced=true` est crucial

Imagine que B, pendant qu'il était offline, a **créé une nouvelle row 4 localement** :

```
B local avant reconnect : {1✓, 2✓, 3✓, 4✗}  (4 = synced=false, jamais envoyée serveur)
remote = {1, 3}
remoteKeys = {1, 3}

Phase prune : filter synced=true && key not in remoteKeys
  - row 2 : synced=true ✓, 2 ∉ remoteKeys ✓ -> DELETE
  - row 4 : synced=false ✗ -> SKIP (préservée !)

Après merge : {1✓, 3✓, 4✗}
Phase push (next) : 4 sera push au serveur -> devient synced=true
```

Sans la garde `synced=true`, on supprimerait row 4 (création locale jamais sync) → **perte de données**. La garde fait que B distingue :
- "row que j'ai un jour reçue du serveur" (`synced=true`) → si plus côté serveur = deleted ailleurs → safe to delete localement
- "row que j'ai créée moi-même offline" (`synced=false`) → préservée jusqu'au prochain push réussi

## Trade-off Option A (sans `deleted_at`) vs Option B (avec tombstones)

| Aspect | Option B (`deleted_at` serveur) | Option A (actuelle) |
|---|---|---|
| Source de vérité du delete | Tombstones serveur (la table grossit) | Absence de la row dans GET |
| Détection client | `if (item.deletedAt != null) delete` | `if (synced && !in remote) delete` |
| Coût query serveur | `WHERE deleted_at IS NULL` à chaque SELECT | Aucun |
| Cleanup cron serveur | Nécessaire (sinon table grossit indéfiniment) | Aucun (delete = vraiment supprimé) |
| Compat retroactive multi-device | Plus tolérant (event explicite reçu en différé) | Suppose `getAll()` complet user-scoped |
| Complexité code client | `if remote.deletedAt != null` partout | Une seule routine `pruneStaleLocals` factorisée |
| Risque false-positive delete | 0 | 0 (garde `synced=true`) |

## Limitations / pièges à connaître

| Piège | Mitigation |
|---|---|
| **GET paginé** : si serveur retourne page 1/5, le client ne doit PAS conclure que rows hors-page sont deleted | `getAll()` retourne la liste **complète** user-scoped (cas actuel sport-app, pas de pagination). Si pagination future → endpoint `/changes-since/<timestamp>` ou `deleted_at` ciblé. |
| **GET filtré** (ex. `GET /workouts?date=...`) | Le prune ne doit s'appliquer QUE sur le scope du filtre, pas globalement. Comparer uniquement les rows du même scope local. Aujourd'hui sport-app n'a pas ce cas (les `getAll()` retournent tout user-scoped). |
| **Race condition UUID collision** | UUIDs v4 (128-bit) → quasi-impossible. Pas un vrai problème en pratique. |
| **Client buggé qui ignore le contrat** (ex. fresh-fetch + écrasement total sans flag synced) | Perd ses créations locales en cours. Bug client-spécifique, pas systémique. |
| **Multi-tabs partageant IndexedDB** (cas SPA Angular) | IndexedDB locks au niveau transaction → toutes les tabs voient la même DB et appliquent le même prune. OK natif. |

## Application à un nouveau client

### Cas 1 — SPA "fresh fetch" à chaque session (pas de cache persistant)

L'utilisateur ouvre l'app → load des données serveur → state in-memory (NgRx, Zustand, BehaviorSubject) → fermeture app = state perdu.

→ **Aucun problème de divergence possible**. Pas de "local stale". Pas besoin du flag `synced`. Pas besoin de `pruneStaleLocals`. C'est le cas le plus simple.

### Cas 2 — SPA + cache offline (IndexedDB / localStorage / Service Worker)

L'utilisateur peut continuer à voir/modifier les données même offline. Au retour online, sync avec serveur.

→ **Implémenter les 3 contrats** :
1. Cache : IndexedDB (recommandé pour les volumes) ou localStorage (max ~5MB).
2. Flag `synced` : ajouter une colonne dans chaque store IndexedDB.
3. Prune lifecycle : au reconnect (ou au login, ou périodiquement), iterate sur les stores, faire un GET serveur, diff, delete les absents `synced=true`.

C'est ce que ferait par exemple un PWA "offline first" type Notion / Linear.

### Cas 3 — Mobile native (iOS, Flutter, RN) ou desktop Electron

Idem cas 2 — n'importe quel stockage local persistant + flag `synced` + routine prune au pull.

## Inter-opérabilité Android ↔ Angular (futur hypothétique)

Si tu décides un jour de faire une UI Angular qui se sync avec le même backend :

1. **Backend serveur** : aucun changement, c'est exactement le même protocole REST + WS.
2. **Angular** : implémenter les 3 contrats. Reuse les mêmes DTO (camelCase wire).
3. **Tests cross-device** : delete sur Android pendant qu'Angular est offline (cas typique : tu fais ton workout sur le tel, puis tu ouvres ton ordi le soir avec un cache Angular vieux d'une heure) → Angular merge avec prune au login → convergence.

Et tu peux mélanger : delete sur Angular pendant qu'Android offline → flow inverse, Android prune au reconnect.

## Conclusion

Le mécanisme est **transport-agnostique** (REST + WS) et **stockage-agnostique** (Room / IndexedDB / Core Data / SQLite Flutter / etc.). C'est un **protocole**, pas une implémentation. Tant qu'un nouveau client respecte les 3 contrats au moment où tu l'ajoutes, **zéro changement serveur nécessaire**.

---

**Historique** :
- 2026-05-05 (V5.5) : Drop `deleted_at` 21 tables Postgres + Room v8→v9 + nettoyage models/schemas/RemoteDataMerger.
- 2026-05-07 (T4.2) : Refonte sync layer Android avec extraction de `pruneStaleLocals` dans `SyncMergeOps.kt` (factorisé pour les 20 SyncableEntity).
- 2026-05-11 : Ce document créé pour servir de référence cross-client.
