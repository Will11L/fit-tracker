from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- ExerciseMuscle --------------------

class ExerciseMuscleBase(BaseModel):
    exercise_uuid: str = Field(..., alias="exerciseUUID")
    muscle_uuid: str = Field(..., alias="muscleUUID")
    coefficient: float

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class ExerciseMuscleCreate(ExerciseMuscleBase):
    pass

class ExerciseMuscleOut(ExerciseMuscleBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
