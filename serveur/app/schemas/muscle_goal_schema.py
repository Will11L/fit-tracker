from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from app.utc_datetime import UTCDateTime

# -------------------- MuscleGoal --------------------

class MuscleGoalBase(BaseModel):
    muscle_uuid: str = Field(..., alias="muscleUUID")
    priority: str
    done: int
    target: str
    week_iso: str = Field(..., alias="weekISO")
    status: str
    added_manually: bool = Field(..., alias="addedManually")

    updated_at: Optional[UTCDateTime] = Field(None, alias="updatedAt")

    uuid: str
    model_config = {"populate_by_name": True}

class MuscleGoalCreate(MuscleGoalBase):
    pass

class MuscleGoalOut(MuscleGoalBase):
    user_id: int = Field(..., alias="userId")
    
    model_config = {
        "from_attributes": True,
        "populate_by_name": True,  # accepte snake_case et camelCase en entrÃ©e
    }
