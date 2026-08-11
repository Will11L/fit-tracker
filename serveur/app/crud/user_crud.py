from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import func, select
from app import models


# Récupérer un utilisateur par ID
async def get_user_by_id(db: AsyncSession, user_id: int):
    result = await db.execute(
        select(models.User).where(models.User.id == user_id)
    )
    return result.scalars().first()


# Récupérer un utilisateur par nom d'utilisateur
async def get_user_by_username(db: AsyncSession, username: str):
    result = await db.execute(
        select(models.User).where(models.User.username == username)
    )
    return result.scalars().first()


# Récupérer tous les utilisateurs
async def get_all_users(db: AsyncSession):
    result = await db.execute(select(models.User))
    return result.scalars().all()


# Créer ou mettre à jour un utilisateur
async def upsert_user(
    db: AsyncSession,
    user_id: int | None,
    username: str,
    hashed_password: str,
    first_name: str | None,
    last_name: str | None
):
    if user_id:
        user = await get_user_by_id(db, user_id)
    else:
        user = await get_user_by_username(db, username)

    if user:
        user.username = username
        user.hashed_password = hashed_password
        user.first_name = first_name
        user.last_name = last_name
    else:
        user = models.User(
            username=username,
            hashed_password=hashed_password,
            first_name=first_name,
            last_name=last_name,
        )
        db.add(user)

    await db.commit()
    await db.refresh(user)
    return user


# Compte le nombre d'admins (anti last-admin self-demote)
async def count_admins(db: AsyncSession) -> int:
    result = await db.execute(
        select(func.count(models.User.id)).where(models.User.is_admin.is_(True))
    )
    return int(result.scalar() or 0)


# Toggle uniquement le flag is_admin sans toucher aux autres champs
async def set_user_admin(db: AsyncSession, user_id: int, is_admin: bool):
    user = await get_user_by_id(db, user_id)
    if not user:
        return None
    user.is_admin = is_admin
    await db.commit()
    await db.refresh(user)
    return user


# Supprimer un utilisateur
async def delete_user(db: AsyncSession, user_id: int):
    user = await get_user_by_id(db, user_id)
    if not user:
        return None
    await db.delete(user)
    await db.commit()
    return user
