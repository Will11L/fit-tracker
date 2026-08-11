# SERVEUR — Analyse détaillée du backend FastAPI

> ⚠️ **DOC LARGEMENT FIGÉ AU 2026-05-06.** Audit ~30 findings dont 15 critiques **tous résolus depuis** (vagues V1-V8 + F1-F11 — cf. historique [CLAUDE.md](../CLAUDE.md)). Comptages périmés (« 27 CRUDs » → réel 21 après F8, « 22 routers » reste correct). Sections **toujours vivantes et utilisées comme référence** : **§2B-1 squelette canonique CRUD / router / schema** (politique 9 du projet) et **§3 archétypes Type A / B / C**. Pour l'état courant : code `serveur/app/`, Swagger [`/secure-docs`](https://<pi-fqdn>/secure-docs), [HOW_TO_ADD_ENTITY.md](HOW_TO_ADD_ENTITY.md), [DATABASES.md](DATABASES.md), [INTEGRATION.md](INTEGRATION.md).

> Document construit en 3 sous-étapes (2A → 2B → 2C). Référence cousine : [PROJECT_MAP.md](PROJECT_MAP.md) (vue haute), [DATABASES.md](DATABASES.md) (schéma DB), [INTEGRATION.md](INTEGRATION.md) (mapping serveur ↔ Android).

## Sommaire

