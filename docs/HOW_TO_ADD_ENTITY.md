# How to add a new entity (cross-stack checklist)

> Quand tu ajoutes une entité (ex. pour le module Nutrition à venir), tu dois toucher ~15 fichiers sur les 2 stacks pour rester cohérent avec le squelette canonique. Cette checklist évite les oublis.

**Pré-requis** : avoir lu [`docs/SERVEUR.md §2B-1`](SERVEUR.md) (squelette CRUD canonique). Le squelette DAO Style A est référencé ci-dessous (§8.2) — la version détaillée de l'audit historique est dans [`docs/APPLI_ANDROID.md §3D §2`](APPLI_ANDROID.md) (doc figé au 2026-05-04, structure du squelette inchangée depuis).

**Convention** : `<Entity>` = nom CamelCase singulier (`Workout`), `<entities>` = snake_case pluriel route (`workouts`), `<entity_table>` = snake_case pluriel table (`workouts`).

**Préfixe API** : tous les endpoints applicatifs sont versionnés sous `/api/v1/` via `app.include_router(r, prefix="/api/v1")` dans [`serveur/app/main.py`](../serveur/app/main.py). Les paths dans les **routers FastAPI** s'écrivent **sans** le préfixe (il est ajouté au montage) ; la **base URL Retrofit** côté Android l'inclut (`build.gradle.kts` → `API_BASE_URL = "https://<pi-fqdn>/api/v1/"`). Endpoints utility hors préfixe : `/healthz`, `/secure-docs`, `/token-helper`, `/webhook/deploy`.

---

## Côté serveur (Python)

### 1. Modèle SQLAlchemy — `serveur/app/models/<entity>.py`

```python
from sqlalchemy import Column, Integer, String, Boolean, Float, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base

class <Entity>(Base):
    __tablename__ = "<entity_table>"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    uuid = Column(String, unique=True, nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)

    # ... champs métier ...

    synced = Column(Boolean, nullable=False, default=False)
    pending_deletion = Column(Boolean, nullable=False, default=False)
    updated_at = Column(DateTime(timezone=True), nullable=True)
```

Conventions :
- **Toujours** `id` + `uuid` (sinon `attach_triggers.sql` ignore la table — pas de push WS)
- `user_id` FK → `users.id` avec `ondelete="CASCADE"` si user-scoped (Type A)
- Pas de `default` côté Python si la valeur est arbitraire (ex. `set_order`) — cf. [CLAUDE.md §10 politique defaults](../CLAUDE.md)
- `nullable=False` pour les états (cf. [CLAUDE.md §11 politique UPPER_CASE](../CLAUDE.md))

### 2. Schéma Pydantic — `serveur/app/schemas/<entity>_schema.py`

```python
from pydantic import BaseModel
from typing import Optional
from app.utc_datetime import UTCDateTime

class <Entity>Base(BaseModel):
    model_config = {"populate_by_name": True}

    uuid: str
    # ... champs métier ...

    synced: bool = False
    pendingDeletion: bool = False
    updatedAt: Optional[UTCDateTime] = None

class <Entity>Create(<Entity>Base):
    pass

class <Entity>Out(<Entity>Base):
    userId: int
```

Conventions :
- `userId` **uniquement dans `Out`** (jamais dans `Base`/`Create` — V2.2)
- Dates : `UTCDateTime` (format wire canonique `YYYY-MM-DDTHH:MM:SS.UUUUUUZ`, V3.2)
- `model_config = {"populate_by_name": True}` (V6.2-III)

### 3. CRUD canonique — `serveur/app/crud/<entity>_crud.py`

Squelette canonique V6.2 :

