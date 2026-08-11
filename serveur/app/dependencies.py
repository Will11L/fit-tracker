# app/dependencies.py
from fastapi import Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_session
from app.auth import get_current_user, oauth2_scheme, verify_token
from app.models.user import User


def get_current_user_id(token: str = Depends(oauth2_scheme)) -> int:
    """Decode le JWT et lit `user_id` directement du payload.

    Optimisation : evite +1 SELECT par requete (vs lookup username -> user.id).
    Tokens crees apres V1.3 (auth_router.py:68) embarquent `user_id`.
    Si le token est invalide ou ne contient pas user_id (ancien token,
    payload corrompu) -> 401 Unauthorized.
    """
    payload = verify_token(token)
    user_id = payload.get("user_id")
    if user_id is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token sans user_id (re-login requis)",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user_id


async def require_admin(
    db: AsyncSession = Depends(get_session),
    current_user: str = Depends(get_current_user),
) -> User:
    """
    Dependency a injecter sur les endpoints qui ne doivent etre accessibles
    qu'aux comptes administrateurs (writes sur entites globales : equipment,
    training_cycle templates, etc.).
    """
    res = await db.execute(select(User).where(User.username == current_user))
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Utilisateur non trouvé",
        )
    if not user.is_admin:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Privileges admin requis",
        )
    return user
