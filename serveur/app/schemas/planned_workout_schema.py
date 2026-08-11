from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- PlannedWorkout --------------------

class PlannedWorkoutBase(BaseModel):
    name: str
    day_of_week: str = Field(..., alias="dayOfWeek")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class PlannedWorkoutCreate(PlannedWorkoutBase):
    pass

class PlannedWorkoutOut(PlannedWorkoutBase):
    user_id: int = Field(..., alias="userId")

    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