```python
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from fastapi import HTTPException
from app.models.<entity> import <Entity>
from app.schemas.<entity>_schema import <Entity>Create

async def get_all_<entities>(db: AsyncSession, user_id: int) -> list[<Entity>]:
    res = await db.execute(select(<Entity>).where(<Entity>.user_id == user_id))
    return res.scalars().all()

async def get_<entity>_by_uuid(db: AsyncSession, uuid: str, user_id: int) -> <Entity> | None:
    res = await db.execute(select(<Entity>).where(<Entity>.uuid == uuid, <Entity>.user_id == user_id))
    return res.scalar_one_or_none()

async def upsert_<entity>(db: AsyncSession, uuid: str, dto: <Entity>Create, user_id: int) -> <Entity>:
    existing = await get_<entity>_by_uuid(db, uuid, user_id)
    if existing is None:
        # Vérifier qu'aucun autre user ne possède déjà cet uuid
        cross = await db.execute(select(<Entity>).where(<Entity>.uuid == uuid))
        if cross.scalar_one_or_none() is not None:
            raise HTTPException(status_code=403, detail="UUID owned by another user")
        new = <Entity>(**dto.model_dump(), user_id=user_id)
        db.add(new)
    else:
        for k, v in dto.model_dump().items():
            setattr(existing, k, v)
        existing.user_id = user_id  # injecté serveur, jamais lu du payload
    await db.commit()
    return existing or await get_<entity>_by_uuid(db, uuid, user_id)

async def delete_<entity>(db: AsyncSession, uuid: str, user_id: int) -> bool:
    existing = await get_<entity>_by_uuid(db, uuid, user_id)
    if existing is None:
        return False
    await db.delete(existing)
    await db.commit()
    return True
```

Règles V2.1+V2.2 : ownership check + 403 cross-user ; `user_id` jamais lu du payload.

### 4. Router canonique — `serveur/app/routers/<entity>_router.py`

```python
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_session
from app.deps import get_current_user_id
from app.crud import <entity>_crud
from app.schemas.<entity>_schema import <Entity>Create, <Entity>Out

<entity>_router = APIRouter(tags=["<entities>"])

@<entity>_router.get("/<entities>", response_model=list[<Entity>Out])
async def list_<entities>(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    return await <entity>_crud.get_all_<entities>(db, user_id)

@<entity>_router.get("/<entities>/{uuid}", response_model=<Entity>Out)
async def get_<entity>(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    item = await <entity>_crud.get_<entity>_by_uuid(db, uuid, user_id)
    if item is None:
        raise HTTPException(status_code=404)
    return item

@<entity>_router.put("/<entities>/{uuid}", response_model=<Entity>Out)
async def upsert_<entity>(
    uuid: str,
    dto: <Entity>Create,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if dto.uuid != uuid:
        raise HTTPException(status_code=400, detail="uuid mismatch")
    return await <entity>_crud.upsert_<entity>(db, uuid, dto, user_id)

@<entity>_router.put("/<entities>", response_model=list[<Entity>Out])
async def upsert_many_<entities>(
    items: list[<Entity>Create],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    return [await <entity>_crud.upsert_<entity>(db, i.uuid, i, user_id) for i in items]

@<entity>_router.delete("/<entities>/{uuid}")
async def delete_<entity>(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    ok = await <entity>_crud.delete_<entity>(db, uuid, user_id)
    if not ok:
        raise HTTPException(status_code=404)
    return {"ok": True}
```

### 5. Trigger SQL — `serveur/app/db_triggers/<entity_table>_trigger.sql`

Format fragment qui sera substitué dans `base_function.sql` :

```sql
IF TG_TABLE_NAME = '<entity_table>' THEN
  payload := jsonb_build_object(
    'event', CASE TG_OP WHEN 'DELETE' THEN 'delete' ELSE 'upsert' END,
    'table', '<entity_table>',
    'userId', target_user_id,
    'clientId', current_setting('app.client_id', true),
    'data', jsonb_build_object(
      'uuid', rec.uuid,
      'userId', rec.user_id,
      -- ... champs métier ...
      'synced', rec.synced,
      'pendingDeletion', rec.pending_deletion,
      'updatedAt', iso_utc(rec.updated_at)
    )
  );
END IF;
```

