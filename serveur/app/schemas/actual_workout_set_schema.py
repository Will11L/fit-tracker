from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- ActualWorkoutSet --------------------

class ActualWorkoutSetBase(BaseModel):
    actual_workout_exercise_uuid: str = Field(..., alias="actualWorkoutExerciseUUID")
    set_order: int = Field(..., alias="setOrder")
    reps: int
    weight: float
    is_dropset: bool = Field(False, alias="isDropset")
    notes: Optional[str] = None
    recommendation: Optional[str] = None
    status: str = "NOT_STARTED"

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class ActualWorkoutSetCreate(ActualWorkoutSetBase):
    pass

class ActualWorkoutSetOut(ActualWorkoutSetBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
