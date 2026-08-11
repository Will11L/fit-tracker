# app/schemas/exercise_equipment_schemas.py

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

class ExerciseEquipmentBase(BaseModel):
    exercise_uuid: str = Field(..., alias="exerciseUUID")
    equipment_uuid: str = Field(..., alias="equipmentUUID")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class ExerciseEquipmentCreate(ExerciseEquipmentBase):
    pass

class ExerciseEquipmentOut(ExerciseEquipmentBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
