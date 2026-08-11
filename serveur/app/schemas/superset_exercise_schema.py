from pydantic import BaseModel, Field
from datetime import datetime
from app.utc_datetime import UTCDateTime
from typing import Optional

# -------------------- SupersetExercise --------------------

class SupersetExerciseBase(BaseModel):
    superset_group_uuid: str = Field(..., alias="supersetGroupUUID")
    exercise_uuid: str = Field(..., alias="exerciseUUID")
    uuid: str
    order_in_group: int = Field(..., alias="orderInGroup")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")
    model_config = {"populate_by_name": True}

class SupersetExerciseCreate(SupersetExerciseBase):
    pass

class SupersetExerciseOut(SupersetExerciseBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
