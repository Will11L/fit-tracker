from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- PlannedWorkoutExercise --------------------

class PlannedWorkoutExerciseBase(BaseModel):
    planned_workout_uuid: str = Field(..., alias="plannedWorkoutUUID")
    exercise_uuid: str = Field(..., alias="exerciseUUID")
    sets: int
    reps: str
    phase: str
    status: str
    order: int
    ignored: bool = False

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class PlannedWorkoutExerciseCreate(PlannedWorkoutExerciseBase):
    pass

class PlannedWorkoutExerciseOut(PlannedWorkoutExerciseBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
