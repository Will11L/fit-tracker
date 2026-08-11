# app/schemas/muscle.py
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- Muscle --------------------

class MuscleBase(BaseModel):
    uuid: str
    name: str
    muscle_group: Optional[str] = Field(None, alias="muscleGroup")
    zone: Optional[str] = None
    is_favorite: bool = Field(False, alias="isFavorite")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    model_config = {
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }


class MuscleCreate(MuscleBase):
    pass


class MuscleOut(MuscleBase):
    # F7-2 (2026-05-06) : `id` PK technique retiré (Android utilise uniquement
    # `uuid` côté Room ; aucun callsite serveur ne lit `muscle.id`). Cohérent avec
    # les autres entités métier *Out qui n'exposent pas `id`. Seul `User*` garde
    # `id` (légitime : c'est la référence FK utilisée comme `user_id` ailleurs).
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,
    }
