from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- CycleWorkout --------------------

class CycleWorkoutBase(BaseModel):
    training_cycle_uuid: str = Field(..., alias="trainingCycleUUID")
    planned_workout_uuid: str = Field(..., alias="plannedWorkoutUUID")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str

    model_config = {"populate_by_name": True}

# Pour la création
class CycleWorkoutCreate(CycleWorkoutBase):
    pass

# Pour la lecture
class CycleWorkoutOut(CycleWorkoutBase):
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
