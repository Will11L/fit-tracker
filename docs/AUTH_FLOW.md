# AUTH_FLOW — Diagramme séquence

Flow d'authentification JWT bout-en-bout entre l'app Android et le serveur FastAPI : signup, login, accès authentifié, expiration `access_token` (~30 min) et refresh transparent via `Authenticator` OkHttp. Complète [FLOWS.md §1](FLOWS.md) (vue curl/JSON) avec une vue temporelle des acteurs.

## Diagramme

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant App as Android App
    participant Auth as AuthManager / TokenManager
    participant OkHttp as OkHttp Authenticator
    participant Token as FastAPI /token
    participant Me as FastAPI /me
    participant DB as PostgreSQL

    Note over User,DB: 1. Signup (compte neuf, public)
    User->>App: tape username + password
    App->>Token: POST /api/v1/signup<br/>{username, password, first_name?, last_name?}
    Token->>DB: SELECT users WHERE username=?
    DB-->>Token: rien
    Token->>DB: INSERT users + copy_starter_pack()<br/>(12 muscles + 20 exercices + 43 relations)
    DB-->>Token: ok (commit)
    Token-->>App: 201 UserOut {id, username, isAdmin:false}

    Note over User,DB: 2. Login -> JWT + refresh
    User->>App: ouvre LoginScreen
    App->>Token: POST /api/v1/token<br/>form-urlencoded username, password
    Token->>DB: SELECT users WHERE username=?
    DB-->>Token: user + hashed_password
    Token->>Token: verify_password (bcrypt)
    Token->>DB: INSERT refresh_tokens (long-lived)
    DB-->>Token: ok
    Token-->>App: 200 {access_token (30min), refresh_token, token_type:"bearer"}
    App->>Auth: TokenManager.setTokens()<br/>EncryptedSharedPreferences

    Note over User,DB: 3. Validation post-login + bootstrap WS / sync
    App->>Me: GET /api/v1/me (Bearer)
    Me-->>App: 200 {id, username, isAdmin, firstName, lastName}
    App->>App: CurrentUserManager.setUserId() + setUserAdmin()
    App->>App: WebSocketManager.start(token)<br/>+ SyncCoordinator.onLogin()

    Note over User,DB: 4. Appel authentifié quelconque (cas nominal)
    User->>App: action UI
    App->>Me: GET /api/v1/<entity><br/>Authorization: Bearer <access>
    Me-->>App: 200 payload JSON

    Note over User,DB: 5. Expiration access -> refresh transparent
    User->>App: action UI (~30 min plus tard)
    App->>Me: GET /api/v1/<entity><br/>Authorization: Bearer <expired>
    Me-->>App: 401 Unauthorized
    OkHttp->>OkHttp: Authenticator intercepte<br/>(refreshMutex.withLock)
    OkHttp->>Token: POST /api/v1/refresh<br/>{refresh_token}
    Token->>DB: lookup + revoke ancien + INSERT nouveau refresh
    DB-->>Token: ok
    Token-->>OkHttp: 200 {access_token (neuf), refresh_token (rotated)}
    OkHttp->>Auth: TokenManager.setTokens(neuf)
    OkHttp->>Me: retry requête originale<br/>Authorization: Bearer <new access>
    Me-->>App: 200 payload JSON (transparent pour l'UI)

    Note over User,DB: 6. Refresh KO (expiré / revoqué) -> force logout
    OkHttp->>Token: POST /api/v1/refresh
    Token-->>OkHttp: 401
    OkHttp->>Auth: TokenManager.clearToken()<br/>SyncEvents.onTokenExpired emit
    Auth-->>App: redirect LoginScreen
```

## Notes

- **V8.4 pre-seed signup** : `copy_starter_pack(db, user.id)` (étape 1) copie en transaction le catalogue starter dans les tables user-scoped (12 muscles + 20 exercices + 43 relations `exercise_muscle`). Si la table starter n'est pas seedée → `503 Service Unavailable` + rollback (pas de username squatté).
- **EncryptedSharedPreferences (V8.2)** : `access_token` + `refresh_token` chiffrés au repos via clés Android Keystore — survit aux cold-starts sans réauth.
- **`refreshMutex.withLock`** ([RetrofitInstance.kt:112](../appli-android/app/src/main/java/com/example/sportapp/network/RetrofitInstance.kt)) : un seul `POST /refresh` concurrent même si N requêtes parallèles déclenchent un 401 simultané → évite la reuse-detection côté serveur (qui révoque toutes les sessions si elle voit un refresh deux fois).
- **Reuse detection** : si un refresh déjà révoqué est présenté, le serveur révoque **tous** les tokens du user (vol présumé) → next call → force logout client-side.
- **401 vs 403** : `401` = token absent / expiré / invalide (déclenche refresh). `403` = token valide mais cross-user ou non-admin sur Type C (pas de refresh, juste snackbar erreur).

## Sources

- `serveur/app/routers/auth_router.py` — `/signup`, `/token`, `/refresh`, `/logout`, `/me`, `/me/profile`, `/me` (DELETE).
- `appli-android/.../auth/AuthManager.kt` — `initAuth()` / `stopAuth()`, orchestration WS + merge initial.
- `appli-android/.../network/RetrofitInstance.kt` — `authAuthenticator` (401 → refresh sous mutex), `authInterceptor`, `clientIdInterceptor`.
- `appli-android/.../network/TokenManager.kt` — persistance EncryptedSharedPreferences.
- [FLOWS.md §1](FLOWS.md) — variante curl/JSON.