Notes :
- `iso_utc()` pour toutes les dates → format wire canonique projet.
- `target_user_id` est résolu en amont par `base_function.sql` via `get_user_id_for(...)` ou directement `rec.user_id` si la table a la colonne.

### 6. Étendre `user_id_helper.sql` si user-scoped indirect

Si l'entité est user-scoped via une parent (ex. `actual_workout_set` → `actual_workout` → user), ajouter un case dans `serveur/app/db_triggers/user_id_helper.sql`. Si l'entité a directement `user_id`, **rien à faire** (le helper est inutile).

### 7. Inscriptions dans les `__init__.py`

- `serveur/app/models/__init__.py` → `from .<entity> import <Entity>`
- `serveur/app/schemas/__init__.py` → `from .<entity>_schema import <Entity>Base, <Entity>Create, <Entity>Out`
- `serveur/app/crud/__init__.py` → `from . import <entity>_crud`
- `serveur/app/routers/__init__.py` → `from .<entity>_router import <entity>_router`
- `serveur/app/main.py` → ajouter `<entity>_router` dans le tuple `ROUTERS`
- `serveur/app/triggers_loader.py` → ajouter `"<entity_table>_trigger.sql"` à `PER_TABLE_FRAGMENTS`

---

## Côté Android (Kotlin)

### 8. Model Room → DAO → Api Retrofit → Syncable → SyncHandler → SyncManager

Chaque sous-pas :

**8.1 — Model Room** : `data/model/<Entity>.kt`
```kotlin
@Entity(tableName = "<entity_table>", indices = [Index("uuid", unique = true)])
data class <Entity>(
    @PrimaryKey @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "user_id") val userId: Int,
    // ... champs métier ...
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pendingDeletion: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = getNowISO8601(),
)
```

**8.2 — DAO Style A canonique** : `data/local/<Entity>Dao.kt`
- Wrappers publics avec body qui force `synced=false + updatedAt = getNowISO8601()`
- Délégation à `*Internal` annotés `@Insert`/`@Update`
- `*FromServer` qui délègue à `*Internal` (préserve payload synced du serveur)
- Méthodes obligatoires : `getAllOnce`, `observeAll`, `getByUUID`, `insert`, `insertAll`, `update`, `delete`, `markAsSynced`, `markAsUnsynced`, `markAsPendingDeletion`, `getPendingDeletions`, `getAllUnsynced`, `hasUnsynced`, `clearAll`, `insertFromServer`, `insertAllFromServer`, `updateFromServer`

**8.3 — Api Retrofit** : `network/<Entity>Api.kt`
```kotlin
interface <Entity>Api {
    @GET("<entities>") suspend fun getAll(): List<<Entity>>
    @GET("<entities>/{uuid}") suspend fun getByUUID(@Path("uuid") uuid: String): <Entity>
    @PUT("<entities>/{uuid}") suspend fun upsert(@Path("uuid") uuid: String, @Body item: <Entity>): <Entity>
    @PUT("<entities>") suspend fun upsertAll(@Body items: List<<Entity>>): List<<Entity>>
    @DELETE("<entities>/{uuid}") suspend fun delete(@Path("uuid") uuid: String)
}
```

**8.4 — Syncable** : `sync/syncables/<Entity>Syncable.kt`
Squelette canonique cf. les 21 syncables existants (ex. [`MuscleGoalSyncable.kt`](../appli-android/app/src/main/java/com/example/sportapp/sync/syncables/MuscleGoalSyncable.kt)).

**8.5 — SyncHandler** : `data/remote/<Entity>SyncHandler.kt`
Implémente `WebSocketHandler<<Entity>>` : parse le JSON event, upsert/delete via DAO `*FromServer`. Cf. [`MuscleGoalSyncHandler.kt`](../appli-android/app/src/main/java/com/example/sportapp/data/remote/MuscleGoalSyncHandler.kt).

