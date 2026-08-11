from fastapi import APIRouter, Body, Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordRequestForm
from datetime import timedelta
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app import crud
from app import refresh_tokens as rt_helpers
from app.auth import create_access_token, verify_token, oauth2_scheme
from app.database import get_session
from app.models.user import User  # adapte si ton modèle est ailleurs
from app.rate_limit import limiter
from app.schemas.user_schema import MeDeleteRequest, MeProfileUpdate, SignupRequest, UserOut
from app.starter_pack import StarterTemplateMissing, copy_starter_pack
from passlib.context import CryptContext

auth_router = APIRouter()

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)


def hash_password(password: str) -> str:
    return pwd_context.hash(password)

@auth_router.post("/signup", response_model=UserOut, status_code=status.HTTP_201_CREATED)
@limiter.limit("10/hour")
async def signup(
    request: Request,
    payload: SignupRequest,
    db: AsyncSession = Depends(get_session),
):
    """Endpoint public de creation de compte. Pas d'auth requise. Tout
    nouveau user est cree avec is_admin=False par defaut."""
    existing = await db.execute(select(User).where(User.username == payload.username))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username already taken",
        )

    new_user = User(
        username=payload.username,
        hashed_password=hash_password(payload.password),
        email=payload.email,
        first_name=payload.first_name,
        last_name=payload.last_name,
        is_admin=False,
    )
    db.add(new_user)
    # flush() envoie l'INSERT et assigne new_user.id sans commit. Le copy
    # du starter pack se fait dans la meme transaction : si il echoue, le
    # User est rollback aussi (pas de username squatte).
    await db.flush()

    try:
        await copy_starter_pack(db, new_user.id)
    except StarterTemplateMissing:
        await db.rollback()
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Signup unavailable: starter template is not seeded",
        )

    await db.commit()
    await db.refresh(new_user)
    return UserOut.model_validate(new_user)


@auth_router.post("/token")
@limiter.limit("5/minute")
async def login(
    request: Request,
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: AsyncSession = Depends(get_session),
):
    # Récupère l'utilisateur
    res = await db.execute(select(User).where(User.username == form_data.username))
    user = res.scalar_one_or_none()

    if not user or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    access_token_expires = timedelta(minutes=30)
    access_token = create_access_token(
        data={"sub": user.username, "user_id": user.id},
        expires_delta=access_token_expires,
    )
    # V8.2 : emit aussi un refresh long-lived. Le client le stocke dans
    # EncryptedSharedPreferences (cf. V8.2-3 Android) et le presente a
    # /refresh quand l'access expire (~30 min).
    refresh_token = await rt_helpers.create_refresh_token(db, user.id)
    await db.commit()
    return {
        "access_token": access_token,
        "refresh_token": refresh_token,
        "token_type": "bearer",
    }


@auth_router.post("/refresh")
@limiter.limit("30/minute")
async def refresh(
    request: Request,
    refresh_token: str = Body(..., embed=True),
    db: AsyncSession = Depends(get_session),
):
    """Echange un refresh token valide contre un nouveau pair (rotation).

    - Si le refresh est inconnu : 401 simple.
    - Si le refresh est connu mais deja revoke ou expire : reuse detection,
      on revoke TOUS les tokens du user (signal de vol probable) et on
      renvoie 401.
    - Sinon : on revoke ce refresh + on emit un nouveau pair (access +
      nouveau refresh). Le client doit remplacer son refresh par le nouveau.
    """
    matched, invalid = await rt_helpers.find_token(db, refresh_token)

    if matched is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    if invalid:
        # Reuse detection : revoke tous les tokens actifs du user.
        await rt_helpers.revoke_all_user_tokens(db, matched.user_id)
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token revoked or expired (all sessions revoked)",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Recuperer le user pour reconstruire l'access token
    res = await db.execute(select(User).where(User.id == matched.user_id))
    user = res.scalar_one_or_none()
    if user is None:
        # User supprime entre temps - revoke le token et 401
        await rt_helpers.revoke_token(db, matched)
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User no longer exists",
        )

    # Rotation : revoke l'ancien + emit nouveau pair
    await rt_helpers.revoke_token(db, matched)
    new_refresh = await rt_helpers.create_refresh_token(db, user.id)

    access_token = create_access_token(
        data={"sub": user.username, "user_id": user.id},
        expires_delta=timedelta(minutes=30),
    )
    await db.commit()
    return {
        "access_token": access_token,
        "refresh_token": new_refresh,
        "token_type": "bearer",
    }


