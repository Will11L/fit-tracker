from fastapi import APIRouter, Depends, HTTPException
from fastapi.encoders import jsonable_encoder
from sqlalchemy.ext.asyncio import AsyncSession
from app import schemas, crud
from app.database import get_session
from app.dependencies import require_admin
from app.models.user import User
import bcrypt

user_router = APIRouter(tags=["users"])

def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")

@user_router.get("/users", response_model=list[schemas.UserOut])
async def list_users(
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    """Liste tous les users -- admin only. Retourne UserOut (incluant isAdmin)
    pour l'écran admin Android (toggle is_admin par row)."""
    users = await crud.get_all_users(db)
    return jsonable_encoder(users, by_alias=True)

# La creation de compte se fait via POST /signup (auth_router) — endpoint public
# avec validation Pydantic. PUT /users sans id retire en V1.1.

@user_router.get("/users/{user_id}", response_model=schemas.UserPublic)
async def get_user(
    user_id: int,
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    user = await crud.get_user_by_id(db, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="Utilisateur non trouvé")
    return jsonable_encoder(user, by_alias=True)

@user_router.put("/users/bulk", response_model=list[schemas.UserPublic])
async def bulk_upsert_users(
    users: list[schemas.UserUpsert],
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    result: list[schemas.UserPublic] = []
    for user_data in users:
        existing_user = await crud.get_user_by_id(db, user_data.id) if user_data.id else None

        if existing_user:
            hashed_pw = (
                hash_password(user_data.password)
                if user_data.password
                else existing_user.hashed_password
            )
        else:
            hashed_pw = hash_password(user_data.password or "default")

        user = await crud.upsert_user(
            db,
            user_id=user_data.id,
            username=user_data.username,
            hashed_password=hashed_pw,
            first_name=user_data.first_name,
            last_name=user_data.last_name,
        )
        result.append(user)
    return jsonable_encoder(result, by_alias=True)

@user_router.put("/users/{user_id}", response_model=schemas.UserPublic)
async def upsert_user(
    user_id: int,
    user_data: schemas.UserCreate,
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    hashed_pw = hash_password(user_data.password)
    user = await crud.upsert_user(
        db,
        user_id=user_id,
        username=user_data.username,
        hashed_password=hashed_pw,
        first_name=user_data.first_name,
        last_name=user_data.last_name,
    )
    return jsonable_encoder(user, by_alias=True)

@user_router.patch("/users/{user_id}/admin", response_model=schemas.UserOut)
async def toggle_user_admin(
    user_id: int,
    payload: schemas.UserAdminToggle,
    db: AsyncSession = Depends(get_session),
    current_admin: User = Depends(require_admin),
):
    """Toggle le flag is_admin d'un user. Protections :
    - Self-demote refusé : un admin ne peut pas se rétrograder lui-même
      (anti-bricking : éviter la situation "admin se demote tout seul").
    - Last-admin demote refusé : si demote && count(admins) == 1, refuse
      (sinon plus aucun admin -> personne ne peut re-promote sans SQL manuel).
    """
    target = await crud.get_user_by_id(db, user_id)
    if not target:
        raise HTTPException(status_code=404, detail="User not found")

    new_is_admin = payload.is_admin

    # Idempotent : si la valeur ne change pas, return direct sans toucher DB.
    if target.is_admin == new_is_admin:
        return jsonable_encoder(target, by_alias=True)

    # Demote : appliquer les protections
    if new_is_admin is False:
        if target.id == current_admin.id:
            raise HTTPException(
                status_code=400,
                detail="You cannot demote yourself. "
                       "Promote another user first, then ask them to demote you.",
            )
        admin_count = await crud.count_admins(db)
        if admin_count <= 1:
            raise HTTPException(
                status_code=400,
                detail="Cannot demote the last remaining admin. "
                       "Promote another user first.",
            )

    updated = await crud.set_user_admin(db, user_id=user_id, is_admin=new_is_admin)
    return jsonable_encoder(updated, by_alias=True)


@user_router.delete("/users/{user_id}", response_model=schemas.UserPublic)
async def delete_user(
    user_id: int,
    db: AsyncSession = Depends(get_session),
    _admin = Depends(require_admin),
):
    deleted = await crud.delete_user(db, user_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Utilisateur non trouvé")
    return jsonable_encoder(deleted, by_alias=True)