**8.6 — SyncRegistry** : ajouter dans `sync/SyncRegistry.kt` _(post T4.2, 2026-05-07)_
- Injecter le DAO + le `<Entity>Syncable` via Hilt dans le constructeur du SyncRegistry
- Ajouter une entrée à la liste ordonnée **FK-aware** : parents avant enfants (sinon crash FK au push)
- C'est tout — `SyncEngine` itère la registry pour `pushAll`, `mergeAllFromServer`, etc.
- **Plus de fonction `sync<Entity>s()` dans SyncManager** (supprimé en T4.2, -307 lignes). Le ViewModel appelle directement `syncEngine.pushEntityClass(<Entity>::class)` après une mutation locale.
- **Plus de `safeSync*WithSnackbar`** (supprimé en B2, 2026-05-07) — le snackbar global vit désormais dans `SyncManager.syncAllToServer()` (1 message start / 1 message end).

### 9. Migration Room

- Bumper `DATABASE_VERSION` dans `data/local/AppDatabase.kt`
- Ajouter le DAO dans la classe `AppDatabase` : `abstract fun <entity>Dao(): <Entity>Dao`
- Ajouter l'entité dans le bloc `@Database(entities = [..., <Entity>::class, ...])`
- Écrire `MIGRATION_<old>_<new>` dans `data/local/migrations/Migrations.kt` (CREATE TABLE + INDEX). Cf. exemples existants ; attention aux contraintes SQLite (DROP COLUMN exige SQLite ≥3.35 / Android 14+ — politique projet à valider).
- Coté Hilt : `data/local/AppModule.kt` → `@Provides fun provide<Entity>Dao(db: AppDatabase) = db.<entity>Dao()`
- Coté Retrofit : `network/RetrofitInstance.kt` → `val <entity>Api: <Entity>Api by lazy { buildApi(<Entity>Api::class.java) }`
- Coté WebSocket : `data/remote/WebSocketManager.kt` → ajouter une branche dans le `when` de routing par `table`

### 10. `seed_database.py`

Ajouter dans `serveur/app/seed_database.py` une fonction `seed_<entities>(db, user_id)` appelée depuis `seed_all`. Respecter les nouveaux defaults / nullable du modèle (cf. [CLAUDE.md §13 politique cohérence seed/fill](../CLAUDE.md)).

### 11. Diagrams (politique 14 — OBLIGATOIRE même commit)

- `serveur/app/diagram.dbml` → ajouter le bloc `Table <entity_table>` + les `Ref:` FK
- `serveur/app/diagram.dbdiagram` → ajouter l'entrée positions + les `referencePaths`

**Politique 14** (CLAUDE.md) : DBML doit être mis à jour **dans le même commit** que les modèles SQLAlchemy. Pas de "je le ferai plus tard" — c'est le visuel de référence du projet, il doit toujours refléter l'état effectif.

---

## Vérification finale

1. `python -c "from app.main import app; print('OK')"` (smoke import serveur)
2. `psql ... -c "\dt+"` puis vérifier que la table existe et a le trigger : `psql ... -c "\dft+ trg_<entity_table>_notify"`
3. `appli-android/gradlew :app:compileDebugKotlin` (smoke build Android)
4. Vérifier `<N>.json` schema Room généré dans `appli-android/app/schemas/<package>/`
5. Test E2E manuel : login → PUT new entity → DELETE → vérifier 200/204 + push WS reçu

## Cross-refs squelette canonique

- [`docs/SERVEUR.md §2B-1`](SERVEUR.md) — squelette CRUD/router/schema détaillé
- [`docs/APPLI_ANDROID.md §3D §2`](APPLI_ANDROID.md) — squelette DAO Style A détaillé
- [CLAUDE.md §9 politique uniformité](../CLAUDE.md)