- **[2A. Infrastructure](#2a--infrastructure)** ✅ ce document
  - [1. Cycle de vie d'une requête HTTP](#1--cycle-de-vie-dune-requête-http)
  - [2. `main.py` — point d'entrée](#2--mainpy--point-dentrée)
  - [3. `settings.py` — config](#3--settingspy--config)
  - [4. `database.py` — engine + session + event](#4--databasepy--engine--session--event)
  - [5. `context.py` — ContextVar partagée](#5--contextpy--contextvar-partagée)
  - [6. `middlewares/client_id.py` — extraction du header](#6--middlewaresclient_idpy--extraction-du-header)
  - [7. `auth.py` — JWT et OAuth2](#7--authpy--jwt-et-oauth2)
  - [8. `dependencies.py` — `get_current_user_id`](#8--dependenciespy--get_current_user_id)
  - [9. `ws_hub.py` — registre des connexions WebSocket](#9--ws_hubpy--registre-des-connexions-websocket)
  - [10. `pg_listener.py` — pont PG NOTIFY → WS](#10--pg_listenerpy--pont-pg-notify--ws)
  - [11. `routers/auth_router.py` — `/token` + `/me`](#11--routersauth_routerpy--token--me)
  - [12. `routers/ws_router.py` — `/ws`](#12--routersws_routerpy--ws)
  - [13. Findings de la sous-étape 2A](#13--findings-de-la-sous-étape-2a)
- **[2B. Routers + CRUD + schemas par entité](#2b--routers--crud--schemas-par-entité)**
  - [2B-1. Squelette canonique (cette section)](#2b-1--squelette-canonique) ✅
  - [2B-2. Audit module par module](#2b-2--audit-de-conformité-des-27-modules) ✅
- **[2C. Scripts DB + tests + triggers SQL](#2c--scripts-db--alembic--triggers-sql--tests)** ✅

---

# 2A — Infrastructure

## 1. Cycle de vie d'une requête HTTP

Avant de plonger dans les fichiers, voici ce qui se passe quand l'app Android envoie une requête comme `POST /actual-workouts` :

```
Client Android
    │ Authorization: Bearer <JWT>
    │ X-Client-Id: <UUID> (ajouté par OkHttp Interceptor pour POST/PUT/PATCH/DELETE)
    │ Content-Type: application/json
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ Starlette / FastAPI                                              │
│  1. CORSMiddleware (filtre Origin, OPTIONS preflight)            │
│  2. ClientIdMiddleware                                            │
│       └─ pose request.headers["x-client-id"] dans                │
│          client_id_ctx (ContextVar)                              │
│  3. Routing → router.actual_workout_router                       │
│  4. Dependencies :                                               │
│     - Depends(get_session) → AsyncSession                        │
│     - Depends(oauth2_scheme) → token brut                        │
│     - Depends(get_current_user) → username (str)                 │
│     - Depends(get_current_user_id) → user.id (int)  ⚠ +1 SELECT  │
│  5. Handler appelle un module crud/                              │
│  6. SQLAlchemy ouvre une transaction                             │
│       └─ event after_begin :                                     │
│          SELECT set_config('app.client_id', <UUID>, true)        │
│  7. INSERT/UPDATE/DELETE                                         │
│       └─ trigger SQL AFTER INSERT/UPDATE/DELETE :                │
│          notify_row_change() → pg_notify('db_events', payload)   │
│          (payload contient `originClientId = current_setting(   │
│           'app.client_id')`)                                     │
│  8. Commit auto (sortie de session)                              │
│  9. Retour JSON au client                                        │
└─────────────────────────────────────────────────────────────────┘

   Postgres NOTIFY 'db_events'
            │
            ▼
┌─────────────────────────────────────────────────────────────────┐
│ pg_listener (asyncio task lancée par lifespan FastAPI)           │
│  - asyncpg LISTEN db_events                                      │
│  - reçoit le payload JSON                                        │
│  - ws_hub.broadcast(payload, exclude_client_id=originClientId)   │
└─────────────────────────────────────────────────────────────────┘
            │
            ▼
   Tous les WebSockets ouverts (autres téléphones, autre PC du
   même utilisateur, etc.) sauf l'expéditeur → reçoivent une
   notification → mettent à jour leur Room locale → recompose UI.
```

**Astuce-clé du système** : le couple `X-Client-Id (HTTP) → app.client_id (Postgres setting) → originClientId (NOTIFY payload)` permet au serveur d'**exclure le client à l'origine du change** lors du broadcast WS. Sinon le client qui vient d'écrire recevrait son propre changement en retour et risquerait une boucle.

---

## 2. `main.py` — point d'entrée

[serveur/app/main.py](../serveur/app/main.py)

### Rôle
- Construit l'instance FastAPI
- Active CORS + `ClientIdMiddleware`
- Importe et monte tous les routers
- Définit le `lifespan` qui lance et arrête `pg_listen_task`
- Fournit deux pages HTML inline pour la doc auth-protégée (`/secure-docs`, `/token-helper`)
- Définit un helper `resolve_user_id_from_token` (jamais utilisé dans ce fichier)

### Comportement détaillé

| Lignes | Élément | Rôle |
|---|---|---|
| 1-44 | Imports | Modèle `User`, `pg_listen_task`, `ClientIdMiddleware`, et **24 routers** (1 par entité + auth + ws) |
| 46-54 | `lifespan` | À l'ouverture : lance `pg_listen_task()` en task asyncio. À la fermeture : annule la task proprement |
| 56 | `app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None)` | ⚠ `/docs` et `/redoc` **désactivés** — la doc est servie via `/secure-docs` (custom Swagger UI) qui requiert un token JWT |
| 58-70 | CORS | Allowlist `localhost:4200` + `127.0.0.1:4200` (legacy Angular). Headers : `Authorization`, `Content-Type`, `X-Client-Id`. Méthodes : GET/POST/PUT/PATCH/DELETE/OPTIONS. `allow_credentials=False` |
| 71 | `app.add_middleware(ClientIdMiddleware)` | Ajoute le middleware qui pose `client_id_ctx` |
| 75-102 | Liste `ROUTERS` + boucle `include_router` | Monte les 24 routers à la racine — **aucun préfixe, aucun tag** : tous les paths sont déclarés dans les routers eux-mêmes |
| 104-114 | `resolve_user_id_from_token` | Helper async qui prend un payload JWT décodé. Si `user_id` dedans → renvoie. Sinon SELECT par `username`. **Jamais appelé dans ce fichier** : code mort suspect ou helper futur |
| 119-162 | `/secure-docs` (GET) | Renvoie ~50 lignes de HTML inline qui chargent Swagger UI from CDN, écoutent un message JS (`postMessage`) avec un token, l'injectent en `Authorization: Bearer ...` dans toutes les requêtes Swagger. `include_in_schema=False`. **`SECRET_KEY = "MYSECRET123"`** déclaré ligne 121 mais **jamais validé côté serveur** |
| 166-223 | `/token-helper` (GET) | Page HTML/JS qui POST `/token`, ouvre `/secure-docs?key=MYSECRET123` dans un nouvel onglet, et passe le token via `postMessage`. Bricolage qui marche, mais l'auth est *visuellement* protégée et pas *réellement* (cf. findings) |

### Findings spécifiques `main.py`

- 🔴 **`SECRET_KEY = "MYSECRET123"`** ([main.py:121](../serveur/app/main.py#L121)) : valeur hardcodée jamais validée. Le query param `?key=...` envoyé par `/token-helper` est ignoré → faux mécanisme de sécurité. La vraie protection vient du JWT requis pour les endpoints, pas de cette clé.
- 🟡 **`resolve_user_id_from_token`** ([main.py:104-114](../serveur/app/main.py#L104)) : helper défini mais jamais utilisé dans ce fichier. Soit le déplacer dans `dependencies.py` ou `auth.py`, soit le supprimer.
- 🟡 **CORS pour Angular** ([main.py:60-62](../serveur/app/main.py#L60)) : `localhost:4200` est l'origine par défaut Angular. Si plus aucun client web Angular n'est prévu, retirer.
- 🟡 **HTML inline lourd** ([main.py:119-223](../serveur/app/main.py#L119)) : ~100 lignes de HTML/JS dans un fichier Python d'entrée. À déporter dans `app/static/secure_docs.html` + `app/static/token_helper.html`, servis via `StaticFiles` ou simplement lus à la volée.
- 🟡 **Pas de tag/prefix par router** ([main.py:101-102](../serveur/app/main.py#L101)) : tous les routers sont montés sans `prefix=` ni `tags=`. Conséquence : Swagger affiche tout en vrac sans regroupement, et il n'y a aucun namespace. À ajouter : `app.include_router(actual_workout_router, prefix="/actual-workouts", tags=["actual_workout"])` (mais ça suppose que le router lui-même ne re-déclare pas le prefix — à vérifier en 2B).
- ⚪ **Logique ROUTERS dupliquée** : les imports listent les routers (l. 19-44) et la liste ROUTERS les redéclare (l. 75-100). Possible de simplifier en `from app.routers import *` + fonction `register_all(app)` dans `routers/__init__.py`.

---

## 3. `settings.py` — config

[serveur/app/settings.py](../serveur/app/settings.py)

### Rôle
Charge les variables d'environnement via `pydantic-settings` (BaseSettings). Source : `.env` à côté de l'exécution.

### Structure

```python
class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    DATABASE_URL: str = "postgresql+asyncpg://fittracker:change-me@localhost:5432/fittracker"
    JWT_SECRET_KEY: str = "change-me"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    JWT_ISS: str = "fittracker-api"
    JWT_AUD: str = "fittracker-clients"

settings = Settings()
```

### Comportement
- Si un `.env` existe au CWD au moment de l'instanciation → ses valeurs surchargent les défauts.
- Sinon, les défauts s'appliquent — c'est le cas sur la Pi (qui tourne sans `.env`).
- `model_config` a été ajouté lors du setup PC ([DEV_GUIDE.md §10](../DEV_GUIDE.md)) : sans ça, pydantic-settings v2 ne lit pas `.env`.

### Findings

- 🔴 **Défauts dangereux pour la prod** : `JWT_SECRET_KEY = "change-me"`, mot de passe Postgres `change-me`. Si un environnement (= la Pi) tourne sans `.env`, il hérite de ces secrets. **Solution recommandée** : ne pas mettre de défaut sur `JWT_SECRET_KEY` (le rendre `str` sans valeur par défaut), ce qui force l'app à crasher au boot si non configuré — fail-fast plutôt que silently-insecure.
- 🟡 **`JWT_ISS` et `JWT_AUD` jamais utilisés** ([auth.py](../serveur/app/auth.py)) : déclarés ici mais ni mis dans le payload JWT à la création, ni vérifiés au décodage. Cf. section 7.

---

## 4. `database.py` — engine + session + event

[serveur/app/database.py](../serveur/app/database.py)

### Rôle
- Crée l'engine asynchrone SQLAlchemy
- Définit `AsyncSessionLocal` et `Base`
- Fournit `get_session()` (générateur async pour `Depends`)
- Pose un **event hook `after_begin`** qui injecte `app.client_id` au début de chaque transaction

### Code annoté

```python
engine = create_async_engine(settings.DATABASE_URL, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(bind=engine, expire_on_commit=False, class_=AsyncSession)
Base = declarative_base()

async def get_session() -> AsyncSession:
    async with AsyncSessionLocal() as session:
        yield session

@event.listens_for(Session, "after_begin", propagate=True)
def _set_client_id_on_begin(session, transaction, connection):
    cid = client_id_ctx.get()
    if cid:
        connection.execute(
            text("select set_config('app.client_id', :cid, true)"),
            {"cid": cid},
        )
```

### Notes

- **`pool_pre_ping=True`** : avant chaque check-out, lance un `SELECT 1` pour vérifier que la connexion est encore vivante. Important sur la Pi où la connexion peut être tuée par un sleep long ou un restart Postgres.
- **`expire_on_commit=False`** : après un commit, les attributs des objets restent accessibles sans re-fetch. Convention courante en async.
- **`event.listens_for(Session, "after_begin")`** : se déclenche au début de chaque transaction (donc à chaque `async with session.begin()` ou implicitement). Lit `client_id_ctx` (ContextVar posé par le middleware), et si présent, l'injecte dans le setting Postgres `app.client_id` (visible par tous les triggers SQL via `current_setting('app.client_id', true)`).
- **`set_config(..., true)`** : le 3e paramètre `true` rend le setting *transactional* — il revient à sa valeur précédente au commit/rollback. Garantit qu'il ne fuite pas entre transactions sur une même connexion poolée.

### Findings

- 🟡 **Imports non utilisés** ([database.py:3](../serveur/app/database.py#L3)) : `from sqlalchemy.orm import declarative_base, Session, sessionmaker` — `sessionmaker` (sync) est importé mais pas utilisé. À retirer.
- ⚪ **Le `Session` listener est posé sur la classe `Session` de `sqlalchemy.orm`** (sync), pas sur `AsyncSession`. Ça fonctionne car `propagate=True` propage aux sous-classes et `AsyncSession` use une `Session` interne, mais c'est subtil. Documenter le pourquoi en commentaire dans le code, ou changer pour cibler explicitement `AsyncSession` si possible.

---

## 5. `context.py` — ContextVar partagée

[serveur/app/context.py](../serveur/app/context.py)

```python
from contextvars import ContextVar
client_id_ctx: ContextVar[str | None] = ContextVar("client_id_ctx", default=None)
```

### Rôle
Une seule ContextVar partagée entre `middlewares/client_id.py` (qui set) et `database.py` (qui get). C'est le mécanisme propre pour passer une donnée *par requête* à travers la stack async (équivalent du `threading.local` mais async-safe).

Pas de finding particulier — fichier minimal et correct.

---

## 6. `middlewares/client_id.py` — extraction du header

[serveur/app/middlewares/client_id.py](../serveur/app/middlewares/client_id.py)

```python
class ClientIdMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        token = client_id_ctx.set(request.headers.get("x-client-id"))
        try:
            return await call_next(request)
        finally:
            client_id_ctx.reset(token)
```

### Rôle
À chaque requête HTTP, lit le header `X-Client-Id` (case-insensitive Starlette) et le pose dans `client_id_ctx`. Reset propre en sortie (le pattern `set()` retourne un token permettant de revenir à la valeur précédente).

### Notes
- **Si le header est absent → `None`** : c'est le cas pour GET (l'OkHttp interceptor Android n'ajoute le header que pour POST/PUT/PATCH/DELETE — cf. [RetrofitInstance.kt:73-87](../appli-android/app/src/main/java/com/example/sportapp/network/RetrofitInstance.kt#L73)). Conséquence : un GET ne pose pas `app.client_id`, donc les triggers ne pourront pas exclure l'origine — ce qui est OK puisqu'un GET ne déclenche pas de trigger d'écriture.
- **WebSocket non concerné** : Starlette `BaseHTTPMiddleware` ne s'applique qu'aux requêtes HTTP. Le WS gère son `client_id` via le query param `?client_id=...` (cf. section 12).

Pas de finding particulier.

---

## 7. `auth.py` — JWT et OAuth2

[serveur/app/auth.py](../serveur/app/auth.py)

### Rôle
- Définit le schéma OAuth2 (`OAuth2PasswordBearer(tokenUrl="token")`)
- Fournit `create_access_token(data, expires_delta)` pour générer un JWT signé HS256
- Fournit `get_current_user(token)` pour les `Depends` : décode le token, retourne le **username** (str)
- Fournit `verify_token(token)` pour le décodage manuel (utilisé par `ws_router`)

### Code annoté

```python
SECRET_KEY = settings.JWT_SECRET_KEY            # ← settings (.env ou défaut "change-me")
ALGORITHM = settings.JWT_ALGORITHM              # HS256
ACCESS_TOKEN_EXPIRE_MINUTES = settings.ACCESS_TOKEN_EXPIRE_MINUTES  # 30

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")

def create_access_token(data, expires_delta=None):
    expire = datetime.now(timezone.utc) + (expires_delta or timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES))
    to_encode = {**data, "exp": expire}
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def get_current_user(token: str = Depends(oauth2_scheme)):
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username = payload.get("sub")
        if username is None: raise credentials_exception
        return username                          # ← juste un str !
    except JWTError:
        raise credentials_exception

def verify_token(token: str) -> Dict:
    try:
        return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
    except JWTError as e:
        raise HTTPException(status_code=401, detail="Token invalid or expired")
```

### Findings

- 🟡 **Pas de validation `audience` ni `issuer`** ([auth.py:39](../serveur/app/auth.py#L39), [auth.py:54](../serveur/app/auth.py#L54)) : `jwt.decode(...)` est appelé sans `audience=settings.JWT_AUD` ni `issuer=settings.JWT_ISS`, donc si un attaquant arrivait à signer un JWT avec la même clé HS256 mais un `aud` ou `iss` différent, il serait accepté. **Action** : ajouter `audience=settings.JWT_AUD, issuer=settings.JWT_ISS` aux appels, et passer ces champs à `create_access_token` côté `auth_router`. (Avec HS256 et un secret bien gardé l'impact est limité, mais c'est gratuit à corriger.)
- 🟡 **`get_current_user` retourne un str (username), pas un User** ([auth.py:43](../serveur/app/auth.py#L43)) : tout endpoint qui veut l'`user_id` doit chaîner `Depends(get_current_user_id)` qui re-query la DB. → **+1 SELECT par requête authentifiée**. Solution : retourner le payload complet (qui contient déjà `user_id` depuis [auth_router.py:39](../serveur/app/routers/auth_router.py#L39)), ou retourner directement l'objet User.
- ⚪ **`tokenUrl="token"` (relatif sans `/`)** : marche, mais Swagger UI peut être confus selon le base path. Habituel : `tokenUrl="/token"`.

---

## 8. `dependencies.py` — `get_current_user_id`

[serveur/app/dependencies.py](../serveur/app/dependencies.py)

```python
async def get_current_user_id(
    db: AsyncSession = Depends(get_session),
    current_user: str = Depends(get_current_user),
) -> int:
    res = await db.execute(select(User).where(User.username == current_user))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="Utilisateur non trouvé")
    return user.id
```

### Rôle
Chaîne `get_current_user` (qui décode le JWT) → query DB par username → retourne l'`user.id`. Utilisé par tous les routers métier qui veulent filtrer par utilisateur (cf. par ex. [muscle_weekly_summary_router.py:16](../serveur/app/routers/muscle_weekly_summary_router.py#L16)).

### Findings

- 🟡 **Coût : 1 SELECT supplémentaire par requête authentifiée** : alors que le JWT contient déjà `user_id` au payload (créé en [auth_router.py:39](../serveur/app/routers/auth_router.py#L39)). On pourrait court-circuiter la DB en faisant `int(payload["user_id"])`. Patch minimal : modifier `get_current_user_id` pour décoder le token et lire `user_id` directement, fallback DB seulement si absent (compat avec vieux tokens).
- ⚪ **Erreur 404 vs 401** : si l'utilisateur n'existe plus dans la DB mais que son token est encore valide, on retourne 404 "Utilisateur non trouvé". Plus correct serait 401 (token invalide / utilisateur supprimé = pas authentifié). Cosmétique.

---

## 9. `ws_hub.py` — registre des connexions WebSocket

[serveur/app/ws_hub.py](../serveur/app/ws_hub.py)

### Rôle
Un singleton `ws_hub` qui maintient l'état des WebSockets ouvertes :
- `_clients: Set[WebSocket]` — sockets actives
- `_map_user: Dict[WebSocket, int]` — qui est qui (user_id)
- `_map_client: Dict[WebSocket, str]` — UUID du client (téléphone, browser…)
- `_lock: asyncio.Lock` — pour les modifications atomiques

### API

```python
async def register(ws, user_id, client_id):     # accept() + ajoute aux maps
async def unregister(ws):                        # retire des maps
async def broadcast(message: dict, exclude_client_id: Optional[str] = None):
    # filtre par message["userId"] (si présent) puis exclude_client_id
    # retire les sockets mortes en cours de route
```

### Comportement de `broadcast`

```python
target_uid = message.get("userId")
for ws in list(self._clients):                   # snapshot (sans lock)
    if target_uid is not None:
        if self._map_user.get(ws) != target_uid: continue   # filtre par user
    if exclude_client_id is not None:
        if self._map_client.get(ws) == exclude_client_id: continue  # exclude origin
    await ws.send_json(message)
```

### Findings

- 🟠 **Si `message["userId"]` absent → broadcast à TOUS** : `if target_uid is not None: ...` veut dire qu'un payload sans `userId` part chez tout le monde. À vérifier en 2C (analyse des triggers SQL) que **tous** les triggers incluent toujours `userId`. Si une table met à jour des données partagées (ex: `Equipment` global) sans `userId`, on aurait une fuite cross-utilisateurs.
- 🟡 **`broadcast` itère sans lock** ([ws_hub.py:36](../serveur/app/ws_hub.py#L36)) : `for ws in list(self._clients):` snapshot, mais une `unregister` concurrente peut modifier les maps pendant le `send_json`. Le `_lock` n'est utilisé qu'à l'ajout/retrait. En pratique pas de bug observable car tout est mono-thread asyncio, mais pas robuste à un futur changement.
- ⚪ **`print` au lieu de `logger`** : tout le module utilise `print(...)` pour le tracing. À remplacer par `logging` (configurable, désactivable, niveau-aware).

---

## 10. `pg_listener.py` — pont PG NOTIFY → WS

[serveur/app/pg_listener.py](../serveur/app/pg_listener.py)

### Rôle
Tâche asyncio démarrée par `lifespan` ([main.py:48](../serveur/app/main.py#L48)). Elle :
1. Ouvre une connexion asyncpg dédiée (DSN dérivé de `DATABASE_URL` en retirant le suffixe `+asyncpg`)
2. `LISTEN db_events`
3. À chaque NOTIFY reçue → push dans une queue async → log → `ws_hub.broadcast(payload, exclude_client_id=originClientId)`

### Boucle robuste

```python
while True:
    try:
        conn = await asyncpg.connect(dsn)
        try:
            queue = asyncio.Queue()
            def _on_notify(_conn, _pid, _channel, payload): queue.put_nowait(payload)
            await conn.add_listener("db_events", _on_notify)

            while True:
                payload = await queue.get()
                data = json.loads(payload)
                origin = data.get("originClientId")
                await ws_hub.broadcast(data, exclude_client_id=origin)
        finally:
            await conn.close()
    except asyncio.CancelledError:
        break
    except Exception:
        await asyncio.sleep(2)  # backoff puis retry
```

### Notes
- Le `try/finally` garantit la fermeture de la connexion asyncpg même si l'inner loop crashe.
- L'`except Exception: sleep 2` rend la tâche **immortelle** sauf si annulée explicitement par lifespan : utile si Postgres redémarre.
- La queue intermédiaire évite de bloquer le callback `_on_notify` (asyncpg n'aime pas les callbacks lents).

### Findings

- 🟡 **Pas de cap sur la queue** : `asyncio.Queue()` sans `maxsize` → si les WS sont saturés ou si la diffusion lente, la queue peut grossir indéfiniment. Pour ce niveau de trafic c'est probablement ok, mais à savoir.
- 🟡 **Catch `Exception` muet** : tout est swallowé puis `sleep 2`. Si Postgres a un bug recurrent, on n'en saura rien sans aller dans les logs. À remplacer par `logger.exception(...)`.
- ⚪ **Construction du DSN** : `settings.DATABASE_URL.replace("+asyncpg", "")` — fragile si jamais on change de driver (ex: `+psycopg`). Mieux : un settings dédié `LISTEN_DSN` ou un parsing propre via `sqlalchemy.engine.url.make_url(...)`.

---

## 11. `routers/auth_router.py` — `/token` + `/me`

[serveur/app/routers/auth_router.py](../serveur/app/routers/auth_router.py)

### Routes exposées

| Méthode | Path | Body / Auth | Réponse |
|---|---|---|---|
| POST | `/token` | `OAuth2PasswordRequestForm` (form-urlencoded `username` + `password`) | `{ "access_token": "<JWT>", "token_type": "bearer" }` |
| GET | `/me` | `Authorization: Bearer <JWT>` | `{ "id": int, "username": str, "email": str }` |

### `POST /token` — détail

```python
res = await db.execute(select(User).where(User.username == form_data.username))
user = res.scalar_one_or_none()

if not user or not verify_password(form_data.password, user.hashed_password):
    raise HTTPException(401, "Incorrect username or password")

access_token = create_access_token(
    data={"sub": user.username, "user_id": user.id},
    expires_delta=timedelta(minutes=30),
)
return {"access_token": access_token, "token_type": "bearer"}
```

- **Hash de mot de passe** : bcrypt via `passlib.context.CryptContext(schemes=["bcrypt"])`. À noter que `requirements.txt` épingle `bcrypt==3.2.2` (vieux) — passlib peut warner sur les versions récentes (>=4) mais 3.2.2 reste compatible.
- **Payload JWT** : `{"sub": username, "user_id": id, "exp": <30 min>}`. **Pas de `aud`, pas de `iss`** alors que `settings.py` les déclare.
- **Pas de rate limiting** : un attaquant peut bruteforce `/token` à volonté.

### `GET /me` — détail

```python
payload = verify_token(token)
username = payload.get("sub")
user_id = payload.get("user_id")
return {
    "id": user_id,
    "username": username,
    "email": f"{username}@sportapp.com",   # ⚠ email synthétique
}
```

- **Aucune query DB** : tout est lu du JWT.
- **`email` est fabriqué** : `f"{username}@sportapp.com"`. Si le model `User` a réellement un champ `email`, il est ignoré → l'app voit un email faux. À auditer en 2B / 4 (modèle User).

### Findings

- 🟠 **Email synthétique dans `/me`** ([auth_router.py:57](../serveur/app/routers/auth_router.py#L57)) : si l'app affiche cet email à l'utilisateur (ex: ProfileScreen), il est trompeur. À remplacer par la vraie valeur lue en DB, ou retirer le champ.
- 🟡 **`/me` non listé dans `routes.json`** : confirme que routes.json est obsolète (cf. TODO_FIXES).
- 🟡 **Pas de rate limit sur `/token`** : 0 protection contre bruteforce. Pas critique en local mais l'API est exposée publiquement via `<public-dns>`. Un middleware `slowapi` ou similaire ferait le job.
- 🟡 **`OAuth2PasswordBearer` redéclaré localement** ([auth_router.py:14](../serveur/app/routers/auth_router.py#L14)) : alors qu'`auth.py:16` le déclare déjà. Doublon à factoriser.
- 🟡 **Pas de validation iss/aud à `/token`** : confirme TODO 🟡 (cf. [auth.py findings](#findings-2)).

---

## 12. `routers/ws_router.py` — `/ws`

[serveur/app/routers/ws_router.py](../serveur/app/routers/ws_router.py)

### Endpoint
```
GET /ws?access_token=<JWT>&client=<str>&v=<str>&client_id=<UUID>
  (upgrade vers WebSocket)
```

### Comportement détaillé

```python
@ws_router.websocket("/ws")
async def ws_endpoint(websocket, access_token, client="web", v="1", client_id=None):
    # 1. Décoder le JWT
    payload = verify_token(access_token)        # raises 401
    user_id = int(payload.get("user_id"))       # ⚠ KeyError si vieux token sans user_id

    # 2. Générer un client_id si absent
    if not client_id: client_id = str(uuid.uuid4())

    # 3. Enregistrer
    await ws_hub.register(websocket, user_id, client_id)
    await websocket.send_json({"type": "client_id", "clientId": client_id})

    # 4. Boucle keep-alive : ping/pong
    try:
        while True:
            msg = await websocket.receive_text()
            if msg.strip().lower() in ("ping", '{"type":"ping"}'):
                await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        await ws_hub.unregister(websocket)
```

### Notes
- **Auth via query param** (`?access_token=...`), pas via header `Authorization` — c'est le pattern usuel pour les WS car les browsers ne permettent pas de poser des headers custom à l'upgrade WebSocket. Inconvénient : le token apparaît dans les logs de proxy/Caddy en clair. À documenter.
- **Le `client_id` du WS est différent du `X-Client-Id` HTTP** : ils peuvent coïncider (et le devraient idéalement) pour que `pg_listener.broadcast(... exclude_client_id=...)` exclue effectivement le bon client. Côté Android, c'est `ClientIdProvider` qui maintient une seule UUID partagée (à confirmer en 3).
- **Le serveur informe le client de son client_id** via `{"type": "client_id", "clientId": ...}` au connect. Si le client en avait fourni un, il reçoit le sien en écho ; sinon il reçoit celui généré par le serveur.

### Findings

- 🟠 **`int(payload.get("user_id"))`** ([ws_router.py:20](../serveur/app/routers/ws_router.py#L20)) : si le token n'a pas `user_id` (vieux token, ou token forgé sans ce champ), `payload.get("user_id")` renvoie `None` → `int(None)` raise `TypeError`, qui n'est pas catché → exception 500 au moment de l'upgrade. À corriger : check explicite `if "user_id" not in payload: close(1008)`.
- 🟡 **Token dans l'URL** : sécurité acceptable mais ça apparaît dans les logs Caddy/nginx. À mentionner dans la doc sécurité.
- 🟡 **Client_id côté serveur si absent** ([ws_router.py:29](../serveur/app/routers/ws_router.py#L29)) : si le client ne fournit pas `?client_id=`, le serveur en génère un. Mais alors **les requêtes HTTP du même client utilisent un autre `X-Client-Id`** (UUID local Android), donc l'`exclude_client_id` ne matchera pas correctement. À documenter : le client *doit* fournir son client_id pour le WS aussi.
- ⚪ **Param `client` et `v` inutilisés** ([ws_router.py:13-14](../serveur/app/routers/ws_router.py#L13)) : récupérés mais jamais lus. Soit les utiliser pour du logging/versioning, soit les retirer.

---

## 13. Findings de la sous-étape 2A

Récapitulatif des nouveaux items identifiés pendant 2A. Tous ont été ajoutés à [TODO_FIXES.md](TODO_FIXES.md).

### 🔴 Critique
- (déjà noté en étape 1) `SECRET_KEY = "MYSECRET123"` hardcodé dans `main.py`
- (déjà noté en étape 1) Défauts `change-me` dans `settings.py` qui s'appliquent silencieusement à la Pi

### 🟠 Important
- **`/me` retourne un email synthétique** `{username}@sportapp.com` — soit corriger pour lire la vraie valeur DB, soit retirer le champ
- **`ws_router` peut crasher 500** si le JWT n'a pas `user_id` (vieux tokens) — à blinder par un check explicite
- **`ws_hub.broadcast` broadcast à TOUS si `message["userId"]` absent** — vérifier en 2C que tous les triggers incluent `userId`

### 🟡 Important mais non critique
- **JWT : pas de validation `audience`/`issuer`** alors que les settings les déclarent (impact limité avec HS256 si secret bien gardé)
- **`get_current_user_id` fait +1 SELECT par requête authentifiée** alors que le JWT contient déjà `user_id` au payload
- **`get_current_user` retourne juste un str (username)** au lieu de l'objet User — chaque endpoint doit re-query
- **`OAuth2PasswordBearer` déclaré 2 fois** (auth.py + auth_router.py) — doublon
- **CORS allowlist `localhost:4200` (Angular)** alors que le client est Android natif — dead code probable
- **HTML inline `/secure-docs` + `/token-helper`** (~100 lignes) dans `main.py` — à déporter dans `app/static/`
- **Pas de rate limit sur `/token`** — bruteforce libre via le domaine public
- **Client_id WS doit être fourni par le client** pour que l'exclude marche bien — à documenter (ou imposer 401 si absent)
- **`pg_listener` swallow `Exception` silencieusement** — passer en `logger.exception`
- **`ws_hub` itère sans lock** — pas un bug actuel (asyncio mono-thread) mais fragile
- **Imports inutilisés dans `database.py`** (`sessionmaker`)
- **`resolve_user_id_from_token` dans `main.py`** — défini mais non utilisé, à déplacer ou supprimer

### ⚪ Cosmétique
- Tags / prefix par router à ajouter (UX Swagger)
- `print` → `logger` partout dans l'infra
- DSN du listener construit par `replace("+asyncpg", "")` — fragile

---

*Sous-étape 2A terminée. À suivre : 2B (routers + CRUD + schemas par entité).*

---

# 2B — Routers + CRUD + schémas par entité

## 2B-1 — Squelette canonique

> **But** : définir le squelette de référence pour les triplets `model + crud + router + schemas` du serveur. Politique du projet : tous les modules d'un même type doivent partager ce squelette. Les divergences ne sont acceptables QUE pour des besoins fonctionnels spécifiques (cf. §[Extensions justifiées](#extensions-justifiées-divergences-acceptables)).
>
> **Pourquoi cette spec d'abord** : les CRUDs existants ont divergé inconsistantement (signatures différentes, gestion ownership variable, ordres de params, types de retour, types d'entrée bulk). 4 bugs de sécurité ont été identifiés rien qu'à l'échantillon de 6 fichiers. Plutôt que de documenter ces 27 modules avec leurs accidents historiques, on définit la cible et chaque module sera évalué contre elle en 2B-2.

### Sommaire de la spec

- [3 archétypes d'entité](#3-archétypes-dentité)
- [Squelette CRUD](#squelette-crud)
  - [Type A — User-scoped entity](#type-a--user-scoped-entity)
  - [Type B — M2M junction](#type-b--m2m-junction)
  - [Type C — Global entity](#type-c--global-entity)
- [Squelette Schemas Pydantic](#squelette-schemas-pydantic)
- [Squelette Router FastAPI](#squelette-router-fastapi)
- [Politiques transverses](#politiques-transverses)
- [Extensions justifiées (divergences acceptables)](#extensions-justifiées-divergences-acceptables)

### 3 archétypes d'entité

> **Inventaire confirmé 2026-05-06 (vague F1d)** — audit `grep user_id` sur `app/models/`. Source de vérité de la classification A/B/C. **Étendu 2026-06-12 (Nutrition V1)** : +8 entités (5 Type A + 3 Type B).

| Archétype | FK `user_id` ? | Entités confirmées (post-V5.7) | Auth requise (read) | Auth requise (write) |
|---|---|---|---|---|
| **Type A — User-scoped** | ✅ direct, NOT NULL | actual_workout, available_equipment (depuis F8-Q2 2026-05-06), exercise, food (Nutrition V1 2026-06-12), meal (Nutrition V1), meal_preset (Nutrition V1), muscle, muscle_goal, notification, nutrition_goal (Nutrition V1), planned_workout, recipe (Nutrition V1), routine_period, routine_task, routine_task_check, superset_group, training_cycle (depuis V5.7 commit `16ede81`) | JWT + filtre `user_id` | JWT + `user_id` check (403 si pas owner) |
| **Type B — Enfant / junction (cascade)** | ❌ direct, ownership via parent | actual_workout_exercise, actual_workout_set, cycle_workout, exercise_equipment, exercise_muscle, food_portion (Nutrition V1 : FoodPortion → Food → User), meal_entry (Nutrition V1 : MealEntry → Meal → User ; FK food/recipe SET NULL informatives, snapshot D5), planned_workout_exercise, recipe_ingredient (Nutrition V1 : RecipeIngredient → Recipe → User), superset_exercise | JWT + ownership via parent | JWT + ownership via parent (`assert_user_owns_X`) |
| **Type C — Global (admin write)** | ❌ pas de notion d'ownership | equipment | JWT (tout user authentifié) | **JWT + `Depends(require_admin)`** |
| **Hors scope** | — | user (entité = user, patterns auth_router/user_router), refresh_token (auth infra V8.2) | — | — |

**Bilan** : 17 Type A + 10 Type B + 1 Type C ferme + 2 hors scope = 30 entités métier (22 + 8 Nutrition V1 2026-06-12).

> ⚠ **Politique de sécurité validée** :
> - **Aucun endpoint public** — JWT obligatoire partout, y compris pour la lecture des entités globales (Type C).
> - **Cascade ownership** — pour les entités imbriquées (ex: `ActualWorkoutSet < ActualWorkoutExercise < ActualWorkout < User`), la chaîne d'ownership doit être vérifiée. Stratégie privilégiée : dénormaliser `user_id` jusqu'à la feuille → check direct rapide. Stratégie de fallback : JOIN à travers la chaîne. Cf. §[Cascade ownership](#cascade-ownership-pour-les-entités-imbriquées).
> - **Rôle admin** — les écritures sur Type C nécessitent `is_admin: bool` à `True` sur le `User`. Lecture libre pour tout user authentifié. Implique : ajouter le champ `is_admin` au model `User` + une dépendance `require_admin`. Cf. §[Dépendance `require_admin`](#dépendance-require_admin).

> ⚠ **Hors scope** : `user_crud` lui-même est un cas particulier (l'entité IS le user), il a son propre pattern (id-based, géré par `auth_router` + `user_router`). Ne suit pas le squelette ci-dessous et c'est OK.

### Cascade ownership (pour les entités imbriquées)

Certaines entités sont en **chaîne hiérarchique** :

```
ActualWorkoutSet < ActualWorkoutExercise < ActualWorkout < User
PlannedWorkoutExercise < PlannedWorkout < User
SupersetExercise < SupersetGroup < User
RoutineTaskCheck < RoutineTask < RoutinePeriod < User
CycleWorkout < TrainingCycle (Type C, donc cascade peut être absent ici)
```

**Règle invariante** : toute opération sur une feuille doit valider que la chaîne complète appartient au user authentifié.

**Implémentation préférée — `user_id` dénormalisé sur la feuille** :
```python
# Si actual_workout_set.user_id existe : check direct (1 SELECT, rapide)
async def get_actual_workout_set_by_uuid(db, uuid, user_id):
    res = await db.execute(
        select(ActualWorkoutSet).where(
            ActualWorkoutSet.uuid == uuid,
            ActualWorkoutSet.user_id == user_id,  # check direct
        )
    )
    return res.scalar_one_or_none()
```

Avantages : performance (1 SELECT), code uniforme avec Type A standard.
Inconvénient : duplication de la donnée → invariant à maintenir (le `user_id` du set DOIT toujours être égal à celui du parent workout). À garantir par les triggers ou le code applicatif.

**Implémentation acceptable — JOIN cascade** :
```python
# Si la feuille n'a PAS de user_id : JOIN à travers les parents
async def get_actual_workout_set_by_uuid(db, uuid, user_id):
    res = await db.execute(
        select(ActualWorkoutSet)
        .join(ActualWorkoutExercise, ActualWorkoutSet.exercise_uuid == ActualWorkoutExercise.uuid)
        .join(ActualWorkout, ActualWorkoutExercise.workout_uuid == ActualWorkout.uuid)
        .where(
            ActualWorkoutSet.uuid == uuid,
            ActualWorkout.user_id == user_id,  # check via parent racine
        )
    )
    return res.scalar_one_or_none()
```

**Ce qui est interdit** : check ownership uniquement sur le parent direct sans valider la chaîne complète, ou pas de check du tout sur la feuille.

> ⚠ Pour 2B-2 : auditer chaque entité de chaîne (set, workout_exercise, planned_workout_exercise, routine_task_check, superset_exercise, etc.) → vérifier la stratégie utilisée et qu'elle remonte bien jusqu'à `User`. L'utilisateur indique avoir déjà implémenté la cascade, mais demande explicitement de vérifier.

### Dépendance `require_admin`

À créer dans `app/dependencies.py` :

```python
# app/dependencies.py
from fastapi import Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_session
from app.auth import get_current_user
from app.models.user import User


async def get_current_user_obj(
    db: AsyncSession = Depends(get_session),
    username: str = Depends(get_current_user),
) -> User:
    res = await db.execute(select(User).where(User.username == username))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                            detail="Utilisateur introuvable")
    return user


async def get_current_user_id(user: User = Depends(get_current_user_obj)) -> int:
    return user.id


async def require_admin(user: User = Depends(get_current_user_obj)) -> User:
    if not user.is_admin:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN,
                            detail="Réservé aux administrateurs")
    return user
```

Migration nécessaire :
- Ajouter `is_admin: bool = Column(Boolean, default=False, nullable=False)` au model `User`
- Migration Alembic ou patch du schéma (`ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE`)
- Marquer manuellement les comptes admin (`UPDATE users SET is_admin = TRUE WHERE username = 'will'`)

### Squelette CRUD

#### Type A — User-scoped entity

```python
# app/crud/X_crud.py
from typing import Sequence
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException

from app.models.X import X
from app.schemas import XCreate


# ── Reads ──────────────────────────────────────────────────────────────────

async def get_user_X(db: AsyncSession, user_id: int) -> Sequence[X]:
    """Liste tous les X visibles par cet utilisateur."""
    res = await db.execute(
        select(X).where(X.user_id == user_id).order_by(X.<some_default>.asc())
    )
    return res.scalars().all()


async def get_X_by_uuid(db: AsyncSession, uuid: str, user_id: int) -> X | None:
    """Récupère un X par UUID, en filtrant déjà par user_id (= ownership check)."""
    res = await db.execute(
        select(X).where(X.uuid == uuid, X.user_id == user_id)
    )
    return res.scalar_one_or_none()


# ── Writes ─────────────────────────────────────────────────────────────────

async def upsert_X(db: AsyncSession, uuid: str, dto: XCreate, user_id: int) -> X:
    """Upsert un X. Le `user_id` vient EXCLUSIVEMENT de la dépendance auth — jamais du payload."""
    res = await db.execute(
        select(X).where(X.uuid == uuid)
    )
    existing = res.scalar_one_or_none()

    if existing:
        if existing.user_id != user_id:
            raise HTTPException(status_code=403, detail="Accès interdit à ce X")
        # Update : écrasement total, en excluant les champs réservés
        for key, value in dto.model_dump(exclude={"uuid"}).items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    # Create
    obj = X(**dto.model_dump(), user_id=user_id)
    db.add(obj)
    await db.commit()
    await db.refresh(obj)
    return obj


async def bulk_upsert_X(
    db: AsyncSession, items: list[XCreate], user_id: int
) -> list[X]:
    """Bulk upsert : un seul commit final pour tout le batch."""
    out: list[X] = []
    for dto in items:
        # On réutilise upsert_X mais on évite le commit/refresh par item :
        res = await db.execute(select(X).where(X.uuid == dto.uuid))
        existing = res.scalar_one_or_none()
        if existing:
            if existing.user_id != user_id:
                raise HTTPException(status_code=403, detail=f"Accès interdit à X uuid={dto.uuid}")
            for key, value in dto.model_dump(exclude={"uuid"}).items():
                setattr(existing, key, value)
            out.append(existing)
        else:
            obj = X(**dto.model_dump(), user_id=user_id)
            db.add(obj)
            out.append(obj)
    await db.commit()
    for obj in out:
        await db.refresh(obj)
    return out


async def delete_X(db: AsyncSession, uuid: str, user_id: int) -> bool:
    """Delete par UUID + ownership check. Retourne True si supprimé, False si pas trouvé / pas owner."""
    obj = await get_X_by_uuid(db, uuid, user_id)
    if not obj:
        return False
    await db.delete(obj)
    await db.commit()
    return True
```

**Règles invariantes** :

| Aspect | Règle |
|---|---|
| Ordre des params | `db: AsyncSession` toujours en 1er. Puis `uuid` (si applicable), puis `dto`/`items`, puis `user_id`. |
| Source de `user_id` | **Toujours** issu de `Depends(get_current_user_id)` côté router, **jamais** lu du payload. Si le payload contient `user_id`, on l'ignore (ou on raise 400). |
| Ownership check | **Avant toute write** sur un objet existant. 403 si pas owner. Pas de "silent return False" sur les writes. |
| Read vs not-found | `get_X_by_uuid` retourne `None` si pas trouvé OU pas owner (= invisible). Le router transforme en 404. |
| Update style | **Écrasement total** via `model_dump()`. Si on veut du PATCH partiel, c'est un endpoint séparé `PATCH /X/{uuid}` avec un schéma `XUpdate` (tous champs `Optional`). |
| Champs réservés à exclure | `{"uuid"}` au minimum. Si on a des champs auto-gérés par la DB (`id`, `created_at`), les exclure aussi. |
| Type d'entrée bulk | `list[XCreate]` — **jamais** `list[XOut]`. |
| Type de retour write | L'objet ORM frais (après refresh). Le router fait le `jsonable_encoder(by_alias=True)`. |
| Type de retour delete | `bool` — `True` si supprimé, `False` sinon. **Pas** de dict, pas de None. |
| Commit | **1 commit par opération unitaire** (upsert, delete). **1 commit final** pour le bulk. |
| Erreurs | `HTTPException(403)` pour ownership, `HTTPException(404)` pour not-found côté router (côté CRUD : retourner None/False). |

#### Type B — M2M junction

```python
# app/crud/X_Y_crud.py — junction entre X et Y où X est user-scoped
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from fastapi import HTTPException

from app.models.x_y import XY
from app.models.x import X     # parent user-scoped
from app.schemas import XYCreate


# ── Helper ownership (1 par parent user-scoped) ───────────────────────────

async def assert_user_owns_x(db: AsyncSession, x_uuid: str, user_id: int) -> None:
    res = await db.execute(select(X).where(X.uuid == x_uuid))
    parent = res.scalar_one_or_none()
    if not parent:
        raise HTTPException(status_code=404, detail="X non trouvé")
    if parent.user_id != user_id:
        raise HTTPException(status_code=403, detail="Accès interdit à ce X")


# ── Reads (filtrent via le parent) ────────────────────────────────────────

async def get_user_xys(db: AsyncSession, user_id: int) -> list[XY]:
    res = await db.execute(
        select(XY).join(X, XY.x_uuid == X.uuid).where(X.user_id == user_id)
    )
    return res.scalars().all()


async def get_xy_by_uuid(db: AsyncSession, uuid: str, user_id: int) -> XY | None:
    """Récupère par UUID de la junction, en validant l'ownership du parent."""
    res = await db.execute(select(XY).where(XY.uuid == uuid))
    obj = res.scalar_one_or_none()
    if not obj:
        return None
    await assert_user_owns_x(db, obj.x_uuid, user_id)   # raise 403 si pas owner
    return obj


# ── Writes (ownership check OBLIGATOIRE via assert_user_owns_x) ───────────

async def upsert_xy(db: AsyncSession, uuid: str, dto: XYCreate, user_id: int) -> XY:
    await assert_user_owns_x(db, dto.x_uuid, user_id)   # ← INVARIANT

    res = await db.execute(select(XY).where(XY.uuid == uuid))
    existing = res.scalar_one_or_none()

    if existing:
        # Si existing.x_uuid != dto.x_uuid → vérifier les 2 parents
        if existing.x_uuid != dto.x_uuid:
            await assert_user_owns_x(db, existing.x_uuid, user_id)
        for key, value in dto.model_dump(exclude={"uuid"}).items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing

    obj = XY(**dto.model_dump())
    db.add(obj)
    await db.commit()
    await db.refresh(obj)
    return obj


async def bulk_upsert_xys(db: AsyncSession, items: list[XYCreate], user_id: int) -> list[XY]:
    # Pré-vérifier l'ownership de tous les parents pour fail-fast :
    for dto in items:
        await assert_user_owns_x(db, dto.x_uuid, user_id)

    out: list[XY] = []
    for dto in items:
        res = await db.execute(select(XY).where(XY.uuid == dto.uuid))
        existing = res.scalar_one_or_none()
        if existing:
            for key, value in dto.model_dump(exclude={"uuid"}).items():
                setattr(existing, key, value)
            out.append(existing)
        else:
            obj = XY(**dto.model_dump())
            db.add(obj)
            out.append(obj)
    await db.commit()
    for obj in out:
        await db.refresh(obj)
    return out


async def delete_xy(db: AsyncSession, uuid: str, user_id: int) -> bool:
    obj = await get_xy_by_uuid(db, uuid, user_id)   # raise 403 si pas owner via parent
    if not obj:
        return False
    await db.delete(obj)
    await db.commit()
    return True
```

**Règles spécifiques Type B** :

- **Une junction = un UUID** (clé primaire). Pas d'ops par paire `(parentA_uuid, parentB_uuid)` — toujours par UUID. (Existant : `delete_exercise_muscle(exercise_uuid, muscle_uuid)` est à supprimer en faveur de `delete_exercise_muscle_by_uuid(uuid)`.)
- **Ownership check** : via les parents user-scoped. Toujours valider AU MINIMUM le parent du Type A direct. Si la junction relie 2 parents user-scoped → valider les 2.
- **Helper `assert_user_owns_X`** : 1 par parent user-scoped, dans le CRUD de la junction (ou centralisé dans `app/crud/_ownership.py` à terme).

#### Type C — Global entity

```python
# app/crud/X_crud.py — entité globale, pas user-scoped

async def get_all_xs(db: AsyncSession) -> Sequence[X]:
    res = await db.execute(select(X).order_by(X.name.asc()))
    return res.scalars().all()


async def get_x_by_uuid(db: AsyncSession, uuid: str) -> X | None:
    res = await db.execute(select(X).where(X.uuid == uuid))
    return res.scalar_one_or_none()


async def upsert_x(db: AsyncSession, uuid: str, dto: XCreate) -> X:
    res = await db.execute(select(X).where(X.uuid == uuid))
    existing = res.scalar_one_or_none()
    if existing:
        for key, value in dto.model_dump(exclude={"uuid"}).items():
            setattr(existing, key, value)
        await db.commit()
        await db.refresh(existing)
        return existing
    obj = X(**dto.model_dump())
    db.add(obj)
    await db.commit()
    await db.refresh(obj)
    return obj


async def bulk_upsert_xs(db: AsyncSession, items: list[XCreate]) -> list[X]:
    out: list[X] = []
    for dto in items:
        res = await db.execute(select(X).where(X.uuid == dto.uuid))
        existing = res.scalar_one_or_none()
        if existing:
            for key, value in dto.model_dump(exclude={"uuid"}).items():
                setattr(existing, key, value)
            out.append(existing)
        else:
            obj = X(**dto.model_dump())
            db.add(obj)
            out.append(obj)
    await db.commit()
    for obj in out:
        await db.refresh(obj)
    return out


async def delete_x(db: AsyncSession, uuid: str) -> bool:
    obj = await get_x_by_uuid(db, uuid)
    if not obj:
        return False
    await db.delete(obj)
    await db.commit()
    return True
```

**Règles spécifiques Type C** :

- Aucun `user_id` dans la signature CRUD.
- **Le router impose `Depends(get_current_user_id)` sur les reads** (auth requise pour lire — politique validée).
- **Le router impose `Depends(require_admin)` sur les writes** (upsert + bulk + delete réservés aux admins — politique validée).
- Côté CRUD, les writes ne reçoivent ni `user_id` ni `admin_id` : on suppose la dépendance déjà passée. Le CRUD modifie librement la donnée globale.

Exemple Type C router :

```python
@x_router.get("", response_model=list[XOut])
async def list_xs(
    db: AsyncSession = Depends(get_session),
    _ = Depends(get_current_user_id),    # auth seulement
):
    items = await crud.get_all_xs(db)
    return jsonable_encoder(items, by_alias=True)


@x_router.put("/bulk", response_model=list[XOut])
async def bulk_upsert_xs(
    items: list[XCreate],
    db: AsyncSession = Depends(get_session),
    _ = Depends(require_admin),          # admin requis
):
    results = await crud.bulk_upsert_xs(db, items)
    return jsonable_encoder(results, by_alias=True)


@x_router.delete("/{uuid}")
async def delete_x(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    _ = Depends(require_admin),          # admin requis
):
    success = await crud.delete_x(db, uuid)
    if not success:
        raise HTTPException(status_code=404, detail="X non trouvé")
    return jsonable_encoder({"detail": "X supprimé"}, by_alias=True)
```

### Squelette Schemas Pydantic

```python
# app/schemas/X_schema.py
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class XBase(BaseModel):
    """Champs présents en input ET en output. Aliases camelCase pour le wire."""
    uuid: str
    name: str
    is_favorite: bool = Field(False, alias="isFavorite")

    updated_at: Optional[datetime] = Field(None, alias="updatedAt")
    deleted_at: Optional[datetime] = Field(None, alias="deletedAt")

    model_config = {
        "populate_by_name": True,  # accepte snake_case ET camelCase en entrée
    }


class XCreate(XBase):
    """Body input pour POST/PUT — accepte tout ce qui est dans XBase."""
    pass


class XUpdate(BaseModel):
    """Body input pour PATCH partiel. Tous les champs Optional."""
    name: Optional[str] = None
    is_favorite: Optional[bool] = Field(None, alias="isFavorite")
    updated_at: Optional[datetime] = Field(None, alias="updatedAt")
    deleted_at: Optional[datetime] = Field(None, alias="deletedAt")
    model_config = {"populate_by_name": True}


class XOut(XBase):
    """Body output — ajoute id et user_id (pour Type A) ou seulement id (pour Type B/C)."""
    id: int
    user_id: int = Field(..., alias="userId")  # ← RETIRER pour Type C global

    model_config = {
        "from_attributes": True,    # permet `XOut.model_validate(orm_obj)`
        "populate_by_name": True,
    }
```

**Règles invariantes** :

| Aspect | Règle |
|---|---|
| Triplet | **`XBase` (interne) + `XCreate` + `XOut`** au minimum. `XUpdate` si on veut PATCH partiel (sinon on n'en a pas). |
| Aliases JSON | Tout champ snake_case avec >= 2 mots → `Field(..., alias="camelCase")`. Cohérence avec ce que Gson Android attend. |
| `populate_by_name` | `True` partout — on accepte les deux sens. |
| `from_attributes` | `True` sur `XOut` uniquement. |
| `XCreate` ne contient PAS `id` | jamais. |
| `XCreate` ne contient PAS `user_id` | sauf preuve du contraire (= jamais). Le `user_id` est imposé par la dépendance auth, pas par le client. |
| `XOut.user_id` | présent pour Type A, absent pour Type C. Pour Type B : présent si on veut renvoyer `user_id` calculé via parent (sinon absent). |
| Champs `created_at` / `updated_at` / `deleted_at` | typés `datetime`, aliases camelCase. `created_at` peut être absent en `XCreate` (DB-générée). |

### Squelette Router FastAPI

```python
# app/routers/X_router.py
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.dependencies import get_current_user_id
from app.schemas import XCreate, XUpdate, XOut
from app.crud import X_crud as crud


X_router = APIRouter(prefix="/x", tags=["x"])


@X_router.get("", response_model=list[XOut])
async def list_xs(
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    items = await crud.get_user_xs(db, user_id)
    return jsonable_encoder(items, by_alias=True)


@X_router.get("/{uuid}", response_model=XOut)
async def get_x(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    obj = await crud.get_x_by_uuid(db, uuid, user_id)
    if not obj:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="X non trouvé")
    return jsonable_encoder(obj, by_alias=True)


@X_router.put("/bulk", response_model=list[XOut])    # ← AVANT /{uuid} pour éviter la collision de routing
async def bulk_upsert_xs(
    items: list[XCreate],
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    results = await crud.bulk_upsert_xs(db, items, user_id)
    return jsonable_encoder(results, by_alias=True)


@X_router.put("/{uuid}", response_model=XOut)
async def upsert_x(
    uuid: str,
    dto: XCreate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    if dto.uuid != uuid:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST,
                            detail="uuid du path et du body divergent")
    result = await crud.upsert_x(db, uuid, dto, user_id)
    return jsonable_encoder(result, by_alias=True)


@X_router.patch("/{uuid}", response_model=XOut)   # OPTIONNEL : seulement si on veut PATCH partiel
async def patch_x(
    uuid: str,
    dto: XUpdate,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    result = await crud.patch_x(db, uuid, dto, user_id)
    if not result:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="X non trouvé")
    return jsonable_encoder(result, by_alias=True)


@X_router.delete("/{uuid}")
async def delete_x(
    uuid: str,
    db: AsyncSession = Depends(get_session),
    user_id: int = Depends(get_current_user_id),
):
    success = await crud.delete_x(db, uuid, user_id)
    if not success:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="X non trouvé")
    return jsonable_encoder({"detail": "X supprimé"}, by_alias=True)
```

**Règles invariantes** :

| Aspect | Règle |
|---|---|
| `prefix=` et `tags=` | **Toujours** sur l'`APIRouter(...)`, pas au montage dans `main.py`. Pluriel cohérent : `/muscles`, `/exercises`, etc. |
| `Depends(get_current_user_id)` | sur **tous les endpoints**, même les GET. Y compris pour Type C (auth requise même si pas d'ownership). |
| Endpoints standards | `GET ""` (list), `GET /{uuid}`, `PUT /bulk`, `PUT /{uuid}`, `DELETE /{uuid}`. Optionnel : `PATCH /{uuid}` si PATCH partiel utile. |
| Ordre `/bulk` AVANT `/{uuid}` | obligatoire — sinon FastAPI route `/bulk` vers le handler `/{uuid}` avec `uuid="bulk"`. |
| Body Pydantic | toujours via type-hint sur le param (`dto: XCreate`). **Jamais** de `request.json()` parsing manuel — utiliser une assertion `if dto.uuid != uuid: 400` à la place. |
| `response_model=` | toujours déclaré (Pydantic valide la sortie + Swagger documente). |
| `jsonable_encoder(..., by_alias=True)` | **toujours** — sinon les clés sortent en snake_case et Gson Android peut ne pas mapper. |
| Status codes | `200` (par défaut implicite OK), `400` body invalide, `403` not owner, `404` not found, `401` géré par auth. Pas de `200 + body {"detail": "..."}` pour des erreurs. |

### Politiques transverses

#### Politique de commit

- Le **CRUD** est responsable des `commit()`. Le router n'appelle pas `db.commit()`.
- 1 op unitaire = 1 commit. 1 bulk = 1 commit final (après la boucle de stagings).
- Pour les opérations multi-tables (ex: créer un workout + ses sets), **transaction explicite** avec `async with db.begin():` au lieu de plusieurs commits.

#### Politique de gestion d'erreurs

- **CRUD** : retour de valeurs sentinelles (`None`, `False`, ou objet ORM) pour les cas "pas trouvé / pas owner" sur les **reads**. Raise `HTTPException(403)` pour les **writes** sur objet existant non-owné.
- **Router** : transforme les sentinelles read en `HTTPException(404)`. Laisse passer les `HTTPException` du CRUD.
- **Pas de `print` ni de `logging.error` swallowing** : laisse remonter, FastAPI gère.

#### Politique d'aliasing JSON

- **Convention serveur** : snake_case Python en interne, camelCase JSON sur le wire.
- Tout `XBase` / `XOut` doit avoir `populate_by_name = True` + `Field(alias="camelCase")` pour les champs >= 2 mots.
- **Sortie** : `jsonable_encoder(obj, by_alias=True)` à chaque endpoint.
- **Entrée** : grâce à `populate_by_name`, on accepte les 2 — utile pour la migration (clients legacy).

#### Politique de filtrage par soft-delete

- Les entités syncables ont un champ `deleted_at: Optional[datetime]` qui sert de "marqueur de suppression logique" pour la sync.
- **Décision à prendre** : les `get_user_X` filtrent-ils sur `deleted_at IS NULL` côté serveur, ou retournent-ils tout (et c'est le client qui filtre) ?
  - Le pattern Android `markAsPendingDeletion` suggère que les soft-deletes sont synchronisés au serveur, puis le serveur fait le hard-delete (ou pas).
  - À traiter en 2C / 4 (sync + DB).

### Extensions justifiées (divergences acceptables)

Les CRUDs peuvent ajouter des fonctions au-delà du squelette de base **uniquement** si elles répondent à un besoin fonctionnel réel non couvert. Exemples valides repérés dans l'existant :

| Fonction additionnelle | CRUD | Justification |
|---|---|---|
| `link_workout_to_cycle(db, cycle_uuid, workout_uuid)` | training_cycle_crud | Le cycle a une junction implicite avec planned_workout via CycleWorkout. Op métier. ⚠ doit ajouter `user_id` + ownership check. |
| `get_cycle_workouts(db, cycle_uuid)` | training_cycle_crud | Lecture par parent. Idem ⚠ user_id. |
| `delete_cycle_workout(db, cycle_uuid, workout_uuid)` | training_cycle_crud | Suppression du lien. Idem ⚠ user_id. |
| `get_check_for_task_on_date(db, user_id, task_uuid, date)` | routine_task_check_crud | Lookup par "tuple métier". Op spécifique au domaine routines. |
| `set_check_for_task_on_date(db, user_id, task_uuid, date, is_checked)` | routine_task_check_crud | UI permet de cocher/décocher pour un jour donné sans connaître l'UUID. Op spécifique. |
| `is_X_owned_by_user(db, uuid, user_id) → bool` | superset_exercise, exercise_equipment | Helper d'ownership pour les junctions multi-niveaux. ✅ aligné avec le pattern Type B. |
| `clear_all_X(db)` | available_equipment | "Vider tout" pour reset utilisateur. ⚠ doit prendre `user_id` pour ne vider que les siennes. |

**À l'inverse — divergences NON justifiées détectées** (à éliminer) :
- Signatures de params dans des ordres différents (ex: `upsert_muscle(db, user_id, dto)` vs `upsert_exercise(db, uuid, dto, user_id)`). → aligner sur `(db, uuid, dto, user_id)`.
- `delete_X` retournant un dict ou rien (vs `bool`).
- Bulk prenant `list[XOut]` au lieu de `list[XCreate]`.
- Update style : patch partiel vs écrasement total mélangés. → écrasement par défaut, PATCH dédié si besoin.
- Owner check absent ou conditionnel sur certaines fonctions.
- `assert_user_owns_X` redéfini dans plusieurs fichiers (exercise_muscle + exercise_equipment) → factoriser.

---

*Squelette canonique défini. Étape suivante : 2B-2 — auditer les 27 modules CRUD + 22 routers + 22 schemas contre cette spec, et produire le tableau de conformité.*

---

## 2B-2 — Audit de conformité des 27 modules

> 70 fichiers lus (27 CRUDs + 22 routers + 22 schemas, sauf `muscle_weekly_summary_*` qui sera supprimé). Évaluation par rapport au squelette canonique défini en 2B-1. Bugs détaillés ajoutés à [TODO_FIXES.md](TODO_FIXES.md). Cette section synthétise les divergences pour navigation rapide.

### Vue d'ensemble : 2 générations de code

L'audit révèle **deux styles** de code coexistants :

| Génération | Caractéristiques | Modules concernés |
|---|---|---|
| **Gen 1 (anciens)** | `data.dict()` (pydantic v1), checks ownership inégaux, signatures variables, `delete_X` retournant l'objet ORM | actual_workout, actual_workout_exercise, actual_workout_set, available_equipment, cycle_workout, equipment, exercise, exercise_equipment, exercise_muscle, muscle_goal, notification, planned_workout, planned_workout_exercise, superset_exercise, superset_group, training_cycle |
| **Gen 2 (récents)** | `model_dump()`, patch partiel champ par champ, ordre `(db, user_id, dto)`, `delete_X → bool` | muscle, routine_period, routine_task, routine_task_check |

Le squelette canonique (2B-1) est **un mix** des deux : `model_dump()` + écrasement total + `(db, uuid, dto, user_id)` + `delete_X → bool`. La migration Gen 1 → canonique est plus lourde (changements de signatures + comportement) que Gen 2 → canonique (changements de signatures seulement).

### Tableau de conformité — CRUDs (27 modules)

Légende :
- ✅ conforme au squelette
- ⚠ diverge sur un aspect mineur (cosmétique, logging)
- ❌ diverge significativement (signatures, sécurité, comportement)
- 🔴 bug avéré (sécurité ou crash)
- ➕ extension justifiée

| CRUD | Type | Conformité | Bugs / divergences principales |
|---|---|---|---|
| `actual_workout_crud` | A | 🔴 | `delete_*` sans user_id ; `upsert_*` ownership conditionnel ; `bulk_*` typage `list[XOut]` |
| `actual_workout_exercise_crud` | A (cascade) | ❌🔴 | 3 fonctions redondantes (create+update+upsert) ; cascade ownership ✅ via JOIN ; `delete_*` retourne l'objet |
| `actual_workout_set_crud` | A (cascade) | ❌ | Cascade ownership ✅ ; `add_set_to_actual_workout(dict)` sans user_id ; `bulk_create` sans user_id ; `upsert_many` typage `list[Model]` (pas Pydantic) |
| `available_equipment_crud` | A ou C ? | 🔴 | À auditer le model — si user-scoped, **manque user_id partout** ; bulk avec `list[XOut]` ; `delete_*` retourne dict ; `clear_all_*` sans user_id |
| `cycle_workout_crud` | B | ❌🔴 | `assert_user_owns_planned_workout` ✅ (helper) ; `get_*_by_uuid` sans user_id ; `upsert_*` reçoit user_id mais l'ignore ; `bulk_*` idem ; `remove_*` retourne dict |
| `equipment_crud` | C | ❌ | Pas de check admin (à ajouter via le router) ; `bulk_*` typage `list[XOut]` ; `delete_*` retourne dict |
| `exercise_crud` | A | ⚠ | Signature `(db, uuid, dto, user_id)` ✅ ; raise 403 ✅ ; `bulk_upsert` raise sur duplicate name (extension à valider) ; `delete_X` raise 404 (canonique = bool) |
| `exercise_equipment_crud` | B | ✅ | Pattern Type B respecté ; `assert_user_owns_exercise` ✅ ; `delete_X → bool` ✅ ; ⚠ modifie l'UUID d'un objet existant ([crud:82](../serveur/app/crud/exercise_equipment_crud.py#L82)) |
| `exercise_muscle_crud` | B | 🔴 | `assert_user_owns_exercise` existe mais **non appelé sur upsert** (bug sécurité) ; `delete_*_by_uuid` ne retourne rien ; `delete_*` (par paire) extension justifiée mais doublon avec `_by_uuid` |
| `muscle_crud` | A (mixte) | ✅ | Gen 2 stylé : signature `(db, user_id, dto)` (canonique demande `(db, uuid, dto, user_id)`) ; patch partiel (canonique demande écrasement total) ; cohérent en interne |
| `muscle_goal_crud` | A | ⚠ | Owner check ✅ ; helper `get_muscle_by_uuid` raise 404 ; `delete_*` retourne `False` (✅) ; export doublé dans `__init__.py` |
| `notification_crud` | A | ⚠ | Pattern proche canonique ✅ ; `delete_*` raise au lieu de retourner bool |
| `planned_workout_crud` | A | ❌ | Owner check upsert ✅ ; **création n'inclut pas user_id du caller** ([crud:47](../serveur/app/crud/planned_workout_crud.py#L47)) — confié au schéma `PlannedWorkoutBase.user_id` (bug schéma) |
| `planned_workout_exercise_crud` | B | ❌🔴 | Cascade ownership PARTIELLE : `upsert_*` ne vérifie pas le parent (commentaire "si besoin") ; `bulk_*` ✅ vérifie ; `delete_*` ✅ via helper ; helper `is_planned_workout_owned_by_user_uuid` ✅ |
| `routine_period_crud` | A | ✅ | Gen 2 stylé ; signature `(db, user_id, dto)` ; patch partiel ; `delete_X → bool` ✅ |
| `routine_task_crud` | A | ✅ | Gen 2 stylé ; helper `get_routine_tasks_by_period_uuid` ➕ ; conforme |
| `routine_task_check_crud` | A | ✅ | Gen 2 stylé ; `get_check_for_task_on_date` + `set_check_for_task_on_date` ➕ |
| `superset_exercise_crud` | B (cascade via group) | ❌🔴 | `get_*_by_uuid` reçoit user_id non utilisé (JOIN commenté) ; helper `is_superset_exercise_owned_by_user` ✅ ; `delete_*` raise au lieu de bool |
| `superset_group_crud` | A | ❌ | `upsert_*` met `user_id` dans le data (lit-il du payload ?) ; `delete_*` retourne l'objet (pas bool) |
| `training_cycle_crud` | C | ❌ | Pas de check user_id ni admin ; `link_workout_to_cycle` + `get_cycle_workouts` + `delete_cycle_workout` ➕ extensions justifiées (pas user_id) |
| `user_crud` | spécial | ⚠ | Cas particulier (id-based) — hors scope du squelette ; signatures par params discrets au lieu de DTO Pydantic |

**Observations** :
- 4 CRUDs avec cascade ownership (set, exercise dans workout, workout exercise, superset exercise) : **2 corrects via JOIN, 2 partiels/buggés**.
- Plus de la moitié des CRUDs ont au moins un bug ou divergence significative.
- 4 helpers `assert_user_owns_X` redéfinis dans 4 CRUDs différents — à factoriser dans `app/crud/_ownership.py` ou `app/dependencies.py`.

### Tableau de conformité — Routers (22 modules entité)

| Router | Auth | Conformité | Bugs principaux |
|---|---|---|---|
| `actual_workout_router` | ✅ | ❌🔴 | bulk accepte `list[XOut]` ; delete retourne dict (fait double-check user_id) |
| `actual_workout_exercise_router` | ✅ | ⚠ | delete retourne objet ORM brut |
| `actual_workout_set_router` | ✅ | ❌🔴 | bulk accepte `list[XOut]` ; mute le DTO ; delete retourne objet |
| `available_equipment_router` | ❌🔴 | 🔴 | **AUCUN auth** ; DELETE sans uuid clear toute la table sans auth ; bulk `list[XOut]` |
| `cycle_workout_router` | ✅ | ⚠ | GET `/{cycle}/{wkt}` extension ➕ ; delete retourne dict avec `detail` |
| `equipment_router` | ❌🔴 | 🔴 | **AUCUN auth** ; pattern propre par ailleurs ; manque admin sur writes |
| `exercise_equipment_router` | ✅ | ⚠ | Path `/exercise-equipment` (singulier) vs pluriel ailleurs |
| `exercise_muscle_router` | ✅ | ❌ | 2 endpoints DELETE qui se chevauchent ; un d'eux ne check pas le succès |
| `exercise_router` | ✅ | ⚠ | delete retourne objet ORM brut |
| `muscle_goal_router` | ✅ | ✅ | Conforme ; tags manquants |
| `muscle_router` | ✅ | ⚠ | **Seul** router avec `tags=`✅ ; mais `upsert_X` parse `request.json()` manuellement |
| `notification_router` | ✅ | ⚠ | delete retourne bool brut sans 404 |
| `planned_workout_router` | ✅ | ✅ | Pattern propre ; delete retourne `{"detail":...}` ✅ |
| `planned_workout_exercise_router` | ✅ | ⚠🔴 | **Collision routing /planned-workouts/{uuid}** avec `planned_workout_router` ; ownership check dupliqué |
| `routine_period_router` | ✅ | ⚠ | delete retourne bool brut sans 404 |
| `routine_task_router` | ✅ | ⚠ | delete retourne bool brut ; `period_uuid` query param ➕ extension OK |
| `routine_task_check_router` | ✅ | ⚠ | delete retourne bool brut ; `date` query param ➕ extension OK |
| `superset_exercise_router` | ✅ | ❌🔴 | **`/{exercise_id}` route 500** (CRUD inexistant) + typage `int` au lieu de UUID |
| `superset_group_router` | ✅ | ❌🔴 | `upsert_*` **path /{uuid} sans param uuid → 422** |
| `training_cycle_router` | ❌🔴 | 🔴 | **AUCUN auth** ; `upsert_*` même bug 422 sans param uuid ; `link_workout_to_cycle` etc. ➕ extensions OK mais sans auth |
| `user_router` | ❌🔴 | 🔴🔴🔴 | **PUBLIC TOTAL** : list users, create, update, delete sans aucune auth — **CRITIQUE** |
| `muscle_weekly_summary_router` | ✅ | ⚠ | À supprimer (entité fantôme) |

**Observations** :
- **5 routers sans auth** ([user_router](../serveur/app/routers/user_router.py), [equipment_router](../serveur/app/routers/equipment_router.py), [available_equipment_router](../serveur/app/routers/available_equipment_router.py), [training_cycle_router](../serveur/app/routers/training_cycle_router.py), partiellement [muscle_weekly_summary_router](../serveur/app/routers/muscle_weekly_summary_router.py)) — **politique de sécurité validée violée**.
- **4 bugs casse-l'API** : `superset_exercise_router` 500, `superset_group_router` + `training_cycle_router` 422, collision routing `/planned-workouts/{uuid}`.
- **1 seul router** sur 22 a `tags=` (`muscle_router`) — **0** ont `prefix=`.
- Conventions de réponse delete inconsistantes : objet ORM brut, dict avec `detail`, dict avec `message`, `bool` brut, `{"status": "deleted"}` — chaque router a sa version.

### Tableau de conformité — Schémas (22 modules)

| Schema | Triplet | `populate_by_name` sur Base ? | `id` dans Out ? | `user_id` dans Out ? | Bugs principaux |
|---|---|---|---|---|---|
| `actual_workout_schema` | Base/Create/Out | ✅ | ❌ | ✅ | `uuid: Optional[str] = None` (devrait être obligatoire) |
| `actual_workout_exercise_schema` | Base/Create/Out | ❌ | ❌ | ❌ (cascade) | `uuid: Optional[str] = None` ; `Out` ajoute `added_manually` (déjà dans `Create`) |
| `actual_workout_set_schema` | Base/Create/Out | ❌ | ❌ | ❌ (cascade) | OK structure ; cascade implicite via parent |
| `available_equipment_schema` | Base/Create/Out | ❌ | ❌ | ❌ | À auditer si Type A (manque user_id) |
| `cycle_workout_schema` | Base/Create/Out | ❌ | ❌ | ❌ (junction) | OK |
| `equipment_schema` | Base/Create/Out | ❌ | ❌ | ❌ (Type C) | OK |
| `exercise_equipment_schema` | Base/Create/Out | ❌ | ❌ | ❌ (junction) | OK |
| `exercise_muscle_schema` | Base/Create/Out | ❌ | ❌ | ❌ (junction) | OK |
| `exercise_schema` | Base/Create/Out | ❌ | ❌ | ✅ | OK structure |
| `muscle_goal_schema` | Base/Create/Out | ❌ | ❌ | ✅ | **`weekISO: str` sans alias** (naming non standard) |
| `muscle_schema` | Base/Create/Out | ✅ | ✅ | ✅ | **Le seul vraiment conforme au canonique** |
| `notification_schema` | Base/Create/Out | ❌ | ❌ | ✅ | OK |
| `planned_workout_schema` | Base/Create/Out | ❌ | ❌ | ✅ (mais aussi dans Base!) | 🔴 `user_id` obligatoire dans `Base` → `Create` exige `userId` du client |
| `planned_workout_exercise_schema` | Base/Create/Out | ❌ | ❌ | ❌ (cascade) | OK |
| `routine_period_schema` | Base/Create/Out | ❌ | ❌ | ✅ | OK |
| `routine_task_schema` | Base/Create/Out | ❌ | ❌ | ✅ | OK |
| `routine_task_check_schema` | Base/Create/Out | ❌ | ❌ | ✅ | OK |
| `superset_exercise_schema` | Base/Create/Out | ❌ | ❌ | ❌ (cascade) | OK |
| `superset_group_schema` | Base/Create/Out | ❌ | ❌ | ✅ (mais aussi dans Base!) | 🔴 `user_id` obligatoire dans `Base` → `Create` exige `userId` du client |
| `training_cycle_schema` | Base/Create/Out | ❌ | ❌ | ❌ (Type C) | OK |
| `user_schema` | Create/Out/Public/Upsert | ✅ | ✅ (id) | n/a | Cas spécial — pas de Base ; `UserUpsert` étend pour bulk |

**Observations** :
- **18/22 schémas n'ont pas `model_config = {"populate_by_name": True}` sur `Base`** (seulement sur `Out`). Comportement input asymétrique : seul le format alias (camelCase) est accepté, snake_case rejeté.
- **2 schémas ont `user_id` obligatoire dans `Base` (= `Create`)** : `PlannedWorkout`, `SupersetGroup`. Le client peut donc créer pour un autre user en envoyant `userId` dans le body — bug sécurité 🔴.
- **1 schéma 100% conforme au canonique** : `muscle_schema`.
- Aucun schéma n'expose `XUpdate` (PATCH partiel) — cohérent puisque la politique est PUT-overwrite.

### Plan de remédiation suggéré

**Phase 1 — Bugs critiques (0 tolérance)** :
1. Sécuriser les 5 routers sans auth (`user_router` en priorité car le plus dangereux).
2. Corriger les 3 bugs casse-l'API (500 / 422).
3. Corriger la collision de routing `/planned-workouts/{uuid}`.
4. Retirer `user_id` de `PlannedWorkoutBase` et `SupersetGroupBase`.
5. Ajouter le champ `User.is_admin` + dépendance `require_admin`.

**Phase 2 — Sécurité ownership** :
6. Auditer chaque entité de chaîne pour la cascade ownership (4 candidats : actual_workout_set ✅, actual_workout_exercise ✅ via JOIN, planned_workout_exercise ❌, superset_exercise ❌).
7. Corriger `actual_workout_crud` (delete sans user_id, upsert ownership conditionnel).
8. Corriger `exercise_muscle_crud.upsert_*` (assert_user_owns_exercise non appelé).
9. Corriger `cycle_workout_crud` (3 fonctions ignorent user_id).

**Phase 3 — Uniformisation** :
10. Définir/factoriser `assert_user_owns_X` dans `app/crud/_ownership.py` (ou similaire).
11. Migrer Gen 1 vers canonique : `data.dict()` → `model_dump()` ; signatures `(db, uuid, dto, user_id)` ; `delete_X → bool` ; bulk `list[XCreate]`.
12. Migrer Gen 2 (4 modules) : alignement signatures sur canonique.
13. Ajouter `tags=` et `prefix=` sur les 21 routers manquants.
14. Standardiser le format de réponse `delete` (toujours `{"detail": "..."}`+ 404 si pas trouvé).
15. Ajouter `model_config = {"populate_by_name": True}` sur les 18 `Base` qui ne l'ont pas.

**Phase 4 — Suppression** :
16. Supprimer `MuscleWeeklySummary` (cf. TODO_FIXES, gros chantier avec migration Room).

**Phase 5 — Cosmétique** :
17. `print` → `logger`.
18. Retirer `dict()` partout.
19. Path `/exercise-equipment` → `/exercise-equipments` (pluriel).

---

*Sous-étape 2B-2 terminée. Le serveur a 5 endpoints publics non sécurisés (dont `user_router` total) — à traiter en priorité absolue. Étape suivante : 2C (scripts DB + tests + triggers SQL).*

---

# 2C — Scripts DB + Alembic + Triggers SQL + Tests

> Cette section couvre l'infrastructure périphérique du serveur : scripts opérationnels (`exec_pg.py`, `fill_database.py`, `seed_database.py`, `clear_database.py`, helpers de debug), migrations Alembic, fichiers SQL de triggers et fichiers de tests.

## Sommaire 2C

- [Scripts DB opérationnels](#scripts-db-opérationnels)
- [Helpers de debug](#helpers-de-debug)
- [Alembic — état et anomalies](#alembic--état-et-anomalies)
- [Triggers SQL — architecture et matrice de couverture](#triggers-sql--architecture-et-matrice-de-couverture)
- [Tests](#tests)
- [Findings de la sous-étape 2C](#findings-de-la-sous-étape-2c)

## Scripts DB opérationnels

| Script | Rôle | État |
|---|---|---|
| [`serveur/exec_pg.py`](../serveur/exec_pg.py) | DROP all + CREATE all + charge triggers SQL + lance uvicorn | ⚠ **destructif** ; manque 2 triggers (déjà noté en 2A) |
| [`serveur/app/fill_database.py`](../serveur/app/fill_database.py) | DROP toutes les tables sauf `users` + recreate + clear + seed | ⚠ **ne réinstalle pas les triggers** (DEV_GUIDE le mentionne) ; preserve users via `tables=tables_to_drop` ✅ |
| [`serveur/app/seed_database.py`](../serveur/app/seed_database.py) | Insertion massive de données fictives | ⚠ **bug** : seuls 5 muscles sont seedés (les 20 autres sont dans une string commentée triple-quote) |
| [`serveur/app/clear_database.py`](../serveur/app/clear_database.py) | Vide toutes les tables sauf `users` | ✅ ordre FK respecté ; ⚠ référence `muscle_weekly_summary` (à supprimer après cleanup de l'entité) |
| [`serveur/old_exec_file.py`](../serveur/old_exec_file.py) | Ancienne version | ❌ **code mort** — référence un fichier `database.db` SQLite (l'app a migré vers Postgres). À supprimer (déjà confirmé par l'utilisateur). |
| [`serveur/start_api.sh`](../serveur/start_api.sh) | Wrapper systemd | ⚠ utilise `--reload` (cf. 🟡 TODO 2A) |
| [`serveur/deploy.sh`](../serveur/deploy.sh) | git pull + pip install + systemctl restart | ✅ |

### Comportement détaillé

- **`exec_pg.py`** ([2A](#2--mainpy--point-dentrée) déjà couvert) : charge 16 fichiers `.sql` (sur 18 pertinents — manque `sessions_trigger.sql` à juste titre, mais aussi `training_cycles_trigger.sql` qui est obsolète, cf. ci-dessous).
- **`fill_database.py`** : utilise `Base.metadata.drop_all(tables=tables_to_drop)` puis `create_all(checkfirst=True)`. Skip la table `users`. Mais **ne ré-attache pas les triggers** : après un fill, les triggers WS sont absents. → un push WebSocket cassé tant qu'on relance pas `exec_pg.py`. Bug connu (DEV_GUIDE §9).
- **`seed_database.py`** : 700+ lignes. Crée 5 users (`will`, `bob`, `charlie`, `diana`, `eve`) avec le mot de passe défini par `SEED_USER_PASSWORD`. Crée 14 exercices, 8 actual workouts, 3 actual_workout_exercises et 12 sets pour le user 1. ⚠ **Bug subtil ligne 423-446** : la liste `names_zones` ouvre une string `"""` après les 5 premiers items, ce qui transforme les 20 muscles suivants en literal commenté Python — donc **seuls 5 muscles sont créés** alors que 25 étaient prévus. À corriger.
- **`seed_database.py`** : référence `MuscleWeeklySummary` (5 entries) → à retirer quand l'entité sera supprimée.
- **`seed_database.py`** : un bloc `WorkoutSession(...)` est commenté (vestige du rename `sessions → actual_workout`). À nettoyer.
- **`clear_database.py`** : vide tables dans l'ordre des FK (du plus dépendant au moins). `users` préservé. Référence `muscle_weekly_summary` (à virer aussi).

## Helpers de debug

| Script | Rôle | État |
|---|---|---|
| [`serveur/test_db_connection.py`](../serveur/test_db_connection.py) | Mini-test asyncpg `SELECT 1` | ✅ utile pour debug Windows |
| [`serveur/app/inspect_schema.py`](../serveur/app/inspect_schema.py) | Liste les tables + colonnes via SQLAlchemy `inspect()` | ⚠ **bug probable** : `inspect(engine)` est synchrone, mais `engine` est async (`create_async_engine`). Risque de ne pas fonctionner en l'état. À tester. |
| [`serveur/app/view_database.py`](../serveur/app/view_database.py) | Affiche le top 10 de chaque table (debug) | ✅ utile ; référence `MuscleWeeklySummary` à virer |
| [`serveur/app/print_models_sqlalchemy.py`](../serveur/app/print_models_sqlalchemy.py) | Helper debug models | À auditer (non lu) |

## Alembic — état et anomalies

[`serveur/alembic.ini`](../serveur/alembic.ini) + [`serveur/app/alembic/env.py`](../serveur/app/alembic/env.py) + [`serveur/app/alembic/versions/20250811_notify_triggers.py`](../serveur/app/alembic/versions/20250811_notify_triggers.py)

### État actuel

| Aspect | Valeur |
|---|---|
| `script_location` | `app/alembic` ✅ |
| `sqlalchemy.url` (alembic.ini) | placeholder `driver://user:pass@localhost/dbname` (réécrit par `env.py`) |
| URL effective | `settings.DATABASE_URL.replace("+asyncpg", "")` → sync, OK |
| `target_metadata` | **`None`** ❌ — autogenerate **impossible** |
| Migrations | **1 seule** : `20250811_notify_triggers.py` |
| Revision ID | `"2025xxxx_notify_triggers"` (placeholder, devrait être un hash hex) |
| Down revision | `None` (= première migration, OK) |

### Anomalie majeure : divergence entre Alembic et `db_triggers/`

La migration Alembic [`20250811_notify_triggers.py`](../serveur/app/alembic/versions/20250811_notify_triggers.py) définit `notify_row_change()` avec un payload **simple** :

```sql
payload := jsonb_build_object(
  'table', TG_TABLE_NAME,
  'op', op,
  'id', to_jsonb(rec.id),
  'uuid', to_jsonb(rec.uuid),
  'user_id', to_jsonb(rec.user_id),
  'updated_at', COALESCE(to_jsonb(rec.updated_at), to_jsonb(NOW()))
);
PERFORM pg_notify('db_events', payload::text);
```

…tandis que [`base_function.sql`](../serveur/app/db_triggers/base_function.sql) + les blocs spécifiques par table (`<table>_trigger.sql`) définissent une **autre version** : un payload riche par table avec `type: '<entity>_updated'` ou `'<entity>_deleted'` + champs spécifiques + `originClientId`.

**Conséquence** : selon ce qui est appliqué en dernier (`alembic upgrade` OU `python exec_pg.py`), le payload broadcast au WS est différent. C'est l'`exec_pg.py` qui gagne actuellement (`CREATE OR REPLACE FUNCTION`), mais c'est fragile.

**Recommandation** : intégrer la version "riche" dans une nouvelle migration Alembic, retirer le bloc dupliqué côté `db_triggers/`. Décider une seule source de vérité.

## Triggers SQL — architecture et matrice de couverture

### Architecture observée

```
exec_pg.py:recreate_schema()
    │
    ├─ DROP all + CREATE all (Base.metadata)
    │
    ├─ Charge base_function.sql                    ← définit notify_row_change() — squelette
    │     │
    │     │ {{TABLE_SPECIFIC_BLOCKS}} ← remplacé par concat de :
    │     │   ├─ actual_workouts_trigger.sql
    │     │   ├─ actual_workout_exercises_trigger.sql
    │     │   ├─ actual_workout_sets_trigger.sql
    │     │   ├─ available_equipments_trigger.sql
    │     │   ├─ cycle_workouts_trigger.sql
    │     │   ├─ equipments_trigger.sql
    │     │   ├─ exercise_equipment_trigger.sql
    │     │   ├─ exercise_muscle_trigger.sql
    │     │   ├─ exercises_trigger.sql
    │     │   ├─ muscle_goals_trigger.sql
    │     │   ├─ muscle_weekly_summary_trigger.sql  ← à supprimer (entité fantôme)
    │     │   ├─ muscles_trigger.sql
    │     │   ├─ planned_workout_exercises_trigger.sql
    │     │   ├─ planned_workouts_trigger.sql
    │     │   ├─ superset_exercises_trigger.sql
    │     │   └─ superset_groups_trigger.sql
    │     │
    │     └─ Au runtime : pg_notify('db_events', payload)
    │
    ├─ Charge attach_triggers.sql                  ← attache trg_<table>_notify
    │     │  pour toute table ayant `id ET uuid`
    │     │  (= toutes les tables sauf alembic_version)
    │
    └─ Charge user_id_helper.sql                   ← fonction get_user_id_for(table, uuid)
```

### Deux styles de triggers coexistants

**Style "moderne"** (utilisé par les 16 fichiers chargés par `exec_pg.py`) :
- Le fichier `.sql` contient juste un fragment `IF TG_TABLE_NAME = 'X' THEN payload := jsonb_build_object(...) END IF`
- Concaténé dans `base_function.sql` qui dispatch via `TG_TABLE_NAME`
- Tous les triggers utilisent `notify_row_change()` (1 seule fonction pour toutes les tables)

**Style "legacy"** (2 fichiers obsolètes, NON chargés) :
- `sessions_trigger.sql` — définit `notify_sessions_change()` (fonction complète + `pg_notify`). Vestige du rename. À **supprimer**.
- `training_cycles_trigger.sql` — définit `notify_training_cycles_change()` (fonction complète, isolée). **Pas chargé** par `exec_pg.py:load_sql_parts()`. → **L'entité `training_cycles` n'a aucun push WS** (ni via cette fonction obsolète, ni via le pipeline moderne). À migrer vers le style moderne.

### Matrice de couverture — quelles entités ont un push WebSocket ?

| Entité | Trigger SQL fichier | Listé `exec_pg.py` ? | Trigger DB attaché ? | userId dans payload ? | Push WS fonctionnel ? |
|---|---|---|---|---|---|
| actual_workouts | ✅ moderne | ✅ | ✅ (id+uuid) | ✅ via helper | ✅ |
| actual_workout_exercises | ✅ moderne | ✅ | ✅ | ✅ via helper | ✅ |
| actual_workout_sets | ✅ moderne | ✅ | ✅ | ✅ via subselect→helper | ✅ |
| available_equipment | ✅ moderne | ✅ | ✅ | ⚠ à vérifier (Type A ou C ?) | ⚠ |
| cycle_workouts | ✅ moderne | ✅ | ✅ | ✅ via helper | ✅ |
| equipment | ✅ moderne | ✅ | ✅ | ❌ (Type C) | ⚠ broadcast à TOUS |
| exercise_equipment | ✅ moderne | ✅ | ✅ | ⚠ pas de userId dans payload | ⚠ broadcast à TOUS |
| exercise_muscles | ✅ moderne | ✅ | ✅ | ❌ pas de userId | 🔴 broadcast à TOUS |
| exercises | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| muscle_goals | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| muscle_weekly_summary | ✅ moderne | ✅ | ✅ | ✅ | ✅ — mais à supprimer |
| muscles | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| planned_workout_exercises | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| planned_workouts | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| superset_exercises | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| superset_groups | ✅ moderne | ✅ | ✅ | ✅ | ✅ |
| **training_cycles** | ❌ legacy isolé | ❌ | ✅ (générique sans payload) | n/a | 🔴 **AUCUN push** |
| **notifications** | ❌ aucun | n/a | ✅ (générique sans payload) | n/a | 🔴 **AUCUN push** |
| **routine_periods** | ❌ aucun | n/a | ✅ (générique sans payload) | n/a | 🔴 **AUCUN push** |
| **routine_tasks** | ❌ aucun | n/a | ✅ (générique sans payload) | n/a | 🔴 **AUCUN push** |
| **routine_task_checks** | ❌ aucun | n/a | ✅ (générique sans payload) | n/a | 🔴 **AUCUN push** |
| sessions | ❌ legacy isolé | ❌ | n/a (table absente) | n/a | n/a (entité fantôme) |
| users | n/a (pas pushé) | n/a | ✅ générique sans payload | n/a | n/a (intentionnel) |

**Lecture critique** :
- 5 entités actives **n'ont aucun push WebSocket** : `training_cycles`, `notifications`, `routine_periods`, `routine_tasks`, `routine_task_checks`. Pour ces entités, la sync se fait uniquement via les API REST, pas en temps réel.
- 2 entités ont un payload **sans `userId`** (`exercise_muscles`, `exercise_equipment`, `equipment`) → broadcast à tous les WS connectés. Conséquence pratique : un user A modifie un de ses `exercise_muscle`, **tous** les autres users connectés en sont notifiés. Côté Android : le sync handler doit ignorer si l'exercice parent n'est pas dans sa Room locale (à confirmer en étape 3).

### Helper `user_id_helper.sql` — couverture FK

[`serveur/app/db_triggers/user_id_helper.sql`](../serveur/app/db_triggers/user_id_helper.sql) couvre 16 cas (table → user_id) :

| Table | Stratégie | Note |
|---|---|---|
| muscles, exercises, muscle_goals, planned_workouts, actual_workouts, superset_groups | Direct (`SELECT user_id WHERE uuid = ...`) | ✅ |
| exercise_equipment, exercise_muscles | Via `exercises.user_id` (1 JOIN) | ✅ |
| actual_workout_exercises, actual_workout_sets | Via `actual_workouts.user_id` (1-2 JOINs) | ✅ |
| muscle_weekly_summary | Via `muscles.user_id` | ⚠ à virer |
| planned_workout_exercises | Via `planned_workouts.user_id` | ✅ |
| superset_exercises | Via `superset_groups.user_id` | ✅ |
| training_cycles | Via `planned_workouts → cycle_workouts → training_cycles` | ⚠ chaîne complexe (pas de user_id sur training_cycle) |
| cycle_workouts | Via `planned_workouts.user_id` | ✅ |
| **sessions** | présente | 🔴 entité fantôme à virer |
| `notifications`, `routine_*`, `available_equipment`, `equipment` | **absent** | 🔴 manque |

## Tests

| Fichier | Rôle | État |
|---|---|---|
| [`serveur/tests/full_test.py`](../serveur/tests/full_test.py) | Générateur : (1) export routes via `app.routes` → `routes.json`, (2) génère `test_api.py` à partir de `routes.json`, (3) lance pytest | ⚠ régénère à partir de `routes.json` qui était obsolète. Logique correcte mais output dépend du moment d'exécution. |
| [`serveur/test_api.py`](../serveur/test_api.py) | Tests générés (smoke) | ❌ **obsolète** — teste des routes qui n'existent plus (`/sessions`) ; ne teste pas `/notifications`, `/routine-*`, `/ws`. À régénérer. |
| [`serveur/test_db_connection.py`](../serveur/test_db_connection.py) | Helper debug asyncpg | ✅ à garder ou déplacer dans `scripts/` |

### Qualité des tests générés

L'assertion universelle est :
```python
assert response.status_code in [200, 201, 204, 404, 422]
```

**Conséquence** : ces tests passent même si le serveur retourne une 422 sur tous les endpoints (= validation Pydantic échoue). Ils valident **la simple présence de la route**, pas son comportement. **Tests de smoke** uniquement.

L'auth est en dur : `username=will, password=<password>`. Si on change le seed (par ex. en supprimant `will`), ces tests ne tournent plus.

**Recommandation** : ces tests sont une base mais doivent être complétés par des tests fonctionnels (créer un workout, vérifier qu'il apparaît dans la liste, etc.). À traiter en TODO_FEATURES.

## Findings de la sous-étape 2C

Tous les items ont été ajoutés à [TODO_FIXES.md](TODO_FIXES.md) et/ou seront répercutés dans `TODO_FEATURES.md` à l'étape 6.

### 🔴 Critique
- **5 entités sans push WebSocket** : `training_cycles`, `notifications`, `routine_periods`, `routine_tasks`, `routine_task_checks` — leurs changements ne sont pas propagés en temps réel aux clients connectés.
- **`exercise_muscle_trigger`, `exercise_equipment_trigger`, `equipment_trigger` payloadent SANS `userId`** — broadcast à TOUS, fuite cross-utilisateurs (le client doit filtrer côté Android — à confirmer).
- **Divergence Alembic ↔ `db_triggers/`** — deux versions de `notify_row_change()` coexistent (simple vs riche). Selon ce qui est appliqué en dernier, le payload diffère. Source de vérité à unifier.

### 🟠 Important
- **`seed_database.py` ne crée que 5 muscles** (les 20 autres sont dans une string commentée par triple-quote, [seed_database.py:423-446](../serveur/app/seed_database.py#L423)).
- **`fill_database.py` ne réinstalle pas les triggers** — déjà noté, à confirmer dans le plan de remédiation.
- **`alembic/env.py:target_metadata = None`** — empêche `alembic revision --autogenerate`. À brancher sur `Base.metadata` (importer `app.models` pour enregistrer les modèles).
- **`test_api.py` obsolète** — régénérer après mise à jour de `routes.json`. Idem `tests/full_test.py` ré-exécuté après nettoyage.
- **2 fichiers de triggers SQL legacy** à supprimer : `sessions_trigger.sql`, `training_cycles_trigger.sql` (le second remplacer par un fichier moderne).
- **`user_id_helper.sql` manque 6 entités** : `notifications`, `routine_periods`, `routine_tasks`, `routine_task_checks`, `available_equipment`, `equipment`. Inclut aussi `sessions` (à virer).

### 🟡 Mineur
- **`inspect_schema.py`** : `inspect(engine)` synchrone sur engine async — peut ne pas fonctionner. À tester ou refactorer.
- **`alembic.ini`** : `sqlalchemy.url` placeholder (pas critique car réécrit, mais traînerait à un script qui le lit directement).
- **Bloc `WorkoutSession` commenté dans `seed_database.py`** ([l. 535-545](../serveur/app/seed_database.py#L535)) — vestige du rename, à supprimer.
- **`old_exec_file.py`** déjà confirmé comme code mort — à supprimer (cf. TODO_FIXES Phase 1).

---

*Sous-étape 2C terminée. Étape 2 (Serveur en profondeur) **complète**.*

## Bilan global de l'étape 2 (Serveur)

**3 sous-étapes terminées** :
- [2A. Infrastructure](#2a--infrastructure) — 12 fichiers d'infra documentés, 18 findings
- [2B-1. Squelette canonique](#2b-1--squelette-canonique) — spec de référence pour CRUDs/routers/schemas/auth
- [2B-2. Audit de conformité](#2b-2--audit-de-conformité-des-27-modules) — 70 fichiers évalués, ~30 findings dont **15 bugs critiques**
- [2C. Scripts DB + Triggers + Tests](#2c--scripts-db--alembic--triggers-sql--tests) — 12 findings

**Compte des bugs critiques** (🔴 dans TODO_FIXES) : ~25 items. Concentrés sur :
- Sécurité : 5 routers publics, `actual_workout_crud` × 3, `exercise_muscle_crud`, cascade ownership × 4
- Stabilité : 4 bugs casse-l'API (500/422), Room sans Migration, Alembic divergent
- Realtime : 5 entités sans push WS, 3 triggers sans userId

**Le serveur est fonctionnel** (l'app communique avec) **mais a 25 bugs critiques** dont certains exposent des données sur Internet via `<public-dns>`. Le plan de remédiation en 5 phases ([§2B-2 fin](#plan-de-remédiation-suggéré)) priorise ces fixes après audit complet.

**Prochaine étape** : 3 (Application Android en profondeur). Objectif : mettre la lumière sur la moitié client de la communication, voir comment elle compense (ou amplifie) les bugs serveur, et poser les bases pour étape 5 (mapping endpoints serveur ↔ Android).