@auth_router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(
    refresh_token: str = Body(..., embed=True),
    db: AsyncSession = Depends(get_session),
):
    """Revoke le refresh token courant. Ignore silencieusement si le
    token est inconnu/deja revoke (idempotent : un client qui re-clique
    logout ne doit pas avoir d'erreur)."""
    matched, _ = await rt_helpers.find_token(db, refresh_token)
    if matched is not None and matched.revoked_at is None:
        await rt_helpers.revoke_token(db, matched)
        await db.commit()
    # Pas de body 204

@auth_router.get("/me")
async def read_me(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_session),
):
    try:
        payload = verify_token(token)
        username = payload.get("sub")
        user_id = payload.get("user_id")

        if username is None or user_id is None:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

        res = await db.execute(select(User).where(User.id == user_id))
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")

        return {
            "id": user.id,
            "username": user.username,
            "isAdmin": user.is_admin,
            # Vrai email optionnel (2026-06-06) -- nullable. Remplace l'ancien
            # email synthetique `{username}@sportapp.com` (trompeur).
            "email": user.email,
            "firstName": user.first_name,
            "lastName": user.last_name,
            # Bio : completee ici (etait absente => bug "bio a — au reload").
            # date -> ISO "YYYY-MM-DD" via jsonable_encoder de FastAPI.
            "birthDate": user.birth_date,
            "sex": user.sex,
            "heightCm": user.height_cm,
            "weightKg": user.weight_kg,
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))


@auth_router.patch("/me/profile", response_model=UserOut)
async def update_me_profile(
    payload: MeProfileUpdate,
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_session),
):
    """Self-only : le user édite son propre firstName/lastName.
    Utilisé par l'onboarding Welcome step + ProfileScreen futur.
    Pas de require_admin ici -- c'est l'user qui s'édite lui-même via son JWT."""
    payload_decoded = verify_token(token)
    user_id = payload_decoded.get("user_id")
    if user_id is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    res = await db.execute(select(User).where(User.id == user_id))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    # Patch partiel : seuls les champs présents (non-None) sont mis à jour.
    # Permet à l'UI d'envoyer juste {firstName: "..."} sans toucher au reste.
    data = payload.model_dump(exclude_unset=True, by_alias=False)

    # Email unique : si l'user change son email, vérifier qu'aucun AUTRE user ne
    # l'utilise déjà -> 409 (sinon le commit casserait sur la contrainte UNIQUE
    # en 500). On ne vérifie que si l'email change réellement.
    new_email = data.get("email")
    if new_email and new_email != user.email:
        clash = await db.execute(
            select(User).where(User.email == new_email, User.id != user_id)
        )
        if clash.scalar_one_or_none() is not None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Email already in use",
            )

    for field in ("email", "first_name", "last_name", "birth_date", "sex", "height_cm", "weight_kg"):
        if field in data:
            setattr(user, field, data[field])

    await db.commit()
    await db.refresh(user)
    return UserOut.model_validate(user)


@auth_router.delete("/me", response_model=UserOut)
async def delete_me(
    payload: MeDeleteRequest,
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_session),
):
    """Self-only : l'user supprime son propre compte. Suppression IRRÉVERSIBLE :
    le DELETE sur `users` cascade (FK ON DELETE CASCADE) sur toutes les entités
    user-scoped (workouts, exercises, muscles, notifications, refresh_tokens...).

    Garde-fous :
    - Confirmation par mot de passe (re-saisie) -- évite une suppression
      accidentelle ou un compte détruit via un JWT volé.
    - Last-admin : un admin qui est le seul admin restant ne peut pas se
      supprimer (sinon plus personne pour gérer les entités globales).
      Cohérent avec la protection demote de PATCH /users/{id}/admin.
    """
    payload_decoded = verify_token(token)
    user_id = payload_decoded.get("user_id")
    if user_id is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    res = await db.execute(select(User).where(User.id == user_id))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    if not verify_password(payload.password, user.hashed_password):
        # 403 (et non 401) : l'user EST authentifié (JWT valide) -- c'est la
        # re-confirmation du mot de passe qui échoue. Un 401 serait en plus
        # intercepté par l'Authenticator OkHttp côté Android (refresh + retry
        # en boucle sur un simple mauvais mot de passe).
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Incorrect password",
        )

    if user.is_admin and await crud.count_admins(db) <= 1:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot delete the last remaining admin account. "
                   "Promote another user to admin first.",
        )

    # Snapshot AVANT le delete : après commit l'objet est détaché, on renvoie
    # une copie Pydantic figée du user supprimé.
    user_out = UserOut.model_validate(user)
    await crud.delete_user(db, user_id)
    return user_out
